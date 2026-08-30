import SwiftUI
import Charts
import AppKit

private let bg = Color(red: 0.035, green: 0.047, blue: 0.063)
private let panel = Color(red: 0.065, green: 0.087, blue: 0.12)
private let panel2 = Color(red: 0.085, green: 0.113, blue: 0.15)
private let mint = Color(red: 0.22, green: 0.91, blue: 0.65)
private let cyan = Color(red: 0.36, green: 0.84, blue: 1.0)
private let muted = Color(red: 0.55, green: 0.62, blue: 0.68)

struct RootView: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        NavigationSplitView {
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12).fill(mint)
                        Image(systemName: "terminal.fill")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(Color.black.opacity(0.8))
                    }
                    .frame(width: 40, height: 40)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("CodexMeter")
                            .font(.headline)
                        Text("Desktop")
                            .font(.caption2)
                            .foregroundStyle(muted)
                    }
                    Spacer()
                }
                .padding(16)

                List(AppSection.allCases, selection: $store.section) { section in
                    Label(section.rawValue, systemImage: section.symbol)
                        .tag(section)
                        .padding(.vertical, 4)
                }
                .listStyle(.sidebar)

                Spacer()
                Divider()
                HStack {
                    Circle()
                        .fill(store.snapshot.source == "No data" ? Color.orange : mint)
                        .frame(width: 8, height: 8)
                    Text(store.status)
                        .font(.caption)
                        .foregroundStyle(muted)
                        .lineLimit(1)
                    Spacer()
                }
                .padding(14)
            }
            .navigationSplitViewColumnWidth(min: 200, ideal: 220, max: 250)
        } detail: {
            ZStack {
                bg.ignoresSafeArea()
                VStack(spacing: 0) {
                    DesktopHeader()
                    Group {
                        switch store.section {
                        case .overview: OverviewView()
                        case .tokens: TokenView()
                        case .sessions: SessionsView()
                        case .trends: TrendsView()
                        case .models: ModelsView()
                        case .alerts: AlertsView()
                        case .diagnostics: DiagnosticsView()
                        }
                    }
                }
            }
        }
    }
}

struct DesktopHeader: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(store.section.rawValue)
                    .font(.system(size: 24, weight: .bold))
                Text(store.snapshot.email == "—" ? store.status : "\(store.snapshot.plan) · \(store.snapshot.email)")
                    .font(.caption)
                    .foregroundStyle(muted)
            }
            Spacer()

            if store.isRefreshing {
                ProgressView().controlSize(.small)
            }

            Button {
                Task { await store.refresh() }
            } label: {
                Label("刷新", systemImage: "arrow.clockwise")
            }
            .keyboardShortcut("r", modifiers: [.command])
        }
        .padding(.horizontal, 26)
        .padding(.vertical, 16)
        .background(bg.opacity(0.98))
    }
}

struct OverviewView: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if store.snapshot.fiveHour == nil && store.snapshot.weekly == nil {
                    LoginCard()
                }

                HStack(spacing: 16) {
                    QuotaCard(title: "5 HOUR WINDOW", window: store.snapshot.fiveHour, accent: mint)
                    QuotaCard(title: "WEEKLY WINDOW", window: store.snapshot.weekly, accent: cyan)
                }

                HStack(spacing: 16) {
                    HealthCard()
                    TokenSummaryCard()
                }

                HStack(spacing: 16) {
                    VelocityCard()
                    IntelligenceCard()
                }

                if let error = store.lastError {
                    InfoCard(title: "最近错误", icon: "exclamationmark.triangle") {
                        Text(error)
                            .foregroundStyle(Color.orange)
                            .textSelection(.enabled)
                    }
                }
            }
            .padding(24)
        }
    }
}

