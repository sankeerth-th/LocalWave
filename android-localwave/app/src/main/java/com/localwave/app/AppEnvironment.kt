package com.localwave.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.localwave.core.bluetooth.AndroidBleTransport
import com.localwave.core.crypto.AndroidKeystoreIdentityKeyStore
import com.localwave.core.crypto.SessionCrypto
import com.localwave.core.diagnostics.RedactedLogger
import com.localwave.core.model.TransportState
import com.localwave.core.notifications.WakeNotificationManager
import com.localwave.core.persistence.LocalWaveDatabase
import com.localwave.core.persistence.LOCALWAVE_MIGRATION_1_2
import com.localwave.core.persistence.LOCALWAVE_MIGRATION_2_3
import com.localwave.core.persistence.RoomMessageRepository
import com.localwave.core.persistence.RoomPeerRepository
import com.localwave.core.protocol.LocalWaveEngine
import com.localwave.core.protocol.LocalWaveObjectFileStore
import com.localwave.core.protocol.RealLocalWaveEngine
import java.io.File

enum class EngineMode { REAL, MOCK }

data class AppEnvironment(
    val engine: LocalWaveEngine,
    val mode: EngineMode,
    val permissionController: LocalWavePermissionController,
    val database: LocalWaveDatabase? = null
) {
    companion object {
        fun create(context: Context): AppEnvironment {
            val appContext = context.applicationContext
            val database = Room.databaseBuilder(appContext, LocalWaveDatabase::class.java, "localwave.db")
                .addMigrations(LOCALWAVE_MIGRATION_1_2, LOCALWAVE_MIGRATION_2_3)
                .build()
            val identityStore = AndroidKeystoreIdentityKeyStore(appContext)
            val crypto = SessionCrypto(identityStore)
            val logger = RedactedLogger()
            val transport = AndroidBleTransport(appContext, logger)
            val engine = RealLocalWaveEngine(
                identityStore = identityStore,
                crypto = crypto,
                peerRepository = RoomPeerRepository(database.peerDao()),
                messageRepository = RoomMessageRepository(database.messageDao()),
                transport = transport,
                wakeNotificationManager = WakeNotificationManager(appContext),
                objectStore = LocalWaveObjectFileStore(File(appContext.filesDir, "localwave-objects"))
            )
            return AppEnvironment(
                engine = engine,
                mode = EngineMode.REAL,
                permissionController = AndroidPermissionController(appContext),
                database = database
            )
        }
    }
}

interface LocalWavePermissionController {
    fun currentState(): TransportState
    fun requestBluetooth(activity: Activity)
    fun requestNotifications(activity: Activity)
}

class AndroidPermissionController(private val context: Context) : LocalWavePermissionController {
    override fun currentState(): TransportState {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter
        val scan = hasPermission(Manifest.permission.BLUETOOTH_SCAN)
        val advertise = hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
        val connect = hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        val notifications = Build.VERSION.SDK_INT < 33 || hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        return TransportState(
            bluetoothEnabled = adapter?.isEnabled == true,
            scanPermission = Build.VERSION.SDK_INT < 31 || scan,
            advertisePermission = Build.VERSION.SDK_INT < 31 || advertise,
            connectPermission = Build.VERSION.SDK_INT < 31 || connect,
            notificationPermission = notifications,
            bleSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
            advertiserSupported = adapter?.bluetoothLeAdvertiser != null,
            batteryRestricted = false,
            activeModeAvailable = false
        )
    }

    override fun requestBluetooth(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 31) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                1204
            )
        }
    }

    override fun requestNotifications(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1205)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
