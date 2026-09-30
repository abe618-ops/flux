package com.abe.quickvideo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Anonymous resolver for ordinary public Douyin videos.
 * Primary path: short link -> aweme id -> mobile Feed API -> media URL.
 * Fallback: iesdouyin share-page SSR -> embedded media/play URIs.
 */
final class DouyinResolver {
    static final String APP_UA =
            "com.ss.android.ugc.aweme/290101 (Linux; U; Android 10; zh_CN; Pixel 4; " +
            "Build/QQ3A.200805.001; Cronet/TTNetVersion:5f9037be 2023-01-13 QuicVersion:4668bb42 2022-11-21)";
    static final String SHARE_UA =
            "Mozilla/5.0 (Linux; Android 10; Pixel 4 Build/QQ3A.200805.001) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36";
    private static final String[] FEED_ENDPOINTS = new String[]{
            "https://api5-normal-c-hl.amemv.com/aweme/v1/feed/",
            "https://aweme.snssdk.com/aweme/v1/feed/"
    };
    private static final Pattern[] ID_PATTERNS = new Pattern[]{
            Pattern.compile("/(?:video|note|share/video)/(\\d{10,})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:aweme_id|modal_id|item_id)=([0-9]{10,})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[\\\"']aweme_id[\\\"']\\s*:\\s*[\\\"']?([0-9]{10,})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[\\\"']awemeId[\\\"']\\s*:\\s*[\\\"']?([0-9]{10,})", Pattern.CASE_INSENSITIVE)
    };
    private static final Pattern HTTP_PATTERN = Pattern.compile("https?://[^\\\"'<>\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern URI_PATTERN = Pattern.compile("[\\\"']uri[\\\"']\\s*:\\s*[\\\"']([^\\\"']{12,})[\\\"']", Pattern.CASE_INSENSITIVE);

    static final class Result {
        final String mediaUrl;
        final String pageUrl;
        final String title;
        final String awemeId;

        Result(String mediaUrl, String pageUrl, String title, String awemeId) {
            this.mediaUrl = mediaUrl == null ? "" : mediaUrl;
            this.pageUrl = pageUrl == null ? "" : pageUrl;
            this.title = title == null || title.isEmpty() ? "抖音视频" : title;
            this.awemeId = awemeId == null ? "" : awemeId;
        }
    }

    static Result resolve(String originalUrl) {
        String pageUrl = originalUrl;
        String body = "";
        String awemeId = "";
        try {
            Page landing = fetchPage(originalUrl, SHARE_UA, "", 10_000, 12_000);
            if (landing != null) {
                pageUrl = landing.url;
                body = landing.body;
                awemeId = extractId(pageUrl + "\n" + body);
            }
        } catch (Exception ignored) {
        }

        if (awemeId.isEmpty()) awemeId = extractId(originalUrl);
        if (awemeId.isEmpty()) return new Result("", pageUrl, "抖音视频", "");

        for (String endpoint : FEED_ENDPOINTS) {
            try {
                String api = endpoint + "?aweme_id=" + awemeId + "&aid=1128";
                Page p = fetchPage(api, APP_UA, "", 7_000, 8_000);
                if (p == null || p.body.isEmpty()) continue;
                JSONObject root = new JSONObject(p.body);
                JSONObject detail = findAwemeDetail(root, awemeId);
                if (detail == null) continue;
                String title = detail.optString("desc", "抖音视频");
                String media = chooseVerified(detail.optJSONObject("video"), pageUrl);
                if (!media.isEmpty()) return new Result(media, pageUrl, title, awemeId);
            } catch (Exception ignored) {
            }
        }

        String shareUrl = "https://www.iesdouyin.com/share/video/" + awemeId;
        try {
            Page share = fetchPage(shareUrl, SHARE_UA, "https://www.douyin.com/?is_from_mobile_home=1&recommend=1", 8_000, 10_000);
            if (share != null && !share.body.isEmpty()) {
                pageUrl = share.url;
                String title = extractTitle(share.body);
                String media = chooseFromShareHtml(share.body, share.url);
                if (!media.isEmpty()) return new Result(media, share.url, title, awemeId);
            }
        } catch (Exception ignored) {
        }

        String[] aids = new String[]{"6383", "1128"};
        for (String aid : aids) {
            try {
                String api = "https://www.douyin.com/aweme/v1/web/aweme/detail/?device_platform=webapp" +
                        "&aid=" + aid + "&channel=channel_pc_web&pc_client_type=1&version_code=190500" +
                        "&version_name=19.5.0&aweme_id=" + awemeId;
                Page p = fetchPage(api, SHARE_UA, "https://www.douyin.com/video/" + awemeId, 7_000, 8_000);
                if (p == null || p.body.isEmpty()) continue;
                JSONObject root = new JSONObject(p.body);
                JSONObject detail = root.optJSONObject("aweme_detail");
                if (detail == null) continue;
                String media = chooseVerified(detail.optJSONObject("video"), pageUrl);
                if (!media.isEmpty()) return new Result(media, pageUrl, detail.optString("desc", "抖音视频"), awemeId);
            } catch (Exception ignored) {
            }
        }

        return new Result("", shareUrl, "抖音视频", awemeId);
    }

    static String extractAwemeId(String text) {
        return extractId(text);
    }

    static Result resolveById(String awemeId, String pageUrl) {
        if (awemeId == null || awemeId.isEmpty()) return new Result("", pageUrl, "抖音视频", "");

        for (String endpoint : FEED_ENDPOINTS) {
            try {
                String api = endpoint + "?aweme_id=" + awemeId + "&aid=1128";
                Page p = fetchPage(api, APP_UA, "", 7_000, 8_000);
                if (p == null || p.body.isEmpty()) continue;
                JSONObject root = new JSONObject(p.body);
                JSONObject detail = findAwemeDetail(root, awemeId);
                if (detail == null) continue;
                String title = detail.optString("desc", "抖音视频");
                String media = chooseVerified(detail.optJSONObject("video"), pageUrl);
                if (!media.isEmpty()) return new Result(media, pageUrl, title, awemeId);
            } catch (Exception ignored) {
            }
        }

        String shareUrl = "https://www.iesdouyin.com/share/video/" + awemeId;
        try {
            Page share = fetchPage(shareUrl, SHARE_UA,
                    "https://www.douyin.com/?is_from_mobile_home=1&recommend=1", 8_000, 10_000);
            if (share != null && !share.body.isEmpty()) {
                String title = extractTitle(share.body);
                String media = chooseFromShareHtml(share.body, share.url);
                if (!media.isEmpty()) return new Result(media, share.url, title, awemeId);
            }
        } catch (Exception ignored) {
        }

        for (String aid : new String[]{"6383", "1128"}) {
            try {
                String api = "https://www.douyin.com/aweme/v1/web/aweme/detail/?device_platform=webapp"
                        + "&aid=" + aid + "&channel=channel_pc_web&pc_client_type=1&version_code=190500"
                        + "&version_name=19.5.0&aweme_id=" + awemeId;
                Page p = fetchPage(api, SHARE_UA,
                        "https://www.douyin.com/video/" + awemeId, 7_000, 8_000);
                if (p == null || p.body.isEmpty()) continue;
                JSONObject root = new JSONObject(p.body);
                JSONObject detail = root.optJSONObject("aweme_detail");
                if (detail == null) continue;
                String media = chooseVerified(detail.optJSONObject("video"), pageUrl);
                if (!media.isEmpty()) return new Result(media, pageUrl,
                        detail.optString("desc", "抖音视频"), awemeId);
            } catch (Exception ignored) {
            }
        }

        return new Result("", shareUrl, "抖音视频", awemeId);
    }

    private static JSONObject findAwemeDetail(JSONObject root, String awemeId) {
        JSONObject direct = root.optJSONObject("aweme_detail");
        if (direct != null) return direct;
        JSONArray list = root.optJSONArray("aweme_list");
        if (list == null) return null;
        JSONObject first = null;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            if (first == null) first = item;
            String id = item.optString("aweme_id", item.optString("id", ""));
            if (awemeId.equals(id)) return item;
        }
        return list.length() == 1 ? first : null;
    }

