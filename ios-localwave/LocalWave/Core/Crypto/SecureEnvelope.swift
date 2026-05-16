import Foundation

public enum SecureEnvelopeKind: String, Codable, Sendable {
    case message
    case wake
    case attachment
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
    private static let fractionalISO8601Formatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let wholeSecondISO8601Formatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    public static func encode<T: Encodable>(_ value: T) throws -> Data {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .custom { date, encoder in
            var container = encoder.singleValueContainer()
            try container.encode(fractionalISO8601Formatter.string(from: date))
        }
        return try encoder.encode(value)
    }

    public static func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let rawValue = try container.decode(String.self)
            if let date = fractionalISO8601Formatter.date(from: rawValue) ?? wholeSecondISO8601Formatter.date(from: rawValue) {
                return date
            }
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid LocalWave timestamp.")
        }
        return try decoder.decode(type, from: data)
    }
}
