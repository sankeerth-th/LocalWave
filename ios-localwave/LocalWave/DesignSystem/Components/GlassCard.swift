import SwiftUI

struct GlassCard<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .padding(16)
            .background(LWMaterials.card, in: RoundedRectangle(cornerRadius: LWTheme.cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: LWTheme.cornerRadius, style: .continuous)
                    .strokeBorder(Color.primary.opacity(0.08))
            )
    }
}

