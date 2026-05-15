import SwiftUI

enum LWTheme {
    static let cornerRadius: CGFloat = 18
    static let compactRadius: CGFloat = 12
    static let screenPadding: CGFloat = 20
    static let rowSpacing: CGFloat = 12

    static let accent = Color.cyan
    static let success = Color.green
    static let warning = Color.orange
    static let danger = Color.red
    static let muted = Color.secondary

    static var background: some View {
        LocalWaveBackground()
    }
}

private struct LocalWaveBackground: View {
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        LinearGradient(
            colors: colors,
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }

    private var colors: [Color] {
        if colorScheme == .dark {
            return [
                Color(red: 0.03, green: 0.04, blue: 0.05),
                Color(red: 0.06, green: 0.08, blue: 0.10),
                Color(uiColor: .systemBackground)
            ]
        }

        return [
            Color(uiColor: .systemBackground),
            Color(uiColor: .secondarySystemBackground)
        ]
    }
}
