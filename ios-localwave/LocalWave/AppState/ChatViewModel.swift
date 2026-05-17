import Foundation

public struct SharePackageURL: Identifiable, Sendable {
    public var url: URL
    public var id: String { url.absoluteString }
}

@MainActor
public final class ChatViewModel: ObservableObject {
    @Published public private(set) var messages: [ChatMessage] = []
    @Published public var draft = ""
    @Published public private(set) var isSending = false
    @Published public private(set) var isTransferring = false
    @Published public private(set) var wakeState: WakeButtonState = .idle
    @Published public private(set) var errorMessage: String?
    @Published public private(set) var transferRecords: [TransferRecord] = []
    @Published public var sharePackageURL: SharePackageURL?

    public let environment: AppEnvironment
    @Published public private(set) var peer: PeerProfile
    @Published public private(set) var transportState = TransportState()

    private var messagesTask: Task<Void, Never>?
    private var peerTask: Task<Void, Never>?
    private var transportTask: Task<Void, Never>?
    private var transferTask: Task<Void, Never>?

    public init(environment: AppEnvironment, peer: PeerProfile) {
        self.environment = environment
        self.peer = peer
    }

    deinit {
        messagesTask?.cancel()
        peerTask?.cancel()
        transportTask?.cancel()
        transferTask?.cancel()
    }

    public var canSend: Bool {
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && isPeerReachable && transportState.permission == .allowed && !isSending
    }

    public var isPeerReachable: Bool {
        peer.state == .available || peer.state == .connecting
    }

    public func updatePeer(_ peer: PeerProfile) {
        self.peer = peer
    }

    public func start() {
        guard messagesTask == nil else { return }
        messagesTask = Task { [environment, peerID = peer.id] in
            for await messages in environment.engine.observeMessages(peerId: peerID) {
                if Task.isCancelled { return }
                self.messages = messages.sorted { $0.sentAt < $1.sentAt }
            }
        }

        peerTask = Task { [environment, peerID = peer.id] in
            for await peers in environment.engine.observePeers() {
                if Task.isCancelled { return }
                if let updatedPeer = peers.first(where: { $0.id == peerID }) {
                    self.peer = updatedPeer
                } else if self.peer.state == .available || self.peer.state == .connecting {
                    self.peer.state = .recentlySeen
                    self.peer.lastSeen = Date()
                }
            }
        }

        transportTask = Task { [environment] in
            for await state in environment.engine.observeTransportState() {
                if Task.isCancelled { return }
                self.transportState = state
            }
        }

        transferTask = Task { [environment, peerID = peer.id] in
            for await transfers in environment.engine.observeTransfers() {
                if Task.isCancelled { return }
                self.transferRecords = transfers
                    .filter { $0.peerId == peerID }
                    .sorted { $0.updatedAt > $1.updatedAt }
            }
        }
    }

    public func stop() {
        messagesTask?.cancel()
        peerTask?.cancel()
        transportTask?.cancel()
        transferTask?.cancel()
        messagesTask = nil
        peerTask = nil
        transportTask = nil
        transferTask = nil
    }

    public func sendDraft() async {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, isPeerReachable else { return }
        await send(text)
    }

    public func retry(_ message: ChatMessage) async {
        guard message.status == .failed else { return }
        await send(message.text)
    }

    public func sendWake() async {
        guard isPeerReachable else {
            wakeState = .unavailable
            return
        }

        wakeState = .sending
        do {
            try await environment.engine.sendWake(to: peer.id)
            wakeState = .sent
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            if !Task.isCancelled, wakeState == .sent {
                wakeState = .idle
            }
        } catch {
            wakeState = .failed
            errorMessage = "Wake could not be sent. This person may not be reachable right now."
        }
    }

    private func send(_ text: String) async {
        isSending = true
        errorMessage = nil
        if draft.trimmingCharacters(in: .whitespacesAndNewlines) == text {
            draft = ""
        }
        defer { isSending = false }

        do {
            _ = try await environment.engine.sendMessage(text: text, to: peer.id)
        } catch LocalWaveError.peerUnavailable {
            peer.state = .recentlySeen
            peer.lastSeen = Date()
            errorMessage = LocalWaveError.peerUnavailable.localizedDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    public func sendAttachment(from url: URL) async {
        isTransferring = true
        errorMessage = nil
        defer { isTransferring = false }
        do {
            let attachment = try loadAttachment(from: url)
            _ = try await environment.engine.sendAttachment(attachment, to: peer.id)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    public func exportEncryptedSharePackage(from url: URL) async {
        isTransferring = true
        errorMessage = nil
        defer { isTransferring = false }
        do {
            let attachment = try loadAttachment(from: url)
            let package = try await environment.engine.exportEncryptedSharePackage(attachment, to: peer.id)
            let encoded = try SecureEnvelopeCodec.encode(package)
            let safeName = Self.safePackageName(for: attachment.fileName)
            let exportDirectory = FileManager.default.temporaryDirectory
                .appendingPathComponent("LocalWaveExports", isDirectory: true)
            try FileManager.default.createDirectory(at: exportDirectory, withIntermediateDirectories: true)
            let target = exportDirectory
                .appendingPathComponent("\(safeName).\(EncryptedSharePackage.fileExtension)")
            try encoded.write(to: target, options: [.atomic, .completeFileProtection])
            sharePackageURL = SharePackageURL(url: target)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    public func importEncryptedSharePackage(from url: URL) async {
        isTransferring = true
        errorMessage = nil
        defer { isTransferring = false }
        do {
            let didStart = url.startAccessingSecurityScopedResource()
            defer { if didStart { url.stopAccessingSecurityScopedResource() } }
            let data = try Data(contentsOf: url)
            let package = try SecureEnvelopeCodec.decode(EncryptedSharePackage.self, from: data)
            _ = try await environment.engine.importEncryptedSharePackage(package)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func loadAttachment(from url: URL) throws -> OutboundAttachment {
        let didStart = url.startAccessingSecurityScopedResource()
        defer { if didStart { url.stopAccessingSecurityScopedResource() } }
        let data = try Data(contentsOf: url)
        let resourceValues = try? url.resourceValues(forKeys: [.contentTypeKey, .localizedNameKey, .nameKey])
        let fileName = resourceValues?.localizedName ?? resourceValues?.name ?? url.lastPathComponent
        let contentType = resourceValues?.contentType?.preferredMIMEType ?? "application/octet-stream"
        return try OutboundAttachment(fileName: fileName, contentType: contentType, data: data)
    }

    private static func safePackageName(for fileName: String) -> String {
        let cleaned = fileName
            .replacingOccurrences(of: "/", with: "-")
            .replacingOccurrences(of: ":", with: "-")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "._-"))
        let scalars = cleaned.unicodeScalars.map { allowed.contains($0) ? Character($0) : "-" }
        let safe = String(scalars).trimmingCharacters(in: CharacterSet(charactersIn: ".-"))
        return safe.isEmpty ? "localwave-package" : safe
    }
}
