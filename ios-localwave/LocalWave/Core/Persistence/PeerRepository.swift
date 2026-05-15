import Foundation

public actor PeerRepository: PeerRepositoryProtocol {
    private let store: LocalStore
    private let fileName = "peers.json"

    public init(store: LocalStore = LocalStore()) {
        self.store = store
    }

    public func upsert(_ peer: PeerProfile) async throws {
        var current = try await peersById()
        current[peer.id] = peer
        try await store.save(current, fileName: fileName)
    }

    public func peers() async throws -> [PeerProfile] {
        try await peersById().values.sorted { $0.displayName < $1.displayName }
    }

    public func peer(id: PeerID) async throws -> PeerProfile? {
        try await peersById()[id]
    }

    public func clearRecentlySeen() async throws {
        try await store.save([PeerID: PeerProfile](), fileName: fileName)
    }

    private func peersById() async throws -> [PeerID: PeerProfile] {
        try await store.load([PeerID: PeerProfile].self, fileName: fileName, default: [:])
    }
}

