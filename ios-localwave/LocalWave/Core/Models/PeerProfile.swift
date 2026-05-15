import Foundation

public enum PresenceState: String, Codable, Sendable, CaseIterable {
    case available = "Available"
    case recentlySeen = "Recently Seen"
    case connecting = "Connecting"
    case sleeping = "Sleeping"
    case permissionNeeded = "Permission Needed"
}

public struct PeerProfile: Identifiable, Hashable, Codable, Sendable {
    public var id: PeerID
    public var displayName: String
    public var fingerprint: String
    public var rssi: Int
    public var lastSeen: Date
    public var state: PresenceState
    public var publicKeyData: Data?

    public init(
        id: PeerID,
        displayName: String,
        fingerprint: String,
        rssi: Int,
        lastSeen: Date,
        state: PresenceState,
        publicKeyData: Data? = nil
    ) {
        self.id = id
        self.displayName = displayName
        self.fingerprint = fingerprint
        self.rssi = rssi
        self.lastSeen = lastSeen
        self.state = state
        self.publicKeyData = publicKeyData
    }
}

