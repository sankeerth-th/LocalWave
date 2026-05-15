import Foundation

@MainActor
public final class ChatViewModel: ObservableObject {
    @Published public private(set) var messages: [ChatMessage] = []
    @Published public var draft = ""
    @Published public private(set) var isSending = false
    @Published public private(set) var wakeState: WakeButtonState = .idle
    @Published public private(set) var errorMessage: String?

    public let environment: AppEnvironment
    @Published public private(set) var peer: PeerProfile
    @Published public private(set) var transportState = TransportState()

    private var messagesTask: Task<Void, Never>?
    private var peerTask: Task<Void, Never>?
    private var transportTask: Task<Void, Never>?

    public init(environment: AppEnvironment, peer: PeerProfile) {
        self.environment = environment
        self.peer = peer
    }

    deinit {
        messagesTask?.cancel()
        peerTask?.cancel()
        transportTask?.cancel()
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
    }

    public func stop() {
        messagesTask?.cancel()
        peerTask?.cancel()
        transportTask?.cancel()
        messagesTask = nil
        peerTask = nil
        transportTask = nil
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
}
