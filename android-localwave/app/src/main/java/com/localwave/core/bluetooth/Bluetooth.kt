package com.localwave.core.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.localwave.core.diagnostics.RedactedLogger
import com.localwave.core.model.ChannelCode
import com.localwave.core.model.LocalIdentity
import com.localwave.core.model.PeerId
import com.localwave.core.model.PeerProfile
import com.localwave.core.model.PresenceState
import com.localwave.core.model.TransportPermissionState
import com.localwave.core.model.TransportState
import com.localwave.core.notifications.WakeNotificationManager
import com.localwave.core.protocol.UuidDerivation
import java.util.UUID
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

sealed interface BluetoothTransportEvent {
    data class PeerDiscovered(val peer: PeerProfile) : BluetoothTransportEvent
    data class Packet(val data: ByteArray, val from: PeerId?) : BluetoothTransportEvent
    data class StateChanged(val state: TransportState) : BluetoothTransportEvent
}

interface BluetoothTransport {
    suspend fun start(channel: ChannelCode, identity: LocalIdentity)
    suspend fun stop()
    suspend fun send(data: ByteArray, kind: com.localwave.core.model.TransportPacketKind, to: PeerId)
    fun observeEvents(): Flow<BluetoothTransportEvent>
    val state: StateFlow<TransportState>
}

class BlePermissionManager(private val context: Context) {
    fun status(): BlePermissionStatus {
        val checker = BleCapabilityChecker(context)
        if (!checker.bleSupported()) return BlePermissionStatus(TransportPermissionState.BLE_UNSUPPORTED)
        if (!checker.bluetoothEnabled()) return BlePermissionStatus(TransportPermissionState.BLUETOOTH_DISABLED)
        if (!checker.advertiserSupported()) return BlePermissionStatus(TransportPermissionState.BLE_ADVERTISER_UNSUPPORTED)
        if (Build.VERSION.SDK_INT >= 31) {
            if (!has(Manifest.permission.BLUETOOTH_SCAN)) return BlePermissionStatus(TransportPermissionState.MISSING_SCAN_PERMISSION)
            if (!has(Manifest.permission.BLUETOOTH_ADVERTISE)) return BlePermissionStatus(TransportPermissionState.MISSING_ADVERTISE_PERMISSION)
            if (!has(Manifest.permission.BLUETOOTH_CONNECT)) return BlePermissionStatus(TransportPermissionState.MISSING_CONNECT_PERMISSION)
        } else if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return BlePermissionStatus(TransportPermissionState.LOCATION_PERMISSION_NEEDED)
        }
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) {
            return BlePermissionStatus(TransportPermissionState.MISSING_NOTIFICATION_PERMISSION)
        }
        return BlePermissionStatus(TransportPermissionState.ALLOWED)
    }

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

data class BlePermissionStatus(val state: TransportPermissionState)

class BleCapabilityChecker(private val context: Context) {
    private val manager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    fun bleSupported(): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    fun bluetoothEnabled(): Boolean = manager?.adapter?.isEnabled == true
    fun advertiserSupported(): Boolean = manager?.adapter?.isMultipleAdvertisementSupported == true
}

