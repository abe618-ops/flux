# App diagnostics design

Android Codex Bridge is intended to diagnose visible application failures without pretending that a normal APK can bypass Android sandboxing.

## What the bridge should correlate

A useful diagnosis is not a single log dump. The agent should correlate four views of the same moment:

1. **Visual state** — screenshot and UI hierarchy.
2. **App state** — foreground package/activity, package metadata and process memory.
3. **System state** — battery, storage, memory pressure, Android build and relevant `dumpsys` services.
4. **Failure evidence** — bounded logcat around FATAL EXCEPTION, ANR, native fatal signals, permission errors and service-specific errors.

The first implementation in `host-bridge` can already collect these data through ADB and save a structured JSON report.

## Initial supported problem classes

- Java/Kotlin crashes (`FATAL EXCEPTION`)
- ANR / UI hangs
- native crashes (`Fatal signal`, `SIGSEGV`)
- runtime permission and `SecurityException` failures
- camera failures (`CameraAccessException`, CameraService state)
- memory pressure and process-memory anomalies
- app launch/foreground-state problems
- visible UI-state mismatch via screenshot + UIAutomator hierarchy

## Recommended agent loop

The safe default loop is:

`observe -> collect baseline -> perform one bounded action -> observe again -> detect failure -> collect diagnostics -> explain evidence`

Actions should be narrow and reversible where possible. Destructive operations such as clearing app data, uninstalling apps, rebooting, changing secure settings, or deleting user files are not part of the default autonomous tool surface.

## Example

```bash
android-codex-bridge doctor
android-codex-bridge devices
android-codex-bridge visual-snapshot --output-dir before
android-codex-bridge diagnose --package com.example.app --output report.json
```

For a camera problem, the report also includes `dumpsys media.camera` unless `--no-camera` is supplied.

## Next implementation steps

- expose bounded host functions through MCP so Codex can call them directly;
- add app launch/force-stop and safe intent helpers;
- add time-windowed before/after log capture for reproduction sessions;
- add Android-side MediaProjection screenshot support for no-PC mode;
- add optional Shizuku backend for enhanced local diagnostics;
- add a user-facing diagnostic session screen in the APK;
- produce signed release APKs after the debug build is stable.