struct LoginCard: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        InfoCard(title: "连接 Codex", icon: "person.crop.circle.badge.checkmark") {
            HStack(alignment: .center, spacing: 18) {
                VStack(alignment: .leading, spacing: 7) {
                    Text("使用你的 ChatGPT 账号登录 Codex")
                        .font(.title3.bold())
                    Text("登录只发生在本机 Codex。CodexMeter 不保存你的 ChatGPT 密码。")
                        .font(.caption)
                        .foregroundStyle(muted)
                }
                Spacer()
                if let code = store.loginCode, let url = store.loginURL {
                    VStack(alignment: .trailing, spacing: 8) {
                        Text(code)
                            .font(.system(size: 26, weight: .heavy, design: .monospaced))
                            .foregroundStyle(mint)
                        Button("打开授权页面") {
                            if let u = URL(string: url) { NSWorkspace.shared.open(u) }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(mint)
                    }
                } else {
                    Button("使用 ChatGPT 登录") {
                        Task { await store.startLogin() }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(mint)
                }
            }
        }
    }
}

struct QuotaCard: View {
    let title: String
    let window: QuotaWindow?
    let accent: Color

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text(title)
                        .font(.caption.bold())
                        .tracking(1.1)
                        .foregroundStyle(muted)
                    Spacer()
                    if let window {
                        Text("\(Int(window.usedPercent))% USED")
                            .font(.caption2.monospacedDigit())
                            .foregroundStyle(muted)
                    }
                }

                HStack(alignment: .firstTextBaseline, spacing: 7) {
                    Text(window.map { "\(Int($0.remainingPercent))" } ?? "—")
                        .font(.system(size: 52, weight: .heavy, design: .rounded))
                        .foregroundStyle(accent)
                        .monospacedDigit()
                    Text("% LEFT")
                        .font(.caption.bold())
                        .foregroundStyle(muted)
                }

                ProgressView(value: window?.remainingPercent ?? 0, total: 100)
                    .tint(accent)
                    .scaleEffect(x: 1, y: 1.5)

                HStack {
                    Image(systemName: "clock")
                    Text(window?.resetAt.map { "\($0.countdownText) 后重置" } ?? "等待真实数据")
                }
                .font(.caption)
                .foregroundStyle(muted)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

