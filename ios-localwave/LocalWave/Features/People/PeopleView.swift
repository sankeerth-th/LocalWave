import SwiftUI

struct PeopleView: View {
    @StateObject private var viewModel: PeopleViewModel
    @ObservedObject var store: AppStore
    @State private var detailPeer: PeerProfile?
    @State private var showingSettings = false

    init(viewModel: PeopleViewModel, store: AppStore) {
        _viewModel = StateObject(wrappedValue: viewModel)
        self.store = store
    }

    var body: some View {
        ZStack {
            LWTheme.background
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    FrequencyHeaderView(
                        channel: viewModel.channel,
                        status: viewModel.scanningStatusText,
                        isScanning: viewModel.transportState.isScanning,
                        mode: viewModel.environment.mode
                    )

                    if viewModel.needsBluetoothBanner {
                        PermissionBanner(text: "Bluetooth is off or permission is missing. Turn it on to find nearby people.")
                    }

                    SectionHeader(title: "Nearby", subtitle: nil)

                    if viewModel.peers.isEmpty {
                        EmptyStateView(
                            title: "No one nearby yet.",
                            message: "Keep LocalWave open on both phones with the same Frequency Code."
                        )
                    } else {
                        LazyVStack(spacing: 10) {
                            ForEach(viewModel.peers) { peer in
                                NavigationLink(value: peer) {
                                    PeerRowView(
                                        peer: peer,
                                        wakeState: viewModel.wakeState(for: peer),
                                        wakeAction: {
                                            Task { await viewModel.sendWake(to: peer) }
                                        },
                                        detailAction: { detailPeer = peer }
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(LWTheme.screenPadding)
            }
        }
        .navigationTitle("LocalWave")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showingSettings = true
                } label: {
                    Image(systemName: LWSymbols.settings)
                }
                .accessibilityLabel("Settings")
            }
        }
        .navigationDestination(for: PeerProfile.self) { peer in
            ChatView(viewModel: ChatViewModel(environment: store.environment, peer: peer))
        }
        .sheet(item: $detailPeer) { peer in
            PeerDetailSheet(
                peer: peer,
                wakeState: viewModel.wakeState(for: peer),
                wakeAction: {
                    Task { await viewModel.sendWake(to: peer) }
                }
            )
        }
        .sheet(isPresented: $showingSettings) {
            NavigationStack {
                SettingsView(viewModel: SettingsViewModel(store: store))
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("Done") {
                                showingSettings = false
                            }
                        }
                    }
            }
        }
        .task { viewModel.start() }
        .onDisappear { viewModel.stop() }
    }
}

#Preview("People Dark") {
    NavigationStack {
        PeopleView(
            viewModel: PeopleViewModel(environment: .mock, channel: .sample, displayName: "Maya"),
            store: AppStore.preview(onboarded: true)
        )
    }
    .preferredColorScheme(.dark)
}
