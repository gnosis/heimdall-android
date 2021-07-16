package io.gnosis.safe.notifications.firebase

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.gnosis.safe.HeimdallApplication
import io.gnosis.safe.notifications.NotificationRepository
import io.gnosis.safe.notifications.models.PushNotification
import io.gnosis.safe.workers.WorkRepository
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

class HeimdallFirebaseService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationRepo: NotificationRepository

    @Inject
    lateinit var workRepository: WorkRepository

    override fun onCreate() {
        super.onCreate()
        HeimdallApplication[this].inject(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // No data received
        if (message.data.isEmpty()) return
        runBlocking {
            try {
                notificationRepo.handlePushNotification(PushNotification.fromMap(message.data))
            } catch (e: IllegalArgumentException) {
                Timber.e(e)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("Firebase token: $token")
        workRepository.registerForPushNotifications(token)
    }
}
