import SwiftUI

struct ChatListView: View {
    @Bindable var viewModel: ChatListViewModel
    let environment: AppEnvironment

    var body: some View {
        List {
            if viewModel.peers.isEmpty {
                ContentUnavailableView(
                    "No Conversations",
                    systemImage: "bubble.left.and.bubble.right",
                    description: Text("Nearby teammates using the same Frequency Code will appear here.")
                )
            } else {
                Section {
                    ForEach(viewModel.peers) { peer in
                        NavigationLink {
                            ChatView(viewModel: ChatViewModel(environment: environment, peer: peer))
                        } label: {
                            ChatPeerRow(peer: peer)
                        }
                        .accessibilityLabel("Open chat with \(peer.displayName), \(peer.state.rawValue)")
                    }
                } header: {
                    Text("Nearby People")
                }
            }
        }
        .navigationTitle("Chat")
        .task {
            viewModel.start()
        }
    }
}

private struct ChatPeerRow: View {
    let peer: PeerProfile

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: peer.state.statusIcon)
                .foregroundStyle(peer.state.statusTint)
                .frame(width: 28)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 3) {
                Text(peer.displayName)
                    .font(.headline)
                Text(peer.state.rawValue)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

#Preview("Chat List") {
    let environment = AppEnvironment.mock
    NavigationStack {
        ChatListView(viewModel: ChatListViewModel(environment: environment), environment: environment)
    }
}
