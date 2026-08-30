import Foundation

enum CodexServiceError: LocalizedError {
    case executableNotFound
    case processFailed(String)
    case invalidResponse
    case timeout(String)

    var errorDescription: String? {
        switch self {
        case .executableNotFound: return "未找到 Codex。请使用 DMG 内置版本或安装 Codex CLI。"
        case .processFailed(let s): return "Codex 启动失败：\(s)"
        case .invalidResponse: return "Codex 返回了无法识别的数据"
        case .timeout(let method): return "Codex RPC 超时：\(method)"
        }
    }
}

final class CodexService {
    static let shared = CodexService()

    private var process: Process?
    private var stdin: FileHandle?
    private var stdout: FileHandle?
    private var buffer = Data()
    private var nextID = 1
    private let queue = DispatchQueue(label: "CodexMeter.CodexService")
    private var pending: [Int: CheckedContinuation<[String: Any], Error>] = [:]
    private(set) var executablePath: String = ""

    private init() {}

    func stop() {
        queue.sync {
            process?.terminate()
            process = nil
            stdin = nil
            stdout = nil
            pending.removeAll()
        }
    }

    private func locateCodex() -> String? {
        if let bundled = Bundle.main.path(forResource: "codex", ofType: nil, inDirectory: "bin") {
            return bundled
        }
        let candidates = [
            "/opt/homebrew/bin/codex",
            "/usr/local/bin/codex",
            NSHomeDirectory() + "/.local/bin/codex"
        ]
        for path in candidates where FileManager.default.isExecutableFile(atPath: path) {
            return path
        }
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/usr/bin/which")
        p.arguments = ["codex"]
        let pipe = Pipe()
        p.standardOutput = pipe
        try? p.run()
        p.waitUntilExit()
        guard p.terminationStatus == 0 else { return nil }
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        let path = String(decoding: data, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
        return path.isEmpty ? nil : path
    }

    private func ensureStarted() async throws {
        if process?.isRunning == true { return }
        guard let codex = locateCodex() else { throw CodexServiceError.executableNotFound }
        executablePath = codex

        let p = Process()
        p.executableURL = URL(fileURLWithPath: codex)
        p.arguments = ["-s", "read-only", "-a", "never", "app-server"]

        let input = Pipe()
        let output = Pipe()
        let error = Pipe()
        p.standardInput = input
        p.standardOutput = output
        p.standardError = error

        do {
            try p.run()
        } catch {
            throw CodexServiceError.processFailed(error.localizedDescription)
        }

        process = p
        stdin = input.fileHandleForWriting
        stdout = output.fileHandleForReading

        output.fileHandleForReading.readabilityHandler = { [weak self] h in
            let data = h.availableData
            guard !data.isEmpty else { return }
            self?.consume(data)
        }

        _ = try await call(method: "initialize", params: [
            "clientInfo": [
                "name": "codexmeter-macos",
                "title": "CodexMeter for Mac",
                "version": "1.0.0"
            ],
            "capabilities": [:]
        ], skipStart: true)

        try sendNotification(method: "initialized", params: [:])
    }

    private func consume(_ data: Data) {
        queue.async {
            self.buffer.append(data)
            while let range = self.buffer.firstRange(of: Data([0x0A])) {
                let line = self.buffer.subdata(in: 0..<range.lowerBound)
                self.buffer.removeSubrange(0...range.lowerBound)
                guard !line.isEmpty,
                      let obj = try? JSONSerialization.jsonObject(with: line) as? [String: Any],
                      let id = obj["id"] as? Int
                else { continue }

                guard let continuation = self.pending.removeValue(forKey: id) else { continue }
                if let e = obj["error"] {
                    continuation.resume(throwing: CodexServiceError.processFailed(String(describing: e)))
                } else if let result = obj["result"] as? [String: Any] {
                    continuation.resume(returning: result)
                } else {
                    continuation.resume(returning: [:])
                }
            }
        }
    }

    private func send(_ object: [String: Any]) throws {
        let data = try JSONSerialization.data(withJSONObject: object)
        var line = data
        line.append(0x0A)
        stdin?.write(line)
    }

    private func sendNotification(method: String, params: [String: Any]) throws {
        try send(["method": method, "params": params])
    }

    private func call(method: String, params: [String: Any], skipStart: Bool = false) async throws -> [String: Any] {
        if !skipStart { try await ensureStarted() }
        let id: Int = queue.sync {
            defer { nextID += 1 }
            return nextID
        }

        return try await withCheckedThrowingContinuation { continuation in
            queue.async {
                self.pending[id] = continuation
                do {
                    try self.send(["method": method, "id": id, "params": params])
                } catch {
                    self.pending.removeValue(forKey: id)
                    continuation.resume(throwing: error)
                }
            }

            DispatchQueue.global().asyncAfter(deadline: .now() + 15) {
                self.queue.async {
                    if let c = self.pending.removeValue(forKey: id) {
                        c.resume(throwing: CodexServiceError.timeout(method))
                    }
                }
            }
        }
    }

    func loginStatus() async -> Bool {
        do {
            let r = try await call(method: "account/read", params: ["refreshToken": true])
            return r["account"] is [String: Any]
        } catch {
            return false
        }
    }

    func beginDeviceCodeLogin() async throws -> (url: String, code: String) {
        let r = try await call(method: "account/login/start", params: ["type": "chatgptDeviceCode"])
        guard let url = r["verificationUrl"] as? String,
              let code = r["userCode"] as? String else {
            throw CodexServiceError.invalidResponse
        }
        return (url, code)
    }

    func fetchSnapshot() async throws -> UsageSnapshot {
        try await ensureStarted()
        let account = try await call(method: "account/read", params: ["refreshToken": false])
        let rate = try await call(method: "account/rateLimits/read", params: [:])
        let usage = (try? await call(method: "account/usage/read", params: [:])) ?? [:]

        let accountObj = account["account"] as? [String: Any] ?? [:]
        let snapshot = chooseCodexSnapshot(rate)
        let windows = [snapshot["primary"], snapshot["secondary"]].compactMap { $0 as? [String: Any] }
        let five = nearest(windows, target: 300)
        let week = nearest(windows, target: 10080)

        let dailyRaw = usage["dailyUsageBuckets"] as? [[String: Any]] ?? []
        let iso = ISO8601DateFormatter()
        let dayParser = DateFormatter()
        dayParser.dateFormat = "yyyy-MM-dd"
        dayParser.locale = Locale(identifier: "en_US_POSIX")

        let daily = dailyRaw.compactMap { row -> DailyToken? in
            guard let s = row["startDate"] as? String else { return nil }
            let date = iso.date(from: s) ?? dayParser.date(from: String(s.prefix(10)))
            guard let date else { return nil }
            let tokens = Self.int64(row["tokens"]) ?? 0
            return DailyToken(date: date, tokens: tokens)
        }.sorted { $0.date < $1.date }

        let summary = usage["summary"] as? [String: Any] ?? [:]
        return UsageSnapshot(
            capturedAt: .now,
            fiveHour: quota(five),
            weekly: quota(week),
            email: accountObj["email"] as? String ?? "Codex account",
            plan: accountObj["planType"] as? String ?? snapshot["planType"] as? String ?? "ChatGPT",
            lifetimeTokens: Self.int64(summary["lifetimeTokens"]) ?? 0,
            daily: daily,
            source: "Local Codex app-server"
        )
    }

    private func chooseCodexSnapshot(_ rate: [String: Any]) -> [String: Any] {
        if let byID = rate["rateLimitsByLimitId"] as? [String: Any],
           let codex = byID["codex"] as? [String: Any] {
            return codex
        }
        return rate["rateLimits"] as? [String: Any] ?? [:]
    }

    private func nearest(_ windows: [[String: Any]], target: Int) -> [String: Any]? {
        windows.min {
            abs((Self.int($0["windowDurationMins"]) ?? 0) - target) <
            abs((Self.int($1["windowDurationMins"]) ?? 0) - target)
        }
    }

    private func quota(_ raw: [String: Any]?) -> QuotaWindow? {
        guard let raw,
              let used = Self.double(raw["usedPercent"]) else { return nil }
        let reset: Date?
        if let epoch = Self.double(raw["resetsAt"]) {
            reset = Date(timeIntervalSince1970: epoch)
        } else {
            reset = nil
        }
        return QuotaWindow(
            usedPercent: used,
            resetAt: reset,
            windowMinutes: Self.int(raw["windowDurationMins"]) ?? 0
        )
    }

    private static func int(_ value: Any?) -> Int? {
        if let i = value as? Int { return i }
        if let n = value as? NSNumber { return n.intValue }
        return nil
    }

    private static func int64(_ value: Any?) -> Int64? {
        if let i = value as? Int64 { return i }
        if let i = value as? Int { return Int64(i) }
        if let n = value as? NSNumber { return n.int64Value }
        return nil
    }

    private static func double(_ value: Any?) -> Double? {
        if let d = value as? Double { return d }
        if let n = value as? NSNumber { return n.doubleValue }
        return nil
    }
}
