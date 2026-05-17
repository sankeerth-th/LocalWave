import SwiftUI

struct MessageComposerView: View {
    @Binding var text: String
    let canSend: Bool
    let send: () -> Void
    @FocusState private var focused: Bool

    var body: some View {
        HStack(alignment: .bottom, spacing: 10) {
            TextField("Message over LocalWave...", text: $text, axis: .vertical)
                .lineLimit(1...5)
                .autocorrectionDisabled()
                .textFieldStyle(.plain)
                .padding(.horizontal, 14)
                .padding(.vertical, 11)
                .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .focused($focused)
                .accessibilityLabel("Message over LocalWave")

            Button(action: send) {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 34))
            }
            .disabled(!canSend)
            .accessibilityLabel("Send message")
        }
        .padding(.horizontal)
        .padding(.vertical, 10)
        .background(LWMaterials.subtle)
    }
}
