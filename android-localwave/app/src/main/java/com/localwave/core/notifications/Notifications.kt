package com.localwave.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.localwave.R

object NotificationChannels {
    const val WAKE_CHANNEL_ID = "localwave_wake"
    const val ACTIVE_CHANNEL_ID = "localwave_active"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(WAKE_CHANNEL_ID, "LocalWave Wake", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(ACTIVE_CHANNEL_ID, "LocalWave Active", NotificationManager.IMPORTANCE_LOW)
        )
    }
}

class WakeNotificationManager(private val context: Context) {
    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun showWake(displayName: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val body = if (displayName.isNullOrBlank()) {
            "A nearby teammate is trying to reach you on LocalWave."
        } else {
            "$displayName is trying to reach you on LocalWave."
        }
        val notification = NotificationCompat.Builder(context, NotificationChannels.WAKE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("LocalWave wake")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        } catch (_: SecurityException) {
            // Wake is best-effort and must respect notification permission revocation.
        }
    }

    fun activeNotification(): Notification =
        NotificationCompat.Builder(context, NotificationChannels.ACTIVE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("LocalWave active nearby")
            .setContentText("Bluetooth nearby mode is running.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
