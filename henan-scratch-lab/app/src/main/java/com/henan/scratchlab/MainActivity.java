package com.henan.scratchlab;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;
import java.security.SecureRandom;

public final class MainActivity extends Activity {
    private WebView web;
    private final SecureRandom rng = new SecureRandom();

    public final class LocalBridge {
        @JavascriptInterface public int randomInt(int bound) {
            if (bound < 1 || bound > 1000000000) return 0;
            return rng.nextInt(bound);
        }
        @JavascriptInterface public void copyText(String text) {
            final String safe = text == null ? "" : text;
            runOnUiThread(() -> {
                ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if(cb!=null) cb.setPrimaryClip(ClipData.newPlainText("河南刮刮乐研究记录",safe));
                Toast.makeText(MainActivity.this,"研究记录已复制",Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        web.addJavascriptInterface(new LocalBridge(), "LocalBridge");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }
    @Override public void onBackPressed() {
        if(web!=null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if(web!=null){web.removeJavascriptInterface("LocalBridge");web.destroy();web=null;}
        super.onDestroy();
    }
}
