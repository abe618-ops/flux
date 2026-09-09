const MENU_SELECTION = "send-selection-to-email";
const MENU_PAGE = "push-page-content";

const SERVER_BASE = "http://127.0.0.1:39127";

const DEFAULT_SETTINGS = {
  contacts: [], // 常用接收邮箱
  defaultTo: "", // 默认接收邮箱（useDefault=false 时生效）
  useDefault: false, // true=发送时直接用授权邮箱作为收件人
  sendMode: "panel", // panel=弹出面板确认 | direct=直接静默发送
  includePageInfo: true,
  subjectPrefix: "",
  channel: "tencent", // tencent=腾讯 Agent 邮箱(含微信) | netease=网易 Agent 邮箱(ClawEmail)
};

const BODY_MAX_LEN = 50000;

function getSettings() {
  return chrome.storage.sync.get(DEFAULT_SETTINGS).then((data) =>
    Object.assign({}, DEFAULT_SETTINGS, data)
  );
}

function saveSettings(patch) {
  return chrome.storage.sync.set(patch);
}

function buildSubject(settings, piece) {
  const parts = [];
  if (settings.subjectPrefix) parts.push(settings.subjectPrefix);
  if (piece) parts.push(piece);
  return parts.join(" | ").slice(0, 200) || "网页分享";
}

function truncate(s, n) {
  s = String(s || "");
  return s.length > n ? s.slice(0, n) + "…" : s;
}

async function notify(title, message) {
  try {
    await chrome.notifications.create({
      type: "basic",
      iconUrl: "icons/icon48.png",
      title: truncate(title, 60),
      message: truncate(message, 320),
    });
  } catch (e) {}
}

