package dev.tmdbrows.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The TV launcher asks apps to (re)initialize channels after install/boot. */
class InitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SyncScheduler.ensurePeriodic(context)
        SyncScheduler.syncNow(context)
    }
}
