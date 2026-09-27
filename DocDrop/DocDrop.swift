// 文档快投 DocDrop 1.3 —— macOS 小工具
// 在 Word / Excel / PowerPoint / WPS / Pages 里编辑文档时，不用“另存为”：
//   · 把当前文档直接拖进 AI 工具（ChatGPT、Claude、Kimi、豆包……）
//   · 查看 / 复制文件路径，一键打开所在文件夹
//
// 鼠标操作：
//   在文档窗口里 按住 Alt 点右键 → 路径菜单（查看路径、打开所在文件夹、复制路径、复制文件）
//   拖动悬浮球                    → 把当前文档拖进 AI 窗口
//   单击悬浮球                    → 详情浮窗
//   右键悬浮球                    → 路径菜单
//   按住 Alt 拖悬浮球             → 移动位置
// 键盘快捷键（Ctrl+Alt+D/C/P/F）默认关闭，可在菜单栏图标里开启。

import Cocoa
import Carbon.HIToolbox
import ApplicationServices

let appVersion = "1.3"

// MARK: - 数据模型

struct DocInfo: Equatable {
    let url: URL
    let appName: String
    var name: String { url.lastPathComponent }
    var path: String { url.path }
}

enum LocateResult {
    case found(DocInfo)
    case failed(String)
}

/// 记录识别过程，用于“识别诊断”
final class Trace {
    var lines: [String] = []
    func add(_ s: String) { lines.append(s) }
}

// MARK: - 定位“当前正在编辑的文档”

@MainActor
enum DocLocator {
    static let docExts: Set<String> = [
        "doc", "docx", "docm", "dot", "dotx", "rtf", "wps", "wpt",
        "xls", "xlsx", "xlsm", "xlsb", "et", "ett", "csv",
        "ppt", "pptx", "pptm", "pps", "ppsx", "dps", "dpt",
        "pdf", "ofd", "pages", "numbers", "key", "txt", "md", "odt", "ods", "odp",
    ]
    private static var titleHit: [String: String] = [:]
    private static var titleMiss: Set<String> = []
    private static var scriptFailedApps: Set<String> = []

    /// 是否是文档类应用（Office、WPS、iWork、文本编辑、预览等）
    static func isDocApp(_ bundleID: String) -> Bool {
        let b = bundleID.lowercased()
        return ["com.microsoft.word", "com.microsoft.excel", "com.microsoft.powerpoint",
                "kingsoft", "wps", "libreoffice", "openoffice", "yozo",
                "com.apple.iwork", "com.apple.textedit", "com.apple.preview", "pdf"].contains { b.contains($0) }
    }

    static func locate(app: NSRunningApplication, fresh: Bool, trace: Trace? = nil) -> LocateResult {
        let pid = app.processIdentifier
        let appName = app.localizedName ?? "当前应用"
        let bid = app.bundleIdentifier ?? ""
        let axOK = AXIsProcessTrusted()
        trace?.add("前台应用：\(appName)（\(bid)）")
        trace?.add("辅助功能权限：\(axOK ? "已开启" : "未开启 ✗")")

        func found(_ p: String, _ how: String) -> LocateResult {
            trace?.add("→ 识别成功（\(how)）：\(p)")
            return .found(DocInfo(url: URL(fileURLWithPath: p), appName: appName))
        }

        let window = axOK ? focusedWindow(pid: pid) : nil
        let title = window.flatMap { axString($0, kAXTitleAttribute) } ?? ""
        trace?.add("窗口标题：\(title.isEmpty ? "（读不到）" : title)")

        if let w = window, let p = activeDocumentPath(pid: pid, window: w) {
            return found(p, "当前窗口或焦点文档路径")
        }

        // 1) 窗口的 AXDocument 属性（Pages、文本编辑、预览及多数原生应用）
        if let w = window, let raw = axString(w, kAXDocumentAttribute) {
            trace?.add("窗口文档属性：\(raw)")
            if let p = localPath(from: raw), exists(p) { return found(p, "窗口文档属性") }
        } else {
            trace?.add("窗口文档属性：（无）")
        }

        // 2) AppleScript：Office 精确路径；WPS 等尝试通用写法
        var automationDenied = false
        if let script = appleScript(bundleID: bid), trace != nil || !scriptFailedApps.contains(bid) {
            let (out, err) = runAppleScript(script)
            if let err {
                trace?.add("AppleScript：失败（\(err)）")
                if err.hasPrefix("-1743") { automationDenied = true }
                if !bid.hasPrefix("com.microsoft.") { scriptFailedApps.insert(bid) }
            } else {
                trace?.add("AppleScript：\(out ?? "")")
            }
            if let r = out, !r.isEmpty {
                if r.lowercased().hasPrefix("http") {
                    trace?.add("→ 云端文档")
                    return .failed("这是云端文档（OneDrive / SharePoint），本机没有对应文件。\n请先“另存为”一份到本地。")
                }
                if exists(r) { return found(r, "AppleScript") }
            }
        }

        let cands = title.isEmpty ? [] : titleCandidates(title)
        let titleHasExt = cands.contains { docExts.contains(($0 as NSString).pathExtension.lowercased()) }
        let docApp = isDocApp(bid)

        if docApp || titleHasExt {
            // 3) 进程当前打开的文档文件，与标题匹配
            let open = openDocFiles(pid: pid)
            trace?.add("进程打开的文档：" + (open.isEmpty ? "（无）" : "\n  " + open.joined(separator: "\n  ")))
            let hits = open.filter { matches($0, cands) }
            if hits.count == 1 { return found(hits[0], "进程打开的文件") }
            if hits.count > 1 {
                return .failed("发现多个同名文件，无法确定当前编辑的是哪份：\n" + hits.joined(separator: "\n") + "\n请在 WPS 中关闭重复的同名文档后重试。")
            }

            // 4) 按标题里的文件名用 Spotlight 查找
            if !title.isEmpty {
                let key = "\(pid)|\(title)"
                if let p = titleHit[key], exists(p) { return found(p, "标题匹配（缓存）") }
                if fresh || trace != nil { titleMiss.remove(key) }
                if !titleMiss.contains(key) {
                    trace?.add("按文件名搜索：\(cands.joined(separator: " / "))")
                    if let p = spotlight(cands) {
                        titleHit[key] = p
                        return found(p, "按文件名搜索")
                    }
                    titleMiss.insert(key)
                }
            }

            // 5) 进程打开的文档里最近修改的那个
            // Never substitute another tab's file when the active title cannot be matched.
        }

        // 识别失败：给出具体原因
        let reason: String
        if !axOK {
            reason = "DocDrop 还没有「辅助功能」权限。\n请到 系统设置 › 隐私与安全性 › 辅助功能，打开 DocDrop。\n（如果已经是打开状态，请先用“−”删掉它，再重新添加。）"
        } else if automationDenied {
            reason = "DocDrop 没有控制「\(appName)」的权限。\n请到 系统设置 › 隐私与安全性 › 自动化，在 DocDrop 下勾选「\(appName)」。"
        } else if !docApp {
            reason = "当前前台是「\(appName)」，不是文档窗口。\n请先点一下 Word / WPS 里的文档，再操作。"
        } else if !title.isEmpty {
            reason = "没找到「\(title)」对应的本地文件。\n可能是还没保存过的新文档，或是云文档。"
        } else {
            reason = "「\(appName)」当前没有已保存的文档窗口。"
        }
        trace?.add("→ 识别失败：\(reason.replacingOccurrences(of: "\n", with: " "))")
        return .failed(reason)
    }

    static func windowTitle(pid: pid_t) -> String? {
        guard AXIsProcessTrusted() else { return nil }
        return focusedWindow(pid: pid).flatMap { axString($0, kAXTitleAttribute) }
    }

    // MARK: AX 辅助

    private static func axValue(_ el: AXUIElement, _ attr: String) -> CFTypeRef? {
        var v: CFTypeRef?
        guard AXUIElementCopyAttributeValue(el, attr as CFString, &v) == .success else { return nil }
        return v
    }

    private static func axString(_ el: AXUIElement, _ attr: String) -> String? {
        axValue(el, attr) as? String
    }

