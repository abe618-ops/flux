from pathlib import Path
import os, re

root = Path(os.environ["PROJECT_DIR"])
main = root / "app/src/main/java/com/abe/quickvideo/MainActivity.java"
s = main.read_text()

s = s.replace(
    "等待分享或链接…\\n保存目录：Download/快存视频",
    "等待分享或链接…\\n保存目录：Movies/快存视频（自动加入本地视频）"
)
s = s.replace(
    'req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "快存视频/" + fileName);',
    '''req.allowScanningByMediaScanner();
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "快存视频/" + fileName);'''
)
s = s.replace(
    'setStatus("已建立下载任务 #" + id + "\\n保存到 Download/快存视频", false);',
    'setStatus("已建立下载任务 #" + id + "\\n保存到 Movies/快存视频；完成后自动加入本地视频库", false);'
)

if 'DouyinResolver.Result fast = DouyinResolver.resolve(originalUrl);' not in s:
    key = 'private ResolveResult resolveHttp(String originalUrl) throws Exception {'
    pos = s.find(key)
    if pos < 0:
        raise RuntimeError('resolveHttp anchor missing')
    brace = s.find('{', pos)
    inject = r'''
        if ("抖音".equals(detectPlatform(originalUrl))) {
            setStatus("正在使用抖音免登录移动 Feed 解析…", false);
            DouyinResolver.Result fast = DouyinResolver.resolve(originalUrl);
            if (fast != null) {
                if (fast.pageUrl != null && !fast.pageUrl.isEmpty()) currentPageUrl = fast.pageUrl;
                if (fast.title != null && !fast.title.isEmpty()) currentTitle = fast.title;
                if (fast.mediaUrl != null && !fast.mediaUrl.isEmpty()) {
                    return new ResolveResult(fast.mediaUrl, fast.pageUrl, fast.title, "");
                }
            }
            return null;
        }
'''
    s = s[:brace + 1] + inject + s[brace + 1:]

s = s.replace(
    'runOnUiThread(() -> startWebProbe(url));',
    'runOnUiThread(() -> startWebProbe(currentPageUrl == null || currentPageUrl.isEmpty() ? url : currentPageUrl));'
)

old_header = 'req.addRequestHeader("User-Agent", UA);\n                if (referer != null && !referer.isEmpty()) req.addRequestHeader("Referer", referer);'
if old_header in s:
    s = s.replace(old_header, '''String mediaLower = mediaUrl.toLowerCase(Locale.ROOT);
                String refLower = referer == null ? "" : referer.toLowerCase(Locale.ROOT);
                boolean douyinMedia = "抖音".equals(detectPlatform(currentSourceText))
                        || refLower.contains("douyin") || refLower.contains("iesdouyin")
                        || mediaLower.contains("douyin") || mediaLower.contains("douyinvod")
                        || mediaLower.contains("amemv") || mediaLower.contains("snssdk")
                        || mediaLower.contains("bytecdn") || mediaLower.contains("zjcdn.com")
                        || mediaLower.contains("bytedance") || mediaLower.contains("ibytedtos");
                req.addRequestHeader("User-Agent", douyinMedia ? DouyinResolver.APP_UA : UA);
                if (douyinMedia) {
                    req.addRequestHeader("Referer", "https://www.douyin.com/");
                } else if (referer != null && !referer.isEmpty()) {
                    req.addRequestHeader("Referer", referer);
                }''')

