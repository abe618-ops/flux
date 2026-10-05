package com.local.wechataiarchive;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class Network {
    static final String UA="Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36";
    static final class Result{final byte[] bytes;final String url;Result(byte[] b,String u){bytes=b;url=u;}String text(){return new String(bytes,StandardCharsets.UTF_8);}}
    static void check(URL u)throws Exception{
        if(!"https".equalsIgnoreCase(u.getProtocol())||u.getUserInfo()!=null||u.getHost().isEmpty()||(u.getPort()!=-1&&u.getPort()!=443))throw new IOException("请使用公开 HTTPS 链接");
        for(InetAddress a:InetAddress.getAllByName(u.getHost())){byte[] b=a.getAddress();if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress()||(b.length==4&&(b[0]&255)==100&&(b[1]&255)>=64&&(b[1]&255)<=127)||(b.length==16&&(b[0]&254)==252))throw new IOException("不支持本机或局域网链接");}
    }
    static Result get(String raw,int max,String referer)throws Exception{
        URL u=new URL(raw.trim());
        for(int i=0;i<5;i++){
            check(u);HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestMethod("GET");c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Accept-Language","zh-CN,zh;q=0.9");if(referer!=null&&referer.startsWith("https://"))c.setRequestProperty("Referer",referer);
            try{int status=c.getResponseCode();if(status>=300&&status<400){String loc=c.getHeaderField("Location");if(loc==null)throw new IOException("跳转地址为空");u=new URL(u,loc);continue;}if(status<200||status>=300)throw new IOException("网页返回 HTTP "+status);if(c.getContentLength()>max)throw new IOException("文件过大");return new Result(read(c.getInputStream(),max),u.toString());}finally{c.disconnect();}
        }throw new IOException("跳转次数过多");
    }
    static byte[] read(InputStream in,int max)throws IOException{try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n,total=0;while((n=input.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("任务已取消");total+=n;if(total>max)throw new IOException("内容超过大小限制");out.write(b,0,n);}return out.toByteArray();}}
}