class AndroidBleTransport(
    private val context: Context,
    private val logger: RedactedLogger
) : BluetoothTransport {
    private val permissionManager = BlePermissionManager(context)
    private val bluetoothManager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val framer = BlePacketFramer()
    private val events = MutableSharedFlow<BluetoothTransportEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val _state = MutableStateFlow(TransportState())
    override val state: StateFlow<TransportState> = _state

    private var advertiserServer: BleAdvertiserServer? = null
    private var scannerClient: BleScannerClient? = null
    private var gattClient: GattClientManager? = null
    private var identity: LocalIdentity? = null
    private var channel: ChannelCode? = null

    override suspend fun start(channel: ChannelCode, identity: LocalIdentity) {
        this.channel = channel
        this.identity = identity
        val permission = permissionManager.status().state
        if (permission != TransportPermissionState.ALLOWED && permission != TransportPermissionState.MISSING_NOTIFICATION_PERMISSION) {
            publishState(TransportState(permission = permission, lastError = permission.name))
            return
        }
        val profile = GattProfile.forChannel(channel)
        advertiserServer = BleAdvertiserServer(context, profile, identity, logger) { data ->
            events.tryEmit(BluetoothTransportEvent.Packet(data, null))
        }.also { it.start() }
        gattClient = GattClientManager(context, profile, logger, events)
        scannerClient = BleScannerClient(context, profile, logger) { result ->
            gattClient?.connect(result)
        }.also { it.start() }
        publishState(
            TransportState(
                isRunning = true,
                isScanning = true,
                isAdvertising = true,
                permission = TransportPermissionState.ALLOWED,
                notificationPermission = permission != TransportPermissionState.MISSING_NOTIFICATION_PERMISSION
            )
        )
    }

    override suspend fun stop() {
        scannerClient?.stop()
        advertiserServer?.stop()
        gattClient?.close()
        scannerClient = null
        advertiserServer = null
        gattClient = null
        publishState(TransportState())
    }

    override suspend fun send(data: ByteArray, kind: com.localwave.core.model.TransportPacketKind, to: PeerId) {
        val chunks = framer.frame(data, kind).map { framer.encode(it) }
        gattClient?.send(chunks, to) ?: throw IllegalStateException("Peer is not connected.")
    }

    override fun observeEvents(): Flow<BluetoothTransportEvent> = events.asSharedFlow()

    private fun publishState(state: TransportState) {
        _state.value = state
        events.tryEmit(BluetoothTransportEvent.StateChanged(state))
    }
}

data class GattProfile(
    val serviceUuid: UUID,
    val packetCharacteristicUuid: UUID,
    val presenceCharacteristicUuid: UUID,
    val wakeCharacteristicUuid: UUID
) {
    companion object {
        fun forChannel(channel: ChannelCode): GattProfile {
            val gatt = UuidDerivation.derive(channel).gatt
            return GattProfile(gatt.serviceUuid, gatt.packetCharacteristicUuid, gatt.presenceCharacteristicUuid, gatt.wakeCharacteristicUuid)
        }
    }
}

@Serializable
private data class PresenceAdvertisement(
    val peerId: String,
    val displayName: String,
    val fingerprint: String,
    val agreementPublicKey: String
)

class BleAdvertiserServer(
    private val context: Context,
    private val profile: GattProfile,
    private val identity: LocalIdentity,
    private val logger: RedactedLogger,
    private val onPacket: (ByteArray) -> Unit
) {
    private val manager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    private val advertiser: BluetoothLeAdvertiser? = manager?.adapter?.bluetoothLeAdvertiser
    private var gattServer: BluetoothGattServer? = null

    @SuppressLint("MissingPermission")
    fun start() {
        val service = BluetoothGattService(profile.serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(profile.presenceCharacteristicUuid, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                profile.packetCharacteristicUuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(profile.wakeCharacteristicUuid, BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE)
        )
        gattServer = manager?.openGattServer(context, serverCallback)?.also { it.addService(service) }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(profile.serviceUuid))
            .setIncludeDeviceName(false)
            .build()
        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        gattServer = null
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid != profile.presenceCharacteristicUuid) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                return
            }
            val bytes = presenceBytes()
            val slice = if (offset <= bytes.size) bytes.copyOfRange(offset, bytes.size) else null
            gattServer?.sendResponse(device, requestId, if (slice == null) BluetoothGatt.GATT_FAILURE else BluetoothGatt.GATT_SUCCESS, offset, slice)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == profile.packetCharacteristicUuid || characteristic.uuid == profile.wakeCharacteristicUuid) {
                onPacket(value)
                if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            } else if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            logger.record("ble", "advertise failed code=$errorCode")
        }
    }

    private fun presenceBytes(): ByteArray = Json.encodeToString(
        PresenceAdvertisement(
            peerId = identity.peerId.value,
            displayName = identity.displayName,
            fingerprint = identity.fingerprint,
            agreementPublicKey = Base64.getEncoder().encodeToString(identity.agreementPublicKey)
        )
    ).encodeToByteArray()
}

