import Foundation

public enum CryptoError: Error, Equatable {
    case missingPeerPublicKey
    case invalidPeerPublicKey
    case invalidSealedBox
    case replayDetected
    case keychainFailure(OSStatus)
}

