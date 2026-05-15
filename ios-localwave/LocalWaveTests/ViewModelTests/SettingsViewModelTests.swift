import XCTest
@testable import LocalWave

@MainActor
final class SettingsViewModelTests: XCTestCase {
    func testSwitchChannelRejectsInvalidCode() async {
        let store = makeStore()
        let viewModel = SettingsViewModel(store: store)
        viewModel.channelText = "A"

        await viewModel.switchChannel()

        XCTAssertEqual(viewModel.errorMessage, LocalWaveError.invalidChannelCode.localizedDescription)
    }

    func testDisplayNameUpdatePersists() async {
        let store = makeStore()
        let viewModel = SettingsViewModel(store: store)
        viewModel.displayName = "Dock Lead"

        await viewModel.saveDisplayName()

        XCTAssertEqual(store.displayName, "Dock Lead")
        XCTAssertNil(viewModel.errorMessage)
    }

    func testDiagnosticsRedactsLongError() {
        let raw = "Peripheral write failed for channel DOCK-A-17 with packet body 1234567890"

        XCTAssertFalse(raw.redactedDiagnostic.contains("1234567890"))
        XCTAssertTrue(raw.redactedDiagnostic.contains("Peripheral write"))
    }

    private func makeStore() -> AppStore {
        AppStore(environment: .mock, defaults: UserDefaults(suiteName: "localwave.settings.tests.\(UUID().uuidString)")!)
    }
}

