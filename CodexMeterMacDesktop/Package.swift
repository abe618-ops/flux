// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CodexMeterDesktop",
    platforms: [.macOS(.v13)],
    products: [
        .executable(name: "CodexMeterDesktop", targets: ["CodexMeterDesktop"])
    ],
    targets: [
        .executableTarget(
            name: "CodexMeterDesktop",
            path: "Sources/CodexMeterDesktop"
        )
    ]
)
