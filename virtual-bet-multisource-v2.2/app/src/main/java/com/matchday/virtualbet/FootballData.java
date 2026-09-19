package com.matchday.virtualbet;

import org.json.*;
import java.util.*;
import java.text.*;
import java.util.regex.*;

/** Date-scoped source parsers and decimal-odds simulation. No Android dependencies. */
public final class FootballData {
    private FootballData() {}
    public static final TimeZone TZ = TimeZone.getTimeZone("Asia/Shanghai");
    public static final String[] MARKETS = {"HAD","HHAD","CRS","TTG","HAFU","DS","OU","AH"};
    public static final String[] TITLES = {"胜平负","让球胜平负","比分","总进球","半全场","上下单双","大小球","亚洲让球"};
    public static String title(String m) { for(int i=0;i<MARKETS.length;i++) if(MARKETS[i].equals(m)) return TITLES[i]; return m; }
    public static String[] labels(String m) {
        if(m.equals("HAD"))return new String[]{"胜","平","负"};
        if(m.equals("HHAD"))return new String[]{"让胜","让平","让负"};
        if(m.equals("CRS"))return "1:0 2:0 2:1 3:0 3:1 3:2 4:0 4:1 4:2 5:0 5:1 5:2 胜其他 0:0 1:1 2:2 3:3 平其他 0:1 0:2 1:2 0:3 1:3 2:3 0:4 1:4 2:4 0:5 1:5 2:5 负其他".split(" ");
        if(m.equals("TTG"))return "0球 1球 2球 3球 4球 5球 6球 7+球".split(" ");
        if(m.equals("HAFU"))return "胜/胜 胜/平 胜/负 平/胜 平/平 平/负 负/胜 负/平 负/负".split(" ");
        if(m.equals("DS"))return "上单 上双 下单 下双".split(" ");
        if(m.equals("OU"))return new String[]{"大","小"};
        if(m.equals("AH"))return new String[]{"主","客"};
        return new String[0];
    }
    public static JSONObject copy(JSONObject o) throws JSONException {return new JSONObject(o.toString());}
    public static JSONArray copy(JSONArray o) throws JSONException {return new JSONArray(o.toString());}
    public static String date(long t) {SimpleDateFormat f=new SimpleDateFormat("yyyyMMdd",Locale.US);f.setTimeZone(TZ);return f.format(new Date(t));}
    public static String today(){return date(System.currentTimeMillis());}
    public static String iso(String k){return k.substring(0,4)+"-"+k.substring(4,6)+"-"+k.substring(6);}
    public static String shift(String k,int days){Calendar c=Calendar.getInstance(TZ);c.setTimeInMillis(time(iso(k)+" 12:00:00"));c.add(Calendar.DATE,days);return date(c.getTimeInMillis());}
    public static long time(String s){for(String p:new String[]{"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd HH:mm"})try{SimpleDateFormat f=new SimpleDateFormat(p,Locale.US);f.setTimeZone(TZ);f.setLenient(false);return f.parse(s).getTime();}catch(Exception ignored){}return 0;}
    public static boolean validOdd(double v){return !Double.isNaN(v)&&!Double.isInfinite(v)&&v>1&&v<=100000;}
    public static double money(double v){return Math.round(v*100.0)/100.0;}
    public static int[] score(String s){Matcher m=Pattern.compile("^\\s*(\\d{1,2})\\s*[:：-]\\s*(\\d{1,2})\\s*$").matcher(s==null?"":s);if(!m.matches())return null;int h=Integer.parseInt(m.group(1)),a=Integer.parseInt(m.group(2));return h<=50&&a<=50?new int[]{h,a}:null;}
    public static String clean(String s){return s.replaceAll("(?is)<!--.*?-->|<script\\b.*?</script>|<[^>]+>"," ").replace("&nbsp;"," ").replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replaceAll("\\s+"," ").trim();}
    public static String team(String s){return clean(s).replaceAll("\\[[^]]*\\]","").trim();}
    public static String norm(String s){return team(s==null?"":s).toLowerCase(Locale.ROOT).replaceAll("[\\s\\-_.·•()（）'’]","");}
    public static String attr(String s,String key){Matcher m=Pattern.compile("(?i)(?:^|\\s)"+Pattern.quote(key)+"\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))").matcher(s);if(!m.find())return "";return clean(m.group(1)!=null?m.group(1):m.group(2)!=null?m.group(2):m.group(3));}
    static String meta(String s,String key){Matcher m=Pattern.compile(Pattern.quote(key)+"\\s*:\\s*['\"]([^'\"]*)['\"]").matcher(s);return m.find()?clean(m.group(1)):"";}
    public static JSONObject market(JSONObject f,String m){JSONObject all=f.optJSONObject("markets");JSONObject p=all==null?null:all.optJSONObject(m);return p==null?new JSONObject():p;}
    public static double marketLine(JSONObject f,String m){JSONObject lines=f.optJSONObject("marketLines");return lines==null?Double.NaN:lines.optDouble(m,Double.NaN);}
    public static void marketLine(JSONObject f,String m,double line)throws JSONException{if(!MarketMath.quarterLine(line))throw new JSONException("盘口需为0.25整数倍");JSONObject lines=f.optJSONObject("marketLines");if(lines==null){lines=new JSONObject();f.put("marketLines",lines);}lines.put(m,line);}
    public static int priceCount(JSONObject f){int n=0;for(String m:MARKETS)n+=market(f,m).length();return n;}
    public static void prices(JSONObject f,String m,JSONObject p,String src)throws JSONException {
        if(p.length()==0)return;JSONObject all=f.optJSONObject("markets");if(all==null){all=new JSONObject();f.put("markets",all);}all.put(m,p);
        JSONObject sources=f.optJSONObject("sources");if(sources==null){sources=new JSONObject();f.put("sources",sources);}sources.put(m,src);
        f.put("updatedAt",System.currentTimeMillis());
    }
    static JSONObject fixture(String id,String k,String n,String h,String a,long start,int mode)throws JSONException{
        JSONObject f=new JSONObject();f.put("id",id);f.put("dayKey",k);f.put("number",n);f.put("home",h);f.put("away",a);f.put("kickoff",start);f.put("mode",mode);f.put("markets",new JSONObject());f.put("sources",new JSONObject());f.put("marketLines",new JSONObject());return f;
    }
    static void csv(JSONObject f,String kind,String text,int offset,String source)throws JSONException{
        String[] a=text.split(",",-1),ls=labels(kind);if(a.length!=ls.length+offset)return;JSONObject p=new JSONObject();
        for(int i=0;i<ls.length;i++){try{double v=Double.parseDouble(a[i+offset]);if(validOdd(v))p.put(ls[i],v);}catch(Exception ignored){}}
        prices(f,kind,p,source);
    }
    public static ArrayList<JSONObject> sina(String text,String k)throws JSONException{
        JSONObject r=new JSONObject(text).optJSONObject("result");JSONArray a=r==null?null:r.optJSONArray("data");ArrayList<JSONObject> out=new ArrayList<>();if(a==null)return out;
        for(int i=0;i<Math.min(500,a.length());i++){
            JSONObject x=a.optJSONObject(i);if(x==null)continue;String n=x.optString("matchNo"),h=x.optString("team1"),away=x.optString("team2"),jc=x.optString("tiCaiId");
            long start=time(x.optString("matchTimeFormat"));if(n.isEmpty()||h.isEmpty()||away.isEmpty()||!inSaleDay(start,k))continue;
            JSONObject f=fixture(jc.isEmpty()?"SINA-"+k+"-"+n:"JC-"+jc,k,n,h,away,start,0);f.put("jcId",jc);f.put("sinaId",x.optString("matchId"));f.put("league",x.optString("league"));
            String src="新浪 · 历史固定奖金";csv(f,"HAD",x.optString("spf"),0,src);String rq=x.optString("rqspf");String[] p=rq.split(",",-1);if(p.length==4&&p[0].matches("[+-]?\\d+")){f.put("goalLine",p[0]);csv(f,"HHAD",rq,1,src);}
            csv(f,"CRS",x.optString("bf"),0,src);csv(f,"TTG",x.optString("jq"),0,src);csv(f,"HAFU",x.optString("bqc"),0,src);
            int[] ft=score(x.optString("score1")+":"+x.optString("score2")),ht=score(x.optString("halfScore1")+":"+x.optString("halfScore2"));
            boolean end="3".equals(x.optString("showSellStatus"))||x.optString("matchStatus").matches("(?i)FT|finished|完场");
            if(ft!=null&&end)f.put("result",result(f,ft,ht,"新浪公开赛果",true));out.add(f);
        }return out;
    }
    static boolean inSaleDay(long t,String k){
        if(t<=0)return false;
        long begin=time(iso(k)+" 06:00:00");
        return begin>0&&t>=begin&&t<begin+24L*60*60*1000;
    }
    public static ArrayList<JSONObject> five(String html,String k)throws JSONException{
        ArrayList<Integer> starts=new ArrayList<>(),ends=new ArrayList<>();ArrayList<String> attrs=new ArrayList<>();Matcher tr=Pattern.compile("(?is)<tr\\b([^>]*\\bdata-fixtureid\\s*=[^>]*)>").matcher(html);
        while(tr.find()){starts.add(tr.start());ends.add(tr.end());attrs.add(tr.group(1));}ArrayList<JSONObject> out=new ArrayList<>();
        for(int i=0;i<starts.size();i++){
            String at=attrs.get(i);if(!attr(at,"data-processdate").replace("-","").equals(k))continue;String h=attr(at,"data-homesxname"),a=attr(at,"data-awaysxname"),jc=attr(at,"data-id");if(h.isEmpty()||a.isEmpty()||jc.isEmpty())continue;
            String block=html.substring(ends.get(i),i+1<starts.size()?starts.get(i+1):html.length());
            JSONObject f=fixture("JC-"+jc,k,attr(at,"data-matchnum"),h,a,time(attr(at,"data-matchdate")+" "+attr(at,"data-matchtime")),0);
            f.put("jcId",jc);f.put("fiveId",attr(at,"data-fixtureid"));f.put("goalLine",attr(at,"data-rangqiu"));f.put("league",attr(at,"data-simpleleague"));
            HashMap<String,JSONObject> maps=new HashMap<>();Matcher btn=Pattern.compile("(?is)<p\\b([^>]*\\bdata-sp\\s*=[^>]*)>").matcher(block);
            while(btn.find()){
                String b=btn.group(1),type=attr(b,"data-type"),value=attr(b,"data-value");String m=type.equals("nspf")?"HAD":type.equals("spf")?"HHAD":type.equals("bf")?"CRS":type.equals("jqs")?"TTG":type.equals("bqc")?"HAFU":"";if(m.isEmpty())continue;
                String label=fiveLabel(m,value);if(label.isEmpty())continue;try{double v=Double.parseDouble(attr(b,"data-sp"));if(validOdd(v)){JSONObject p=maps.get(m);if(p==null){p=new JSONObject();maps.put(m,p);}p.put(label,v);}}catch(Exception ignored){}
            }
            for(String m:maps.keySet())prices(f,m,maps.get(m),"500 · 固定奖金");
            Matcher sc=Pattern.compile("(?is)<a\\b[^>]*class=['\"][^'\"]*\\bscore\\b[^'\"]*['\"][^>]*>(.*?)</a>").matcher(block);
            if(sc.find()&&block.contains("betbtn-ok")){int[] ft=score(clean(sc.group(1)));if(ft!=null)f.put("result",result(f,ft,null,"500公开赛果",true));}
            out.add(f);
        }return out;
    }
    static String fiveLabel(String m,String v){
        if(m.equals("HAD")||m.equals("HHAD")){String s=code(v);return s.isEmpty()?"":(m.equals("HHAD")?"让":"")+s;}
        if(m.equals("CRS"))return v.replace("其它","其他");
        if(m.equals("TTG"))return v.equals("7")?"7+球":v+"球";
        if(m.equals("HAFU")){String[] a=v.split("-");return a.length==2&&!code(a[0]).isEmpty()&&!code(a[1]).isEmpty()?code(a[0])+"/"+code(a[1]):"";}return "";
    }
    static String code(String s){return s.equals("3")?"胜":s.equals("1")?"平":s.equals("0")?"负":"";}
    static HashMap<String,String> schedule(String html,String name){
        HashMap<String,String> out=new HashMap<>();int p=html.indexOf(name);if(p<0)return out;
        int eq=html.indexOf('=',p+name.length()),q=eq<0?-1:html.indexOf('"',eq);char quote='"';
        if(q<0){q=eq<0?-1:html.indexOf('\'',eq);quote='\'';}if(q<0)return out;int end=html.indexOf(quote,q+1);if(end<0)return out;
        String[] parts=html.substring(q+1,end).split("\\|",-1),ids=parts[0].split(","),nums=parts.length>1?parts[1].split(","):new String[0];
        for(int i=0;i<ids.length;i++)out.put(ids[i].trim(),i<nums.length?nums[i].trim():"");return out;
    }
    public static ArrayList<JSONObject> titan(String html,String k,int mode)throws JSONException{
        HashMap<String,String> numbers=schedule(html,mode==0?"jinZuSchedule":"beiDanSchedule");ArrayList<JSONObject> out=new ArrayList<>();
        int cursor=0;
        while((cursor=html.indexOf("<tr",cursor))>=0){int openEnd=html.indexOf('>',cursor);if(openEnd<0)break;int close=html.indexOf("</tr>",openEnd+1);if(close<0)break;String header=html.substring(cursor,openEnd+1),body=html.substring(openEnd+1,close);cursor=close+5;
            String id=attr(header,"sId");if(!numbers.containsKey(id))continue;ArrayList<String> cells=new ArrayList<>();int tdCursor=0;while((tdCursor=body.indexOf("<td",tdCursor))>=0){int tdOpen=body.indexOf('>',tdCursor);if(tdOpen<0)break;int tdClose=body.indexOf("</td>",tdOpen+1);if(tdClose<0)break;cells.add(clean(body.substring(tdOpen+1,tdClose)));tdCursor=tdClose+5;}if(cells.size()<7)continue;
            String h=team(cells.get(3)),a=team(cells.get(5));if(h.isEmpty()||a.isEmpty())continue;String clock=cells.get(1);Matcher tm=Pattern.compile("(?:(\\d{1,2})日)?\\s*(\\d{1,2}:\\d{2})").matcher(clock);long start=0;
            if(tm.find()){String d=k;if(tm.group(1)!=null&&!tm.group(1).equals(String.valueOf(Integer.parseInt(k.substring(6))))){String next=shift(k,1);if(Integer.parseInt(next.substring(6))==Integer.parseInt(tm.group(1)))d=next;else continue;}start=time(iso(d)+" "+tm.group(2));}
            JSONObject f=fixture("TITAN-"+id,k,numbers.get(id),h,a,start,mode);f.put("titanId",id);f.put("league",cells.get(0));
            if(cells.get(2).equals("完")){int[] ft=score(cells.get(4)),ht=score(cells.get(6));if(ft!=null)f.put("result",result(f,ft,ht,"球探完场比分",true));}
            out.add(f);
        }return out;
    }
    /** Global schedule: scan all Titan rows instead of only JC/BD schedule IDs. */
    public static ArrayList<JSONObject> globalTitan(String html,String k)throws JSONException{
        ArrayList<JSONObject> out=new ArrayList<>();int cursor=0,seq=1;HashSet<String> seen=new HashSet<>();
        while((cursor=html.indexOf("<tr",cursor))>=0){int openEnd=html.indexOf('>',cursor);if(openEnd<0)break;int close=html.indexOf("</tr>",openEnd+1);if(close<0)break;String header=html.substring(cursor,openEnd+1),body=html.substring(openEnd+1,close);cursor=close+5;String id=attr(header,"sId");if(id.isEmpty()||!id.matches("\\d+")||!seen.add(id))continue;
            ArrayList<String> cells=new ArrayList<>();int td=0;while((td=body.indexOf("<td",td))>=0){int a=body.indexOf('>',td),b=a<0?-1:body.indexOf("</td>",a+1);if(a<0||b<0)break;cells.add(clean(body.substring(a+1,b)));td=b+5;}if(cells.size()<6)continue;
            String home=team(cells.get(3)),away=team(cells.get(5));if(home.isEmpty()||away.isEmpty())continue;String clock=cells.get(1);Matcher tm=Pattern.compile("(?:(\\d{1,2})日)?\\s*(\\d{1,2}:\\d{2})").matcher(clock);long start=0;if(tm.find()){String d=k;if(tm.group(1)!=null&&!tm.group(1).equals(String.valueOf(Integer.parseInt(k.substring(6))))){String n=shift(k,1);if(Integer.parseInt(n.substring(6))==Integer.parseInt(tm.group(1)))d=n;}start=time(iso(d)+" "+tm.group(2));}
            JSONObject f=fixture("TITAN-"+id,k,String.format(Locale.CHINA,"全%03d",seq++),home,away,start,2);f.put("titanId",id);f.put("league",cells.get(0));
            if(cells.size()>6&&cells.get(2).equals("完")){int[] ft=score(cells.get(4)),ht=score(cells.get(6));if(ft!=null)f.put("result",result(f,ft,ht,"球探公开完场比分",true));}out.add(f);
        }return out;
    }
    public static ArrayList<JSONObject> beidan(String html,String k)throws JSONException{
        ArrayList<JSONObject> out=new ArrayList<>();Matcher tr=Pattern.compile("(?is)<tr\\b([^>]*\\bfid=[^>]*)>(.*?)</tr>").matcher(html);
        while(tr.find()){
            String at=tr.group(1),raw=attr(at,"value"),d=meta(raw,"scheduleDate").replace("-","");if(!d.equals(k))continue;String id=attr(at,"fid"),h=meta(raw,"homeTeam"),a=meta(raw,"guestTeam");if(h.isEmpty()||a.isEmpty())continue;
            JSONObject f=fixture("BD500-"+id,k,meta(raw,"index"),h,a,time(meta(raw,"endTime")),1);f.put("fiveId",id);f.put("league",meta(raw,"leagueName"));f.put("goalLine",meta(raw,"rangqiuNum"));
            // 北单这里只取赛程；浮动赔率由具体公司盘补齐。
            out.add(f);
        }return out;
    }
    public static JSONObject result(JSONObject f,int[] ft,int[] ht,String source,boolean finished)throws JSONException{
        JSONObject r=new JSONObject();for(String k:new String[]{"id","jcId","sinaId","titanId","fiveId","number","home","away","dayKey","mode","kickoff"})if(f.has(k))r.put(k,f.opt(k));
        r.put("ft",ft[0]+":"+ft[1]);if(ht!=null&&ht[0]<=ft[0]&&ht[1]<=ft[1])r.put("ht",ht[0]+":"+ht[1]);r.put("finished",finished);r.put("source",source);r.put("observedAt",System.currentTimeMillis());return r;
    }
    static boolean teamSimilar(String a,String b){
        String x=norm(a),y=norm(b);if(x.isEmpty()||y.isEmpty())return false;if(x.equals(y))return true;
        return x.length()>=4&&y.length()>=4&&(x.contains(y)||y.contains(x));
    }
    public static boolean sameFixture(JSONObject a,JSONObject b){
        if(!a.optString("dayKey").equals(b.optString("dayKey")))return false;
        for(String k:new String[]{"jcId","titanId","fiveId"}){String v=a.optString(k);if(!v.isEmpty()&&v.equals(b.optString(k))){
            if(teamSimilar(a.optString("home"),b.optString("away"))&&!teamSimilar(a.optString("home"),b.optString("home")))return false;
            return true;
        }}
        long at=a.optLong("kickoff"),bt=b.optLong("kickoff");boolean near=at>0&&bt>0&&Math.abs(at-bt)<=45*60000;
        if(teamSimilar(a.optString("home"),b.optString("home"))&&teamSimilar(a.optString("away"),b.optString("away"))&&(near||at==0||bt==0))return true;
        int am=a.optInt("mode",-1),bm=b.optInt("mode",-1);String an=a.optString("number"),bn=b.optString("number");
        if(am==1&&bm==1&&near&&!an.isEmpty()&&an.equals(bn))return true;
        return am==0&&bm==0&&near&&!an.isEmpty()&&an.equals(bn)&&!(teamSimilar(a.optString("home"),b.optString("away"))&&teamSimilar(a.optString("away"),b.optString("home")));
    }
    public static JSONObject findResult(JSONObject f,List<JSONObject> results){JSONObject found=null;for(JSONObject r:results)if(sameFixture(f,r)){if(found!=null&&!found.optString("ft").equals(r.optString("ft")))return null;if(found==null||r.optString("ht").length()>found.optString("ht").length())found=r;}return found;}
    public static void applyEvidence(JSONObject selection,JSONObject result)throws JSONException{
        JSONObject old=selection.optJSONObject("result");if(old!=null&&old.optBoolean("manual"))return;
        JSONObject evidence=selection.optJSONObject("resultEvidence");if(evidence==null){evidence=new JSONObject();if(old!=null&&old.optBoolean("finished"))evidence.put(old.optString("source"),old);}
        evidence.put(result.getString("source"),copy(result));selection.put("resultEvidence",evidence);JSONObject best=null;String full="",half="";boolean conflict=false;
        for(Iterator<String> it=evidence.keys();it.hasNext();){JSONObject r=evidence.getJSONObject(it.next());if(best==null)best=copy(r);String ft=r.optString("ft"),ht=r.optString("ht");if(!full.isEmpty()&&!ft.equals(full))conflict=true;full=ft;if(!half.isEmpty()&&!ht.isEmpty()&&!half.equals(ht))conflict=true;if(!ht.isEmpty())half=ht;}
        if(best==null)return;if(!half.isEmpty())best.put("ht",half);best.put("conflict",conflict);if(conflict){best.put("finished",false);best.put("source","赛果来源存在冲突");}selection.put("result",best);
    }
    public static ArrayList<JSONObject> merge(List<JSONObject> base,List<JSONObject> incoming,boolean prefer)throws JSONException{
        ArrayList<JSONObject> out=new ArrayList<>();for(JSONObject x:base)out.add(copy(x));
        for(JSONObject x:incoming){JSONObject match=null;for(JSONObject y:out)if(sameFixture(x,y)){if(match!=null){match=null;break;}match=y;}
            if(match==null){out.add(copy(x));continue;}
            for(String key:new String[]{"jcId","sinaId","titanId","fiveId"})if(!x.optString(key).isEmpty())match.put(key,x.optString(key));
            for(String m:MARKETS)if(market(x,m).length()>0&&(prefer||market(match,m).length()==0)){
                prices(match,m,copy(market(x,m)),x.optJSONObject("sources").optString(m));if(m.equals("HHAD"))match.put("goalLine",x.optString("goalLine"));double line=marketLine(x,m);if(!Double.isNaN(line))marketLine(match,m,line);
            }
            JSONObject r=x.optJSONObject("result"),old=match.optJSONObject("result");if(r!=null){if(old==null||r.optString("ht").length()>old.optString("ht").length())match.put("result",copy(r));}
        }
        Collections.sort(out,(a,b)->{int z=a.optString("number").compareTo(b.optString("number"));return z!=0?z:Long.compare(a.optLong("kickoff"),b.optLong("kickoff"));});return out;
    }
    public static String infer(String label){if(label.startsWith("让"))return "HHAD";if(label.contains(":")||label.contains("其他")||label.contains("其它"))return "CRS";if(label.endsWith("球"))return "TTG";if(label.contains("/")||label.matches("[胜平负]{2}"))return "HAFU";if(label.matches("[上下][单双]"))return "DS";return "HAD";}
    static String outcome(int h,int a){return h>a?"胜":h<a?"负":"平";}
    /** NaN means unresolved, 0 loss, 1 void, otherwise saved decimal odds. */
    public static double factor(JSONObject p,JSONObject r){
        if(r==null||!r.optBoolean("finished"))return Double.NaN;if(r.optBoolean("void"))return 1;
        int[] ft=score(r.optString("ft"));if(ft==null)return Double.NaN;String l=p.optString("label").replace("其它","其他"),m=p.optString("market",infer(l));double odd=p.optDouble("odd");if(!validOdd(odd))return Double.NaN;
        int h=ft[0],a=ft[1];boolean hit=false;
        if(m.equals("HAD"))hit=l.equals(outcome(h,a))||l.equals("主胜")&&h>a||l.equals("客胜")&&h<a;
        else if(m.equals("HHAD")){String v=p.optString("goalLine");if(!v.matches("[+-]?\\d+"))return Double.NaN;double d=h-a+Double.parseDouble(v);hit=l.equals("让"+(d>0?"胜":d<0?"负":"平"));}
        else if(m.equals("TTG"))hit=l.equals((h+a)+"球")||l.equals("7+球")&&h+a>=7;
        else if(m.equals("HAFU")){int[] ht=score(r.optString("ht"));if(ht==null||ht[0]>h||ht[1]>a)return Double.NaN;hit=l.replace("/","").equals(outcome(ht[0],ht[1])+outcome(h,a));}
        else if(m.equals("DS"))hit=l.equals((h+a>=3?"上":"下")+((h+a)%2==0?"双":"单"));
        else if(m.equals("OU")){double line=p.optDouble("line",Double.NaN);if(Double.isNaN(line))return Double.NaN;try{return MarketMath.asian(h+a,line,l.equals("大"),odd);}catch(Exception e){return Double.NaN;}}
        else if(m.equals("AH")){double line=p.optDouble("line",Double.NaN);if(Double.isNaN(line))return Double.NaN;try{return MarketMath.asian(h-a,-line,l.equals("主"),odd);}catch(Exception e){return Double.NaN;}}
        else if(m.equals("CRS")){String exact=h+":"+a;JSONArray recorded=p.optJSONArray("listedScores");Set<String> known=new HashSet<>();if(recorded!=null){for(int i=0;i<recorded.length();i++)known.add(recorded.optString(i));}else known.addAll(Arrays.asList(labels("CRS")));hit=l.equals(exact)||l.equals(outcome(h,a)+"其他")&&!known.contains(exact);}
        else return Double.NaN;return hit?odd:0;
    }
    public static double combinations(List<Double> weights,int k){if(k<1||k>weights.size()||k>8)return 0;double[] dp=new double[k+1];dp[0]=1;for(double w:weights){if(Double.isNaN(w)||Double.isInfinite(w)||w<0)throw new IllegalArgumentException("无效赔率");for(int j=k;j>0;j--)dp[j]+=dp[j-1]*w;}return dp[k];}
    public static double count(JSONArray ss,int k){ArrayList<Double> a=new ArrayList<>();for(int i=0;i<ss.length();i++){JSONArray p=ss.optJSONObject(i).optJSONArray("picks");a.add(p==null?0.0:(double)p.length());}return combinations(a,k);}
    /** Recalculation is idempotent; no funds added to a balance on repeated sync. */
    public static void settle(JSONObject t)throws JSONException{
        JSONArray ss=t.optJSONArray("selections");if(ss==null||ss.length()==0)return;int k=t.optInt("passK",ss.length());if(k<1||k>Math.min(8,ss.length()))throw new JSONException("无效串关方式");
        double count=count(ss,k);if(count<=0)throw new JSONException("无投注项");double stake=t.optDouble("stake",0),unit=t.has("unitStake")?t.optDouble("unitStake"):stake/count;
        if(Double.isNaN(unit)||Double.isInfinite(unit)||unit<=0)throw new JSONException("无效金额");t.put("unitStake",unit);t.put("count",count);
        ArrayList<Double> weights=new ArrayList<>();boolean all=true;int complete=0;StringBuilder scores=new StringBuilder();
        for(int i=0;i<ss.length();i++){
            JSONObject s=ss.getJSONObject(i),r=s.optJSONObject("result");JSONArray ps=s.optJSONArray("picks");double win=0;boolean known=true;
            for(int j=0;ps!=null&&j<ps.length();j++){
                JSONObject p=ps.getJSONObject(j);if(!p.has("goalLine")&&s.has("goalLine"))p.put("goalLine",s.optString("goalLine"));double v=factor(p,r);
                String status;if(Double.isNaN(v))status=r!=null&&r.optBoolean("finished")?"待补数据":"待完场";else if(r.optBoolean("void"))status="退回";else if((p.optString("market").equals("OU")||p.optString("market").equals("AH"))&&Math.abs(v-1)<0.000001)status="走盘";else if(v>0&&v<1)status="输半";else if((p.optString("market").equals("OU")||p.optString("market").equals("AH"))&&v>1&&v+0.000001<p.optDouble("odd"))status="赢半";else status=v>0?"命中":"未中";
                p.put("check",status);if(Double.isNaN(v)){known=false;p.remove("factor");}else{p.put("factor",v);win+=v;}
            }
            if(known)complete++;else all=false;
            s.put("resultScore",r==null?"待同步":r.optBoolean("void")?"退回":r.optString("ft","待同步"));s.put("checked",known);if(known)s.put("hit",win>0);else s.remove("hit");
            weights.add(known?win:0.0);if(i>0)scores.append("；");scores.append(s.optString("resultScore"));
        }
        double returned=money(unit*combinations(weights,k));t.put("completedMatches",complete);t.put("settlementResult",scores.toString());t.put("settled",all);t.put("knownReturn",returned);
        t.put("prize",all?returned:0);t.put("status",all?returned>0?"已结算·有回报":"已结算·未中":"待赛果 "+complete+"/"+ss.length());
        if(all){t.put("net",money(returned-stake));t.put("roi",stake>0?(returned-stake)/stake*100:0);if(!t.has("settledAt"))t.put("settledAt",System.currentTimeMillis());}
        else {t.remove("net");t.remove("roi");t.remove("settledAt");}
        t.put("calculation","按保存赔率乘法计算（虚拟）");
    }
}