function escapeHtml(s) {
  return String(s || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function linkifyUrls(text) {
  return escapeHtml(text).replace(/(https?:\/\/[^\s<]+)/g, '<a href="$1">$1</a>');
}

function selectionToHtml(text, title, url) {
  const paras = text
    .split(/\n{2,}/)
    .map((p) => `<p>${linkifyUrls(p).replace(/\n/g, "<br>")}</p>`)
    .join("\n");
  return `<div>${paras}<p><small>来自: ${escapeHtml(title || "")}${
    url ? ` · <a href="${escapeHtml(url)}">${escapeHtml(url)}</a>` : ""
  }</small></p></div>`;
}

function buildPlainBody(article, mode, settings) {
  const lines = [];
  if (settings.includePageInfo) {
    lines.push(article.title || "(无标题)");
    lines.push(article.url || "");
    lines.push("");
  }
  lines.push(article.text || "(未能提取页面正文)");
  const wantImages = mode === "image" || mode === "html";
  if (wantImages && article.images.length) {
    lines.push("");
    lines.push("—— 图片链接 ——");
    article.images.slice(0, 20).forEach((im) => lines.push(im.src));
  }
  return lines.join("\n");
}

async function tryMessage(tabId, msg, timeoutMs = 4000) {
  try {
    const p = chrome.tabs.sendMessage(tabId, msg);
    const timer = timeoutMs
      ? new Promise((_, rej) => setTimeout(() => rej(new Error("timeout")), timeoutMs))
      : null;
    return await Promise.race([p, timer].filter(Boolean));
  } catch (e) {
    return null;
  }
}

/* ---------------- Agently 通道（本机 loopback 服务 -> agently-cli） ---------------- */

function api(path, payload, timeoutMs = 260000) {
  return fetch(SERVER_BASE + path, {
    method: payload ? "POST" : "GET",
    headers: { "Content-Type": "application/json" },
    body: payload ? JSON.stringify(payload) : undefined,
    signal: AbortSignal.timeout(timeoutMs),
  })
    .then(async (resp) => {
      const j = await resp.json().catch(() => null);
      return j && typeof j === "object" ? j : { ok: false, message: "服务响应异常(" + resp.status + ")" };
    })
    .catch((e) => {
      const name = (e && e.name) || "";
      let msg = "本机发送服务未就绪: " + (e.message || e);
      if (name === "TimeoutError") {
        msg = "本机发送服务响应超时，请稍后重试";
      } else if (/failed|ERR_CONNECTION/i.test(e.message || "")) {
        msg = "无法连接本地发送服务，请双击扩展文件夹里的 setup.command 完成一键安装";
      }
      return { ok: false, message: msg };
    });
}

function hostStatus(channel, timeoutMs = 30000) {
  const q = channel ? "?channel=" + encodeURIComponent(channel) : "";
  return api("/status" + q, null, timeoutMs);
}

function hostAuth(channel) {
  const q = channel ? "?channel=" + encodeURIComponent(channel) : "";
  return api("/auth" + q, {}, 270000);
}

function hostSend(payload) {
  return api("/send", payload, 200000);
}

async function sendAndNotify(to, subject, text, html) {
  if (!text && (!html || !html.length)) {
    await notify("发送失败", "没有可发送的内容");
    return { ok: false, message: "没有可发送的内容" };
  }
  const settings = await getSettings();
  const r = await hostSend({ to, subject, text, html, channel: settings.channel });
  if (r && r.ok) {
    if (!settings.useDefault) await saveSettings({ defaultTo: to });
    await notify("已发送到 " + to, subject);
    return { ok: true, message: (r.message && "已发送：" + r.message) || "发送成功" };
  }
  const msg = truncate((r && r.message) || "发送失败（本机程序未就绪）", 300);
  await notify("发送失败", msg);
  return { ok: false, message: msg };
}

async function ensureDefault() {
  const settings = await getSettings();
  if (settings.defaultTo) return settings;
  const st = await hostStatus(settings.channel);
  if (st && st.ok && st.mailbox) {
    await saveSettings({ defaultTo: st.mailbox });
    settings.defaultTo = st.mailbox;
  }
  return settings;
}

async function resolveDefaultTo(settings) {
  let effective = (settings.defaultTo || "").trim();
  if (!effective && settings.useDefault) {
    const st = await hostStatus(settings.channel);
    if (st && st.ok && st.mailbox) effective = st.mailbox;
  }
  return effective;
}

async function deliverPayload(tabId, { to, subject, text, html }) {
  const settings = await getSettings();
  const effectiveTo = await resolveDefaultTo(settings);

  if (settings.sendMode === "panel" && tabId) {
    const ack = await tryMessage(tabId, {
      type: "show-send-panel",
      payload: { to: to || effectiveTo, subject, text, html, contacts: settings.contacts },
    });
    if (ack) return;
  }

  const finalTo = (to || effectiveTo || "").trim();
  if (!finalTo) {
    await notify("发送失败", "未设置接收邮箱，请在面板中填写或在选项页设置");
    await chrome.runtime.openOptionsPage();
    return;
  }
  await sendAndNotify(finalTo, subject, text, html);
}

/* ---------------- 推送入口 ---------------- */

async function pushSelectionToEmail(tab, selectionText) {
  const settings = await getSettings();
  let text = selectionText || "";
  let title = (tab && tab.title) || "";
  if (!text && tab) {
    const r = await tryMessage(tab.id, { type: "get-selection" });
    if (r && r.text) text = r.text;
  }
  const subject = buildSubject(settings, (text || title).slice(0, 60));
  const plain = text ? `${text}\n\n—— 来自: ${title}` : title || "(无内容)";
  await deliverPayload(tab.id, {
    to: "",
    subject,
    text: plain,
    html: text ? selectionToHtml(text, title, "") : "",
  });
}

async function pushPageContent(tab) {
  const settings = await getSettings();
  let article = await tryMessage(tab.id, { type: "extract-content" });
  if (!article) {
    article = {
      title: (tab && tab.title) || "",
      url: (tab && tab.url) || "",
      text: "",
      images: [],
      detected: "none",
      html: "",
    };
  }
  const mode = settings.shareMode === "auto" ? article.detected : settings.shareMode || article.detected;
  const subject = buildSubject(settings, article.title || "页面全文");
  const plain = buildPlainBody(article, mode, settings);
  const htmlMode = mode === "html" || mode === "image";
  await deliverPayload(tab.id, {
    to: "",
    subject,
    text: plain,
    html: htmlMode ? article.html || "" : "",
  });
}

/* ---------------- 菜单 / 快捷键 / 消息 ---------------- */

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.removeAll(() => {
    chrome.contextMenus.create({
      id: MENU_SELECTION,
      title: "发送选中文本到邮箱 %s",
      contexts: ["selection"],
    });
    chrome.contextMenus.create({
      id: MENU_PAGE,
      title: "推送页面全文到邮箱",
      contexts: ["page"],
    });
  });
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === MENU_SELECTION) {
    pushSelectionToEmail(tab, info.selectionText || "");
  } else if (info.menuItemId === MENU_PAGE) {
    pushPageContent(tab);
  }
});