    private static String chooseVerified(JSONObject video, String referer) {
        if (video == null) return "";

        // 1. Compatibility first: Douyin often exposes an explicit AVC/H.264 address
        // even when every adaptive bit_rate entry is HEVC/H.265.
        for (String u : urlsFromAddress(video.optJSONObject("play_addr_h264"), "1080p")) {
            String ok = validateAndResolve(u, referer);
            if (!ok.isEmpty()) return ok;
        }

        // 2. Use only adaptive H.264 entries here. Never let an HEVC adaptive stream
        // pre-empt the explicit H.264 fallback.
        List<StreamCandidate> h264Ranked = new ArrayList<>();
        List<StreamCandidate> h265Ranked = new ArrayList<>();
        JSONArray bitRates = video.optJSONArray("bit_rate");
        if (bitRates != null) {
            for (int i = 0; i < bitRates.length(); i++) {
                JSONObject item = bitRates.optJSONObject(i);
                if (item == null) continue;
                JSONObject addr = item.optJSONObject("play_addr");
                long bitrate = item.optLong("bit_rate", 0L);
                int h265 = item.optInt("is_h265", 0);
                int width = addr == null ? item.optInt("width", 0) : addr.optInt("width", item.optInt("width", 0));
                int height = addr == null ? item.optInt("height", 0) : addr.optInt("height", item.optInt("height", 0));
                for (String u : urlsFromAddress(addr, "1080p")) {
                    StreamCandidate sc = new StreamCandidate(u, h265, (long) width * height, bitrate);
                    if (h265 == 0) h264Ranked.add(sc);
                    else h265Ranked.add(sc);
                }
            }
        }

        Comparator<StreamCandidate> quality = Comparator
                .comparingLong((StreamCandidate x) -> -x.pixels)
                .thenComparingLong(x -> -x.bitrate);
        Collections.sort(h264Ranked, quality);
        for (StreamCandidate sc : h264Ranked) {
            String ok = validateAndResolve(sc.url, referer);
            if (!ok.isEmpty()) return ok;
        }

        // 3. On current Douyin feeds these generic/original addresses are normally AVC.
        // Prefer them before any confirmed HEVC stream.
        for (String key : new String[]{"play_addr", "download_addr"}) {
            JSONObject addr = video.optJSONObject(key);
            for (String u : urlsFromAddress(addr, key.equals("download_addr") ? "default" : "1080p")) {
                String ok = validateAndResolve(u, referer);
                if (!ok.isEmpty()) return ok;
            }
        }

        // 4. HEVC is a last-resort fallback only. Some Android gallery/local-video
        // players still cannot decode hvc1 reliably.
        Collections.sort(h265Ranked, quality);
        for (StreamCandidate sc : h265Ranked) {
            String ok = validateAndResolve(sc.url, referer);
            if (!ok.isEmpty()) return ok;
        }
        for (String u : urlsFromAddress(video.optJSONObject("play_addr_265"), "1080p")) {
            String ok = validateAndResolve(u, referer);
            if (!ok.isEmpty()) return ok;
        }
        return "";
    }

