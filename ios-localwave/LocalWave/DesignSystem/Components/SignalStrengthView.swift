import SwiftUI

struct SignalStrengthView: View {
    let rssi: Int

    private var activeBars: Int {
        if rssi >= -55 { return 4 }
        if rssi >= -68 { return 3 }
        if rssi >= -80 { return 2 }
        return 1
    }

    var body: some View {
        HStack(alignment: .bottom, spacing: 2) {
            ForEach(1...4, id: \.self) { index in
                Capsule()
                    .fill(index <= activeBars ? LWTheme.accent : Color.secondary.opacity(0.25))
                    .frame(width: 4, height: CGFloat(5 + index * 4))
            }
        }
        .frame(width: 26, height: 24)
        .accessibilityLabel("Signal strength \(activeBars) of 4")
    }
}
