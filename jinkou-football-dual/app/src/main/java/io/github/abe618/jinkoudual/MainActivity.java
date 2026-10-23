package io.github.abe618.jinkoudual;
import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.util.*;

public final class MainActivity extends Activity {
 static final String[] B={"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};
 static final String[] S={"甲","乙","丙","丁","戊","己","庚","辛","壬","癸"};
 static final String[] E={"木","火","土","金","水"};
 static final int[] BE={4,2,0,0,2,1,1,2,3,3,2,4}, SE={0,0,1,1,2,2,3,3,4,4};
 static final int[] HE={1,0,11,10,9,8,7,6,5,4,3,2};
 static final int[] GS={1,5,6,3,4,2,10,8,7,0,9,11};
 static final String[] GN={"贵人","腾蛇","朱雀","六合","勾陈","青龙","天空","白虎","太常","玄武","太阴","天后"};
 static final String[] JN={"神后","大吉","功曹","太冲","天罡","太乙","胜光","小吉","传送","从魁","河魁","登明"};
 static final String[] POS={"人元","贵神","将神","地分"};
 static final int BG=0xff0b1422,CARD=0xff172b40,FG=0xffe8f4ff,MUT=0xff98b3c9,ACC=0xff75dec1,STROKE=0xff25445b;
 LinearLayout page; ScrollView scroll; Chart fixed,random; long seed; int mode=0; boolean expanded=false; ZonedDateTime manual=null;
 android.content.SharedPreferences prefs;
 static int mod(int a,int n){int v=a%n;return v<0?v+n:v;}
 static boolean sheng(int a,int b){return (a+1)%5==b;}
 static boolean ke(int a,int b){return (a+2)%5==b;}
 static String sb(int[] p){return S[p[0]]+B[p[1]];}
 static int value(String x){if("旺".equals(x))return 3;if("相".equals(x))return 2;if("休".equals(x))return 0;if("囚".equals(x))return -1;return -2;}
 static String clockText(ZonedDateTime t){return String.format(Locale.CHINA,"%04d-%02d-%02d %02d:%02d",t.getYear(),t.getMonthValue(),t.getDayOfMonth(),t.getHour(),t.getMinute());}
 static double solar(ZonedDateTime t) {
  double jd=t.toInstant().toEpochMilli()/86400000.0+2440587.5,T=(jd-2451545.0)/36525.0,r=Math.PI/180;
  double L=280.46646+T*(36000.76983+0.0003032*T);
  double M=(357.52911+T*(35999.05029-0.0001537*T))*r;
  double c=(1.914602-0.004817*T-0.000014*T*T)*Math.sin(M)+(0.019993-0.000101*T)*Math.sin(2*M)+0.000289*Math.sin(3*M);
  double v=L+c-0.00569-0.00478*Math.sin((125.04-1934.136*T)*r);
  return (v%360+360)%360;
 }
 static boolean daylight(ZonedDateTime t,int mi){int h=t.getHour();if(mi>=3&&mi<=5)return h>=3&&h<19;if(mi>=9&&mi<=11)return h>=7&&h<17;return h>=5&&h<17;}
 static final class Chart {
  ZonedDateTime t;int[][] pillar=new int[4][2];int earth,jiang,god,godSlot,noble,mjiang,yuan,shenGan,jiangGan,use,master;
  int[] elem=new int[4];String[] inner=new String[4],outer=new String[4];int[] voids=new int[2];String move="";
  boolean day,border,allSame;double lh,la;long seed;String origin;
  String four(){return sb(pillar[0])+"　"+sb(pillar[1])+"　"+sb(pillar[2])+"　"+sb(pillar[3]);}
 }
 static Chart make(ZonedDateTime time,int earth,long seed,String origin){
  Chart c=new Chart();c.t=time;c.earth=earth;c.origin=origin;c.seed=seed;
  double lon=solar(time);int mi=(int)Math.floor(((lon-315+360)%360)/30);
  int yr=time.getYear(),solarYear=(time.getMonthValue()<=2&&lon<315)?yr-1:yr,yi=mod(solarYear-1984,60);
  c.pillar[0]=new int[]{yi%10,yi%12};
  c.pillar[1]=new int[]{mod(new int[]{2,4,6,8,0}[yi%5]+mi,10),mod(mi+2,12)};
  long delta=time.toLocalDate().toEpochDay()-LocalDate.of(2026,10,21).toEpochDay();
  int di=mod((int)(delta%60)+4,60),hb=(time.getHour()+1)/2%12;
  c.pillar[2]=new int[]{di%10,di%12};
  c.pillar[3]=new int[]{mod((di%10%5)*2+hb,10),hb};
  c.border=Math.min((lon-315+720)%30,30-(lon-315+720)%30)<0.25;
  c.mjiang=HE[c.pillar[1][1]];
  c.jiang=mod(c.mjiang+earth-hb,12);
  int ds=c.pillar[2][0];
  int[] pair=ds==0||ds==4||ds==6?new int[]{1,7}:(ds==1||ds==5?new int[]{0,8}:(ds==2||ds==3?new int[]{11,9}:(ds==8||ds==9?new int[]{5,3}:new int[]{6,2})));
  c.day=daylight(time,mi);c.noble=pair[c.day?0:1];
  int sign=c.noble<=3?1:-1;
  c.godSlot=mod(sign*(earth-c.noble),12);c.god=GS[c.godSlot];
  c.yuan=mod((ds%5)*2+earth,10);c.shenGan=mod((ds%5)*2+c.god,10);c.jiangGan=mod((ds%5)*2+c.jiang,10);
  c.elem=new int[]{SE[c.yuan],BE[c.god],BE[c.jiang],BE[earth]};
  int[] count=new int[5];for(int e:c.elem)count[e]++;
  c.allSame=false;for(int n:count)if(n==4)c.allSame=true;
  int best=-999,candidate=c.elem[0];
  for(int e=0;e<5;e++)if(count[e]>0){
   boolean attacked=false;int victims=0;
   for(int x=0;x<5;x++)if(count[x]>0){if(ke(x,e))attacked=true;if(ke(e,x))victims++;}
   int points=count[e]*3+(attacked?0:8)+victims*2;
   if(points>best){best=points;candidate=e;}
  }
  c.master=candidate;
  for(int i=0;i<4;i++){
   int e=c.elem[i];
   c.inner[i]=c.allSame?"俱比":(e==candidate?"旺":sheng(candidate,e)?"相":sheng(e,candidate)?"休":ke(candidate,e)?"死":"囚");
   int m=BE[c.pillar[1][1]];
   c.outer[i]=e==m?"旺":sheng(m,e)?"相":sheng(e,m)?"休":ke(e,m)?"囚":"死";
  }
  int yin=(c.yuan%2)+(c.god%2)+(c.jiang%2)+(earth%2);
  if(yin==0)c.use=1;else if(yin==4||yin==2)c.use=2;else {
   int target=yin==3?0:1;c.use=c.god%2==target?1:2;
  }
  int start=mod(c.pillar[2][1]-ds,12);
  c.voids[0]=(start+10)%12;c.voids[1]=(start+11)%12;
  ArrayList<String> moves=new ArrayList<>();
  if(ke(c.elem[2],c.elem[1]))moves.add("财动：将克神");
  if(ke(c.elem[1],c.elem[0]))moves.add("官动：神克元");
  if(ke(c.elem[3],c.elem[0]))moves.add("鬼动：分克元");
  if(ke(c.elem[0],c.elem[3]))moves.add("妻动：元克分");
  if(ke(c.elem[1],c.elem[2]))moves.add("贼动：神克将");
  if(sheng(c.elem[3],c.elem[0]))moves.add("父母动：分生元");
  if(c.elem[0]==c.elem[3])moves.add("兄弟动：元分比");
  if(sheng(c.elem[0],c.elem[3]))moves.add("子孙动：元生分");
  c.move=moves.size()==0?"无显著动象":android.text.TextUtils.join("；",moves);
  double home=value(c.inner[2])*.68+value(c.outer[2])*.32;
  double away=value(c.inner[1])*.68+value(c.outer[1])*.32;
  double diff=home-away+(moves.contains("财动：将克神")?.6:0)-(moves.contains("贼动：神克将")?.6:0);
  double intensity=(Math.abs(home)+Math.abs(away))*.05+(moves.size()>2?.08:0);
  c.lh=Math.max(.35,Math.min(2.75,1.30+diff*.18+intensity));
  c.la=Math.max(.35,Math.min(2.75,1.12-diff*.17+intensity));
  return c;
 }
 static final class Pred {
  double win,draw,lose,over,even;int goals,scH,scA;String hf,top;
 }
 static double poiss(int n,double l){double x=Math.exp(-l);for(int i=1;i<=n;i++)x=x*l/i;return x;}
 static Pred predict(double h,double a){
  Pred p=new Pred();double max=-1;double[] num=new double[8];ArrayList<double[]> tops=new ArrayList<>();
  for(int x=0;x<10;x++)for(int y=0;y<10;y++){
   double v=poiss(x,h)*poiss(y,a);
   if(x>y)p.win+=v;else if(x==y)p.draw+=v;else p.lose+=v;
   if(x+y>=3)p.over+=v;if((x+y)%2==0)p.even+=v;
   num[Math.min(x+y,7)]+=v;
   if(v>max){max=v;p.scH=x;p.scA=y;}
   tops.add(new double[]{v,x,y});
  }
  p.goals=0;for(int i=1;i<num.length;i++)if(num[i]>num[p.goals])p.goals=i;
  tops.sort((x,y)->Double.compare(y[0],x[0]));
  p.top=(int)tops.get(0)[1]+":"+(int)tops.get(0)[2]+" / "+(int)tops.get(1)[1]+":"+(int)tops.get(1)[2]+" / "+(int)tops.get(2)[1]+":"+(int)tops.get(2)[2];
  double[] hf=new double[9];String[] s={"胜胜","胜平","胜负","平胜","平平","平负","负胜","负平","负负"};
  for(int x=0;x<6;x++)for(int y=0;y<6;y++)for(int u=0;u<6;u++)for(int v=0;v<6;v++){
   int h0=x>y?0:x==y?1:2,f0=x+u>y+v?0:x+u==y+v?1:2;
   hf[h0*3+f0]+=poiss(x,h*.46)*poiss(y,a*.46)*poiss(u,h*.54)*poiss(v,a*.54);
  }
  int m=0;for(int i=1;i<hf.length;i++)if(hf[i]>hf[m])m=i;p.hf=s[m];return p;
 }
 static String dir(Pred p){return p.win>=p.draw&&p.win>=p.lose?"主胜":p.draw>=p.lose?"平局":"客胜";}
 static String size(Pred p){return p.over>=.5?"大2.5":"小2.5";}
 static String odd(Pred p){return p.even>=.5?"双":"单";}
 static String per(double v){return String.format(Locale.CHINA,"%.1f%%",v*100);}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);prefs=getSharedPreferences("jinkou_v1",0);loadClock();reSeed();render();}
 void loadClock(){ZonedDateTime t=manual==null?ZonedDateTime.now():manual;fixed=make(t,(t.getHour()+1)/2%12,0,"按时支取地分");}
 void reSeed(){seed=new java.security.SecureRandom().nextInt(Integer.MAX_VALUE);rebuildRandom(seed);}
 void rebuildRandom(long s){seed=s;Random rng=new Random(s);ZonedDateTime t=ZonedDateTime.of(2016+rng.nextInt(22),rng.nextInt(12)+1,1+rng.nextInt(28),rng.nextInt(24),rng.nextInt(60),0,0,ZoneId.systemDefault());random=make(t,rng.nextInt(12),s,"独立随机取地分");}
 int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 TextView label(String t,int sz,int color,boolean bold){TextView x=new TextView(this);x.setText(t);x.setTextSize(sz);x.setTextColor(color);if(bold)x.setTypeface(null,Typeface.BOLD);x.setPadding(dp(5),dp(7),dp(5),dp(7));page.addView(x);return x;}
 void btn(String name,Runnable action,boolean primary){
  Button b=new Button(this);b.setText(name);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?BG:FG);
  android.graphics.drawable.GradientDrawable dr=new android.graphics.drawable.GradientDrawable();
  dr.setCornerRadius(dp(13));dr.setColor(primary?ACC:0xff27435b);
  b.setBackground(dr);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(primary?55:47));lp.setMargins(0,dp(6),0,dp(6));page.addView(b,lp);b.setOnClickListener(v->action.run());
 }
 void section(String t){label(t,17,FG,true);}
 void note(String t){label(t,12,MUT,false);}
 void cardText(String t,int size,int color,boolean bold){
  TextView x=label(t,size,color,bold);x.setBackgroundColor(CARD);
  x.setPadding(dp(15),dp(13),dp(15),dp(13));
  LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)x.getLayoutParams();lp.setMargins(0,dp(3),0,dp(3));x.setLayoutParams(lp);
 }
 void render(){
  scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
  page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(16),dp(14),dp(16),dp(25));scroll.addView(page);setContentView(scroll);
  label("金口足球 · 双盘合参",22,FG,true);
  note("干 · 神 · 将 · 方｜正时起课 + 随机起课｜离线 v1.0");
  LinearLayout tabs=new LinearLayout(this);page.addView(tabs);
  for(int i=0;i<3;i++){final int m=i;Button x=new Button(this);x.setAllCaps(false);x.setText(i==0?"双盘合参":i==1?"正时盘":"随机盘");x.setTextSize(12);x.setTextColor(i==mode?ACC:FG);
   LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(0,dp(50),1);tabs.addView(x,l);x.setOnClickListener(v->{mode=m;render();});}
  double h=mode==0?fixed.lh*.65+random.lh*.35:mode==1?fixed.lh:random.lh;
  double a=mode==0?fixed.la*.65+random.la*.35:mode==1?fixed.la:random.la;
  Pred p=predict(h,a),pc=predict(fixed.lh,fixed.la),pr=predict(random.lh,random.la);
  cardText("胜平负  "+dir(p)+"\n"+per(p.win)+" 主胜   "+per(p.draw)+" 平局   "+per(p.lose)+" 客胜",25,ACC,true);
  cardText("首选比分   "+p.scH+":"+p.scA+"           半全场  "+p.hf+"\n总进球       "+(p.goals==7?"7+":p.goals)+"球           大小球  "+size(p)+"\n单双           "+odd(p)+"               两盘权重  "+(mode==0?"65:35":"独立"),18,FG,true);
  cardText("主胜 "+per(p.win)+"     平 "+per(p.draw)+"     客胜 "+per(p.lose)+"\n大2.5 "+per(p.over)+"     小2.5 "+per(1-p.over)+"\n单数 "+per(1-p.even)+"     双数 "+per(p.even)+"\n备选比分 "+p.top,13,MUT,false);
  if(mode==0&&(!dir(pc).equals(dir(pr))||!size(pc).equals(size(pr))))note("⚠ 正时盘与随机盘方向不一致。合参不能视为共同信号。");
  btn("随机下一场",()->{if(manual==null)loadClock();reSeed();render();},true);
  btn("刷新正时盘",()->{manual=null;loadClock();render();},false);
  btn("锁定本次预测",()->freeze(p),false);
  note("正时："+clockText(fixed.t)+"  地分"+B[fixed.earth]+"｜随机种子："+seed+"  地分"+B[random.earth]);
  section("两盘盘面与断课依据");
  btn(expanded?"收起完整盘面":"展开四位盘面及逐项解析",()->{expanded=!expanded;render();},false);
  if(expanded){chartCard(fixed,"【正时盘】",pc);chartCard(random,"【随机盘】",pr);}
  btn("指定正时日期与时间（可选）",()->pickDate(),false);
  btn("输入旧随机种子复演（可选）",()->replaySeed(),false);
  btn("查看已锁定预测与赛果",()->showHistory(),false);
  note("技术边界：太阳黄经和季节昼夜为近似值，节气附近要人工复核。以时支取地分是明确选用的自动起课方法。足球角色对应、泊松进球模型、65/35合参均是现代实验设定，古籍未给出这种足球胜率。");
 }
 void chartCard(Chart c,String name,Pred p){
  String[] symbols={S[c.yuan],S[c.shenGan]+B[c.god],S[c.jiangGan]+B[c.jiang],B[c.earth]};
  StringBuilder b=new StringBuilder(name+"  "+dir(p)+" / "+size(p)+" / "+p.scH+":"+p.scA+"\n");
  b.append("年 月 日 时："+c.four()+"\n");
  for(int i=0;i<4;i++)b.append(POS[i]).append(i==c.use?"★":"　").append("  ").append(symbols[i]).append("  ")
   .append(E[c.elem[i]]).append("  内").append(c.inner[i]).append("／外").append(c.outer[i]).append("\n");
  b.append("起课：").append(clockText(c.t)).append(" · ").append(c.origin).append("\n");
  b.append("月建 ").append(B[c.pillar[1][1]]).append(" · 月将 ").append(B[c.mjiang]).append(" · 入课将 ").append(B[c.jiang]).append("\n");
  b.append(c.day?"昼贵":"夜贵").append("：起贵").append(B[c.noble]).append(c.noble<=3?" 顺布":" 逆布")
   .append(" → ").append(GN[c.godSlot]).append(B[c.god]).append("\n");
  b.append("用神 ").append(POS[c.use]).append("  旬空").append(B[c.voids[0]]).append(B[c.voids[1]]).append("\n");
  b.append("主旺五行 ").append(E[c.master]).append(c.allSame?"（四位俱比）":"").append("\n");
  b.append("五动三动：").append(c.move).append("\n");
  b.append("实验映射：将神作主队，贵神作客队；主进球强度").append(String.format(Locale.CHINA,"%.2f",c.lh))
    .append("，客进球强度").append(String.format(Locale.CHINA,"%.2f",c.la)).append("\n");
  b.append("实验输出：").append(dir(p)).append("　").append(p.hf).append("　").append(p.scH).append(":").append(p.scA).append("　").append(size(p)).append("　").append(odd(p)).append("\n");
  if(c.border)b.append("⚠ 近节气交界，请核对天文历数据。\n");
  b.append("引用：《金口解秘》物理17—24页。五动三动依第24页定义；贵神采用掌盘起点定顺逆，未混入入门书另一算法。");
  cardText(b.toString(),13,FG,false);
 }
 void freeze(Pred p){
  try{
   JSONArray a=new JSONArray(prefs.getString("freeze","[]"));
   JSONObject o=new JSONObject();
   o.put("time",clockText(ZonedDateTime.now()));o.put("seed",seed);
   o.put("direction",dir(p));o.put("score",p.scH+":"+p.scA);o.put("ht",p.hf);o.put("size",size(p));o.put("odd",odd(p));
   JSONArray n=new JSONArray();n.put(o);for(int i=0;i<a.length()&&i<49;i++)n.put(a.getJSONObject(i));
   prefs.edit().putString("freeze",n.toString()).apply();
   Toast.makeText(this,"已锁定记录",Toast.LENGTH_SHORT).show();
  }catch(Exception ex){Toast.makeText(this,"保存失败",Toast.LENGTH_SHORT).show();}
 }
 void showHistory(){
  try{
   JSONArray a=new JSONArray(prefs.getString("freeze","[]"));StringBuilder b=new StringBuilder("本机锁定预测（最近50条）\n\n");
   for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);b.append(i+1).append("  ").append(o.optString("time")).append("\n");
    b.append(o.optString("direction")).append("  ").append(o.optString("score")).append("  ").append(o.optString("ht")).append("  ")
     .append(o.optString("size")).append("  ").append(o.optString("odd")).append("  种子:").append(o.optLong("seed")).append("\n");
    if(o.has("actual"))b.append("赛果 ").append(o.optString("actual")).append("\n");b.append("\n");}
   if(a.length()==0)b.append("暂无锁定预测。");
   new AlertDialog.Builder(this).setTitle("锁定及复盘").setMessage(b.toString()).setPositiveButton("关闭",null)
    .setNeutralButton("录入最近一场赛果",(d,w)->{if(a.length()==0)return;EditText e=new EditText(this);e.setHint("实际比分，例如 1:0");new AlertDialog.Builder(this).setTitle("赛果").setView(e).setPositiveButton("保存",(d2,w2)->{try{String v=e.getText().toString().trim();if(!v.matches("\\d{1,2}:\\d{1,2}"))return;a.getJSONObject(0).put("actual",v);prefs.edit().putString("freeze",a.toString()).apply();}catch(Exception ex){}}).setNegativeButton("取消",null).show();}).show();
  }catch(Exception ex){new AlertDialog.Builder(this).setMessage("历史数据无法读取").setPositiveButton("确定",null).show();}
 }
 void replaySeed(){
  EditText x=new EditText(this);x.setInputType(2);x.setHint("输入0至2147483647之间的种子");
  new AlertDialog.Builder(this).setTitle("随机盘复演").setView(x).setPositiveButton("排盘",(d,w)->{try{long n=Long.parseLong(x.getText().toString());if(n>=0&&n<=2147483647L){rebuildRandom(n);render();}}catch(Exception ignored){Toast.makeText(this,"种子无效",Toast.LENGTH_SHORT).show();}}).setNegativeButton("取消",null).show();
 }
 void pickDate(){
  ZonedDateTime t=manual==null?ZonedDateTime.now():manual;
  new DatePickerDialog(this,(picker,y,m,d)->new TimePickerDialog(this,(tp,h,min)->{manual=ZonedDateTime.of(y,m+1,d,h,min,0,0,ZoneId.systemDefault());loadClock();render();},t.getHour(),t.getMinute(),true).show(),t.getYear(),t.getMonthValue()-1,t.getDayOfMonth()).show();
 }
}