    private static List<String> urlsFromAddress(JSONObject addr, String ratio) {
        List<String> out = new ArrayList<>();
        if (addr == null) return out;
        JSONArray urls = addr.optJSONArray("url_list");
        if (urls == null) urls = addr.optJSONArray("download_url_list");
        if (urls != null) {
            for (int i = 0; i < urls.length(); i++) {
                String u = normalizeUrl(urls.optString(i, ""));
                if (!u.isEmpty()) out.add(rewritePlayUrl(u));
            }
        }
        String uri = addr.optString("uri", "").trim();
        if (!uri.isEmpty() && !uri.toLowerCase(Locale.ROOT).contains("mp3")) {
            if (uri.startsWith("http")) out.add(rewritePlayUrl(uri));
            else {
                out.add(buildPlayEndpoint(uri, ratio));
                if (!"720p".equals(ratio)) out.add(buildPlayEndpoint(uri, "720p"));
            }
        }
        return dedupe(out);
    }

    private static String chooseFromShareHtml(String html, String referer) {
        List<String> bodies = new ArrayList<>();
        bodies.add(html);
        bodies.add(unescape(html));
        try {
            String decoded = URLDecoder.decode(html, StandardCharsets.UTF_8.name());
            bodies.add(unescape(decoded));
        } catch (Exception ignored) {
        }

        Set<String> candidates = new LinkedHashSet<>();
        for (String body : bodies) {
            Matcher m = HTTP_PATTERN.matcher(body);
            while (m.find()) {
                String u = normalizeUrl(m.group());
                String l = u.toLowerCase(Locale.ROOT);
                if (l.contains("douyinvod") || l.contains("bytecdn") || l.contains("/aweme/v1/play/") ||
                        l.contains("mime_type=video") || l.contains(".mp4")) {
                    candidates.add(rewritePlayUrl(u));
                }
            }
            Matcher um = URI_PATTERN.matcher(body);
            while (um.find()) {
                String uri = um.group(1).trim();
                if (uri.length() >= 12 && !uri.startsWith("http") && !uri.toLowerCase(Locale.ROOT).contains("mp3")) {
                    candidates.add(buildPlayEndpoint(uri, "1080p"));
                }
            }
        }
        for (String candidate : candidates) {
            String ok = validateAndResolve(candidate, referer);
            if (!ok.isEmpty()) return ok;
        }
        return "";
    }