chrome.commands.onCommand.addListener((command) => {
  chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
    const tab = tabs && tabs[0];
    if (!tab) return;
    if (command === "send-selection") pushSelectionToEmail(tab, "");
    else if (command === "push-page") pushPageContent(tab);
  });
});

chrome.action.onClicked.addListener(async (tab) => {
  const settings = await getSettings();
  const r = await tryMessage(tab.id, { type: "get-selection" });
  if (r && r.text) {
    await pushSelectionToEmail(tab, r.text);
    return;
  }
  // 一键提取+直发：强制 direct 模式，不弹面板
  let article = await tryMessage(tab.id, { type: "extract-content" });
  if (!article) {
    article = {
      title: (tab && tab.title) || "",
      url: (tab && tab.url) || "",
      text: "",
      images: [],
      detected: "none",
      html: "",
    };
  }
  const mode = article.detected === "html" || article.detected === "image" ? article.detected : "text";
  const subject = buildSubject(settings, article.title || "页面全文");
  const plain = buildPlainBody(article, mode, settings);
  const effectiveTo = await resolveDefaultTo(settings);
  if (!effectiveTo) {
    tryMessage(tab.id, { type: "show-toast", text: "未设置接收邮箱", cls: "err" });
    await notify("发送失败", "未设置接收邮箱，请在选项页设置");
    await chrome.runtime.openOptionsPage();
    return;
  }
  const r2 = await sendAndNotify(effectiveTo, subject, plain, mode === "html" ? (article.html || "") : "");
  tryMessage(tab.id, {
    type: "show-toast",
    text: r2 && r2.ok ? "✓ 已发送到 " + effectiveTo : "✗ " + (r2 ? r2.message : "发送失败"),
    cls: r2 && r2.ok ? "ok" : "err",
  });
});

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg && msg.type === "agently-send") {
    const { to, subject, text, html } = msg.payload || {};
    sendAndNotify(to, subject, text, html).then((r) => sendResponse(r));
    return true;
  }
  if (msg && msg.type === "esq-status") {
    getSettings().then((s) => hostStatus(s.channel)).then((r) => sendResponse(r));
    return true;
  }
  if (msg && msg.type === "esq-auth") {
    getSettings().then((s) =>
      hostAuth(s.channel).then(async (r) => {
        if (r && r.ok) {
          const settings = await getSettings();
          if (!settings.defaultTo) {
            const st = await hostStatus(s.channel);
            if (st && st.ok && st.mailbox) {
              await saveSettings({ defaultTo: st.mailbox });
              r.defaultTo = st.mailbox;
            }
          }
        }
        sendResponse(r);
      })
    );
    return true;
  }
  if (msg && msg.type === "esq-netease-setkey") {
    api("/netease/setkey", { apikey: msg.apikey }, 60000).then((r) => sendResponse(r));
    return true;
  }
  if (msg && msg.type === "esq-netease-login") {
    api("/netease/login", { email: msg.email }, 220000).then((r) => sendResponse(r));
    return true;
  }
});