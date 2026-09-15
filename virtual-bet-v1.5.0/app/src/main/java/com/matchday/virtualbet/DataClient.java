package com.matchday.virtualbet;

import java.net.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import org.json.*;

/** Bounded HTTP requests. A page refresh never cancels the result-sync connections. */
public final class DataClient {
    public static final class Scope {
        volatile boolean cancelled;
        final Set<HttpURLConnection> connections=Collections.newSetFromMap(new ConcurrentHashMap<HttpURLConnection,Boolean>());
        public void cancel(){cancelled=true;for(HttpURLConnection c:connections)c.disconnect();connections.clear();}
        public void check()throws IOException{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("请求已取消");}
    }
    public static String get(String url,String charset,Scope scope)throws IOException {
        long until=System.currentTimeMillis()+14000;scope.check();HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();scope.connections.add(c);
        try{
            c.setConnectTimeout(5000);c.setReadTimeout(6500);c.setUseCaches(false);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
            c.setRequestProperty("Accept","application/json,text/html,*/*");c.setRequestProperty("Accept-Encoding","gzip");
            String host=new URL(url).getHost();c.setRequestProperty("Referer",host.contains("sina")?"https://sports.sina.com.cn/":host.contains("500")?"https://trade.500.com/":"https://www.titan007.com/");
            int status=c.getResponseCode();if(status!=200)throw new IOException("来源响应 "+status);
            if(!"https".equals(c.getURL().getProtocol()))throw new IOException("不支持非加密重定向");
            InputStream raw=c.getInputStream();if("gzip".equalsIgnoreCase(c.getContentEncoding()))raw=new GZIPInputStream(raw);
            try(InputStream in=raw;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){scope.check();if(System.currentTimeMillis()>until)throw new IOException("请求超时");if(out.size()+n>4*1024*1024)throw new IOException("来源数据过大");out.write(b,0,n);}
                String ct=c.getContentType();String cs=charset;if(ct!=null){Matcher m=Pattern.compile("(?i)charset=([A-Za-z0-9_-]+)").matcher(ct);if(m.find())cs=m.group(1);}
                String text=new String(out.toByteArray(),Charset.forName(cs));scope.check();return text;
            }
        }finally{scope.connections.remove(c);c.disconnect();}
    }
    public static String sourceName(String kind,int mode,int source){
        if("fixture".equals(kind)){if(mode==0)return source==0?"新浪竞彩":"500竞彩";return source==0?"500北单":"球探北单";}
        if(mode==0)return source==0?"球探赛果":source==1?"新浪赛果":"500赛果";return source==0?"球探赛果":"备用赛果";
    }
    public static String sinaUrl(String k){return "https://mix.lottery.sina.com.cn/gateway/index/entry?format=json&__caller__=web&__version__=1.0.0&__verno__=1&cat1=jczqMatches&gameTypes=spf&date="+FootballData.iso(k)+"&isPrized=&isAll=1&dpc=1";}
    public static String fiveUrl(String k){return "https://trade.500.com/jczq/?date="+FootballData.iso(k)+"&playid=312&g=2";}
    public static String titanUrl(String k){return "https://bf.titan007.com/football/Over_"+k+".htm";}
    public static ArrayList<JSONObject> fixtures(int mode,int source,String k,Scope scope)throws Exception{
        if(mode==0)return source==0?FootballData.sina(get(sinaUrl(k),"UTF-8",scope),k):FootballData.five(get(fiveUrl(k),"GB18030",scope),k);
        if(source==0)return FootballData.beidan(get("https://trade.500.com/bjdc/","GB18030",scope),k);
        String url=k.compareTo(FootballData.today())>=0?"https://bf.titan007.com/football/Next_"+k+".htm":titanUrl(k);
        return FootballData.titan(get(url,"GB18030",scope),k,1);
    }
    public static ArrayList<JSONObject> results(String k,int mode,int source,Scope scope)throws Exception{
        ArrayList<JSONObject> fixtures=source==0?FootballData.titan(get(titanUrl(k),"GB18030",scope),k,mode):source==1?FootballData.sina(get(sinaUrl(k),"UTF-8",scope),k):FootballData.five(get(fiveUrl(k),"GB18030",scope),k);
        ArrayList<JSONObject> out=new ArrayList<>();for(JSONObject f:fixtures){JSONObject r=f.optJSONObject("result");if(r!=null)out.add(r);}return out;
    }
    public static JSONObject euro(String id,long kickoff,Scope scope)throws Exception{
        if(!id.matches("\\d+"))throw new IOException("缺球探赛程编号");return parseEuro(get("https://txt.titan007.com/1x2/"+id+".js","UTF-8",scope),kickoff);
    }
    public static JSONObject parseEuro(String js,long kickoff)throws JSONException{
        Matcher arr=Pattern.compile("(?s)(?:var\\s+)?gameDetail\\s*=\\s*(?:new\\s+)?Array\\((.*?)\\);?").matcher(js);if(!arr.find())return null;
        ArrayList<String> items=new ArrayList<>();Matcher q=Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(arr.group(1));while(q.find())items.add(q.group(1));
        String[] ids={"","",""},names={"澳门","Bet365","12BET"};
        Matcher games=Pattern.compile("(?s)\\bgame\\s*=\\s*Array\\((.*?)\\);").matcher(js);
        if(games.find()){Matcher item=Pattern.compile("\"([^\"]*)\"").matcher(games.group(1));while(item.find()){
            String[] cols=item.group(1).split("\\|",-1);if(cols.length<3)continue;String name=cols[2].toLowerCase(Locale.ROOT).replace(" ","");
            int ix=name.equals("macauslot")?0:name.equals("bet365")?1:name.equals("12bet")?2:-1;if(ix>=0)ids[ix]=cols[1];
        }}
        for(int p=0;p<ids.length;p++)for(String item:items){if(!item.startsWith(ids[p]+"^"))continue;long latest=Long.MIN_VALUE;JSONObject best=null;
            for(String row:item.substring(item.indexOf('^')+1).split(";")){
                String[] fields=row.split("\\|",-1);if(fields.length<4)continue;try{
                    double h=Double.parseDouble(fields[0]),d=Double.parseDouble(fields[1]),a=Double.parseDouble(fields[2]);if(!FootballData.validOdd(h)||!FootballData.validOdd(d)||!FootballData.validOdd(a))continue;
                    String year=fields.length>7&&fields[7].matches("\\d{4}")?fields[7]:kickoff>0?FootballData.date(kickoff).substring(0,4):"";
                    Matcher t=Pattern.compile("(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}:\\d{2})").matcher(fields[3]);if(!t.find()||year.isEmpty())continue;
                    String dt=String.format(Locale.US,"%s-%02d-%02d %s",year,Integer.parseInt(t.group(1)),Integer.parseInt(t.group(2)),t.group(3));long at=FootballData.time(dt);
                    if(at<=0||kickoff<=0||at>kickoff||at<kickoff-400L*24*3600000)continue;
                    if(at>=latest){latest=at;JSONObject prices=new JSONObject();prices.put("胜",h);prices.put("平",d);prices.put("负",a);best=new JSONObject();best.put("prices",prices);best.put("source",names[p]+" · 赛前欧赔参考（不让球）");best.put("at",at);}
                }catch(Exception ignored){}
            }if(best!=null)return best;
        }return null;
    }
    public static String error(Exception e){String s=e.getMessage();if(s==null)s=e.getClass().getSimpleName();if(s.contains("resolve"))return "域名暂不可达";if(s.contains("timed out"))return "网络超时";return s.length()>70?s.substring(0,70):s;}
}
