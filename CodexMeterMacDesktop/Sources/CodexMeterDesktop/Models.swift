import Foundation

struct QuotaWindow: Codable, Hashable {
    var usedPercent: Double
    var resetAt: Date?
    var windowMinutes: Int
    var remainingPercent: Double { max(0, min(100, 100 - usedPercent)) }
}

struct DailyToken: Codable, Identifiable, Hashable {
    var date: Date
    var tokens: Int64
    var id: Date { date }
}

struct LocalSession: Codable, Identifiable, Hashable {
    var id: String
    var title: String
    var modifiedAt: Date
    var model: String
    var input: Int64
    var cached: Int64
    var output: Int64
    var total: Int64
}

struct UsageSnapshot: Codable, Hashable {
    var capturedAt: Date
    var fiveHour: QuotaWindow?
    var weekly: QuotaWindow?
    var email: String
    var plan: String
    var lifetimeTokens: Int64
    var daily: [DailyToken]
    var source: String

    static let empty = UsageSnapshot(
        capturedAt: .now,
        fiveHour: nil,
        weekly: nil,
        email: "—",
        plan: "—",
        lifetimeTokens: 0,
        daily: [],
        source: "No data"
    )

    func tokensLast(days: Int) -> Int64 {
        let cal = Calendar.current
        let start = cal.startOfDay(for: .now)
        guard let cutoff = cal.date(byAdding: .day, value: -(days - 1), to: start) else { return 0 }
        return daily.filter { $0.date >= cutoff }.reduce(0) { $0 + $1.tokens }
    }
}

struct HistoryPoint: Codable, Identifiable, Hashable {
    var capturedAt: Date
    var fiveUsed: Double?
    var weekUsed: Double?
    var lifetimeTokens: Int64
    var id: Date { capturedAt }
}

struct ModelUsage: Identifiable, Hashable {
    var model: String
    var tokens: Int64
    var id: String { model }
}

enum AppSection: String, CaseIterable, Identifiable {
    case overview = "总览"
    case tokens = "Token"
    case sessions = "任务 / 会话"
    case trends = "趋势"
    case models = "模型"
    case alerts = "提醒"
    case diagnostics = "诊断"

    var id: String { rawValue }

    var symbol: String {
        switch self {
        case .overview: return "square.grid.2x2"
        case .tokens: return "gauge.with.dots.needle.67percent"
        case .sessions: return "list.bullet.rectangle"
        case .trends: return "chart.xyaxis.line"
        case .models: return "cpu"
        case .alerts: return "bell.badge"
        case .diagnostics: return "stethoscope"
        }
    }
}

extension Int64 {
    var compact: String {
        let n = Double(self)
        if self >= 1_000_000_000 { return String(format: "%.2fB", n / 1_000_000_000) }
        if self >= 1_000_000 { return String(format: "%.1fM", n / 1_000_000) }
        if self >= 1_000 { return String(format: "%.1fK", n / 1_000) }
        return "\(self)"
    }
}

extension Date {
    var countdownText: String {
        let sec = max(0, Int(timeIntervalSinceNow))
        let d = sec / 86400
        let h = (sec % 86400) / 3600
        let m = (sec % 3600) / 60
        if d > 0 { return "\(d)天 \(h)小时" }
        if h > 0 { return "\(h)小时 \(m)分" }
        return "\(m)分"
    }
}
