import XCTest
@testable import LocalWave

final class RepositoryTests: XCTestCase {
    func testMessageRepositoryPersistsAndUpdatesMessages() async throws {
        let directory = try temporaryDirectory()
        let repository = JSONMessageRepository(directory: directory)
        let peerId = "peer-1"
        let first = ChatMessage(peerId: peerId, text: "One", direction: .outgoing, status: .pending)
        let second = ChatMessage(peerId: peerId, text: "Two", sentAt: Date().addingTimeInterval(1), direction: .incoming, status: .delivered)

        try await repository.save(second)
        try await repository.save(first)
        try await repository.updateStatus(messageId: first.id, status: .sent)

        let reloaded = JSONMessageRepository(directory: directory)
        let messages = try await reloaded.messages(peerId: peerId)

        XCTAssertEqual(messages.map(\.id), [first.id, second.id])
        XCTAssertEqual(messages.first?.status, .sent)
    }

    func testPeerRepositoryPersistsPeersAndMarksRecentlySeen() async throws {
        let directory = try temporaryDirectory()
        let repository = JSONPeerRepository(directory: directory)
        let peer = PeerProfile(id: "peer-1", displayName: "Desk One", fingerprint: "fp", rssi: -61, lastSeen: Date(), state: .available)

        try await repository.upsert(peer)
        try await repository.clearRecentlySeen()

        let reloaded = JSONPeerRepository(directory: directory)
        let peers = try await reloaded.peers()

        XCTAssertEqual(peers.count, 1)
        XCTAssertEqual(peers.first?.id, peer.id)
        XCTAssertEqual(peers.first?.state, .recentlySeen)
    }

    private func temporaryDirectory() throws -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("LocalWaveTests")
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