if 'private void startDouyinProbe(String url)' not in s:
    probe_key = 'private void startWebProbe(String url) {'
    pp = s.find(probe_key)
    if pp < 0:
        raise RuntimeError('startWebProbe anchor missing')
    pb = s.find('{', pp)
    s = s[:pb + 1] + r'''
        if ("抖音".equals(detectPlatform(url))) {
            startDouyinProbe(url);
            return;
        }
''' + s[pb + 1:]

    methods = r'''

    private void startDouyinProbe(String url) {
        if (isFinishing()) return;
        if (!resolved.compareAndSet(false, true)) return;
        setStatus("正在解析抖音极速版跳转并读取移动 Feed…", false);

        if (probeWebView != null) {
            root.removeView(probeWebView);
            probeWebView.destroy();
            probeWebView = null;
        }

        final java.util.concurrent.atomic.AtomicBoolean idHandled = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicBoolean downloadStarted = new java.util.concurrent.atomic.AtomicBoolean(false);

        probeWebView = new WebView(this);
        probeWebView.setAlpha(0.01f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(2), dp(2));
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        root.addView(probeWebView, lp);
        probeWebView.getSettings().setJavaScriptEnabled(true);
        probeWebView.getSettings().setDomStorageEnabled(true);
        probeWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        probeWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        probeWebView.getSettings().setUserAgentString(DouyinResolver.SHARE_UA);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) cm.setAcceptThirdPartyCookies(probeWebView, true);

        probeWebView.addJavascriptInterface(new DouyinProbeBridge(downloadStarted), "QuickDouyinBridge");
        probeWebView.setWebViewClient(new WebViewClient() {
            private void inspectId(String candidate) {
                if (candidate == null || candidate.isEmpty()) return;
                String id = DouyinResolver.extractAwemeId(candidate);
                if (id == null || id.isEmpty()) return;
                if (!idHandled.compareAndSet(false, true)) return;
                currentPageUrl = "https://www.douyin.com/video/" + id;
                currentTitle = "抖音_" + id;
                setStatus("已识别作品 " + id + "，正在读取匿名移动 Feed…", false);
                executor.execute(() -> {
                    DouyinResolver.Result result = DouyinResolver.resolveById(id, currentPageUrl);
                    if (result != null && result.mediaUrl != null && !result.mediaUrl.isEmpty()
                            && downloadStarted.compareAndSet(false, true)) {
                        currentPageUrl = result.pageUrl;
                        currentTitle = result.title;
                        enqueueDownload(result.mediaUrl, result.pageUrl, result.title, "");
                    } else if (!downloadStarted.get()) {
                        setStatus("移动 Feed 未命中，继续监听浏览器播放器媒体请求…", false);
                    }
                });
            }

            private void inspectMedia(String candidate) {
                if (candidate == null || candidate.isEmpty() || downloadStarted.get()) return;
                String low = candidate.toLowerCase(Locale.ROOT);
                boolean media = !low.startsWith("blob:") && !low.contains(".m3u8") &&
                        (low.contains("douyinvod") || low.contains("bytecdn") ||
                         low.contains("/aweme/v1/play/") || low.contains("mime_type=video") ||
                         low.contains(".mp4"));
                if (!media || !candidate.startsWith("http")) return;
                if (!downloadStarted.compareAndSet(false, true)) return;
                String cookie = CookieManager.getInstance().getCookie(candidate);
                enqueueDownload(candidate.replace("&amp;", "&"), currentPageUrl, currentTitle,
                        cookie == null ? "" : cookie);
            }

            @Override
            public void onPageStarted(WebView view, String pageUrl, android.graphics.Bitmap favicon) {
                currentPageUrl = pageUrl;
                inspectId(pageUrl);
                super.onPageStarted(view, pageUrl, favicon);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null) inspectId(request.getUrl().toString());
                return false;
            }

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null) {
                    String u = request.getUrl().toString();
                    inspectId(u);
                    inspectMedia(u);
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                currentPageUrl = pageUrl;
                inspectId(pageUrl);
                if (!downloadStarted.get()) {
                    String js = "(function(){try{" +
                            "var h=location.href||'';var s=(document.documentElement&&document.documentElement.innerHTML)||'';" +
                            "var m=(h+' '+s).match(/(?:\\/video\\/|\\/note\\/)([0-9]{10,})/);" +
                            "if(m)QuickDouyinBridge.foundId(m[1]);" +
                            "var v=document.querySelector('video');var u=v&&(v.currentSrc||v.src);" +
                            "if(u&&u.indexOf('http')===0)QuickDouyinBridge.foundMedia(u);" +
                            "}catch(e){}})();";
                    try { view.evaluateJavascript(js, null); } catch (Exception ignored) {}
                }
                super.onPageFinished(view, pageUrl);
            }
        });

        probeWebView.loadUrl(url);
        mainHandler.postDelayed(() -> {
            if (!resolved.get() || downloadStarted.get() || isFinishing()) return;
            setStatus("移动 Feed、分享页和浏览器播放器均未返回可下载地址。请重新分享该作品后再试。", true);
            resolved.set(false);
        }, 18000);
    }

    private class DouyinProbeBridge {
        private final java.util.concurrent.atomic.AtomicBoolean downloadStarted;

        DouyinProbeBridge(java.util.concurrent.atomic.AtomicBoolean downloadStarted) {
            this.downloadStarted = downloadStarted;
        }

        @JavascriptInterface
        public void foundId(String id) {
            if (id == null || id.isEmpty() || downloadStarted.get()) return;
            executor.execute(() -> {
                DouyinResolver.Result result = DouyinResolver.resolveById(id, currentPageUrl);
                if (result != null && result.mediaUrl != null && !result.mediaUrl.isEmpty()
                        && downloadStarted.compareAndSet(false, true)) {
                    currentPageUrl = result.pageUrl;
                    currentTitle = result.title;
                    enqueueDownload(result.mediaUrl, result.pageUrl, result.title, "");
                }
            });
        }

        @JavascriptInterface
        public void foundMedia(String mediaUrl) {
            if (mediaUrl == null || !mediaUrl.startsWith("http")) return;
            if (!downloadStarted.compareAndSet(false, true)) return;
            String cookie = CookieManager.getInstance().getCookie(mediaUrl);
            enqueueDownload(mediaUrl.replace("&amp;", "&"), currentPageUrl, currentTitle,
                    cookie == null ? "" : cookie);
        }
    }
'''
    insert = s.find('    private String extractFirstUrl(String text)')
    if insert < 0:
        raise RuntimeError('extractFirstUrl anchor missing')
    s = s[:insert] + methods + '\n' + s[insert:]

