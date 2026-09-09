const DEFAULT_SETTINGS = {
  targetEmail: "",
  evernoteEmail: "",
  includePageInfo: true,
};

const toastEl = document.getElementById("toast");
let toastTimer = null;

function showToast(text) {
  toastEl.textContent = text;
  toastEl.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.remove("show"), 2200);
}

function baseMailto(to, subject, body) {
  const params = new URLSearchParams();
  params.set("subject", subject);
  params.set("body", body);
  return `mailto:${encodeURIComponent(to)}?${params.toString()}`;
}

chrome.storage.session.get(["previewArticle", "previewMode"], (data) => {
  const article = data.previewArticle;
  if (!article) {
    document.getElementById("emptyState").hidden = false;
    document.getElementById("previewFrame").hidden = true;
    return;
  }

  document.getElementById("pageTitle").textContent =
    article.title || "(无标题)";
  const link = document.getElementById("originalLink");
  link.href = article.url || "#";

  const isImageMode = (data.previewMode || "image") === "image";
  let s = article.html;
  if (!isImageMode && article.images.length) {
    s = article.html;
  }
  document.getElementById("previewFrame").srcdoc = s;

  chrome.storage.sync.get(DEFAULT_SETTINGS, (settings) => {
    const plainBody = buildPlainBody(article, settings);
    const subject = article.title ? `${article.title}` : "页面全文";

    const mailBtn = document.getElementById("mailTextBtn");
    const evernoteBtn = document.getElementById("evernoteTextBtn");
    if (settings.targetEmail) {
      mailBtn.hidden = false;
      mailBtn.addEventListener("click", () => {
        window.location.href = baseMailto(settings.targetEmail, subject, plainBody);
      });
    } else {
      mailBtn.hidden = true;
    }
    if (settings.evernoteEmail) {
      evernoteBtn.hidden = false;
      evernoteBtn.addEventListener("click", () => {
        window.location.href = baseMailto(settings.evernoteEmail, subject, plainBody);
      });
    } else {
      evernoteBtn.hidden = true;
    }
  });

  document.getElementById("copyHtmlBtn").addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(article.html || "");
      showToast("HTML 源码已复制");
    } catch (e) {
      showToast("复制失败：" + e.message);
    }
  });
});

document.getElementById("backBtn").addEventListener("click", () => {
  window.close();
});

function buildPlainBody(article, settings) {
  const lines = [];
  if (settings.includePageInfo) {
    lines.push(article.title || "(无标题)");
    lines.push(article.url || "");
    lines.push("");
  }
  lines.push(
    article.text ||
      "(未能提取页面正文，请到原页面使用“发送选中文本”)"
  );
  if (article.images && article.images.length) {
    lines.push("");
    lines.push("—— 图片链接 ——");
    article.images.slice(0, 20).forEach((im) => lines.push(im.src));
  }
  return lines.join("\n");
}