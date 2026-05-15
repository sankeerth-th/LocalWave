import Foundation

public struct MessageEnvelope: Codable, Sendable, Equatable {
    public var version: UInt8
    public var senderId: PeerID
    public var recipientId: PeerID
    public var timestamp: Date
    public var messageId: MessageID
    public var replayCounter: UInt64
    public var nonce: Data
    public var ciphertext: Data
    public var tag: Data
}

public struct WakeEnvelope: Codable, Sendable, Equatable {
    public var version: UInt8
    public var senderId: PeerID
    public var recipientId: PeerID
    public var timestamp: Date
    public var replayCounter: UInt64
    public var nonce: Data
    public var ciphertext: Data
    public var tag: Data
}

public struct DeliveryReceipt: Codable, Sendable, Equatable {
    public var messageId: MessageID
    public var senderId: PeerID
    public var recipientId: PeerID
    public var deliveredAt: Date
}

