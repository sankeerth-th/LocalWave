import SwiftUI

struct WelcomeView: View {
    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: LWSymbols.frequency)
                .font(.system(size: 72, weight: .semibold))
                .foregroundStyle(LWTheme.accent)
                .accessibilityHidden(true)

            VStack(spacing: 10) {
                Text("No internet. No signup. Nearby only.")
                    .font(.largeTitle.bold())
                    .multilineTextAlignment(.center)
                    .minimumScaleFactor(0.72)
                Text("Join a private local Bluetooth channel with a display name and Frequency Code.")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }

            GlassCard {
                VStack(alignment: .leading, spacing: 12) {
                    Label("Bluetooth-only nearby discovery", systemImage: LWSymbols.bluetooth)
                    Label("No account, phone number, email, server, cloud, or analytics", systemImage: LWSymbols.privacy)
                    Label("Wake sends a local Bluetooth ping when reachable", systemImage: LWSymbols.wake)
                }
                .font(.subheadline)
            }
            Spacer()
        }
        .padding(LWTheme.screenPadding)
    }
}

