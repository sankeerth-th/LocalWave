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

    func testTransferRecordStorePersistsObservableTransferState() async throws {
        let directory = try temporaryDirectory()
        let store = TransferRecordStore(directory: directory)
        let transfer = TransferRecord(
            id: UUID(),
            peerId: "peer-1",
            fileName: "inventory.pdf",
            byteCount: 128_000,
            route: .l2cap,
            status: .waitingForPeer,
            updatedAt: Date(),
            failureReason: "Waiting for encrypted transfer receipt."
        )

        try await store.save([transfer])
        let reloaded = try await TransferRecordStore(directory: directory).load()

        XCTAssertEqual(reloaded.count, 1)
        XCTAssertEqual(reloaded.first?.id, transfer.id)
        XCTAssertEqual(reloaded.first?.peerId, transfer.peerId)
        XCTAssertEqual(reloaded.first?.fileName, transfer.fileName)
        XCTAssertEqual(reloaded.first?.byteCount, transfer.byteCount)
        XCTAssertEqual(reloaded.first?.route, transfer.route)
        XCTAssertEqual(reloaded.first?.status, transfer.status)
        XCTAssertEqual(reloaded.first?.failureReason, transfer.failureReason)
        XCTAssertEqual(reloaded.first?.updatedAt.timeIntervalSince1970 ?? 0, transfer.updatedAt.timeIntervalSince1970, accuracy: 0.001)
    }

    private func temporaryDirectory() throws -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("LocalWaveTests")
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
