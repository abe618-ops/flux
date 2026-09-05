package com.flux.webos;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ExtensionsActivity extends Activity {
    private static final int PICK_EXTENSION_ZIP = 4101;
    private TextView result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Flux Extensions");

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
        intro.setText("一个 APK 承载 WebExtension。当前阶段支持导入 ZIP 并检查 manifest、权限和移动端兼容等级。\n\n兼容等级：A 直接运行 · B 需要 Android Bridge · C 暂不支持");
        intro.setTextSize(16);
        intro.setPadding(0, dp(12), 0, dp(18));
        root.addView(intro);

        TextView builtIn = new TextView(this);
        builtIn.setText("✓ Flux Bridge\n内置扩展 · 已随系统安装 · A/B 基础桥接层");
        builtIn.setTextSize(16);
        builtIn.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(builtIn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Button importZip = new Button(this);
        importZip.setText("导入 WebExtension ZIP");
        importZip.setAllCaps(false);
        importZip.setOnClickListener(v -> pickZip());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52));
        buttonParams.topMargin = dp(18);
        root.addView(importZip, buttonParams);

        result = new TextView(this);
        result.setText("请选择一个扩展 ZIP。Flux 会先做本地检查，不会在检查阶段执行其中代码。");
        result.setTextSize(15);
        result.setGravity(Gravity.START);
        result.setPadding(0, dp(18), 0, 0);
        root.addView(result);

        setContentView(scroll);
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
                        + "下一阶段将把通过检查的 A/B 级扩展加入 GeckoView 安装与启停管理。";
                runOnUiThread(() -> result.setText(text));
            } catch (Exception e) {
                runOnUiThread(() -> result.setText("扩展包检查失败：\n" + e.getMessage()));
            }
        }).start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
