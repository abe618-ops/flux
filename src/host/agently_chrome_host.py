#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Send2Mail Chrome 原生消息宿主：桥接 agently-cli（腾讯 AI 邮箱）。
协议：stdin/stdout 各为 4 字节小端长度 + UTF-8 JSON，一条命令一个响应。
只能向 stdout 写协议消息，调试一律走 stderr。"""

import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import threading
import time
import webbrowser

HOST_LOG = os.path.join(
    os.path.expanduser("~/Library/Application Support/com.local.Send2Mail"), "host.log"
)


def log_line(text):
    try:
        with open(HOST_LOG, "a") as f:
            f.write(time.strftime("%Y-%m-%d %H:%M:%S ") + str(text) + "\n")
    except Exception:
        pass

BUILTIN_PATHS = [
    "/opt/homebrew/bin",
    "/usr/local/bin",
    "/usr/bin",
    "/bin",
    "/usr/sbin",
    "/sbin",
]


def enriched_env():
    env = dict(os.environ)
    path = env.get("PATH", "")
    for p in BUILTIN_PATHS + [
        os.path.expanduser("~/.npm-global/bin"),
        os.path.expanduser("~/bin"),
    ]:
        if p not in path:
            path = p + ":" + path
    env["PATH"] = path
    return env


def find_cli():
    candidates = [
        "/opt/homebrew/bin/agently-cli",
        "/usr/local/bin/agently-cli",
        os.path.expanduser("~/.npm-global/bin/agently-cli"),
        os.path.expanduser("~/bin/agently-cli"),
        "/usr/bin/agently-cli",
    ]
    for c in candidates:
        if os.path.isfile(c) and os.access(c, os.X_OK):
            return c
    try:
        nvm = os.path.expanduser("~/.nvm/versions/node")
        for v in sorted(os.listdir(nvm), reverse=True):
            p = os.path.join(nvm, v, "bin", "agently-cli")
            if os.path.isfile(p) and os.access(p, os.X_OK):
                return p
    except OSError:
        pass
    sh = shutil.which("agently-cli")
    if sh and os.access(sh, os.X_OK):
        return sh
    return None


def run_cli(args, cwd=None, timeout=180):
    bin = find_cli()
    if not bin:
        return -1, "未检测到 agently-cli，请先安装: npm install -g @tencent-qqmail/agently-cli"
    try:
        p = subprocess.run(
            [bin] + args,
            capture_output=True,
            text=True,
            timeout=timeout,
            cwd=cwd,
            env=enriched_env(),
        )
        out = p.stdout or ""
        if p.stderr:
            out += "\n[stderr] " + p.stderr
        return p.returncode, out
    except subprocess.TimeoutExpired:
        return -1, "agently-cli 运行超时"
    except Exception as e:
        return -1, "运行失败: %s" % e


def parse_json(out):
    m = re.search(r"\{.*\}", out or "", re.S)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except Exception:
        return None


def mailbox_from(out):
    j = parse_json(out)
    if not isinstance(j, dict):
        return None

    def take(d):
        if not isinstance(d, dict):
            return None
        if d.get("email"):
            return d["email"]
        aliases = d.get("aliases")
        if isinstance(aliases, list):
            for a in aliases:
                if isinstance(a, dict) and a.get("email"):
                    return a["email"]
        return None

    return take(j) or take(j.get("data"))


def summary(out):
    j = parse_json(out)
    if isinstance(j, dict):
        d = j.get("data")
        if isinstance(d, dict) and d.get("summary"):
            return d["summary"]
        if j.get("message"):
            return j["message"]
        if j.get("error"):
            return j["error"]
    clean = (out or "").strip()
    return clean[:200] if clean else "无输出"


def extract_confirmation(out):
    j = parse_json(out)
    if isinstance(j, dict):
        d = j.get("data")
        if isinstance(d, dict) and d.get("confirmation_token"):
            return d["confirmation_token"]
    m = re.search(r"ctk_[A-Za-z0-9_\-]+", out or "")
    return m.group(0) if m else None


def cmd_status():
    bin = find_cli()
    if not bin:
        return {
            "ok": False,
            "mailbox": None,
            "cli": None,
            "message": "未检测到 agently-cli，请先安装: npm install -g @tencent-qqmail/agently-cli",
        }
    code, out = run_cli(["+me"])
    mb = mailbox_from(out)
    if mb:
        return {"ok": True, "mailbox": mb, "message": "已授权", "cli": bin}
    return {
        "ok": False,
        "mailbox": None,
        "cli": bin,
        "message": "CLI 已找到但未授权或状态获取失败: " + summary(out),
    }


def cmd_auth():
    bin = find_cli()
    if not bin:
        return {"ok": False, "message": "未检测到 agently-cli，请先安装"}
    opened = {"v": False}
    acc = {"v": ""}
    try:
        proc = subprocess.Popen(
            [bin, "auth", "login"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            env=enriched_env(),
        )
    except Exception as e:
        return {"ok": False, "message": "启动授权失败: %s" % e}

    def stream():
        try:
            for line in proc.stdout:
                acc["v"] += line
                m = re.search(r"https?://[^\s\"'<>)]+", acc["v"])
                if m and not opened["v"]:
                    opened["v"] = True
                    webbrowser.open(m.group(0))
        except Exception:
            pass

    t = threading.Thread(target=stream, daemon=True)
    t.start()
    t.join(timeout=250)
    try:
        rc = proc.poll()
    except Exception:
        rc = None
    if rc == 0:
        _, out = run_cli(["+me"])
        mb = mailbox_from(out)
        if mb:
            return {"ok": True, "message": "授权完成，发件邮箱: " + mb}
        return {"ok": True, "message": "授权完成"}
    url_msg = "授权链接已打开请在浏览器中完成" if opened["v"] else ""
    return {"ok": False, "message": "授权未完成。%s（退出码 %s）" % (url_msg, rc)}


def cmd_send(req):
    to = (req.get("to") or "").strip()
    subject = (req.get("subject") or "网页分享").strip() or "网页分享"
    text = req.get("text") or ""
    html = req.get("html") or ""
    if not to:
        return {"ok": False, "message": "未填写接收邮箱"}
    args = ["message", "+send", "--to", to, "--subject", subject]
    cwd = None
    if html:
        try:
            cwd = tempfile.mkdtemp(prefix="send2mail-")
            with open(os.path.join(cwd, "body.html"), "w", encoding="utf-8") as f:
                f.write(html)
            args += ["--body-file", "body.html"]
        except Exception as e:
            return {"ok": False, "message": "写入 HTML 临时文件失败: %s" % e}
    else:
        args += ["--body", text]

    try:
        code1, out1 = run_cli(args, cwd=cwd)
        token = None if code1 != 0 else extract_confirmation(out1)
        if not token:
            return {"ok": False, "message": "获取确认令牌失败：" + summary(out1)}
        code2, out2 = run_cli(args + ["--confirmation-token", token], cwd=cwd)
        if code2 == 0:
            return {"ok": True, "message": summary(out2) or "发送成功"}
        return {"ok": False, "message": "确认发送失败：" + summary(out2)}
    finally:
        if cwd:
            import shutil as _sh

            _sh.rmtree(cwd, ignore_errors=True)


def read_msg():
    raw = sys.stdin.buffer.read(4)
    if not raw or len(raw) < 4:
        return None
    (n,) = struct.unpack("=I", raw)
    if n <= 0 or n > 64 * 1024 * 1024:
        return None
    data = sys.stdin.buffer.read(n)
    if len(data) < n:
        return None
    try:
        return json.loads(data.decode("utf-8"))
    except Exception:
        return None


def send_msg(obj):
    data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
    sys.stdout.buffer.write(struct.pack("=I", len(data)))
    sys.stdout.buffer.write(data)
    sys.stdout.buffer.flush()


def main():
    log_line("host 启动 argv=" + repr(sys.argv[1:]))
    while True:
        msg = read_msg()
        if msg is None:
            break
        cmd = msg.get("cmd")
        log_line("cmd=" + str(cmd) + " 来源(首参)=" + (sys.argv[1] if len(sys.argv) > 1 else "无"))
        if cmd == "status":
            r = cmd_status()
        elif cmd == "auth":
            r = cmd_auth()
        elif cmd == "send":
            r = cmd_send(msg)
        elif cmd == "ping":
            r = {"ok": True, "message": "pong", "cli": find_cli()}
        else:
            r = {"ok": False, "message": "未知命令: %s" % cmd}
        send_msg(r)


if __name__ == "__main__":
    main()