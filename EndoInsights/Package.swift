// swift-tools-version:6.0
//
// EndoInsights — the Foundation-only insights package (Phase 5, D-09).
//
// THE SC1 COMPILER BOUNDARY: this manifest declares ZERO dependencies. With no
// SwiftData/SwiftUI/UIKit/CoreData in the dependency graph, any such `import` inside the
// `EndoInsights` target fails to compile — the boundary is enforced by the linker, not by
// convention. The app maps its SwiftData `@Model` rows → `[DaySnapshot]` at the Repository
// seam (app-side, Plan 03); the package never sees the store.
//
// `platforms: [.iOS(.v18)]` MUST match the app's deployment target (project.yml: iOS "18.0")
// so the package builds for the iOS Simulator destination the app scheme uses.
import PackageDescription

let package = Package(
    name: "EndoInsights",
    platforms: [.iOS(.v18)],
    products: [
        .library(name: "EndoInsights", targets: ["EndoInsights"]),
    ],
    targets: [
        // NO dependencies → cannot link SwiftData/SwiftUI. THIS is the SC1 boundary.
        .target(name: "EndoInsights"),
        .testTarget(name: "EndoInsightsTests", dependencies: ["EndoInsights"]),
    ]
)
