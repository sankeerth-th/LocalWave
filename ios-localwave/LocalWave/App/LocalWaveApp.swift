import SwiftUI

@main
struct LocalWaveApp: App {
    @StateObject private var store = AppStore(environment: .live)

    var body: some Scene {
        WindowGroup {
            AppRootView(store: store)
        }
    }
}
