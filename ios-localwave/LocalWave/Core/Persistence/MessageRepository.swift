import Foundation

public actor MessageRepository: MessageRepositoryProtocol {
    private let store: LocalStore
    private let fileName = "messages.json"

    public init(store: LocalStore = LocalStore()) {
        self.store = store
    }

    public func save(_ message: ChatMessage) async throws {
        var current = try await allMessages()
        current.append(message)
        try await store.save(current, fileName: fileName)
    }

    public func updateStatus(messageId: MessageID, status: MessageStatus) async throws {
        var current = try await allMessages()
        guard let index = current.firstIndex(where: { $0.id == messageId }) else { return }
        current[index].status = status
        try await store.save(current, fileName: fileName)
    }

    public func messages(peerId: PeerID) async throws -> [ChatMessage] {
        try await allMessages()
            .filter { $0.peerId == peerId }
            .sorted { $0.sentAt < $1.sentAt }
    }

    public func deleteAll(peerId: PeerID) async throws {
        let remaining = try await allMessages().filter { $0.peerId != peerId }
        try await store.save(remaining, fileName: fileName)
    }

    private func allMessages() async throws -> [ChatMessage] {
        try await store.load([ChatMessage].self, fileName: fileName, default: [])
    }
}