    private static func focusedWindow(pid: pid_t) -> AXUIElement? {
        let appEl = AXUIElementCreateApplication(pid)
        for attr in [kAXFocusedWindowAttribute, kAXMainWindowAttribute] {
            if let v = axValue(appEl, attr), CFGetTypeID(v) == AXUIElementGetTypeID() {
                return (v as! AXUIElement)
            }
        }
        return nil
    }

    private static func activeDocumentPath(pid: pid_t, window: AXUIElement) -> String? {
        var elements: [AXUIElement] = [window]
        let app = AXUIElementCreateApplication(pid)
        if let focused = axValue(app, kAXFocusedUIElementAttribute),
           CFGetTypeID(focused) == AXUIElementGetTypeID() {
            var current = focused as! AXUIElement
            var ancestry: [AXUIElement] = []
            for _ in 0..<24 {
                ancestry.append(current)
                if CFEqual(current, window) { elements += ancestry; break }
                guard let parent = axValue(current, kAXParentAttribute),
                      CFGetTypeID(parent) == AXUIElementGetTypeID() else { break }
                current = parent as! AXUIElement
            }
        }
        // Read only the active window and its focus ancestry, not sibling tabs.
        var paths = Set<String>()
        for element in elements {
            for attribute in [kAXDocumentAttribute, "AXURL"] {
                let value = axValue(element, attribute)
                let raw = (value as? String) ?? (value as? URL)?.absoluteString
                if let raw, let path = localPath(from: raw), exists(path), isUserDoc(path) {
                    paths.insert(path)
                }
            }
        }
        return paths.count == 1 ? paths.first : nil
    }

    private static func localPath(from raw: String) -> String? {
        if raw.hasPrefix("file://") { return URL(string: raw)?.path }
        if raw.hasPrefix("/") { return raw }
        return nil
    }

    private static func exists(_ p: String) -> Bool {
        !p.isEmpty && FileManager.default.fileExists(atPath: p)
    }

    private static func mtime(_ p: String) -> Date {
        let attrs = try? FileManager.default.attributesOfItem(atPath: p)
        return (attrs?[.modificationDate] as? Date) ?? .distantPast
    }

    // MARK: AppleScript

    private static func appleScript(bundleID: String) -> String? {
        let active: String
        let collection: String
        switch bundleID {
        case "com.microsoft.Word": active = "active document"; collection = "documents"
        case "com.microsoft.Excel": active = "active workbook"; collection = "workbooks"
        case "com.microsoft.Powerpoint": active = "active presentation"; collection = "presentations"
        default:
            let b = bundleID.lowercased()
            guard ["kingsoft", "wps", "libreoffice", "openoffice", "yozo"].contains(where: { b.contains($0) }) else { return nil }
            return """
            set p to ""
            with timeout of 3 seconds
                tell application id "\(bundleID)"
                    try
                        set p to (path of front document) as text
                    end try
                end tell
            end timeout
            return p
            """
        }
        return """
        with timeout of 5 seconds
            tell application id "\(bundleID)"
                if (count of \(collection)) = 0 then return ""
                set p to full name of \(active)
            end tell
        end timeout
        if p starts with "http" then return p
        if p does not start with "/" then set p to POSIX path of p
        return p
        """
    }

    private static func runAppleScript(_ source: String) -> (String?, String?) {
        guard let script = NSAppleScript(source: source) else { return (nil, "脚本创建失败") }
        var err: NSDictionary?
        let out = script.executeAndReturnError(&err)
        if let err {
            let num = (err[NSAppleScript.errorNumber] as? Int).map { String($0) } ?? "?"
            let msg = err[NSAppleScript.errorMessage] as? String ?? ""
            return (nil, "\(num) \(msg)")
        }
        return (out.stringValue, nil)
    }

    // MARK: 进程打开的文件 / 标题反查

    private static func titleCandidates(_ title: String) -> [String] {
        var s = title
        for junk in [" [兼容模式]", "[兼容模式]", " [Compatibility Mode]", " [只读]", " [Read-Only]",
                     " — 已编辑", " - 已编辑", " — Edited", " - Edited", " (已编辑)", "*"] {
            s = s.replacingOccurrences(of: junk, with: "")
        }
        var out: [String] = [s]
        for sep in [" - ", " — ", " – ", " | "] {
            out += s.components(separatedBy: sep)
        }
        var seen = Set<String>()
        return out.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { $0.count >= 2 && seen.insert($0).inserted }
    }

    private static func matches(_ path: String, _ cands: [String]) -> Bool {
        let fn = (path as NSString).lastPathComponent
        let base = (fn as NSString).deletingPathExtension
        return cands.contains { $0 == fn || $0 == base }
    }

    /// 只保留用户自己的文档（排除临时文件、模板、应用内部文件；保留 iCloud / 网盘同步目录）
    private static func isUserDoc(_ path: String) -> Bool {
        let fn = (path as NSString).lastPathComponent
        if fn.hasPrefix("~$") || fn.hasPrefix(".") { return false }
        if ["/Applications/", "/System/", "/private/", "/usr/", "/opt/"].contains(where: { path.hasPrefix($0) })
            || path.contains("/.Trash/") {
            return false
        }
        if path.contains("/Library/") && !path.contains("/Library/Mobile Documents/") && !path.contains("/Library/CloudStorage/") {
            return false
        }
        return docExts.contains((fn as NSString).pathExtension.lowercased())
    }

    private static func openDocFiles(pid: pid_t) -> [String] {
        var seen = Set<String>()
        return run("/usr/sbin/lsof", ["-p", "\(pid)", "-Fn"])
            .split(separator: "\n")
            .filter { $0.hasPrefix("n/") }
            .map { decodeLsofPath(String($0.dropFirst())) }
            .filter { exists($0) && isUserDoc($0) && seen.insert($0).inserted }
    }

    static func decodeLsofPath(_ raw: String) -> String {
        // lsof escapes non-ASCII bytes as hexadecimal, not Unicode scalars.
        // Preserve literal existing paths before interpreting escape sequences.
        if FileManager.default.fileExists(atPath: raw) { return raw }
        let input = Array(raw.utf8)
        var output: [UInt8] = []
        var i = 0
        while i < input.count {
            if input[i] == 92, i + 3 < input.count, input[i + 1] == 120,
               let byte = UInt8(String(bytes: input[(i + 2)...(i + 3)], encoding: .ascii) ?? "", radix: 16) {
                output.append(byte)
                i += 4
            } else {
                output.append(input[i])
                i += 1
            }
        }
        return String(bytes: output, encoding: .utf8) ?? raw
    }

    private static func spotlight(_ cands: [String]) -> String? {
        var hits = Set<String>()
        for c in cands {
            let results = run("/usr/bin/mdfind", ["-name", c]).split(separator: "\n").map(String.init)
            for p in results where isUserDoc(p) && matches(p, cands) {
                if exists(p) { hits.insert(p) }
            }
        }
        return hits.count == 1 ? hits.first : nil
    }

    private static func run(_ exe: String, _ args: [String]) -> String {
        let p = Process()
        p.executableURL = URL(fileURLWithPath: exe)
        p.arguments = args
        let pipe = Pipe()
        p.standardOutput = pipe
        p.standardError = FileHandle.nullDevice
        do { try p.run() } catch { return "" }
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        p.waitUntilExit()
        return String(decoding: data, as: UTF8.self)
    }
}

// MARK: - 文件剪贴板 / 拖拽 / Finder（兼容微信、LINE、Finder 等老式接收方）

extension NSPasteboard.PasteboardType {
    /// Finder 复制/拖拽文件时带的老式类型，微信、LINE、Finder 粘贴都依赖它
    static let legacyFilenames = NSPasteboard.PasteboardType("NSFilenamesPboardType")
}

final class CompatibleFileWriter: NSObject, NSPasteboardWriting {
    let url: NSURL
    init(_ url: URL) { self.url = url as NSURL }
    func writableTypes(for pasteboard: NSPasteboard) -> [NSPasteboard.PasteboardType] {
        Array(Set(url.writableTypes(for: pasteboard) + [.legacyFilenames]))
    }
    func pasteboardPropertyList(forType type: NSPasteboard.PasteboardType) -> Any? {
        if type == .legacyFilenames { return [url.path! ] }
        return url.pasteboardPropertyList(forType: type)
    }
}

