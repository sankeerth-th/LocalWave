import Foundation
@preconcurrency import UserNotifications

public protocol LocalNotificationServiceProtocol: Sendable {
    func requestAuthorization() async throws -> Bool
    func notifyIncomingMessage(from peer: PeerProfile) async throws
    func notifyWake(from peer: PeerProfile) async throws
}

public protocol LocalNotificationServicing: Sendable {
    func requestAuthorization() async throws -> Bool
    func notifyIncomingMessage(from peer: PeerProfile) async throws
    func notifyWake(from peer: PeerProfile) async throws
    func notifyWake(from displayName: String, channel: ChannelCode) async
}

public struct LocalNotificationService: LocalNotificationServiceProtocol, LocalNotificationServicing {
    private let center: UNUserNotificationCenter

    public init(center: UNUserNotificationCenter = .current()) {
        self.center = center
    }

    public func requestAuthorization() async throws -> Bool {
        try await center.requestAuthorization(options: [.alert, .sound, .badge])
    }

    public func notifyIncomingMessage(from peer: PeerProfile) async throws {
        let content = UNMutableNotificationContent()
        content.title = peer.displayName
        content.body = "New encrypted LocalWave message."
        content.sound = .default
        try await add(content: content, identifier: "message-\(peer.id)-\(UUID().uuidString)")
    }

    public func notifyWake(from peer: PeerProfile) async throws {
        let content = UNMutableNotificationContent()
        content.title = "LocalWave wake"
        content.body = "\(peer.displayName) is asking for your attention."
        content.sound = .default
        try await add(content: content, identifier: "wake-\(peer.id)-\(UUID().uuidString)")
    }

    public func notifyWake(from displayName: String, channel: ChannelCode) async {
        let content = UNMutableNotificationContent()
        content.title = "LocalWave wake"
        content.body = "\(displayName) is asking for your attention."
        content.sound = .default
        try? await add(content: content, identifier: "wake-\(channel.id)-\(UUID().uuidString)")
    }

    private func add(content: UNNotificationContent, identifier: String) async throws {
        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: nil)
        try await center.add(request)
    }
}
