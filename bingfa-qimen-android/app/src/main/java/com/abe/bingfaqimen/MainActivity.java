package com.abe.bingfaqimen;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.content.Context;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 兵法奇门·球赛随机盘
 *
 * 民俗/娱乐研究工具。随机模型为 2(阴阳遁) × 9(局) × 60(时干支) = 1080 个等概率状态。
 * 球赛输出规则是本项目在传统主客、五行、景门/值使等用法上的实验映射，
 * 不代表统计学概率，也不作为投注依据。
 */
public class MainActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(185, 20, 15));
        setContentView(new QimenView(this));
    }

    static final class QimenView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final SecureRandom rng = new SecureRandom();

        private static final String[] GAN = {"甲","乙","丙","丁","戊","己","庚","辛","壬","癸"};
        private static final String[] ZHI = {"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};
        private static final String[] YI = {"戊","己","庚","辛","壬","癸","丁","丙","乙"};
        private static final int[] LUO = {1,8,3,4,9,2,7,6};
        private static final int[] SHUN = {1,8,3,4,9,2,7,6};
        private static final int[] NI = {1,6,7,2,9,4,3,8};
        private static final String[] GOD = {"值符","腾蛇","太阴","六合","白虎","玄武","九地","九天"};

        private final String[] di = new String[10];
        private final String[] tian = new String[10];
        private final String[] star = new String[10];
        private final String[] door = new String[10];
        private final String[] god = new String[10];

        private int serial, ju, hourIndex;
        private boolean yin;
        private String hourGz, xunYi, zhiFuStar, zhiShiDoor;
        private int zhiFuOrigin, zhiFuGong, zhiShiGong, jingGong;
        private int homeGong, awayGong;
        private double homeStrength, awayStrength, diff;
        private String result;
        private int hg, ag, totalGoals;
        private boolean bagua = false;

        private final RectF randomBtn = new RectF();
        private final RectF gridBtn = new RectF();
        private final RectF baguaBtn = new RectF();

        private float d, sp;
        private int W, H;

        QimenView(Context c) {
            super(c);
            setBackground(new ColorDrawable(Color.rgb(248,248,248)));
            d = getResources().getDisplayMetrics().density;
            sp = getResources().getDisplayMetrics().scaledDensity;
            generate();
        }

        private static int mod(int a, int b) { int r=a%b; return r<0?r+b:r; }
        private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

        private String jiazi(int i) {
            return GAN[mod(i,10)] + ZHI[mod(i,12)];
        }

        private String palaceName(int g) {
            switch (g) {
                case 1:return "坎"; case 2:return "坤"; case 3:return "震"; case 4:return "巽";
                case 5:return "中"; case 6:return "乾"; case 7:return "兑"; case 8:return "艮"; case 9:return "离";
            }
            return "";
        }

        private String element(int g) {
            switch(g) {
                case 1:return "水";
                case 2: case 5: case 8:return "土";
                case 3: case 4:return "木";
                case 6: case 7:return "金";
                case 9:return "火";
            }
            return "";
        }

        private String basicStar(int g) {
            switch(g) {
                case 1:return "天蓬"; case 8:return "天任"; case 3:return "天冲"; case 4:return "天辅";
                case 9:return "天英"; case 2:return "天芮"; case 7:return "天柱"; case 6:return "天心";
                case 5:return "天禽";
            }
            return "";
        }

        private String basicDoor(int g) {
            switch(g) {
                case 1:return "休门"; case 8:return "生门"; case 3:return "伤门"; case 4:return "杜门";
                case 9:return "景门"; case 2:return "死门"; case 7:return "惊门"; case 6:return "开门";
            }
            return "";
        }

        private int idx(int[] a, int v) {
            for (int i=0;i<a.length;i++) if(a[i]==v) return i;
            return -1;
        }

        private int findStem(String[] plate, String stem) {
            for (int g=1; g<=9; g++) {
                if (g!=5 && stem.equals(plate[g])) return g;
            }
            if (stem.equals(plate[5])) return 2;
            return -1;
        }

        private void buildDi() {
            for(int i=0;i<10;i++) di[i]="";
            int g=ju;
            for(int i=0;i<9;i++) {
                di[g]=YI[i];
                g += yin ? -1 : 1;
                if(g==0)g=9; if(g==10)g=1;
            }
        }

        private void buildTianAndStars() {
            for(int i=0;i<10;i++){ tian[i]=""; star[i]=""; }

            int oi=idx(LUO,zhiFuOrigin);
            int hi=idx(LUO,zhiFuGong);
            int step=mod(hi-oi,8);

            for(int i=0;i<8;i++) {
                int og=LUO[i];
                int ng=LUO[mod(i+step,8)];
                tian[ng]=di[og];
                String s=basicStar(og);
                if(og==2) s="禽芮";
                star[ng]=s;
            }
            tian[5]=di[5];
            star[5]="";
        }

        private void buildDoors() {
            for(int i=0;i<10;i++) door[i]="";
            int raw=zhiFuOrigin;
            int n=hourIndex%10;
            for(int i=0;i<n;i++) {
                raw += yin ? -1 : 1;
                if(raw==0)raw=9; if(raw==10)raw=1;
            }
            zhiShiGong = raw==5 ? 2 : raw;
            zhiShiDoor=basicDoor(zhiFuOrigin);
            int from=idx(LUO,zhiFuOrigin);
            int to=idx(LUO,zhiShiGong);
            int step=(from<0||to<0)?0:mod(to-from,8);
            for(int i=0;i<8;i++) {
                int og=LUO[i];
                int ng=LUO[mod(i+step,8)];
                door[ng]=basicDoor(og);
            }
            door[5]="";
            jingGong=1;
            for(int g=1;g<=9;g++) if("景门".equals(door[g])) jingGong=g;
        }

        private void buildGods() {
            for(int i=0;i<10;i++)god[i]="";
            int[] order=yin?NI:SHUN;
            int start=idx(order,zhiFuGong);
            if(start<0) start=idx(order,2);
            for(int i=0;i<8;i++) god[order[mod(start+i,8)]]=GOD[i];
            god[5]="";
        }

        private double doorScore(String x) {
            if(x==null)return 0;
            switch(x) {
                case "开门": return 1.25;
                case "生门": return 1.15;
                case "休门": return .8;
                case "景门": return .75;
                case "伤门": return -.55;
                case "杜门": return -.75;
                case "惊门": return -.9;
                case "死门": return -1.2;
            }
            return 0;
        }

        private double starScore(String x) {
            if(x==null)return 0;
            switch(x) {
                case "天辅": return .55;
                case "天心": return .5;
                case "天任": return .35;
                case "天英": return .25;
                case "天冲": return .15;
                case "天蓬": return 0;
                case "天柱": return -.2;
                case "天芮": case "禽芮": return -.45;
            }
            return 0;
        }

        private double godScore(String x) {
            if(x==null)return 0;
            switch(x) {
                case "值符": return .75;
                case "六合": return .55;
                case "太阴": return .45;
                case "九天": return .4;
                case "九地": return .3;
                case "腾蛇": return -.3;
                case "白虎": return -.55;
                case "玄武": return -.45;
            }
            return 0;
        }

        private boolean generates(String a, String b) {
            return ("木".equals(a)&&"火".equals(b)) || ("火".equals(a)&&"土".equals(b)) ||
                    ("土".equals(a)&&"金".equals(b)) || ("金".equals(a)&&"水".equals(b)) ||
                    ("水".equals(a)&&"木".equals(b));
        }

        private boolean controls(String a, String b) {
            return ("木".equals(a)&&"土".equals(b)) || ("土".equals(a)&&"水".equals(b)) ||
                    ("水".equals(a)&&"火".equals(b)) || ("火".equals(a)&&"金".equals(b)) ||
                    ("金".equals(a)&&"木".equals(b));
        }

        private double relationBonus(int h, int a) {
            String he=element(h), ae=element(a);
            if(he.equals(ae)) return .10;
            if(controls(he,ae)) return 1.15;
            if(controls(ae,he)) return -1.15;
            if(generates(ae,he)) return .55;
            if(generates(he,ae)) return -.55;
            return 0;
        }

        private double palaceScore(int g) {
            double s=doorScore(door[g])+starScore(star[g])+godScore(god[g]);
            if(g==zhiFuGong)s+=.35;
            if(g==zhiShiGong)s+=.15;
            return s;
        }

        private boolean isAttackDoor(String x) {
            return "景门".equals(x)||"开门".equals(x)||"伤门".equals(x);
        }
        private boolean isBarrierDoor(String x) {
            return "杜门".equals(x)||"死门".equals(x)||"休门".equals(x);
        }

        private void predict() {
            homeStrength=palaceScore(homeGong);
            awayStrength=palaceScore(awayGong);
            diff=homeStrength-awayStrength+relationBonus(homeGong,awayGong);

            if(diff>1.05) result="主胜";
            else if(diff<-1.05) result="客胜";
            else result="平";

            int t=2;
            if(jingGong==homeGong || jingGong==awayGong) t++;
            if(isAttackDoor(door[homeGong])) t++;
            if(isAttackDoor(door[awayGong])) t++;
            if(isBarrierDoor(door[homeGong]) && isBarrierDoor(door[awayGong])) t--;
            if(homeGong==awayGong) t--;
            if(serial%11==0) t++;
            if(serial%17==0) t--;
            t=clamp(t,0,6);

            if("平".equals(result)) {
                if((t&1)==1) t = (t>=5)?4:t+1;
                hg=ag=t/2;
            } else if("主胜".equals(result)) {
                t=Math.max(1,t);
                ag=Math.max(0,(t-1)/2);
                hg=t-ag;
                if(hg<=ag){hg=ag+1;t=hg+ag;}
            } else {
                t=Math.max(1,t);
                hg=Math.max(0,(t-1)/2);
                ag=t-hg;
                if(ag<=hg){ag=hg+1;t=hg+ag;}
            }
            totalGoals=hg+ag;
        }

        private void generate() {
            int r=rng.nextInt(1080);
            serial=r+1;
            yin=r>=540;
            int local=r%540;
            ju=local/60+1;
            hourIndex=local%60;
            hourGz=jiazi(hourIndex);
            xunYi=YI[hourIndex/10];

            buildDi();

            zhiFuOrigin=findStem(di,xunYi);
            if(zhiFuOrigin<0) zhiFuOrigin=2;
            if(zhiFuOrigin==5) zhiFuOrigin=2;
            zhiFuStar=basicStar(zhiFuOrigin);

            String hourGan=GAN[hourIndex%10];
            homeGong="甲".equals(hourGan)?zhiFuOrigin:findStem(di,hourGan);
            if(homeGong<0)homeGong=zhiFuOrigin;
            zhiFuGong=homeGong;

            buildTianAndStars();
            buildDoors();
            buildGods();

            awayGong="甲".equals(hourGan)?zhiFuGong:findStem(tian,hourGan);
            if(awayGong<0)awayGong=zhiFuGong;

            predict();
        }

        private float tx(float spv){ return spv*sp; }
        private float dp(float v){ return v*d; }

        private void fill(int c){p.setStyle(Paint.Style.FILL);p.setColor(c);}
        private void stroke(int c,float w){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(w));p.setColor(c);}

        private void text(Canvas c,String s,float x,float y,float size,int color,Paint.Align align,boolean bold) {
            p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(tx(size)); p.setTextAlign(align);
            p.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
            c.drawText(s,x,y,p);
        }

        private void round(Canvas c,RectF r,int color,float rad){
            fill(color); c.drawRoundRect(r,dp(rad),dp(rad),p);
        }

        @Override protected void onSizeChanged(int w,int h,int ow,int oh) {
            W=w;H=h;
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float y=0;
            fill(Color.rgb(205,24,17)); c.drawRect(0,0,W,dp(58),p);
            text(c,"兵法奇门·球赛随机盘",W/2f,dp(40),23,Color.WHITE,Paint.Align.CENTER,true);
            y=dp(66);

            text(c,"起局法：",dp(12),y+dp(22),16,Color.DKGRAY,Paint.Align.LEFT,true);
            RectF method=new RectF(dp(83),y,dp(190),y+dp(32));
            round(c,method,Color.WHITE,5);
            stroke(Color.LTGRAY,1);c.drawRoundRect(method,dp(5),dp(5),p);
            text(c,"随机硬局",method.centerX(),y+dp(22),15,Color.rgb(40,40,40),Paint.Align.CENTER,true);

            text(c,"排盘法：",dp(205),y+dp(22),16,Color.DKGRAY,Paint.Align.LEFT,true);
            RectF bmethod=new RectF(dp(276),y,Math.min(W-dp(10),dp(372)),y+dp(32));
            round(c,bmethod,Color.WHITE,5); stroke(Color.LTGRAY,1);c.drawRoundRect(bmethod,dp(5),dp(5),p);
            text(c,"兵法",bmethod.centerX(),y+dp(22),15,Color.rgb(40,40,40),Paint.Align.CENTER,true);

            y+=dp(42);
            RectF card=new RectF(dp(8),y,W-dp(8),y+dp(116));
            round(c,card,Color.WHITE,8);
            stroke(Color.rgb(224,224,224),1);c.drawRoundRect(card,dp(8),dp(8),p);

            text(c,"直接预测",dp(18),y+dp(23),15,Color.rgb(198,20,16),Paint.Align.LEFT,true);
            int resColor="主胜".equals(result)?Color.rgb(200,35,25):("客胜".equals(result)?Color.rgb(30,90,180):Color.rgb(80,80,80));
            text(c,result,dp(104),y+dp(26),25,resColor,Paint.Align.LEFT,true);
            text(c,"全场进球",dp(18),y+dp(57),14,Color.DKGRAY,Paint.Align.LEFT,true);
            text(c,String.valueOf(totalGoals),dp(105),y+dp(60),23,Color.rgb(200,35,25),Paint.Align.LEFT,true);
            text(c,totalGoals>=3?"大2.5":"小2.5",dp(137),y+dp(58),14,Color.GRAY,Paint.Align.LEFT,false);
            text(c,"参考比分",dp(18),y+dp(91),14,Color.DKGRAY,Paint.Align.LEFT,true);
            text(c,hg+":"+ag,dp(105),y+dp(94),25,Color.rgb(200,35,25),Paint.Align.LEFT,true);
            text(c,String.format(Locale.CHINA,"强弱差 %+1.2f",diff),W-dp(18),y+dp(90),12,Color.GRAY,Paint.Align.RIGHT,false);

            y+=dp(125);
            text(c,"随机局号：",dp(12),y+dp(16),14,Color.rgb(190,20,15),Paint.Align.LEFT,true);
            text(c,String.format(Locale.CHINA,"%04d / 1080",serial),dp(92),y+dp(16),14,Color.DKGRAY,Paint.Align.LEFT,true);
            text(c,"  "+(yin?"阴遁":"阳遁")+ju+"局  时柱"+hourGz,dp(190),y+dp(16),14,Color.DKGRAY,Paint.Align.LEFT,true);
            text(c,"值符："+zhiFuStar+"落"+palaceName(zhiFuGong)+"宫    值使："+zhiShiDoor+"落"+palaceName(zhiShiGong)+"宫",dp(12),y+dp(39),14,Color.DKGRAY,Paint.Align.LEFT,true);
            text(c,"主："+palaceName(homeGong)+"宫("+element(homeGong)+")    客："+palaceName(awayGong)+"宫("+element(awayGong)+")    景门："+palaceName(jingGong)+"宫",dp(12),y+dp(62),13,Color.GRAY,Paint.Align.LEFT,false);

            y+=dp(72);
            float bw=(W-dp(28))/3f;
            gridBtn.set(dp(8),y,dp(8)+bw,y+dp(34));
            baguaBtn.set(dp(14)+bw,y,dp(14)+2*bw,y+dp(34));
            randomBtn.set(dp(20)+2*bw,y,W-dp(8),y+dp(34));
            round(c,gridBtn,!bagua?Color.rgb(205,24,17):Color.rgb(235,235,235),6);
            round(c,baguaBtn,bagua?Color.rgb(205,24,17):Color.rgb(235,235,235),6);
            round(c,randomBtn,Color.rgb(205,24,17),6);
            text(c,"九宫格",gridBtn.centerX(),y+dp(23),14,!bagua?Color.WHITE:Color.DKGRAY,Paint.Align.CENTER,true);
            text(c,"八卦阵",baguaBtn.centerX(),y+dp(23),14,bagua?Color.WHITE:Color.DKGRAY,Paint.Align.CENTER,true);
            text(c,"完全随机取局",randomBtn.centerX(),y+dp(23),14,Color.WHITE,Paint.Align.CENTER,true);

            y+=dp(45);
            if(bagua) drawBagua(c,y);
            else drawGrid(c,y);

            text(c,"民俗算法实验 · 不代表真实概率 · 不作投注依据",W/2f,H-dp(10),10,Color.GRAY,Paint.Align.CENTER,false);
        }

        private void drawGrid(Canvas c,float top) {
            float margin=dp(12);
            float maxH=Math.max(dp(225),H-top-dp(30));
            float size=Math.min(W-2*margin, maxH);
            float cell=size/3f;
            int[][] layout={{4,9,2},{3,5,7},{8,1,6}};

            text(c,"南 ↑",margin,top-dp(5),11,Color.GRAY,Paint.Align.LEFT,true);
            for(int r=0;r<3;r++)for(int col=0;col<3;col++) {
                int g=layout[r][col];
                RectF rr=new RectF(margin+col*cell,top+r*cell,margin+(col+1)*cell,top+(r+1)*cell);
                fill(Color.WHITE);c.drawRect(rr,p);
                stroke(Color.rgb(170,170,170),1);c.drawRect(rr,p);

                float x=rr.left+dp(6), yy=rr.top+dp(18);
                String mark=(g==homeGong?"主 ":"")+(g==awayGong?"客 ":"");
                int mc=g==homeGong?Color.rgb(200,35,25):(g==awayGong?Color.rgb(40,90,180):Color.GRAY);
                text(c,mark+palaceName(g)+g,x,yy,12,mc,Paint.Align.LEFT,true);
                if(g!=5) {
                    text(c,god[g],rr.right-dp(6),yy,11,Color.DKGRAY,Paint.Align.RIGHT,true);
                    text(c,star[g],x,yy+dp(20),13,Color.rgb(30,30,30),Paint.Align.LEFT,true);
                    text(c,door[g],x,yy+dp(40),14,doorScore(door[g])>=0?Color.rgb(190,30,25):Color.rgb(40,80,150),Paint.Align.LEFT,true);
                    text(c,(tian[g]==null?"":tian[g])+"/"+(di[g]==null?"":di[g]),rr.right-dp(6),yy+dp(40),12,Color.GRAY,Paint.Align.RIGHT,false);
                } else {
                    text(c,"中宫",rr.centerX(),rr.centerY(),15,Color.LTGRAY,Paint.Align.CENTER,true);
                }
            }
            text(c,"北 ↓",margin,top+size+dp(16),11,Color.GRAY,Paint.Align.LEFT,true);
        }

        private void drawBagua(Canvas c,float top) {
            float cx=W/2f;
            float avail=Math.max(dp(230),H-top-dp(35));
            float radius=Math.min(W*0.36f,avail*0.38f);
            float cy=top+radius+dp(20);

            stroke(Color.rgb(170,170,170),1.2f);
            c.drawCircle(cx,cy,radius,p);
            c.drawCircle(cx,cy,radius*0.50f,p);

            int[] ring={9,2,7,6,1,8,3,4};
            for(int i=0;i<8;i++) {
                double a=-Math.PI/2+i*Math.PI/4;
                float x=cx+(float)Math.cos(a)*radius;
                float yy=cy+(float)Math.sin(a)*radius;
                float nx=cx+(float)Math.cos(a)*radius*.5f;
                float ny=cy+(float)Math.sin(a)*radius*.5f;
                stroke(Color.rgb(200,200,200),1); c.drawLine(nx,ny,x,yy,p);

                int g=ring[i];
                int color=g==homeGong?Color.rgb(200,35,25):(g==awayGong?Color.rgb(40,90,180):Color.DKGRAY);
                text(c,(g==homeGong?"主":"")+(g==awayGong?"客":"")+palaceName(g)+g,x,yy-dp(9),12,color,Paint.Align.CENTER,true);
                text(c,star[g],x,yy+dp(8),11,Color.DKGRAY,Paint.Align.CENTER,true);
                text(c,door[g],x,yy+dp(23),11,doorScore(door[g])>=0?Color.rgb(190,30,25):Color.rgb(40,80,150),Paint.Align.CENTER,true);
            }
            text(c,"中5",cx,cy-dp(5),13,Color.GRAY,Paint.Align.CENTER,true);
            text(c,(yin?"阴遁":"阳遁")+ju+"局",cx,cy+dp(14),12,Color.GRAY,Paint.Align.CENTER,false);
            text(c,"南",cx,top+dp(6),11,Color.GRAY,Paint.Align.CENTER,true);
            text(c,"北",cx,cy+radius+dp(25),11,Color.GRAY,Paint.Align.CENTER,true);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if(e.getAction()==MotionEvent.ACTION_UP) {
                float x=e.getX(),y=e.getY();
                if(randomBtn.contains(x,y)){generate();invalidate();performClick();return true;}
                if(gridBtn.contains(x,y)){bagua=false;invalidate();performClick();return true;}
                if(baguaBtn.contains(x,y)){bagua=true;invalidate();performClick();return true;}
            }
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
