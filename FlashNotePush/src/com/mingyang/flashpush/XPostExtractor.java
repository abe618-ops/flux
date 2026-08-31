package com.mingyang.flashpush;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Extracts full note-tweet and X Article content from the FxTwitter-compatible JSON shape. */
final class XPostExtractor {
    static final class Result {
        final String title;
        final String text;
        final String richSourceHtml;
        final boolean article;

        Result(String title, String text, String richSourceHtml, boolean article) {
            this.title = title;
            this.text = text;
            this.richSourceHtml = richSourceHtml;
            this.article = article;
        }
    }

    private XPostExtractor() {}

    static Result extract(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject post = firstObject(root, "tweet", "status", "post", "data");
        if (post == null) post = root;

        JSONObject author = post.optJSONObject("author");
        String authorName = author == null ? "" : first(author.optString("name"), author.optString("screen_name"));
        JSONObject article = post.optJSONObject("article");
        boolean hasArticle = article != null;
        String title = hasArticle ? article.optString("title", "") : "";
        if (title.isEmpty()) title = authorName.isEmpty() ? "X 推文" : authorName + " 的 X 推文";

        String raw = "";
        JSONObject rawText = post.optJSONObject("raw_text");
        if (rawText != null) raw = rawText.optString("text", "");
        if (raw.isEmpty()) raw = post.optString("text", "");

        StringBuilder articleText = new StringBuilder();
        StringBuilder articleHtml = new StringBuilder();
        if (hasArticle) {
            JSONObject content = article.optJSONObject("content");
            if (content == null) content = article.optJSONObject("content_state");
            JSONArray blocks = content == null ? null : content.optJSONArray("blocks");
            if (blocks != null) appendBlocks(blocks, articleText, articleHtml);
        }

        String body;
        if (hasArticle && articleText.length() > 0) {
            body = title + "\n\n" + articleText;
        } else {
            body = raw;
        }
        body = ArticleExtractor.tidy(removeTrailingShortLinks(body));
        if (body.length() < 3) throw new Exception("empty X content");

        StringBuilder source = new StringBuilder("<article>");
        if (hasArticle && !title.isEmpty()) source.append("<h1>").append(escape(title)).append("</h1>");
        if (articleHtml.length() > 0) source.append(articleHtml);
        else for (String paragraph : body.split("\\n+")) if (!paragraph.trim().isEmpty())
            source.append("<p>").append(escape(paragraph.trim())).append("</p>");

        List<String> images = new ArrayList<>();
        collectImageUrls(post.opt("media"), images, new HashSet<>());
        if (hasArticle) {
            collectImageUrls(article.opt("cover_media"), images, new HashSet<>(images));
            collectImageUrls(article.opt("media_entities"), images, new HashSet<>(images));
        }
        int added = 0;
        Set<String> seen = new HashSet<>();
        for (String image : images) {
            if (!seen.add(image) || added >= 20) continue;
            source.append("<figure><img src=\"").append(escapeAttr(image)).append("\" alt=\"X 图片\"></figure>");
            added++;
        }
        source.append("</article>");
        return new Result(title, body, source.toString(), hasArticle);
    }

    private static void appendBlocks(JSONArray blocks, StringBuilder text, StringBuilder html) {
        boolean inUl = false;
        boolean inOl = false;
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject block = blocks.optJSONObject(i);
            if (block == null) continue;
            String value = ArticleExtractor.tidy(block.optString("text", ""));
            String type = block.optString("type", "unstyled").toLowerCase(Locale.ROOT);
            boolean ul = type.contains("unordered-list");
            boolean ol = type.contains("ordered-list");
            if (!ul && inUl) { html.append("</ul>"); inUl = false; }
            if (!ol && inOl) { html.append("</ol>"); inOl = false; }
            if (value.isEmpty()) continue;
            if (text.length() > 0) text.append("\n\n");
            if (ul) text.append("• ");
            if (ol) text.append(i + 1).append(". ");
            text.append(value);

            if (ul) {
                if (!inUl) { html.append("<ul>"); inUl = true; }
                html.append("<li>").append(escape(value)).append("</li>");
            } else if (ol) {
                if (!inOl) { html.append("<ol>"); inOl = true; }
                html.append("<li>").append(escape(value)).append("</li>");
            } else if (type.contains("header-one")) html.append("<h1>").append(escape(value)).append("</h1>");
            else if (type.contains("header-two")) html.append("<h2>").append(escape(value)).append("</h2>");
            else if (type.contains("header-three")) html.append("<h3>").append(escape(value)).append("</h3>");
            else if (type.contains("blockquote")) html.append("<blockquote>").append(escape(value)).append("</blockquote>");
            else html.append("<p>").append(escape(value)).append("</p>");
        }
        if (inUl) html.append("</ul>");
        if (inOl) html.append("</ol>");
    }

    private static void collectImageUrls(Object node, List<String> output, Set<String> seen) {
        if (node == null || node == JSONObject.NULL || output.size() >= 40) return;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) collectImageUrls(array.opt(i), output, seen);
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject object = (JSONObject) node;
        String[] preferred = {"original_img_url", "media_url_https", "thumbnail_url", "url"};
        for (String key : preferred) {
            String value = object.optString(key, "");
            if (looksLikeImage(value) && seen.add(value)) output.add(value);
        }
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) collectImageUrls(object.opt(keys.next()), output, seen);
    }

    private static boolean looksLikeImage(String value) {
        if (value == null || !value.startsWith("http")) return false;
        String low = value.toLowerCase(Locale.ROOT);
        return low.contains("pbs.twimg.com/media") || low.contains("pbs.twimg.com/card_img") ||
                low.matches(".*\\.(?:jpg|jpeg|png|webp|gif)(?:[?].*)?$");
    }

    private static JSONObject firstObject(JSONObject root, String... keys) {
        for (String key : keys) {
            JSONObject value = root.optJSONObject(key);
            if (value != null) return value;
        }
        return null;
    }

    private static String removeTrailingShortLinks(String value) {
        return value.replaceAll("(?i)(?:\\s*https?://t\\.co/\\S+)+\\s*$", "").trim();
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escapeAttr(String value) { return escape(value).replace("'", "&#39;"); }
}