class BleScannerClient(
    private val context: Context,
    private val profile: GattProfile,
    private val logger: RedactedLogger,
    private val onDiscover: (ScanResult) -> Unit
) {
    private val scanner: BluetoothLeScanner? =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner

    @SuppressLint("MissingPermission")
    fun start() {
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(profile.serviceUuid)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        scanner?.startScan(listOf(filter), settings, callback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        scanner?.stopScan(callback)
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            onDiscover(result)
        }

        override fun onScanFailed(errorCode: Int) {
            logger.record("ble", "scan failed code=$errorCode")
        }
    }
}

class GattClientManager(
    private val context: Context,
    private val profile: GattProfile,
    private val logger: RedactedLogger,
    private val events: MutableSharedFlow<BluetoothTransportEvent>
) {
    private val scheduler = ConnectionScheduler()
    private val connected = mutableMapOf<PeerId, GattConnection>()

    @SuppressLint("MissingPermission")
    fun connect(result: ScanResult) {
        if (!scheduler.shouldAttempt(result.device.address)) return
        result.device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun send(chunks: List<ByteArray>, peerId: PeerId) {
        val connection = connected[peerId] ?: throw IllegalStateException("Peer unavailable.")
        connection.pendingWrites.addAll(chunks)
        connection.flush()
    }

    @SuppressLint("MissingPermission")
    fun close() {
        connected.values.forEach { it.gatt.close() }
        connected.clear()
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestMtu(185)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected.values.removeAll { it.gatt == gatt }
                gatt.close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(profile.serviceUuid) ?: return
            val presence = service.getCharacteristic(profile.presenceCharacteristicUuid) ?: return
            gatt.readCharacteristic(presence)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (characteristic.uuid != profile.presenceCharacteristicUuid || status != BluetoothGatt.GATT_SUCCESS) return
            val presence = runCatching {
                Json.decodeFromString<PresenceAdvertisement>(value.decodeToString())
            }.getOrElse {
                logger.record("ble", "presence decode failed")
                return
            }
            val peerId = PeerId(presence.peerId)
            val service = gatt.getService(profile.serviceUuid) ?: return
            val packetCharacteristic = service.getCharacteristic(profile.packetCharacteristicUuid) ?: return
            connected[peerId] = GattConnection(gatt, packetCharacteristic)
            val peer = PeerProfile(
                id = peerId,
                displayName = presence.displayName,
                fingerprint = presence.fingerprint,
                rssi = 0,
                lastSeenEpochMillis = System.currentTimeMillis(),
                state = PresenceState.AVAILABLE,
                publicKeyData = Base64.getDecoder().decode(presence.agreementPublicKey)
            )
            events.tryEmit(BluetoothTransportEvent.PeerDiscovered(peer))
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            connected.values.firstOrNull { it.gatt == gatt }?.flush()
        }
    }
}

class GattConnection(val gatt: BluetoothGatt, private val packetCharacteristic: BluetoothGattCharacteristic) {
    val pendingWrites: ArrayDeque<ByteArray> = ArrayDeque()

    @SuppressLint("MissingPermission")
    fun flush() {
        val next = pendingWrites.removeFirstOrNull() ?: return
        packetCharacteristic.value = next
        gatt.writeCharacteristic(packetCharacteristic)
    }
}

class ConnectionScheduler {
    private val retryTimes = mutableMapOf<String, Long>()

    fun shouldAttempt(address: String, now: Long = System.currentTimeMillis()): Boolean {
        val retryAt = retryTimes[address] ?: return true.also { scheduleRetry(address) }
        return now >= retryAt
    }

    fun scheduleRetry(address: String, intervalMillis: Long = 5_000) {
        retryTimes[address] = System.currentTimeMillis() + intervalMillis
    }

    fun clear(address: String) {
        retryTimes.remove(address)
    }
}

class PresenceManager
class WakePingManager

class ActiveLocalWaveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = WakeNotificationManager(this).activeNotification()
        startForeground(LOCALWAVE_ACTIVE_NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val LOCALWAVE_ACTIVE_NOTIFICATION_ID = 4021
    }
}
