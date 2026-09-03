from __future__ import annotations

import json
import shutil
import subprocess
from dataclasses import dataclass


@dataclass
class AdbResult:
    command: list[str]
    returncode: int
    stdout: str
    stderr: str


class AdbClient:
    def __init__(self, serial: str | None = None) -> None:
        adb = shutil.which("adb")
        if not adb:
            raise RuntimeError("ADB not found. Install Android platform-tools and ensure adb is on PATH.")
        self.adb = adb
        self.serial = serial

    def _base(self) -> list[str]:
        cmd = [self.adb]
        if self.serial:
            cmd += ["-s", self.serial]
        return cmd

    def run(self, *args: str, timeout: int = 20) -> AdbResult:
        cmd = self._base() + list(args)
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return AdbResult(cmd, proc.returncode, proc.stdout.strip(), proc.stderr.strip())

    def shell(self, *args: str, timeout: int = 20) -> AdbResult:
        return self.run("shell", *args, timeout=timeout)

    def snapshot(self) -> dict:
        props = self.shell("getprop")
        battery = self.shell("dumpsys", "battery")
        mem = self.shell("dumpsys", "meminfo")
        storage = self.shell("df", "-h", "/data")
        activity = self.shell("dumpsys", "activity", "activities")
        return {
            "serial": self.serial,
            "getprop": props.stdout,
            "battery": battery.stdout,
            "memory": mem.stdout,
            "storage": storage.stdout,
            "activity": activity.stdout,
        }


def list_devices() -> list[dict[str, str]]:
    adb = shutil.which("adb")
    if not adb:
        return []
    proc = subprocess.run([adb, "devices", "-l"], capture_output=True, text=True, timeout=10)
    devices: list[dict[str, str]] = []
    for line in proc.stdout.splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        serial = parts[0]
        state = parts[1] if len(parts) > 1 else "unknown"
        meta = {"serial": serial, "state": state}
        for token in parts[2:]:
            if ":" in token:
                k, v = token.split(":", 1)
                meta[k] = v
        devices.append(meta)
    return devices


def snapshot_json(serial: str | None = None) -> str:
    return json.dumps(AdbClient(serial).snapshot(), ensure_ascii=False, indent=2)
