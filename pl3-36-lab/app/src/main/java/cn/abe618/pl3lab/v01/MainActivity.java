package cn.abe618.pl3lab.resonance;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final int SAVE_JSON=1001;
    private WebView webView;
    private String pendingJson;
    private long lastBackMs;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        webView=new WebView(this);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        WebSettings settings=webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        webView.addJavascriptInterface(new Bridge(),"Android");
        getWindow().setStatusBarColor(android.graphics.Color.rgb(11,18,32));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(11,18,32));
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }
    private final class Bridge {
        @JavascriptInterface public void saveJson(String name, String json){
            new Handler(Looper.getMainLooper()).post(()->{
                pendingJson=json;
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("application/json");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE,"PL3-36-backup.json");
                try{startActivityForResult(intent,SAVE_JSON);}catch(Exception e){
                    Toast.makeText(MainActivity.this,"保存失败："+e.getMessage(),Toast.LENGTH_LONG).show();
                }
            });
        }
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=SAVE_JSON||resultCode!=RESULT_OK||data==null||data.getData()==null||pendingJson==null)return;
        try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
            if(out==null)throw new IllegalStateException("无输出流");
            out.write(pendingJson.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this,"已保存数据备份",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"保存失败："+e.getMessage(),Toast.LENGTH_LONG).show();}
        finally{pendingJson=null;}
    }
    @Override public void onBackPressed(){
        long now=System.currentTimeMillis();
        if(now-lastBackMs<1600){super.onBackPressed();return;}
        lastBackMs=now;
        Toast.makeText(this,"再按一次退出",Toast.LENGTH_SHORT).show();
    }
    @Override protected void onDestroy(){if(webView!=null){webView.destroy();webView=null;}super.onDestroy();}
}