@MainActor
enum FileTransfer {
    /// 让剪贴板内容和在 Finder 里按“拷贝”完全一样：可以粘贴到微信、LINE、AI 输入框、Finder
    static func copyToClipboard(_ url: URL) {
        let pb = NSPasteboard.general
        pb.clearContents()
        pb.writeObjects([CompatibleFileWriter(url)])
    }

    static func addLegacy(_ url: URL, to pb: NSPasteboard) {
        pb.addTypes([.legacyFilenames], owner: nil)
        pb.setPropertyList([url.path], forType: .legacyFilenames)
    }

    /// 从某个视图开始拖拽文件（和从 Finder 拖出的效果一样）
    static func beginDrag(from view: NSView & NSDraggingSource, url: URL, event: NSEvent, frame: NSRect) {
        let item = NSDraggingItem(pasteboardWriter: CompatibleFileWriter(url))
        item.setDraggingFrame(frame, contents: NSWorkspace.shared.icon(forFile: url.path))
        let session = view.beginDraggingSession(with: [item], event: event, source: view)
        session.animatesToStartingPositionsOnCancelOrFail = true
    }

    /// 允许复制 / 链接，不允许“移动”（避免把正在编辑的原文件挪走）
    static let dragOperations: NSDragOperation = .copy

    /// 在 Finder 中打开所在文件夹并选中文件（用系统 open -R，最稳定）
    static func reveal(_ path: String) { runOpen(["-R", path]) }

    /// 在 Finder 中打开某个文件夹
    static func openFolder(_ path: String) { runOpen([path]) }

    private static func runOpen(_ args: [String]) {
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/usr/bin/open")
        p.arguments = args
        try? p.run()
    }
}

// MARK: - 模拟 ⌘S（可选：抓取前自动保存）

enum Keys {
    static func sendSave() {
        let src = CGEventSource(stateID: .combinedSessionState)
        let key = CGKeyCode(kVK_ANSI_S)
        guard let down = CGEvent(keyboardEventSource: src, virtualKey: key, keyDown: true),
              let up = CGEvent(keyboardEventSource: src, virtualKey: key, keyDown: false) else { return }
        down.flags = .maskCommand
        up.flags = .maskCommand
        down.post(tap: .cghidEventTap)
        up.post(tap: .cghidEventTap)
    }
}

// MARK: - 文档窗口里「Alt + 右键」

// 这几个全局变量只在主线程（主 RunLoop 上的事件回调）里读写
var gHookEnabled = true
var gHookFrontIsDocApp = false
var gHookTap: CFMachPort?

private func rightClickTap(_ proxy: CGEventTapProxy, _ type: CGEventType, _ event: CGEvent,
                           _ refcon: UnsafeMutableRawPointer?) -> Unmanaged<CGEvent>? {
    if type == .tapDisabledByTimeout || type == .tapDisabledByUserInput {
        if let t = gHookTap { CGEvent.tapEnable(tap: t, enable: true) }
        return Unmanaged.passUnretained(event)
    }
    guard gHookEnabled, gHookFrontIsDocApp, event.flags.contains(.maskAlternate) else {
        return Unmanaged.passUnretained(event)
    }
    if type == .rightMouseDown {
        Task { @MainActor in RightClickHook.fire() }
    }
    return nil // 吞掉这次右键，不让 Word / WPS 弹出它自己的菜单
}

@MainActor
enum RightClickHook {
    static var onFire: (() -> Void)?
    static var installed: Bool { gHookTap != nil }

    /// 需要「辅助功能」权限；没权限时返回 false，稍后再试
    @discardableResult
    static func install() -> Bool {
        if gHookTap != nil { return true }
        let mask = CGEventMask(1 << CGEventType.rightMouseDown.rawValue) | CGEventMask(1 << CGEventType.rightMouseUp.rawValue)
        guard let tap = CGEvent.tapCreate(tap: .cgSessionEventTap, place: .headInsertEventTap, options: .defaultTap,
                                          eventsOfInterest: mask, callback: rightClickTap, userInfo: nil) else { return false }
        gHookTap = tap
        let src = CFMachPortCreateRunLoopSource(kCFAllocatorDefault, tap, 0)
        CFRunLoopAddSource(CFRunLoopGetMain(), src, .commonModes)
        CGEvent.tapEnable(tap: tap, enable: true)
        return true
    }

    static func updateFront(_ app: NSRunningApplication?) {
        gHookFrontIsDocApp = DocLocator.isDocApp(app?.bundleIdentifier ?? "")
    }

    static func fire() { onFire?() }
}

// MARK: - 全局快捷键（默认关闭）

private func hotKeyHandler(_ next: EventHandlerCallRef?, _ event: EventRef?, _ user: UnsafeMutableRawPointer?) -> OSStatus {
    var hk = EventHotKeyID()
    let st = GetEventParameter(event, EventParamName(kEventParamDirectObject), EventParamType(typeEventHotKeyID),
                               nil, MemoryLayout<EventHotKeyID>.size, nil, &hk)
    if st == noErr {
        let id = hk.id
        Task { @MainActor in HotKeys.fire(id) }
    }
    return noErr
}

@MainActor
enum HotKeys {
    private static var actions: [UInt32: () -> Void] = [:]
    private static var refs: [EventHotKeyRef?] = []
    private static var installed = false

    static func register(id: UInt32, keyCode: Int, modifiers: Int, action: @escaping () -> Void) {
        if !installed {
            installed = true
            var spec = EventTypeSpec(eventClass: OSType(kEventClassKeyboard), eventKind: UInt32(kEventHotKeyPressed))
            InstallEventHandler(GetApplicationEventTarget(), hotKeyHandler, 1, &spec, nil, nil)
        }
        actions[id] = action
        var ref: EventHotKeyRef?
        let hkID = EventHotKeyID(signature: OSType(0x4444_5250), id: id)
        let st = RegisterEventHotKey(UInt32(keyCode), UInt32(modifiers), hkID, GetApplicationEventTarget(), 0, &ref)
        if st != noErr { NSLog("DocDrop: 快捷键 \(id) 注册失败（可能被其他应用占用）") }
        refs.append(ref)
    }

    static func fire(_ id: UInt32) { actions[id]?() }

    static func unregisterAll() {
        for r in refs { if let r { UnregisterEventHotKey(r) } }
        refs = []
        actions = [:]
    }
}

// MARK: - 轻提示

@MainActor
enum Toast {
    private static var panel: NSPanel?
    private static var generation = 0

    static func show(_ text: String, seconds: Double = 2.4) {
        let label = NSTextField(wrappingLabelWithString: text)
        label.font = .systemFont(ofSize: 13, weight: .medium)
        label.preferredMaxLayoutWidth = 360
        label.translatesAutoresizingMaskIntoConstraints = false

        let fx = NSVisualEffectView()
        fx.material = .hudWindow
        fx.state = .active
        fx.wantsLayer = true
        fx.layer?.cornerRadius = 10
        fx.layer?.masksToBounds = true
        fx.addSubview(label)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: fx.leadingAnchor, constant: 14),
            label.trailingAnchor.constraint(equalTo: fx.trailingAnchor, constant: -14),
            label.topAnchor.constraint(equalTo: fx.topAnchor, constant: 10),
            label.bottomAnchor.constraint(equalTo: fx.bottomAnchor, constant: -10),
        ])
        let size = fx.fittingSize

        let p = panel ?? NSPanel(contentRect: .zero, styleMask: [.borderless, .nonactivatingPanel],
                                 backing: .buffered, defer: false)
        p.isOpaque = false
        p.backgroundColor = .clear
        p.hasShadow = true
        p.level = .statusBar
        p.ignoresMouseEvents = true
        p.isReleasedWhenClosed = false
        p.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        p.contentView = fx

        let m = NSEvent.mouseLocation
        var o = NSPoint(x: m.x + 14, y: m.y - size.height - 14)
        if let vf = (NSScreen.screens.first { NSMouseInRect(m, $0.frame, false) } ?? NSScreen.main)?.visibleFrame {
            o.x = min(max(o.x, vf.minX + 8), vf.maxX - size.width - 8)
            o.y = min(max(o.y, vf.minY + 8), vf.maxY - size.height - 8)
        }
        p.setFrame(NSRect(origin: o, size: size), display: true)
        p.orderFrontRegardless()
        panel = p

        generation += 1
        let g = generation
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            if g == generation { panel?.orderOut(nil) }
        }
    }
}

