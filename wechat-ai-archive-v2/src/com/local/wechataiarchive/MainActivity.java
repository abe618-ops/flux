package com.local.wechataiarchive;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.graphics.Color;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private WebView web;
    private Intent pending;
    private volatile Future<?> currentJob;
    private File pendingSave;
    private static final int SAVE_DOCUMENT=301;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);pending=getIntent();
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(237,247,241));root.setOnApplyWindowInsetsListener((v,in)->{v.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in;});
        web=new WebView(this);root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);getWindow().setStatusBarColor(Color.rgb(237,247,241));getWindow().setNavigationBarColor(Color.rgb(237,247,241));getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);root.requestApplyInsets();
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);web.addJavascriptInterface(new Bridge(),"NativeBridge");
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return asset(r.getUrl());}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,String u){return asset(Uri.parse(u));}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public boolean shouldOverrideUrlLoading(WebView v,String u){return true;}
        });web.loadUrl("https://app.local/assets/index.html");
    }
    private WebResourceResponse asset(Uri uri){String path=uri.getPath();if(!"https".equals(uri.getScheme())||!"app.local".equals(uri.getHost())||path==null||!path.startsWith("/assets/"))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));String n=path.substring(8);if(!Arrays.asList("index.html","style.css","core.js","ui.js").contains(n))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));try{return new WebResourceResponse(n.endsWith(".html")?"text/html":n.endsWith(".css")?"text/css":"application/javascript","UTF-8",getAssets().open(n));}catch(IOException e){return null;}}
    @Override public void onNewIntent(Intent i){super.onNewIntent(i);pending=i;setIntent(i);if(web!=null)web.evaluateJavascript("window.receiveShare&&window.receiveShare()",null);}
    public final class Bridge {
        @JavascriptInterface public synchronized String getSharedPayload(){try{Intent i=pending;pending=null;if(i==null)return "{}";CharSequence t=i.getCharSequenceExtra(Intent.EXTRA_TEXT),h=i.getCharSequenceExtra(Intent.EXTRA_HTML_TEXT);return new JSONObject().put("text",t==null?"":t.toString()).put("html",h==null?"":h.toString()).put("url",i.getData()==null?"":i.getData().toString()).toString();}catch(Exception e){return "{}";}}
        @JavascriptInterface public void fetchArticle(String url,String cb){worker.submit(()->{try{Network.Result r=Network.get(url,10*1024*1024,null);respond(cb,new JSONObject().put("ok",true).put("url",r.url).put("html",r.text()));}catch(Exception e){failure(cb,e);}});}
        @JavascriptInterface public void exportArticles(String articles,String cb){if(currentJob!=null&&!currentJob.isDone()){failure(cb,new IOException("正在转换，请等待当前任务完成"));return;}currentJob=worker.submit(()->{try{JSONObject meta=new ArticleExporter(getApplicationContext(),MainActivity.this::progress).generate(articles);respond(cb,new JSONObject().put("ok",true).put("export",meta));}catch(Exception e){failure(cb,e);}});}
        @JavascriptInterface public void cancel(){Future<?> f=currentJob;if(f!=null)f.cancel(true);}
        @JavascriptInterface public String history(){JSONArray out=new JSONArray();File root=new File(getFilesDir(),"exports");File[] dirs=root.listFiles();if(dirs==null)return "[]";Arrays.sort(dirs,(a,b)->b.getName().compareTo(a.getName()));for(File d:dirs){if(out.length()>=30)break;try{File meta=new File(d,"metadata.json");if(meta.isFile()&&new File(d,"article.zip").isFile())out.put(new JSONObject(new String(Network.read(new FileInputStream(meta),128*1024),StandardCharsets.UTF_8)));}catch(Exception ignored){}}return out.toString();}
        @JavascriptInterface public void share(String id,String format){runOnUiThread(()->shareFile(id,format));}
        @JavascriptInterface public void shareImages(String id,int start){runOnUiThread(()->shareImagesNow(id,start));}
        @JavascriptInterface public void saveDownloads(String id,String cb){worker.submit(()->{try{File dir=exportDir(id);if(getPreferences(MODE_PRIVATE).getBoolean("saved."+id,false)){respond(cb,new JSONObject().put("ok",true).put("message","此文件已保存到 下载/微信图文转AI/"+id));return;}if(Build.VERSION.SDK_INT<29){File zip=new File(dir,"article.zip");runOnUiThread(()->{pendingSave=zip;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"微信图文_"+id+".zip");startActivityForResult(i,SAVE_DOCUMENT);});respond(cb,new JSONObject().put("ok",true).put("message","请选择 ZIP 保存位置，解压后可得到所有文件"));return;}String rel=Environment.DIRECTORY_DOWNLOADS+"/微信图文转AI/"+id+"/";for(File f:dir.listFiles()){ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,f.getName());v.put(MediaStore.Downloads.MIME_TYPE,mime(f.getName()));v.put(MediaStore.Downloads.RELATIVE_PATH,rel);v.put(MediaStore.Downloads.IS_PENDING,1);Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("无法创建下载文件");try(OutputStream o=getContentResolver().openOutputStream(u);InputStream in=new FileInputStream(f)){if(o==null)throw new IOException("无法写入下载文件");copy(in,o);}catch(Exception e){getContentResolver().delete(u,null,null);throw e;}ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(u,done,null,null);}getPreferences(MODE_PRIVATE).edit().putBoolean("saved."+id,true).apply();respond(cb,new JSONObject().put("ok",true).put("message","已保存到 下载/微信图文转AI/"+id));}catch(Exception e){failure(cb,e);}});}
        @JavascriptInterface public void copyBody(String id){worker.submit(()->{try{String body=new String(Network.read(new FileInputStream(new File(exportDir(id),"article.txt")),2*1024*1024),StandardCharsets.UTF_8);runOnUiThread(()->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("文章正文",body));toast("正文已复制，可粘贴到 AI");});}catch(Exception e){toast(e.getMessage());}});}
    }
    private File exportDir(String id)throws IOException{if(id==null||!id.matches("[A-Za-z0-9_-]+"))throw new IOException("导出编号无效");File d=new File(new File(getFilesDir(),"exports"),id);if(!new File(d,"metadata.json").isFile())throw new IOException("文件已不可用，请重新转换");return d;}
    private static String filename(String f){switch(f){case "pdf":return "article.pdf";case "md":return "article.md";case "txt":return "article.txt";case "zip":return "article.zip";case "html":return "article.html";case "epub":return "article.epub";default:return "article.pdf";}}
    private static String mime(String n){if(n.endsWith(".pdf"))return "application/pdf";if(n.endsWith(".md"))return "text/plain";if(n.endsWith(".txt"))return "text/plain";if(n.endsWith(".zip"))return "application/zip";if(n.endsWith(".html"))return "text/html";if(n.endsWith(".epub"))return "application/epub+zip";if(n.endsWith(".jpg"))return "image/jpeg";return "application/json";}
    private void shareFile(String id,String format){try{File dir=exportDir(id);String name=filename(format);File file=new File(dir,name);if(!file.isFile())throw new IOException("文件不存在");Uri uri=ShareProvider.uri(id,name);Intent send=new Intent(Intent.ACTION_SEND).setType(mime(name)).putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_SUBJECT,"微信文章文件").putExtra(Intent.EXTRA_TITLE,name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri(name,uri));startActivity(Intent.createChooser(send,"发送文件到 ChatGPT、Claude 或其他应用"));}catch(Exception e){shareFallback(e);}}
    private void shareImagesNow(String id,int start){try{File dir=exportDir(id);List<File> files=new ArrayList<>();for(File f:dir.listFiles())if(f.getName().startsWith("image_")&&f.getName().endsWith(".jpg"))files.add(f);Collections.sort(files,(a,b)->a.getName().compareTo(b.getName()));if(files.isEmpty())throw new IOException("本次没有成功下载的图片");int at=Math.max(0,start);if(at>=files.size())throw new IOException("所有图片已选择，可从第一组再次分享");ArrayList<Uri> uris=new ArrayList<>();for(int i=at;i<Math.min(at+8,files.size());i++)uris.add(ShareProvider.uri(id,files.get(i).getName()));Intent send=new Intent(uris.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_TEXT,"与刚才的文章正文一起分析。这是原图第 "+(at+1)+"—"+(at+uris.size())+"张。");if(uris.size()==1)send.putExtra(Intent.EXTRA_STREAM,uris.get(0));else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);ClipData clips=ClipData.newRawUri("文章原图",uris.get(0));for(int i=1;i<uris.size();i++)clips.addItem(new ClipData.Item(uris.get(i)));send.setClipData(clips);startActivity(Intent.createChooser(send,"发送原图 · 同一 AI 对话中添加"));}catch(Exception e){shareFallback(e);}}
    private void shareFallback(Exception e){new AlertDialog.Builder(this).setTitle("无法直接分享").setMessage((e.getMessage()==null?"接收应用不支持该文件分享方式":e.getMessage())+"\n可点“保存到下载”，再在 ChatGPT 或 Claude 中用附件按钮选择文件。").setPositiveButton("知道了",null).show();}
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==SAVE_DOCUMENT&&result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingSave!=null){final File f=pendingSave;final Uri u=data.getData();pendingSave=null;worker.submit(()->{try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(u)){if(out==null)throw new IOException("无法写入目标文件");copy(in,out);toast("图文 ZIP 已保存");}catch(Exception e){toast("保存失败："+e.getMessage());}});}}
    private void progress(String m){runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window.nativeProgress&&window.nativeProgress("+JSONObject.quote(m)+")",null);});}
    private void respond(String cb,JSONObject result){runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window.NativeCallbacks&&window.NativeCallbacks["+JSONObject.quote(cb)+"]&&window.NativeCallbacks["+JSONObject.quote(cb)+"]("+result+")",null);});}
    private void failure(String cb,Exception e){try{respond(cb,new JSONObject().put("ok",false).put("error",e.getMessage()==null?"处理失败，请重试":e.getMessage()));}catch(JSONException ignored){}}
    private void toast(String s){runOnUiThread(()->Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show());}
    @Override protected void onDestroy(){if(currentJob!=null&&!currentJob.isDone())currentJob.cancel(true);worker.shutdownNow();if(web!=null){web.removeJavascriptInterface("NativeBridge");web.destroy();web=null;}super.onDestroy();}
}
