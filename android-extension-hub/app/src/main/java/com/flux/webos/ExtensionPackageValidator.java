package com.flux.webos;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ExtensionPackageValidator {
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;

    private ExtensionPackageValidator() {}

    public static Result inspect(Context context, Uri uri) throws Exception {
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IllegalArgumentException("Cannot open selected file");
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (!entry.isDirectory() && ("manifest.json".equals(name) || name.endsWith("/manifest.json"))) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int total = 0;
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            total += read;
                            if (total > MAX_MANIFEST_BYTES) {
                                throw new IllegalArgumentException("manifest.json is too large");
                            }
                            out.write(buffer, 0, read);
                        }
                        String json = out.toString(StandardCharsets.UTF_8);
                        return parseManifest(json);
                    }
                }
            }
        }
        throw new IllegalArgumentException("No manifest.json found in ZIP");
    }

    private static Result parseManifest(String raw) throws Exception {
        JSONObject manifest = new JSONObject(raw);
        String name = manifest.optString("name", "Unnamed extension");
        String version = manifest.optString("version", "unknown");
        int manifestVersion = manifest.optInt("manifest_version", 0);

        List<String> permissions = new ArrayList<>();
        JSONArray perms = manifest.optJSONArray("permissions");
        if (perms != null) {
            for (int i = 0; i < perms.length(); i++) permissions.add(perms.optString(i));
        }
        JSONArray hosts = manifest.optJSONArray("host_permissions");
        if (hosts != null) {
            for (int i = 0; i < hosts.length(); i++) permissions.add(hosts.optString(i));
        }

        String compatibility = "A";
        String reason = "Pure WebExtension capabilities";
        String joined = permissions.toString().toLowerCase();
        if (joined.contains("nativeMessaging".toLowerCase()) || manifest.has("devtools_page")) {
            compatibility = "C";
            reason = "Desktop/native integration detected; not supported in the first mobile runtime";
        } else if (joined.contains("downloads") || joined.contains("clipboard") || joined.contains("notifications")) {
            compatibility = "B";
            reason = "Requires Flux Android Bridge capability mapping";
        }

        if (manifestVersion != 2 && manifestVersion != 3) {
            compatibility = "C";
            reason = "Unsupported or missing manifest_version";
        }

        return new Result(name, version, manifestVersion, permissions, compatibility, reason);
    }

    public record Result(
            String name,
            String version,
            int manifestVersion,
            List<String> permissions,
            String compatibility,
            String reason
    ) {}
}
