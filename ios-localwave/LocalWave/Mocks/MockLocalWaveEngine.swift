import Foundation

public final class MockLocalWaveEngine: LocalWaveEngineProtocol, @unchecked Sendable {
    private let lock = NSLock()
    private var peerContinuations: [UUID: AsyncStream<[PeerProfile]>.Continuation] = [:]
    private var transportContinuations: [UUID: AsyncStream<TransportState>.Continuation] = [:]
    private var messageContinuations: [PeerID: [UUID: AsyncStream<[ChatMessage]>.Continuation]] = [:]
    private var messagesByPeer: [PeerID: [ChatMessage]] = [:]
    private var peers: [PeerProfile] = []
    private var transportState = TransportState()
    private var simulationTask: Task<Void, Never>?
    private var identity = PreviewData.identity
    private var messageSendDelayNanoseconds: UInt64 = 500_000_000
    private let seedSampleData: Bool

    public init(seedSampleData: Bool = true) {
        self.seedSampleData = seedSampleData
    }

    public convenience init(
        peers: [PeerProfile],
        messagesByPeer: [PeerID: [ChatMessage]] = [:],
        transportState: TransportState = TransportState(permission: .allowed),
        sendDelayNanoseconds: UInt64 = 500_000_000
    ) {
        self.init(seedSampleData: false)
        lock.withLock {
            self.peers = peers
            self.messagesByPeer = messagesByPeer
            self.transportState = transportState
            self.messageSendDelayNanoseconds = sendDelayNanoseconds
        }
    }

    public func start(channel: ChannelCode, displayName: String) async throws {
        lock.withLock {
            identity.displayName = displayName
            transportState = TransportState(isRunning: true, isScanning: true, isAdvertising: true, permission: .allowed)
            if seedSampleData {
                peers = PreviewData.peers
                for peer in peers where messagesByPeer[peer.id] == nil {
                    messagesByPeer[peer.id] = PreviewData.messages(peerId: peer.id)
                }
            }
        }
        broadcastTransport()
        broadcastPeers()
        startSimulation()
    }

    public func stop() async {
        simulationTask?.cancel()
        simulationTask = nil
        lock.withLock {
            transportState = TransportState(isRunning: false, isScanning: false, isAdvertising: false, permission: .allowed)
        }
        broadcastTransport()
    }

    public func updateDisplayName(_ displayName: String) async {
        lock.withLock {
            identity.displayName = displayName
        }
    }

    public func switchChannel(_ channel: ChannelCode) async throws {
        lock.withLock {
            peers = []
            messagesByPeer = [:]
            transportState = TransportState(isRunning: true, isScanning: true, isAdvertising: true, permission: .allowed)
        }
        broadcastPeers()
        broadcastTransport()

        try await Task.sleep(nanoseconds: 600_000_000)
        lock.withLock {
            if seedSampleData {
                peers = PreviewData.peers
                for peer in peers {
                    messagesByPeer[peer.id] = PreviewData.messages(peerId: peer.id)
                }
            }
        }
        broadcastPeers()
        broadcastAllMessages()
    }

    public func sendMessage(text: String, to peerId: PeerID) async throws -> MessageID {
        let permission = lock.withLock { transportState.permission }
        guard permission == .allowed else {
            throw permission == .denied ? LocalWaveError.bluetoothPermissionDenied : LocalWaveError.bluetoothUnavailable
        }
        let peer = lock.withLock { peers.first { $0.id == peerId } }
        guard peer?.state == .available || peer?.state == .connecting else {
            appendMessage(ChatMessage(peerId: peerId, text: text, direction: .outgoing, status: .failed))
            throw LocalWaveError.peerUnavailable
        }

        let id = MessageID()
        appendMessage(ChatMessage(id: id, peerId: peerId, text: text, direction: .outgoing, status: .pending))

        Task {
            try? await Task.sleep(nanoseconds: self.messageSendDelayNanoseconds)
            self.updateMessage(id: id, peerId: peerId, status: .sent)
            try? await Task.sleep(nanoseconds: 650_000_000)
            self.updateMessage(id: id, peerId: peerId, status: .delivered)
        }

        return id
    }

    public func sendWake(to peerId: PeerID) async throws {
        try await Task.sleep(nanoseconds: 650_000_000)
        let permission = lock.withLock { transportState.permission }
        guard permission == .allowed else {
            throw permission == .denied ? LocalWaveError.bluetoothPermissionDenied : LocalWaveError.bluetoothUnavailable
        }
        let peer = lock.withLock { peers.first { $0.id == peerId } }
        guard peer?.state == .available || peer?.state == .connecting else {
            throw LocalWaveError.peerUnavailable
        }
    }

