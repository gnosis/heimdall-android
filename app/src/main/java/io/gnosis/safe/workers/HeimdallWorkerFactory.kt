package io.gnosis.safe.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.gnosis.data.repositories.ChainInfoRepository
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.Tracker
import io.gnosis.safe.notifications.NotificationRepository
import javax.inject.Inject

class HeimdallWorkerFactory @Inject constructor(
    private val safeRepository: SafeRepository,
    private val chainInfoRepository: ChainInfoRepository,
    private val notificationRepository: NotificationRepository,
    private val tracker: Tracker
) : WorkerFactory() {

    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? {

        return when (workerClassName) {
            UpdateChainInfoWorker::class.java.name -> UpdateChainInfoWorker(
                safeRepository,
                chainInfoRepository,
                tracker,
                appContext,
                workerParameters
            )
            NotificationsRegistrationWorker::class.java.name -> NotificationsRegistrationWorker(
                notificationRepository,
                tracker,
                appContext,
                workerParameters
            )
            else -> null
        }
    }
}
