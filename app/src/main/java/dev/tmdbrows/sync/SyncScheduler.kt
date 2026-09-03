package dev.tmdbrows.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val PERIODIC = "tmdbrows-periodic"
    private const val ONESHOT = "tmdbrows-now"

    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensurePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(net).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Sync everything now, or just one list if [configId] is given. */
    fun syncNow(context: Context, configId: Long? = null) {
        val data = Data.Builder().apply { if (configId != null) putLong(SyncWorker.KEY_CONFIG_ID, configId) }.build()
        val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(net).setInputData(data).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONESHOT, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }
}
