package com.localwave.app

import android.app.Application
import com.localwave.core.notifications.NotificationChannels

class LocalWaveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
    }
}
