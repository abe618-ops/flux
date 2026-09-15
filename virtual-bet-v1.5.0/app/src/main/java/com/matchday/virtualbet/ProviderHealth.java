package com.matchday.virtualbet;

import android.content.*;
import java.util.*;

/** Remembers source latency/failure so later syncs try the healthiest public source first. */
public final class ProviderHealth {
    private final SharedPreferences p;
    public ProviderHealth(Context c){p=c.getApplicationContext().getSharedPreferences("provider_health",0);}
    private String k(String kind,int mode,int source,String field){return kind+"_"+mode+"_"+source+"_"+field;}
    public synchronized void ok(String kind,int mode,int source,long ms){
        long old=p.getLong(k(kind,mode,source,"lat"),ms);long smooth=old<=0?ms:(old*3+ms)/4;
        int fail=Math.max(0,p.getInt(k(kind,mode,source,"fail"),0)-1);
        p.edit().putLong(k(kind,mode,source,"lat"),smooth).putInt(k(kind,mode,source,"fail"),fail).putLong(k(kind,mode,source,"okAt"),System.currentTimeMillis()).apply();
    }
    public synchronized void fail(String kind,int mode,int source){
        int n=Math.min(20,p.getInt(k(kind,mode,source,"fail"),0)+1);
        p.edit().putInt(k(kind,mode,source,"fail"),n).putLong(k(kind,mode,source,"failAt"),System.currentTimeMillis()).apply();
    }
    private long score(String kind,int mode,int source){
        long lat=p.getLong(k(kind,mode,source,"lat"),3500);int fail=p.getInt(k(kind,mode,source,"fail"),0);long okAt=p.getLong(k(kind,mode,source,"okAt"),0);
        long stale=okAt==0?2500:Math.min(2500,(System.currentTimeMillis()-okAt)/(12*3600000L)*250);
        return lat+fail*2200L+stale;
    }
    public int[] order(String kind,int mode,int count){
        Integer[] a=new Integer[count];for(int i=0;i<count;i++)a[i]=i;
        Arrays.sort(a,(x,y)->Long.compare(score(kind,mode,x),score(kind,mode,y)));
        int[] out=new int[count];for(int i=0;i<count;i++)out[i]=a[i];return out;
    }
    public String summary(String kind,int mode,int count){
        int[] o=order(kind,mode,count);StringBuilder b=new StringBuilder();for(int i=0;i<o.length;i++){if(i>0)b.append(" → ");b.append(DataClient.sourceName(kind,mode,o[i]));}return b.toString();
    }
}
