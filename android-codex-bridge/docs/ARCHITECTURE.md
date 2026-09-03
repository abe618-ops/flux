# Architecture

## 1. Design principles

Android Codex Bridge separates **reasoning**, **observation**, and **execution**.

The AI model should never receive arbitrary unrestricted shell access by default. Device actions are exposed as narrow tools with validation, explicit capability checks, and audit logs.

## 2. Execution layers

### Layer A — Accessibility

Available without root after the user enables the service.

Supported operations:

- inspect visible accessibility node tree
- locate text/content descriptions/class names
- click nodes
- perform global Back/Home/Recents actions
- dispatch gestures
- paste/type into editable nodes

Limitations:

- some apps intentionally hide accessibility semantics
- secure windows and DRM surfaces may not be capturable
- Accessibility cannot read another app's private files/databases

### Layer B — MediaProjection

Used for user-approved screen capture. Consent is controlled by Android and should be requested only when required.

### Layer C — Shizuku (optional)

Provides a higher-privilege execution channel backed by ADB/root identity depending on how the user starts Shizuku.

The bridge will expose only an allow-listed command set. Example diagnostic families:

- `am` activity inspection/launch
- selected `pm` package queries
- selected `dumpsys` services
- safe `settings get` queries

Destructive package-management and arbitrary shell execution are not enabled by default.

### Layer D — Host ADB bridge

This is the preferred mode for device debugging and crash diagnosis.

Observations:

- `adb devices -l`
- `adb shell getprop`
- `adb shell dumpsys ...`
- `adb exec-out screencap -p`
- `adb shell uiautomator dump`
- `adb logcat`

Actions:

- `adb shell input tap`
- `adb shell input swipe`
- `adb shell input keyevent`
- `adb shell input text`
- `adb shell am start`

## 3. Agent loop

```text
Observe -> Normalize -> Reason -> Validate -> Execute -> Verify -> Report
```

Every operation should create an audit event containing:

- timestamp
- tool name
- redacted arguments
- capability used
- success/failure
- resulting foreground package where available

## 4. Diagnostic workflow example: camera crash

1. Snapshot device/build/battery/storage.
2. Resolve default camera package.
3. Start bounded `logcat` capture.
4. Launch camera.
5. Reproduce user-approved interaction sequence.
6. Detect process death/ANR/crash signatures.
7. Query `dumpsys activity`, `dumpsys media.camera`, package state and permissions.
8. Classify evidence into likely layers:
   - app-level exception
   - permission/configuration
   - Android framework
   - camera provider/HAL/service
   - resource/storage pressure
   - possible hardware issue
9. Generate report with evidence and confidence.

## 5. Privacy boundaries

The project must not silently harvest personal content. Screen capture and accessibility observations are transient unless the user explicitly exports a session. Diagnostic exports should support redaction.

## 6. MCP surface (planned)

Initial MCP tools:

- `android_doctor`
- `android_list_devices`
- `android_connect`
- `android_snapshot`
- `android_screenshot`
- `android_ui_tree`
- `android_tap`
- `android_swipe`
- `android_type`
- `android_key`
- `android_launch_app`
- `android_logcat_start`
- `android_logcat_stop`
- `android_dumpsys`
- `android_camera_diagnose`
- `android_export_report`

Tool implementations will remain model-independent.
