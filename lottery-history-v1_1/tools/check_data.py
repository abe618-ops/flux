
#!/usr/bin/env python3
import re, sys
from pathlib import Path

specs = {
    "3d_desc.txt": 3,
    "pl3_desc.txt": 3,
    "pl5_desc.txt": 5,
    "kl8_desc.txt": 20,
}
root=Path(sys.argv[1])
ok=True
for name, need in specs.items():
    p=root/name
    if not p.exists() or p.stat().st_size < 1000:
        print(f"ERROR {name}: missing or too small"); ok=False; continue
    text=p.read_text("utf-8", errors="ignore")
    lines=[x for x in text.splitlines() if x.strip()]
    print(f"{name}: {len(lines)} non-empty lines, {p.stat().st_size} bytes")
    if len(lines)<100:
        print(f"ERROR {name}: too few historical rows"); ok=False
if not ok:
    sys.exit(2)