    public func observePeers() -> AsyncStream<[PeerProfile]> {
        AsyncStream { continuation in
            let id = UUID()
            let snapshot = lock.withLock { () -> [PeerProfile] in
                peerContinuations[id] = continuation
                return peers
            }
            continuation.yield(snapshot)
            continuation.onTermination = { [weak self] _ in
                self?.lock.withLock {
                    self?.peerContinuations.removeValue(forKey: id)
                }
            }
        }
    }

    public func observeMessages(peerId: PeerID) -> AsyncStream<[ChatMessage]> {
        AsyncStream { continuation in
            let id = UUID()
            let snapshot = lock.withLock { () -> [ChatMessage] in
                messageContinuations[peerId, default: [:]][id] = continuation
                return messagesByPeer[peerId] ?? []
            }
            continuation.yield(snapshot)
            continuation.onTermination = { [weak self] _ in
                self?.lock.withLock {
                    self?.messageContinuations[peerId]?.removeValue(forKey: id)
                }
            }
        }
    }

    public func observeTransportState() -> AsyncStream<TransportState> {
        AsyncStream { continuation in
            let id = UUID()
            let snapshot = lock.withLock { () -> TransportState in
                transportContinuations[id] = continuation
                return transportState
            }
            continuation.yield(snapshot)
            continuation.onTermination = { [weak self] _ in
                self?.lock.withLock {
                    self?.transportContinuations.removeValue(forKey: id)
                }
            }
        }
    }

    public func localIdentity() async throws -> LocalIdentity {
        lock.withLock { identity }
    }

    public func simulatePermissionDenied() {
        lock.withLock {
            transportState.permission = .denied
            transportState.isScanning = false
            transportState.lastError = "Bluetooth permission denied"
        }
        broadcastTransport()
    }

    public func replacePeers(_ peers: [PeerProfile]) {
        lock.withLock {
            self.peers = peers
        }
        broadcastPeers()
    }

    public func setTransportState(_ state: TransportState) {
        lock.withLock {
            transportState = state
        }
        broadcastTransport()
    }

    private func startSimulation() {
        simulationTask?.cancel()
        simulationTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 4_000_000_000)
                self?.togglePeerState()
            }
        }
    }

    private func togglePeerState() {
        lock.withLock {
            guard !peers.isEmpty else { return }
            let index = Int.random(in: 0..<peers.count)
            peers[index].lastSeen = Date()
            peers[index].rssi = Int.random(in: -82 ... -38)
            peers[index].state = peers[index].state == .available ? .recentlySeen : .available
        }
        broadcastPeers()
    }

    private func appendMessage(_ message: ChatMessage) {
        lock.withLock {
            messagesByPeer[message.peerId, default: []].append(message)
        }
        broadcastMessages(peerId: message.peerId)
    }

    private func updateMessage(id: MessageID, peerId: PeerID, status: MessageStatus) {
        lock.withLock {
            guard let index = messagesByPeer[peerId]?.firstIndex(where: { $0.id == id }) else { return }
            messagesByPeer[peerId]?[index].status = status
        }
        broadcastMessages(peerId: peerId)
    }

    private func broadcastPeers() {
        let snapshot = lock.withLock { peers }
        let continuations = lock.withLock { Array(peerContinuations.values) }
        continuations.forEach { $0.yield(snapshot) }
    }

    private func broadcastTransport() {
        let snapshot = lock.withLock { transportState }
        let continuations = lock.withLock { Array(transportContinuations.values) }
        continuations.forEach { $0.yield(snapshot) }
    }

    private func broadcastMessages(peerId: PeerID) {
        let snapshot = lock.withLock { messagesByPeer[peerId] ?? [] }
        let continuations = lock.withLock { () -> [AsyncStream<[ChatMessage]>.Continuation] in
            guard let values = messageContinuations[peerId]?.values else { return [] }
            return Array(values)
        }
        continuations.forEach { $0.yield(snapshot) }
    }

    private func broadcastAllMessages() {
        let peerIDs = lock.withLock { Array(messagesByPeer.keys) }
        peerIDs.forEach { broadcastMessages(peerId: $0) }
    }
}

private extension NSLock {
    @discardableResult
    func withLock<T>(_ body: () -> T) -> T {
        lock()
        defer { unlock() }
        return body()
    }
}
