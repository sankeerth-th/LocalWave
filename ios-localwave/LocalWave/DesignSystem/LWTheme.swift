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
        LinearGradient(
            colors: [
                Color(uiColor: .systemBackground),
                Color(uiColor: .secondarySystemBackground)
            ],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }
}

