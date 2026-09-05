package com.flux.webos;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;

import java.util.List;

public class ExtensionsActivity extends Activity {
    private static final int PICK_EXTENSION_ZIP = 4101;
    private TextView result;
    private LinearLayout installedList;
    private EditText signedUrl;
    private GeckoRuntime runtime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Flux Extensions");
        runtime = FluxRuntime.get(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Flux Extension Center");
        title.setTextSize(24);
        root.addView(title);

        TextView intro = new TextView(this);
        intro.setText("一个 APK 承载 WebExtension。\n\nA：可直接运行 · B：需要 Flux Android Bridge · C：依赖桌面/不兼容 API。\n\nGecko 原生安装通道用于 Mozilla 签名的 XPI/WebExtension；普通 Chrome ZIP 先进入 Flux 兼容层检查，不绕过签名校验。");
        intro.setTextSize(16);
        intro.setPadding(0, dp(12), 0, dp(18));
        root.addView(intro);

        TextView signedTitle = new TextView(this);
        signedTitle.setText("安装签名 WebExtension / XPI");
        signedTitle.setTextSize(18);
        root.addView(signedTitle);

        signedUrl = new EditText(this);
        signedUrl.setSingleLine(true);
        signedUrl.setHint("https://.../extension.xpi");
        root.addView(signedUrl, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        Button installSigned = new Button(this);
        installSigned.setText("安装签名扩展");
        installSigned.setAllCaps(false);
        installSigned.setOnClickListener(v -> installSignedExtension());
        root.addView(installSigned, fullButtonParams());

        Button importZip = new Button(this);
        importZip.setText("导入 Chrome / WebExtension ZIP（兼容检查）");
        importZip.setAllCaps(false);
        importZip.setOnClickListener(v -> pickZip());
        root.addView(importZip, fullButtonParams());

        result = new TextView(this);
        result.setText("状态：准备就绪");
        result.setTextSize(15);
        result.setGravity(Gravity.START);
        result.setPadding(0, dp(18), 0, dp(18));
        root.addView(result);

        TextView installedTitle = new TextView(this);
        installedTitle.setText("已安装扩展");
        installedTitle.setTextSize(20);
        root.addView(installedTitle);

        Button refresh = new Button(this);
        refresh.setText("刷新列表");
        refresh.setAllCaps(false);
        refresh.setOnClickListener(v -> refreshInstalled());
        root.addView(refresh, fullButtonParams());

        installedList = new LinearLayout(this);
        installedList.setOrientation(LinearLayout.VERTICAL);
        root.addView(installedList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scroll);
        refreshInstalled();
    }

    private LinearLayout.LayoutParams fullButtonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        p.topMargin = dp(10);
        return p;
    }

    private void installSignedExtension() {
        String url = signedUrl.getText().toString().trim();
        if (!(url.startsWith("https://") || url.startsWith("file://"))) {
            result.setText("请输入 https:// 或 file:// 的签名 XPI/WebExtension 地址。");
            return;
        }
        result.setText("正在交给 GeckoView 校验签名并安装…");
        AddonController.installSigned(runtime, url, new AddonController.ExtensionCallback() {
            @Override public void onSuccess(WebExtension extension) {
                runOnUiThread(() -> {
                    result.setText("安装成功：" + displayName(extension));
                    signedUrl.setText("");
                    refreshInstalled();
                });
            }

            @Override public void onError(Throwable error) {
                runOnUiThread(() -> result.setText("安装失败：" + safeError(error)
                        + "\n\n提示：Gecko 普通安装要求 Mozilla 签名。未签名 Chrome ZIP 请使用下方 ZIP 兼容检查入口。"));
            }
        });
    }

    private void refreshInstalled() {
        installedList.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("正在读取…");
        installedList.addView(loading);

        AddonController.list(runtime, new AddonController.ListCallback() {
            @Override public void onSuccess(List<WebExtension> extensions) {
                runOnUiThread(() -> renderInstalled(extensions));
            }

            @Override public void onError(Throwable error) {
                runOnUiThread(() -> {
                    installedList.removeAllViews();
                    TextView e = new TextView(ExtensionsActivity.this);
                    e.setText("读取失败：" + safeError(error));
                    installedList.addView(e);
                });
            }
        });
    }

