import SwiftUI

struct FrequencyPill: View {
    let channel: ChannelCode
    var isScanning: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pulse = false

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: LWSymbols.frequency)
                .symbolEffect(.pulse, options: .repeating, isActive: isScanning && !reduceMotion)
            Text(channel.normalized)
                .font(.headline.monospaced())
                .lineLimit(1)
                .minimumScaleFactor(0.72)
            Image(systemName: isScanning ? "dot.radiowaves.left.and.right" : "checkmark.circle.fill")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 9)
        .background(LWMaterials.prominent, in: Capsule())
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Frequency Code \(channel.normalized)")
    }
}
