import SwiftUI

struct ChannelSettingsView: View {
    @Binding var channelText: String
    let save: () -> Void

    var body: some View {
        TextField("Frequency Code", text: $channelText)
            .textInputAutocapitalization(.characters)
            .autocorrectionDisabled()
            .accessibilityLabel("Frequency Code")

        Button("Change Frequency Code", action: save)

        LabeledContent("Private Channel Password") {
            Text("Coming soon")
                .foregroundStyle(.secondary)
        }
        Text("Password support will be enabled only when Thread 1 exposes it in the engine contract.")
            .font(.footnote)
            .foregroundStyle(.secondary)
    }
}

