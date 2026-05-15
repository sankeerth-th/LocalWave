import Foundation

public actor PeerDiscoveryEngine {
    private var peers: [PeerID: PeerProfile] = [:]

    public init() {}

    public func update(_ profiles: [PeerProfile]) -> [PeerProfile] {
        for profile in profiles {
            peers[profile.id] = profile
        }
        return sortedPeers()
    }

    public func clear() -> [PeerProfile] {
        peers.removeAll()
        return []
    }

    public func sortedPeers() -> [PeerProfile] {
        peers.values.sorted { lhs, rhs in
            if lhs.state == rhs.state {
                return lhs.displayName.localizedCaseInsensitiveCompare(rhs.displayName) == .orderedAscending
            }
            return lhs.state == .available
        }
    }
}

