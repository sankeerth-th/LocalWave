import SwiftUI

struct PeerDetailSheet: View {
    let peer: PeerProfile
    let wakeState: WakeButtonState
    let wakeAction: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 14) {
                        PeerAvatar(name: peer.displayName, size: 58)
                        VStack(alignment: .leading, spacing: 5) {
                            Text(peer.displayName)
                                .font(.title3.bold())
                            Text(peer.state.rawValue)
                                .foregroundStyle(.secondary)
                        }
                    }
                    WakeButton(state: wakeState, action: wakeAction)
                }

                Section("Signal") {
                    HStack {
                        Text("Strength")
                        Spacer()
                        SignalStrengthView(rssi: peer.rssi)
                    }
                    LabeledContent("Last seen", value: peer.lastSeen.relativeDisplay)
                }

                Section("Identity") {
                    LabeledContent("Fingerprint", value: peer.fingerprint.redactedDiagnostic)
                }

                Section {
                    Text("Wake sends a local Bluetooth ping when the other phone is reachable. It is not guaranteed and depends on Bluetooth, notification permission, and iOS background rules.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Peer Details")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

