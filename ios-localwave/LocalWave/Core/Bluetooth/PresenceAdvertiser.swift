import Foundation

public struct PresenceAdvertiser: Sendable {
    public init() {}

    public func payload(identity: LocalIdentity) -> PresencePayload {
        PresencePayload(
            peerId: identity.peerId,
            displayName: identity.displayName,
            fingerprint: identity.fingerprint,
            agreementPublicKey: identity.agreementPublicKey,
            signingPublicKey: identity.signingPublicKey,
            timestamp: Date()
        )
    }
}
