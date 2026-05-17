import Foundation

@MainActor
public final class AppStore: ObservableObject {
    private enum Keys {
        static let displayName = "localwave.displayName"
        static let channelCode = "localwave.channelCode"
        static let onboardingComplete = "localwave.onboardingComplete"
    }

    public let environment: AppEnvironment
    private let defaults: UserDefaults
    private var hasStartedEngine = false
    private var isStartingEngine = false

    @Published public private(set) var isOnboardingComplete: Bool
    @Published public private(set) var displayName: String
    @Published public private(set) var channelCodeText: String
    @Published public private(set) var lastStartError: String?

    public var currentChannel: ChannelCode? {
        try? ChannelCode(channelCodeText)
    }

    public init(environment: AppEnvironment, defaults: UserDefaults = .standard) {
        self.environment = environment
        self.defaults = defaults
        self.displayName = defaults.string(forKey: Keys.displayName) ?? ""
        self.channelCodeText = defaults.string(forKey: Keys.channelCode) ?? ""
        self.isOnboardingComplete = defaults.bool(forKey: Keys.onboardingComplete)
    }

    public func completeOnboarding(displayName: String, channel: ChannelCode) async throws {
        let trimmedName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            throw LocalWaveError.displayNameRequired
        }

        self.displayName = trimmedName
        self.channelCodeText = channel.normalized
        self.isOnboardingComplete = true
        persistSetup()
        try await startEngine(channel: channel, displayName: trimmedName)
    }

    public func startIfReady() async {
        guard isOnboardingComplete, !hasStartedEngine, !isStartingEngine, let channel = currentChannel else {
            return
        }

        do {
            try await startEngine(channel: channel, displayName: displayName)
        } catch {
            lastStartError = error.localizedDescription
        }
    }

    public func updateDisplayName(_ name: String) async throws {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            throw LocalWaveError.displayNameRequired
        }

        displayName = trimmedName
        defaults.set(trimmedName, forKey: Keys.displayName)
        await environment.engine.updateDisplayName(trimmedName)
    }

    public func switchChannel(_ channel: ChannelCode) async throws {
        try await environment.engine.switchChannel(channel)
        channelCodeText = channel.normalized
        defaults.set(channel.normalized, forKey: Keys.channelCode)
    }

    public func resetOnboardingForTests() {
        isOnboardingComplete = false
        displayName = ""
        channelCodeText = ""
        hasStartedEngine = false
        defaults.removeObject(forKey: Keys.displayName)
        defaults.removeObject(forKey: Keys.channelCode)
        defaults.removeObject(forKey: Keys.onboardingComplete)
    }

    private func startEngine(channel: ChannelCode, displayName: String) async throws {
        guard !hasStartedEngine, !isStartingEngine else { return }
        isStartingEngine = true
        do {
            try await environment.engine.start(channel: channel, displayName: displayName)
            hasStartedEngine = true
            lastStartError = nil
            isStartingEngine = false
        } catch {
            isStartingEngine = false
            throw error
        }
    }

    private func persistSetup() {
        defaults.set(displayName, forKey: Keys.displayName)
        defaults.set(channelCodeText, forKey: Keys.channelCode)
        defaults.set(isOnboardingComplete, forKey: Keys.onboardingComplete)
    }
}

extension AppStore {
    static func preview(onboarded: Bool) -> AppStore {
        let defaults = UserDefaults(suiteName: "localwave.preview.\(UUID().uuidString)")!
        let store = AppStore(environment: .mock, defaults: defaults)
        if onboarded {
            Task { @MainActor in
                try? await store.completeOnboarding(displayName: "Maya", channel: .sample)
            }
        }
        return store
    }
}
