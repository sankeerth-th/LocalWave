import SwiftUI

struct FrequencyJoinView: View {
    @Binding var channelText: String

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            Spacer()
            SectionHeader(
                title: "Enter a Frequency Code",
                subtitle: "Everyone who should find each other needs the same code."
            )

            TextField("DOCK-A-17", text: $channelText)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .submitLabel(.next)
                .textFieldStyle(.roundedBorder)
                .accessibilityLabel("Frequency Code")

            PermissionBanner(
                text: "This is a private local Bluetooth channel code used by LocalWave. It is not cellular, Wi-Fi, internet, or a real radio tuner.",
                systemImage: LWSymbols.lock
            )
            Spacer()
        }
        .padding(LWTheme.screenPadding)
    }
}
