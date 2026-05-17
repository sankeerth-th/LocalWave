import CryptoKit
import Foundation

public final class LocalWaveEngine: LocalWaveEngineProtocol, @unchecked Sendable {
    private let identityStore: IdentityStoreProtocol
    private let crypto: SessionCrypto
    private let peerRepository: PeerRepositoryProtocol
    private let messageRepository: MessageRepositoryProtocol
    private let notificationService: LocalNotificationServicing
    private let transport: BluetoothTransportProtocol
    private let objectTransfer: ObjectTransferCrypto
    private let objectStore: LocalWaveObjectStore
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
    private var transferContinuations: [AsyncStream<[TransferRecord]>.Continuation] = []
    private var transfers: [TransferRecord] = []
    private var incomingObjects: [String: IncomingObjectState] = [:]
    private var replayCounter: UInt64 = 1
    private var eventTask: Task<Void, Never>?

    public init(
        identityStore: IdentityStoreProtocol = KeychainIdentityStore(),
        peerRepository: PeerRepositoryProtocol = JSONPeerRepository(),
        messageRepository: MessageRepositoryProtocol = JSONMessageRepository(),
        notificationService: LocalNotificationServicing = LocalNotificationService(),
        transport: BluetoothTransportProtocol = LocalWaveBluetoothTransport(),
        objectStore: LocalWaveObjectStore = LocalWaveObjectStore()
    ) {
        self.identityStore = identityStore
        self.crypto = SessionCrypto(identityStore: identityStore)
        self.objectTransfer = ObjectTransferCrypto(sessionCrypto: self.crypto)
        self.peerRepository = peerRepository
        self.messageRepository = messageRepository
        self.notificationService = notificationService
        self.transport = transport
        self.objectStore = objectStore
    }

    public func start(channel: ChannelCode, displayName: String) async throws {
        let trimmed = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw LocalWaveError.displayNameRequired }
        self.channel = channel
        self.displayName = trimmed
        let identity = try await identityStore.loadOrCreateIdentity(displayName: trimmed)
        observeTransportEvents()
        try await transport.start(channel: channel, identity: identity)
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
        let peer = try await resolvePeer(peerId)
        let envelope = try await crypto.encryptMessage(text, to: peer, counter: nextCounter(), channel: channel)
        let message = ChatMessage(id: envelope.messageId, peerId: peerId, text: text, sentAt: envelope.timestamp, direction: .outgoing, status: .pending)
        try await messageRepository.save(message)
        await publishMessages(peerId: peerId)

