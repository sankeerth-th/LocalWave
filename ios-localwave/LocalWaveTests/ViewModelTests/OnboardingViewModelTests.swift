import XCTest
@testable import LocalWave

@MainActor
final class OnboardingViewModelTests: XCTestCase {
    func testDisplayNameValidationBlocksContinue() {
        let viewModel = OnboardingViewModel(store: makeStore())
        viewModel.step = .displayName
        viewModel.displayName = "   "

        viewModel.next()

        XCTAssertEqual(viewModel.step, .displayName)
        XCTAssertEqual(viewModel.errorMessage, LocalWaveError.displayNameRequired.localizedDescription)
    }

    func testInvalidFrequencyCodeBlocksContinue() {
        let viewModel = OnboardingViewModel(store: makeStore())
        viewModel.step = .frequency
        viewModel.channelText = "A"

        viewModel.next()

        XCTAssertEqual(viewModel.step, .frequency)
        XCTAssertEqual(viewModel.errorMessage, LocalWaveError.invalidChannelCode.localizedDescription)
    }

    func testFinishStoresNormalizedChannel() async {
        let store = makeStore()
        let viewModel = OnboardingViewModel(store: store)
        viewModel.displayName = " Maya "
        viewModel.channelText = " dock-a-17 "

        await viewModel.finish()

        XCTAssertTrue(store.isOnboardingComplete)
        XCTAssertEqual(store.displayName, "Maya")
        XCTAssertEqual(store.channelCodeText, "DOCK-A-17")
    }

    private func makeStore() -> AppStore {
        AppStore(environment: .mock, defaults: UserDefaults(suiteName: "localwave.onboarding.tests.\(UUID().uuidString)")!)
    }
}

