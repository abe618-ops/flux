package com.matchday.virtualbet;

import android.content.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TicketStore {
    private static final Object LOCK=new Object();
    private static final AtomicBoolean SYNCING=new AtomicBoolean(false);
    final SharedPreferences prefs;
    final MatchCacheDb cacheDb;
    final ProviderHealth health;
    public TicketStore(Context c){Context app=c.getApplicationContext();prefs=app.getSharedPreferences("virtual_bet",0);cacheDb=new MatchCacheDb(app);health=new ProviderHealth(app);migrateCache();migrate();}
    private void migrateCache(){if(prefs.getBoolean("sqlite_cache_140",false))return;try{Map<String,?> all=prefs.getAll();for(String key:new ArrayList<String>(all.keySet())){if(!key.startsWith("fixtures130_")||key.startsWith("fixtures130_time_"))continue;String[] x=key.substring("fixtures130_".length()).split("_",2);if(x.length!=2)continue;int mode=Integer.parseInt(x[0]);JSONArray a=new JSONArray(String.valueOf(all.get(key)));ArrayList<JSONObject> rows=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)rows.add(o);}if(!rows.isEmpty())cacheDb.putFixtures(mode,x[1],rows);}prefs.edit().putBoolean("sqlite_cache_140",true).apply();}catch(Exception ignored){}}
    public JSONArray read()throws JSONException{synchronized(LOCK){return new JSONArray(prefs.getString("tickets","[]"));}}
    private void migrate(){synchronized(LOCK){if(prefs.getBoolean("schema_130",false))return;try{
        String original=prefs.getString("tickets","[]");JSONArray tickets=new JSONArray(original);
        for(int i=0;i<tickets.length();i++){
            JSONObject t=tickets.getJSONObject(i);JSONArray ss=t.optJSONArray("selections");if(ss==null)continue;t.put("schema",3);int mode=t.optString("mode").contains("北")?1:0;
            if(!t.has("estimate"))t.put("estimate",t.optDouble("prize",0));
            for(int j=0;j<ss.length();j++){JSONObject s=ss.getJSONObject(j);s.put("dayKey",t.optString("dayKey"));s.put("mode",mode);if(!s.has("kickoff"))s.put("kickoff",0);JSONArray ps=s.optJSONArray("picks");for(int z=0;ps!=null&&z<ps.length();z++){JSONObject p=ps.getJSONObject(z);if(!p.has("market"))p.put("market",FootballData.infer(p.optString("label")));}}
            FootballData.settle(t);
        }
        prefs.edit().putString("tickets_before_v130",original).putString("tickets",tickets.toString()).putBoolean("schema_130",true).apply();
    }catch(Exception ignored){}}}
    public void add(JSONObject ticket)throws JSONException{synchronized(LOCK){JSONArray a=read();a.put(ticket);prefs.edit().putString("tickets",a.toString()).apply();}}
    public JSONObject get(String id)throws JSONException{JSONArray a=read();for(int i=0;i<a.length();i++)if(a.getJSONObject(i).optString("id").equals(id))return a.getJSONObject(i);return null;}
    public void manual(String ticketId,int index,JSONObject result)throws JSONException{synchronized(LOCK){JSONArray a=read();for(int i=0;i<a.length();i++){JSONObject t=a.getJSONObject(i);if(!ticketId.equals(t.optString("id")))continue;JSONObject s=t.getJSONArray("selections").getJSONObject(index);s.put("result",result);s.remove("resultEvidence");FootballData.settle(t);}prefs.edit().putString("tickets",a.toString()).apply();}}
    public void recalculate()throws JSONException{synchronized(LOCK){JSONArray a=read();for(int i=0;i<a.length();i++)FootballData.settle(a.getJSONObject(i));prefs.edit().putString("tickets",a.toString()).apply();}}
    public void applyResults(List<JSONObject> results)throws JSONException{synchronized(LOCK){JSONArray a=read();boolean changed=false;
        for(int i=0;i<a.length();i++){
            JSONObject t=a.getJSONObject(i);JSONArray ss=t.optJSONArray("selections");if(ss==null)continue;boolean touched=false;
            for(int j=0;j<ss.length();j++){
                JSONObject s=ss.getJSONObject(j),old=s.optJSONObject("result");if(old!=null&&old.optBoolean("manual"))continue;
                JSONObject r=FootballData.findResult(s,results);if(r==null||!r.optBoolean("finished"))continue;
                FootballData.applyEvidence(s,r);touched=true;
            }
            if(touched){FootballData.settle(t);changed=true;}
        }
        if(changed)prefs.edit().putString("tickets",a.toString()).apply();
    }}
    public ArrayList<JSONObject> cache(int mode,String date){return cacheDb.getFixtures(mode,date);}
    public long cacheTime(int mode,String date){return cacheDb.getUpdatedAt(mode,date);}
    public void cache(int mode,String date,List<JSONObject> data){cacheDb.putFixtures(mode,date,data);}
    public ArrayList<JSONObject> resultCache(int mode,String date){return cacheDb.getResults(mode,date);}
    public void resultCache(int mode,String date,List<JSONObject> data){cacheDb.putResults(mode,date,data);}
    public interface Progress{void update(String message);}
    public String sync(DataClient.Scope scope,Progress progress){
        if(!SYNCING.compareAndSet(false,true))return "赛果同步正在进行";
        ExecutorService io=Executors.newFixedThreadPool(4);int ok=0,fail=0;
        try{
            recalculate();JSONArray a=read();LinkedHashSet<String> days=new LinkedHashSet<>();String recent=FootballData.shift(FootballData.today(),-2);
            for(int i=a.length()-1;i>=0;i--){JSONObject t=a.getJSONObject(i);JSONArray ss=t.optJSONArray("selections");for(int j=0;ss!=null&&j<ss.length();j++){JSONObject s=ss.getJSONObject(j);String d=s.optString("dayKey",t.optString("dayKey"));if(!d.matches("\\d{8}")||d.compareTo(FootballData.today())>0)continue;if(!t.optBoolean("settled")||d.compareTo(recent)>=0)days.add(s.optInt("mode",t.optString("mode").contains("北")?1:0)+"|"+d);}}
            ExecutorCompletionService<List<JSONObject>> service=new ExecutorCompletionService<>(io);int requests=0;
            for(String day:days){int mode=Integer.parseInt(day.substring(0,1));String d=day.substring(2);if(requests>=30)break;
                ArrayList<JSONObject> fromCache=resultCache(mode,d);
                if(fromCache.isEmpty()){for(JSONObject f:cache(mode,d)){JSONObject r=f.optJSONObject("result");if(r!=null)fromCache.add(r);}}
                if(!fromCache.isEmpty())applyResults(fromCache);
                int count=mode==0?3:1;int[] order=health.order("result",mode,count);
                for(int pos=0;pos<order.length;pos++){final int s=order[pos];final String dayKey=d;final int mm=mode;service.submit(()->{long started=System.currentTimeMillis();try{List<JSONObject> rs=DataClient.results(dayKey,mm,s,scope);if(rs!=null&&!rs.isEmpty()){health.ok("result",mm,s,System.currentTimeMillis()-started);resultCache(mm,dayKey,rs);}else health.fail("result",mm,s);return rs;}catch(Exception e){health.fail("result",mm,s);throw e;}});requests++;}
            }
            long deadline=System.currentTimeMillis()+90000;
            for(int i=0;i<requests;i++){scope.check();long left=deadline-System.currentTimeMillis();if(left<=0)break;Future<List<JSONObject>> f=service.poll(Math.min(left,16000),TimeUnit.MILLISECONDS);if(f==null)break;
                try{List<JSONObject> rs=f.get();applyResults(rs);ok++;if(progress!=null)progress.update("已核验 "+(i+1)+"/"+requests+" 个赛果来源");}catch(ExecutionException e){fail++;}
            }
            a=read();int settled=0;for(int i=0;i<a.length();i++)if(a.getJSONObject(i).optBoolean("settled"))settled++;
            return "已结算 "+settled+" 张 · 待赛果 "+(a.length()-settled)+" 张"+(fail>0?" · "+fail+" 个来源暂不可用":"")+(requests==0?"":" · "+ok+" 个来源已核验");
        }catch(Exception e){return "本次同步未完成："+DataClient.error(e);}finally{scope.cancel();io.shutdownNow();SYNCING.set(false);}
    }
    public JSONObject backup()throws JSONException{JSONObject b=new JSONObject();b.put("kind","matchday-backup");b.put("schema",3);b.put("tickets",read());b.put("created",System.currentTimeMillis());b.put("legacy",prefs.getString("tickets_before_v130","[]"));return b;}
    public int restore(JSONObject data)throws JSONException{synchronized(LOCK){if(!data.optString("kind").equals("matchday-backup"))throw new JSONException("不是本软件备份");JSONArray incoming=data.getJSONArray("tickets"),all=read();if(incoming.length()>3000)throw new JSONException("备份记录过多");HashSet<String> ids=new HashSet<>();for(int i=0;i<all.length();i++)ids.add(all.getJSONObject(i).optString("id"));int count=0;
        for(int i=0;i<incoming.length();i++){JSONObject t=incoming.getJSONObject(i);if(t.optString("id").isEmpty()||!t.optString("dayKey").matches("\\d{8}"))throw new JSONException("记录标识无效");JSONArray ss=t.getJSONArray("selections");if(ss.length()>30)throw new JSONException("场次过多");for(int j=0;j<ss.length();j++){JSONObject s=ss.getJSONObject(j);if(s.optString("home").isEmpty()||s.optString("away").isEmpty())throw new JSONException("缺少主客队");JSONArray ps=s.getJSONArray("picks");if(ps.length()>60)throw new JSONException("选项过多");for(int p=0;p<ps.length();p++)if(!FootballData.validOdd(ps.getJSONObject(p).optDouble("odd")))throw new JSONException("赔率无效");}FootballData.settle(t);if(ids.add(t.getString("id"))){all.put(t);count++;}}
        prefs.edit().putString("tickets",all.toString()).apply();return count;
    }}
}
