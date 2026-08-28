#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Send2Mail 本地发送服务（loopback HTTP, 127.0.0.1:39127）
替代 Chrome 原生消息方案，任何 Chromium 内核浏览器（Edge/Chrome/夸克/通义等）
只需扩展与本服务通信，无扩展 ID 白名单、无需重启浏览器。
路由:
  GET  /ping    -> {"ok": true, "message": "pong", "cli": ...}
  GET  /status  -> {"ok": true, "mailbox": "x@y.z", ...}
  POST /auth    -> 触发 agently-cli 授权，自动打开扫码头
  POST /send    -> {"to","subject","text","html"} 两阶段确认发送
日志: ~/Library/Application Support/com.local.Send2Mail/server.log"""

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import urllib.parse
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "127.0.0.1"
PORT = 39127
APP_DIR = os.path.join(os.path.expanduser("~"), "Library/Application Support/com.local.Send2Mail")
LOG_FILE = os.path.join(APP_DIR, "server.log")

BUILTIN_PATHS = [
    "/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "/bin", "/usr/sbin", "/sbin",
]


def log_line(text):
    try:
        os.makedirs(APP_DIR, exist_ok=True)
        with open(LOG_FILE, "a") as f:
            f.write(time.strftime("%Y-%m-%d %H:%M:%S ") + str(text) + "\n")
    except Exception:
        pass


def enriched_env():
    env = dict(os.environ)
    path = env.get("PATH", "")
    for p in BUILTIN_PATHS + [os.path.expanduser("~/.npm-global/bin"),
                               os.path.expanduser("~/bin")]:
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
        p = subprocess.run([bin] + args, capture_output=True, text=True,
                           timeout=timeout, cwd=cwd, env=enriched_env())
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
        if isinstance(d, dict) and isinstance(d.get("summary"), str) and d["summary"]:
            return d["summary"]
        for key in ("message", "error"):
            v = j.get(key)
            if isinstance(v, str) and v:
                return v
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


def cmd_status_agently():
    bin = find_cli()
    if not bin:
        return {"ok": False, "mailbox": None, "cli": None,
                "message": "未检测到 agently-cli，请先安装: npm install -g @tencent-qqmail/agently-cli"}
    code, out = run_cli(["+me"])
    mb = mailbox_from(out)
    if mb:
        return {"ok": True, "mailbox": mb, "message": "已授权", "cli": bin}
    return {"ok": False, "mailbox": None, "cli": bin,
            "message": "CLI 已找到但未授权或状态获取失败: " + summary(out)}


def cmd_auth():
    bin = find_cli()
    if not bin:
        return {"ok": False, "message": "未检测到 agently-cli，请先安装"}
    opened = {"v": False}
    acc = {"v": ""}
    try:
        proc = subprocess.Popen([bin, "auth", "login"], stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True, env=enriched_env())
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


def find_mail_cli():
    candidates = [
        "/opt/homebrew/bin/mail-cli",
        "/usr/local/bin/mail-cli",
        os.path.expanduser("~/.npm-global/bin/mail-cli"),
        os.path.expanduser("~/bin/mail-cli"),
    ]
    for c in candidates:
        if os.path.isfile(c) and os.access(c, os.X_OK):
            return c
    sh = shutil.which("mail-cli")
    if sh and os.access(sh, os.X_OK):
        return sh
    return None


def run_cmd(cmd, timeout=120, cwd=None):
    try:
        p = subprocess.run(cmd, capture_output=True, text=True,
                           timeout=timeout, cwd=cwd, env=enriched_env())
        out = p.stdout or ""
        if p.stderr:
            out += "\n[stderr] " + p.stderr
        return p.returncode, out
    except subprocess.TimeoutExpired:
        return -1, "命令运行超时"
    except Exception as e:
        return -1, "运行失败: %s" % e


def netease_status():
    bin = find_mail_cli()
    if not bin:
        return {"ok": False, "mailbox": None, "cli": None,
                "message": "未检测到 mail-cli。请双击 setup.command 一键安装。"}
    code, out = run_cmd([bin, "auth", "test"])
    if code == 0 and ("valid" in out.lower() or "jwt" in out.lower()):
        mailbox = None
        code2, out2 = run_cmd([bin, "clawemail", "list"], timeout=30)
        if code2 == 0:
            m = re.search(r"([\w.+-]+@claw\.163\.com)\s+primary\s+active", out2)
            mailbox = m.group(1) if m else None
        return {"ok": True, "mailbox": mailbox, "cli": bin, "message": "已授权"}
    return {"ok": False, "mailbox": None, "cli": bin,
            "message": "ClawEmail 未授权或状态获取失败: " + summary(out)}


def netease_setkey(apikey):
    bin = find_mail_cli()
    if not bin:
        return {"ok": False, "message": "未检测到 mail-cli。请双击 setup.command 一键安装。"}
    if not (apikey or "").strip():
        return {"ok": False, "message": "API Key 不能为空"}
    code, out = run_cmd([bin, "auth", "apikey", "set", apikey.strip()])
    if code == 0:
        return {"ok": True, "message": "API Key 已保存"}
    return {"ok": False, "message": "保存失败：" + summary(out)}


def netease_login(email):
    bin = find_mail_cli()
    if not bin:
        return {"ok": False, "message": "未检测到 mail-cli。请双击 setup.command 一键安装。"}
    if not (email or "").strip():
        return {"ok": False, "message": "请填写你的 @claw.163.com 邮箱"}
    opened = {"v": False}
    acc = {"v": ""}

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

    try:
        proc = subprocess.Popen([bin, "auth", "login", "--user", email.strip()],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, env=enriched_env())
    except Exception as e:
        return {"ok": False, "message": "启动登录失败: %s" % e}
    t = threading.Thread(target=stream, daemon=True)
    t.start()
    t.join(timeout=200)
    rc = proc.poll()
    if rc == 0:
        return {"ok": True, "message": "授权完成"}
    url_msg = "授权链接已打开，请在浏览器中完成" if opened["v"] else ""
    return {"ok": False, "message": "授权未完成。%s（退出码 %s）" % (url_msg, rc)}


def netease_send(req):
    bin = find_mail_cli()
    if not bin:
        return {"ok": False, "message": "未检测到 mail-cli。安装: npm install -g @clawemail/mail-cli"}
    to = (req.get("to") or "").strip()
    subject = (req.get("subject") or "网页分享").strip() or "网页分享"
    text = req.get("text") or ""
    html = req.get("html") or ""
    if not to:
        return {"ok": False, "message": "未填写接收邮箱"}
    args = [bin, "compose", "send", "--to", to, "--subject", subject]
    cwd = None
    if html:
        try:
            cwd = tempfile.mkdtemp(prefix="clawsend-")
            with open(os.path.join(cwd, "body.html"), "w", encoding="utf-8") as f:
                f.write(html)
            args += ["--body-file", "body.html"]
        except Exception as e:
            return {"ok": False, "message": "写入 HTML 临时文件失败: %s" % e}
    else:
        args += ["--body", text]
    try:
        code, out = run_cmd(args, cwd=cwd)
        if code == 0:
            return {"ok": True, "message": summary(out) or "发送成功"}
        return {"ok": False, "message": "发送失败：" + summary(out)}
    finally:
        if cwd:
            shutil.rmtree(cwd, ignore_errors=True)


def cmd_status(channel):
    if channel == "netease":
        return netease_status()
    return cmd_status_agently()


def cmd_send_agently(req):
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
            shutil.rmtree(cwd, ignore_errors=True)


def cmd_send(req):
    channel = req.get("channel") or "tencent"
    if channel == "netease":
        return netease_send(req)
    return cmd_send_agently(req)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def _json(self, obj, code=200):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/ping":
            log_line("GET /ping")
            self._json({"ok": True, "message": "pong", "cli": find_cli(),
                        "mail_cli": find_mail_cli()})
        elif self.path.startswith("/status"):
            params = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            channel = (params.get("channel") or ["tencent"])[0]
            log_line("GET /status channel=" + channel)
            self._json(cmd_status(channel))
        else:
            self._json({"ok": False, "message": "未知路由"}, 404)

    def do_POST(self):
        try:
            n = int(self.headers.get("Content-Length", "0") or 0)
            req = json.loads(self.rfile.read(n).decode("utf-8") or "{}")
        except Exception:
            self._json({"ok": False, "message": "请求体不是合法 JSON"}, 400)
            return
        if self.path.startswith("/auth"):
            params = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            channel = (params.get("channel") or ["tencent"])[0]
            log_line("POST /auth channel=" + channel)
            if channel == "netease":
                self._json({"ok": False, "authorized": False,
                            "message": "网易授权请在扩展选项页粘贴 API Key 与邮箱后点「授权」"})
            else:
                self._json(cmd_auth())
        elif self.path == "/netease/setkey":
            log_line("POST /netease/setkey")
            self._json(netease_setkey(req.get("apikey") or ""))
        elif self.path == "/netease/login":
            log_line("POST /netease/login")
            self._json(netease_login(req.get("email") or ""))
        elif self.path == "/send":
            log_line("POST /send to=" + str(req.get("to")))
            try:
                self._json(cmd_send(req))
            except Exception as e:
                log_line("POST /send 异常: %r" % e)
                self._json({"ok": False, "message": "服务内部错误: %s" % e})
        else:
            self._json({"ok": False, "message": "未知路由"}, 404)


def main():
    try:
        server = ThreadingHTTPServer((HOST, PORT), Handler)
    except OSError as e:
        if e.errno == 48:
            log_line("端口 %s 已被占用，可能已在运行" % PORT)
            print("端口 %s 已被占用（服务可能已在运行）" % PORT)
            sys.exit(0)
        raise
    log_line("Send2Mail 服务已启动 %s:%s agently-cli=%s" % (HOST, PORT, find_cli()))
    print("Send2Mail 服务已启动 %s:%s" % (HOST, PORT))
    server.serve_forever()


if __name__ == "__main__":
    main()