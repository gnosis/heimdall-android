package io.gnosis.safe.workers

import androidx.work.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WorkRepository
@Inject constructor(
    private val workManager: WorkManager
) {

    fun updateChainInfo() {
        val updateChainInfoRequest: WorkRequest =
            OneTimeWorkRequestBuilder<UpdateChainInfoWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                .build()
        workManager.enqueue(updateChainInfoRequest)
    }

    fun registerForPushNotifications(token: String? = null) {
        val notificationsRegistrationRequest: WorkRequest =
            OneTimeWorkRequestBuilder<NotificationsRegistrationWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                .setInputData(Data.Builder().putString(NotificationsRegistrationWorker.TOKEN, token).build())
                .build()
        workManager.enqueue(notificationsRegistrationRequest)
    }
}