// MARK: - 可拖拽的文件图标（浮窗里）

@MainActor
final class DragIconView: NSView, NSDraggingSource {
    var fileURL: URL? {
        didSet {
            needsDisplay = true
            toolTip = fileURL == nil ? nil : "按住拖到 AI 窗口即可上传"
        }
    }
    var onDragEnded: ((Bool) -> Void)?
    private var downEvent: NSEvent?

    override var intrinsicContentSize: NSSize { NSSize(width: 84, height: 84) }
    override var mouseDownCanMoveWindow: Bool { false }
    override func acceptsFirstMouse(for event: NSEvent?) -> Bool { true }

    override func draw(_ dirtyRect: NSRect) {
        let r = bounds.insetBy(dx: 2, dy: 2)
        let box = NSBezierPath(roundedRect: r, xRadius: 12, yRadius: 12)
        NSColor.controlAccentColor.withAlphaComponent(0.10).setFill()
        box.fill()
        let dash: [CGFloat] = [5, 3]
        box.setLineDash(dash, count: dash.count, phase: 0)
        box.lineWidth = 1.2
        NSColor.controlAccentColor.withAlphaComponent(0.55).setStroke()
        box.stroke()

        if let u = fileURL {
            NSWorkspace.shared.icon(forFile: u.path).draw(in: r.insetBy(dx: 12, dy: 12))
        } else if let img = NSImage(systemSymbolName: "questionmark.folder", accessibilityDescription: nil) {
            img.draw(in: r.insetBy(dx: 24, dy: 26))
        }
    }

    override func mouseDown(with event: NSEvent) { downEvent = event }

    override func mouseDragged(with event: NSEvent) {
        guard let u = fileURL, let down = downEvent else { return }
        downEvent = nil
        FileTransfer.beginDrag(from: self, url: u, event: down, frame: bounds.insetBy(dx: 12, dy: 12))
    }

    func draggingSession(_ session: NSDraggingSession,
                         sourceOperationMaskFor context: NSDraggingContext) -> NSDragOperation {
        FileTransfer.dragOperations
    }

    func draggingSession(_ session: NSDraggingSession, endedAt screenPoint: NSPoint, operation: NSDragOperation) {
        onDragEnded?(operation != [])
    }
}

// MARK: - 悬浮球

@MainActor
final class FloatBallView: NSView, NSDraggingSource {
    /// 按下鼠标时调用，返回要拖出去的文档
    var provideDoc: (() -> DocInfo?)?
    var onNoDoc: (() -> Void)?
    var onClick: (() -> Void)?
    var onRightClick: ((NSEvent) -> Void)?
    var onHover: (() -> Void)?
    var onMoved: (() -> Void)?
    var onDragEnded: ((Bool) -> Void)?
    /// 缺少权限时显示红点
    var warning = false { didSet { if warning != oldValue { needsDisplay = true } } }

    private var downEvent: NSEvent?
    private var downDoc: DocInfo?
    private var downMouse = NSPoint.zero
    private var downOrigin = NSPoint.zero
    private var moving = false
    private var dragged = false
    private var hovering = false { didSet { needsDisplay = true } }

    override var mouseDownCanMoveWindow: Bool { false }
    override func acceptsFirstMouse(for event: NSEvent?) -> Bool { true }

    override func updateTrackingAreas() {
        super.updateTrackingAreas()
        trackingAreas.forEach(removeTrackingArea)
        addTrackingArea(NSTrackingArea(rect: bounds, options: [.mouseEnteredAndExited, .activeAlways, .inVisibleRect],
                                       owner: self, userInfo: nil))
    }

    override func mouseEntered(with event: NSEvent) { hovering = true; onHover?() }
    override func mouseExited(with event: NSEvent) { hovering = false }

    override func draw(_ dirtyRect: NSRect) {
        let r = bounds.insetBy(dx: 4, dy: 4)
        let circle = NSBezierPath(ovalIn: r)
        NSColor.controlAccentColor.withAlphaComponent(hovering ? 1.0 : 0.82).setFill()
        circle.fill()
        NSColor.white.withAlphaComponent(0.9).setStroke()
        circle.lineWidth = 2
        circle.stroke()
        if let img = NSImage(systemSymbolName: "doc.badge.arrow.up", accessibilityDescription: nil)
            ?? NSImage(systemSymbolName: "doc", accessibilityDescription: nil) {
            let cfg = NSImage.SymbolConfiguration(pointSize: 20, weight: .semibold)
                .applying(NSImage.SymbolConfiguration(paletteColors: [.white]))
            let tinted = img.withSymbolConfiguration(cfg) ?? img
            let s = tinted.size
            tinted.draw(in: NSRect(x: bounds.midX - s.width / 2, y: bounds.midY - s.height / 2, width: s.width, height: s.height))
        }
        if warning {
            let dot = NSBezierPath(ovalIn: NSRect(x: bounds.maxX - 18, y: bounds.maxY - 18, width: 14, height: 14))
            NSColor.systemRed.setFill()
            dot.fill()
            NSColor.white.setStroke()
            dot.lineWidth = 1.5
            dot.stroke()
        }
    }

    override func mouseDown(with event: NSEvent) {
        downEvent = event
        downMouse = NSEvent.mouseLocation
        downOrigin = window?.frame.origin ?? .zero
        moving = event.modifierFlags.contains(.option)
        dragged = false
        downDoc = moving ? nil : provideDoc?()
    }

    override func mouseDragged(with event: NSEvent) {
        let m = NSEvent.mouseLocation
        let dx = m.x - downMouse.x, dy = m.y - downMouse.y
        if !dragged && hypot(dx, dy) < 4 { return }
        if moving {
            dragged = true
            window?.setFrameOrigin(NSPoint(x: downOrigin.x + dx, y: downOrigin.y + dy))
            return
        }
        guard !dragged else { return }
        dragged = true
        guard let d = downDoc, let down = downEvent else { onNoDoc?(); return }
        FileTransfer.beginDrag(from: self, url: d.url, event: down, frame: bounds)
    }

    override func mouseUp(with event: NSEvent) {
        if moving { if dragged { onMoved?() }; return }
        if !dragged { onClick?() }
    }

    override func rightMouseDown(with event: NSEvent) { onRightClick?(event) }

    func draggingSession(_ session: NSDraggingSession,
                         sourceOperationMaskFor context: NSDraggingContext) -> NSDragOperation {
        FileTransfer.dragOperations
    }

    func draggingSession(_ session: NSDraggingSession, endedAt screenPoint: NSPoint, operation: NSDragOperation) {
        onDragEnded?(operation != [])
    }
}

// MARK: - 拖拽浮窗

enum PanelAction {
    case copyFile, copyPath, reveal, refresh
    case pin(Bool)
    case dragEnded(Bool)
}

