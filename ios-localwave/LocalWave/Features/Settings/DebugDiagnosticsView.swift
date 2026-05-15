import SwiftUI

struct DebugDiagnosticsView: View {
    @ObservedObject var viewModel: SettingsViewModel

    var body: some View {
        List {
            Section("App") {
                LabeledContent("Mode", value: viewModel.appModeText)
                LabeledContent("Nearby peer count", value: "\(viewModel.nearbyPeerCount)")
            }

            Section("Transport") {
                LabeledContent("Running", value: viewModel.transportState.isRunning ? "Yes" : "No")
                LabeledContent("Scanning", value: viewModel.transportState.isScanning ? "Yes" : "No")
                LabeledContent("Advertising", value: viewModel.transportState.isAdvertising ? "Yes" : "No")
                LabeledContent("Bluetooth", value: viewModel.transportState.permission.rawValue.capitalized)
                LabeledContent("Last redacted BLE error", value: viewModel.redactedLastError)
            }

            Section("Privacy") {
                Text("Diagnostics do not show plaintext messages, private keys, shared secrets, full Frequency Codes, or raw packet bodies.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Diagnostics")
    }
}