struct HealthCard: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        Card {
            HStack(spacing: 18) {
                ZStack {
                    Circle()
                        .stroke(panel2, lineWidth: 12)
                    if let score = store.healthScore {
                        Circle()
                            .trim(from: 0, to: CGFloat(score) / 100)
                            .stroke(mint, style: StrokeStyle(lineWidth: 12, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                        Text("\(score)")
                            .font(.system(size: 30, weight: .heavy, design: .rounded))
                    } else {
                        Text("—")
                            .font(.system(size: 30, weight: .heavy))
                    }
                }
                .frame(width: 108, height: 108)

                VStack(alignment: .leading, spacing: 6) {
                    Text("CODEX HEALTH")
                        .font(.caption.bold())
                        .foregroundStyle(muted)
                    Text(healthLabel)
                        .font(.title2.bold())
                    Text("综合 5H、Weekly 与近期消耗速度")
                        .font(.caption)
                        .foregroundStyle(muted)
                }
                Spacer()
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var healthLabel: String {
        guard let s = store.healthScore else { return "等待数据" }
        if s >= 85 { return "Plenty" }
        if s >= 70 { return "Good" }
        if s >= 50 { return "Moderate" }
        if s >= 30 { return "Tight" }
        return "Critical"
    }
}

struct TokenSummaryCard: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 16) {
                SectionLabel("TOKEN ACTIVITY", symbol: "memorychip")
                HStack(spacing: 24) {
                    MiniMetric(label: "今日", value: store.snapshot.tokensLast(days: 1).compact)
                    MiniMetric(label: "本周", value: store.snapshot.tokensLast(days: 7).compact)
                    MiniMetric(label: "30天", value: store.snapshot.tokensLast(days: 30).compact)
                    MiniMetric(label: "累计", value: store.snapshot.lifetimeTokens.compact)
                }
                Divider().overlay(Color.white.opacity(0.08))
                HStack {
                    Text("本地会话扫描")
                        .font(.caption)
                        .foregroundStyle(muted)
                    Spacer()
                    Text("\(store.sessions.count) sessions")
                        .font(.caption.monospacedDigit())
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

struct VelocityCard: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 12) {
                SectionLabel("14-DAY TOKEN VELOCITY", symbol: "chart.xyaxis.line")
                Chart(store.snapshot.daily.suffix(14)) { item in
                    AreaMark(
                        x: .value("Date", item.date),
                        y: .value("Tokens", item.tokens)
                    )
                    .foregroundStyle(
                        LinearGradient(colors: [mint.opacity(0.42), mint.opacity(0.03)], startPoint: .top, endPoint: .bottom)
                    )
                    LineMark(
                        x: .value("Date", item.date),
                        y: .value("Tokens", item.tokens)
                    )
                    .foregroundStyle(mint)
                    .lineStyle(StrokeStyle(lineWidth: 2.5))
                }
                .chartYAxis {
                    AxisMarks(position: .leading)
                }
                .frame(height: 170)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

struct IntelligenceCard: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 15) {
                SectionLabel("USAGE INTELLIGENCE", symbol: "sparkles")
                IntelligenceRow(
                    icon: "percent",
                    title: "UC ↔ Token",
                    value: store.tokensPerWeeklyPercent.map { "1% ≈ \(Int64($0).compact)" } ?? "正在积累快照"
                )
                IntelligenceRow(
                    icon: "speedometer",
                    title: "Weekly burn",
                    value: store.weeklyBurnPerHour.map { String(format: "%.2f%% / h", $0) } ?? "数据不足"
                )
                IntelligenceRow(
                    icon: "externaldrive.badge.timemachine",
                    title: "历史快照",
                    value: "\(store.history.count)"
                )
                IntelligenceRow(
                    icon: "cpu",
                    title: "本地模型",
                    value: "\(store.modelUsage.count)"
                )
            }
        }
        .frame(maxWidth: .infinity)
    }
}

struct TokenView: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                HStack(spacing: 16) {
                    BigMetricCard(label: "TODAY", value: store.snapshot.tokensLast(days: 1).compact, accent: mint)
                    BigMetricCard(label: "7 DAYS", value: store.snapshot.tokensLast(days: 7).compact, accent: cyan)
                    BigMetricCard(label: "30 DAYS", value: store.snapshot.tokensLast(days: 30).compact, accent: .orange)
                    BigMetricCard(label: "LIFETIME", value: store.snapshot.lifetimeTokens.compact, accent: .white)
                }

                InfoCard(title: "每日 Token", icon: "chart.bar.xaxis") {
                    Chart(store.snapshot.daily.suffix(30)) { item in
                        BarMark(
                            x: .value("Date", item.date, unit: .day),
                            y: .value("Tokens", item.tokens)
                        )
                        .foregroundStyle(mint.gradient)
                        .cornerRadius(3)
                    }
                    .frame(height: 300)
                }

                InfoCard(title: "本地会话 Token 拆分", icon: "chart.bar.fill") {
                    VStack(spacing: 12) {
                        BreakdownRow(label: "Input", value: store.totalLocalInput, total: localTotal, color: cyan)
                        BreakdownRow(label: "Cached", value: store.totalLocalCached, total: localTotal, color: mint)
                        BreakdownRow(label: "Output", value: store.totalLocalOutput, total: localTotal, color: .orange)
                        Text("此拆分来自 ~/.codex/sessions 本地日志扫描；账户级官方 Usage 未返回拆分时不会伪造。")
                            .font(.caption2)
                            .foregroundStyle(muted)
                    }
                }
            }
            .padding(24)
        }
    }

    private var localTotal: Int64 {
        store.totalLocalInput + store.totalLocalCached + store.totalLocalOutput
    }
}

