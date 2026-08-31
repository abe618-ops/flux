package com.mingyang.flashpush;

import android.os.Build;
import android.text.Html;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ArticleExtractor {
    static final int MAX_RESULT_CHARS = 240000;

    static final class Result {
        final String title;
        final String text;
        final String method;
        Result(String title, String text, String method) {
            this.title = cleanLine(title);
            this.text = tidy(text);
            this.method = method;
        }
    }

    private static final Pattern SCRIPT = Pattern.compile("(?is)<script\\b([^>]*)>(.*?)</script>");
    private static final Pattern META = Pattern.compile("(?is)<meta\\b([^>]*)>");
    private static final Pattern ATTR = Pattern.compile("(?is)([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2");
    private static final Pattern CONTENT_OPEN = Pattern.compile("(?is)<(article|main|section|div)\\b([^>]*)>");
    private static final Pattern RICH_ELEMENT = Pattern.compile("(?is)<(p|h[1-6]|li|blockquote|figcaption|pre|td|th|dt|dd)\\b[^>]*>(.*?)</\\1>");
    private static final Pattern RICH_HTML_ELEMENT = Pattern.compile("(?is)<(p|h[1-6]|ul|ol|dl|blockquote|figure|table|pre)\\b[^>]*>.*?</\\1>");
    private static final Pattern CONTENT_HINT = Pattern.compile("(?i)(js_content|rich_media_content|article[-_ ]?(content|body|text)|post[-_ ]?(content|body)|entry[-_ ]?content|story[-_ ]?body|main[-_ ]?content|content[-_ ]?body|正文|文章内容)");

    private ArticleExtractor() {}

    static Result extractGeneric(String html) {
        if (html == null) return new Result("", "", "empty");
        String title = firstNonEmpty(meta(html, "property", "og:title", "content"), meta(html, "name", "twitter:title", "content"), tagText(html, "title"));
        Result json = extractJsonLd(html, title);
        String prepared = removeNoise(html);
        String semanticText = textFromBlocks(semanticBlocks(prepared));
        String elementText = collectRichElements(prepared);

        String body = longest(json.text, semanticText);
        String method = json.text.length() >= semanticText.length() ? "json-ld-multi" : "semantic-multi";
        // 不把短摘要误判为全文；正文元素明显更长时继续向下提取。
        if (body.length() < 800 && elementText.length() > body.length()) {
            body = elementText;
            method = "full-elements";
        }
        if (body.length() < 240) {
            String pageText = removeBoilerplate(htmlToText(prepared));
            if (pageText.length() > body.length()) {
                body = pageText;
                method = "page-fallback";
            }
        }
        return limit(new Result(firstNonEmpty(json.title, title), body, method));
    }

    static Result extractTelegram(String html) {
        String author = classText(html, "tgme_widget_message_author_name");
        String body = classText(html, "tgme_widget_message_text");
        if (body.isEmpty()) body = classText(html, "tgme_widget_message_caption");
        return limit(new Result(author, body, "telegram-public-widget"));
    }

    static String richHtmlBlocks(String html) {
        String prepared = removeNoise(html == null ? "" : html);
        List<String> blocks = semanticBlocks(prepared);
        if (!blocks.isEmpty()) return joinHtml(blocks);
        Matcher matcher = RICH_HTML_ELEMENT.matcher(prepared);
        StringBuilder out = new StringBuilder();
        Set<String> seen = new HashSet<>();
        while (matcher.find() && out.length() < 900000) {
            String fragment = matcher.group();
            String fp = fingerprint(htmlToText(fragment));
            if (fp.length() >= 3 && seen.add(fp)) out.append(fragment);
        }
        return out.toString();
    }

    static String metaValue(String html, String key, String expected, String valueKey) {
        return meta(html == null ? "" : html, key, expected, valueKey);
    }

    private static Result extractJsonLd(String html, String fallbackTitle) {
        Matcher matcher = SCRIPT.matcher(html);
        List<String> bodies = new ArrayList<>();
        String headline = "";
        while (matcher.find()) {
            if (!matcher.group(1).toLowerCase(Locale.ROOT).contains("ld+json")) continue;
            String json = htmlToText(matcher.group(2)).trim();
            try {
                Object root = json.startsWith("[") ? new JSONArray(json) : new JSONObject(json);
                collectJsonValues(root, "articleBody", bodies);
                if (headline.isEmpty()) headline = firstJsonValue(root, "headline");
            } catch (Exception ignored) { }
        }
        return new Result(firstNonEmpty(headline, fallbackTitle), joinUniqueTexts(bodies), "json-ld-multi");
    }

    private static void collectJsonValues(Object node, String wanted, List<String> output) {
        try {
            if (node instanceof JSONObject) {
                JSONObject object = (JSONObject) node;
                Object direct = object.opt(wanted);
                if (direct instanceof String && !((String) direct).trim().isEmpty()) output.add((String) direct);
                Iterator<String> keys = object.keys();
                while (keys.hasNext()) collectJsonValues(object.opt(keys.next()), wanted, output);
            } else if (node instanceof JSONArray) {
                JSONArray array = (JSONArray) node;
                for (int i = 0; i < array.length(); i++) collectJsonValues(array.opt(i), wanted, output);
            }
        } catch (Exception ignored) { }
    }

    private static String firstJsonValue(Object node, String wanted) {
        List<String> values = new ArrayList<>();
        collectJsonValues(node, wanted, values);
        return values.isEmpty() ? "" : values.get(0);
    }

    private static List<String> semanticBlocks(String html) {
        List<String> blocks = new ArrayList<>();
        List<String> fingerprints = new ArrayList<>();
        Matcher matcher = CONTENT_OPEN.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group(1).toLowerCase(Locale.ROOT);
            String attrs = matcher.group(2);
            if (!("article".equals(tag) || "main".equals(tag) || CONTENT_HINT.matcher(attrs).find())) continue;
            String block = balancedInner(html, matcher.end(), tag);
            String text = removeBoilerplate(htmlToText(removeNoise(block)));
            if (text.length() < 80) continue;
            String fp = fingerprint(text);
            boolean duplicate = false;
            for (String old : fingerprints) {
                if (old.equals(fp) || (old.length() > 120 && fp.length() > 120 && (old.contains(fp) || fp.contains(old)))) { duplicate = true; break; }
            }
            if (!duplicate) { blocks.add(block); fingerprints.add(fp); }
        }
        return blocks;
    }

    private static String balancedInner(String html, int openEnd, String tag) {
        Pattern tokenPattern = Pattern.compile("(?is)</?" + Pattern.quote(tag) + "\\b[^>]*>");
        Matcher token = tokenPattern.matcher(html);
        token.region(openEnd, html.length());
        int depth = 1;
        while (token.find()) {
            String value = token.group().toLowerCase(Locale.ROOT);
            if (value.startsWith("</")) depth--;
            else if (!value.endsWith("/>")) depth++;
            if (depth == 0) return html.substring(openEnd, token.start());
        }
        return html.substring(openEnd, Math.min(html.length(), openEnd + 900000));
    }

    private static String textFromBlocks(List<String> blocks) {
        List<String> values = new ArrayList<>();
        for (String block : blocks) {
            String value = removeBoilerplate(htmlToText(removeNoise(block)));
            if (value.length() >= 30) values.add(value);
        }
        return joinUniqueTexts(values);
    }

    private static String collectRichElements(String html) {
        Matcher matcher = RICH_ELEMENT.matcher(html);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            String value = cleanLine(htmlToText(matcher.group(2)));
            if (value.length() >= 3 && !isBoilerplateLine(value)) values.add(value);
        }
        return joinUniqueTexts(values);
    }

    private static String joinUniqueTexts(List<String> values) {
        StringBuilder out = new StringBuilder();
        Set<String> seen = new HashSet<>();
        for (String raw : values) {
            for (String part : tidy(raw).split("\\n+")) {
                String value = cleanLine(part);
                String fp = fingerprint(value);
                if (value.length() < 2 || isBoilerplateLine(value) || !seen.add(fp)) continue;
                if (out.length() > 0) out.append("\n\n");
                out.append(value);
                if (out.length() >= MAX_RESULT_CHARS) return tidy(out.toString());
            }
        }
        return tidy(out.toString());
    }

    private static String joinHtml(List<String> blocks) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) out.append("\n<section class=\"flashnote-part\">\n");
            out.append(blocks.get(i));
            if (i > 0) out.append("\n</section>");
            if (out.length() > 900000) break;
        }
        return out.toString();
    }

    private static String meta(String html, String key, String expected, String valueKey) {
        Matcher matcher = META.matcher(html);
        while (matcher.find()) {
            java.util.Map<String, String> attrs = attributes(matcher.group(1));
            String actual = attrs.get(key);
            if (actual != null && expected.equalsIgnoreCase(actual.trim())) return htmlToText(attrs.containsKey(valueKey) ? attrs.get(valueKey) : "");
        }
        return "";
    }

    private static java.util.Map<String, String> attributes(String source) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        Matcher matcher = ATTR.matcher(source);
        while (matcher.find()) result.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(3));
        return result;
    }

    private static String classText(String html, String className) {
        Pattern open = Pattern.compile("(?is)<([a-z0-9]+)\\b[^>]*class=[\"'][^\"']*\\b" + Pattern.quote(className) + "\\b[^\"']*[\"'][^>]*>");
        Matcher matcher = open.matcher(html);
        if (!matcher.find()) return "";
        return tidy(htmlToText(balancedInner(html, matcher.end(), matcher.group(1))));
    }

    private static String tagText(String html, String tag) {
        Matcher matcher = Pattern.compile("(?is)<" + tag + "\\b[^>]*>(.*?)</" + tag + ">").matcher(html);
        return matcher.find() ? htmlToText(matcher.group(1)) : "";
    }

    private static String removeNoise(String html) {
        return html.replaceAll("(?is)<!--.*?-->", " ")
                .replaceAll("(?is)<(script|style|noscript|svg|canvas|nav|header|footer|aside|form|button|dialog)\\b[^>]*>.*?</\\1>", " ");
    }

    private static String removeBoilerplate(String text) {
        StringBuilder out = new StringBuilder();
        Set<String> seen = new HashSet<>();
        for (String raw : tidy(text).split("\\n")) {
            String line = cleanLine(raw);
            if (line.isEmpty() || isBoilerplateLine(line) || !seen.add(fingerprint(line))) continue;
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        return tidy(out.toString());
    }

    private static boolean isBoilerplateLine(String line) {
        String low = line.toLowerCase(Locale.ROOT);
        return low.matches("^(menu|home|login|log in|sign in|sign up|subscribe|accept|reject|privacy|cookie(s)?|share|advertisement)$")
                || line.matches("^(首页|登录|注册|订阅|菜单|分享|广告|返回顶部|接受|拒绝|隐私政策|相关阅读|相关推荐)$");
    }

    static String htmlToText(String html) {
        if (html == null || html.isEmpty()) return "";
        String withBreaks = html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|li|h[1-6]|blockquote|section|figcaption|tr|pre|dt|dd)\\s*>", "\n");
        CharSequence value = Build.VERSION.SDK_INT >= 24 ? Html.fromHtml(withBreaks, Html.FROM_HTML_MODE_LEGACY) : Html.fromHtml(withBreaks);
        return tidy(value.toString());
    }

    static String tidy(String text) {
        if (text == null) return "";
        String s = text.replace("\r\n", "\n").replace('\r', '\n').replace('\u00A0', ' ').trim();
        s = s.replaceAll("[ \\t]+(?=\\n)", "").replaceAll("\\n[ \\t]+", "\n").replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    private static String fingerprint(String text) { return cleanLine(text).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}，。！？；：、‘’“”（）【】]+", ""); }
    private static String cleanLine(String text) { return tidy(text).replaceAll("\\s*\\n\\s*", " ").trim(); }
    private static String longest(String first, String second) { return first.length() >= second.length() ? first : second; }
    private static String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }
    private static Result limit(Result input) {
        String text = input.text;
        if (text.length() > MAX_RESULT_CHARS) text = text.substring(0, MAX_RESULT_CHARS) + "\n\n[正文过长，已截取前 24 万字]";
        return new Result(input.title, text, input.method);
    }
}
