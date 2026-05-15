package com.localwave

import android.app.Application
import com.localwave.core.notifications.NotificationChannels

class LocalWaveApplication : Application() {
    lateinit var environment: AppEnvironment
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        environment = AppEnvironment.create(this)
    }
}
