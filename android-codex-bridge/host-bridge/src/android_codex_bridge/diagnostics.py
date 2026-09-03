from __future__ import annotations

import json
import re
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path

from .adb import AdbClient


FATAL_PATTERNS = (
    "FATAL EXCEPTION",
    "ANR in ",
    "Fatal signal",
    "SIGSEGV",
    "SecurityException",
    "CameraAccessException",
    "DeadObjectException",
)


@dataclass
class DiagnosticFinding:
    severity: str
    category: str
    title: str
    evidence: str
    suggestion: str


class AndroidDiagnostics:
    """Collect a bounded, read-mostly Android triage bundle through ADB."""

    def __init__(self, serial: str | None = None) -> None:
        self.adb = AdbClient(serial)

    def foreground_app(self) -> str:
        out = self.adb.shell("dumpsys", "window", "windows").stdout
        patterns = (
            r"mCurrentFocus=Window\{[^ ]+ [^ ]+ ([^}/]+(?:/[^} ]+)?)",
            r"mFocusedApp=.*? ([A-Za-z0-9_.$]+)/(\S+)",
        )
        for pattern in patterns:
            match = re.search(pattern, out)
            if match:
                return match.group(1)
        return "unknown"

    def logcat(self, lines: int = 1200, package: str | None = None) -> str:
        raw = self.adb.run("logcat", "-d", "-v", "threadtime", "-t", str(lines), timeout=30).stdout
        if not package:
            return raw
        keep: list[str] = []
        package_l = package.lower()
        for line in raw.splitlines():
            lower = line.lower()
            if package_l in lower or any(p.lower() in lower for p in FATAL_PATTERNS):
                keep.append(line)
        return "\n".join(keep)

    def camera_state(self) -> str:
        return self.adb.shell("dumpsys", "media.camera", timeout=30).stdout

    def package_state(self, package: str) -> str:
        return self.adb.shell("dumpsys", "package", package, timeout=30).stdout

    def process_state(self, package: str) -> str:
        return self.adb.shell("dumpsys", "meminfo", package, timeout=30).stdout

    def analyze_logcat(self, text: str) -> list[DiagnosticFinding]:
        findings: list[DiagnosticFinding] = []
        lines = text.splitlines()
        for i, line in enumerate(lines):
            if not any(pattern.lower() in line.lower() for pattern in FATAL_PATTERNS):
                continue
            context = "\n".join(lines[max(0, i - 3): min(len(lines), i + 12)])
            low = context.lower()
            if "camera" in low:
                category = "camera"
                suggestion = "Inspect camera permission, CameraService state, camera HAL/provider errors, and competing camera clients."
            elif "securityexception" in low or "permission denial" in low:
                category = "permission"
                suggestion = "Inspect runtime permissions, AppOps, exported components, and Android-version permission changes."
            elif "outofmemory" in low or "low memory" in low:
                category = "memory"
                suggestion = "Inspect process memory, bitmap/video buffers, background pressure, and repeated allocations."
            elif "anr in" in low:
                category = "anr"
                suggestion = "Inspect main-thread stalls, binder waits, I/O on the UI thread, and recent traces."
            else:
                category = "crash"
                suggestion = "Inspect the exception stack, failing component, package version, device build, and reproduction steps."
            findings.append(DiagnosticFinding("high", category, line.strip()[:180], context[-2500:], suggestion))
            if len(findings) >= 12:
                break
        return findings

    def collect(self, package: str | None = None, include_camera: bool = True) -> dict:
        snap = self.adb.snapshot()
        logs = self.logcat(package=package)
        report = {
            "schema": "android-codex-bridge.diagnostic.v1",
            "created_at": datetime.now(timezone.utc).isoformat(),
            "serial": self.adb.serial,
            "target_package": package,
            "foreground_app": self.foreground_app(),
            "device": snap,
            "package_state": self.package_state(package) if package else None,
            "process_state": self.process_state(package) if package else None,
            "camera_state": self.camera_state() if include_camera else None,
            "logcat": logs,
            "findings": [asdict(x) for x in self.analyze_logcat(logs)],
        }
        return report

    def save(self, output: str | Path, package: str | None = None, include_camera: bool = True) -> Path:
        path = Path(output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(self.collect(package, include_camera), ensure_ascii=False, indent=2), encoding="utf-8")
        return path
