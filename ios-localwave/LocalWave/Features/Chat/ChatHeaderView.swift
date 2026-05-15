import SwiftUI

struct ChatHeaderView: View {
    let peer: PeerProfile
    let wakeState: WakeButtonState
    let wakeAction: () -> Void
    @State private var showFingerprint = false

    var body: some View {
        VStack(spacing: 10) {
            HStack(spacing: 12) {
                PeerAvatar(name: peer.displayName, size: 42)
                VStack(alignment: .leading, spacing: 2) {
                    Text(peer.displayName)
                        .font(.headline)
                    HStack(spacing: 6) {
                        Text(peer.state.rawValue)
                        SignalStrengthView(rssi: peer.rssi)
                            .scaleEffect(0.75)
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
                Spacer()
                Button {
                    showFingerprint = true
                } label: {
                    Image(systemName: LWSymbols.fingerprint)
                        .frame(width: 36, height: 36)
                }
                .accessibilityLabel("Show identity fingerprint")

                WakeButton(state: wakeState, compact: true, action: wakeAction)
            }
            .padding(.horizontal)
            .padding(.top, 8)
        }
        .background(LWMaterials.subtle)
        .sheet(isPresented: $showFingerprint) {
            NavigationStack {
                IdentityFingerprintView(title: peer.displayName, fingerprint: peer.fingerprint)
            }
        }
    }
}

