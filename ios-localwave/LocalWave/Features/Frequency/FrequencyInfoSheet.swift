import SwiftUI

struct FrequencyInfoSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("What it is") {
                    Text("A Frequency Code is a logical private Bluetooth channel. Devices using the same code can discover each other nearby and exchange encrypted LocalWave packets.")
                }

                Section("What it is not") {
                    Label("Not analog RF or a radio tuner", systemImage: "radio")
                    Label("Not internet or Wi-Fi messaging", systemImage: "wifi.slash")
                    Label("Not cellular service or SMS", systemImage: "antenna.radiowaves.left.and.right.slash")
                }

                Section("Privacy") {
                    Text("Your display name and device keys are local to this phone. The code limits nearby Bluetooth discovery, but anyone with the same code and a compatible LocalWave app can try to join.")
                }
            }
            .navigationTitle("Frequency Code")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}
