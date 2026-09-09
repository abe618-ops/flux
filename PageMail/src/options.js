const DEFAULT_SETTINGS = {
  contacts: [],
  defaultTo: "",
  useDefault: false,
  sendMode: "panel",
  includePageInfo: true,
  subjectPrefix: "",
  channel: "tencent",
};

const els = {
  offline: document.getElementById("offline"),
  authBtn: document.getElementById("authBtn"),
  tencentStatus: document.getElementById("tencentStatus"),
  tencentBox: document.getElementById("tencentBox"),
  neteaseBox: document.getElementById("neteaseBox"),
  neteaseKey: document.getElementById("neteaseKey"),
  neteaseEmail: document.getElementById("neteaseEmail"),
  neteaseAuthBtn: document.getElementById("neteaseAuthBtn"),
  neteaseStatus: document.getElementById("neteaseStatus"),
  useDefault: document.getElementById("useDefault"),
  defaultTo: document.getElementById("defaultTo"),
  contacts: document.getElementById("contacts"),
  includePageInfo: document.getElementById("includePageInfo"),
  subjectPrefix: document.getElementById("subjectPrefix"),
  saveBtn: document.getElementById("saveBtn"),
  saveStatus: document.getElementById("saveStatus"),
  hostHint: document.getElementById("hostHint"),
};

let current = Object.assign({}, DEFAULT_SETTINGS);

function currentChannel() {
  return (document.querySelector('input[name="channel"]:checked') || {}).value || current.channel || "tencent";
}

function setStatus(el, text, cls) {
  el.textContent = text;
  el.className = "status" + (cls ? " " + cls : "");
}

function parseContacts(text) {
  const list = [];
  const seen = new Set();
  for (const part of text.split(/[\n,]/)) {
    const t = part.trim();
    if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(t) && !seen.has(t)) {
      seen.add(t);
      list.push(t);
    }
  }
  return list;
}

function showChannel(channel) {
  document.querySelectorAll(".card").forEach((c) =>
    c.classList.toggle("sel", c.dataset.ch === channel)
  );
  els.tencentBox.classList.toggle("hide", channel !== "tencent");
  els.neteaseBox.classList.toggle("hide", channel !== "netease");
}

function refreshStatus() {
  chrome.runtime.sendMessage({ type: "esq-status" }, (r) => {
    const reachable = r && (r.ok || r.message);
    els.offline.classList.toggle("show", !reachable);
    if (!reachable) return;
    if (r.ok) {
      const st = currentChannel() === "tencent" ? els.tencentStatus : els.neteaseStatus;
      setStatus(st, "已授权 ✓" + (r.mailbox ? "  发件邮箱: " + r.mailbox : ""), "ok");
      if (!current.defaultTo && r.mailbox) {
        els.defaultTo.value = r.mailbox;
        current.defaultTo = r.mailbox;
      }
    } else {
      const st = currentChannel() === "tencent" ? els.tencentStatus : els.neteaseStatus;
      setStatus(st, r.message || "未授权", "err");
    }
  });
}

/* ---- 腾讯：扫码授权 ---- */
els.authBtn.addEventListener("click", () => {
  els.authBtn.disabled = true;
  setStatus(els.tencentStatus, "正在打开扫码页…");
  chrome.runtime.sendMessage({ type: "esq-auth" }, (r) => {
    els.authBtn.disabled = false;
    if (r && r.ok) {
      setStatus(els.tencentStatus, "授权完成 ✓  " + (r.message || ""), "ok");
      if (r.defaultTo) els.defaultTo.value = r.defaultTo;
      refreshStatus();
    } else {
      setStatus(els.tencentStatus, (r && r.message) || "授权失败", "err");
    }
  });
});

/* ---- 网易：粘贴 Key + 邮箱授权 ---- */
els.neteaseAuthBtn.addEventListener("click", () => {
  const apikey = els.neteaseKey.value.trim();
  const email = els.neteaseEmail.value.trim();
  if (!apikey || !email) {
    setStatus(els.neteaseStatus, "请先填写 API Key 与邮箱", "err");
    return;
  }
  els.neteaseAuthBtn.disabled = true;
  setStatus(els.neteaseStatus, "正在保存 Key…");
  chrome.runtime.sendMessage({ type: "esq-netease-setkey", apikey }, (r1) => {
    if (!r1 || !r1.ok) {
      els.neteaseAuthBtn.disabled = false;
      setStatus(els.neteaseStatus, (r1 && r1.message) || "保存失败", "err");
      return;
    }
    setStatus(els.neteaseStatus, "Key 已保存，正在登录（将打开浏览器）…");
    chrome.runtime.sendMessage({ type: "esq-netease-login", email }, (r2) => {
      els.neteaseAuthBtn.disabled = false;
      if (r2 && r2.ok) {
        setStatus(els.neteaseStatus, "授权完成 ✓", "ok");
        refreshStatus();
      } else {
        setStatus(els.neteaseStatus, (r2 && r2.message) || "授权失败", "err");
      }
    });
  });
});

document.querySelectorAll('input[name="channel"]').forEach((r) =>
  r.addEventListener("change", () => {
    const ch = currentChannel();
    current.channel = ch;
    showChannel(ch);
    refreshStatus();
  })
);

function load() {
  chrome.storage.sync.get(DEFAULT_SETTINGS, (data) => {
    current = Object.assign({}, DEFAULT_SETTINGS, data);
    els.contacts.value = current.contacts.join("\n");
    els.defaultTo.value = current.defaultTo;
    els.useDefault.checked = !!current.useDefault;
    els.includePageInfo.checked = !!current.includePageInfo;
    els.subjectPrefix.value = current.subjectPrefix;
    let found = false;
    for (const input of document.querySelectorAll('input[name="channel"]')) {
      input.checked = input.value === current.channel;
      if (input.checked) found = true;
    }
    if (!found) current.channel = "tencent";
    let sm = false;
    for (const input of document.querySelectorAll('input[name="sendMode"]')) {
      input.checked = input.value === current.sendMode;
      if (input.checked) sm = true;
    }
    if (!sm) current.sendMode = "panel";
    showChannel(current.channel);
    refreshStatus();
    const manifest = chrome.runtime.getManifest();
    els.hostHint.textContent = "扩展 ID: " + chrome.runtime.id + " · v" + manifest.version + " · 本地服务 127.0.0.1:39127";
  });
}

els.contacts.addEventListener("input", () => { els.useDefault.checked = false; });

els.saveBtn.addEventListener("click", () => {
  const sendMode = (document.querySelector('input[name="sendMode"]:checked') || {}).value || "panel";
  const contacts = parseContacts(els.contacts.value);
  const patch = {
    contacts,
    defaultTo: els.useDefault.checked ? "" : els.defaultTo.value.trim(),
    useDefault: els.useDefault.checked,
    sendMode,
    includePageInfo: els.includePageInfo.checked,
    subjectPrefix: els.subjectPrefix.value.trim(),
    channel: currentChannel(),
  };
  chrome.storage.sync.set(patch, () => {
    current = Object.assign({}, current, patch);
    setStatus(els.saveStatus, "已保存 ✓ (" + contacts.length + " 个常用收件人)", "ok");
    setTimeout(() => setStatus(els.saveStatus, ""), 2600);
  });
});

load();