    private static String validateAndResolve(String mediaUrl, String referer) {
        if (mediaUrl == null || !mediaUrl.startsWith("http")) return "";
        String[] userAgents = new String[]{APP_UA, SHARE_UA};
        for (String ua : userAgents) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(mediaUrl).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(7_000);
                conn.setReadTimeout(8_000);
                conn.setRequestProperty("User-Agent", ua);
                conn.setRequestProperty("Accept", "video/*,*/*;q=0.8");
                conn.setRequestProperty("Accept-Encoding", "identity");
                conn.setRequestProperty("Range", "bytes=0-1");
                conn.setRequestProperty("Referer", referer == null || referer.isEmpty() ? "https://www.douyin.com/" : referer);
                int code = conn.getResponseCode();
                String type = conn.getContentType();
                String finalUrl = conn.getURL().toString();
                String lowType = type == null ? "" : type.toLowerCase(Locale.ROOT);
                String lowUrl = finalUrl.toLowerCase(Locale.ROOT);
                boolean media = (code == 200 || code == 206) && (
                        lowType.startsWith("video/") ||
                        lowType.contains("octet-stream") ||
                        lowUrl.contains("douyinvod") || lowUrl.contains("bytecdn") ||
                        lowUrl.contains(".mp4") || lowUrl.contains("/aweme/v1/play/")
                ) && !lowType.contains("text/html") && !lowType.contains("application/json");
                if (media) return finalUrl;
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return "";
    }

    private static String buildPlayEndpoint(String videoId, String ratio) {
        try {
            return "https://www.iesdouyin.com/aweme/v1/play/?video_id=" +
                    java.net.URLEncoder.encode(videoId, StandardCharsets.UTF_8.name()) +
                    "&ratio=" + java.net.URLEncoder.encode(ratio, StandardCharsets.UTF_8.name()) +
                    "&line=0&is_play_url=1&watermark=0&source=PackSourceEnum_PUBLISH";
        } catch (Exception e) {
            return "";
        }
    }