# v0.3.2: persist the exact public Movies path for every DownloadManager id.
# On Android 10-16 the completion broadcast often exposes content://downloads/...,
# so relying on COLUMN_LOCAL_URI being file:// skips MediaStore indexing.
enqueue_anchor = '''                long id = dm.enqueue(req);
                getPreferences(MODE_PRIVATE).edit().putString("last_source", currentSourceText).apply();'''
enqueue_replacement = '''                long id = dm.enqueue(req);

                java.io.File moviesRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                java.io.File targetDir = new java.io.File(moviesRoot, "快存视频");
                java.io.File targetFile = new java.io.File(targetDir, fileName);
                getSharedPreferences("download_index", MODE_PRIVATE).edit()
                        .putString("path_" + id, targetFile.getAbsolutePath())
                        .putString("name_" + id, fileName)
                        .putString("mime_" + id, "video/mp4")
                        .apply();

                getPreferences(MODE_PRIVATE).edit().putString("last_source", currentSourceText).apply();'''
if enqueue_anchor not in s:
    raise RuntimeError("DownloadManager enqueue anchor missing")
s = s.replace(enqueue_anchor, enqueue_replacement)

main.write_text(s)

resolver_src = Path(os.environ["GITHUB_WORKSPACE"]) / "quick-video-downloader/patches/DouyinResolver.java"
resolver_dst = root / "app/src/main/java/com/abe/quickvideo/DouyinResolver.java"
resolver_dst.write_text(resolver_src.read_text())

