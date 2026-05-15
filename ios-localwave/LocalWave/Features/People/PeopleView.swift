import SwiftUI

struct PeopleView: View {
    @StateObject private var viewModel: PeopleViewModel
    @ObservedObject var store: AppStore
    @State private var detailPeer: PeerProfile?

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
                        isScanning: viewModel.transportState.isScanning
                    )

                    if viewModel.needsBluetoothBanner {
                        PermissionBanner(text: "Bluetooth is needed to find nearby LocalWave users. No internet or account is used.")
                    }

                    SectionHeader(title: "Nearby People", subtitle: "People using LocalWave with the same Frequency Code.")

                    if viewModel.peers.isEmpty {
                        EmptyStateView(
                            title: "No one on this frequency yet.",
                            message: "Ask teammates to install LocalWave and enter the same Frequency Code."
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

