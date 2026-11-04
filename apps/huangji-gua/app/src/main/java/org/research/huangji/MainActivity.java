package org.research.huangji;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.content.ClipData;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Standalone offline WebView. No INTERNET permission; only local bundled resources. */
public final class MainActivity extends Activity {
  private static final int SAVE=8011, LOAD=8012;
  private WebView web;
  private ValueCallback<Uri[]> pendingUpload;
  private String pendingJson;

  private final class SaveBridge {
    @JavascriptInterface public void saveJson(String filename, String content) {
      if(content==null || content.length()>4000000) return;
      String safe=(filename==null?"record.json":filename.replaceAll("[^\\p{L}0-9._-]","_")).replace("..","_");
      runOnUiThread(()->{
        pendingJson=content;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,safe.endsWith(".json")?safe:safe+".json");
        startActivityForResult(i,SAVE);
      });
    }
  }
  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    web=new WebView(this); setContentView(web);
    WebSettings s=web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setJavaScriptCanOpenWindowsAutomatically(false);
    s.setSupportMultipleWindows(false);
    s.setAllowFileAccess(true);
    s.setAllowContentAccess(true);
    s.setAllowFileAccessFromFileURLs(false);
    s.setAllowUniversalAccessFromFileURLs(false);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    web.addJavascriptInterface(new SaveBridge(),"AndroidBridge");
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req) {
        return !req.getUrl().toString().startsWith("file:///android_asset/");
      }
    });
    web.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> cb,FileChooserParams p) {
        if(pendingUpload!=null)pendingUpload.onReceiveValue(null);
        pendingUpload=cb;
        Intent i=new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("application/json");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        startActivityForResult(Intent.createChooser(i,"导入赛前及复盘JSON"),LOAD);
        return true;
      }
    });
    web.loadUrl("file:///android_asset/index.html");
  }
  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
    super.onActivityResult(requestCode,resultCode,data);
    if(requestCode==LOAD) {
      if(pendingUpload!=null) {
        Uri[] uris=null;
        if(resultCode==RESULT_OK && data!=null) {
          ClipData clips=data.getClipData();
          if(clips!=null) {
            int n=Math.min(clips.getItemCount(),100);
            uris=new Uri[n];
            for(int j=0;j<n;j++)uris[j]=clips.getItemAt(j).getUri();
          } else if(data.getData()!=null) uris=new Uri[]{data.getData()};
        }
        pendingUpload.onReceiveValue(uris);
        pendingUpload=null;
      }
      return;
    }
    if(requestCode==SAVE) {
      if(resultCode==RESULT_OK && data!=null && data.getData()!=null && pendingJson!=null) {
        try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
          if(out!=null)out.write(pendingJson.getBytes(StandardCharsets.UTF_8));
        } catch(Exception e) {
          Toast.makeText(this,"保存失败："+e.getMessage(),Toast.LENGTH_LONG).show();
        }
      }
      pendingJson=null;
    }
  }
  @Override protected void onDestroy() {
    if(web!=null){web.destroy();web=null;}
    super.onDestroy();
  }
}
