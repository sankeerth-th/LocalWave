import SwiftUI

struct PrivacyExplanationView: View {
    var body: some View {
        List {
            Section {
                privacyRow("No account", "LocalWave does not use a phone number, email, username, backend account, analytics SDK, or tracking SDK.")
                privacyRow("No server or cloud sync", "Messages are local to this app and nearby Bluetooth transport. There is no LocalWave cloud service.")
                privacyRow("No internet required", "The product flow is designed for Bluetooth-only nearby messaging.")
            }

            Section("Frequency Code") {
                Text("A Frequency Code is a private local Bluetooth channel code used by the app. It is not cellular, Wi-Fi, internet, or a real analog radio tuner.")
            }

            Section("Messages and Wake") {
                privacyRow("Encryption", "Messages are designed to be encrypted end-to-end by the core engine before Bluetooth transport.")
                privacyRow("Wake", "Wake sends a local Bluetooth ping when reachable. It depends on Bluetooth reachability, notification permission, and iOS background rules.")
            }

            Section("Local data") {
                Text("Local device data remains on this phone unless you delete it or back up the phone through normal iOS backups.")
            }
        }
        .navigationTitle("Privacy")
    }

    private func privacyRow(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.headline)
            Text(text)
                .foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }
}