    private void renderInstalled(List<WebExtension> extensions) {
        installedList.removeAllViews();
        if (extensions == null || extensions.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无扩展");
            empty.setPadding(0, dp(12), 0, dp(12));
            installedList.addView(empty);
            return;
        }

        for (WebExtension extension : extensions) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(12), dp(12), dp(12));

            String version = extension.metaData == null ? "?" : extension.metaData.version;
            boolean enabled = extension.metaData == null || extension.metaData.enabled;
            TextView info = new TextView(this);
            info.setText(displayName(extension)
                    + "\n版本：" + version
                    + " · " + (enabled ? "已启用" : "已停用")
                    + (extension.isBuiltIn ? " · 系统内置" : "")
                    + "\nID：" + extension.id);
            info.setTextSize(15);
            card.addView(info);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button toggle = new Button(this);
            toggle.setText(enabled ? "停用" : "启用");
            toggle.setAllCaps(false);
            toggle.setOnClickListener(v -> setEnabled(extension, !enabled));
            actions.addView(toggle, new LinearLayout.LayoutParams(0, dp(48), 1f));

            if (!extension.isBuiltIn) {
                Button remove = new Button(this);
                remove.setText("卸载");
                remove.setAllCaps(false);
                remove.setOnClickListener(v -> uninstall(extension));
                actions.addView(remove, new LinearLayout.LayoutParams(0, dp(48), 1f));
            }

            card.addView(actions, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            installedList.addView(card);
        }
    }

    private void setEnabled(WebExtension extension, boolean enabled) {
        AddonController.ExtensionCallback cb = new AddonController.ExtensionCallback() {
            @Override public void onSuccess(WebExtension updated) {
                runOnUiThread(() -> {
                    result.setText((enabled ? "已启用：" : "已停用：") + displayName(updated));
                    refreshInstalled();
                });
            }

            @Override public void onError(Throwable error) {
                runOnUiThread(() -> result.setText("操作失败：" + safeError(error)));
            }
        };
        if (enabled) AddonController.enable(runtime, extension, cb);
        else AddonController.disable(runtime, extension, cb);
    }

    private void uninstall(WebExtension extension) {
        AddonController.uninstall(runtime, extension, new AddonController.VoidCallback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> {
                    result.setText("已卸载：" + displayName(extension));
                    refreshInstalled();
                });
            }

            @Override public void onError(Throwable error) {
                runOnUiThread(() -> result.setText("卸载失败：" + safeError(error)));
            }
        });
    }

    private void pickZip() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream"
        });
        startActivityForResult(intent, PICK_EXTENSION_ZIP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_EXTENSION_ZIP || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        result.setText("正在检查扩展包…");
        new Thread(() -> {
            try {
                ExtensionPackageValidator.Result info = ExtensionPackageValidator.inspect(this, uri);
                String permissions = info.permissions().isEmpty()
                        ? "无显式权限"
                        : String.join(", ", info.permissions());
                String text = "检查通过\n\n"
                        + "名称：" + info.name() + "\n"
                        + "版本：" + info.version() + "\n"
                        + "Manifest：MV" + info.manifestVersion() + "\n"
                        + "权限：" + permissions + "\n\n"
                        + "兼容等级：" + info.compatibility() + "\n"
                        + "说明：" + info.reason() + "\n\n"
                        + "该 ZIP 尚未执行。后续由 Flux Compatibility Runtime 对 A/B 级 Chrome ZIP 提供受限运行。";
                runOnUiThread(() -> result.setText(text));
            } catch (Exception e) {
                runOnUiThread(() -> result.setText("扩展包检查失败：\n" + safeError(e)));
            }
        }).start();
    }

    private static String displayName(WebExtension extension) {
        if (extension != null && extension.metaData != null
                && extension.metaData.name != null && !extension.metaData.name.isBlank()) {
            return extension.metaData.name;
        }
        return extension == null ? "Unknown" : extension.id;
    }

    private static String safeError(Throwable error) {
        if (error == null) return "未知错误";
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
