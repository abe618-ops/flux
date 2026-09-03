from __future__ import annotations

import json
import re
import time
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

CAMERA_SIGNAL_PATTERNS = (
    "CameraService",
    "CameraProvider",
    "Camera3-Device",
    "CameraDevice",
    "camera.provider",
    "cameraserver",
    "MediaProvider",
    "MediaStore",
    "FuseDaemon",
    "sdcard",
    "vold",
    "avc: denied",
    "SELinux",
    "Permission Denial",
    "EACCES",
    "ENOSPC",
    "EROFS",
    "FATAL EXCEPTION",
    "ANR in ",
    "Fatal signal",
    "SIGSEGV",
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

    def clear_logcat(self) -> None:
        self.adb.run("logcat", "-c", timeout=15)

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

    def camera_window_logcat(self, lines: int = 5000, package: str | None = None) -> str:
        raw = self.adb.run("logcat", "-d", "-v", "threadtime", "-t", str(lines), timeout=45).stdout
        package_l = package.lower() if package else None
        keep: list[str] = []
        for line in raw.splitlines():
            lower = line.lower()
            if (package_l and package_l in lower) or any(pattern.lower() in lower for pattern in CAMERA_SIGNAL_PATTERNS):
                keep.append(line)
        return "\n".join(keep)

    def camera_state(self) -> str:
        return self.adb.shell("dumpsys", "media.camera", timeout=30).stdout

    def package_state(self, package: str) -> str:
        return self.adb.shell("dumpsys", "package", package, timeout=30).stdout

    def process_state(self, package: str) -> str:
        return self.adb.shell("dumpsys", "meminfo", package, timeout=30).stdout

    def appops_state(self, package: str) -> str:
        return self.adb.shell("dumpsys", "appops", package, timeout=30).stdout

    def storage_state(self) -> dict[str, str]:
        return {
            "df_data": self.adb.shell("df", "-h", "/data", timeout=20).stdout,
            "df_sdcard": self.adb.shell("df", "-h", "/sdcard", timeout=20).stdout,
            "mount": self.adb.shell("mount", timeout=20).stdout,
        }

    def analyze_logcat(self, text: str) -> list[DiagnosticFinding]:
        findings: list[DiagnosticFinding] = []
        lines = text.splitlines()
        for i, line in enumerate(lines):
            low_line = line.lower()
            if not any(pattern.lower() in low_line for pattern in FATAL_PATTERNS + CAMERA_SIGNAL_PATTERNS):
                continue
            context = "\n".join(lines[max(0, i - 3): min(len(lines), i + 12)])
            low = context.lower()
            if "enospc" in low:
                category = "storage-full"
                suggestion = "Inspect /data and shared-storage free space plus quota/reserved-space behavior."
            elif "erofs" in low or "read-only file system" in low:
                category = "storage-readonly"
                suggestion = "Inspect mount state, filesystem errors, FUSE/vold, and whether shared storage became read-only."
            elif "eacces" in low or "permission denial" in low or "avc: denied" in low or "securityexception" in low:
                category = "permission-storage-selinux"
                suggestion = "Inspect runtime permissions, AppOps, SELinux denials, MediaProvider access and scoped-storage behavior."
            elif "mediaprovider" in low or "mediastore" in low or "fusedaemon" in low:
                category = "media-save-index"
                suggestion = "Inspect MediaProvider insert/write failures, pending rows, FUSE/storage errors and file finalization."
            elif "camera" in low:
                category = "camera-stack"
                suggestion = "Inspect CameraService, provider/HAL errors, camera disconnects, device errors and competing clients."
            elif "outofmemory" in low or "low memory" in low:
                category = "memory"
                suggestion = "Inspect process memory, image buffers, background pressure and repeated allocations."
            elif "anr in" in low:
                category = "anr"
                suggestion = "Inspect main-thread stalls, binder waits, I/O on the UI thread and recent traces."
            else:
                category = "crash"
                suggestion = "Inspect the exception stack, failing component, package version and reproduction timing."
            severity = "high" if any(p.lower() in low for p in FATAL_PATTERNS) else "medium"
            findings.append(DiagnosticFinding(severity, category, line.strip()[:180], context[-2500:], suggestion))
            if len(findings) >= 24:
                break
        return findings

    def collect(self, package: str | None = None, include_camera: bool = True) -> dict:
        snap = self.adb.snapshot()
        logs = self.logcat(package=package)
        return {
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

    def camera_session(self, package: str | None = None, seconds: int = 30) -> dict:
        """Capture only the bounded reproduction window after clearing logcat.

        Intended flow: run command, immediately reproduce the camera save/crash problem
        on the phone during the countdown, then inspect the generated report.
        """
        seconds = max(5, min(seconds, 180))
        started = datetime.now(timezone.utc).isoformat()
        baseline = {
            "foreground_app": self.foreground_app(),
            "camera_state": self.camera_state(),
            "storage": self.storage_state(),
            "package_state": self.package_state(package) if package else None,
            "appops": self.appops_state(package) if package else None,
        }
        self.clear_logcat()
        time.sleep(seconds)
        logs = self.camera_window_logcat(package=package)
        finished = datetime.now(timezone.utc).isoformat()
        return {
            "schema": "android-codex-bridge.camera-session.v1",
            "started_at": started,
            "finished_at": finished,
            "capture_window_seconds": seconds,
            "serial": self.adb.serial,
            "target_package": package,
            "device": self.adb.snapshot(),
            "baseline": baseline,
            "after": {
                "foreground_app": self.foreground_app(),
                "camera_state": self.camera_state(),
                "storage": self.storage_state(),
                "package_state": self.package_state(package) if package else None,
                "process_state": self.process_state(package) if package else None,
                "appops": self.appops_state(package) if package else None,
            },
            "camera_window_logcat": logs,
            "findings": [asdict(x) for x in self.analyze_logcat(logs)],
        }

    def save_camera_session(self, output: str | Path, package: str | None = None, seconds: int = 30) -> Path:
        path = Path(output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(self.camera_session(package, seconds), ensure_ascii=False, indent=2), encoding="utf-8")
        return path

    def save(self, output: str | Path, package: str | None = None, include_camera: bool = True) -> Path:
        path = Path(output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(self.collect(package, include_camera), ensure_ascii=False, indent=2), encoding="utf-8")
        return path
