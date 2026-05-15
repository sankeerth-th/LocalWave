import SwiftUI

struct PermissionBanner: View {
    let text: String
    var systemImage = LWSymbols.bluetooth

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: systemImage)
                .font(.title3)
                .foregroundStyle(LWTheme.accent)
                .frame(width: 28)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.primary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .background(LWMaterials.subtle, in: RoundedRectangle(cornerRadius: LWTheme.compactRadius, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

