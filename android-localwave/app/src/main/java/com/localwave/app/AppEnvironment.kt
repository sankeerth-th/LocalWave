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
import com.localwave.core.model.TransportState
import com.localwave.core.protocol.LocalWaveEngine
import com.localwave.mock.MockLocalWaveEngine

enum class EngineMode { MOCK, REAL_MISSING }

data class AppEnvironment(
    val engine: LocalWaveEngine,
    val mode: EngineMode,
    val permissionController: LocalWavePermissionController
) {
    companion object {
        fun create(context: Context): AppEnvironment = AppEnvironment(
            engine = MockLocalWaveEngine(),
            mode = EngineMode.MOCK,
            permissionController = AndroidPermissionController(context.applicationContext)
        )
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
