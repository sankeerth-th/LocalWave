import CoreBluetooth
import Foundation

public enum BluetoothTransportEvent: Sendable {
    case peerDiscovered(PeerProfile)
    case packet(Data, from: PeerID?)
    case stateChanged(TransportState)
}

public protocol BluetoothTransportProtocol: AnyObject, Sendable {
    func start(channel: ChannelCode, identity: LocalIdentity) async throws
    func stop() async
    func send(_ data: Data, kind: TransportPacketKind, to peerId: PeerID) async throws
    func observeEvents() -> AsyncStream<BluetoothTransportEvent>
}

public final class LocalWaveBluetoothTransport: NSObject, BluetoothTransportProtocol, @unchecked Sendable {
    private struct PresenceAdvertisement: Codable {
        var peerId: PeerID
        var displayName: String
        var fingerprint: String
        var agreementPublicKey: Data
    }

    private let queue = DispatchQueue(label: "com.localwave.bluetooth.transport")
    private let framer = BLEPacketFramer()
    private var centralManager: CBCentralManager?
    private var peripheralManager: CBPeripheralManager?
    private var channel: ChannelCode?
    private var identity: LocalIdentity?
    private var profile: BLECBUUIDProfile?
    private var mutablePacketCharacteristic: CBMutableCharacteristic?
    private var continuations: [UUID: AsyncStream<BluetoothTransportEvent>.Continuation] = [:]
    private var discoveredPeripherals: [PeerID: CBPeripheral] = [:]
    private var packetCharacteristics: [PeerID: CBCharacteristic] = [:]
    private var pendingWrites: [PeerID: [Data]] = [:]

    public override init() {
        super.init()
    }

    public func start(channel: ChannelCode, identity: LocalIdentity) async throws {
        #if targetEnvironment(simulator)
        updateState(TransportState(permission: .unavailable, lastError: "BLE transport requires physical iOS devices."))
        throw LocalWaveError.bluetoothUnavailable
        #else
        self.channel = channel
        self.identity = identity
        self.profile = BLEUUIDFactory.profile(for: channel)
        updateState(TransportState(isRunning: true, permission: .unknown))

        if centralManager == nil {
            centralManager = CBCentralManager(delegate: self, queue: queue)
        }
        if peripheralManager == nil {
            peripheralManager = CBPeripheralManager(delegate: self, queue: queue)
        }
        #endif
    }

    public func stop() async {
        queue.async { [weak self] in
            guard let self else { return }
            if let profile {
                centralManager?.stopScan()
                peripheralManager?.stopAdvertising()
                peripheralManager?.removeAllServices()
                discoveredPeripherals.values.forEach { self.centralManager?.cancelPeripheralConnection($0) }
                self.profile = profile
            }
            discoveredPeripherals.removeAll()
            packetCharacteristics.removeAll()
            pendingWrites.removeAll()
            updateState(TransportState())
        }
    }

    public func send(_ data: Data, kind: TransportPacketKind, to peerId: PeerID) async throws {
        guard data.count <= BLEPacketFramer.defaultMaxPayloadBytes else {
            throw LocalWaveError.oversizedMessage(limitBytes: BLEPacketFramer.defaultMaxPayloadBytes)
        }

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            queue.async { [weak self] in
                guard let self else {
                    continuation.resume(throwing: LocalWaveError.bluetoothUnavailable)
                    return
                }
                guard let peripheral = discoveredPeripherals[peerId],
                      let characteristic = packetCharacteristics[peerId] else {
                    continuation.resume(throwing: LocalWaveError.peerUnavailable)
                    return
                }

                do {
                    let chunks = try self.framer.frame(body: data, kind: kind, conversationId: UUID()).map { self.framer.encode($0) }
                    chunks.forEach { chunk in
                        peripheral.writeValue(chunk, for: characteristic, type: .withoutResponse)
                    }
                    continuation.resume()
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    public func observeEvents() -> AsyncStream<BluetoothTransportEvent> {
        AsyncStream { continuation in
            let id = UUID()
            queue.async { [weak self] in
                self?.continuations[id] = continuation
            }
            continuation.onTermination = { [weak self] _ in
                self?.queue.async {
                    self?.continuations[id] = nil
                }
            }
        }
    }

    private func emit(_ event: BluetoothTransportEvent) {
        continuations.values.forEach { $0.yield(event) }
    }

    private func updateState(_ state: TransportState) {
        emit(.stateChanged(state))
    }

    private func startScanningIfPossible() {
        guard let centralManager, centralManager.state == .poweredOn, let profile else { return }
        centralManager.scanForPeripherals(withServices: [profile.serviceUUID], options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
        updateState(TransportState(isRunning: true, isScanning: true, isAdvertising: peripheralManager?.isAdvertising == true, permission: .allowed))
    }

    private func startAdvertisingIfPossible() {
        guard let peripheralManager, peripheralManager.state == .poweredOn, let profile else { return }
        let packetCharacteristic = CBMutableCharacteristic(
            type: profile.packetCharacteristicUUID,
            properties: [.writeWithoutResponse, .notify],
            value: nil,
            permissions: [.writeable]
        )
        let presenceCharacteristic = CBMutableCharacteristic(
            type: profile.presenceCharacteristicUUID,
            properties: [.read],
            value: presenceData(),
            permissions: [.readable]
        )
        let wakeCharacteristic = CBMutableCharacteristic(
            type: profile.wakeCharacteristicUUID,
            properties: [.writeWithoutResponse],
            value: nil,
            permissions: [.writeable]
        )
        let service = CBMutableService(type: profile.serviceUUID, primary: true)
        service.characteristics = [packetCharacteristic, presenceCharacteristic, wakeCharacteristic]
        mutablePacketCharacteristic = packetCharacteristic
        peripheralManager.removeAllServices()
        peripheralManager.add(service)
    }

    private func advertiseService() {
        guard let peripheralManager, let profile else { return }
        var advertisement: [String: Any] = [
            CBAdvertisementDataServiceUUIDsKey: [profile.serviceUUID],
            CBAdvertisementDataLocalNameKey: "LocalWave"
        ]
        if let presence = presenceData() {
            advertisement[CBAdvertisementDataServiceDataKey] = [profile.serviceUUID: presence]
        }
        peripheralManager.startAdvertising(advertisement)
        updateState(TransportState(isRunning: true, isScanning: centralManager?.isScanning == true, isAdvertising: true, permission: .allowed))
    }

    private func presenceData() -> Data? {
        guard let identity else { return nil }
        let presence = PresenceAdvertisement(
            peerId: identity.peerId,
            displayName: identity.displayName,
            fingerprint: identity.fingerprint,
            agreementPublicKey: identity.agreementPublicKey
        )
        return try? JSONEncoder().encode(presence)
    }

    private func peer(from advertisementData: [String: Any], rssi RSSI: NSNumber) -> PeerProfile? {
        guard let profile,
              let serviceData = advertisementData[CBAdvertisementDataServiceDataKey] as? [CBUUID: Data],
              let data = serviceData[profile.serviceUUID],
              let presence = try? JSONDecoder().decode(PresenceAdvertisement.self, from: data) else {
            return nil
        }
        guard presence.peerId != identity?.peerId else {
            return nil
        }
        return PeerProfile(
            id: presence.peerId,
            displayName: presence.displayName,
            fingerprint: presence.fingerprint,
            rssi: RSSI.intValue,
            lastSeen: Date(),
            state: .available,
            publicKeyData: presence.agreementPublicKey
        )
    }
}

extension LocalWaveBluetoothTransport: CBCentralManagerDelegate {
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            startScanningIfPossible()
        case .unauthorized:
            updateState(TransportState(permission: .denied, lastError: LocalWaveError.bluetoothPermissionDenied.localizedDescription))
        case .unsupported, .poweredOff:
            updateState(TransportState(permission: .unavailable, lastError: LocalWaveError.bluetoothUnavailable.localizedDescription))
        default:
            updateState(TransportState(permission: .unknown))
        }
    }

    public func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        guard let peer = peer(from: advertisementData, rssi: RSSI) else { return }
        discoveredPeripherals[peer.id] = peripheral
        peripheral.delegate = self
        emit(.peerDiscovered(peer))
        if peripheral.state == .disconnected {
            central.connect(peripheral)
        }
    }

    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        guard let profile else { return }
        peripheral.discoverServices([profile.serviceUUID])
    }
}

