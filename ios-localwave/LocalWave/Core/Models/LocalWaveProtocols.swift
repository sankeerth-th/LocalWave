import Foundation

public protocol LocalWaveEngineProtocol: AnyObject, Sendable {
    func start(channel: ChannelCode, displayName: String) async throws
    func stop() async
    func updateDisplayName(_ displayName: String) async
    func switchChannel(_ channel: ChannelCode) async throws
    func sendMessage(text: String, to peerId: PeerID) async throws -> MessageID
    func sendWake(to peerId: PeerID) async throws
    func observePeers() -> AsyncStream<[PeerProfile]>
    func observeMessages(peerId: PeerID) -> AsyncStream<[ChatMessage]>
    func observeTransportState() -> AsyncStream<TransportState>
    func localIdentity() async throws -> LocalIdentity
}

public protocol CryptoServiceProtocol: Sendable {
    func encryptMessage(_ text: String, to peer: PeerProfile, counter: UInt64, channel: ChannelCode) async throws -> MessageEnvelope
    func decryptMessage(_ envelope: MessageEnvelope, from peer: PeerProfile, channel: ChannelCode) async throws -> String
}

public protocol MessageRepositoryProtocol: Sendable {
    func save(_ message: ChatMessage) async throws
    func updateStatus(messageId: MessageID, status: MessageStatus) async throws
    func messages(peerId: PeerID) async throws -> [ChatMessage]
    func deleteAll(peerId: PeerID) async throws
}

public protocol PeerRepositoryProtocol: Sendable {
    func upsert(_ peer: PeerProfile) async throws
    func peers() async throws -> [PeerProfile]
    func peer(id: PeerID) async throws -> PeerProfile?
    func clearRecentlySeen() async throws
}
