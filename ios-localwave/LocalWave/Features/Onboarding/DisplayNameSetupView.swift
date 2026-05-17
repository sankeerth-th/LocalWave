import SwiftUI

struct DisplayNameSetupView: View {
    @Binding var displayName: String

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            Spacer()
            SectionHeader(
                title: "Choose your display name",
                subtitle: "Nearby teammates on the same Frequency Code will see this name."
            )

            TextField("Display name", text: $displayName)
                .textInputAutocapitalization(.words)
                .autocorrectionDisabled()
                .submitLabel(.next)
                .textFieldStyle(.roundedBorder)
                .accessibilityLabel("Display name")

            PermissionBanner(
                text: "Use a role or first name your team recognizes. LocalWave does not create an account."
            )
            Spacer()
        }
        .padding(LWTheme.screenPadding)
    }
}
