import Foundation

public actor LocalStore {
    private let baseURL: URL
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    public init(baseURL: URL? = nil) {
        if let baseURL {
            self.baseURL = baseURL
        } else {
            let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            self.baseURL = support.appendingPathComponent("LocalWave", isDirectory: true)
        }
        self.encoder = JSONEncoder()
        self.encoder.dateEncodingStrategy = .iso8601
        self.decoder = JSONDecoder()
        self.decoder.dateDecodingStrategy = .iso8601
    }

    public func load<T: Decodable>(_ type: T.Type, fileName: String, default defaultValue: T) throws -> T {
        try FileManager.default.createDirectory(at: baseURL, withIntermediateDirectories: true)
        let url = baseURL.appendingPathComponent(fileName)
        guard FileManager.default.fileExists(atPath: url.path) else { return defaultValue }
        let data = try Data(contentsOf: url)
        return try decoder.decode(type, from: data)
    }

    public func save<T: Encodable>(_ value: T, fileName: String) throws {
        try FileManager.default.createDirectory(at: baseURL, withIntermediateDirectories: true)
        let url = baseURL.appendingPathComponent(fileName)
        let data = try encoder.encode(value)
        try data.write(to: url, options: [.atomic, .completeFileProtection])
    }
}