struct SessionsView: View {
    @EnvironmentObject var store: UsageStore
    @State private var query = ""

    var filtered: [LocalSession] {
        if query.isEmpty { return store.sessions }
        return store.sessions.filter {
            $0.title.localizedCaseInsensitiveContains(query) ||
            $0.model.localizedCaseInsensitiveContains(query)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                TextField("搜索任务、模型…", text: $query)
                    .textFieldStyle(.roundedBorder)
                    .frame(maxWidth: 340)
                Spacer()
                Text("\(filtered.count) sessions")
                    .font(.caption)
                    .foregroundStyle(muted)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 12)

            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(filtered) { session in
                        SessionRow(session: session, maxTokens: maxSessionTokens)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 24)
            }
        }
    }

    private var maxSessionTokens: Int64 {
        max(1, filtered.map(\.total).max() ?? 1)
    }
}

struct SessionRow: View {
    let session: LocalSession
    let maxTokens: Int64

    var body: some View {
        Card {
            HStack(spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(session.title)
                        .font(.headline)
                        .lineLimit(1)
                    HStack(spacing: 10) {
                        Label(session.model, systemImage: "cpu")
                        Text(session.modifiedAt.formatted(date: .abbreviated, time: .shortened))
                    }
                    .font(.caption)
                    .foregroundStyle(muted)
                    ProgressView(value: Double(session.total), total: Double(maxTokens))
                        .tint(mint)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 5) {
                    Text(session.total.compact)
                        .font(.title3.bold().monospacedDigit())
                        .foregroundStyle(mint)
                    Text("Input \(session.input.compact) · Cached \(session.cached.compact) · Out \(session.output.compact)")
                        .font(.caption2)
                        .foregroundStyle(muted)
                }
            }
        }
    }
}

struct TrendsView: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                InfoCard(title: "额度时间线", icon: "waveform.path.ecg") {
                    Chart(store.history.suffix(400)) { p in
                        if let five = p.fiveUsed {
                            LineMark(
                                x: .value("Time", p.capturedAt),
                                y: .value("5H Used", five)
                            )
                            .foregroundStyle(mint)
                        }
                        if let week = p.weekUsed {
                            LineMark(
                                x: .value("Time", p.capturedAt),
                                y: .value("Weekly Used", week)
                            )
                            .foregroundStyle(cyan)
                        }
                    }
                    .chartYScale(domain: 0...100)
                    .frame(height: 300)
                }

                HStack(spacing: 16) {
                    BigMetricCard(
                        label: "WEEKLY BURN",
                        value: store.weeklyBurnPerHour.map { String(format: "%.2f%%/h", $0) } ?? "—",
                        accent: cyan
                    )
                    BigMetricCard(
                        label: "TOKEN / 1% WEEK",
                        value: store.tokensPerWeeklyPercent.map { Int64($0).compact } ?? "—",
                        accent: mint
                    )
                    BigMetricCard(
                        label: "SNAPSHOTS",
                        value: "\(store.history.count)",
                        accent: .white
                    )
                }
            }
            .padding(24)
        }
    }
}

struct ModelsView: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                InfoCard(title: "本地模型消耗", icon: "cpu") {
                    if store.modelUsage.isEmpty {
                        Text("暂无可解析的本地会话模型数据")
                            .foregroundStyle(muted)
                    } else {
                        Chart(store.modelUsage.prefix(12)) { m in
                            BarMark(
                                x: .value("Tokens", m.tokens),
                                y: .value("Model", m.model)
                            )
                            .foregroundStyle(mint.gradient)
                            .cornerRadius(4)
                        }
                        .frame(height: max(240, CGFloat(min(12, store.modelUsage.count)) * 34))
                    }
                }

                ForEach(store.modelUsage) { m in
                    Card {
                        HStack {
                            Image(systemName: "cpu")
                                .foregroundStyle(mint)
                            Text(m.model)
                                .font(.headline)
                            Spacer()
                            Text(m.tokens.compact)
                                .font(.headline.monospacedDigit())
                        }
                    }
                }
            }
            .padding(24)
        }
    }
}

