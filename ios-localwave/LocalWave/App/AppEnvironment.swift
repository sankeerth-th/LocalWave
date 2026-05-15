import Foundation

public enum LocalWaveAppMode: String, Sendable {
    case mock = "Mock Engine"
    case real = "Real Engine"
}

public enum NotificationPermissionState: String, Sendable {
    case unknown = "Unknown"
    case allowed = "Allowed"
    case denied = "Denied"
}

public struct PermissionSnapshot: Equatable, Sendable {
    public var bluetooth: BluetoothPermissionState
    public var notifications: NotificationPermissionState

    public init(
        bluetooth: BluetoothPermissionState = .unknown,
        notifications: NotificationPermissionState = .unknown
    ) {
        self.bluetooth = bluetooth
        self.notifications = notifications
    }
}

public struct AppEnvironment: Sendable {
    public var engine: LocalWaveEngineProtocol
    public var mode: LocalWaveAppMode
    public var requestBluetoothPermission: @Sendable () async -> BluetoothPermissionState
    public var requestNotificationPermission: @Sendable () async -> NotificationPermissionState
    public var permissionSnapshot: @Sendable () async -> PermissionSnapshot

    public init(
        engine: LocalWaveEngineProtocol,
        mode: LocalWaveAppMode,
        requestBluetoothPermission: @escaping @Sendable () async -> BluetoothPermissionState = { .unknown },
        requestNotificationPermission: @escaping @Sendable () async -> NotificationPermissionState = { .unknown },
        permissionSnapshot: @escaping @Sendable () async -> PermissionSnapshot = { PermissionSnapshot() }
    ) {
        self.engine = engine
        self.mode = mode
        self.requestBluetoothPermission = requestBluetoothPermission
        self.requestNotificationPermission = requestNotificationPermission
        self.permissionSnapshot = permissionSnapshot
    }

    public static var mock: AppEnvironment {
        let engine = MockLocalWaveEngine()
        return AppEnvironment(
            engine: engine,
            mode: .mock,
            requestBluetoothPermission: { .allowed },
            requestNotificationPermission: { .allowed },
            permissionSnapshot: {
                PermissionSnapshot(bluetooth: .allowed, notifications: .allowed)
            }
        )
    }

    public static var live: AppEnvironment {
        #if targetEnvironment(simulator)
        return .mock
        #else
        return AppEnvironment(
            engine: LocalWaveEngine(),
            mode: .real,
            requestBluetoothPermission: { .unknown },
            requestNotificationPermission: { .unknown },
            permissionSnapshot: {
                PermissionSnapshot(bluetooth: .unknown, notifications: .unknown)
            }
        )
        #endif
    }
}
