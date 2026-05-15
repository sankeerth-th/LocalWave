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
        Text("Password support will be enabled when the engine exposes private channel password storage.")
            .font(.footnote)
            .foregroundStyle(.secondary)
    }
}