    private static String rewritePlayUrl(String url) {
        if (url == null) return "";
        String u = url.replace("/playwm/", "/play/");
        u = u.replace("watermark=1", "watermark=0");
        return u;
    }

    private static String extractId(String text) {
        if (text == null) return "";
        for (Pattern p : ID_PATTERNS) {
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(1);
        }
        return "";
    }

    private static String extractTitle(String html) {
        if (html == null) return "抖音视频";
        Matcher m = Pattern.compile("(?is)<meta[^>]+(?:property|name)=[\\\"'](?:og:title|twitter:title)[\\\"'][^>]+content=[\\\"']([^\\\"']+)[\\\"']").matcher(html);
        if (m.find()) return cleanTitle(m.group(1));
        m = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);
        return m.find() ? cleanTitle(m.group(1).replaceAll("<[^>]+>", "")) : "抖音视频";
    }

    private static String cleanTitle(String s) {
        if (s == null) return "抖音视频";
        s = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("[\\\\/:*?\"<>|\\r\\n]+", " ").replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return "抖音视频";
        return s.length() > 46 ? s.substring(0, 46).trim() : s;
    }

    private static String normalizeUrl(String u) {
        if (u == null) return "";
        u = unescape(u).replace("&amp;", "&");
        while (u.endsWith("\\") || u.endsWith(",") || u.endsWith("}")) u = u.substring(0, u.length() - 1);
        if (u.startsWith("//")) u = "https:" + u;
        return u;
    }

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\u002F", "/").replace("\\u002f", "/")
                .replace("\\u0026", "&").replace("\\u003D", "=").replace("\\u003d", "=")
                .replace("\\/", "/").replace("&amp;", "&").replace("&quot;", "\"");
    }

    private static List<String> dedupe(List<String> input) {
        return new ArrayList<>(new LinkedHashSet<>(input));
    }

    private static Page fetchPage(String source, String userAgent, String referer, int connectTimeout, int readTimeout) throws Exception {
        String current = source;
        for (int hop = 0; hop < 8; hop++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("User-Agent", userAgent);
            conn.setRequestProperty("Accept", "text/html,application/json,text/plain,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            conn.setRequestProperty("Accept-Encoding", "gzip");
            if (referer != null && !referer.isEmpty()) conn.setRequestProperty("Referer", referer);
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null || loc.isEmpty()) break;
                current = new URL(new URL(current), loc).toString();
                continue;
            }
            InputStream raw;
            try { raw = conn.getInputStream(); } catch (IOException e) { raw = conn.getErrorStream(); }
            if (raw == null) { conn.disconnect(); return new Page(current, ""); }
            InputStream in = raw;
            String enc = conn.getContentEncoding();
            if (enc != null && enc.toLowerCase(Locale.ROOT).contains("gzip")) in = new GZIPInputStream(raw);
            String body = readLimited(in, 10 * 1024 * 1024);
            conn.disconnect();
            return new Page(current, body);
        }
        return new Page(current, "");
    }

    private static String readLimited(InputStream in, int max) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = input.read(buf)) != -1) {
                int write = Math.min(n, max - total);
                if (write > 0) out.write(buf, 0, write);
                total += write;
                if (total >= max) break;
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static final class Page {
        final String url;
        final String body;
        Page(String url, String body) { this.url = url; this.body = body == null ? "" : body; }
    }

    private static final class StreamCandidate {
        final String url;
        final int h265;
        final long pixels;
        final long bitrate;
        StreamCandidate(String url, int h265, long pixels, long bitrate) {
            this.url = url;
            this.h265 = h265;
            this.pixels = pixels;
            this.bitrate = bitrate;
        }
    }
}
