import Foundation

@MainActor
public final class SettingsViewModel: ObservableObject {
    @Published public var displayName: String
    @Published public var channelText: String
    @Published public private(set) var identity: LocalIdentity?
    @Published public private(set) var transportState = TransportState()
    @Published public private(set) var permissionSnapshot = PermissionSnapshot()
    @Published public private(set) var nearbyPeerCount = 0
    @Published public private(set) var messageStatusCounts: [MessageStatus: Int] = [:]
    @Published public private(set) var errorMessage: String?

    public let store: AppStore
    private var peerTask: Task<Void, Never>?
    private var transportTask: Task<Void, Never>?

    public init(store: AppStore) {
        self.store = store
        self.displayName = store.displayName
        self.channelText = store.channelCodeText
    }

    deinit {
        peerTask?.cancel()
        transportTask?.cancel()
    }

    public var appModeText: String {
        store.environment.mode.rawValue
    }

    public var redactedLastError: String {
        transportState.lastError?.redactedDiagnostic ?? "None"
    }

    public func start() {
        guard peerTask == nil, transportTask == nil else { return }

        peerTask = Task { [environment = store.environment] in
            for await peers in environment.engine.observePeers() {
                if Task.isCancelled { return }
                nearbyPeerCount = peers.count
            }
        }

        transportTask = Task { [environment = store.environment] in
            for await state in environment.engine.observeTransportState() {
                if Task.isCancelled { return }
                transportState = state
            }
        }

        Task {
            permissionSnapshot = await store.environment.permissionSnapshot()
            identity = try? await store.environment.engine.localIdentity()
        }
    }

    public func saveDisplayName() async {
        do {
            try await store.updateDisplayName(displayName)
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    public func switchChannel() async {
        do {
            let channel = try ChannelCode(channelText)
            try await store.switchChannel(channel)
            channelText = channel.normalized
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

public extension String {
    var redactedDiagnostic: String {
        if isEmpty { return "None" }
        let cleaned = replacingOccurrences(of: "\n", with: " ")
        if cleaned.count <= 28 { return cleaned }
        return "\(cleaned.prefix(18))...\(cleaned.suffix(6))"
    }
}

