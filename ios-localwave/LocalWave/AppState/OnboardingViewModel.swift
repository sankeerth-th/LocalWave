import Foundation

@MainActor
public final class OnboardingViewModel: ObservableObject {
    public enum Step: Int, CaseIterable {
        case welcome
        case displayName
        case frequency
        case bluetooth
        case notifications
    }

    @Published public var step: Step = .welcome
    @Published public var displayName = ""
    @Published public var channelText = ""
    @Published public var bluetoothPermission: BluetoothPermissionState = .unknown
    @Published public var notificationPermission: NotificationPermissionState = .unknown
    @Published public private(set) var errorMessage: String?
    @Published public private(set) var isCompleting = false

    private let store: AppStore

    public init(store: AppStore) {
        self.store = store
    }

    public var canContinue: Bool {
        switch step {
        case .welcome, .bluetooth, .notifications:
            return true
        case .displayName:
            return !displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        case .frequency:
            return (try? ChannelCode(channelText)) != nil
        }
    }

    public func next() {
        errorMessage = nil
        switch step {
        case .welcome:
            step = .displayName
        case .displayName:
            guard canContinue else {
                errorMessage = LocalWaveError.displayNameRequired.localizedDescription
                return
            }
            step = .frequency
        case .frequency:
            guard canContinue else {
                errorMessage = LocalWaveError.invalidChannelCode.localizedDescription
                return
            }
            step = .bluetooth
        case .bluetooth:
            step = .notifications
        case .notifications:
            break
        }
    }

    public func back() {
        errorMessage = nil
        guard let index = Step.allCases.firstIndex(of: step), index > 0 else { return }
        step = Step.allCases[index - 1]
    }

    public func requestBluetoothPermission() async {
        bluetoothPermission = await store.environment.requestBluetoothPermission()
    }

    public func requestNotificationPermission() async {
        notificationPermission = await store.environment.requestNotificationPermission()
    }

    public func finish() async {
        errorMessage = nil
        isCompleting = true
        defer { isCompleting = false }

        do {
            let channel = try ChannelCode(channelText)
            try await store.completeOnboarding(displayName: displayName, channel: channel)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

