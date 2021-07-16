package io.gnosis.safe.notifications

import android.app.*
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.gnosis.data.models.Safe
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.R
import io.gnosis.safe.notifications.models.PushNotification
import io.gnosis.safe.ui.StartActivity
import io.gnosis.safe.utils.BalanceFormatter
import io.gnosis.safe.utils.convertAmount
import io.gnosis.safe.utils.formatForTxList
import kotlinx.coroutines.runBlocking
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.svalinn.common.PreferencesManager
import pm.gnosis.svalinn.common.utils.edit
import pm.gnosis.utils.asEthereumAddressString

class NotificationManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val balanceFormatter: BalanceFormatter,
    private val safeRepository: SafeRepository,
    private val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(context)
) {

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            // For upgrading users. Can be removed in a future release?
            runBlocking {
                safeRepository.getSafes().forEach { safe ->
                    if (notificationManager.getNotificationChannelGroup(safe.notificationChannelId()) == null) {
                        createNotificationChannelGroup(safe)
                    } else if (notificationManager.getNotificationChannelGroup(safe.address.asEthereumAddressChecksumString()) != null) {
                        // legacy notification groups
                        notificationManager.deleteNotificationChannelGroup(safe.address.asEthereumAddressChecksumString())
                    }
                }
                notificationManager.deleteNotificationChannel(CHANNEL_ID)
            }
        }
    }

    private var latestNotificationId: Int
        get() = preferencesManager.prefs.getInt(LATEST_NOTIFICATION_ID, -1)
        set(value) {
            preferencesManager.prefs.edit {
                putInt(LATEST_NOTIFICATION_ID, value)
            }
        }

    fun createNotificationChannelGroup(safe: Safe) {
        val id = safe.notificationChannelId()
        val name = safe.localName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannelGroup(NotificationChannelGroup(id, name))
            createNotificationChannel(id)
        }
    }

    fun deleteNotificationChannelGroup(safe: Safe) {
        val subChannels = ChannelType.values()

        subChannels.forEach { subChannel ->
            notificationManager.deleteNotificationChannel(safe.address.asEthereumAddressChecksumString() + "." + context.getString(subChannel.resId))
        }
        notificationManager.deleteNotificationChannelGroup(safe.address.asEthereumAddressChecksumString())
    }

    fun updateNotificationChannelGroupForSafe(safe: Safe) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannelGroup(safe)
        }
    }

    enum class ChannelType(@StringRes val resId: Int) {
        CONFIRMATION_REQUESTS(R.string.channel_tx_confirmation_request),
        EXECUTED_TRANSACTIONS(R.string.channel_tx_executed_transactions),
        INCOMING_TOKENS(R.string.channel_tx_incoming_tokens),
        INCOMING_ETHER(R.string.channel_tx_incoming_ether)
    }

    private fun createNotificationChannel(channelId: String, importance: Int = NotificationManager.IMPORTANCE_HIGH) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        ChannelType.values().forEach { subChannel ->
            val name = context.getString(subChannel.resId)
            val channel = NotificationChannel("$channelId.$name", name, importance)
            channel.description = name

            channel.enableLights(true)
            channel.lightColor = LIGHT_COLOR

            channel.enableVibration(true)
            channel.vibrationPattern = VIBRATE_PATTERN

            channel.group = channelId

            notificationManager.createNotificationChannel(channel)
        }
    }

    fun builder(
        safe: Safe,
        pushNotification: PushNotification
    ): NotificationCompat.Builder {

        var title = ""
        var text = ""
        var intent: PendingIntent? = null

        val safeName =
            if (safe.localName.isNullOrBlank())
                context.getString(R.string.push_safe_name, safe.address.formatForTxList())
            else
                safe.localName

        when (pushNotification) {
            is PushNotification.ConfirmationRequest -> {
                title = context.getString(R.string.push_title_confirmation_required, safe.chain.name)
                text = context.getString(R.string.push_text_confirmation_required, safeName)
                intent = txDetailsIntent(safe, pushNotification.safeTxHash)
            }
            is PushNotification.ExecutedTransaction -> {
                if (pushNotification.failed) {
                    title = context.getString(R.string.push_title_failed, safe.chain.name)
                    text = context.getString(R.string.push_text_failed, safeName)
                } else {
                    title = context.getString(R.string.push_title_executed)
                    text = context.getString(R.string.push_text_executed, safeName)
                }
                intent = txDetailsIntent(safe, pushNotification.safeTxHash)
            }
            is PushNotification.IncomingToken -> {
                if (pushNotification.tokenId != null) {
                    title = context.getString(R.string.push_title_received_erc721, safe.chain.name)
                    text = context.getString(R.string.push_text_received_erc721, safeName)
                } else {
                    title = context.getString(R.string.push_title_received_erc20, safe.chain.name)
                    text = context.getString(R.string.push_text_received_erc20, safeName)
                }
                intent = txListIntent(safe)
            }
            is PushNotification.IncomingEther -> {
                val currencySymbol = safe.chain.currency.symbol
                title = context.getString(R.string.push_title_received_native_currency, currencySymbol, safe.chain.name)
                val value = balanceFormatter.shortAmount(pushNotification.value.convertAmount(safe.chain.currency.decimals))
                text = context.getString(R.string.push_text_received_native_currency, safeName, value, currencySymbol)
                intent = txListIntent(safe)
            }
        }

        val resId = subChannelForPushNotificationSubType(pushNotification).resId
        val channelId = safe.notificationChannelId() + "." + context.getString(resId)

        return builder(title, text, channelId, intent)
    }

    private fun subChannelForPushNotificationSubType(pushNotification: PushNotification): ChannelType {
        return when (pushNotification) {
            is PushNotification.ConfirmationRequest -> ChannelType.CONFIRMATION_REQUESTS
            is PushNotification.ExecutedTransaction -> ChannelType.EXECUTED_TRANSACTIONS
            is PushNotification.IncomingToken -> ChannelType.INCOMING_TOKENS
            is PushNotification.IncomingEther -> ChannelType.INCOMING_ETHER
        }
    }

    fun builder(
        title: String,
        message: String,
        channelId: String = CHANNEL_ID,
        intent: PendingIntent? = null,
        category: String? = null,
        priority: Int = NotificationCompat.PRIORITY_HIGH
    ) =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_app_24dp)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setVibrate(VIBRATE_PATTERN)
            .setLights(LIGHT_COLOR, 100, 100)
            .setDefaults(Notification.DEFAULT_ALL)
            .setCategory(category)
            .setPriority(priority)
            .setContentIntent(intent)!!


    fun show(id: Int, title: String, message: String, channelId: String?, intent: PendingIntent?) {
        val builder = builder(title, message, channelId ?: CHANNEL_ID, intent)
        show(id, builder.build())
    }

    fun show(notification: Notification) {
        latestNotificationId += 1
        notificationManager.notify(latestNotificationId, notification)
    }

    fun show(id: Int, notification: Notification) {
        notificationManager.notify(id, notification)
    }

    fun hide(id: Int) {
        notificationManager.cancel(id)
    }

    fun hideAll() {
        latestNotificationId = -1
        notificationManager.cancelAll()
    }

    fun notificationsEnabled(): Boolean {
        var enabled = notificationManager.areNotificationsEnabled()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            enabled = enabled && channel?.importance != NotificationManager.IMPORTANCE_NONE

        }
        return enabled
    }

    private fun txDetailsIntent(safe: Safe, safeTxHash: String): PendingIntent {
        val intent = StartActivity.createIntent(context, safe.chainId, safe.address.asEthereumAddressString(), safeTxHash)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun txListIntent(safe: Safe): PendingIntent {
        val intent = StartActivity.createIntent(context, safe.chainId, safe.address.asEthereumAddressString())
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        private const val LATEST_NOTIFICATION_ID = "prefs.int.latest_notification_id"
        private val VIBRATE_PATTERN = longArrayOf(0, 100, 50, 100)
        private const val LIGHT_COLOR = Color.MAGENTA
        private const val CHANNEL_ID = "channel_tx_notifications"
    }
}

fun Safe.notificationChannelId(): String = "${chainId}_${address.asEthereumAddressChecksumString()}"
