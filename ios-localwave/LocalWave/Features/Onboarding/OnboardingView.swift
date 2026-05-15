import SwiftUI

struct OnboardingView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ProgressView(value: Double(viewModel.step.rawValue + 1), total: Double(OnboardingViewModel.Step.allCases.count))
                    .padding(.horizontal)
                    .accessibilityLabel("Onboarding progress")

                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        header
                        currentStep

                        if let error = viewModel.errorMessage {
                            PermissionBanner(text: error, systemImage: LWSymbols.failed)
                                .accessibilityLabel("Error. \(error)")
                        }
                    }
                    .padding(LWTheme.screenPadding)
                }

                controls
                    .padding()
                    .background(.bar)
            }
            .background(LWTheme.background)
            .navigationTitle("LocalWave")
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: "wave.3.right.circle.fill")
                .font(.system(size: 54))
                .foregroundStyle(LWTheme.accent)
                .accessibilityHidden(true)
            Text(title)
                .font(.largeTitle.bold())
            Text(subtitle)
                .font(.body)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    @ViewBuilder
    private var currentStep: some View {
        switch viewModel.step {
        case .welcome:
            VStack(alignment: .leading, spacing: 12) {
                PrivacyPoint(icon: LWSymbols.frequency, title: "Pick a code", body: "Use the same Frequency Code as your team.")
                PrivacyPoint(icon: LWSymbols.people, title: "Find nearby people", body: "People appear when Bluetooth can see them.")
                PrivacyPoint(icon: LWSymbols.wake, title: "Wake or message", body: "Reach teammates when they are nearby.")
            }
        case .displayName:
            fieldCard(title: "Choose a display name") {
                TextField("Display name", text: $viewModel.displayName)
                    .textContentType(.name)
                    .textFieldStyle(.roundedBorder)
                    .accessibilityLabel("Display name")
            }
        case .frequency:
            fieldCard(title: "Enter a Frequency Code") {
                TextField("Frequency Code", text: $viewModel.channelText)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)
                    .accessibilityLabel("Frequency Code")
                Text("Use the exact same code as your nearby team.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        case .bluetooth:
            permissionCard(
                title: "Bluetooth access",
                body: "Needed to find nearby people.",
                buttonTitle: "Check Bluetooth",
                value: viewModel.bluetoothPermission.actionText
            ) {
                Task {
                    await viewModel.requestBluetoothPermission()
                }
            }
        case .notifications:
            permissionCard(
                title: "Wake and message alerts",
                body: "Optional. Needed for Wake alerts.",
                buttonTitle: "Check Notifications",
                value: viewModel.notificationPermission.actionText
            ) {
                Task {
                    await viewModel.requestNotificationPermission()
                }
            }
        }
    }

    private var controls: some View {
        HStack(spacing: 12) {
            Button {
                viewModel.back()
            } label: {
                Label("Back", systemImage: "chevron.left")
            }
            .disabled(viewModel.step == .welcome || viewModel.isCompleting)

            Spacer()

            Button {
                if viewModel.step == .notifications {
                    Task { await viewModel.finish() }
                } else {
                    viewModel.next()
                }
            } label: {
                Label(viewModel.step == .notifications ? "Start" : "Continue", systemImage: "chevron.right")
            }
            .buttonStyle(.borderedProminent)
            .controlSize(dynamicTypeSize.isAccessibilitySize ? .regular : .large)
            .disabled(!viewModel.canContinue || viewModel.isCompleting)
            .accessibilityLabel(viewModel.step == .notifications ? "Start LocalWave" : "Continue onboarding")
        }
    }

    private var title: String {
        switch viewModel.step {
        case .welcome:
            return "Start LocalWave"
        case .displayName:
            return "Your local name"
        case .frequency:
            return "Join the team code"
        case .bluetooth:
            return "Bluetooth"
        case .notifications:
            return "Stay reachable"
        }
    }

    private var subtitle: String {
        switch viewModel.step {
        case .welcome:
            return "Nearby teammates on your team code."
        case .displayName:
            return "Pick the name teammates will recognize nearby."
        case .frequency:
            return "Use the same code as your team."
        case .bluetooth:
            return "Let LocalWave scan nearby."
        case .notifications:
            return "Notifications are optional and help with Wake alerts."
        }
    }

    private func fieldCard<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                Text(title)
                    .font(.headline)
                content()
            }
        }
    }

    private func permissionCard(title: String, body: String, buttonTitle: String, value: String, action: @escaping () -> Void) -> some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 14) {
                Text(title)
                    .font(.headline)
                Text(body)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                Button(action: action) {
                    Label(value == "Allowed" ? value : buttonTitle, systemImage: value == "Allowed" ? "checkmark.circle.fill" : "checkmark.shield")
                }
                .buttonStyle(.bordered)
            }
        }
    }
}

private extension BluetoothPermissionState {
    var actionText: String {
        switch self {
        case .unknown: return "Check Bluetooth"
        case .allowed: return "Allowed"
        case .denied: return "Open Settings"
        case .unavailable: return "Unavailable"
        }
    }
}

private extension NotificationPermissionState {
    var actionText: String {
        switch self {
        case .unknown: return "Check Notifications"
        case .allowed: return "Allowed"
        case .denied: return "Open Settings"
        }
    }
}

private struct PrivacyPoint: View {
    let icon: String
    let title: String
    let detail: String

    init(icon: String, title: String, body: String) {
        self.icon = icon
        self.title = title
        self.detail = body
    }

    var body: some View {
        GlassCard {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(LWTheme.accent)
                    .frame(width: 28)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.headline)
                    Text(detail)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }
}

#Preview("Onboarding") {
    OnboardingView(viewModel: OnboardingViewModel(store: AppStore.preview(onboarded: false)))
}
