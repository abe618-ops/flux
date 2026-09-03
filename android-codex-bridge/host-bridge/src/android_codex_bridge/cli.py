from __future__ import annotations

import argparse
import json
import shutil

from .adb import AdbClient, list_devices
from .diagnostics import AndroidDiagnostics


def doctor() -> int:
    adb = shutil.which("adb")
    devices = list_devices() if adb else []
    print(json.dumps({"adb": adb, "devices": devices}, ensure_ascii=False, indent=2))
    return 0 if adb else 1


def main() -> int:
    parser = argparse.ArgumentParser(prog="android-codex-bridge")
    parser.add_argument("--serial", help="ADB device serial")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("doctor")
    sub.add_parser("devices")
    sub.add_parser("snapshot")
    sub.add_parser("camera-state")

    visual = sub.add_parser("visual-snapshot")
    visual.add_argument("--output-dir", default="android-visual-snapshot")

    logcat = sub.add_parser("logcat")
    logcat.add_argument("--package")
    logcat.add_argument("--lines", type=int, default=1200)

    diagnose = sub.add_parser("diagnose")
    diagnose.add_argument("--package", help="Target Android package, e.g. com.example.app")
    diagnose.add_argument("--output", default="android-diagnostic.json")
    diagnose.add_argument("--no-camera", action="store_true")

    tap = sub.add_parser("tap")
    tap.add_argument("x", type=int)
    tap.add_argument("y", type=int)

    swipe = sub.add_parser("swipe")
    swipe.add_argument("x1", type=int)
    swipe.add_argument("y1", type=int)
    swipe.add_argument("x2", type=int)
    swipe.add_argument("y2", type=int)
    swipe.add_argument("duration", type=int, nargs="?", default=350)

    key = sub.add_parser("key")
    key.add_argument("keycode")

    args = parser.parse_args()

    if args.command == "doctor":
        return doctor()
    if args.command == "devices":
        print(json.dumps(list_devices(), ensure_ascii=False, indent=2))
        return 0

    if args.command in {"diagnose", "logcat", "camera-state"}:
        diag = AndroidDiagnostics(args.serial)
        if args.command == "diagnose":
            path = diag.save(args.output, args.package, include_camera=not args.no_camera)
            print(str(path.resolve()))
            return 0
        if args.command == "logcat":
            print(diag.logcat(lines=args.lines, package=args.package))
            return 0
        print(diag.camera_state())
        return 0

    client = AdbClient(args.serial)
    if args.command == "snapshot":
        print(json.dumps(client.snapshot(), ensure_ascii=False, indent=2))
    elif args.command == "visual-snapshot":
        print(json.dumps(client.visual_snapshot(args.output_dir), ensure_ascii=False, indent=2))
    elif args.command == "tap":
        result = client.shell("input", "tap", str(args.x), str(args.y))
        print(result.stderr or result.stdout)
        return result.returncode
    elif args.command == "swipe":
        result = client.shell(
            "input", "swipe",
            str(args.x1), str(args.y1), str(args.x2), str(args.y2), str(args.duration)
        )
        print(result.stderr or result.stdout)
        return result.returncode
    elif args.command == "key":
        result = client.shell("input", "keyevent", args.keycode)
        print(result.stderr or result.stdout)
        return result.returncode
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
