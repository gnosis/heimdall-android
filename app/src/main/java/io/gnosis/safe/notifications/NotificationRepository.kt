package io.gnosis.safe.notifications

import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging.INSTANCE_ID_SCOPE
import io.gnosis.data.models.Owner
import io.gnosis.data.models.Safe
import io.gnosis.data.repositories.CredentialsRepository
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.BuildConfig
import io.gnosis.safe.Error
import io.gnosis.safe.notifications.models.PushNotification
import io.gnosis.safe.notifications.models.Registration
import io.gnosis.safe.toError
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.PreferencesManager
import pm.gnosis.svalinn.common.utils.edit
import pm.gnosis.utils.addHexPrefix
import pm.gnosis.utils.hexToByteArray
import timber.log.Timber
import java.math.BigInteger
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class NotificationRepository(
    private val safeRepository: SafeRepository,
    private val credentialsRepository: CredentialsRepository,
    private val preferencesManager: PreferencesManager,
    private val notificationService: NotificationServiceApi,
    private val notificationManager: NotificationManager
) {

    //FIXME: workaround for versioning validation on notification service
    private val appVersion: String = BuildConfig.VERSION_NAME
        .replace("(\\d*)\\.(\\d*)\\.(\\d*)(.*)".toRegex()) {
            "${it.groupValues[1]}.${it.groupValues[2]}.${it.groupValues[3]}"
        }

    private var deviceUuid: String?
        get() = preferencesManager.prefs.getString(KEY_DEVICE_UUID, null)
        set(value) {
            preferencesManager.prefs.edit {
                putString(KEY_DEVICE_UUID, value)
            }
        }

    val registered: Boolean
        get() = deviceUuid != null

    suspend fun handlePushNotification(pushNotification: PushNotification) {
        val safe = safeRepository.getSafeBy(pushNotification.safe, pushNotification.chainId)
        if (safe == null) {
            unregisterSafe(pushNotification.chainId, pushNotification.safe)
        } else {
            val notification = notificationManager.builder(safe, pushNotification).build()
            notificationManager.show(notification)
        }
    }

    fun checkPermissions(): Boolean = notificationManager.notificationsEnabled()

    fun clearNotifications() {
        notificationManager.hideAll()
    }

    suspend fun register(token: String? = null) {
        val cloudMessagingToken = token ?: getCloudMessagingToken()
        cloudMessagingToken?.let {
            val registration = Registration(
                uuid = deviceUuid ?: generateUUID(),
                cloudMessagingToken = cloudMessagingToken,
                buildNumber = BuildConfig.VERSION_CODE.toString(),
                bundle = BuildConfig.APPLICATION_ID,
                deviceType = "ANDROID",
                version = appVersion,
                timestamp = (System.currentTimeMillis() / 1000).toString()
            )

            val safesByChain = safeRepository.getSafes().groupBy { it.chainId }

            if (safesByChain.isNotEmpty()) {

                val owners = credentialsRepository.owners()
                registration.addChainRegistrations(safesByChain, owners)

                kotlin.runCatching {
                    notificationService.register(registration)
                }
                    .onSuccess {
                        Timber.d("notification service registration success")
                        deviceUuid = registration.uuid
                    }
                    .onFailure {
                        Timber.d("notification service registration failure")
                        deviceUuid = null
                        handleCloudMessagingTokenIsLinkedToAnotherDeviceError(it)
                    }
            }
        }
    }

    suspend fun registerSafes(notifySafe: Safe? = null) {
        val token = getCloudMessagingToken()
        token?.let {
            val registration = Registration(
                uuid = deviceUuid ?: generateUUID(),
                cloudMessagingToken = token,
                buildNumber = BuildConfig.VERSION_CODE.toString(),
                bundle = BuildConfig.APPLICATION_ID,
                deviceType = "ANDROID",
                version = appVersion,
                timestamp = (System.currentTimeMillis() / 1000).toString()
            )

            val safesByChain = safeRepository.getSafes().groupBy { it.chainId }
            val owners = credentialsRepository.owners()
            registration.addChainRegistrations(safesByChain, owners)

            kotlin.runCatching {
                notificationService.register(registration)
            }
                .onSuccess {
                    deviceUuid = registration.uuid
                    notifySafe?.let {
                        notificationManager.updateNotificationChannelGroupForSafe(notifySafe)
                    }
                }
                .onFailure {
                    handleCloudMessagingTokenIsLinkedToAnotherDeviceError(it)
                }
        }
    }

    suspend fun unregisterOwners() {
        val token = getCloudMessagingToken()
        token?.let {
            val registration = Registration(
                uuid = deviceUuid ?: generateUUID(),
                cloudMessagingToken = token,
                buildNumber = BuildConfig.VERSION_CODE.toString(),
                bundle = BuildConfig.APPLICATION_ID,
                deviceType = "ANDROID",
                version = appVersion,
                timestamp = (System.currentTimeMillis() / 1000).toString()
            )

            val safesByChain = safeRepository.getSafes().groupBy { it.chainId }
            val owners = credentialsRepository.owners()
            registration.addChainRegistrations(safesByChain, owners)

            kotlin.runCatching {
                // owners whose signature is missing will be unregistered
                notificationService.register(registration)
            }
                .onSuccess {
                    deviceUuid = registration.uuid
                }
                .onFailure {
                    handleCloudMessagingTokenIsLinkedToAnotherDeviceError(it)
                }
        }
    }

    private suspend fun handleCloudMessagingTokenIsLinkedToAnotherDeviceError(throwable: Throwable) {
        val error = throwable.toError()
        if (error == Error.Error1113) {
            resetFirebaseToken()
        }
    }

    private suspend fun resetFirebaseToken() {
        kotlin.runCatching {
            with(FirebaseInstanceId.getInstance()) {
                deleteInstanceId()
                getCloudMessagingToken()?.let { token ->
                    deleteToken(token, INSTANCE_ID_SCOPE)
                }
            }
        }
    }

    suspend fun unregisterSafe(chainId: BigInteger, safeAddress: Solidity.Address) {
        // no need to update safe meta because on safe removal safe meta entry will be also deleted
        kotlin.runCatching {
            deviceUuid?.let {
                notificationService.unregisterSafe(it, chainId, safeAddress.asEthereumAddressChecksumString())
            }
        }
    }

    private suspend fun getCloudMessagingToken() = suspendCoroutine<String?> { cont ->
        FirebaseInstanceId.getInstance().instanceId
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Timber.e(task.exception)
                    cont.resumeWithException(task.exception!!)
                } else {
                    // Get new Instance ID token
                    val token = task.result?.token
                    Timber.d("Firebase token: $token")
                    cont.resume(token)
                }
            }
    }

    private fun generateUUID(): String {
        deviceUuid = UUID.randomUUID().toString().toLowerCase()
        return deviceUuid!!
    }

    private fun Registration.addChainRegistrations(safesByChain: Map<BigInteger, List<Safe>>, owners: List<Owner>) {
        safesByChain.keys.forEach {
            val safesForChain = safesByChain[it]!!.sortedBy { it.address.value }.map {
                it.address.asEthereumAddressChecksumString()
            }
            val chainRegistrationData = Registration.ChainData(
                it.toString(),
                safesForChain
            )
            val hashForChain = hashForSafes(safesForChain)
            chainRegistrationData.addSignatures(hashForChain, owners)
            addRegistrationData(chainRegistrationData)
        }
    }

    private fun Registration.ChainData.addSignatures(registrationHashForChain: String, owners: List<Owner>) {
        if (owners.isNotEmpty()) {
            val registrationHash = registrationHashForChain.hexToByteArray()
            owners.forEach {
                try {
                    val signature = credentialsRepository.signWithOwner(it, registrationHash).addHexPrefix()
                    addSignature(signature)
                } catch (e: Exception) {
                    Timber.e(e, "Exception while signing")
                }
            }
        }
    }

    companion object {
        private const val KEY_DEVICE_UUID = "prefs.string.device_uuid"
    }
}
