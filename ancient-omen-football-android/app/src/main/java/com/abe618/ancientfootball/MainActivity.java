package com.abe618.ancientfootball;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public final class MainActivity extends Activity {
    private WebView web;
    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        web.addJavascriptInterface(new NativeBridge(this), "AndroidNative");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                Uri url = request.getUrl();
                if ("file".equalsIgnoreCase(url.getScheme()) && "android_asset".equals(url.getHost())) return false;
                if ("file".equalsIgnoreCase(url.getScheme())) return true;
                if ("http".equalsIgnoreCase(url.getScheme()) || "https".equalsIgnoreCase(url.getScheme())) {
                    Intent i = new Intent(Intent.ACTION_VIEW, url);
                    try { startActivity(i); } catch(Exception ignored) {}
                }
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }
    @Override public void onBackPressed() {
        if(web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if(web!=null) { web.removeJavascriptInterface("AndroidNative"); web.destroy(); web=null; }
        super.onDestroy();
    }
}
