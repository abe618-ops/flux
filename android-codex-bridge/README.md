# Android Codex Bridge

Android Codex Bridge is an open-source Android device diagnostics and agent-control bridge designed for Codex and other MCP-capable AI agents.

> Status: **v0.1 architecture / bootstrap implementation**

## Goals

Turn an Android phone into a controllable, inspectable target for an AI coding/diagnostic agent without requiring root for the core workflow.

The project is intentionally model-agnostic. Codex, Claude Code, OpenCode, Gemini CLI or another MCP client can be used as the reasoning layer.

## Core capabilities

### Device observation
- Device/build information
- Battery, memory and storage snapshot
- Installed/running app inspection where Android permissions allow it
- Accessibility UI tree
- Screen capture through user-approved MediaProjection
- Current foreground package/activity when available
- Diagnostic event timeline

### Device control
- Accessibility node click
- Tap / swipe gestures
- Text input into editable UI nodes
- Back / Home / Recents global actions
- App launch intents
- Optional Shizuku privileged execution layer
- Optional host-side ADB control layer

### Diagnostics
- App crash workflow
- Camera launch/test workflow
- Permission-state checks
- Storage/battery/basic runtime checks
- Host-side `adb logcat`, `dumpsys`, package/activity inspection
- Exportable Markdown/JSON diagnostic reports

## Architecture

```text
                         AI reasoning layer
             Codex / Claude Code / OpenCode / etc.
                              |
                    MCP / local bridge API
                              |
              +---------------+----------------+
              |                                |
       Host-side bridge                 Android companion
       (ADB + MCP server)              (Android application)
              |                                |
      adb shell / screencap      AccessibilityService
      uiautomator dump           MediaProjection
      logcat / dumpsys           Device diagnostics
              |                  Optional Shizuku
              +---------------+----------------+
                              |
                         Android device
```

## Why two execution paths?

**Host-side ADB** is the safest and most dependable path for deep diagnostics. It can collect `logcat`, `dumpsys`, screenshots and UI hierarchy without pretending a normal Android application has system privileges.

**On-device Accessibility/Shizuku** is useful when the phone should operate more independently. Accessibility handles visible UI interaction; Shizuku can optionally expose a controlled subset of shell/system operations after the user explicitly starts and authorizes it.

The app never claims to bypass Android application sandboxing.

## Security model

Android Codex Bridge is designed around explicit user authorization:

1. Accessibility must be manually enabled by the user.
2. Screen capture requires Android MediaProjection consent.
3. Shizuku is optional and separately authorized.
4. ADB requires Android's debugging trust dialog.
5. Dangerous shell operations should be allow-listed by the bridge.
6. No password extraction, private-app database extraction, lock-screen bypass or sandbox bypass is a project goal.

## Repository layout

```text
android-codex-bridge/
├── README.md
├── LICENSE
├── SECURITY.md
├── docs/
│   ├── ARCHITECTURE.md
│   └── ROADMAP.md
├── android-app/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   └── app/
│       ├── build.gradle.kts
│       └── src/main/...
└── host-bridge/
    ├── pyproject.toml
    └── src/android_codex_bridge/...
```

## v0.1 scope

The first implementation concentrates on a small, auditable foundation:

- Android status dashboard
- Accessibility service
- UI-node snapshot JSON
- Global Back/Home/Recents actions
- Basic tap/swipe support
- Local HTTP bridge skeleton
- Host ADB doctor / device snapshot
- Safe shell-command allow-list design

## Reference research

The architecture was informed by public Android automation projects including:

- `NeoAgentman/mobile-use-mcp` — ADB/uiautomator2 MCP design
- `AlexGladkov/claude-in-mobile` — multi-agent Android ADB/MCP integration
- `xiaoran7/Agent-Android` — raw ADB `input`, `screencap`, `uiautomator dump` approach
- `dascard/Open-AutoGLM-App` — on-device Accessibility/Shizuku VLM agent loop
- `xjunz/AutoTask` — Accessibility + Shizuku automation design

This repository does **not** copy their proprietary code; concepts are studied and reimplemented independently. Any future reused open-source code must retain its applicable license notices.

## Build direction

The Android application targets modern Android and is written in Kotlin. The host bridge is Python to keep ADB/MCP tooling easy to inspect and extend.

More implementation details are in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and [`docs/ROADMAP.md`](docs/ROADMAP.md).

## License

Apache License 2.0. See `LICENSE`.
