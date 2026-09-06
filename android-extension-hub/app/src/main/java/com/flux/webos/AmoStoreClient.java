package com.flux.webos;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class AmoStoreClient {
    private AmoStoreClient() {}

    public record Addon(String name, String slug, String summary, String version,
                        double rating, int users, String xpiUrl, boolean recommended) {}

    public static List<Addon> search(String query) throws Exception {
        String q = URLEncoder.encode(query == null ? "" : query.trim(), StandardCharsets.UTF_8);
        String endpoint = "https://addons.mozilla.org/api/v5/addons/search/?app=android&type=extension&page_size=20&lang=zh-CN&q=" + q;
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(18000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "Flux-WebOS/0.2");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("AMO HTTP " + c.getResponseCode());
            StringBuilder json = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) json.append(line);
            }
            JSONArray results = new JSONObject(json.toString()).optJSONArray("results");
            List<Addon> out = new ArrayList<>();
            if (results == null) return out;
            for (int i = 0; i < results.length(); i++) {
                JSONObject a = results.getJSONObject(i);
                JSONObject current = a.optJSONObject("current_version");
                String xpi = null;
                String version = "?";
                if (current != null) {
                    version = current.optString("version", "?");
                    JSONArray files = current.optJSONArray("files");
                    if (files != null) {
                        for (int f = 0; f < files.length(); f++) {
                            JSONObject file = files.getJSONObject(f);
                            String platform = file.optString("platform", "all");
                            if ("android".equals(platform) || "all".equals(platform)) {
                                xpi = file.optString("url", null);
                                if (xpi != null && !xpi.isBlank()) break;
                            }
                        }
                    }
                }
                JSONObject ratings = a.optJSONObject("ratings");
                double rating = ratings == null ? 0 : ratings.optDouble("average", 0);
                JSONObject promoted = a.optJSONObject("promoted");
                boolean recommended = promoted != null && ("recommended".equals(promoted.optString("category")) || "line".equals(promoted.optString("category")));
                out.add(new Addon(
                        translated(a.opt("name")), a.optString("slug", ""), translated(a.opt("summary")),
                        version, rating, a.optInt("average_daily_users", 0), xpi, recommended));
            }
            return out;
        } finally {
            c.disconnect();
        }
    }

    private static String translated(Object value) {
        if (value instanceof String) return (String) value;
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            String zh = o.optString("zh-CN", "");
            if (!zh.isBlank()) return zh;
            String en = o.optString("en-US", "");
            if (!en.isBlank()) return en;
            for (String key : o.keySet()) {
                String v = o.optString(key, "");
                if (!v.isBlank()) return v;
            }
        }
        return "";
    }
}