@MainActor
final class DropPanelController: NSObject {
    let panel: NSPanel
    private let iconView = DragIconView()
    private let nameLabel = NSTextField(labelWithString: "")
    private let pathLabel = NSTextField(wrappingLabelWithString: "")
    private let metaLabel = NSTextField(labelWithString: "")
    private let pinBox = NSButton(checkboxWithTitle: "常驻桌面并自动跟随当前文档", target: nil, action: nil)
    private var positioned = false
    private(set) var doc: DocInfo?
    var onAction: ((PanelAction) -> Void)?
    var isVisible: Bool { panel.isVisible }

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return f
    }()

    override init() {
        panel = NSPanel(contentRect: NSRect(x: 0, y: 0, width: 460, height: 200),
                        styleMask: [.titled, .closable, .nonactivatingPanel, .fullSizeContentView, .utilityWindow],
                        backing: .buffered, defer: false)
        super.init()
        panel.title = "文档快投"
        panel.titlebarAppearsTransparent = true
        panel.isFloatingPanel = true
        panel.level = .floating
        panel.hidesOnDeactivate = false
        panel.isReleasedWhenClosed = false
        panel.becomesKeyOnlyIfNeeded = true
        panel.isMovableByWindowBackground = true
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        buildUI()
        show(doc: nil)
        positioned = panel.setFrameUsingName("DocDropPanel")
        panel.setFrameAutosaveName("DocDropPanel")
    }

    private func button(_ title: String, _ sel: Selector) -> NSButton {
        let b = NSButton(title: title, target: self, action: sel)
        b.bezelStyle = .rounded
        return b
    }

    private func buildUI() {
        let fx = NSVisualEffectView()
        fx.material = .popover
        fx.blendingMode = .behindWindow
        fx.state = .active
        panel.contentView = fx

        iconView.translatesAutoresizingMaskIntoConstraints = false
        iconView.onDragEnded = { [weak self] ok in self?.onAction?(.dragEnded(ok)) }

        nameLabel.font = .boldSystemFont(ofSize: 14)
        nameLabel.lineBreakMode = .byTruncatingMiddle
        pathLabel.font = .systemFont(ofSize: 11)
        pathLabel.textColor = .secondaryLabelColor
        pathLabel.isSelectable = true
        pathLabel.maximumNumberOfLines = 3
        pathLabel.lineBreakMode = .byCharWrapping
        pathLabel.preferredMaxLayoutWidth = 320
        metaLabel.font = .systemFont(ofSize: 11)
        metaLabel.textColor = .tertiaryLabelColor

        let info = NSStackView(views: [nameLabel, pathLabel, metaLabel])
        info.orientation = .vertical
        info.alignment = .leading
        info.spacing = 4
        info.translatesAutoresizingMaskIntoConstraints = false

        let top = NSStackView(views: [iconView, info])
        top.orientation = .horizontal
        top.alignment = .top
        top.spacing = 14

        let buttons = NSStackView(views: [
            button("打开所在文件夹", #selector(revealClicked)),
            button("复制路径", #selector(copyPathClicked)),
            button("复制文件", #selector(copyFileClicked)),
            button("刷新", #selector(refreshClicked)),
        ])
        buttons.orientation = .horizontal
        buttons.spacing = 8

        let hint = NSTextField(wrappingLabelWithString: "把左侧图标拖进 AI 窗口即可上传；或点「复制文件」，再到 AI 输入框粘贴（Windows 键盘按 Win+V）。")
        hint.font = .systemFont(ofSize: 11)
        hint.textColor = .tertiaryLabelColor
        hint.preferredMaxLayoutWidth = 418

        pinBox.target = self
        pinBox.action = #selector(pinClicked)
        pinBox.font = .systemFont(ofSize: 11)

        let root = NSStackView(views: [top, buttons, hint, pinBox])
        root.orientation = .vertical
        root.alignment = .leading
        root.spacing = 10
        root.edgeInsets = NSEdgeInsets(top: 30, left: 16, bottom: 14, right: 16)
        root.translatesAutoresizingMaskIntoConstraints = false
        fx.addSubview(root)

        NSLayoutConstraint.activate([
            root.leadingAnchor.constraint(equalTo: fx.leadingAnchor),
            root.trailingAnchor.constraint(equalTo: fx.trailingAnchor),
            root.topAnchor.constraint(equalTo: fx.topAnchor),
            root.bottomAnchor.constraint(equalTo: fx.bottomAnchor),
            iconView.widthAnchor.constraint(equalToConstant: 84),
            iconView.heightAnchor.constraint(equalToConstant: 84),
            info.widthAnchor.constraint(equalToConstant: 320),
        ])
    }

    func show(doc: DocInfo?) {
        self.doc = doc
        iconView.fileURL = doc?.url
        if let d = doc {
            nameLabel.stringValue = d.name
            pathLabel.stringValue = d.path
            pathLabel.toolTip = d.path
        } else {
            nameLabel.stringValue = "还没有抓取文档"
            pathLabel.stringValue = "先点一下 Word / WPS / Excel / PPT 里的文档，再单击悬浮球"
            pathLabel.toolTip = nil
        }
        refreshMeta()
        if let v = panel.contentView { panel.setContentSize(v.fittingSize) }
    }

    func refreshMeta() {
        guard let d = doc else { metaLabel.stringValue = ""; return }
        let date = (try? d.url.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate
        let when = date.map { Self.dateFormatter.string(from: $0) } ?? "未知"
        metaLabel.stringValue = "来自 \(d.appName) · 磁盘版本保存于 \(when)"
    }

    func setPinnedState(_ on: Bool) { pinBox.state = on ? .on : .off }

    func present(near point: NSPoint?) {
        let size = panel.frame.size
        if let p = point {
            var o = NSPoint(x: p.x - size.width / 2, y: p.y - size.height - 16)
            if let vf = (NSScreen.screens.first { NSMouseInRect(p, $0.frame, false) } ?? NSScreen.main)?.visibleFrame {
                o.x = min(max(o.x, vf.minX + 8), vf.maxX - size.width - 8)
                o.y = min(max(o.y, vf.minY + 8), vf.maxY - size.height - 8)
            }
            panel.setFrameOrigin(o)
            positioned = true
        } else if !positioned, let vf = NSScreen.main?.visibleFrame {
            panel.setFrameOrigin(NSPoint(x: vf.maxX - size.width - 24, y: vf.maxY - size.height - 24))
            positioned = true
        }
        panel.orderFrontRegardless()
    }

    func hide() { panel.orderOut(nil) }

    @objc private func copyFileClicked() { onAction?(.copyFile) }
    @objc private func copyPathClicked() { onAction?(.copyPath) }
    @objc private func revealClicked() { onAction?(.reveal) }
    @objc private func refreshClicked() { onAction?(.refresh) }
    @objc private func pinClicked() { onAction?(.pin(pinBox.state == .on)) }
}

// MARK: - 主程序

@main
@MainActor
final class AppController: NSObject, NSApplicationDelegate, NSMenuDelegate {
    private static var keeper: AppController?

    static func main() {
        let app = NSApplication.shared
        let controller = AppController()
        keeper = controller
        app.delegate = controller
        app.setActivationPolicy(.accessory)
        app.run()
    }

    private var statusItem: NSStatusItem!
    private var dropPanel: DropPanelController!
    private var ballPanel: NSPanel!
    private var ballView: FloatBallView!
    private var lastApp: NSRunningApplication?
    private var lastDocumentApp: NSRunningApplication?
    private var lastDoc: DocInfo?
    private var lastReason = ""
    private var menuDoc: DocInfo?
    private var followTimer: Timer?
    private var healthTimer: Timer?
    private var lastFollowKey = ""
    private let defaults = UserDefaults.standard

    private var autoSave: Bool {
        get { defaults.bool(forKey: "autoSave") }
        set { defaults.set(newValue, forKey: "autoSave") }
    }
    private var pinned: Bool {
        get { defaults.bool(forKey: "pinned") }
        set { defaults.set(newValue, forKey: "pinned") }
    }
    private var hotkeysEnabled: Bool {
        get { defaults.bool(forKey: "hotkeysEnabled") }
        set { defaults.set(newValue, forKey: "hotkeysEnabled") }
    }
    private var ballVisible: Bool {
        get { defaults.object(forKey: "ballVisible") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "ballVisible") }
    }
    private var altRightClick: Bool {
        get { defaults.object(forKey: "altRightClick") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "altRightClick"); gHookEnabled = newValue }
    }
    private var recent: [String] {
        get { defaults.stringArray(forKey: "recent") ?? [] }
        set { defaults.set(Array(newValue.prefix(12)), forKey: "recent") }
    }

    static let helpText = """
    【看路径 / 打开所在文件夹】
    在 Word / WPS / Excel / PPT 的文档窗口里，按住 Alt 键点鼠标右键，
    会弹出菜单：文件路径、打开所在文件夹、复制路径、复制文件。
    （右键悬浮球也是同一个菜单。）

    【发给 AI】
    · 拖动悬浮球 → 把当前文档直接拖进 AI 窗口上传
    · 单击悬浮球 → 详情浮窗
    · 按住 Alt 拖动悬浮球 → 移动它的位置

    先点一下文档窗口，再去操作悬浮球。切到浏览器后，悬浮球仍记得刚才的文档。

    【识别不到文档？】
    悬浮球上有红点 = 缺少「辅助功能」权限：系统设置 › 隐私与安全性 › 辅助功能，打开 DocDrop。
    右键菜单里的「识别诊断」会告诉你具体原因，可以把诊断内容复制给 Claude。
    """

    // MARK: 启动

    func applicationDidFinishLaunching(_ notification: Notification) {
        dropPanel = DropPanelController()
        dropPanel.onAction = { [weak self] a in self?.handle(a) }

        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        if let img = NSImage(systemSymbolName: "doc.badge.arrow.up", accessibilityDescription: "文档快投")
            ?? NSImage(systemSymbolName: "doc", accessibilityDescription: "文档快投") {
            img.isTemplate = true
            statusItem.button?.image = img
        } else {
            statusItem.button?.title = "DD"
        }
        let menu = NSMenu()
        menu.delegate = self
        statusItem.menu = menu

        lastApp = NSWorkspace.shared.frontmostApplication
        RightClickHook.updateFront(lastApp)
        NSWorkspace.shared.notificationCenter.addObserver(
            forName: NSWorkspace.didActivateApplicationNotification, object: nil, queue: .main
        ) { [weak self] note in
            guard let app = note.userInfo?[NSWorkspace.applicationUserInfoKey] as? NSRunningApplication else { return }
            Task { @MainActor in
                RightClickHook.updateFront(app)
                if app.processIdentifier != getpid() { self?.lastApp = app }
                if DocLocator.isDocApp(app.bundleIdentifier ?? "") {
                    self?.lastDocumentApp = app
                }
            }
        }

        gHookEnabled = altRightClick
        RightClickHook.onFire = { [weak self] in self?.showDocMenuAtMouse() }

        if hotkeysEnabled { registerHotkeys() }
        setupBall()

        // 请求“辅助功能”权限（读取窗口信息、Alt+右键菜单、模拟 ⌘S 都需要）
        let opts = ["AXTrustedCheckOptionPrompt": true] as CFDictionary
        _ = AXIsProcessTrustedWithOptions(opts)

        // 每 2 秒检查一次权限：拿到权限后自动启用 Alt+右键，悬浮球红点消失
        healthTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.checkHealth() }
        }
        checkHealth()

        if pinned { setPinned(true) }

        if !defaults.bool(forKey: "launchedBefore.v3") {
            defaults.set(true, forKey: "launchedBefore.v3")
            showHelp()
        }
    }

    private func checkHealth() {
        let ok = AXIsProcessTrusted()
        if ok { RightClickHook.install() }
        ballView?.warning = !ok
    }

    private func registerHotkeys() {
        let mods = controlKey | optionKey
        HotKeys.register(id: 1, keyCode: kVK_ANSI_D, modifiers: mods) { [weak self] in self?.showPanel(near: NSEvent.mouseLocation) }
        HotKeys.register(id: 2, keyCode: kVK_ANSI_C, modifiers: mods) { [weak self] in self?.withDoc { d in self?.copyFile(d) } }
        HotKeys.register(id: 3, keyCode: kVK_ANSI_P, modifiers: mods) { [weak self] in self?.withDoc { d in self?.copyPath(d) } }
        HotKeys.register(id: 4, keyCode: kVK_ANSI_F, modifiers: mods) { [weak self] in self?.withDoc { d in self?.reveal(d) } }
    }

    // MARK: 识别当前文档

    private func targetApp() -> NSRunningApplication? {
        if let f = NSWorkspace.shared.frontmostApplication,
           DocLocator.isDocApp(f.bundleIdentifier ?? "") {
            lastDocumentApp = f
            return f
        }
        if let app = lastDocumentApp, !app.isTerminated { return app }
        // On restart there is no activation history: a single open document app
        // is unambiguous. Never guess between multiple editors.
        let editors = NSWorkspace.shared.runningApplications.filter {
            !$0.isTerminated && DocLocator.isDocApp($0.bundleIdentifier ?? "")
                && DocLocator.windowTitle(pid: $0.processIdentifier) != nil
        }
        if editors.count == 1 { lastDocumentApp = editors[0]; return editors[0] }
        return NSWorkspace.shared.frontmostApplication ?? lastApp
    }

    /// allowLast：前台不是文档应用（比如已切到浏览器）时，使用最近一次识别到的文档
    private func resolveDoc(fresh: Bool, allowLast: Bool) -> DocInfo? {
        guard let app = targetApp() else {
            lastReason = "没有找到前台应用。"
            return allowLast ? validLastDoc() : nil
        }
        switch DocLocator.locate(app: app, fresh: fresh) {
        case .found(let d):
            remember(d)
            return d
        case .failed(let why):
            if allowLast, !DocLocator.isDocApp(app.bundleIdentifier ?? ""), let d = validLastDoc() { return d }
            lastReason = why
            return nil
        }
    }

    private func validLastDoc() -> DocInfo? {
        guard let d = lastDoc, FileManager.default.fileExists(atPath: d.path) else { return nil }
        return d
    }

    /// 识别文档后执行操作（开启“抓取前自动保存”时先按 ⌘S）
    private func withDoc(_ then: @escaping (DocInfo) -> Void) {
        let app = targetApp()
        if autoSave, AXIsProcessTrusted(), let app,
           NSWorkspace.shared.frontmostApplication?.processIdentifier == app.processIdentifier {
            Keys.sendSave()
            Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: 900_000_000)
                guard let self else { return }
                if let d = self.resolveDoc(fresh: true, allowLast: true) { then(d) } else { Toast.show(self.lastReason, seconds: 4) }
            }
        } else if let d = resolveDoc(fresh: true, allowLast: true) {
            then(d)
        } else {
            Toast.show(lastReason, seconds: 4)
        }
    }

    // MARK: 路径菜单（Alt+右键、右键悬浮球、菜单栏共用）

    private func fillDocMenu(_ m: NSMenu, doc: DocInfo?, includePanel: Bool) {
        menuDoc = doc
        if let d = doc {
            let head = NSMenuItem(title: d.name, action: nil, keyEquivalent: "")
            head.attributedTitle = NSAttributedString(string: d.name, attributes: [.font: NSFont.boldSystemFont(ofSize: 13)])
            head.image = icon(d.path)
            head.isEnabled = false
            m.addItem(head)

            let p = addItem(m, d.path, #selector(menuCopyPath))
            p.attributedTitle = NSAttributedString(string: d.path, attributes: [
                .font: NSFont.systemFont(ofSize: 11), .foregroundColor: NSColor.secondaryLabelColor,
            ])
            p.toolTip = "点击复制路径"
            m.addItem(.separator())

            addItem(m, "打开所在文件夹", #selector(menuReveal))

            let levels = NSMenuItem(title: "路径层级（点击打开）", action: nil, keyEquivalent: "")
            let sub = NSMenu()
            var folder = (d.path as NSString).deletingLastPathComponent
            while !folder.isEmpty && folder != "/" {
                let it = NSMenuItem(title: FileManager.default.displayName(atPath: folder), action: #selector(openFolder(_:)), keyEquivalent: "")
                it.target = self
                it.representedObject = folder
                it.image = icon(folder)
                it.toolTip = folder
                sub.addItem(it)
                folder = (folder as NSString).deletingLastPathComponent
            }
            levels.submenu = sub
            m.addItem(levels)

            addItem(m, "复制文件路径", #selector(menuCopyPath))
            addItem(m, "复制文件（可粘贴到微信 / AI / Finder）", #selector(menuCopyFile))
            addItem(m, "存一份副本到…", #selector(menuSaveCopy))
            if includePanel { addItem(m, "显示拖拽浮窗", #selector(menuShowPanel)) }
        } else {
            let head = NSMenuItem(title: "未识别到文档", action: nil, keyEquivalent: "")
            head.attributedTitle = NSAttributedString(string: "未识别到文档", attributes: [.font: NSFont.boldSystemFont(ofSize: 13)])
            head.isEnabled = false
            m.addItem(head)
            for line in lastReason.split(separator: "\n") {
                let it = NSMenuItem(title: String(line), action: nil, keyEquivalent: "")
                it.isEnabled = false
                m.addItem(it)
            }
            m.addItem(.separator())
            if !AXIsProcessTrusted() { addItem(m, "打开「辅助功能」设置…", #selector(openAXSettings)) }
            if lastReason.contains("自动化") { addItem(m, "打开「自动化」设置…", #selector(openAutomationSettings)) }
        }
        m.addItem(.separator())
        addItem(m, "识别诊断…", #selector(showDiagnosis))
    }

    private func icon(_ path: String) -> NSImage {
        let img = NSWorkspace.shared.icon(forFile: path)
        img.size = NSSize(width: 16, height: 16)
        return img
    }

    private func showDocMenuAtMouse() {
        let d = resolveDoc(fresh: true, allowLast: false)
        let m = NSMenu()
        fillDocMenu(m, doc: d, includePanel: true)
        _ = m.popUp(positioning: nil, at: NSEvent.mouseLocation, in: nil)
    }

    @discardableResult
    private func addItem(_ menu: NSMenu, _ title: String, _ sel: Selector?, key: String = "", on: Bool? = nil) -> NSMenuItem {
        let it = NSMenuItem(title: title, action: sel, keyEquivalent: key)
        it.target = self
        if !key.isEmpty { it.keyEquivalentModifierMask = [.control, .option] }
        if let on { it.state = on ? .on : .off }
        menu.addItem(it)
        return it
    }

    @objc private func menuCopyPath() { if let d = menuDoc { copyPath(d) } }
    @objc private func menuCopyFile() { if let d = menuDoc { copyFile(d) } }
    @objc private func menuReveal() { if let d = menuDoc { reveal(d) } }
    @objc private func menuShowPanel() {
        dropPanel.show(doc: menuDoc ?? dropPanel.doc)
        dropPanel.present(near: pinned ? nil : NSEvent.mouseLocation)
    }
    /// 把当前文档另存一份副本到指定文件夹（原文件不动，Word / WPS 继续编辑原文件）
    @objc private func menuSaveCopy() {
        guard let d = menuDoc else { return }
        NSApp.activate(ignoringOtherApps: true)
        let panel = NSOpenPanel()
        panel.canChooseDirectories = true
        panel.canChooseFiles = false
        panel.canCreateDirectories = true
        panel.allowsMultipleSelection = false
        panel.prompt = "存到这里"
        panel.message = "选择要存放「\(d.name)」副本的文件夹"
        panel.directoryURL = d.url.deletingLastPathComponent()
        guard panel.runModal() == .OK, let dir = panel.url else { return }
        var dest = dir.appendingPathComponent(d.name)
        if FileManager.default.fileExists(atPath: dest.path) {
            let f = DateFormatter()
            f.dateFormat = "yyyyMMdd-HHmmss"
            let base = (d.name as NSString).deletingPathExtension
            let ext = d.url.pathExtension
            dest = dir.appendingPathComponent("\(base) \(f.string(from: Date()))" + (ext.isEmpty ? "" : ".\(ext)"))
        }
        do {
            try FileManager.default.copyItem(at: d.url, to: dest)
            Toast.show("已存一份副本：\n\(dest.path)", seconds: 3)
            FileTransfer.reveal(dest.path)
        } catch {
            Toast.show("复制失败：\(error.localizedDescription)", seconds: 4)
        }
    }

    @objc private func openFolder(_ sender: NSMenuItem) {
        guard let p = sender.representedObject as? String else { return }
        FileTransfer.openFolder(p)
    }

    // MARK: 悬浮球

    private func setupBall() {
        let size: CGFloat = 56
        ballPanel = NSPanel(contentRect: NSRect(x: 0, y: 0, width: size, height: size),
                            styleMask: [.borderless, .nonactivatingPanel], backing: .buffered, defer: false)
        ballPanel.isOpaque = false
        ballPanel.backgroundColor = .clear
        ballPanel.hasShadow = true
        ballPanel.level = .floating
        ballPanel.isFloatingPanel = true
        ballPanel.hidesOnDeactivate = false
        ballPanel.isReleasedWhenClosed = false
        ballPanel.becomesKeyOnlyIfNeeded = true
        ballPanel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .stationary]

        ballView = FloatBallView(frame: NSRect(x: 0, y: 0, width: size, height: size))
        ballPanel.contentView = ballView

        ballView.provideDoc = { [weak self] in self?.docForBallPress() }
        ballView.onNoDoc = { [weak self] in Toast.show(self?.lastReason ?? "", seconds: 4) }
        ballView.onClick = { [weak self] in self?.ballClicked() }
        ballView.onRightClick = { [weak self] e in self?.ballRightClicked(e) }
        ballView.onHover = { [weak self] in self?.ballHovered() }
        ballView.onMoved = { [weak self] in
            guard let self else { return }
            self.defaults.set(self.ballPanel.frame.origin.x, forKey: "ballX")
            self.defaults.set(self.ballPanel.frame.origin.y, forKey: "ballY")
        }
        ballView.onDragEnded = { ok in if ok { Toast.show("已发送 ✓") } }

        var origin = NSPoint(x: defaults.double(forKey: "ballX"), y: defaults.double(forKey: "ballY"))
        let rect = NSRect(origin: origin, size: NSSize(width: size, height: size))
        let onScreen = NSScreen.screens.contains { $0.visibleFrame.insetBy(dx: -4, dy: -4).contains(rect) }
        if defaults.object(forKey: "ballX") == nil || !onScreen, let vf = NSScreen.main?.visibleFrame {
            origin = NSPoint(x: vf.maxX - size - 10, y: vf.midY - size / 2)
        }
        ballPanel.setFrameOrigin(origin)
        if ballVisible { ballPanel.orderFrontRegardless() }
    }

    /// 按下悬浮球时识别文档（开启自动保存时顺便按 ⌘S；文件在松手前就会写好）
    private func docForBallPress() -> DocInfo? {
        guard let d = resolveDoc(fresh: true, allowLast: true) else { return nil }
        if autoSave, AXIsProcessTrusted(), let app = targetApp(), DocLocator.isDocApp(app.bundleIdentifier ?? ""),
           NSWorkspace.shared.frontmostApplication?.processIdentifier == app.processIdentifier {
            Keys.sendSave()
            Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                self?.dropPanel.refreshMeta()
            }
        }
        return d
    }

    private func ballClicked() {
        if dropPanel.isVisible && !pinned { dropPanel.hide(); return }
        guard let d = resolveDoc(fresh: true, allowLast: true) else {
            let m = NSMenu()
            fillDocMenu(m, doc: nil, includePanel: false)
            _ = m.popUp(positioning: nil, at: NSEvent.mouseLocation, in: nil)
            return
        }
        dropPanel.show(doc: d)
        let f = ballPanel.frame
        dropPanel.present(near: pinned ? nil : NSPoint(x: f.midX, y: f.minY))
    }

    private func ballHovered() {
        guard !dropPanel.isVisible else { return }
        if let d = resolveDoc(fresh: false, allowLast: true) {
            Toast.show("拖动我 → 发送「\(d.name)」\n单击看详情 · 右键看路径 / 打开所在文件夹")
        } else {
            Toast.show(lastReason + "\n（右键我 → 识别诊断）", seconds: 4)
        }
    }

    private func ballRightClicked(_ event: NSEvent) {
        let d = resolveDoc(fresh: true, allowLast: true)
        let m = NSMenu()
        fillDocMenu(m, doc: d, includePanel: true)
        m.addItem(.separator())
        addItem(m, "抓取前自动保存", #selector(toggleAutoSave), on: autoSave)
        addItem(m, "隐藏悬浮球（可在菜单栏图标里重新打开）", #selector(toggleBall))
        NSMenu.popUpContextMenu(m, with: event, for: ballView)
    }

    // MARK: 浮窗

    private func showPanel(near point: NSPoint) {
        if dropPanel.isVisible && !pinned { dropPanel.hide(); return }
        withDoc { [weak self] d in
            guard let self else { return }
            self.dropPanel.show(doc: d)
            self.dropPanel.present(near: self.pinned ? nil : point)
        }
    }

    private func handle(_ a: PanelAction) {
        switch a {
        case .copyFile: if let d = dropPanel.doc { copyFile(d) }
        case .copyPath: if let d = dropPanel.doc { copyPath(d) }
        case .reveal: if let d = dropPanel.doc { reveal(d) }
        case .refresh:
            if let d = resolveDoc(fresh: true, allowLast: true) { dropPanel.show(doc: d) } else { Toast.show(lastReason, seconds: 4) }
        case .pin(let on): setPinned(on)
        case .dragEnded(let ok): if ok && !pinned { dropPanel.hide() }
        }
    }

    // MARK: 动作

    private func copyFile(_ d: DocInfo) {
        FileTransfer.copyToClipboard(d.url)
        Toast.show("已复制文件「\(d.name)」\n可在微信 / LINE / AI 输入框 / Finder 里粘贴（Windows 键盘按 Win+V）", seconds: 3)
    }

    private func copyPath(_ d: DocInfo) {
        let pb = NSPasteboard.general
        pb.clearContents()
        pb.setString(d.path, forType: .string)
        Toast.show("已复制路径：\n\(d.path)")
    }

    private func reveal(_ d: DocInfo) {
        FileTransfer.reveal(d.path)
    }

    private func remember(_ d: DocInfo) {
        lastDoc = d
        recent = [d.path] + recent.filter { $0 != d.path }
    }

    // MARK: 常驻 & 自动跟随

    private func setPinned(_ on: Bool) {
        pinned = on
        dropPanel.setPinnedState(on)
        followTimer?.invalidate()
        followTimer = nil
        guard on else { return }
        lastFollowKey = ""
        followTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.followTick() }
        }
        if !dropPanel.isVisible { dropPanel.present(near: nil) }
        followTick()
    }

    private func followTick() {
        guard pinned, let f = NSWorkspace.shared.frontmostApplication, f.processIdentifier != getpid() else { return }
        dropPanel.refreshMeta()
        guard DocLocator.isDocApp(f.bundleIdentifier ?? "") else { return }
        let key = "\(f.processIdentifier)|\(DocLocator.windowTitle(pid: f.processIdentifier) ?? "")"
        guard key != lastFollowKey else { return }
        lastFollowKey = key
        if case .found(let d) = DocLocator.locate(app: f, fresh: false), d != dropPanel.doc {
            remember(d)
            dropPanel.show(doc: d)
        }
    }

    // MARK: 菜单栏菜单

    func menuNeedsUpdate(_ menu: NSMenu) {
        menu.removeAllItems()
        fillDocMenu(menu, doc: resolveDoc(fresh: false, allowLast: true), includePanel: true)

        let recentItem = NSMenuItem(title: "最近抓取", action: nil, keyEquivalent: "")
        let sub = NSMenu()
        for p in recent where FileManager.default.fileExists(atPath: p) {
            let it = NSMenuItem(title: (p as NSString).lastPathComponent, action: #selector(openRecent(_:)), keyEquivalent: "")
            it.target = self
            it.representedObject = p
            it.toolTip = p
            it.image = icon(p)
            sub.addItem(it)
        }
        if sub.items.isEmpty {
            let none = NSMenuItem(title: "（暂无）", action: nil, keyEquivalent: "")
            none.isEnabled = false
            sub.addItem(none)
        }
        recentItem.submenu = sub
        menu.addItem(recentItem)

        menu.addItem(.separator())
        addItem(menu, "文档窗口里 Alt+右键 显示路径菜单", #selector(toggleAltRightClick), on: altRightClick)
        addItem(menu, "显示悬浮球", #selector(toggleBall), on: ballVisible)
        addItem(menu, "浮窗常驻并自动跟随当前文档", #selector(togglePinned), on: pinned)
        addItem(menu, "抓取前自动保存", #selector(toggleAutoSave), on: autoSave)
        addItem(menu, "启用键盘快捷键（Ctrl+Alt+D/C/P/F）", #selector(toggleHotkeys), on: hotkeysEnabled)
        menu.addItem(.separator())
        if !AXIsProcessTrusted() { addItem(menu, "⚠︎ 去开启「辅助功能」权限…", #selector(openAXSettings)) }
        addItem(menu, "使用说明", #selector(showHelp))
        addItem(menu, "退出文档快投", #selector(quit))
    }

    @objc private func openRecent(_ sender: NSMenuItem) {
        guard let p = sender.representedObject as? String else { return }
        dropPanel.show(doc: DocInfo(url: URL(fileURLWithPath: p), appName: "最近抓取"))
        dropPanel.present(near: pinned ? nil : NSEvent.mouseLocation)
    }

    @objc private func toggleAltRightClick() {
        altRightClick.toggle()
        Toast.show(altRightClick ? "已开启：文档窗口里 Alt+右键 显示路径菜单" : "已关闭 Alt+右键 菜单")
    }
    @objc private func togglePinned() { setPinned(!pinned) }
    @objc private func toggleAutoSave() {
        autoSave.toggle()
        if autoSave && !AXIsProcessTrusted() { Toast.show("自动保存需要「辅助功能」权限") }
    }
    @objc private func toggleBall() {
        ballVisible.toggle()
        if ballVisible { ballPanel.orderFrontRegardless() } else { ballPanel.orderOut(nil) }
    }
    @objc private func toggleHotkeys() {
        hotkeysEnabled.toggle()
        HotKeys.unregisterAll()
        if hotkeysEnabled {
            registerHotkeys()
            Toast.show("已开启快捷键：Ctrl+Alt+D / C / P / F")
        } else {
            Toast.show("已关闭快捷键")
        }
    }

    @objc private func openAXSettings() {
        if let u = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility") {
            NSWorkspace.shared.open(u)
        }
    }
    @objc private func openAutomationSettings() {
        if let u = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Automation") {
            NSWorkspace.shared.open(u)
        }
    }

    // MARK: 识别诊断

    @objc private func showDiagnosis() {
        var lines = ["文档快投 \(appVersion) · macOS \(ProcessInfo.processInfo.operatingSystemVersionString)",
                     "Alt+右键：\(altRightClick ? "开启" : "关闭")，\(RightClickHook.installed ? "已生效" : "未生效（缺少辅助功能权限）")"]
        if let app = targetApp() {
            let t = Trace()
            _ = DocLocator.locate(app: app, fresh: true, trace: t)
            lines += t.lines
        } else {
            lines.append("没有找到前台应用")
        }
        let text = lines.joined(separator: "\n")

        NSApp.activate(ignoringOtherApps: true)
        let a = NSAlert()
        a.messageText = "识别诊断"
        a.informativeText = "下面是识别当前文档的过程。遇到问题时，把它复制给 Claude 即可。"
        let sv = NSScrollView(frame: NSRect(x: 0, y: 0, width: 480, height: 240))
        sv.hasVerticalScroller = true
        sv.borderType = .bezelBorder
        let tv = NSTextView(frame: NSRect(x: 0, y: 0, width: 480, height: 240))
        tv.isEditable = false
        tv.font = .monospacedSystemFont(ofSize: 11, weight: .regular)
        tv.string = text
        tv.autoresizingMask = [.width]
        sv.documentView = tv
        a.accessoryView = sv
        a.addButton(withTitle: "复制诊断信息")
        a.addButton(withTitle: "打开辅助功能设置")
        a.addButton(withTitle: "关闭")
        let r = a.runModal()
        if r == .alertFirstButtonReturn {
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(text, forType: .string)
            Toast.show("诊断信息已复制")
        } else if r == .alertSecondButtonReturn {
            openAXSettings()
        }
    }

    @objc private func showHelp() {
        NSApp.activate(ignoringOtherApps: true)
        let a = NSAlert()
        a.messageText = "文档快投 · 使用说明"
        a.informativeText = Self.helpText
        a.addButton(withTitle: "知道了")
        a.runModal()
    }

    @objc private func quit() { NSApp.terminate(nil) }
}
