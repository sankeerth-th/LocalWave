import Foundation
import Observation

@MainActor
@Observable
final class ChatListViewModel {
    var peers: [PeerProfile] = []
    var transportState = TransportState()

    private let environment: AppEnvironment
    private var peerTask: Task<Void, Never>?
    private var transportTask: Task<Void, Never>?

    init(environment: AppEnvironment) {
        self.environment = environment
    }

    deinit {
        peerTask?.cancel()
        transportTask?.cancel()
    }

    func start() {
        guard peerTask == nil, transportTask == nil else { return }

        peerTask = Task { [environment] in
            for await peers in environment.engine.observePeers() {
                await MainActor.run {
                    self.peers = peers.sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
                }
            }
        }

        transportTask = Task { [environment] in
            for await state in environment.engine.observeTransportState() {
                await MainActor.run {
                    self.transportState = state
                }
            }
        }
    }
}
