import Foundation
import SwiftUI
import UserNotifications
import ServiceManagement

@MainActor
final class UsageStore: ObservableObject {
    @Published var section: AppSection = .overview
    @Published var snapshot: UsageSnapshot = .empty
    @Published var history: [HistoryPoint] = []
    @Published var sessions: [LocalSession] = []
    @Published var status = "正在连接 Codex…"
    @Published var isRefreshing = false
    @Published var lastError: String?
    @Published var loginURL: String?
    @Published var loginCode: String?
    @Published var autoRefresh = true
    @Published var refreshMinutes = 3
    @Published var alertFiveBelow = 15
    @Published var alertWeekBelow = 20
    @Published var launchAtLogin = false

    private let service = CodexService.shared
    private let scanner = SessionScanner()
    private var refreshTask: Task<Void, Never>?

    init() {
        loadSettings()
        loadHistory()
        syncLaunchAtLoginState()
        startAutoRefresh()
        Task { await refresh() }
    }

    deinit { refreshTask?.cancel() }

    var totalLocalInput: Int64 { sessions.reduce(0) { $0 + $1.input } }
    var totalLocalCached: Int64 { sessions.reduce(0) { $0 + $1.cached } }
    var totalLocalOutput: Int64 { sessions.reduce(0) { $0 + $1.output } }

    var modelUsage: [ModelUsage] {
        Dictionary(grouping: sessions, by: { $0.model })
            .map { ModelUsage(model: $0.key, tokens: $0.value.reduce(0) { $0 + $1.total }) }
            .sorted { $0.tokens > $1.tokens }
    }

    var healthScore: Int? {
        guard let f = snapshot.fiveHour, let w = snapshot.weekly else { return nil }
        let pressure = max(f.usedPercent, w.usedPercent)
        return max(0, min(100, Int(100 - pressure * 0.86)))
    }

    var tokensPerWeeklyPercent: Double? {
        let pts = history.filter { $0.weekUsed != nil }
        guard let first = pts.first, let last = pts.last,
              let a = first.weekUsed, let b = last.weekUsed
        else { return nil }
        let delta = b - a
        let tokens = last.lifetimeTokens - first.lifetimeTokens
        guard delta > 0.2, tokens > 0 else { return nil }
        return Double(tokens) / delta
    }

    var weeklyBurnPerHour: Double? {
        let pts = history.suffix(12)
        guard pts.count >= 2,
              let first = pts.first,
              let last = pts.last,
              let a = first.weekUsed,
              let b = last.weekUsed
        else { return nil }
        let hours = last.capturedAt.timeIntervalSince(first.capturedAt) / 3600
        guard hours > 0.05, b >= a else { return nil }
        return (b - a) / hours
    }

    func refresh() async {
        if isRefreshing { return }
        isRefreshing = true
        status = "正在读取本机 Codex…"
        defer { isRefreshing = false }

        do {
            let snap = try await service.fetchSnapshot()
            snapshot = snap
            status = "LIVE · 本机 Codex"
            lastError = nil
            appendHistory(snap)
            sessions = await Task.detached(priority: .utility) {
                SessionScanner().scan()
            }.value
            await checkAlerts()
        } catch {
            lastError = error.localizedDescription
            status = "需要登录或 Codex 暂不可用"
        }
    }

    func startLogin() async {
        do {
            let result = try await service.beginDeviceCodeLogin()
            loginURL = result.url
            loginCode = result.code
            status = "请在浏览器完成 ChatGPT 授权"
        } catch {
            lastError = error.localizedDescription
        }
    }

    func clearLoginPrompt() {
        loginURL = nil
        loginCode = nil
    }

    func startAutoRefresh() {
        refreshTask?.cancel()
        guard autoRefresh else { return }
        refreshTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                let seconds = UInt64(max(1, self.refreshMinutes) * 60)
                try? await Task.sleep(nanoseconds: seconds * 1_000_000_000)
                if Task.isCancelled { return }
                await self.refresh()
            }
        }
        saveSettings()
    }

    func requestNotifications() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    func setLaunchAtLogin(_ enabled: Bool) {
        do {
            if enabled {
                try SMAppService.mainApp.register()
            } else {
                try SMAppService.mainApp.unregister()
            }
            launchAtLogin = enabled
        } catch {
            lastError = "开机自启设置失败：\(error.localizedDescription)"
            syncLaunchAtLoginState()
        }
        saveSettings()
    }

    func saveSettings() {
        let d = UserDefaults.standard
        d.set(autoRefresh, forKey: "autoRefresh")
        d.set(refreshMinutes, forKey: "refreshMinutes")
        d.set(alertFiveBelow, forKey: "alertFiveBelow")
        d.set(alertWeekBelow, forKey: "alertWeekBelow")
    }

    private func loadSettings() {
        let d = UserDefaults.standard
        if d.object(forKey: "autoRefresh") != nil { autoRefresh = d.bool(forKey: "autoRefresh") }
        if d.integer(forKey: "refreshMinutes") > 0 { refreshMinutes = d.integer(forKey: "refreshMinutes") }
        if d.integer(forKey: "alertFiveBelow") > 0 { alertFiveBelow = d.integer(forKey: "alertFiveBelow") }
        if d.integer(forKey: "alertWeekBelow") > 0 { alertWeekBelow = d.integer(forKey: "alertWeekBelow") }
    }

    private func syncLaunchAtLoginState() {
        launchAtLogin = SMAppService.mainApp.status == .enabled
    }

    private func historyURL() -> URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            .appendingPathComponent("CodexMeter", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("history.json")
    }

    private func loadHistory() {
        guard let data = try? Data(contentsOf: historyURL()),
              let decoded = try? JSONDecoder().decode([HistoryPoint].self, from: data)
        else { return }
        history = decoded
    }

    private func appendHistory(_ snap: UsageSnapshot) {
        let p = HistoryPoint(
            capturedAt: snap.capturedAt,
            fiveUsed: snap.fiveHour?.usedPercent,
            weekUsed: snap.weekly?.usedPercent,
            lifetimeTokens: snap.lifetimeTokens
        )
        history.append(p)
        history = history.filter { $0.capturedAt > Date().addingTimeInterval(-60 * 60 * 24 * 45) }
        if history.count > 3000 { history.removeFirst(history.count - 3000) }
        if let data = try? JSONEncoder().encode(history) {
            try? data.write(to: historyURL(), options: .atomic)
        }
    }

    private func checkAlerts() async {
        let center = UNUserNotificationCenter.current()
        func notify(_ title: String, _ body: String, id: String) {
            let content = UNMutableNotificationContent()
            content.title = title
            content.body = body
            content.sound = .default
            center.add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
        }

        if let f = snapshot.fiveHour, Int(f.remainingPercent) <= alertFiveBelow {
            notify("Codex 5小时额度偏低", "剩余 \(Int(f.remainingPercent))%，\(f.resetAt?.countdownText ?? "稍后")重置", id: "five-low")
        }
        if let w = snapshot.weekly, Int(w.remainingPercent) <= alertWeekBelow {
            notify("Codex 周额度偏低", "剩余 \(Int(w.remainingPercent))%，\(w.resetAt?.countdownText ?? "稍后")重置", id: "week-low")
        }
    }
}
