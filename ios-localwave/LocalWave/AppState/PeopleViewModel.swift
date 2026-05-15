import Foundation

@MainActor
public final class PeopleViewModel: ObservableObject {
    @Published public private(set) var peers: [PeerProfile] = []
    @Published public private(set) var transportState = TransportState()
    @Published public private(set) var wakeStateByPeer: [PeerID: WakeButtonState] = [:]
    @Published public private(set) var wakeError: String?

    public let environment: AppEnvironment
    public let channel: ChannelCode
    public let displayName: String

    private var peerTask: Task<Void, Never>?
    private var transportTask: Task<Void, Never>?

    public init(environment: AppEnvironment, channel: ChannelCode, displayName: String) {
        self.environment = environment
        self.channel = channel
        self.displayName = displayName
    }

    deinit {
        peerTask?.cancel()
        transportTask?.cancel()
    }

    public var scanningStatusText: String {
        switch transportState.permission {
        case .denied, .unavailable:
            return "Bluetooth permission needed"
        case .unknown:
            return "Checking Bluetooth..."
        case .allowed:
            if transportState.isScanning {
                return "Scanning nearby..."
            }
            return peers.isEmpty ? "No one nearby yet." : "Offline local mode active"
        }
    }

    public var needsBluetoothBanner: Bool {
        transportState.permission == .denied || transportState.permission == .unavailable || transportState.permission == .unknown
    }

    public func start() {
        guard peerTask == nil, transportTask == nil else { return }

        peerTask = Task { [environment] in
            for await peers in environment.engine.observePeers() {
                if Task.isCancelled { return }
                self.peers = peers.sorted { lhs, rhs in
                    if lhs.state == rhs.state {
                        return lhs.displayName.localizedCaseInsensitiveCompare(rhs.displayName) == .orderedAscending
                    }
                    return lhs.state.sortOrder < rhs.state.sortOrder
                }
            }
        }

        transportTask = Task { [environment] in
            for await state in environment.engine.observeTransportState() {
                if Task.isCancelled { return }
                self.transportState = state
            }
        }
    }

    public func stop() {
        peerTask?.cancel()
        transportTask?.cancel()
        peerTask = nil
        transportTask = nil
    }

    public func wakeState(for peer: PeerProfile) -> WakeButtonState {
        if peer.state != .available && peer.state != .connecting {
            return .unavailable
        }
        if transportState.permission != .allowed {
            return .permissionNeeded
        }
        return wakeStateByPeer[peer.id] ?? .idle
    }

    public func sendWake(to peer: PeerProfile) async {
        let state = wakeState(for: peer)
        guard state == .idle || state == .failed else { return }

        wakeError = nil
        wakeStateByPeer[peer.id] = .sending
        do {
            try await environment.engine.sendWake(to: peer.id)
            wakeStateByPeer[peer.id] = .sent
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            if !Task.isCancelled, wakeStateByPeer[peer.id] == .sent {
                wakeStateByPeer[peer.id] = .idle
            }
        } catch {
            wakeStateByPeer[peer.id] = .failed
            wakeError = "Wake could not be sent. This person may not be reachable right now."
        }
    }
}

private extension PresenceState {
    var sortOrder: Int {
        switch self {
        case .available: return 0
        case .connecting: return 1
        case .recentlySeen: return 2
        case .sleeping: return 3
        case .permissionNeeded: return 4
        }
    }
}
