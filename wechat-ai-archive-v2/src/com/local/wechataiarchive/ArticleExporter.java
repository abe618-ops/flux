package com.local.wechataiarchive;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.*;

final class ArticleExporter {
    interface Progress {void update(String message);}
    private final Context context;
    private final Progress progress;
    private final List<byte[]> images=new ArrayList<>();
    private final List<String> warnings=new ArrayList<>();
    private final List<String> originalUrls=new ArrayList<>();
    private int downloaded=0;
    ArticleExporter(Context c,Progress p){context=c.getApplicationContext();progress=p;}
    private void check()throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException("任务已取消");}
    JSONObject generate(String raw)throws Exception{
        JSONArray articles=new JSONArray(raw);if(articles.length()==0||articles.length()>50)throw new IOException("每次支持 1—50 篇文章");
        String title=articles.length()==1?articles.getJSONObject(0).optString("title","微信文章"):articles.getJSONObject(0).optString("title","文章")+"等"+articles.length()+"篇合集";
        String id=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+"_"+UUID.randomUUID().toString().substring(0,8);
        File dir=new File(new File(context.getFilesDir(),"exports"),id);if(!dir.mkdirs())throw new IOException("无法创建导出目录");
        ExecutorService pool=Executors.newFixedThreadPool(3);
        try{
            StringBuilder md=new StringBuilder(),pdfMd=new StringBuilder(),htmlBody=new StringBuilder(),epubBody=new StringBuilder();JSONArray articleMeta=new JSONArray();long total=0;
            for(int ai=0;ai<articles.length();ai++){
                check();JSONObject a=articles.getJSONObject(ai);String t=a.optString("title","未命名文章"),source=a.optString("source","");String body=a.optString("markdown","");String xhtml=a.optString("xhtml","");JSONArray urls=a.optJSONArray("urls");if(urls==null)urls=new JSONArray();if(urls.length()+images.size()>120)throw new IOException("图片超过 120 张，请分批导出");
                final String ref=source;List<Future<byte[]>> futures=new ArrayList<>();
                for(int i=0;i<urls.length();i++){if(i<3){final String u=urls.optString(i);futures.add(pool.submit(()->normalizeImage(u,ref)));}else futures.add(null);}
                Map<Integer,String> mdRefs=new HashMap<>(),pdfRefs=new HashMap<>();
                String localHtml=xhtml,localEpub=xhtml;int offset=images.size();
                for(int i=0;i<urls.length();i++){
                    check();int global=offset+i;String u=urls.optString(i);originalUrls.add(u);byte[] data=null;
                    progress.update("第 "+(ai+1)+"/"+articles.length()+" 篇 · 图片 "+(i+1)+"/"+urls.length());
                    Future<byte[]> task=futures.get(i);
                    try{data=task.get(65,TimeUnit.SECONDS);if(total+data.length>Math.min(12L*1024*1024,Runtime.getRuntime().maxMemory()/24))throw new IOException("本次图文图片较多，请分批导出以避免内存不足");write(new File(dir,imageName(global)),data);total+=data.length;downloaded++;}
                    catch(Exception e){task.cancel(true);if(e instanceof InterruptedException)throw e;data=null;warnings.add("第 "+(ai+1)+" 篇图 "+(i+1)+"未下载："+reason(e));}
                    futures.set(i,null);if(i+3<urls.length()){final String nextUrl=urls.optString(i+3);futures.set(i+3,pool.submit(()->normalizeImage(nextUrl,ref)));}
                    images.add(data);String local=data==null?u:imageName(global);
                    mdRefs.put(i,local);pdfRefs.put(i,"__WXIMG_"+global+"__");
                    String encoded=xml(u);String embedded=data==null?u:"data:image/jpeg;base64,"+Base64.encodeToString(data,Base64.NO_WRAP);
                    localHtml=localHtml.replace("src=\""+encoded+"\"","src=\""+embedded+"\"");
                    localEpub=localEpub.replace("src=\""+encoded+"\"","src=\""+(data==null?u:"images/"+imageName(global))+"\"");
                }
                String localMd=replaceRefs(body,mdRefs),pdfBody=replaceRefs(body,pdfRefs);
                md.append("## ").append(t).append("\n\n来源：").append(source).append("\n\n").append(localMd).append("\n\n");
                pdfMd.append("## ").append(t).append("\n\n来源：").append(source).append("\n\n").append(pdfBody).append("\n\n");
                htmlBody.append("<article><h2>").append(xml(t)).append("</h2><p class=\"source\">").append(xml(source)).append("</p>").append(localHtml).append("</article>");
                epubBody.append("<section><h2>").append(xml(t)).append("</h2><p>").append(xml(source)).append("</p>").append(localEpub).append("</section>");
                articleMeta.put(new JSONObject().put("title",t).put("source",source).put("images",urls.length()));
            }
            check();pool.shutdownNow();progress.update("生成 Markdown、图文 PDF 和图片包…");
            String content="# "+title+"\n\n"+md;
            if(!warnings.isEmpty())content+="\n## 导出提示\n\n"+joinWarnings(warnings)+"\n\n部分图片未下载，MD 保留其原始地址，PDF 中已注明。\n";
            String front="---\ntitle: "+JSONObject.quote(title)+"\ncreated: "+new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(new Date())+"\nformat_version: 2\n---\n\n";
            write(new File(dir,"article.md"),front+content);write(new File(dir,"article.txt"),content);
            write(new File(dir,"article.html"),"<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>"+xml(title)+"</title><style>body{max-width:850px;margin:auto;padding:24px;font:16px/1.85 sans-serif;color:#243a32}img{max-width:100%;height:auto}table{border-collapse:collapse}td,th{border:1px solid #ccc;padding:6px}pre{white-space:pre-wrap}blockquote{border-left:3px solid #348068;padding-left:15px}.source{font-size:12px;overflow-wrap:anywhere}</style></head><body><h1>"+xml(title)+"</h1>"+htmlBody+"</body></html>");
            int pages=makePdf(new File(dir,"article.pdf"),title,pdfMd.toString());
            write(new File(dir,"article.epub"),makeEpub(title,epubBody.toString()));
            String help="本包包含 article.md（正文）、article.txt（兼容文本）、article.pdf（图文一体）、article.html（内嵌图片）、article.epub 和 image_*.jpg。\n\nMarkdown 相对路径图片需与 MD 保持在同一目录。ZIP 不保证 AI 可直接读取；请解压或在应用中选择 PDF。\nChatGPT 普通套餐可能不读取 PDF 内图片，请用“正文＋图片”将正文与原图分开附加。\n单篇长截图在 PDF 中分页保留，动态视频仅保留页面可见文字。\n";
            write(new File(dir,"README.txt"),help);
            JSONObject meta=new JSONObject().put("id",id).put("title",title).put("created",new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date())).put("pages",pages).put("imageCount",downloaded).put("imageTotal",images.size()).put("articles",articleMeta).put("warnings",new JSONArray(warnings)).put("pdfBytes",new File(dir,"article.pdf").length());
            write(new File(dir,"metadata.json"),meta.toString(2));
            try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(new File(dir,"article.zip")))){for(File f:dir.listFiles()){if(f.getName().endsWith(".zip"))continue;z.putNextEntry(new ZipEntry(f.getName()));try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)z.write(b,0,n);}z.closeEntry();}}
            check();return meta;
        }catch(Exception e){for(File f:dir.listFiles())f.delete();dir.delete();throw e;}finally{pool.shutdownNow();images.clear();}
    }
    private static String reason(Exception e){Throwable c=e instanceof ExecutionException?e.getCause():e;return c==null||c.getMessage()==null?"请求失败或超时":c.getMessage();}
    private static byte[] normalizeImage(String url,String source)throws Exception{
        byte[] raw;
        if("local-self-test".equals(source)&&"https://app.local/test-image".equals(url)){
            Bitmap b=Bitmap.createBitmap(900,3600,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.rgb(238,247,241));Paint p=new Paint(3);p.setColor(Color.rgb(25,85,64));p.setTextSize(48);for(int i=0;i<12;i++){c.drawText("图文分享测试 · 第 "+(i+1)+" 段",45,120+i*290,p);p.setColor(i%2==0?Color.rgb(42,123,98):Color.rgb(128,168,149));c.drawRect(45,150+i*290,850,270+i*290,p);p.setColor(Color.rgb(25,85,64));}ByteArrayOutputStream o=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,88,o);b.recycle();return o.toByteArray();
        }
        raw=Network.get(url,8*1024*1024,source).bytes;
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(raw,0,raw.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0||((long)bounds.outWidth*bounds.outHeight)>160000000)throw new IOException("图片格式不支持或尺寸过大");
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;long pixelBudget=Math.min(8000000L,Runtime.getRuntime().maxMemory()/36);while(bounds.outWidth/opts.inSampleSize>2200||bounds.outHeight/opts.inSampleSize>16000||((long)bounds.outWidth*bounds.outHeight)/(opts.inSampleSize*opts.inSampleSize)>pixelBudget)opts.inSampleSize*=2;
        Bitmap b=BitmapFactory.decodeByteArray(raw,0,raw.length,opts);if(b==null)throw new IOException("图片解码失败");try{Bitmap flattened=Bitmap.createBitmap(b.getWidth(),b.getHeight(),Bitmap.Config.RGB_565);try{Canvas canvas=new Canvas(flattened);canvas.drawColor(Color.WHITE);canvas.drawBitmap(b,0,0,new Paint(3));ByteArrayOutputStream o=new ByteArrayOutputStream();flattened.compress(Bitmap.CompressFormat.JPEG,90,o);return o.toByteArray();}finally{flattened.recycle();}}finally{b.recycle();}
    }
    static String replaceRefs(String body,Map<Integer,String> refs){Matcher m=Pattern.compile("__WXIMG_(\\d+)__").matcher(body);StringBuffer out=new StringBuffer();while(m.find()){String v=refs.get(Integer.parseInt(m.group(1)));m.appendReplacement(out,Matcher.quoteReplacement(v==null?"图片未下载":v));}m.appendTail(out);return out.toString();}
    static String joinWarnings(List<String> list){StringBuilder b=new StringBuilder();for(String s:list){if(b.length()>0)b.append("\n\n");b.append(s);}return b.toString();}
    static String imageName(int i){return String.format(Locale.US,"image_%03d.jpg",i+1);}
    private int makePdf(File file,String title,String md)throws Exception{
        try(PdfWriter writer=new PdfWriter()){writer.text(title,20,true);Pattern img=Pattern.compile("!\\[([^]]*)\\]\\(__WXIMG_(\\d+)__\\)");for(String line:md.split("\\r?\\n",-1)){check();Matcher m=img.matcher(line);int end=0;while(m.find()){writer.markdown(line.substring(end,m.start()));int n=Integer.parseInt(m.group(2));writer.text("图 "+(n+1)+(m.group(1).trim().isEmpty()?"":" · "+m.group(1).trim()),10,false);if(n<images.size()&&images.get(n)!=null)writer.image(images.get(n));else writer.text("该图片下载失败，请参照 MD 中的来源地址。",11,false);end=m.end();}writer.markdown(line.substring(end));}if(!warnings.isEmpty()){writer.text("导出提示",16,true);for(String w:warnings)writer.text(w,10,false);}return writer.finish(file);}
    }
    private static final class PdfWriter implements AutoCloseable{
        final PdfDocument doc=new PdfDocument();PdfDocument.Page page;Canvas canvas;float y;int number;final Paint p=new Paint(3);static final int W=595,H=842,L=40,R=40,T=50,B=45;
        PdfWriter(){start();}
        void finishPage(){if(page!=null){p.setColor(Color.GRAY);p.setTextSize(9);p.setTypeface(Typeface.DEFAULT);canvas.drawText("微信图文转AI · "+number,L,H-22,p);doc.finishPage(page);page=null;}}
        void start(){finishPage();number++;page=doc.startPage(new PdfDocument.PageInfo.Builder(W,H,number).create());canvas=page.getCanvas();canvas.drawColor(Color.WHITE);y=T;}
        void markdown(String s){s=s.trim();if(s.isEmpty()){y+=5;return;}boolean h=s.matches("^#{1,6}\\s.*");s=s.replaceFirst("^#{1,6}\\s*","").replaceAll("\\*\\*|`","").replaceAll("\\[([^]]*)\\]\\(([^)]*)\\)","$1 ($2)");text(s,h?16:11,h);}
        void text(String s,float size,boolean bold){if(s==null||s.isEmpty())return;for(String line:s.split("\\n",-1)){int at=0;while(at<line.length()){p.setTextSize(size);p.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);p.setColor(Color.rgb(35,53,44));int n=p.breakText(line,at,line.length(),true,W-L-R,null);if(n<=0)n=1;if(at+n<line.length()&&Character.isHighSurrogate(line.charAt(at+n-1)))n=Math.max(1,n-1);if(y+size+6>H-B){start();p.setTextSize(size);p.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);p.setColor(Color.rgb(35,53,44));}canvas.drawText(line,at,at+n,L,y,p);at+=n;y+=size+7;}y+=3;}}
        void image(byte[] bytes)throws IOException{Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(b==null)throw new IOException("PDF 图片解码失败");try{float scale=Math.min(1f,(W-L-R)/(float)b.getWidth());int top=0;while(top<b.getHeight()){if(H-B-y<60)start();int take=Math.min(b.getHeight()-top,(int)((H-B-y)/scale));if(take<=0){start();continue;}float height=take*scale;p.setColor(Color.WHITE);canvas.drawBitmap(b,new Rect(0,top,b.getWidth(),top+take),new RectF(L,y,L+b.getWidth()*scale,y+height),p);top+=take;y+=height+10;if(top<b.getHeight())start();}}finally{b.recycle();}}
        int finish(File f)throws IOException{finishPage();try(OutputStream o=new FileOutputStream(f)){doc.writeTo(o);}return number;}
        public void close(){finishPage();doc.close();}
    }
    private byte[] makeEpub(String title,String body)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){
            byte[] mime="application/epub+zip".getBytes(StandardCharsets.US_ASCII);ZipEntry entry=new ZipEntry("mimetype");entry.setMethod(ZipEntry.STORED);entry.setSize(mime.length);CRC32 crc=new CRC32();crc.update(mime);entry.setCrc(crc.getValue());z.putNextEntry(entry);z.write(mime);z.closeEntry();
            put(z,"META-INF/container.xml","<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
            StringBuilder manifest=new StringBuilder("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/><item id=\"chapter\" href=\"chapter.xhtml\" media-type=\"application/xhtml+xml\"/>");for(int i=0;i<images.size();i++)if(images.get(i)!=null){manifest.append("<item id=\"img").append(i).append("\" href=\"images/").append(imageName(i)).append("\" media-type=\"image/jpeg\"/>");put(z,"OEBPS/images/"+imageName(i),images.get(i));}
            put(z,"OEBPS/content.opf","<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"id\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"id\">urn:uuid:"+UUID.randomUUID()+"</dc:identifier><dc:title>"+xml(title)+"</dc:title><dc:language>zh-CN</dc:language><meta property=\"dcterms:modified\">"+new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US){{setTimeZone(TimeZone.getTimeZone("UTC"));}}.format(new Date())+"</meta></metadata><manifest>"+manifest+"</manifest><spine><itemref idref=\"chapter\"/></spine></package>");
            put(z,"OEBPS/nav.xhtml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\"><head><title>目录</title></head><body><nav epub:type=\"toc\"><ol><li><a href=\"chapter.xhtml\">"+xml(title)+"</a></li></ol></nav></body></html>");
            put(z,"OEBPS/chapter.xhtml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>"+xml(title)+"</title><style>body{line-height:1.7}img{max-width:100%;height:auto}</style></head><body>"+body+"</body></html>");
        }return out.toByteArray();
    }
    private static void put(ZipOutputStream z,String path,String data)throws IOException{put(z,path,data.getBytes(StandardCharsets.UTF_8));}
    private static void put(ZipOutputStream z,String path,byte[] data)throws IOException{z.putNextEntry(new ZipEntry(path));z.write(data);z.closeEntry();}
    static void write(File file,String data)throws IOException{write(file,data.getBytes(StandardCharsets.UTF_8));}
    static void write(File file,byte[] data)throws IOException{try(FileOutputStream o=new FileOutputStream(file)){o.write(data);}}
    static String xml(String text){return(text==null?"":text).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
