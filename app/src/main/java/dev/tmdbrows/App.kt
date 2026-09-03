package dev.tmdbrows

import android.app.Application
import dev.tmdbrows.sync.SyncScheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.ensurePeriodic(this)
    }
}
