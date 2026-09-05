package com.flux.webos;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Stores inspected Chrome/WebExtension ZIPs in app-private storage. */
public final class FluxPackageStore {
    private static final String PREFS = "flux_extension_packages";
    private static final String KEY = "packages";

    private FluxPackageStore() {}

    public record PackageInfo(String id, String name, String version, String compatibility, String path) {}

    public static PackageInfo importPackage(
            Context context,
            Uri source,
            ExtensionPackageValidator.Result info
    ) throws Exception {
        File dir = new File(context.getFilesDir(), "flux-extensions");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create extension store");

        String id = sha256(source.toString() + "|" + info.name() + "|" + info.version());
        File destination = new File(dir, id + ".zip");
        try (InputStream in = context.getContentResolver().openInputStream(source);
             FileOutputStream out = new FileOutputStream(destination)) {
            if (in == null) throw new IllegalArgumentException("Cannot reopen selected extension");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }

        PackageInfo stored = new PackageInfo(
                id, info.name(), info.version(), info.compatibility(), destination.getAbsolutePath());
        upsert(context, stored);
        return stored;
    }

    public static List<PackageInfo> list(Context context) {
        List<PackageInfo> out = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                String path = o.optString("path", "");
                if (!path.isEmpty() && new File(path).exists()) {
                    out.add(new PackageInfo(
                            o.optString("id"),
                            o.optString("name", "Unnamed extension"),
                            o.optString("version", "?"),
                            o.optString("compatibility", "?"),
                            path));
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void remove(Context context, PackageInfo target) {
        if (target.path() != null) new File(target.path()).delete();
        List<PackageInfo> current = list(context);
        current.removeIf(p -> p.id().equals(target.id()));
        save(context, current);
    }

    private static void upsert(Context context, PackageInfo value) {
        List<PackageInfo> current = list(context);
        current.removeIf(p -> p.id().equals(value.id()));
        current.add(value);
        save(context, current);
    }

    private static void save(Context context, List<PackageInfo> values) {
        JSONArray array = new JSONArray();
        try {
            for (PackageInfo p : values) {
                JSONObject o = new JSONObject();
                o.put("id", p.id());
                o.put("name", p.name());
                o.put("version", p.version());
                o.put("compatibility", p.compatibility());
                o.put("path", p.path());
                array.put(o);
            }
        } catch (Exception ignored) {}
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, array.toString()).apply();
    }

    private static String sha256(String input) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString().substring(0, 24);
    }
}
