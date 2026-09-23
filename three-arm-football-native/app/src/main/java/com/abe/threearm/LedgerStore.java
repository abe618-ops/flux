package com.abe.threearm;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Local-only ledger compatible in spirit with the original sgmc samples. */
public final class LedgerStore {
    private static final String PREF="sgmc_offline"; private static final String KEY="ledger_v2";
    public static final class Sample {
        public String id, seed, created; public Integer home,away; public String dir;
        public boolean hasResult(){return dir!=null || (home!=null&&away!=null);}
        public int outcomeIndex(){if(dir!=null)return dir.equals("主")?0:dir.equals("平")?1:2;return PredictionEngine.outcomeIndex(home,away);}
        public String resultText(){return dir!=null?"仅方向 "+dir:(home!=null?home+":"+away:"待出结果");}
    }
    private final SharedPreferences sp; private final List<Sample> samples=new ArrayList<>();
    public LedgerStore(Context c){sp=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);load();}
    public List<Sample> all(){return Collections.unmodifiableList(samples);} public Sample latest(){return samples.isEmpty()?null:samples.get(samples.size()-1);}
    public Sample add(String seed){Sample s=new Sample();s.id=String.format(Locale.US,"%03d",nextId());s.seed=seed;s.created=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());samples.add(s);save();return s;}
    public void setScore(Sample s,int h,int a){s.home=h;s.away=a;s.dir=null;save();}
    public void setDirection(Sample s,String d){s.home=null;s.away=null;s.dir=d;save();}
    private int nextId(){int n=69;for(Sample s:samples)try{n=Math.max(n,Integer.parseInt(s.id));}catch(Exception ignored){}return n+1;}
    private void load(){
        String raw=sp.getString(KEY,null);if(raw==null){seedHistory();save();return;}
        try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);Sample s=new Sample();s.id=o.getString("id");s.seed=o.getString("seed");s.created=o.optString("created","");if(o.has("dir"))s.dir=o.getString("dir");if(o.has("home")){s.home=o.getInt("home");s.away=o.getInt("away");}samples.add(s);}}catch(Exception e){samples.clear();seedHistory();save();}
    }
    private void save(){try{JSONArray a=new JSONArray();for(Sample s:samples){JSONObject o=new JSONObject();o.put("id",s.id);o.put("seed",s.seed);o.put("created",s.created);if(s.dir!=null)o.put("dir",s.dir);if(s.home!=null){o.put("home",s.home);o.put("away",s.away);}a.put(o);}sp.edit().putString(KEY,a.toString()).apply();}catch(Exception ignored){}}
    private void seedHistory(){
        String[][] x={{"070","3194350835040860996","1","1"},{"071","16907959578202588323","2","1"},{"072","2648498682349008761","3","0"},{"073","2396446489805148050","2","1"},{"074","2885623196426583516","",""},{"075","2668622928328113623","",""},{"076","4609992865523023458","",""},{"077","9172654467136604075","",""},{"078","3731742816511762768","4","1"},{"079","11743964159402733811","",""},{"080","13479390268398869218","",""},{"081","5580822898547318970","",""},{"082","6844831764468925773","主",""},{"083","12134288040939091116","主",""}};
        for(String[] q:x){Sample s=new Sample();s.id=q[0];s.seed=q[1];s.created="历史";if(q[2].equals("主"))s.dir="主";else if(!q[2].isEmpty()){s.home=Integer.parseInt(q[2]);s.away=Integer.parseInt(q[3]);}samples.add(s);}
    }

    public Stats stats(){Stats st=new Stats();double[] sums=new double[3];int[] hits=new int[3];for(Sample s:samples){if(!s.hasResult())continue;st.done++;PredictionEngine.Forecast f=PredictionEngine.forecast(s.seed);PredictionEngine.Markets[] ms={f.A.mk,f.B1.mk,f.B2.mk};int idx=s.outcomeIndex();for(int k=0;k<3;k++){sums[k]+=PredictionEngine.rps(ms[k],idx);double[] p={ms[k].home,ms[k].draw,ms[k].away};int best=p[0]>=p[1]&&p[0]>=p[2]?0:(p[1]>=p[2]?1:2);if(best==idx)hits[k]++;}}st.pending=samples.size()-st.done;for(int k=0;k<3;k++){st.rps[k]=st.done==0?0:sums[k]/st.done;st.hits[k]=hits[k];}return st;}
    public static final class Stats{public int done,pending;public final double[] rps=new double[3];public final int[] hits=new int[3];}
}
