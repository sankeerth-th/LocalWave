import SwiftUI

struct SettingsView: View {
    @ObservedObject var viewModel: SettingsViewModel

    var body: some View {
        List {
            Section("Local Profile") {
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

                if let identity = viewModel.identity {
                    LabeledContent("Fingerprint", value: identity.fingerprint)
                }
            }

            Section("Privacy") {
                Label("No account or backend relay", systemImage: "person.crop.circle.badge.xmark")
                Label("Frequency Code is a logical Bluetooth channel", systemImage: LWSymbols.frequency)
                Label("Not analog RF, internet, cellular, or Wi-Fi messaging", systemImage: "wifi.slash")
            }

            Section("Permissions") {
                LabeledContent("Bluetooth", value: viewModel.permissionSnapshot.bluetooth.rawValue.capitalized)
                LabeledContent("Notifications", value: viewModel.permissionSnapshot.notifications.rawValue)
            }

            DiagnosticsSection(viewModel: viewModel)

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
