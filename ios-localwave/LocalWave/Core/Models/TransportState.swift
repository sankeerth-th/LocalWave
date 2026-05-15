import Foundation

public enum BluetoothPermissionState: String, Codable, Sendable {
    case unknown
    case allowed
    case denied
    case unavailable
}

public struct TransportState: Equatable, Codable, Sendable {
    public var isRunning: Bool
    public var isScanning: Bool
    public var isAdvertising: Bool
    public var permission: BluetoothPermissionState
    public var lastError: String?

    public init(
        isRunning: Bool = false,
        isScanning: Bool = false,
        isAdvertising: Bool = false,
        permission: BluetoothPermissionState = .unknown,
        lastError: String? = nil
    ) {
        self.isRunning = isRunning
        self.isScanning = isScanning
        self.isAdvertising = isAdvertising
        self.permission = permission
        self.lastError = lastError
    }
}