struct AlertsView: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                InfoCard(title: "额度提醒", icon: "bell.badge") {
                    VStack(alignment: .leading, spacing: 18) {
                        HStack {
                            Text("5H 剩余低于")
                            Spacer()
                            Stepper("\(store.alertFiveBelow)%", value: $store.alertFiveBelow, in: 5...50, step: 5)
                                .frame(width: 160)
                        }
                        HStack {
                            Text("Weekly 剩余低于")
                            Spacer()
                            Stepper("\(store.alertWeekBelow)%", value: $store.alertWeekBelow, in: 5...50, step: 5)
                                .frame(width: 160)
                        }
                        HStack {
                            Button("允许系统通知") { store.requestNotifications() }
                            Spacer()
                            Text("达到阈值时由 macOS 本地通知提醒")
                                .font(.caption)
                                .foregroundStyle(muted)
                        }
                    }
                    .onChange(of: store.alertFiveBelow) { _ in store.saveSettings() }
                    .onChange(of: store.alertWeekBelow) { _ in store.saveSettings() }
                }

                InfoCard(title: "自动刷新", icon: "arrow.triangle.2.circlepath") {
                    VStack(spacing: 16) {
                        Toggle("后台自动刷新", isOn: $store.autoRefresh)
                            .onChange(of: store.autoRefresh) { _ in store.startAutoRefresh() }
                        HStack {
                            Text("刷新间隔")
                            Spacer()
                            Stepper("\(store.refreshMinutes) 分钟", value: $store.refreshMinutes, in: 1...30)
                                .frame(width: 180)
                                .onChange(of: store.refreshMinutes) { _ in store.startAutoRefresh() }
                        }
                    }
                }
            }
            .padding(24)
        }
    }
}

struct DiagnosticsView: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                InfoCard(title: "Codex 连接", icon: "stethoscope") {
                    DiagnosticRow("状态", store.status)
                    DiagnosticRow("数据源", store.snapshot.source)
                    DiagnosticRow("Codex", CodexService.shared.executablePath.isEmpty ? "尚未启动" : CodexService.shared.executablePath)
                    DiagnosticRow("Plan", store.snapshot.plan)
                    DiagnosticRow("账号", store.snapshot.email)
                    DiagnosticRow("最近刷新", store.snapshot.capturedAt.formatted(date: .abbreviated, time: .standard))
                }

                InfoCard(title: "数据完整性", icon: "checkmark.shield") {
                    DiagnosticRow("5H window", store.snapshot.fiveHour.map { "\($0.windowMinutes) min" } ?? "缺失")
                    DiagnosticRow("Weekly window", store.snapshot.weekly.map { "\($0.windowMinutes) min" } ?? "缺失")
                    DiagnosticRow("Daily buckets", "\(store.snapshot.daily.count)")
                    DiagnosticRow("History snapshots", "\(store.history.count)")
                    DiagnosticRow("Local sessions", "\(store.sessions.count)")
                }

                if let error = store.lastError {
                    InfoCard(title: "最近错误", icon: "exclamationmark.triangle.fill") {
                        Text(error)
                            .font(.system(.body, design: .monospaced))
                            .foregroundStyle(Color.orange)
                            .textSelection(.enabled)
                    }
                }
            }
            .padding(24)
        }
    }
}

struct MenuBarView: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("CodexMeter")
                .font(.headline)
            Divider()
            HStack {
                Text("5H")
                Spacer()
                Text(store.snapshot.fiveHour.map { "\(Int($0.remainingPercent))% left" } ?? "—")
                    .monospacedDigit()
            }
            HStack {
                Text("Weekly")
                Spacer()
                Text(store.snapshot.weekly.map { "\(Int($0.remainingPercent))% left" } ?? "—")
                    .monospacedDigit()
            }
            Divider()
            Button("刷新") { Task { await store.refresh() } }
            Button("退出") { NSApplication.shared.terminate(nil) }
        }
        .padding(10)
        .frame(width: 230)
    }
}

