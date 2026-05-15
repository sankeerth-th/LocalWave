import Foundation

public final class LocalWaveEngine: LocalWaveEngineProtocol, @unchecked Sendable {
    private let identityStore: IdentityStoreProtocol
    private let crypto: SessionCrypto
    private let peerRepository: PeerRepositoryProtocol
    private let messageRepository: MessageRepositoryProtocol
    private let notificationService: LocalNotificationServicing
    private let transport: BluetoothTransportProtocol
    private let discovery = PeerDiscoveryEngine()
    private let framer = BLEPacketFramer()
    private let reassembler = BLEPacketReassembler()

    private var channel: ChannelCode?
    private var displayName: String = ""
    private var peers: [PeerProfile] = []
    private var transportState = TransportState()
    private var peerContinuations: [AsyncStream<[PeerProfile]>.Continuation] = []
    private var stateContinuations: [AsyncStream<TransportState>.Continuation] = []
    private var messageContinuations: [PeerID: [AsyncStream<[ChatMessage]>.Continuation]] = [:]
    private var replayCounter: UInt64 = 1
    private var eventTask: Task<Void, Never>?

    public init(
        identityStore: IdentityStoreProtocol = KeychainIdentityStore(),
        peerRepository: PeerRepositoryProtocol = JSONPeerRepository(),
        messageRepository: MessageRepositoryProtocol = JSONMessageRepository(),
        notificationService: LocalNotificationServicing = LocalNotificationService(),
        transport: BluetoothTransportProtocol = LocalWaveBluetoothTransport()
    ) {
        self.identityStore = identityStore
        self.crypto = SessionCrypto(identityStore: identityStore)
        self.peerRepository = peerRepository
        self.messageRepository = messageRepository
        self.notificationService = notificationService
        self.transport = transport
    }

