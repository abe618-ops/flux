package com.mingyang.flashpush;

import android.util.Base64;

import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HtmlArchive {
    interface ImageLoader { ImageData load(String absoluteUrl) throws Exception; }

    static final class ImageData {
        final byte[] bytes;
        final String mime;
        ImageData(byte[] bytes, String mime) { this.bytes = bytes; this.mime = mime; }
    }

    static final class Result {
        final String html;
        final int embeddedImages;
        Result(String html, int embeddedImages) { this.html = html; this.embeddedImages = embeddedImages; }
    }

    private static final Pattern IMAGE = Pattern.compile("(?is)<img\\b[^>]*>");
    private static final Pattern LINK = Pattern.compile("(?is)<a\\b[^>]*>");
    private static final Pattern ATTR = Pattern.compile("(?is)\\b([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*([\"'])(.*?)\\2");
    private static final int MAX_IMAGES = 20;
    private static final int MAX_TOTAL_IMAGE_BYTES = 10 * 1024 * 1024;

    private HtmlArchive() {}

    static Result fromWebPage(String originalHtml, String baseUrl, String title,
                              String plainText, ImageLoader loader) {
        String block = ArticleExtractor.richHtmlBlocks(originalHtml);
        if (ArticleExtractor.htmlToText(block).length() < 80) block = textAsHtml(plainText);

        String cover = firstNonEmpty(
                ArticleExtractor.metaValue(originalHtml, "property", "og:image", "content"),
                ArticleExtractor.metaValue(originalHtml, "name", "twitter:image", "content"));
        if (!cover.isEmpty() && !IMAGE.matcher(block).find()) {
            block = "<figure class=\"cover\"><img data-src=\"" + escapeAttr(cover) + "\" alt=\"封面图\"></figure>" + block;
        }

        block = sanitize(block);
        Matcher matcher = IMAGE.matcher(block);
        StringBuffer rewritten = new StringBuffer();
        int count = 0;
        int total = 0;
        while (matcher.find()) {
            String tag = matcher.group();
            String src = firstNonEmpty(attribute(tag, "data-src"), attribute(tag, "data-original"),
                    attribute(tag, "data-actualsrc"), attribute(tag, "data-lazy-src"), attribute(tag, "src"),
                    firstSrcset(attribute(tag, "srcset")));
            String alt = firstNonEmpty(attribute(tag, "alt"), attribute(tag, "title"));
            String replacement = "";
            if (src.startsWith("data:image/")) {
                replacement = "<img src=\"" + escapeAttr(src) + "\" alt=\"" + escapeAttr(alt) + "\">";
            } else if (!src.isEmpty() && count < MAX_IMAGES && total < MAX_TOTAL_IMAGE_BYTES) {
                try {
                    String absolute = new URL(new URL(baseUrl), src).toString();
                    ImageData image = loader.load(absolute);
                    if (image != null && image.bytes.length > 0 && total + image.bytes.length <= MAX_TOTAL_IMAGE_BYTES) {
                        String data = Base64.encodeToString(image.bytes, Base64.NO_WRAP);
                        replacement = "<img src=\"data:" + escapeAttr(image.mime) + ";base64," + data + "\" alt=\"" + escapeAttr(alt) + "\">";
                        count++;
                        total += image.bytes.length;
                    }
                } catch (Exception ignored) { }
            }
            if (replacement.isEmpty() && !src.isEmpty()) {
                try { replacement = "<img src=\"" + escapeAttr(new URL(new URL(baseUrl), src).toString()) + "\" alt=\"" + escapeAttr(alt) + "\">"; }
                catch (Exception ignored) { replacement = ""; }
            }
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rewritten);
        String richBody = rewriteLinks(rewritten.toString(), baseUrl);

        String document = "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<title>" + escape(title) + "</title><style>" +
                "body{max-width:760px;margin:0 auto;padding:24px 18px;background:#f4f8f4;color:#27322c;" +
                "font:18px/1.78 serif;word-wrap:break-word}h1{font-size:28px;line-height:1.35}h2{font-size:23px}h3{font-size:20px}" +
                "img{display:block;max-width:100%;height:auto;margin:18px auto;border-radius:8px}figure{margin:22px 0}figcaption{color:#748078;font-size:14px;text-align:center}" +
                "p{margin:0 0 1em}a{color:#237c56}strong,b{font-weight:700}blockquote{margin:18px 0;border-left:4px solid #87b89d;padding:8px 14px;background:#eaf3ed}" +
                "ul,ol{padding-left:1.5em}li{margin:.35em 0}table{display:block;max-width:100%;overflow:auto;border-collapse:collapse;margin:18px 0}" +
                "th,td{border:1px solid #bdcbc2;padding:7px 9px;vertical-align:top}pre,code{white-space:pre-wrap;background:#e8efea;border-radius:5px}" +
                "pre{padding:12px}.flashnote-part{border-top:1px dashed #b8c7bd;margin-top:24px;padding-top:20px}" +
                ".source{font-size:13px;color:#718078;border-top:1px solid #ccd8d0;margin-top:28px;padding-top:12px}</style></head><body>" +
                "<h1>" + escape(title) + "</h1>" + richBody +
                "<div class=\"source\">来源：<a href=\"" + escapeAttr(baseUrl) + "\">" + escape(baseUrl) + "</a></div>" +
                "</body></html>";
        return new Result(document, count);
    }

    static Result fromPlainText(String title, String text, String sourceUrl) {
        String source = sourceUrl == null || sourceUrl.isEmpty() ? "" :
                "<div class=\"source\">来源：<a href=\"" + escapeAttr(sourceUrl) + "\">" + escape(sourceUrl) + "</a></div>";
        String document = "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + escape(title) + "</title>" +
                "<style>body{max-width:760px;margin:0 auto;padding:24px 18px;background:#f4f8f4;color:#27322c;font:18px/1.75 serif}" +
                "h1{font-size:28px}.source{font-size:13px;color:#718078;border-top:1px solid #ccd8d0;margin-top:28px;padding-top:12px}</style>" +
                "</head><body><h1>" + escape(title) + "</h1>" + textAsHtml(text) + source + "</body></html>";
        return new Result(document, 0);
    }

    private static String sanitize(String html) {
        return html
                .replaceAll("(?is)<!--.*?-->", "")
                .replaceAll("(?is)<(script|style|noscript|iframe|object|embed|form|button|input|textarea|select|svg|canvas)\\b[^>]*>.*?</\\1>", "")
                .replaceAll("(?is)<(script|style|noscript|iframe|object|embed|form|button|input|textarea|select|svg|canvas)\\b[^>]*/?>", "")
                .replaceAll("(?is)\\s+on[a-z]+\\s*=\\s*([\"']).*?\\1", "")
                .replaceAll("(?is)\\s+(sizes|loading|decoding)\\s*=\\s*([\"']).*?\\2", "");
    }

    private static String rewriteLinks(String html, String baseUrl) {
        Matcher matcher = LINK.matcher(html);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String tag = matcher.group();
            String href = attribute(tag, "href");
            String replacement = tag;
            if (!href.isEmpty() && !href.startsWith("#") && !href.toLowerCase(Locale.ROOT).startsWith("mailto:")) {
                try {
                    String absolute = new URL(new URL(baseUrl), href).toString();
                    replacement = tag.replace(href, escapeAttr(absolute));
                } catch (Exception ignored) { }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String firstSrcset(String srcset) {
        if (srcset == null || srcset.trim().isEmpty()) return "";
        String first = srcset.split(",", 2)[0].trim();
        return first.split("\\s+", 2)[0].trim();
    }

    private static String attribute(String tag, String wanted) {
        Matcher matcher = ATTR.matcher(tag);
        while (matcher.find()) if (wanted.equalsIgnoreCase(matcher.group(1))) return matcher.group(3).trim();
        return "";
    }

    private static String textAsHtml(String text) {
        String[] paragraphs = ArticleExtractor.tidy(text).split("\\n\\s*\\n|\\n");
        StringBuilder out = new StringBuilder();
        for (String paragraph : paragraphs) if (!paragraph.trim().isEmpty()) out.append("<p>").append(escape(paragraph.trim())).append("</p>");
        return out.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escapeAttr(String value) { return escape(value).replace("'", "&#39;"); }
    private static String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }
}
