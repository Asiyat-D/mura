// swift-tools-version: 6.0
import PackageDescription
let package = Package(
    name: "MuraCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "MuraCore", targets: ["MuraCore"]), .executable(name: "MuraInterop", targets: ["MuraInterop"]), .executable(name: "MuraChatInterop", targets: ["MuraChatInterop"])],
    targets: [
        .target(name: "MuraCore", linkerSettings: [.linkedLibrary("z")]),
        .executableTarget(name: "MuraInterop", dependencies: ["MuraCore"]),
        .executableTarget(name: "MuraChatInterop", dependencies: ["MuraCore"]),
        .testTarget(name: "MuraCoreTests", dependencies: ["MuraCore"], resources: [.process("Fixtures")])
    ]
)
