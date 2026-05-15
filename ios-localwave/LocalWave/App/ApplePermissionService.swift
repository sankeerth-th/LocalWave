import CoreBluetooth
import Foundation
import UserNotifications

final class ApplePermissionService: NSObject, @unchecked Sendable {
    private let queue = DispatchQueue(label: "com.localwave.permissions.bluetooth")
    private var centralManager: CBCentralManager?
    private var bluetoothContinuation: CheckedContinuation<BluetoothPermissionState, Never>?

    func requestBluetoothPermission() async -> BluetoothPermissionState {
        #if targetEnvironment(simulator)
        return .unavailable
        #else
        let authorization = Self.bluetoothPermissionState()
        if authorization != .unknown {
            return authorization
        }

        return await withCheckedContinuation { continuation in
            queue.async { [weak self] in
                guard let self else {
                    continuation.resume(returning: .unknown)
                    return
                }
                bluetoothContinuation = continuation
                centralManager = CBCentralManager(delegate: self, queue: queue)
            }
        }
        #endif
    }

    func requestNotificationPermission() async -> NotificationPermissionState {
        do {
            let allowed = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
            return allowed ? .allowed : .denied
        } catch {
            return .denied
        }
    }

    func snapshot() async -> PermissionSnapshot {
        let notificationSettings = await UNUserNotificationCenter.current().notificationSettings()
        let notifications: NotificationPermissionState
        switch notificationSettings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            notifications = .allowed
        case .denied:
            notifications = .denied
        case .notDetermined:
            notifications = .unknown
        @unknown default:
            notifications = .unknown
        }

        return PermissionSnapshot(
            bluetooth: Self.bluetoothPermissionState(),
            notifications: notifications
        )
    }

    private static func bluetoothPermissionState() -> BluetoothPermissionState {
        #if targetEnvironment(simulator)
        return .unavailable
        #else
        switch CBManager.authorization {
        case .allowedAlways:
            return .allowed
        case .denied:
            return .denied
        case .restricted:
            return .unavailable
        case .notDetermined:
            return .unknown
        @unknown default:
            return .unknown
        }
        #endif
    }

    private func finishBluetoothRequest(with state: BluetoothPermissionState) {
        guard let continuation = bluetoothContinuation else { return }
        bluetoothContinuation = nil
        continuation.resume(returning: state)
    }
}

extension ApplePermissionService: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        let state: BluetoothPermissionState
        switch central.state {
        case .poweredOn:
            state = .allowed
        case .unauthorized:
            state = .denied
        case .unsupported, .poweredOff:
            state = .unavailable
        case .unknown, .resetting:
            state = Self.bluetoothPermissionState()
        @unknown default:
            state = .unknown
        }
        finishBluetoothRequest(with: state)
    }
}
