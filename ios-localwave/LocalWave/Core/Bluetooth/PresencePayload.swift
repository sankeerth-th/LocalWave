import Foundation

public struct PresencePayload: Codable, Sendable, Equatable {
    public var peerId: PeerID
    public var displayName: String
    public var fingerprint: String
    public var agreementPublicKey: Data
    public var signingPublicKey: Data
    public var timestamp: Date

    public init(
        peerId: PeerID,
        displayName: String,
        fingerprint: String,
        agreementPublicKey: Data,
        signingPublicKey: Data,
        timestamp: Date = Date()
    ) {
        self.peerId = peerId
        self.displayName = displayName
        self.fingerprint = fingerprint
        self.agreementPublicKey = agreementPublicKey
        self.signingPublicKey = signingPublicKey
        self.timestamp = timestamp
    }
}