extension LocalWaveBluetoothTransport: CBPeripheralDelegate {
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard error == nil, let profile else { return }
        peripheral.services?.forEach { service in
            guard service.uuid == profile.serviceUUID else { return }
            peripheral.discoverCharacteristics([profile.packetCharacteristicUUID, profile.presenceCharacteristicUUID], for: service)
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard error == nil, let profile else { return }
        service.characteristics?.forEach { characteristic in
            if characteristic.uuid == profile.packetCharacteristicUUID,
               let peerId = discoveredPeripherals.first(where: { $0.value === peripheral })?.key {
                packetCharacteristics[peerId] = characteristic
            }
            if characteristic.uuid == profile.presenceCharacteristicUUID {
                peripheral.readValue(for: characteristic)
            }
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard error == nil,
              let data = characteristic.value,
              let presence = try? JSONDecoder().decode(PresenceAdvertisement.self, from: data),
              presence.peerId != identity?.peerId else {
            return
        }
        discoveredPeripherals[presence.peerId] = peripheral
        emit(.peerDiscovered(PeerProfile(
            id: presence.peerId,
            displayName: presence.displayName,
            fingerprint: presence.fingerprint,
            rssi: 0,
            lastSeen: Date(),
            state: .available,
            publicKeyData: presence.agreementPublicKey
        )))
    }
}

extension LocalWaveBluetoothTransport: CBPeripheralManagerDelegate {
    public func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        switch peripheral.state {
        case .poweredOn:
            startAdvertisingIfPossible()
        case .unauthorized:
            updateState(TransportState(permission: .denied, lastError: LocalWaveError.bluetoothPermissionDenied.localizedDescription))
        case .unsupported, .poweredOff:
            updateState(TransportState(permission: .unavailable, lastError: LocalWaveError.bluetoothUnavailable.localizedDescription))
        default:
            updateState(TransportState(permission: .unknown))
        }
    }

    public func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        guard error == nil else {
            updateState(TransportState(permission: .allowed, lastError: error?.localizedDescription))
            return
        }
        advertiseService()
    }

    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        guard let profile,
              request.characteristic.uuid == profile.presenceCharacteristicUUID,
              let data = presenceData(),
              request.offset <= data.count else {
            peripheral.respond(to: request, withResult: .attributeNotFound)
            return
        }
        request.value = data.subdata(in: request.offset..<data.count)
        peripheral.respond(to: request, withResult: .success)
    }

    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            guard let profile, let data = request.value else {
                peripheral.respond(to: request, withResult: .invalidAttributeValueLength)
                continue
            }
            if request.characteristic.uuid == profile.packetCharacteristicUUID || request.characteristic.uuid == profile.wakeCharacteristicUUID {
                emit(.packet(data, from: nil))
                peripheral.respond(to: request, withResult: .success)
            } else {
                peripheral.respond(to: request, withResult: .attributeNotFound)
            }
        }
    }
}
