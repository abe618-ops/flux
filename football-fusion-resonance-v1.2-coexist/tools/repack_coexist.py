#!/usr/bin/env python3
"""Patch an extracted Football Fusion APK so v1.2 can coexist with v1.1."""
from pathlib import Path
import argparse, hashlib, struct, zlib

OLD = "com.abe618.footballfusion.lab"
NEW = "com.abe618.footballfusion.v12"


def patch_utf16(path: Path) -> None:
    data = path.read_bytes()
    old, new = OLD.encode("utf-16le"), NEW.encode("utf-16le")
    if old not in data:
        raise SystemExit(f"{path}: old package not found")
    path.write_bytes(data.replace(old, new))


def patch_dex(path: Path) -> None:
    data = bytearray(path.read_bytes())
    old = OLD.replace(".", "/").encode()
    new = NEW.replace(".", "/").encode()
    if old not in data:
        raise SystemExit(f"{path}: old DEX descriptor not found")
    data[:] = data.replace(old, new)
    data[12:32] = hashlib.sha1(data[32:]).digest()
    data[8:12] = struct.pack("<I", zlib.adler32(data[12:]) & 0xFFFFFFFF)
    path.write_bytes(data)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("root", type=Path, help="extracted APK root")
    args = ap.parse_args()
    if len(OLD) != len(NEW):
        raise SystemExit("package names must remain equal length")
    patch_utf16(args.root / "AndroidManifest.xml")
    patch_utf16(args.root / "resources.arsc")
    patch_dex(args.root / "classes.dex")
    print(f"patched package: {OLD} -> {NEW}")
    print("Sign rebuilt APK with a NEW keystore; do not reuse v1.1 key.")


if __name__ == "__main__":
    main()
