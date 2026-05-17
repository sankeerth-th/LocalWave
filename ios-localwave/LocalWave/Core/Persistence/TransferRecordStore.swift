import Foundation

public actor TransferRecordStore {
    private let url: URL
    private let fileManager: FileManager

    public init(directory: URL? = nil, fileManager: FileManager = .default) {
        self.fileManager = fileManager
        let root = directory ?? fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("LocalWave", isDirectory: true)
        self.url = root.appendingPathComponent("transfers.json")
    }

    public func load() throws -> [TransferRecord] {
        guard fileManager.fileExists(atPath: url.path) else { return [] }
        let data = try Data(contentsOf: url)
        return try SecureEnvelopeCodec.decode([TransferRecord].self, from: data)
    }

    public func save(_ transfers: [TransferRecord]) throws {
        try fileManager.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        let data = try SecureEnvelopeCodec.encode(transfers)
        try data.write(to: url, options: [.atomic, .completeFileProtection])
    }
}