struct DesktopSettingsView: View {
    @EnvironmentObject var store: UsageStore

    var body: some View {
        Form {
            Section("启动") {
                Toggle("登录 Mac 后自动启动 CodexMeter", isOn: Binding(
                    get: { store.launchAtLogin },
                    set: { store.setLaunchAtLogin($0) }
                ))
            }
            Section("刷新") {
                Toggle("自动刷新", isOn: $store.autoRefresh)
                    .onChange(of: store.autoRefresh) { _ in store.startAutoRefresh() }
                Stepper("每 \(store.refreshMinutes) 分钟", value: $store.refreshMinutes, in: 1...30)
                    .onChange(of: store.refreshMinutes) { _ in store.startAutoRefresh() }
            }
            Section("提醒") {
                Stepper("5H 低于 \(store.alertFiveBelow)%", value: $store.alertFiveBelow, in: 5...50, step: 5)
                Stepper("Weekly 低于 \(store.alertWeekBelow)%", value: $store.alertWeekBelow, in: 5...50, step: 5)
                Button("请求通知权限") { store.requestNotifications() }
            }
        }
        .padding(20)
    }
}

struct Card<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(20)
            .background(panel)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(Color.white.opacity(0.05), lineWidth: 1)
            )
    }
}

struct InfoCard<Content: View>: View {
    let title: String
    let icon: String
    @ViewBuilder var content: Content

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 16) {
                SectionLabel(title.uppercased(), symbol: icon)
                content
            }
        }
    }
}

struct SectionLabel: View {
    let title: String
    let symbol: String

    init(_ title: String, symbol: String) {
        self.title = title
        self.symbol = symbol
    }

    var body: some View {
        Label(title, systemImage: symbol)
            .font(.caption.bold())
            .tracking(1)
            .foregroundStyle(muted)
    }
}

struct MiniMetric: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label)
                .font(.caption2)
                .foregroundStyle(muted)
            Text(value)
                .font(.title3.bold().monospacedDigit())
        }
    }
}

struct BigMetricCard: View {
    let label: String
    let value: String
    let accent: Color

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 8) {
                Text(label)
                    .font(.caption.bold())
                    .foregroundStyle(muted)
                Text(value)
                    .font(.system(size: 29, weight: .heavy, design: .rounded))
                    .foregroundStyle(accent)
                    .monospacedDigit()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

struct IntelligenceRow: View {
    let icon: String
    let title: String
    let value: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .frame(width: 24)
                .foregroundStyle(mint)
            Text(title)
                .foregroundStyle(muted)
            Spacer()
            Text(value)
                .fontWeight(.semibold)
                .monospacedDigit()
        }
        .font(.callout)
    }
}

struct BreakdownRow: View {
    let label: String
    let value: Int64
    let total: Int64
    let color: Color

    var body: some View {
        HStack {
            Text(label)
                .frame(width: 70, alignment: .leading)
                .foregroundStyle(muted)
            ProgressView(value: total == 0 ? 0 : Double(value) / Double(total))
                .tint(color)
            Text(value.compact)
                .frame(width: 70, alignment: .trailing)
                .monospacedDigit()
        }
    }
}

struct DiagnosticRow: View {
    let key: String
    let value: String

    init(_ key: String, _ value: String) {
        self.key = key
        self.value = value
    }

    var body: some View {
        HStack(alignment: .top) {
            Text(key)
                .foregroundStyle(muted)
                .frame(width: 140, alignment: .leading)
            Text(value)
                .textSelection(.enabled)
            Spacer()
        }
        .font(.callout)
        .padding(.vertical, 3)
    }
}
