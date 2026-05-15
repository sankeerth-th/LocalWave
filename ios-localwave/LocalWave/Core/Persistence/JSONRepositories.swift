import Foundation

public actor JSONMessageRepository: MessageRepositoryProtocol {
    private let fileURL: URL
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    public init(directory: URL? = nil) {
        let root = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("LocalWave", isDirectory: true)
        self.fileURL = root.appendingPathComponent("messages.json")
        self.encoder = JSONMessageRepository.makeEncoder()
        self.decoder = JSONMessageRepository.makeDecoder()
    }

    public func save(_ message: ChatMessage) async throws {
        var messages = try loadMessages()
        if let index = messages.firstIndex(where: { $0.id == message.id }) {
            messages[index] = message
        } else {
            messages.append(message)
        }
        try saveMessages(messages)
    }

    public func updateStatus(messageId: MessageID, status: MessageStatus) async throws {
        var messages = try loadMessages()
        guard let index = messages.firstIndex(where: { $0.id == messageId }) else {
            return
        }
        messages[index].status = status
        try saveMessages(messages)
    }

    public func messages(peerId: PeerID) async throws -> [ChatMessage] {
        try loadMessages()
            .filter { $0.peerId == peerId }
            .sorted { $0.sentAt < $1.sentAt }
    }

    public func deleteAll(peerId: PeerID) async throws {
        let messages = try loadMessages().filter { $0.peerId != peerId }
        try saveMessages(messages)
    }

    private func loadMessages() throws -> [ChatMessage] {
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            return []
        }
        do {
            let data = try Data(contentsOf: fileURL)
            return try decoder.decode([ChatMessage].self, from: data)
        } catch {
            throw LocalWaveError.repositoryFailure("Unable to read local messages.")
        }
    }

    private func saveMessages(_ messages: [ChatMessage]) throws {
        do {
            try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            let data = try encoder.encode(messages)
            try data.write(to: fileURL, options: [.atomic])
        } catch {
            throw LocalWaveError.repositoryFailure("Unable to save local messages.")
        }
    }

    private static func makeEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys, .prettyPrinted]
        return encoder
    }

    private static func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}

public actor JSONPeerRepository: PeerRepositoryProtocol {
    private let fileURL: URL
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    public init(directory: URL? = nil) {
        let root = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("LocalWave", isDirectory: true)
        self.fileURL = root.appendingPathComponent("peers.json")
        self.encoder = JSONPeerRepository.makeEncoder()
        self.decoder = JSONPeerRepository.makeDecoder()
    }

    public func upsert(_ peer: PeerProfile) async throws {
        var peers = try loadPeers()
        if let index = peers.firstIndex(where: { $0.id == peer.id }) {
            peers[index] = peer
        } else {
            peers.append(peer)
        }
        try savePeers(peers)
    }

    public func peers() async throws -> [PeerProfile] {
        try loadPeers().sorted { $0.displayName.localizedStandardCompare($1.displayName) == .orderedAscending }
    }

    public func peer(id: PeerID) async throws -> PeerProfile? {
        try loadPeers().first { $0.id == id }
    }

    public func clearRecentlySeen() async throws {
        let peers = try loadPeers().map { peer in
            var updated = peer
            if updated.state != .permissionNeeded {
                updated.state = .recentlySeen
            }
            return updated
        }
        try savePeers(peers)
    }

    private func loadPeers() throws -> [PeerProfile] {
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            return []
        }
        do {
            let data = try Data(contentsOf: fileURL)
            return try decoder.decode([PeerProfile].self, from: data)
        } catch {
            throw LocalWaveError.repositoryFailure("Unable to read local peers.")
        }
    }

    private func savePeers(_ peers: [PeerProfile]) throws {
        do {
            try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            let data = try encoder.encode(peers)
            try data.write(to: fileURL, options: [.atomic])
        } catch {
            throw LocalWaveError.repositoryFailure("Unable to save local peers.")
        }
    }

    private static func makeEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys, .prettyPrinted]
        return encoder
    }

    private static func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}