receiver = root / "app/src/main/java/com/abe/quickvideo/DownloadCompleteReceiver.java"
receiver.write_text(r'''package com.abe.quickvideo;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;

public class DownloadCompleteReceiver extends BroadcastReceiver {
    private static final String PREFS = "download_index";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        final long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        if (id < 0) return;

        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;

        boolean success = false;
        String localUri = "";
        try (Cursor cursor = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst()) return;
            int si = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (si >= 0) success = cursor.getInt(si) == DownloadManager.STATUS_SUCCESSFUL;
            int ui = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            if (ui >= 0) localUri = cursor.getString(ui);
        } catch (Exception ignored) {
            return;
        }
        if (!success) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String path = prefs.getString("path_" + id, "");
        String name = prefs.getString("name_" + id, "");
        String mime = prefs.getString("mime_" + id, "video/mp4");

        File target = path == null || path.isEmpty() ? null : new File(path);

        if ((target == null || !target.exists()) && localUri != null && !localUri.isEmpty()) {
            try {
                Uri uri = Uri.parse(localUri);
                if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
                    target = new File(uri.getPath());
                }
            } catch (Exception ignored) {}
        }

        if ((target == null || !target.exists()) && name != null && !name.isEmpty()) {
            File movies = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MOVIES);
            target = new File(new File(movies, "快存视频"), name);
        }

        if (target == null) return;
        final File scanTarget = target;

        try {
            File parent = scanTarget.getParentFile();
            if (parent != null) {
                File noMedia = new File(parent, ".nomedia");
                if (noMedia.exists()) noMedia.delete();
            }
        } catch (Exception ignored) {}

        final PendingResult pending = goAsync();
        try {
            MediaScannerConnection.scanFile(
                    context.getApplicationContext(),
                    new String[]{scanTarget.getAbsolutePath()},
                    new String[]{mime == null || mime.isEmpty() ? "video/mp4" : mime},
                    (scannedPath, scannedUri) -> {
                        try {
                            if (scannedUri != null) {
                                context.getContentResolver().notifyChange(scannedUri, null);
                            }
                            context.getContentResolver().notifyChange(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null);
                        } catch (Exception ignored) {
                        } finally {
                            prefs.edit()
                                    .remove("path_" + id)
                                    .remove("name_" + id)
                                    .remove("mime_" + id)
                                    .apply();
                            pending.finish();
                        }
                    });
        } catch (Exception e) {
            try {
                context.getContentResolver().notifyChange(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null);
            } catch (Exception ignored) {}
            pending.finish();
        }
    }
}
''')

manifest = root / "app/src/main/AndroidManifest.xml"
m = manifest.read_text()
if ".DownloadCompleteReceiver" not in m:
    receiver_xml = '''
        <receiver
            android:name=".DownloadCompleteReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.DOWNLOAD_COMPLETE" />
            </intent-filter>
        </receiver>
'''
    m = m.replace("    </application>", receiver_xml + "    </application>")
manifest.write_text(m)

gradle = root / "app/build.gradle"
g = gradle.read_text()
g = re.sub(r"applicationId '[^']+'", "applicationId 'com.abe.quickvideo.v032'", g)
g = re.sub(r"versionCode\s+\d+", "versionCode 10", g)
g = re.sub(r"versionName '[^']+'", "versionName '0.3.2'", g)
gradle.write_text(g)

readme = root / "README.md"
r = readme.read_text() if readme.exists() else "# 快存视频\n"
r += """

## v0.3.2 Android 相册/MediaStore 入库修复
- 保留 v0.3.1 的抖音 H.264/AVC 优先下载逻辑。\n- 建立 DownloadManager 任务时按下载 ID 保存 Movies/快存视频 的真实绝对路径。
- 无需登录、无需用户 Cookie、无需 a_bogus。
- 优先 play_addr_h264 / H.264(AVC)；只有不存在 H.264 时才回退 HEVC/H.265。\n- 对实际样本已验证：H.264 文件 fourcc 为 avc1 + mp4a，可直接用于 Android 本地播放。
- 原生短链无法取得作品号时，WebView 从真实跳转链识别 aweme_id，再回灌移动 Feed。
- 同时监听浏览器实际 MP4/douyinvod/play 请求作为末级兜底。
- DownloadManager 完成后不再要求 localUri 必须是 file://；即使返回 content://downloads/... 也能按真实路径扫描。\n- 扫描完成后主动 notifyChange(MediaStore.Video)，提高 Android 16 与 realme/OPPO 相册刷新可靠性。\n- 若目标目录存在 .nomedia 会主动删除，避免图库隐藏该目录。
"""
readme.write_text(r)
