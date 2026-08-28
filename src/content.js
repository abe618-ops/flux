(() => {
  const JUNK_SELECTOR = [
    "script", "style", "noscript", "svg", "canvas", "video", "audio", "iframe",
    "object", "embed", "form", "button", "input", "select", "textarea",
    "nav", "footer", "header", "aside", "dialog",
    '[role="navigation"]', '[role="banner"]', '[role="complementary"]', '[role="contentinfo"]',
    ".nav", ".navbar", ".menu", ".ad", ".ads", ".advert", ".advertisement",
    ".banner-ad", ".adsbygoogle", ".ad-container",
    ".comment", ".comments", ".comment-list", ".share", ".social", ".sidebar",
    ".footer", ".header", ".breadcrumb", ".crumb", ".pagination", ".related", ".recommend",
    ".popup", ".modal", ".toolbar", ".action-bar", ".newsletter", ".subscribe",
    ".copyright", "[class*='copyright']",
  ].join(",");

  const MAX_IMAGES = 30;
  const POSITIVE_HINT = /article|content|post|entry|story|main|news|detail|body|text/i;
  const NEGATIVE_HINT = /comment|sidebar|related|recommend|footer|header|nav|menu|hot|rank|list|share|advert|banner|slide|popup|widget|notice/i;
  const PUNCT_RE = /[，。！？；：,.!?;:、]/g;

  function absolutize(url) {
    try {
      return new URL(url, location.href).href;
    } catch (e) {
      return url;
    }
  }

  function textLen(el) {
    return (el.innerText || "").replace(/\s+/g, " ").trim().length;
  }

  function scoreBlock(el) {
    const text = (el.innerText || "").replace(/\s+/g, " ").trim();
    const len = text.length;
    if (len < 50) return 0;
    let linkLen = 0;
    for (const a of el.querySelectorAll("a")) linkLen += (a.innerText || "").length;
    const density = linkLen / len;
    let s = Math.min(len, 6000);
    if (density > 0.6) s *= 0.25;
    else if (density > 0.35) s *= 0.6;
    else if (density > 0.2) s *= 0.9;
    s += (text.match(PUNCT_RE) || []).length * 6;
    const hint = (el.className || "") + " " + (el.id || "");
    if (POSITIVE_HINT.test(hint)) s *= 1.15;
    if (NEGATIVE_HINT.test(hint)) s *= 0.35;
    return s;
  }

  function findMainContainer(root) {
    const preferred = root.querySelector("article, main");
    if (preferred) return preferred;
    const els = Array.from(root.querySelectorAll("article, main, section, div, td"));
    let best = null;
    let bestScore = 0;
    for (const el of els) {
      if (el === root) continue;
      const s = scoreBlock(el);
      if (s > bestScore) {
        bestScore = s;
        best = el;
      }
    }
    if (!best || bestScore < 150) return root;
    for (let pass = 0; pass < 4; pass++) {
      let moved = false;
      for (const el of els) {
        if (el === best || !best.contains(el)) continue;
        const s = scoreBlock(el);
        if (s >= bestScore * 0.55 && el.querySelector("p, pre, blockquote")) {
          best = el;
          bestScore = s;
          moved = true;
        }
      }
      if (!moved) break;
    }
    return best;
  }

  function cleanNoiseBlocks(container) {
    for (const el of container.querySelectorAll("p, div, li, td")) {
      const total = textLen(el);
      if (!total || total > 80) continue;
      let linkLen = 0;
      for (const a of el.querySelectorAll("a")) linkLen += (a.innerText || "").length;
      if (linkLen / total > 0.7) el.remove();
    }
  }

  function scrubMarkupNoise(text) {
    return text
      .split("\n")
      .filter((l) => {
        const t = l.trim();
        return !/^(<!DOCTYPE|<html>|<\/html>|<head>|<\/head>|<body[ >]|<\/body>|<title>|<\/title>|<\/?style>|<meta\b|<link\b|<svg\b|<\/svg>)/i.test(t);
      })
      .join("\n");
  }

  function normalizeText(text) {
    return scrubMarkupNoise(
      text
        .replace(/[\u00a0\u200b\u3000]/g, " ")
        .replace(/[ \t]+\n/g, "\n")
        .replace(/\n[ \t]+/g, "\n")
        .replace(/\n{3,}/g, "\n\n")
    ).trim();
  }

  function collectImages() {
    const images = [];
    const seen = new Set();
    for (const img of document.querySelectorAll("img")) {
      const src = img.getAttribute("src") || "";
      if (!src || src.startsWith("data:") || img.closest(JUNK_SELECTOR)) continue;
      const rect = img.getBoundingClientRect();
      const w = rect.width || parseInt(img.getAttribute("width") || "0", 10) || img.naturalWidth || 0;
      const h = rect.height || parseInt(img.getAttribute("height") || "0", 10) || img.naturalHeight || 0;
      if (w < 100 || h < 80) continue;
      const abs = absolutize(src);
      if (seen.has(abs)) continue;
      seen.add(abs);
      images.push({ src: abs, alt: img.alt || "", width: w, height: h });
      if (images.length >= MAX_IMAGES) break;
    }
    return images;
  }

  function extractPageTitle() {
    const og = document.querySelector('meta[property="og:title"]');
    if (og && og.getAttribute("content") && og.getAttribute("content").trim()) {
      return og.getAttribute("content").trim();
    }
    const h1 = document.querySelector("article h1, main h1, h1");
    if (h1 && (h1.innerText || "").trim()) return h1.innerText.trim();
    return document.title || "";
  }

  function serializeMain(container) {
    const clone = container.cloneNode(true);
    clone.querySelectorAll(JUNK_SELECTOR).forEach((n) => n.remove());
    clone.querySelectorAll("img").forEach((img) => {
      const src = img.getAttribute("src") || img.getAttribute("data-src") || "";
      if (src) img.setAttribute("src", absolutize(src));
      if (!img.getAttribute("loading")) img.setAttribute("loading", "lazy");
    });
    const html = normalizeText(clone.outerHTML);
    const parsable = (clone.outerHTML.match(/<(p|pre|blockquote|table|h[1-6])\b/g) || []).length;
    if (parsable < 2) return "";
    return html;
  }

  function extractArticle() {
    const clone = document.body.cloneNode(true);
    clone.querySelectorAll(JUNK_SELECTOR).forEach((n) => n.remove());
    const container = findMainContainer(clone);
    cleanNoiseBlocks(container);
    let text = normalizeText(container.innerText || "");
    const hasBlocks = container.querySelector("p, pre, blockquote") !== null;
    if ((container === clone || !hasBlocks) && text.length < 60) {
      text = normalizeText(clone.innerText || "");
    }
    const html = container !== clone ? serializeMain(container) : "";
    const images = collectImages();
    return {
      title: (extractPageTitle() || "").slice(0, 200),
      url: location.href || "",
      text,
      images,
      detected: null,
      html,
    };
  }

  function detectType(article) {
    const textLen = article.text.length;
    if (article.images.length === 0) return textLen >= 100 ? "text" : "none";
    if (textLen < 200) return "image";
    const imgArea = article.images.reduce((sum, i) => sum + i.width * i.height, 0);
    return article.images.length >= 3 && imgArea > textLen * 40 ? "image" : "text";
  }

  function escapeHtml(s) {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  function linkifyUrls(text) {
    return escapeHtml(text).replace(
      /(https?:\/\/[^\s<]+)/g,
      '<a href="$1">$1</a>'
    );
  }

  function buildHtml(article) {
    const title = escapeHtml(article.title || "(无标题)");
    const paras = article.text
      .split(/\n{2,}/)
      .map((p) => `<p>${linkifyUrls(p).replace(/\n/g, "<br>")}</p>`)
      .join("\n");
    const figs = article.images
      .map(
        (im) =>
          `<figure><img src="${escapeHtml(im.src)}" alt="${escapeHtml(im.alt)}" loading="lazy"><figcaption>${escapeHtml(im.alt)}</figcaption></figure>`
      )
      .join("\n");
    const srcLink = `<p><a href="${escapeHtml(article.url)}">原文链接: ${escapeHtml(article.url)}</a></p>`;
    return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>${title}</title>
<style>
body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;line-height:1.75;max-width:720px;margin:0 auto;padding:24px 16px;color:#222}
img{max-width:100%;height:auto;border-radius:6px;display:block;margin:0 auto}
figure{margin:20px 0}
figcaption{color:#888;font-size:13px;text-align:center;margin-top:8px;word-break:break-all}
a{color:#2563eb;word-break:break-all}
h1{font-size:24px;line-height:1.4;margin-bottom:8px}
</style>
</head>
<body>
<h1>${title}</h1>
${paras}
${figs}
${srcLink}
</body>
</html>`;
  }

  chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    if (msg && msg.type === "get-selection") {
      sendResponse({ text: (window.getSelection() || {}).toString() || "" });
      return;
    }
    if (msg && msg.type === "extract-content") {
      const article = extractArticle();
      article.detected = detectType(article);
      article.html = buildHtml(article);
      sendResponse(article);
      return;
    }
    if (msg && msg.type === "show-send-panel") {
      showSendPanel(msg.payload);
      sendResponse(true);
      return;
    }
    if (msg && msg.type === "show-toast") {
      showToast(msg.text || "", msg.cls || "");
      sendResponse(true);
      return;
    }
  });

  /* ---------------- 快捷发送面板（浮动、不遮挡页面） ---------------- */

  let panelRoot = null;

  const PANEL_CSS = `
    #esq-panel {
      position: fixed;
      top: 16px;
      right: 16px;
      z-index: 2147483647;
      width: 320px;
      max-width: calc(100vw - 32px);
      background: #fff;
      color: #1f2937;
      border: 1px solid #e5e7eb;
      border-radius: 14px;
      box-shadow: 0 10px 36px rgba(0, 0, 0, 0.16);
      font: 13px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
        "Microsoft YaHei", sans-serif;
      box-sizing: border-box;
    }
    #esq-panel * { box-sizing: border-box; }
    #esq-panel .esq-head {
      display: flex; align-items: center; justify-content: space-between;
      padding: 11px 14px; border-bottom: 1px solid #f0f2f5; font-weight: 600;
    }
    #esq-panel .esq-close {
      border: none; background: none; font-size: 15px; color: #9ca3af;
      cursor: pointer; padding: 0 4px; line-height: 1;
    }
    #esq-panel .esq-close:hover { color: #1f2937; }
    #esq-panel .esq-body { padding: 12px 14px; }
    #esq-panel label { display: block; font-size: 12px; color: #6b7280; margin-bottom: 4px; }
    #esq-panel input[type="email"] {
      width: 100%; padding: 8px 10px; border: 1px solid #d1d5db; border-radius: 9px;
      font-size: 13px; outline: none;
    }
    #esq-panel input[type="email"]:focus { border-color: #2563eb; }
    #esq-panel .esq-row { display: flex; gap: 16px; margin: 10px 0; font-size: 13px; }
    #esq-panel .esq-row label {
      margin: 0; color: #1f2937; display: inline-flex; align-items: center; gap: 5px; cursor: pointer;
    }
    #esq-panel .esq-foot {
      display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 12px;
    }
    #esq-panel .esq-send {
      border: none; background: #2563eb; color: #fff; padding: 8px 18px;
      border-radius: 9px; font-size: 13px; font-weight: 600; cursor: pointer;
    }
    #esq-panel .esq-send:hover { background: #1d4ed8; }
    #esq-panel .esq-send:disabled { background: #93c5fd; cursor: default; }
    #esq-panel .esq-default {
      border: none; background: none; color: #6b7280; font-size: 12px; cursor: pointer; padding: 4px;
    }
    #esq-panel .esq-default:hover { color: #2563eb; }
    #esq-panel .esq-status { font-size: 12px; min-height: 18px; margin-top: 8px; word-break: break-all; }
    #esq-panel .esq-status.ok { color: #16a34a; }
    #esq-panel .esq-status.err { color: #dc2626; }
  `;

  function showSendPanel(payload) {
    removeSendPanel();
    const styleEl = document.createElement("style");
    styleEl.textContent = PANEL_CSS;
    (document.head || document.documentElement).appendChild(styleEl);

    const panel = document.createElement("div");
    panel.id = "esq-panel";
    panel.innerHTML = `
      <div class="esq-head">
        <span>发送到邮箱</span>
        <button class="esq-close" type="button" title="关闭">✕</button>
      </div>
      <div class="esq-body">
        <label>接收邮箱</label>
        <input type="email" class="esq-to" value="${escapeHtml(payload.to)}" list="esq-contacts" placeholder="收件人">
        <datalist id="esq-contacts">
          ${(payload.contacts || []).map((c) => `<option value="${escapeHtml(c)}">`).join("")}
        </datalist>
        <div class="esq-row">
          <label><input type="radio" name="esq-fmt" value="text"> 纯文字</label>
          <label><input type="radio" name="esq-fmt" value="html"> HTML 图文</label>
        </div>
        <div class="esq-foot">
          <button class="esq-default" type="button">存为默认</button>
          <button class="esq-send" type="button">发送</button>
        </div>
        <div class="esq-status"></div>
      </div>
    `;

    const fmtHtml = panel.querySelector('input[name="esq-fmt"][value="html"]');
    const fmtText = panel.querySelector('input[name="esq-fmt"][value="text"]');
    const hasHtml = !!(payload.html && payload.html.length);
    (hasHtml ? fmtHtml : fmtText).checked = true;
    if (!hasHtml) fmtHtml.disabled = true;

    panelRoot = panel;
    document.documentElement.appendChild(panel);

    const toInput = panel.querySelector(".esq-to");
    const statusEl = panel.querySelector(".esq-status");
    const sendBtn = panel.querySelector(".esq-send");

    panel.querySelector(".esq-close").addEventListener("click", removeSendPanel);

    panel.querySelector(".esq-default").addEventListener("click", () => {
      const to = toInput.value.trim();
      if (!to) return;
      chrome.storage.sync.set({ defaultTo: to });
      statusEl.textContent = "已设为默认邮箱";
      statusEl.className = "esq-status ok";
    });

    sendBtn.addEventListener("click", () => {
      const to = toInput.value.trim();
      if (!to || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(to)) {
        statusEl.textContent = "邮箱地址格式不正确";
        statusEl.className = "esq-status err";
        return;
      }
      const fmt = (fmtHtml.checked ? "html" : "text") || "text";
      sendBtn.disabled = true;
      statusEl.textContent = "发送中…";
      statusEl.className = "esq-status";
      chrome.runtime.sendMessage(
        {
          type: "agently-send",
          payload: {
            to,
            subject: payload.subject,
            text: fmt === "text" ? payload.text : "",
            html: fmt === "html" ? payload.html : "",
          },
        },
        (r) => {
          sendBtn.disabled = false;
          if (chrome.runtime.lastError) {
            statusEl.textContent = "发送失败：" + chrome.runtime.lastError.message;
            statusEl.className = "esq-status err";
            return;
          }
          if (r && r.ok) {
            statusEl.textContent = "发送成功 ✓";
            statusEl.className = "esq-status ok";
            setTimeout(removeSendPanel, 1600);
          } else {
            statusEl.textContent = "发送失败：" + (r ? r.message : "未知错误");
            statusEl.className = "esq-status err";
          }
        }
      );
    });
  }

  function removeSendPanel() {
    if (panelRoot) {
      panelRoot.remove();
      panelRoot = null;
    }
  }

  /* ---------------- 轻量 Toast 反馈（一键发送后显示） ---------------- */

  const TOAST_CSS = `
    #esq-toast {
      position: fixed; top: 16px; right: 16px; z-index: 2147483646;
      padding: 10px 18px; border-radius: 10px; font-size: 13px; font-weight: 500;
      font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif;
      color: #fff; pointer-events: none; opacity: 0; transform: translateY(-8px);
      transition: opacity .25s, transform .25s; box-shadow: 0 4px 14px rgba(0,0,0,.18);
    }
    #esq-toast.show { opacity: 1; transform: translateY(0); }
    #esq-toast.ok  { background: #16a34a; }
    #esq-toast.err { background: #dc2626; }
  `;

  function showToast(text, cls) {
    let styleEl = document.getElementById("esq-toast-style");
    if (!styleEl) {
      styleEl = document.createElement("style");
      styleEl.id = "esq-toast-style";
      styleEl.textContent = TOAST_CSS;
      (document.head || document.documentElement).appendChild(styleEl);
    }
    let toast = document.getElementById("esq-toast");
    if (!toast) {
      toast = document.createElement("div");
      toast.id = "esq-toast";
      document.documentElement.appendChild(toast);
    }
    toast.textContent = text;
    toast.className = cls === "ok" ? "ok" : "err";
    clearTimeout(showToast._timer);
    requestAnimationFrame(() => {
      toast.classList.add("show");
      showToast._timer = setTimeout(() => {
        toast.classList.remove("show");
      }, 2200);
    });
  }
})();