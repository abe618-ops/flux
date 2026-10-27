package com.abe618.ceshuyingqi;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
public final class MainActivity extends Activity {
 private static final int EXPORT_JSON=1907;
 private WebView web;private String pending;
 @Override public void onCreate(Bundle state){super.onCreate(state);
  web=new WebView(this);
  WebSettings settings=web.getSettings();
  settings.setJavaScriptEnabled(true);
  settings.setAllowContentAccess(false);
  settings.setAllowFileAccess(true);
  settings.setAllowFileAccessFromFileURLs(false);
  settings.setAllowUniversalAccessFromFileURLs(false);
  settings.setBlockNetworkLoads(true);
  settings.setDomStorageEnabled(false);
  settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
  web.addJavascriptInterface(new Bridge(),"AndroidBridge");
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req){
    Uri uri=req.getUrl();return !"file".equals(uri.getScheme())||!"/android_asset/index.html".equals(uri.getPath());
   }
  });
  setContentView(web);web.loadUrl("file:///android_asset/index.html");
 }
 private final class Bridge{
  @JavascriptInterface public String calculateStalks(String upper,String lower,int moving,String system){
   try{return CeshuCore.calculate(upper,lower,moving,system).toJson();}
   catch(Exception e){return "{\"error\":\"invalid input\"}";}
  }
  @JavascriptInterface public void saveJson(String file,String text){
   runOnUiThread(()->{
    if(text==null||text.length()>3000000){Toast.makeText(MainActivity.this,"数据过长",Toast.LENGTH_SHORT).show();return;}
    pending=text;
    String safe=file==null?"研究记录.json":file.replaceAll("[\\\\/:*?\"<>|]","_");
    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("application/json");
    intent.putExtra(Intent.EXTRA_TITLE,safe);
    startActivityForResult(intent,EXPORT_JSON);
   });
  }
 }
 @Override protected void onActivityResult(int req,int res,Intent data){
  super.onActivityResult(req,res,data);
  if(req!=EXPORT_JSON)return;
  try{
   if(res==RESULT_OK&&data!=null&&data.getData()!=null&&pending!=null){
    try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
     if(out==null)throw new IllegalStateException("无法写入");out.write(pending.getBytes(StandardCharsets.UTF_8));}
    Toast.makeText(this,"记录已导出",Toast.LENGTH_SHORT).show();
   }
  }catch(Exception e){Toast.makeText(this,"导出失败："+e.getMessage(),Toast.LENGTH_LONG).show();}
  finally{pending=null;}
 }
 @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override protected void onDestroy(){if(web!=null){web.destroy();web=null;}super.onDestroy();}
}