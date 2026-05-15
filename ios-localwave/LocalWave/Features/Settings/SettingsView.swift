import SwiftUI

struct SettingsView: View {
    @ObservedObject var viewModel: SettingsViewModel

    var body: some View {
        List {
            Section("Profile") {
                TextField("Display Name", text: $viewModel.displayName)
                    .textContentType(.name)
                    .accessibilityLabel("Display name")
                Button {
                    Task { await viewModel.saveDisplayName() }
                } label: {
                    Label("Save Display Name", systemImage: "checkmark.circle")
                }

                TextField("Frequency Code", text: $viewModel.channelText)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .accessibilityLabel("Frequency Code")
                Button {
                    Task { await viewModel.switchChannel() }
                } label: {
                    Label("Switch Frequency Code", systemImage: LWSymbols.frequency)
                }

            }

            Section("Security") {
                if let identity = viewModel.identity {
                    NavigationLink {
                        IdentityFingerprintView(title: "My Identity", fingerprint: identity.fingerprint)
                    } label: {
                        Label("Identity Fingerprint", systemImage: LWSymbols.fingerprint)
                    }
                }
                NavigationLink {
                    PrivacyExplanationView()
                } label: {
                    Label("Privacy", systemImage: LWSymbols.privacy)
                }
            }

            Section("Permissions") {
                LabeledContent("Bluetooth", value: viewModel.permissionSnapshot.bluetooth.settingsText)
                LabeledContent("Notifications", value: viewModel.permissionSnapshot.notifications.settingsText)
            }

            Section {
                NavigationLink {
                    DebugDiagnosticsView(viewModel: viewModel)
                } label: {
                    Label("Diagnostics", systemImage: LWSymbols.diagnostics)
                }
            }

            if let error = viewModel.errorMessage {
                Section {
                    Label(error, systemImage: LWSymbols.failed)
                        .foregroundStyle(LWTheme.danger)
                }
                .accessibilityLabel("Settings error. \(error)")
            }
        }
        .navigationTitle("Settings")
        .task {
            viewModel.start()
        }
    }
}

private extension BluetoothPermissionState {
    var settingsText: String {
        switch self {
        case .unknown: return "Not checked"
        case .allowed: return "Allowed"
        case .denied: return "Denied"
        case .unavailable: return "Unavailable"
        }
    }
}

private extension NotificationPermissionState {
    var settingsText: String {
        switch self {
        case .unknown: return "Not checked"
        case .allowed: return "Allowed"
        case .denied: return "Denied"
        }
    }
}

private struct DiagnosticsSection: View {
    @ObservedObject var viewModel: SettingsViewModel

    var body: some View {
        Section("Diagnostics") {
            LabeledContent("App Mode", value: viewModel.appModeText)
            LabeledContent("Nearby Peers", value: "\(viewModel.nearbyPeerCount)")
            LabeledContent("Bluetooth Permission", value: viewModel.transportState.permission.rawValue.capitalized)
            LabeledContent("Scanning", value: viewModel.transportState.isScanning ? "On" : "Off")
            LabeledContent("Advertising", value: viewModel.transportState.isAdvertising ? "On" : "Off")
            LabeledContent("Transport", value: viewModel.transportState.isRunning ? "Running" : "Stopped")
            LabeledContent("Last Error", value: viewModel.redactedLastError)
        }
    }
}

#Preview("Settings") {
    NavigationStack {
        SettingsView(viewModel: SettingsViewModel(store: AppStore.preview(onboarded: true)))
    }
}
