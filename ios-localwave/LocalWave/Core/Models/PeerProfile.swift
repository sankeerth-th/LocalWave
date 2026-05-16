import Foundation

public enum PresenceState: String, Codable, Sendable, CaseIterable {
    case available = "Available"
    case recentlySeen = "Recently Seen"
    case connecting = "Connecting"
    case sleeping = "Sleeping"
    case permissionNeeded = "Permission Needed"
}

public enum PeerTrustState: String, Codable, Sendable, CaseIterable {
    case unverified = "Unverified"
    case verified = "Verified"
    case changed = "Changed"
    case blocked = "Blocked"
}

public struct PeerProfile: Identifiable, Hashable, Codable, Sendable {
    public var id: PeerID
    public var displayName: String
    public var fingerprint: String
    public var rssi: Int
    public var lastSeen: Date
    public var state: PresenceState
    public var trustState: PeerTrustState
    public var publicKeyData: Data?

    private enum CodingKeys: String, CodingKey {
        case id
        case displayName
        case fingerprint
        case rssi
        case lastSeen
        case state
        case trustState
        case publicKeyData
    }

    public init(
        id: PeerID,
        displayName: String,
        fingerprint: String,
        rssi: Int,
        lastSeen: Date,
        state: PresenceState,
        trustState: PeerTrustState = .unverified,
        publicKeyData: Data? = nil
    ) {
        self.id = id
        self.displayName = displayName
        self.fingerprint = fingerprint
        self.rssi = rssi
        self.lastSeen = lastSeen
        self.state = state
        self.trustState = trustState
        self.publicKeyData = publicKeyData
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(PeerID.self, forKey: .id)
        displayName = try container.decode(String.self, forKey: .displayName)
        fingerprint = try container.decode(String.self, forKey: .fingerprint)
        rssi = try container.decode(Int.self, forKey: .rssi)
        lastSeen = try container.decode(Date.self, forKey: .lastSeen)
        state = try container.decode(PresenceState.self, forKey: .state)
        trustState = try container.decodeIfPresent(PeerTrustState.self, forKey: .trustState) ?? .unverified
        publicKeyData = try container.decodeIfPresent(Data.self, forKey: .publicKeyData)
    }
}
