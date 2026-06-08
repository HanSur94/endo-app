// Task-1 structural de-risk: the package-test-in-scheme SMOKE.
//
// This single @Test exists to PROVE the EndoInsights Swift Testing target actually executes inside
// `xcodebuild test -scheme EndoApp` (SC3 mechanism). The suite name is deliberately DISTINCTIVE so
// it can be grepped in the test log to confirm the package test target ran (not green-by-omission).
import Testing

@Suite("EndoInsights wiring smoke")
struct SmokeTests {
    @Test func packageTestTargetRunsInScheme() {
        #expect(Bool(true))
    }
}
