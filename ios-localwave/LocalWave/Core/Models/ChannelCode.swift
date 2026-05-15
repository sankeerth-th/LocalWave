import Foundation

public struct ChannelCode: Hashable, Codable, Sendable, Identifiable {
    public let rawValue: String
    public let normalized: String

    public var id: String { normalized }

    public init(_ value: String) throws {
        let collapsed = value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .split(whereSeparator: { $0.isWhitespace })
            .joined(separator: " ")
            .uppercased()

        guard collapsed.count >= 3 else {
            throw LocalWaveError.invalidChannelCode
        }

        self.rawValue = value
        self.normalized = collapsed
    }

    public static let sample = try! ChannelCode("DOCK-A-17")
}

