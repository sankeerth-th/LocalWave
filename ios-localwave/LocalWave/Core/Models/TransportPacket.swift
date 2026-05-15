import Foundation

public enum TransportPacketKind: UInt8, Codable, Sendable {
    case presence = 1
    case message = 2
    case wake = 3
    case receipt = 4
}

public struct TransportPacket: Identifiable, Hashable, Codable, Sendable {
    public static let version: UInt8 = 1

    public var id: PacketID
    public var conversationId: ConversationID
    public var kind: TransportPacketKind
    public var chunkIndex: UInt16
    public var chunkCount: UInt16
    public var bodyLength: UInt32
    public var checksum: UInt32
    public var body: Data

    public init(
        id: PacketID,
        conversationId: ConversationID,
        kind: TransportPacketKind,
        chunkIndex: UInt16,
        chunkCount: UInt16,
        bodyLength: UInt32,
        checksum: UInt32,
        body: Data
    ) {
        self.id = id
        self.conversationId = conversationId
        self.kind = kind
        self.chunkIndex = chunkIndex
        self.chunkCount = chunkCount
        self.bodyLength = bodyLength
        self.checksum = checksum
        self.body = body
    }
}

