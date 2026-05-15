import Foundation

public enum MessageDirection: String, Codable, Sendable {
    case incoming
    case outgoing
}

public enum MessageStatus: String, Codable, Sendable {
    case pending
    case sent
    case delivered
    case failed
}

public struct ChatMessage: Identifiable, Hashable, Codable, Sendable {
    public var id: MessageID
    public var peerId: PeerID
    public var text: String
    public var sentAt: Date
    public var direction: MessageDirection
    public var status: MessageStatus

    public init(
        id: MessageID = UUID(),
        peerId: PeerID,
        text: String,
        sentAt: Date = Date(),
        direction: MessageDirection,
        status: MessageStatus
    ) {
        self.id = id
        self.peerId = peerId
        self.text = text
        self.sentAt = sentAt
        self.direction = direction
        self.status = status
    }
}

