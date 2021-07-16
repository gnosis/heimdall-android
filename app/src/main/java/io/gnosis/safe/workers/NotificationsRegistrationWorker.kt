package io.gnosis.safe.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.gnosis.safe.Tracker
import io.gnosis.safe.notifications.NotificationRepository

class NotificationsRegistrationWorker(
    private val notificationsRepository: NotificationRepository,
    private val tracker: Tracker,
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val token = inputData.getString(TOKEN)
        return try {
            notificationsRepository.register(token)
            if (notificationsRepository.registered) {
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            tracker.logException(e)
            Result.failure()
        }
    }

    companion object {
        const val TOKEN = "token"
    }
}
