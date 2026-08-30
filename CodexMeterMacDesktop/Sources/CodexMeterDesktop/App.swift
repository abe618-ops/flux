import SwiftUI

@main
struct CodexMeterDesktopApp: App {
    @StateObject private var store = UsageStore()

    var body: some Scene {
        WindowGroup("CodexMeter") {
            RootView()
                .environmentObject(store)
                .frame(minWidth: 1040, minHeight: 700)
                .preferredColorScheme(.dark)
        }
        .windowStyle(.titleBar)
        .defaultSize(width: 1180, height: 780)

        MenuBarExtra("CodexMeter", systemImage: "gauge.with.dots.needle.67percent") {
            MenuBarView()
                .environmentObject(store)
        }

        Settings {
            DesktopSettingsView()
                .environmentObject(store)
                .frame(width: 460, height: 420)
        }
    }
}
