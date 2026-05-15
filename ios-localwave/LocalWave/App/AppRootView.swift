import SwiftUI

struct AppRootView: View {
    @ObservedObject var store: AppStore

    var body: some View {
        Group {
            if store.isOnboardingComplete, let channel = store.currentChannel {
                MainTabView(store: store, channel: channel)
            } else {
                OnboardingView(viewModel: OnboardingViewModel(store: store))
            }
        }
        .task {
            await store.startIfReady()
        }
    }
}

private struct MainTabView: View {
    @ObservedObject var store: AppStore
    let channel: ChannelCode
    @State private var selectedTab: MainTab = .people

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                PeopleView(
                    viewModel: PeopleViewModel(
                        environment: store.environment,
                        channel: channel,
                        displayName: store.displayName
                    ),
                    store: store
                )
            }
            .id(channel.normalized)
            .tabItem {
                Label("People", systemImage: LWSymbols.people)
            }
            .tag(MainTab.people)

            NavigationStack {
                SettingsView(viewModel: SettingsViewModel(store: store))
            }
            .tabItem {
                Label("Settings", systemImage: LWSymbols.settings)
            }
            .tag(MainTab.settings)
        }
    }
}

private enum MainTab: Hashable {
    case people
    case settings
}

#Preview("Onboarding") {
    AppRootView(store: AppStore.preview(onboarded: false))
}

#Preview("Main Dark") {
    AppRootView(store: AppStore.preview(onboarded: true))
        .preferredColorScheme(.dark)
}
