import Foundation

public struct LocalIdentity: Codable, Sendable, Equatable {
    public var peerId: PeerID
    public var displayName: String
    public var agreementPublicKey: Data
    public var signingPublicKey: Data
    public var fingerprint: String

    public init(
        peerId: PeerID,
        displayName: String,
        agreementPublicKey: Data,
        signingPublicKey: Data,
        fingerprint: String
    ) {
        self.peerId = peerId
        self.displayName = displayName
        self.agreementPublicKey = agreementPublicKey
        self.signingPublicKey = signingPublicKey
        self.fingerprint = fingerprint
    }
}