    public func start(channel: ChannelCode, displayName: String) async throws {
        let trimmed = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw LocalWaveError.displayNameRequired }
        self.channel = channel
        self.displayName = trimmed
        let identity = try await identityStore.loadOrCreateIdentity(displayName: trimmed)
        try await transport.start(channel: channel, identity: identity)
        observeTransportEvents()
        peers = try await peerRepository.peers()
        publishPeers()
    }

    public func stop() async {
        await transport.stop()
        eventTask?.cancel()
        eventTask = nil
        transportState = TransportState()
        publishState()
    }

    public func updateDisplayName(_ displayName: String) async {
        self.displayName = displayName
        try? await identityStore.updateDisplayName(displayName)
    }

    public func switchChannel(_ channel: ChannelCode) async throws {
        await stop()
        peers = await discovery.clear()
        try await peerRepository.clearRecentlySeen()
        publishPeers()
        try await start(channel: channel, displayName: displayName)
    }

    public func sendMessage(text: String, to peerId: PeerID) async throws -> MessageID {
        guard let channel else { throw LocalWaveError.invalidChannelCode }
        let knownPeer = peers.first { $0.id == peerId }
        let storedPeer = try await peerRepository.peer(id: peerId)
        guard let peer = knownPeer ?? storedPeer else {
            throw LocalWaveError.peerUnavailable
        }
        let message = ChatMessage(peerId: peerId, text: text, direction: .outgoing, status: .pending)
        try await messageRepository.save(message)
        await publishMessages(peerId: peerId)

        let envelope = try await crypto.encryptMessage(text, to: peer, counter: nextCounter(), channel: channel)
        let body = try SecureEnvelopeCodec.encode(envelope)
        try await transport.send(body, kind: .message, to: peerId)

        try await messageRepository.updateStatus(messageId: message.id, status: .sent)
        await publishMessages(peerId: peerId)
        return message.id
    }

    public func sendWake(to peerId: PeerID) async throws {
        guard let channel else { throw LocalWaveError.invalidChannelCode }
        let knownPeer = peers.first { $0.id == peerId }
        let storedPeer = try await peerRepository.peer(id: peerId)
        guard let peer = knownPeer ?? storedPeer else {
            throw LocalWaveError.peerUnavailable
        }
        let wake = try await crypto.encryptWake(to: peer, counter: nextCounter(), channel: channel)
        let body = try SecureEnvelopeCodec.encode(wake)
        try await transport.send(body, kind: .wake, to: peerId)
    }

    public func observePeers() -> AsyncStream<[PeerProfile]> {
        AsyncStream { continuation in
            peerContinuations.append(continuation)
            continuation.yield(peers)
        }
    }

    public func observeMessages(peerId: PeerID) -> AsyncStream<[ChatMessage]> {
        AsyncStream { continuation in
            messageContinuations[peerId, default: []].append(continuation)
            Task { await self.publishMessages(peerId: peerId) }
        }
    }

    public func observeTransportState() -> AsyncStream<TransportState> {
        AsyncStream { continuation in
            stateContinuations.append(continuation)
            continuation.yield(transportState)
        }
    }

    public func localIdentity() async throws -> LocalIdentity {
        try await identityStore.loadOrCreateIdentity(displayName: displayName.isEmpty ? "Local User" : displayName)
    }

    private func observeTransportEvents() {
        eventTask?.cancel()
        eventTask = Task { [weak self] in
            guard let self else { return }
            for await event in transport.observeEvents() {
                await self.handle(event)
            }
        }
    }

    private func handle(_ event: BluetoothTransportEvent) async {
        switch event {
        case .peerDiscovered(let peer):
            peers = await discovery.update([peer])
            for peer in peers {
                try? await peerRepository.upsert(peer)
            }
            publishPeers()
        case .packet(let data, _):
            await handlePacketData(data)
        case .stateChanged(let state):
            transportState = state
            publishState()
        }
    }

    private func handlePacketData(_ data: Data) async {
        guard let channel else { return }
        do {
            let packet = try framer.decode(data)
            guard let assembled = try reassembler.receive(packet) else { return }

            switch assembled.kind {
            case .message:
                let envelope = try SecureEnvelopeCodec.decode(MessageEnvelope.self, from: assembled.body)
                let knownSender = peers.first { $0.id == envelope.senderId }
                let storedSender = try await peerRepository.peer(id: envelope.senderId)
                let sender = knownSender ?? storedSender
                guard let sender else { return }
                let text = try await crypto.decryptMessage(envelope, from: sender, channel: channel)
                let message = ChatMessage(
                    id: envelope.messageId,
                    peerId: sender.id,
                    text: text,
                    sentAt: envelope.timestamp,
                    direction: .incoming,
                    status: .delivered
                )
                try await messageRepository.save(message)
                await publishMessages(peerId: sender.id)
            case .wake:
                let envelope = try SecureEnvelopeCodec.decode(WakeEnvelope.self, from: assembled.body)
                let knownSender = peers.first { $0.id == envelope.senderId }
                let storedSender = try await peerRepository.peer(id: envelope.senderId)
                let sender = knownSender ?? storedSender
                guard let sender else { return }
                try await crypto.decryptWake(envelope, from: sender, channel: channel)
                await notificationService.notifyWake(from: sender.displayName, channel: channel)
            case .presence, .receipt:
                break
            }
        } catch {
            transportState.lastError = "encrypted packet rejected"
            publishState()
        }
    }

    private func nextCounter() -> UInt64 {
        defer { replayCounter += 1 }
        return replayCounter
    }

    private func publishPeers() {
        for continuation in peerContinuations {
            continuation.yield(peers)
        }
    }

    private func publishState() {
        for continuation in stateContinuations {
            continuation.yield(transportState)
        }
    }

    private func publishMessages(peerId: PeerID) async {
        guard let continuations = messageContinuations[peerId] else { return }
        let messages = (try? await messageRepository.messages(peerId: peerId)) ?? []
        for continuation in continuations {
            continuation.yield(messages)
        }
    }
}
