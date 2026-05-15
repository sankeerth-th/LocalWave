import Foundation

public enum SecureEnvelopeKind: String, Codable, Sendable {
    case message
    case wake
}

public struct SecureEnvelope: Codable, Equatable, Sendable {
    public var version: UInt8
    public var kind: SecureEnvelopeKind
    public var senderId: PeerID
    public var recipientId: PeerID
    public var timestamp: Date
    public var messageId: MessageID?
    public var replayCounter: UInt64
    public var nonce: Data
    public var ciphertext: Data
    public var tag: Data

    public init(
        version: UInt8 = 1,
        kind: SecureEnvelopeKind,
        senderId: PeerID,
        recipientId: PeerID,
        timestamp: Date = Date(),
        messageId: MessageID? = nil,
        replayCounter: UInt64,
        nonce: Data,
        ciphertext: Data,
        tag: Data
    ) {
        self.version = version
        self.kind = kind
        self.senderId = senderId
        self.recipientId = recipientId
        self.timestamp = timestamp
        self.messageId = messageId
        self.replayCounter = replayCounter
        self.nonce = nonce
        self.ciphertext = ciphertext
        self.tag = tag
    }
}

public enum SecureEnvelopeCodec {
    public static func encode<T: Encodable>(_ value: T) throws -> Data {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return try encoder.encode(value)
    }

    public static func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(type, from: data)
    }
}
