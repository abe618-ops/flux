package com.abe.shushufootball;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import java.text.*;
import java.util.*;
import java.security.SecureRandom;

public class MainActivity extends Activity {
    private final int BG=Color.rgb(245,242,234), CARD=Color.WHITE, INK=Color.rgb(49,42,34), ACCENT=Color.rgb(107,79,43), MUTED=Color.rgb(112,105,96);
    private EditText homeEdit,awayEdit,dateEdit,timeEdit;
    private RadioButton randomMode;
    private TextView seedView,summaryView,detailView;
    private LinearLayout tabs;
    private final List<R> results=new ArrayList<>();
    private R ensemble;
    private int selected=0;
    private long randomMasterSeed=0L;
    private final SecureRandom secureRandom=new SecureRandom();

    private static final String[] NAMES={"合参","梅花","太玄","轨策","大定","演禽","铁板"};
    private static final String[] TRI={"乾","兑","离","震","巽","坎","艮","坤"};
    private static final String[] ELEM={"金","金","火","木","木","水","土","土"};
    private static final int[] CE={36,28,28,32,28,32,32,24};
    private static final String[] XIU={
        "角木蛟","亢金龙","氐土貉","房日兔","心月狐","尾火虎","箕水豹",
        "斗木獬","牛金牛","女土蝠","虚日鼠","危月燕","室火猪","壁水貐",
        "奎木狼","娄金狗","胃土雉","昴日鸡","毕月乌","觜火猴","参水猿",
        "井木犴","鬼金羊","柳土獐","星日马","张月鹿","翼火蛇","轸水蚓"};

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT>=23)getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        buildUi();
        generate(false);
    }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root=col(); root.setPadding(dp(18),dp(18),dp(18),dp(36)); scroll.addView(root);

        root.addView(txt("术数足球合参",28,INK,true));
        TextView sub=txt("六门独立排盘 · 时刻盘 / 随机盘 · 统一比分概率层",13,MUTED,false);
        sub.setPadding(0,dp(5),0,dp(14)); root.addView(sub);

        LinearLayout c=card(); root.addView(c,lp(-1,-2,0,0,0,dp(12)));
        LinearLayout teams=row();
        homeEdit=input("主队","主队"); awayEdit=input("客队","客队");
        teams.addView(homeEdit,new LinearLayout.LayoutParams(0,dp(50),1));
        teams.addView(space(dp(10),1));
        teams.addView(awayEdit,new LinearLayout.LayoutParams(0,dp(50),1));
        c.addView(teams);

        Calendar now=Calendar.getInstance();
        dateEdit=input("比赛日期",new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(now.getTime()));
        timeEdit=input("开赛时间",new SimpleDateFormat("HH:mm",Locale.US).format(now.getTime()));
        LinearLayout dt=row(); dt.setPadding(0,dp(10),0,0);
        dt.addView(dateEdit,new LinearLayout.LayoutParams(0,dp(50),1));
        dt.addView(space(dp(10),1));
        dt.addView(timeEdit,new LinearLayout.LayoutParams(0,dp(50),1));
        c.addView(dt);

        RadioGroup rg=new RadioGroup(this); rg.setOrientation(RadioGroup.HORIZONTAL); rg.setPadding(0,dp(8),0,0);
        RadioButton timeMode=new RadioButton(this); timeMode.setText("比赛时刻盘"); timeMode.setChecked(true); timeMode.setTextColor(INK);
        randomMode=new RadioButton(this); randomMode.setText("随机实验盘"); randomMode.setTextColor(INK);
        rg.addView(timeMode); rg.addView(randomMode); c.addView(rg);

        Button gen=btn("生成 / 重新起盘",true); gen.setOnClickListener(v->generate(true)); c.addView(gen,lp(-1,dp(48),0,dp(8),0,0));
        seedView=txt("",12,MUTED,false); seedView.setPadding(dp(2),dp(8),0,0); c.addView(seedView);

        HorizontalScrollView hsv=new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false);
        tabs=row(); hsv.addView(tabs); root.addView(hsv,lp(-1,dp(50),0,dp(2),0,dp(10)));
        for(int i=0;i<NAMES.length;i++){
            final int idx=i; Button b=btn(NAMES[i],false);
            b.setOnClickListener(v->{selected=idx; paintTabs(); render();});
            tabs.addView(b,lp(dp(72),dp(42),0,0,dp(6),0));
        }

        LinearLayout rc=card(); root.addView(rc,lp(-1,-2,0,0,0,dp(12)));
        rc.addView(txt("预测结果",17,INK,true));
        summaryView=txt("",15,INK,false); summaryView.setTypeface(Typeface.MONOSPACE); summaryView.setLineSpacing(0,1.15f); summaryView.setPadding(0,dp(10),0,0); rc.addView(summaryView);

        LinearLayout dc=card(); root.addView(dc);
        dc.addView(txt("盘面与推导",17,INK,true));
        detailView=txt("",13,INK,false); detailView.setTypeface(Typeface.MONOSPACE); detailView.setLineSpacing(0,1.12f); detailView.setPadding(0,dp(10),0,0); dc.addView(detailView);
        setContentView(scroll);
    }

    private void generate(boolean reroll){
        String h=clean(homeEdit.getText().toString(),"主队"), a=clean(awayEdit.getText().toString(),"客队");
        long epoch=parseEpoch(); boolean random=randomMode.isChecked();
        if(random&&(reroll||randomMasterSeed==0L))randomMasterSeed=secureRandom.nextLong();
        if(!random)randomMasterSeed=0L;
        long base=random?randomMasterSeed:mix(epoch^hash(h+"|"+a));
        results.clear();
        results.add(meihua(base^0x13579BDF2468ACE1L,epoch,h,a));
        results.add(taixuan(base^0x2468ACE113579BDFL,epoch,h,a));
        results.add(cegui(base^0x55AA33CC77EE1199L,epoch,h,a));
        results.add(dading(base^0x1029384756ABCDEFL,epoch,h,a));
        results.add(yanqin(base^0x7F4A7C159E3779B9L,epoch,h,a));
        results.add(tieban(base^0x6A09E667F3BCC909L,epoch,h,a));
        ensemble=ensemble(results,h,a,base);
        seedView.setText(random?"随机盘 Seed："+hex(randomMasterSeed)+"（可复盘）":"时刻盘：同一球队 + 同一时间 → 同一结果");
        paintTabs(); render();
    }

    private R meihua(long seed,long epoch,String h,String a){
        Calendar c=cal(epoch); int y=c.get(Calendar.YEAR),m=c.get(Calendar.MONTH)+1,d=c.get(Calendar.DAY_OF_MONTH),hr=c.get(Calendar.HOUR_OF_DAY),mi=c.get(Calendar.MINUTE);
        int up=1+mod(y+m+d,8),lo=1+mod(y+m+d+hr,8),move=1+mod(y+m+d+hr+mi,6);
        int body=move<=3?up:lo,use=move<=3?lo:up;
        String be=ELEM[body-1],ue=ELEM[use-1]; double diff=relation(be,ue)*0.72+center(seed,8)*0.42+(up-lo)*0.055;
        double pace=2.15+mod(up+lo+move,6)*0.14+center(seed,22)*0.18;
        String t="【梅花神易】\n上卦="+TRI[up-1]+"("+ELEM[up-1]+")  下卦="+TRI[lo-1]+"("+ELEM[lo-1]+")\n动爻="+move+"  体="+TRI[body-1]+"  用="+TRI[use-1]+"\n体用关系="+relText(be,ue)+"\n足球适配：体=主、用=客；体用生克定方向，卦数与动爻调总球。";
        return make("梅花",diff,pace,seed,h,a,t);
    }

    private R taixuan(long seed,long epoch,String h,String a){
        Calendar c=cal(epoch); long day=floorDiv(epoch,86400000L);
        int t1=1+mod((int)(day+(seed>>>1)),3),t2=1+mod((int)(day/3+c.get(Calendar.HOUR_OF_DAY)+(seed>>>7)),3);
        int t3=1+mod((int)(day/9+c.get(Calendar.MINUTE)+(seed>>>13)),3),t4=1+mod((int)(day/27+c.get(Calendar.MONTH)+(seed>>>19)),3);
        int state=1+(t1-1)*27+(t2-1)*9+(t3-1)*3+(t4-1),praise=1+mod(c.get(Calendar.MINUTE)+(int)(seed>>>25),9);
        int g1=tx(mod((int)day,10),true),g2=tx(mod((int)day,12),false);
        double diff=((t1+t2)-(t3+t4))*0.34+(g1-g2)*0.055+center(seed,31)*0.25;
        double pace=1.95+mod(state,7)*0.17+(praise-5)*0.035;
        String t="【太玄81首】\n玄数="+t1+"·"+t2+"·"+t3+"·"+t4+" → 第"+state+"首\n九赞取="+praise+"  干支太玄辅助数="+g1+"/"+g2+"\n三态用于主客态势；81首/九赞负责节奏与离散化。\n干支太玄数只作辅助，不替代81首本体。";
        return make("太玄",diff,pace,seed,h,a,t);
    }

    private R cegui(long seed,long epoch,String h,String a){
        Calendar c=cal(epoch); int q1=1+mod(c.get(Calendar.YEAR)+c.get(Calendar.MONTH)+1,8),q2=1+mod(c.get(Calendar.DAY_OF_MONTH)+c.get(Calendar.HOUR_OF_DAY),8);
        int s1=CE[q1-1],s2=CE[q2-1]; long n=mix(seed^(((long)s1)<<32)^s2)&Long.MAX_VALUE;
        int yuan=1+(int)(n%9),hui=1+(int)((n/11)%9),yun=1+(int)((n/101)%9),shi=1+(int)((n/1009)%9);
        double diff=((yuan+hui)-(yun+shi))*0.19+((s1-s2)/8.0)*0.22;
        double pace=2.00+((yuan+yun)%7)*0.15+center(seed,40)*0.12;
        String t="【演周易 / 策轨】\n先天卦="+TRI[q1-1]+" 策="+s1+"；后天卦="+TRI[q2-1]+" 轨="+s2+"\n现代竞技四位：元="+yuan+" 会="+hui+" 运="+yun+" 世="+shi+"\n主侧(元会)="+(yuan+hui)+"  客侧(运世)="+(yun+shi)+"\n元会→主侧、运世→客侧属于足球适配层，不标作古籍原义。";
        return make("轨策",diff,pace,seed,h,a,t);
    }

    private R dading(long seed,long epoch,String h,String a){
        Calendar c=cal(epoch); int y=c.get(Calendar.YEAR),mo=c.get(Calendar.MONTH)+1,d=c.get(Calendar.DAY_OF_MONTH),hr=c.get(Calendar.HOUR_OF_DAY),mi=c.get(Calendar.MINUTE);
        int heaven=1+mod(y+mo,9),earth=1+mod(d+hr,9),human=1+mod(hr+mi,9),thing=1+mod((int)(seed>>>17),9);
        int homeQi=heaven+human+mod(d,6),awayQi=earth+thing+mod(hr,6),move=1+mod((int)(seed^epoch),6);
        double diff=(homeQi-awayQi)*0.16+(move-3.5)*0.07;
        double pace=1.90+mod(heaven+earth+human+thing,8)*0.15;
        String t="【大定 / 心易数理】\n天数="+heaven+" 地数="+earth+" 人数="+human+" 物数="+thing+"\n动数="+move+"  主家气数="+homeQi+" 客家气数="+awayQi+"\n足球适配优先采用“两家气数 / 出师胜负”的事件框架，不套人命断语。";
        return make("大定",diff,pace,seed,h,a,t);
    }

    private R yanqin(long seed,long epoch,String h,String a){
        Calendar c=cal(epoch); long day=floorDiv(epoch,86400000L),anchor=daysUtc(1986,5,29);
        int dayIdx=mod((int)(day-anchor)+7,28),branch=((c.get(Calendar.HOUR_OF_DAY)+1)/2)%12,yuan=mod((int)(day-anchor),7);
        int hourIdx=mod(dayIdx+branch*2+yuan,28),fanIdx=mod(dayIdx*2-hourIdx+28,28),daoIdx=mod(hourIdx+14+yuan,28);
        String me=xiuElem(hourIdx),they=xiuElem(fanIdx); int dist=Math.min(mod(hourIdx-fanIdx,28),mod(fanIdx-hourIdx,28));
        double diff=relation(me,they)*0.82+center(seed,51)*0.24+(xiuElem(daoIdx).equals(me)?0.13:-0.04);
        double pace=1.85+(dist%8)*0.14+(sunMoon(hourIdx)||sunMoon(fanIdx)?0.16:0);
        String t="【演禽 · 翻禽倒将】\n日禽="+XIU[dayIdx]+"  时禽(我)="+XIU[hourIdx]+"\n翻禽(彼)="+XIU[fanIdx]+"  倒将="+XIU[daoIdx]+"\n我彼五行="+me+" / "+they+" → "+relText(me,they)+"\n足球适配：时禽=我(主)，翻禽=彼(客)，倒将作交叉校验。";
        return make("演禽",diff,pace,seed,h,a,t);
    }

    private R tieban(long seed,long epoch,String h,String a){
        Calendar c=cal(epoch); int hr=c.get(Calendar.HOUR_OF_DAY),mi=c.get(Calendar.MINUTE),mins=(hr%2)*60+mi,quarter=Math.min(7,mins/15);
        long code=mix(seed^epoch^quarter*48L)&Long.MAX_VALUE;
        int d1=(int)(code%10),d2=(int)((code/10)%10),d3=(int)((code/100)%10),d4=(int)((code/1000)%10);
        int left=d1+d2+quarter+1,right=d3+d4+(8-quarter),roll=1+mod((int)(code/10000),64);
        double diff=(left-right)*0.15+center(seed,61)*0.30;
        double pace=1.95+mod(d1+d2+d3+d4,8)*0.14;
        String t="【铁板 / 邵子数字核】\n时辰八刻索引="+(quarter+1)+"  数码="+d4+d3+d2+d1+"  八卦滚序="+roll+"\n主侧数字="+left+"  客侧数字="+right+"\n本模块仅使用取数/滚数引擎，不调用12000条人命条文。";
        return make("铁板",diff,pace,seed,h,a,t);
    }

    private R ensemble(List<R> rs,String h,String a,long seed){
        double lh=0,la=0,hs=0,as=0; StringBuilder t=new StringBuilder("【六门合参】\n");
        for(R r:rs){lh+=r.lh;la+=r.la;hs+=r.hs;as+=r.as;t.append(String.format(Locale.CHINA,"%-4s λ %.2f : %.2f  %s  %s\n",r.name,r.lh,r.la,r.wdl,r.score));}
        lh/=rs.size();la/=rs.size();hs/=rs.size();as/=rs.size();
        t.append("\n第一版等权合参；后续可按胜平负/大小/单双/总球MAE分别学习权重。\n");
        return fromLambda("合参",lh,la,hs,as,seed,t.toString());
    }

    private R make(String name,double diff,double pace,long seed,String h,String a,String trace){
        diff=Math.max(-2.7,Math.min(2.7,diff)); pace=Math.max(1.25,Math.min(4.45,pace));
        double share=1.0/(1.0+Math.exp(-diff));
        double lh=0.18+(pace-0.36)*share,la=0.18+(pace-0.36)*(1-share);
        return fromLambda(name,lh,la,50+diff*14,50-diff*14,seed,trace);
    }

    private R fromLambda(String name,double lh,double la,double hs,double as,long seed,String trace){
        lh=Math.max(0.10,Math.min(4.80,lh)); la=Math.max(0.10,Math.min(4.80,la));
        double pw=0,pd=0,pl=0; List<S> all=new ArrayList<>();
        for(int i=0;i<=6;i++)for(int j=0;j<=6;j++){double p=pois(i,lh)*pois(j,la);all.add(new S(i,j,p));if(i>j)pw+=p;else if(i==j)pd+=p;else pl+=p;}
        Collections.sort(all,(x,y)->Double.compare(y.p,x.p)); S top=all.get(0),ht=topScore(lh*0.45,la*0.45);
        String wdl=(pw>=pd&&pw>=pl)?"主胜":(pl>=pd?"客胜":"平");
        String htr=ht.h>ht.a?"主":ht.h<ht.a?"客":"平",ftr=top.h>top.a?"主":top.h<top.a?"客":"平";
        double total=lh+la,under=Math.exp(-total)*(1+total+total*total/2.0);
        int tg=top.h+top.a;
        R r=new R();r.name=name;r.lh=lh;r.la=la;r.hs=hs;r.as=as;r.wdl=wdl;r.htft=htr+"/"+ftr;r.total=tg;r.score=top.score();
        r.top3=all.get(0).score()+" / "+all.get(1).score()+" / "+all.get(2).score();r.ou=(1-under)>=0.5?"大2.5":"小2.5";r.parity=tg%2==0?"双":"单";r.net=top.h-top.a;r.seed=seed;r.trace=trace;
        r.probs=String.format(Locale.CHINA,"主 %.1f%%  平 %.1f%%  客 %.1f%%",pw*100,pd*100,pl*100);return r;
    }

    private void render(){
        R r=selected==0?ensemble:results.get(selected-1); if(r==null)return;
        summaryView.setText("胜平负  "+r.wdl+"\n"+r.probs+"\n\n半全场  "+r.htft+"\n总进球  "+r.total+"\n比分首选  "+r.score+"\n比分前三  "+r.top3+"\n大小球  "+r.ou+"\n进球单双  "+r.parity+"\n净胜球  "+(r.net>0?"+":"")+r.net+"\n\n预期进球 λ  "+fmt(r.lh)+" : "+fmt(r.la)+"\n强弱指数    "+fmt(r.hs)+" : "+fmt(r.as));
        detailView.setText(r.trace+"\n\n统一映射层：盘面 → 主客强弱差/比赛节奏 → λ主/λ客 → 0:0~6:6 泊松比分矩阵。\n因此胜平负、比分、总球、大小和单双来自同一组参数。\n内部Seed："+hex(r.seed));
    }

    private void paintTabs(){
        for(int i=0;i<tabs.getChildCount();i++){Button b=(Button)tabs.getChildAt(i);boolean on=i==selected;b.setTextColor(on?Color.WHITE:INK);b.setBackground(round(on?ACCENT:Color.rgb(232,227,217),12));}
    }

    private long parseEpoch(){
        try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);f.setLenient(false);return f.parse(dateEdit.getText().toString().trim()+" "+timeEdit.getText().toString().trim()).getTime();}
        catch(Exception e){return System.currentTimeMillis();}
    }

    private S topScore(double lh,double la){S best=null;for(int i=0;i<=5;i++)for(int j=0;j<=5;j++){double p=pois(i,lh)*pois(j,la);if(best==null||p>best.p)best=new S(i,j,p);}return best;}
    private double pois(int k,double l){double v=Math.exp(-l);for(int i=1;i<=k;i++)v*=l/i;return v;}
    private double relation(String a,String b){if(a.equals(b))return 0;if(over(a,b))return 1;if(over(b,a))return -1;if(gen(b,a))return .65;if(gen(a,b))return -.50;return 0;}
    private String relText(String a,String b){double r=relation(a,b);return r>=.9?"我克彼":r<=-.9?"彼克我":r>.5?"彼生我":r<-.4?"我生彼":"比和";}
    private boolean gen(String a,String b){return(a.equals("木")&&b.equals("火"))||(a.equals("火")&&b.equals("土"))||(a.equals("土")&&b.equals("金"))||(a.equals("金")&&b.equals("水"))||(a.equals("水")&&b.equals("木"));}
    private boolean over(String a,String b){return(a.equals("木")&&b.equals("土"))||(a.equals("土")&&b.equals("水"))||(a.equals("水")&&b.equals("火"))||(a.equals("火")&&b.equals("金"))||(a.equals("金")&&b.equals("木"));}
    private String xiuElem(int i){char c=XIU[mod(i,28)].charAt(1);if(c=='日')return"火";if(c=='月')return"水";return String.valueOf(c);}
    private boolean sunMoon(int i){char c=XIU[mod(i,28)].charAt(1);return c=='日'||c=='月';}
    private int tx(int i,boolean stem){if(stem){int[]v={9,8,7,6,5,9,8,7,6,5};return v[mod(i,10)];}int[]v={9,8,7,6,5,4,9,8,7,6,5,4};return v[mod(i,12)];}
    private double center(long seed,int shift){long z=mix(seed+shift*0x9E3779B97F4A7C15L);return(((z>>>11)&0x1FFFFFL)/(double)0x1FFFFF)-.5;}
    private long hash(String s){long h=0xcbf29ce484222325L;for(int i=0;i<s.length();i++){h^=s.charAt(i);h*=0x100000001b3L;}return h;}
    private long mix(long z){z=(z^(z>>>33))*0xff51afd7ed558ccdL;z=(z^(z>>>33))*0xc4ceb9fe1a85ec53L;return z^(z>>>33);}
    private int mod(int a,int n){int r=a%n;return r<0?r+n:r;}
    private long floorDiv(long a,long b){long q=a/b,r=a%b;return(r!=0&&((a^b)<0))?q-1:q;}
    private long daysUtc(int y,int m,int d){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("UTC"));c.clear();c.set(y,m-1,d,0,0,0);return floorDiv(c.getTimeInMillis(),86400000L);}
    private Calendar cal(long e){Calendar c=Calendar.getInstance();c.setTimeInMillis(e);return c;}
    private String fmt(double x){return String.format(Locale.CHINA,"%.2f",x);}
    private String hex(long x){return String.format(Locale.US,"%016X",x);}
    private String clean(String s,String d){s=s.trim();return s.length()==0?d:s;}

    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout card(){LinearLayout l=col();l.setPadding(dp(14),dp(14),dp(14),dp(14));l.setBackground(round(CARD,16));l.setElevation(dp(1));return l;}
    private TextView txt(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private EditText input(String hint,String val){EditText e=new EditText(this);e.setHint(hint);e.setText(val);e.setTextSize(14);e.setSingleLine(true);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setPadding(dp(12),0,dp(12),0);e.setBackground(round(Color.rgb(239,236,229),12));e.setInputType(InputType.TYPE_CLASS_TEXT);return e;}
    private Button btn(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:INK);b.setBackground(round(primary?ACCENT:Color.rgb(232,227,217),12));b.setPadding(dp(8),0,dp(8),0);return b;}
    private View space(int w,int h){Space s=new Space(this);s.setLayoutParams(new LinearLayout.LayoutParams(w,h));return s;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(l,t,r,b);return p;}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}

    static class S{int h,a;double p;S(int h,int a,double p){this.h=h;this.a=a;this.p=p;}String score(){return h+":"+a;}}
    static class R{String name,wdl,htft,score,top3,ou,parity,trace,probs;int total,net;double lh,la,hs,as;long seed;}
}