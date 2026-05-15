import XCTest
@testable import LocalWave

@MainActor
final class ChatViewModelTests: XCTestCase {
    func testSendDisabledForEmptyDraft() {
        let viewModel = ChatViewModel(environment: .mock, peer: PreviewData.peers[0])
        viewModel.draft = "   "

        XCTAssertFalse(viewModel.canSend)
    }

    func testSendDisabledForUnreachablePeer() {
        var peer = PreviewData.peers[0]
        peer.state = .recentlySeen
        let viewModel = ChatViewModel(environment: .mock, peer: peer)
        viewModel.draft = "Checking in"

        XCTAssertFalse(viewModel.canSend)
    }

    func testSendMessageMovesThroughMockStatusUpdates() async throws {
        let peer = PreviewData.peers[0]
        let engine = MockLocalWaveEngine(peers: [peer], sendDelayNanoseconds: 120_000_000)
        let environment = AppEnvironment(engine: engine, mode: .mock)
        let viewModel = ChatViewModel(environment: environment, peer: peer)
        viewModel.start()

        viewModel.draft = "Status check"
        await viewModel.sendDraft()

        await waitUntil {
            viewModel.messages.contains { $0.text == "Status check" && $0.status == .pending }
        }
        await waitUntil {
            viewModel.messages.contains { $0.text == "Status check" && ($0.status == .sent || $0.status == .delivered) }
        }
    }

    func testRetryFailedMessageSendsAgain() async throws {
        let engine = MockLocalWaveEngine()
        let environment = AppEnvironment(engine: engine, mode: .mock)
        let channel = try ChannelCode("DOCK-A-17")
        try await engine.start(channel: channel, displayName: "Maya")
        let peer = PreviewData.peers[0]
        let viewModel = ChatViewModel(environment: environment, peer: peer)

        let failed = ChatMessage(peerId: peer.id, text: "Retry me", direction: .outgoing, status: .failed)
        await viewModel.retry(failed)
        try await Task.sleep(nanoseconds: 1_300_000_000)

        XCTAssertNil(viewModel.errorMessage)
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 1_500_000_000,
        condition: @escaping @MainActor () -> Bool,
        file: StaticString = #filePath,
        line: UInt = #line
    ) async {
        let deadline = Date().addingTimeInterval(Double(timeoutNanoseconds) / 1_000_000_000)
        while Date() < deadline {
            if condition() {
                return
            }
            try? await Task.sleep(nanoseconds: 25_000_000)
        }
        XCTFail("Timed out waiting for condition.", file: file, line: line)
    }
}
