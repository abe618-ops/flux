package com.abe618.ancientfootball;

import android.webkit.JavascriptInterface;
import android.content.Context;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;

import com.abe618.bingfaqimenv2.QimenEngine;
import com.abe618.fourdivfootball.FourDivinationEngine;
import com.abe618.sixfusionfootball.FusionEngine;
import com.abe618.fourshushurandom.ChartEngine;
import com.abe618.sanshifootball.TraditionalEngine;
import com.abe618.triastrofootball.PredictionEngine;

public final class NativeBridge {
  private final Context app;
  private final SecureRandom entropy = new SecureRandom();
  NativeBridge(Context context) { this.app=context.getApplicationContext(); }
  private String error(Exception e){return "{\"error\":"+JSONObject.quote(e.getClass().getSimpleName()+": "+e.getMessage())+"}";}
  private JSONObject system(FourDivinationEngine.SystemResult x)throws Exception{
    JSONObject j=new JSONObject();
    j.put("name",x.name);j.put("raw_plate",x.raw);j.put("direction",x.direction);
    j.put("total_goals",x.goals);j.put("over_under",x.overUnder);
    j.put("odd_even",x.oddEven);j.put("margin",x.margin);j.put("note",x.note);
    return j;
  }
  private JSONObject astro(PredictionEngine.MethodResult x)throws Exception{
    JSONObject j=new JSONObject();
    j.put("name",x.name);j.put("raw",x.randomMark);j.put("detail",x.detail);
    j.put("direction",x.direction);j.put("p_home",x.pHome);
    j.put("p_draw",x.pDraw);j.put("p_away",x.pAway);
    j.put("goal_mean",x.goalMean);j.put("btts",x.btts);return j;
  }
  @JavascriptInterface public String newSeed(){
    byte[] b=new byte[24];entropy.nextBytes(b);
    StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(java.util.Locale.ROOT,"%02x",v&255));
    return s.toString();
  }
  @JavascriptInterface public String runOldSystems(String seed){
    try {
      if(seed==null||seed.trim().length()==0) throw new IllegalArgumentException("需要封盘种子");
      if(seed.length()>256) throw new IllegalArgumentException("种子长度不超过256");
      long n=FourDivinationEngine.seedFromText(seed);
      int serial=(int)Math.floorMod(n,1080L)+1;
      QimenEngine.Board b=QimenEngine.generateBySerial(serial);
      FourDivinationEngine.CastState four=FourDivinationEngine.cast(n);
      FusionEngine.CastState fusion=FusionEngine.cast(n);
      ChartEngine.State charts=ChartEngine.cast(ChartEngine.seedFromText(seed));
      JSONObject root=new JSONObject();
      root.put("seed",seed);
      JSONObject q=new JSONObject();
      q.put("serial",b.serial);q.put("dun",b.yin?"阴":"阳");q.put("bureau",b.ju);
      q.put("hour_ganzhi",b.hourGz);q.put("xun_yi",b.xunYi);
      q.put("home_palace",b.homeGong);q.put("away_palace",b.awayGong);
      q.put("zhifu_origin",b.zhiFuOrigin);q.put("zhifu_star",b.zhiFuStar);
      q.put("zhishi_palace",b.zhiShiGong);q.put("zhishi_door",b.zhiShiDoor);
      q.put("direction",b.prediction.result);q.put("goals",b.prediction.totalGoals);
      q.put("score",b.prediction.scoreText());q.put("primary",b.prediction.primary);
      q.put("secondary",b.prediction.secondary);q.put("index",b.prediction.finalIndex);
      JSONArray palaces=new JSONArray();
      for(int i=1;i<=9;i++){
        JSONObject palace=new JSONObject();palace.put("number",i);
        palace.put("earth",b.di[i]);palace.put("heaven",b.tian[i]);
        palace.put("star",b.star[i]);palace.put("door",b.door[i]);palace.put("god",b.god[i]);
        palaces.put(palace);
      }
      q.put("palaces",palaces);
      root.put("qimen_1080",q);
      JSONObject f=new JSONObject();
      f.put("Maya",system(four.maya));f.put("Sikidy",system(four.sikidy));
      f.put("Ifa",system(four.ifa));f.put("Raml",system(four.raml));
      root.put("four",f);
      JSONObject fusionObj=new JSONObject();
      fusionObj.put("raw",fusion.bingzhan.raw);fusionObj.put("direction",fusion.bingzhan.direction);
      fusionObj.put("goals",fusion.bingzhan.goals);fusionObj.put("net_goal_hint",fusion.bingzhan.netGoalHint);
      root.put("bingzhan",fusionObj);
      JSONObject c=new JSONObject();
      c.put("nineYao",charts.nineYao.text);c.put("lingQi",charts.lingQi.text);
      c.put("sevenStar",charts.sevenStar.text);c.put("triangle",charts.triangle.text);
      root.put("other_original_charts",c);
      root.put("scope","原版民俗软件算法，真实比赛准确率未验证");
      return root.toString();
    }catch(Exception e){return error(e);}
  }
  @JavascriptInterface public String runCalendarSystems(String localTime){
    try{
      if(localTime==null||localTime.length()>40)throw new IllegalArgumentException("日期格式 YYYY-MM-DDTHH:mm");
      LocalDateTime t=LocalDateTime.parse(localTime);
      if(t.getYear()<1900||t.getYear()>2099)throw new IllegalArgumentException("年份限1900-2099");
      TraditionalEngine.CalendarInfo c=TraditionalEngine.CalendarInfo.of(t.getYear(),t.getMonthValue(),t.getDayOfMonth(),t.getHour(),t.getMinute());
      JSONObject out=new JSONObject();out.put("civil_time",localTime);out.put("four_pillars",c.fourPillars());
      out.put("jieqi",c.jieQi);out.put("mansion",c.xiu);
      JSONObject qi=new JSONObject();
      for(String mode:new String[]{"时家","刻家10","刻家12","日家","月家","年家"}){
        TraditionalEngine.QimenResult x=TraditionalEngine.qimen(c,mode);
        JSONObject row=new JSONObject();
        row.put("mode",x.mode);row.put("active",x.activeGanZhi);
        row.put("dun",x.dun);row.put("bureau",x.bureau);row.put("yuan",x.yuan);
        row.put("zhifu",x.zhiFu);row.put("zhishi",x.zhiShi);row.put("note",x.note);
        JSONArray palaces=new JSONArray();
        for(int i=1;i<=9;i++){
          TraditionalEngine.QimenPalace p=x.palaces[i];
          JSONObject palace=new JSONObject();
          palace.put("gong",i);palace.put("name",p.name);
          palace.put("earth",p.earthStem);palace.put("heaven",p.heavenStem);
          palace.put("star",p.star);palace.put("door",p.door);palace.put("god",p.god);
          palaces.put(palace);
        }
        row.put("palaces",palaces);qi.put(mode,row);
      }
      out.put("qimen",qi);
      JSONObject ty=new JSONObject();
      for(String mode:new String[]{"年计","月计","日计","时计"}){
        TraditionalEngine.TaiyiResult x=TraditionalEngine.taiyi(c,mode);
        JSONObject row=new JSONObject();row.put("mode",x.mode);row.put("active",x.activeGz);
        row.put("accumulated",x.accumulated);row.put("bureau",x.bureau);row.put("dun",x.yinYang);
        row.put("taiyi",x.taiyi);row.put("taiyi_palace",x.taiyiPalace);
        row.put("wenchang",x.wenchang);row.put("wenchang_palace",x.wenchangPalace);
        row.put("shiji",x.shiji);row.put("shiji_palace",x.shijiPalace);
        row.put("jishen",x.jishen);row.put("lord_count",x.lord);
        row.put("guest_count",x.guest);row.put("fixed_count",x.fixed);
        row.put("lord_general",x.lordGeneral);row.put("lord_assistant",x.lordAssistant);
        row.put("guest_general",x.guestGeneral);row.put("guest_assistant",x.guestAssistant);
        row.put("note",x.note);ty.put(mode,row);
      }
      out.put("taiyi",ty);
      TraditionalEngine.LiuRenResult lr=TraditionalEngine.liuren(c);
      JSONObject liu=new JSONObject();
      liu.put("month_general",lr.monthGeneral);liu.put("month_general_name",lr.monthGeneralName);
      liu.put("earth_to_sky",new JSONObject(lr.earthToSky));
      liu.put("four_lessons",new JSONArray(lr.lessons));
      liu.put("relations",new JSONArray(lr.relations));
      liu.put("three_pass",lr.threePass==null?JSONObject.NULL:new JSONArray(lr.threePass));
      liu.put("note",lr.methodNote);
      out.put("liu_ren",liu);
      TraditionalEngine.YanqinResult y=TraditionalEngine.yanqin(c);
      JSONObject yan=new JSONObject();yan.put("mansion",y.fullName);yan.put("luminary",y.luminary);
      yan.put("previous",y.previous);yan.put("next",y.next);
      yan.put("ring",y.ring);yan.put("note",y.note);out.put("yanqin",yan);
      out.put("time_note","按设备输入的民用时间排盘；不自动进行真太阳时与比赛地点修正");
      return out.toString();
    }catch(Exception e){return error(e);}
  }
  @JavascriptInterface public String runAstro(String seed){
    try{
      if(seed==null||seed.trim().isEmpty()||seed.length()>256)throw new IllegalArgumentException("请输入固定随机种子");
      PredictionEngine.Result r=PredictionEngine.run(seed);
      JSONObject out=new JSONObject();
      out.put("seed",r.seed);out.put("western",astro(r.western));
      out.put("indian",astro(r.indian));out.put("geomancy",astro(r.geomancy));
      out.put("initial_pattern",r.m1Pattern);out.put("experimental_rounds",r.k);
      out.put("converged_direction",r.m2Direction);
      out.put("caution","原版按随机时间和近似星历演算，不是比赛发生时的精确天文星盘");
      return out.toString();
    }catch(Exception e){return error(e);}
  }
  @JavascriptInterface public String saveSnapshot(String payload){
    try{
      if(payload==null||payload.length()>200000)throw new IllegalArgumentException("JSON 超出上限");
      JSONObject record=new JSONObject(payload);record.put("saved_at",System.currentTimeMillis());
      File file=new File(app.getFilesDir(),"v3_snapshots.jsonl");
      try(BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file,true),StandardCharsets.UTF_8))){
        writer.write(record.toString());writer.newLine();
      }
      return "{\"saved\":true}";
    }catch(Exception e){return error(e);}
  }
  @JavascriptInterface public String readSnapshots(){
    try{
      File file=new File(app.getFilesDir(),"v3_snapshots.jsonl");
      if(!file.exists())return "[]";
      ArrayList<String> items=new ArrayList<>();
      try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))){
        String s;while((s=reader.readLine())!=null){items.add(s);if(items.size()>80)items.remove(0);}
      }
      JSONArray a=new JSONArray();
      for(int i=items.size()-1;i>=0;i--)a.put(new JSONObject(items.get(i)));
      return a.toString();
    }catch(Exception e){return error(e);}
  }
}
