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
                Text("Start LocalWave")
                    .font(.largeTitle.bold())
                    .multilineTextAlignment(.center)
                    .minimumScaleFactor(0.72)
                Text("Join teammates on the same Frequency Code.")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }

            GlassCard {
                VStack(alignment: .leading, spacing: 12) {
                    Label("Find nearby teammates", systemImage: LWSymbols.bluetooth)
                    Label("Use the team Frequency Code", systemImage: LWSymbols.frequency)
                    Label("Wake or message when reachable", systemImage: LWSymbols.wake)
                }
                .font(.subheadline)
            }
            Spacer()
        }
        .padding(LWTheme.screenPadding)
    }
}
