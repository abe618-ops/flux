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

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

public class MainActivity extends Activity {
    private GeckoSession session;
    private EditText addressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GeckoRuntime runtime = FluxRuntime.get(this);
        ExtensionManager.ensureBuiltIns(runtime);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        int padding = dp(8);
        toolbar.setPadding(padding, padding, padding, padding);

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint("搜索或输入网址");
        addressBar.setText("https://www.google.com");
        toolbar.addView(addressBar, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button go = new Button(this);
        go.setText("打开");
        go.setAllCaps(false);
        go.setOnClickListener(v -> loadInput(addressBar.getText().toString()));
        toolbar.addView(go, new LinearLayout.LayoutParams(dp(72), dp(48)));

        Button extensions = new Button(this);
        extensions.setText("扩展");
        extensions.setAllCaps(false);
        extensions.setOnClickListener(v -> startActivity(new Intent(this, ExtensionsActivity.class)));
        toolbar.addView(extensions, new LinearLayout.LayoutParams(dp(76), dp(48)));

        GeckoView webView = new GeckoView(this);
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));
        setContentView(root);

        session = new GeckoSession();
        session.setContentDelegate(new GeckoSession.ContentDelegate() {});
        session.open(runtime);
        webView.setSession(session);

        String shared = extractSharedText(getIntent());
        if (shared != null && !shared.isBlank()) {
            addressBar.setText(shared);
            loadInput(shared);
        } else {
            session.loadUri("https://www.google.com");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String shared = extractSharedText(intent);
        if (shared != null && !shared.isBlank() && session != null) {
            addressBar.setText(shared);
            loadInput(shared);
        }
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
        }
        super.onDestroy();
    }

    private void loadInput(String raw) {
        String input = raw == null ? "" : raw.trim();
        if (input.isEmpty()) return;

        Uri parsed = Uri.parse(input);
        if (parsed.getScheme() == null) {
            if (input.contains(" ") || !input.contains(".")) {
                input = "https://www.google.com/search?q=" + Uri.encode(input);
            } else {
                input = "https://" + input;
            }
        }
        session.loadUri(input);
    }

    private static String extractSharedText(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return null;
        CharSequence value = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (value == null) return null;

        String text = value.toString().trim();
        int start = text.indexOf("http://");
        if (start < 0) start = text.indexOf("https://");
        if (start >= 0) {
            int end = text.indexOf(' ', start);
            return end > start ? text.substring(start, end) : text.substring(start);
        }
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