        do {
            let body = try SecureEnvelopeCodec.encode(envelope)
            try? await sendPresence(to: peerId)
            try await transport.send(body, kind: .message, to: peerId)
            try await messageRepository.updateStatus(messageId: message.id, status: .sent)
            await publishMessages(peerId: peerId)
            return message.id
        } catch {
            try? await messageRepository.updateStatus(messageId: message.id, status: .failed)
            await publishMessages(peerId: peerId)
            throw error
        }
    }

    public func sendWake(to peerId: PeerID) async throws {
        guard let channel else { throw LocalWaveError.invalidChannelCode }
        let peer = try await resolvePeer(peerId)
        let wake = try await crypto.encryptWake(to: peer, counter: nextCounter(), channel: channel)
        let body = try SecureEnvelopeCodec.encode(wake)
        try? await sendPresence(to: peerId)
        try await transport.send(body, kind: .wake, to: peerId)
    }

    public func sendAttachment(_ attachment: OutboundAttachment, to peerId: PeerID) async throws -> TransferID {
        guard let channel else { throw LocalWaveError.invalidChannelCode }
        let peer = try await resolvePeer(peerId)
        let identity = try await identityStore.loadOrCreateIdentity(displayName: displayName)
        let objectPackage = try await objectTransfer.createPackage(attachment: attachment, to: peer, localIdentity: identity, channel: channel)
        try await objectStore.store(objectPackage)
        let transferId = Self.transferId(for: objectPackage.manifest.objectId)
        let transfer = TransferRecord(
            id: transferId,
            peerId: peerId,
            fileName: attachment.fileName,
            byteCount: attachment.data.count,
            route: .l2cap,
            status: .transferring,
            updatedAt: Date(),
            failureReason: nil
        )
        upsertTransfer(transfer)
        try? await sendPresence(to: peerId)
        try await transport.send(try ObjectTransferCodec.encode(objectPackage.manifest), kind: .objectManifest, to: peerId)
        for pieces in objectPackage.pieces.chunked(size: 4) {
            let batch = ObjectPieceBatch(objectProtocolVersion: 1, objectId: objectPackage.manifest.objectId, pieces: pieces)
            try await transport.sendBulk(try ObjectTransferCodec.encode(batch), to: peerId)
        }
        upsertTransfer(TransferRecord(
            id: transferId,
            peerId: peerId,
            fileName: attachment.fileName,
            byteCount: attachment.data.count,
            route: .l2cap,
            status: .pending,
            updatedAt: Date(),
            failureReason: nil
        ))
        return transferId
    }

    public func exportEncryptedSharePackage(_ attachment: OutboundAttachment, to peerId: PeerID) async throws -> EncryptedSharePackage {
        guard let channel else { throw LocalWaveError.invalidChannelCode }
        let peer = try await resolvePeer(peerId)
        let identity = try await identityStore.loadOrCreateIdentity(displayName: displayName)
        let objectPackage = try await objectTransfer.createPackage(attachment: attachment, to: peer, localIdentity: identity, channel: channel)
        try await objectStore.store(objectPackage)
        let transferId = Self.transferId(for: objectPackage.manifest.objectId)
        let package = EncryptedSharePackage(
            version: 2,
            packageId: transferId,
            createdAt: Date(timeIntervalSince1970: TimeInterval(objectPackage.manifest.createdAtEpochMillis) / 1_000),
            route: .nativeShare,
            senderId: objectPackage.manifest.senderId,
            recipientId: objectPackage.manifest.recipientId,
            envelope: Self.placeholderEnvelope(transferId: transferId, manifest: objectPackage.manifest),
            objectManifest: objectPackage.manifest,
            objectPieces: objectPackage.pieces,
            senderFingerprint: identity.fingerprint,
            senderAgreementPublicKey: identity.agreementPublicKey
        )
        upsertTransfer(TransferRecord(
            id: package.packageId,
            peerId: peerId,
            fileName: attachment.fileName,
            byteCount: attachment.data.count,
            route: .nativeShare,
            status: .exported,
            updatedAt: Date(),
            failureReason: nil
        ))
        return package
    }

    public func importEncryptedSharePackage(_ package: EncryptedSharePackage) async throws -> ImportedSharePackage {
        guard package.version == 1 || package.version == 2 else { throw LocalWaveError.unsupportedPackage }
        guard let channel else { throw LocalWaveError.invalidChannelCode }
        if let manifest = package.objectManifest, let pieces = package.objectPieces {
            try await objectStore.store(LocalWaveObjectPackage(manifest: manifest, pieces: pieces))
            let sender = try await peerForImportedObject(package: package, manifest: manifest)
            let result = try await objectTransfer.decryptPackage(LocalWaveObjectPackage(manifest: manifest, pieces: pieces), from: sender, channel: channel)
            upsertTransfer(TransferRecord(
                id: package.packageId,
                peerId: package.senderId,
                fileName: result.attachment.fileName,
                byteCount: result.attachment.data.count,
                route: .nativeShare,
                status: .completed,
                updatedAt: Date(),
                failureReason: nil
            ))
            return ImportedSharePackage(transferId: package.packageId, senderId: package.senderId, attachment: result.attachment, verifiedHash: result.receipt.verifiedPlainSHA256)
        }
        let sender = try await resolvePeer(package.senderId)
        let attachment = try await crypto.decryptAttachment(package.envelope, from: sender, channel: channel)
        let verifiedHash = Data(SHA256.hash(data: attachment.data))
        upsertTransfer(TransferRecord(
            id: package.packageId,
            peerId: package.senderId,
            fileName: attachment.fileName,
            byteCount: attachment.data.count,
            route: .nativeShare,
            status: .completed,
            updatedAt: Date(),
            failureReason: nil
        ))
        return ImportedSharePackage(transferId: package.packageId, senderId: package.senderId, attachment: attachment, verifiedHash: verifiedHash)
    }

    public func verifyPeer(_ peerId: PeerID, fingerprint: String) async throws {
        var peer = try await resolvePeer(peerId)
        guard peer.fingerprint.caseInsensitiveCompare(fingerprint.trimmingCharacters(in: .whitespacesAndNewlines)) == .orderedSame else {
            peer.trustState = .changed
            try? await peerRepository.upsert(peer)
            throw LocalWaveError.fingerprintMismatch
        }
        peer.trustState = .verified
        try await peerRepository.upsert(peer)
        peers = peers.map { $0.id == peerId ? peer : $0 }
        publishPeers()
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

    public func observeTransfers() -> AsyncStream<[TransferRecord]> {
        AsyncStream { continuation in
            transferContinuations.append(continuation)
            continuation.yield(transfers)
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
        case .bulk(let data, _):
            await handleBulkData(data)
        case .stateChanged(let state):
            transportState = state
            publishState()
        }
    }

    private func handleBulkData(_ data: Data) async {
        if let batch = try? ObjectTransferCodec.decode(ObjectPieceBatch.self, from: data) {
            incomingObjects[batch.objectId, default: IncomingObjectState()].pieces.append(contentsOf: batch.pieces)
            try? await objectStore.store(batch.pieces)
            if let manifest = incomingObjects[batch.objectId]?.manifest {
                upsertTransfer(TransferRecord(
                    id: Self.transferId(for: batch.objectId),
                    peerId: manifest.senderId,
                    fileName: "Encrypted object",
                    byteCount: batch.pieces.reduce(0) { $0 + $1.ciphertext.count },
                    route: .l2cap,
                    status: .transferring,
                    updatedAt: Date(),
                    failureReason: nil
                ))
            }
            await tryCompleteObject(batch.objectId)
            return
        }

        await handleLegacyBulkData(data)
    }

    private func handleLegacyBulkData(_ data: Data) async {
        guard let channel else { return }
        do {
            let envelope = try SecureEnvelopeCodec.decode(AttachmentEnvelope.self, from: data)
            let sender = try await resolvePeer(envelope.senderId)
            let attachment = try await crypto.decryptAttachment(envelope, from: sender, channel: channel)
            upsertTransfer(TransferRecord(
                id: envelope.transferId,
                peerId: sender.id,
                fileName: attachment.fileName,
                byteCount: attachment.data.count,
                route: .l2cap,
                status: .completed,
                updatedAt: Date(),
                failureReason: nil
            ))
        } catch {
            transportState.lastError = "encrypted attachment rejected"
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
                await markPeerAvailable(sender)
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
                await sendDeliveryReceipt(for: envelope, to: sender)
            case .wake:
                let envelope = try SecureEnvelopeCodec.decode(WakeEnvelope.self, from: assembled.body)
                let knownSender = peers.first { $0.id == envelope.senderId }
                let storedSender = try await peerRepository.peer(id: envelope.senderId)
                let sender = knownSender ?? storedSender
                guard let sender else { return }
                try await crypto.decryptWake(envelope, from: sender, channel: channel)
                await markPeerAvailable(sender)
                await notificationService.notifyWake(from: sender.displayName, channel: channel)
            case .objectManifest:
                let manifest = try ObjectTransferCodec.decode(EncryptedObjectManifest.self, from: assembled.body)
                incomingObjects[manifest.objectId, default: IncomingObjectState()].manifest = manifest
                try? await objectStore.store(manifest)
                if let sender = await knownPeer(id: manifest.senderId) {
                    await markPeerAvailable(sender)
                }
                upsertTransfer(TransferRecord(
                    id: Self.transferId(for: manifest.objectId),
                    peerId: manifest.senderId,
                    fileName: "Encrypted object",
                    byteCount: manifest.ciphertext.count,
                    route: .l2cap,
                    status: .manifestReceived,
                    updatedAt: Date(),
                    failureReason: nil
                ))
                await tryCompleteObject(manifest.objectId)
            case .receipt:
                if let deliveryReceipt = try? SecureEnvelopeCodec.decode(DeliveryReceipt.self, from: assembled.body) {
                    try await messageRepository.updateStatus(messageId: deliveryReceipt.messageId, status: .delivered)
                    await publishMessages(peerId: deliveryReceipt.recipientId)
                    if let recipient = await knownPeer(id: deliveryReceipt.recipientId) {
                        await markPeerAvailable(recipient)
                    }
                } else {
                    let receipt = try ObjectTransferCodec.decode(TransferReceipt.self, from: assembled.body)
                    if let index = transfers.firstIndex(where: { $0.id == receipt.transferId }) {
                        transfers[index].status = .completed
                        transfers[index].updatedAt = Date()
                        publishTransfers()
                    }
                    if let recipient = await knownPeer(id: receipt.recipientId) {
                        await markPeerAvailable(recipient)
                    }
                }
            case .presence:
                let intro = try SecureEnvelopeCodec.decode(PeerIntroEnvelope.self, from: assembled.body)
                await markPeerAvailable(PeerProfile(
                    id: intro.peerId,
                    displayName: intro.displayName,
                    fingerprint: intro.fingerprint,
                    rssi: 0,
                    lastSeen: intro.sentAt,
                    state: .available,
                    publicKeyData: intro.agreementPublicKey
                ))
            case .objectControl:
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

    private func resolvePeer(_ peerId: PeerID) async throws -> PeerProfile {
        let knownPeer = peers.first { $0.id == peerId }
        let storedPeer = try await peerRepository.peer(id: peerId)
        guard let peer = knownPeer ?? storedPeer else {
            throw LocalWaveError.peerUnavailable
        }
        return peer
    }

    private func knownPeer(id peerId: PeerID) async -> PeerProfile? {
        if let peer = peers.first(where: { $0.id == peerId }) {
            return peer
        }
        return try? await peerRepository.peer(id: peerId)
    }

    private func markPeerAvailable(_ peer: PeerProfile) async {
        var updated = peer
        updated.state = .available
        updated.lastSeen = Date()
        if let index = peers.firstIndex(where: { $0.id == peer.id }) {
            peers[index] = updated
        } else {
            peers.append(updated)
        }
        try? await peerRepository.upsert(updated)
        publishPeers()
    }

    private func sendDeliveryReceipt(for envelope: MessageEnvelope, to sender: PeerProfile) async {
        do {
            let identity = try await identityStore.loadOrCreateIdentity(displayName: displayName.isEmpty ? "Local User" : displayName)
            let receipt = DeliveryReceipt(
                messageId: envelope.messageId,
                senderId: envelope.senderId,
                recipientId: identity.peerId,
                deliveredAt: Date()
            )
            let encoded = try SecureEnvelopeCodec.encode(receipt)
            try await transport.send(encoded, kind: .receipt, to: sender.id)
        } catch {
            transportState.lastError = "delivery receipt pending"
            publishState()
        }
    }

    private func sendPresence(to peerId: PeerID) async throws {
        let identity = try await identityStore.loadOrCreateIdentity(displayName: displayName.isEmpty ? "Local User" : displayName)
        let intro = PeerIntroEnvelope(
            peerId: identity.peerId,
            displayName: identity.displayName,
            fingerprint: identity.fingerprint,
            agreementPublicKey: identity.agreementPublicKey
        )
        try await transport.send(try SecureEnvelopeCodec.encode(intro), kind: .presence, to: peerId)
    }

    private func tryCompleteObject(_ objectId: String) async {
        guard let channel else { return }
        let storedPackage = try? await objectStore.package(objectId: objectId)
        let state = incomingObjects[objectId]
        guard let manifest = state?.manifest ?? storedPackage?.manifest else { return }
        let pieces = state?.pieces ?? storedPackage?.pieces ?? []
        do {
            let sender = try await peerForImportedObject(package: nil, manifest: manifest)
            let result = try await objectTransfer.decryptPackage(LocalWaveObjectPackage(manifest: manifest, pieces: pieces), from: sender, channel: channel)
            upsertTransfer(TransferRecord(
                id: result.transferId,
                peerId: sender.id,
                fileName: result.attachment.fileName,
                byteCount: result.attachment.data.count,
                route: .l2cap,
                status: .completed,
                updatedAt: Date(),
                failureReason: nil
            ))
            incomingObjects[objectId] = nil
            if let receiptData = try? ObjectTransferCodec.encode(result.receipt) {
                try? await transport.send(receiptData, kind: .receipt, to: sender.id)
            }
        } catch {
            var current = incomingObjects[objectId] ?? IncomingObjectState()
            current.lastError = error.localizedDescription
            incomingObjects[objectId] = current
        }
    }

    private func peerForImportedObject(package: EncryptedSharePackage?, manifest: EncryptedObjectManifest) async throws -> PeerProfile {
        if let known = peers.first(where: { $0.id == manifest.senderId }) {
            return known
        }
        if let stored = try await peerRepository.peer(id: manifest.senderId) {
            return stored
        }
        let peer = PeerProfile(
            id: manifest.senderId,
            displayName: "Imported Peer",
            fingerprint: package?.senderFingerprint ?? manifest.senderFingerprint,
            rssi: 0,
            lastSeen: Date(),
            state: .recentlySeen,
            trustState: .unverified,
            publicKeyData: package?.senderAgreementPublicKey ?? manifest.senderAgreementPublicKey
        )
        try await peerRepository.upsert(peer)
        peers.append(peer)
        publishPeers()
        return peer
    }

    private func upsertTransfer(_ transfer: TransferRecord) {
        if let index = transfers.firstIndex(where: { $0.id == transfer.id }) {
            transfers[index] = transfer
        } else {
            transfers.append(transfer)
        }
        publishTransfers()
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

    private func publishTransfers() {
        for continuation in transferContinuations {
            continuation.yield(transfers)
        }
    }

    private func publishMessages(peerId: PeerID) async {
        guard let continuations = messageContinuations[peerId] else { return }
        let messages = (try? await messageRepository.messages(peerId: peerId)) ?? []
        for continuation in continuations {
            continuation.yield(messages)
        }
    }

    private static func transferId(for objectId: String) -> TransferID {
        UUID(uuidString: String(objectId.prefix(32)).uuidFormattedFromHex) ?? UUID()
    }

    private static func placeholderEnvelope(transferId: TransferID, manifest: EncryptedObjectManifest) -> AttachmentEnvelope {
        AttachmentEnvelope(
            version: 1,
            senderId: manifest.senderId,
            recipientId: manifest.recipientId,
            timestamp: Date(timeIntervalSince1970: TimeInterval(manifest.createdAtEpochMillis) / 1_000),
            transferId: transferId,
            replayCounter: 0,
            nonce: Data(),
            ciphertext: Data(),
            tag: Data()
        )
    }
}

private struct IncomingObjectState {
    var manifest: EncryptedObjectManifest?
    var pieces: [ObjectPiece] = []
    var lastError: String?
}

private extension Array {
    func chunked(size: Int) -> [[Element]] {
        stride(from: 0, to: count, by: size).map { Array(self[$0..<Swift.min($0 + size, count)]) }
    }
}

private extension String {
    var uuidFormattedFromHex: String {
        let padded = padding(toLength: 32, withPad: "0", startingAt: 0)
        return "\(padded.prefix(8))-\(padded.dropFirst(8).prefix(4))-\(padded.dropFirst(12).prefix(4))-\(padded.dropFirst(16).prefix(4))-\(padded.dropFirst(20).prefix(12))"
    }
}
