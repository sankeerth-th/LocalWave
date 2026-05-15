import XCTest
@testable import LocalWave

@MainActor
final class PeopleViewModelTests: XCTestCase {
    func testObservesMockPeersAndTransportState() async throws {
        let engine = MockLocalWaveEngine(peers: [], sendDelayNanoseconds: 0)
        let environment = AppEnvironment(engine: engine, mode: .mock)
        let channel = try ChannelCode("DOCK-A-17")

        let viewModel = PeopleViewModel(environment: environment, channel: channel, displayName: "Maya")
        viewModel.start()

        let peer = PeerProfile(
            id: "peer-relay",
            displayName: "Relay Lead",
            fingerprint: "ABCD 1234",
            rssi: -54,
            lastSeen: Date(),
            state: .available
        )
        engine.setTransportState(TransportState(isRunning: true, isScanning: true, isAdvertising: true, permission: .allowed))
        engine.replacePeers([peer])

        await waitUntil {
            viewModel.peers.map(\.id) == ["peer-relay"]
        }
        XCTAssertEqual(viewModel.transportState.permission, .allowed)
        XCTAssertEqual(viewModel.scanningStatusText, "Scanning nearby...")
    }

    func testPermissionNeededStatusAndWakeState() async throws {
        let engine = MockLocalWaveEngine()
        let environment = AppEnvironment(engine: engine, mode: .mock)
        let channel = try ChannelCode("DOCK-A-17")
        try await engine.start(channel: channel, displayName: "Maya")
        engine.simulatePermissionDenied()

        let viewModel = PeopleViewModel(environment: environment, channel: channel, displayName: "Maya")
        viewModel.start()
        try await Task.sleep(nanoseconds: 150_000_000)

        XCTAssertEqual(viewModel.scanningStatusText, "Bluetooth permission needed")
        XCTAssertEqual(viewModel.wakeState(for: PreviewData.peers[0]), .permissionNeeded)
    }

    func testWakeTransitionsToSent() async throws {
        let engine = MockLocalWaveEngine()
        let environment = AppEnvironment(engine: engine, mode: .mock)
        let channel = try ChannelCode("DOCK-A-17")
        try await engine.start(channel: channel, displayName: "Maya")
        let peer = PreviewData.peers[0]

        let viewModel = PeopleViewModel(environment: environment, channel: channel, displayName: "Maya")
        viewModel.start()
        await waitUntil {
            viewModel.transportState.permission == .allowed
        }
        await viewModel.sendWake(to: peer)

        XCTAssertTrue(viewModel.wakeStateByPeer[peer.id] == .sent || viewModel.wakeStateByPeer[peer.id] == .idle)
    }

    func testWakeUnavailableForSleepingPeer() async throws {
        var peer = PreviewData.peers[0]
        peer.state = .sleeping
        let engine = MockLocalWaveEngine(peers: [peer], sendDelayNanoseconds: 0)
        let channel = try ChannelCode("DOCK-A-17")
        let viewModel = PeopleViewModel(environment: AppEnvironment(engine: engine, mode: .mock), channel: channel, displayName: "Maya")
        viewModel.start()
        await waitUntil {
            viewModel.transportState.permission == .allowed
        }

        XCTAssertEqual(viewModel.wakeState(for: peer), .unavailable)
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
