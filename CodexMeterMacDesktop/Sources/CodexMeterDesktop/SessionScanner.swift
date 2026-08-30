import Foundation

final class SessionScanner {
    func scan(limit: Int = 200) -> [LocalSession] {
        let root = URL(fileURLWithPath: NSHomeDirectory()).appendingPathComponent(".codex/sessions")
        guard let e = FileManager.default.enumerator(
            at: root,
            includingPropertiesForKeys: [.contentModificationDateKey, .fileSizeKey],
            options: [.skipsHiddenFiles]
        ) else { return [] }

        var urls: [(URL, Date)] = []
        for case let url as URL in e {
            guard url.pathExtension.lowercased() == "jsonl" else { continue }
            let values = try? url.resourceValues(forKeys: [.contentModificationDateKey])
            urls.append((url, values?.contentModificationDate ?? .distantPast))
        }

        return urls
            .sorted { $0.1 > $1.1 }
            .prefix(limit)
            .compactMap { parse(url: $0.0, modified: $0.1) }
            .sorted { $0.modifiedAt > $1.modifiedAt }
    }

    private func parse(url: URL, modified: Date) -> LocalSession? {
        guard let data = try? String(contentsOf: url, encoding: .utf8) else { return nil }

        var model = "Unknown"
        var maxInput: Int64 = 0
        var maxCached: Int64 = 0
        var maxOutput: Int64 = 0
        var maxTotal: Int64 = 0
        var firstText: String?

        data.enumerateLines { line, _ in
            guard let lineData = line.data(using: .utf8),
                  let obj = try? JSONSerialization.jsonObject(with: lineData)
            else { return }

            self.walk(obj) { key, value in
                let k = key.lowercased()
                if k == "model", let s = value as? String, !s.isEmpty {
                    model = s
                } else if ["input_tokens", "inputtokens"].contains(k), let n = self.number(value) {
                    maxInput = max(maxInput, n)
                } else if ["cached_input_tokens", "cachedinputtokens", "cached_tokens"].contains(k), let n = self.number(value) {
                    maxCached = max(maxCached, n)
                } else if ["output_tokens", "outputtokens"].contains(k), let n = self.number(value) {
                    maxOutput = max(maxOutput, n)
                } else if ["total_tokens", "totaltokens"].contains(k), let n = self.number(value) {
                    maxTotal = max(maxTotal, n)
                } else if firstText == nil,
                          ["text", "prompt", "content"].contains(k),
                          let s = value as? String,
                          s.count > 10 {
                    firstText = String(s.replacingOccurrences(of: "\n", with: " ").prefix(72))
                }
            }
        }

        let total = max(maxTotal, maxInput + maxCached + maxOutput)
        if total == 0 && model == "Unknown" { return nil }

        let title = firstText ?? url.deletingPathExtension().lastPathComponent
        return LocalSession(
            id: url.path,
            title: title,
            modifiedAt: modified,
            model: model,
            input: maxInput,
            cached: maxCached,
            output: maxOutput,
            total: total
        )
    }

    private func walk(_ value: Any, visit: (String, Any) -> Void) {
        if let dict = value as? [String: Any] {
            for (k, v) in dict {
                visit(k, v)
                walk(v, visit: visit)
            }
        } else if let array = value as? [Any] {
            array.forEach { walk($0, visit: visit) }
        }
    }

    private func number(_ value: Any) -> Int64? {
        if let n = value as? NSNumber { return n.int64Value }
        if let i = value as? Int64 { return i }
        if let i = value as? Int { return Int64(i) }
        return nil
    }
}
