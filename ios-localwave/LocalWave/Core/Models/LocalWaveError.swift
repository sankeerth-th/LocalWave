import Foundation

public enum LocalWaveError: LocalizedError, Equatable {
    case invalidChannelCode
    case displayNameRequired
    case bluetoothUnavailable
    case bluetoothPermissionDenied
    case peerUnavailable
    case oversizedMessage(limitBytes: Int)
    case identityUnavailable
    case encryptionFailed
    case decryptionFailed
    case replayDetected
    case malformedPacket
    case attachmentTooLarge(limitBytes: Int)
    case transferUnavailable(String)
    case fingerprintMismatch
    case unsupportedPackage
    case repositoryFailure(String)

    public var errorDescription: String? {
        switch self {
        case .invalidChannelCode:
            return "Enter a Frequency Code with at least 3 characters."
        case .displayNameRequired:
            return "Enter a display name before joining a channel."
        case .bluetoothUnavailable:
            return "Bluetooth is not available on this device."
        case .bluetoothPermissionDenied:
            return "Bluetooth permission is needed to discover nearby teammates."
        case .peerUnavailable:
            return "That teammate is not currently reachable."
        case .oversizedMessage(let limitBytes):
            return "Messages are limited to \(limitBytes / 1024) KB in this version."
        case .identityUnavailable:
            return "Local identity keys are unavailable."
        case .encryptionFailed:
            return "The message could not be encrypted."
        case .decryptionFailed:
            return "The message could not be decrypted."
        case .replayDetected:
            return "Duplicate encrypted packet rejected."
        case .malformedPacket:
            return "Received packet data is malformed."
        case .attachmentTooLarge(let limitBytes):
            return "Attachments are limited to \(limitBytes / (1024 * 1024)) MB for Bluetooth-safe sharing."
        case .transferUnavailable(let reason):
            return reason
        case .fingerprintMismatch:
            return "This teammate's identity fingerprint does not match."
        case .unsupportedPackage:
            return "This LocalWave package cannot be opened by this app version."
        case .repositoryFailure(let message):
            return message
        }
    }
}
