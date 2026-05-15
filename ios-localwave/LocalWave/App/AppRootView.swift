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

    var body: some View {
        TabView {
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
            .tabItem {
                Label("Frequency", systemImage: LWSymbols.frequency)
            }

            NavigationStack {
                SettingsView(viewModel: SettingsViewModel(store: store))
            }
            .tabItem {
                Label("Settings", systemImage: LWSymbols.settings)
            }
        }
    }
}

#Preview("Onboarding") {
    AppRootView(store: AppStore.preview(onboarded: false))
}

#Preview("Main Dark") {
    AppRootView(store: AppStore.preview(onboarded: true))
        .preferredColorScheme(.dark)
}
