package com.abe.threearm;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import java.util.*;

public class MainActivity extends Activity {
    private final int BG=Color.rgb(244,246,241), INK=Color.rgb(23,35,46), MUTED=Color.rgb(91,104,112), RULE=Color.rgb(205,211,202), ACCENT=Color.rgb(52,72,154), WHITE=Color.WHITE;
    private LedgerStore store; private LedgerStore.Sample current; private LinearLayout root;
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);store=new LedgerStore(this);current=store.latest();render();}

    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);} private TextView tv(String s,float sp,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(c);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private GradientDrawable box(int color,float r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));g.setStroke(dp(1),RULE);return g;}
    private void pad(View v,int l,int t,int r,int b){v.setPadding(dp(l),dp(t),dp(r),dp(b));}
    private LinearLayout.LayoutParams lp(int w,int h,float weight){return new LinearLayout.LayoutParams(w,h,weight);}

    private void render(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);pad(root,14,10,14,10);setContentView(root);
        if(current==null){current=store.add(PredictionEngine.newSeed());}
        PredictionEngine.Forecast f=PredictionEngine.forecast(current.seed);
        renderHeader(f);renderFour(f);renderFusion(f);renderArms(f);renderButtons();
    }

    private void renderHeader(PredictionEngine.Forecast f){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=tv("三轨盲测 · 离线版",21,INK);title.setTypeface(Typeface.DEFAULT_BOLD);row.addView(title,lp(0,dp(44),1));
        TextView id=tv("#"+current.id,18,ACCENT);id.setTypeface(Typeface.DEFAULT_BOLD);row.addView(id,new LinearLayout.LayoutParams(dp(58),dp(44)));
        root.addView(row);
        TextView sub=tv("Seed "+shortSeed(current.seed)+"  ·  "+current.resultText()+"  ·  无网络权限",11,MUTED);sub.setSingleLine(true);root.addView(sub,new LinearLayout.LayoutParams(-1,dp(24)));
    }

    private void renderFour(PredictionEngine.Forecast f){
        TextView lab=tv("四术合参",13,MUTED);lab.setTypeface(Typeface.DEFAULT_BOLD);root.addView(lab,new LinearLayout.LayoutParams(-1,dp(25)));
        GridLayout g=new GridLayout(this);g.setColumnCount(2);g.setRowCount(2);g.setUseDefaultMargins(false);
        for(PredictionEngine.Reading r:f.readings){
            LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setBackground(box(Color.rgb(250,251,248),7));pad(c,9,5,9,5);c.setOnClickListener(v->showReading(r));
            TextView a=tv(r.system+"   "+r.direction+" · "+r.strength+" · "+r.goals+"球",13,INK);a.setTypeface(Typeface.DEFAULT_BOLD);c.addView(a,new LinearLayout.LayoutParams(-1,dp(23)));
            String tg=r.tags.isEmpty()?"—":joinZh(r.tags);TextView b=tv(tg+"  |  "+compactCast(r.cast),10,MUTED);b.setSingleLine(true);c.addView(b,new LinearLayout.LayoutParams(-1,dp(19)));
            GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=dp(50);p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);p.setMargins(dp(2),dp(2),dp(2),dp(2));g.addView(c,p);
        }
        root.addView(g,new LinearLayout.LayoutParams(-1,dp(108)));
    }

    private void renderFusion(PredictionEngine.Forecast f){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setBackground(box(WHITE,9));pad(card,11,7,11,7);
        PredictionEngine.Markets m=f.A.mk;String dir=bestDir(m);double best=bestDirP(m);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView k=tv("合参结果",14,MUTED);k.setTypeface(Typeface.DEFAULT_BOLD);top.addView(k,lp(0,dp(30),1));
        TextView big=tv(dir+" "+pct(best),20,ACCENT);big.setTypeface(Typeface.DEFAULT_BOLD);top.addView(big,new LinearLayout.LayoutParams(dp(140),dp(30)));card.addView(top);
        TextView line1=tv("主 "+pct(m.home)+"   平 "+pct(m.draw)+"   客 "+pct(m.away)+"   ｜ 稳："+bestDouble(m),13,INK);line1.setSingleLine(true);card.addView(line1,new LinearLayout.LayoutParams(-1,dp(24)));
        int core=0;for(int i=1;i<9;i++)if(m.goals[i]>m.goals[core])core=i;
        TextView line2=tv("总球 "+core+"（期望 "+f2(m.expGoals)+"）   大2.5 "+pct(m.over25)+"   单/双 "+pct(m.odd)+"/"+pct(m.even),12,INK);line2.setSingleLine(true);card.addView(line2,new LinearLayout.LayoutParams(-1,dp(23)));
        String sc=m.topScores.get(0).score()+"  "+m.topScores.get(1).score()+"  "+m.topScores.get(2).score();String hf=topHtFt(f.A.hf,3);
        TextView line3=tv("比分 "+sc+"   ｜ 半全场 "+hf,11,MUTED);line3.setSingleLine(true);card.addView(line3,new LinearLayout.LayoutParams(-1,dp(22)));
        root.addView(card,new LinearLayout.LayoutParams(-1,dp(107)));
    }

    private void renderArms(PredictionEngine.Forecast f){
        TextView lab=tv("三轨对照",13,MUTED);lab.setTypeface(Typeface.DEFAULT_BOLD);root.addView(lab,new LinearLayout.LayoutParams(-1,dp(25)));
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setBackground(box(Color.rgb(250,251,248),8));pad(card,9,4,9,4);
        addArm(card,"A 四术",f.A,true);addArm(card,"B1 基线",f.B1,false);addArm(card,"B2 对照",f.B2,false);
        root.addView(card,new LinearLayout.LayoutParams(-1,dp(95)));
    }
    private void addArm(LinearLayout p,String name,PredictionEngine.Arm a,boolean strong){TextView t=tv(name+"   主"+pct(a.mk.home)+"  平"+pct(a.mk.draw)+"  客"+pct(a.mk.away)+"   总球"+f2(a.mk.expGoals),11,strong?ACCENT:INK);if(strong)t.setTypeface(Typeface.DEFAULT_BOLD);p.addView(t,new LinearLayout.LayoutParams(-1,dp(28)));}

    private void renderButtons(){
        Space s=new Space(this);root.addView(s,lp(-1,0,1));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);row.setWeightSum(3);row.addView(btn("下一场",true,v->{current=store.add(PredictionEngine.newSeed());render();}),lp(0,dp(48),1));row.addView(btn("录赛果",false,v->scoreDialog()),lp(0,dp(48),1));row.addView(btn("历史 / 累计",false,v->statsDialog()),lp(0,dp(48),1));root.addView(row,new LinearLayout.LayoutParams(-1,dp(52)));
    }
    private Button btn(String s,boolean pri,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(pri?WHITE:INK);GradientDrawable g=box(pri?INK:Color.TRANSPARENT,7);if(!pri)g.setStroke(dp(1),RULE);b.setBackground(g);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.setMargins(dp(3),0,dp(3),0);b.setLayoutParams(p);b.setOnClickListener(l);return b;}

    private void scoreDialog(){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);pad(c,20,10,20,4);
        TextView h=tv("样本 #"+current.id+" 录入全场比分",16,INK);h.setTypeface(Typeface.DEFAULT_BOLD);c.addView(h,new LinearLayout.LayoutParams(-1,dp(42)));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);EditText eh=scoreInput(),ea=scoreInput();row.addView(eh);row.addView(tv(" : ",24,INK));row.addView(ea);c.addView(row,new LinearLayout.LayoutParams(-1,dp(64)));
        AlertDialog d=new AlertDialog.Builder(this).setView(c).setPositiveButton("保存",null).setNeutralButton("只记方向",null).setNegativeButton("取消",null).create();
        d.setOnShowListener(x->{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String a=eh.getText().toString(),b=ea.getText().toString();if(a.matches("\\d{1,2}")&&b.matches("\\d{1,2}")){store.setScore(current,Integer.parseInt(a),Integer.parseInt(b));d.dismiss();render();}else Toast.makeText(this,"请输入 0–99 的完整比分",Toast.LENGTH_SHORT).show();});d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{new AlertDialog.Builder(this).setTitle("只记录赛果方向").setItems(new String[]{"主胜","平局","客胜"},(q,w)->{store.setDirection(current,new String[]{"主","平","客"}[w]);d.dismiss();render();}).show();});});d.show();
    }
    private EditText scoreInput(){EditText e=new EditText(this);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setGravity(Gravity.CENTER);e.setTextSize(24);e.setSingleLine(true);e.setSelectAllOnFocus(true);e.setLayoutParams(new LinearLayout.LayoutParams(dp(70),dp(54)));return e;}

    private void statsDialog(){
        LedgerStore.Stats s=store.stats();LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);pad(c,20,12,20,12);
        TextView h=tv("累计：已复盘 "+s.done+" · 待结果 "+s.pending,18,INK);h.setTypeface(Typeface.DEFAULT_BOLD);c.addView(h,new LinearLayout.LayoutParams(-1,dp(44)));
        String[] names={"A 四术","B1 气候基线","B2 随机对照"};for(int i=0;i<3;i++){String z=s.done==0?"—":String.format(Locale.US,"%.4f",s.rps[i]);TextView t=tv(names[i]+"    RPS "+z+"    方向 "+s.hits[i]+"/"+s.done,13,i==0?ACCENT:INK);if(i==0)t.setTypeface(Typeface.DEFAULT_BOLD);c.addView(t,new LinearLayout.LayoutParams(-1,dp(34)));}
        TextView note=tv("RPS 越低越好。原项目规则：A 必须同时显著优于 B1、B2，且样本达到约 150 场后再下结论。",11,MUTED);note.setPadding(0,dp(8),0,0);c.addView(note,new LinearLayout.LayoutParams(-1,dp(58)));
        StringBuilder hist=new StringBuilder();List<LedgerStore.Sample> all=store.all();for(int i=all.size()-1;i>=Math.max(0,all.size()-8);i--){LedgerStore.Sample q=all.get(i);hist.append("#").append(q.id).append("  ").append(q.resultText()).append("\n");}TextView hs=tv(hist.toString().trim(),12,INK);c.addView(hs,new LinearLayout.LayoutParams(-1,dp(145)));
        new AlertDialog.Builder(this).setView(c).setPositiveButton("关闭",null).show();
    }

    private void showReading(PredictionEngine.Reading r){String tags=r.tags.isEmpty()?"—":joinZh(r.tags);new AlertDialog.Builder(this).setTitle(r.system).setMessage("起盘："+r.cast+"\n方向："+r.direction+"\n强度："+r.strength+"\n进球层："+r.goals+"\n结构："+tags).setPositiveButton("关闭",null).show();}
    private String joinZh(List<String> xs){List<String> z=new ArrayList<>();for(String x:xs)z.add(PredictionEngine.TAG_ZH.getOrDefault(x,x));return String.join("、",z);} private String compactCast(String s){return s.length()>19?s.substring(0,19)+"…":s;} private String shortSeed(String s){return s.length()>12?s.substring(0,6)+"…"+s.substring(s.length()-4):s;}
    private String pct(double x){return String.format(Locale.US,"%.1f%%",x*100);} private String f2(double x){return String.format(Locale.US,"%.2f",x);} private String bestDir(PredictionEngine.Markets m){if(m.home>=m.draw&&m.home>=m.away)return"主胜";if(m.draw>=m.away)return"平局";return"客胜";} private double bestDirP(PredictionEngine.Markets m){return Math.max(m.home,Math.max(m.draw,m.away));}
    private String bestDouble(PredictionEngine.Markets m){double a=m.oneX(),b=m.twelve(),c=m.xTwo();if(a>=b&&a>=c)return"主不败 "+pct(a);if(b>=c)return"有胜负 "+pct(b);return"客不败 "+pct(c);} private String topHtFt(PredictionEngine.HtFt h,int n){List<Map.Entry<String,Double>> x=new ArrayList<>(h.flat.entrySet());x.sort((a,b)->Double.compare(b.getValue(),a.getValue()));List<String> z=new ArrayList<>();for(int i=0;i<Math.min(n,x.size());i++)z.add(x.get(i).getKey());return String.join("/",z);}
}
