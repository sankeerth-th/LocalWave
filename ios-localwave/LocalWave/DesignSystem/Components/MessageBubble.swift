import SwiftUI

struct MessageBubble: View {
    let message: ChatMessage
    let retry: () -> Void

    var body: some View {
        HStack {
            if message.direction == .outgoing { Spacer(minLength: 42) }

            VStack(alignment: message.direction == .outgoing ? .trailing : .leading, spacing: 5) {
                Text(message.text)
                    .font(.body)
                    .foregroundStyle(message.direction == .outgoing ? .white : .primary)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(bubbleBackground, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .accessibilityLabel(message.direction == .outgoing ? "Outgoing message" : "Incoming message")
                    .accessibilityValue(message.text)

                HStack(spacing: 6) {
                    MessageStatusView(status: message.status)
                    if message.status == .failed {
                        Button("Retry", action: retry)
                            .font(.caption.weight(.semibold))
                    }
                }
                .padding(.horizontal, 6)
            }

            if message.direction == .incoming { Spacer(minLength: 42) }
        }
    }

    private var bubbleBackground: some ShapeStyle {
        message.direction == .outgoing ? AnyShapeStyle(LWTheme.accent.gradient) : AnyShapeStyle(LWMaterials.card)
    }
}

