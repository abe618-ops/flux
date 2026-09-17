package com.abe618.triastrofootball;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.UUID;

public final class PredictionEngine {
    public static final int HOME = 0, DRAW = 1, AWAY = 2;
    private static final String[] DIR = {"主", "平", "客"};
    private static final String[] SIGNS = {"白羊","金牛","双子","巨蟹","狮子","处女","天秤","天蝎","射手","摩羯","水瓶","双鱼"};
    private static final String[] PLANETS = {"太阳","月亮","水星","金星","火星","木星","土星"};
    private static final int[] RULER = {4,3,2,1,0,2,3,4,5,6,6,5};
    private static final String[] FIGURES = {
            "Populus","Via","Fortuna Major","Fortuna Minor",
            "Acquisitio","Amissio","Laetitia","Tristitia",
            "Puella","Puer","Albus","Rubeus",
            "Conjunctio","Carcer","Caput Draconis","Cauda Draconis"
    };
    private static final double[] FIGURE_VALUE = {
            0.0,0.0,1.3,0.6,1.2,-0.7,0.8,-0.8,
            0.4,0.2,0.8,-0.8,0.3,-0.5,0.9,-0.9
    };

    public static final class MethodResult {
        public String name;
        public String direction;
        public String detail;
        public String randomMark;
        public double pHome, pDraw, pAway;
        public double goalMean;
        public double btts;
        public double volatility;
        int directionCode;
    }

    public static final class Score {
        public int home, away;
        public double probability;
        public Score(int h, int a, double p) { home=h; away=a; probability=p; }
        public String label() { return home + ":" + away; }
    }

    public static final class Result {
        public String seed;
        public MethodResult geomancy, western, indian;
        public String m1Pattern;
        public String m2Direction;
        public int k;
        public double pHome, pDraw, pAway;
        public double over25, odd, btts, mu, volatility;
        public String winner, protect, ou, parity, bttsText, totalRange, half, halfFull;
        public String directionConfidence;
        public List<Score> topScores = new ArrayList<>();
        public List<Score> mirrorScores = new ArrayList<>();
        public String summary;
    }

    private static final class Astro {
        ZonedDateTime time;
        double asc;
        double[] lon = new double[7];
    }

    private static final class Round {
        MethodResult g,w,i;
        int direction = -1;
    }

    private PredictionEngine() {}

    public static String randomSeed() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    public static Result run(String seedText) {
        String seed = (seedText == null || seedText.trim().isEmpty()) ? randomSeed() : seedText.trim();
        SplittableRandom root = new SplittableRandom(hash64(seed));

        Result out = new Result();
        out.seed = seed;
        out.geomancy = geomancy(root.split());
        out.western = western(root.split());
        out.indian = indian(root.split());
        out.m1Pattern = out.geomancy.direction + " / " + out.western.direction + " / " + out.indian.direction;

        Round converged = null;
        int k = 30;
        for (int n=1; n<=30; n++) {
            Round r = new Round();
            r.g = geomancy(root.split());
            r.w = western(root.split());
            r.i = indian(root.split());
            if (r.g.directionCode == r.w.directionCode && r.w.directionCode == r.i.directionCode) {
                r.direction = r.g.directionCode;
                converged = r;
                k = n;
                break;
            }
        }
        out.k = k;
        out.m2Direction = converged == null ? "30轮未归一" : DIR[converged.direction];

        combine(out, converged);
        return out;
    }

    private static MethodResult geomancy(SplittableRandom r) {
        int[] m = new int[4];
        for (int i=0;i<4;i++) m[i]=r.nextInt(16);
        int[] d = new int[4];
        for (int row=0;row<4;row++) {
            int v=0;
            for (int mother=0;mother<4;mother++) {
                int bit=(m[mother]>>row)&1;
                v |= bit<<mother;
            }
            d[row]=v;
        }
        int[] n = {m[0]^m[1], m[2]^m[3], d[0]^d[1], d[2]^d[3]};
        int rw=n[0]^n[1], lw=n[2]^n[3], judge=rw^lw;
        int h1=m[0], h4=m[3], h7=d[2], h10=n[1];

        double hs = FIGURE_VALUE[h1] + .55*FIGURE_VALUE[h10] + .35*FIGURE_VALUE[lw];
        double as = FIGURE_VALUE[h7] + .55*FIGURE_VALUE[h4] + .35*FIGURE_VALUE[rw];
        double draw = 1.35 - .38*Math.abs(hs-as) + (Integer.bitCount(judge)==2 ? .45 : 0);
        double[] p=softmax(hs, draw, as);

        int singles = Integer.bitCount(h1)+Integer.bitCount(h7)+Integer.bitCount(rw)+Integer.bitCount(lw)+Integer.bitCount(judge);
        double gm = clamp(1.35 + singles/20.0*3.2 + .18*Math.abs(hs-as), 1.2, 5.2);
        double bt = logistic(-.35 + .32*(Integer.bitCount(h1)+Integer.bitCount(h7)-4) - .16*Math.abs(hs-as));
        double vol = clamp(.28 + .055*Math.abs(Integer.bitCount(h1)-Integer.bitCount(h7)) + .04*Integer.bitCount(judge), .2, .85);

        MethodResult x = method("地占术", p, gm, bt, vol);
        x.randomMark = "四母：" + FIGURES[m[0]]+" / "+FIGURES[m[1]]+" / "+FIGURES[m[2]]+" / "+FIGURES[m[3]];
        x.detail = "1宫="+FIGURES[h1]+"；7宫="+FIGURES[h7]+"；10宫="+FIGURES[h10]+"；4宫="+FIGURES[h4]
                +"；右见证="+FIGURES[rw]+"；左见证="+FIGURES[lw]+"；Judge="+FIGURES[judge]
                +"；主客强度="+fmt(hs)+"/"+fmt(as)+"；进球能量="+fmt(gm);
        return x;
    }

    private static MethodResult western(SplittableRandom r) {
        Astro a=randomAstro(r);
        int ascSign=sign(a.asc), descSign=(ascSign+6)%12;
        int hr=RULER[ascSign], ar=RULER[descSign];
        double hs=planetStrength(hr,a.lon[hr],a.asc)+moonContact(a.lon[1],a.lon[hr]);
        double as=planetStrength(ar,a.lon[ar],a.asc)+moonContact(a.lon[1],a.lon[ar]);
        int moonHouse=house(a.lon[1],a.asc);
        double draw=1.2-.34*Math.abs(hs-as)+(moonHouse==4||moonHouse==7 ? .35:0);
        double[] p=softmax(hs,draw,as);

        double open=aspectOpenness(a.lon[1],a.lon[4])+aspectOpenness(a.lon[1],a.lon[5]);
        double gm=clamp(2.05+.28*open+.10*(angular(a.lon[hr],a.asc)+angular(a.lon[ar],a.asc))+.08*Math.abs(hs-as),1.25,5.1);
        double bt=logistic(-.05+.20*open-.14*Math.abs(hs-as));
        double vol=clamp(.30+.13*open+.04*Math.abs(hs-as),.2,.9);

        MethodResult x=method("西洋占星",p,gm,bt,vol);
        x.randomMark="随机时刻："+a.time.toString();
        x.detail="上升="+SIGNS[ascSign]+"；主队宫主="+PLANETS[hr]+"；客队宫主="+PLANETS[ar]
                +"；月亮宫位="+moonHouse+"；主客强度="+fmt(hs)+"/"+fmt(as)
                +"；月亮开放度="+fmt(open)+"；轻量均值黄经算法";
        return x;
    }

    private static MethodResult indian(SplittableRandom r) {
        Astro t=randomAstro(r);
        int year=t.time.getYear();
        double ay=23.8567 + 0.01397*(year-2000);
        double asc=norm(t.asc-ay);
        double[] sid=new double[7];
        for(int i=0;i<7;i++) sid[i]=norm(t.lon[i]-ay);

        int ascSign=sign(asc), desc=(ascSign+6)%12;
        int hr=RULER[ascSign], ar=RULER[desc];
        double hs=planetStrength(hr,sid[hr],asc);
        double as=planetStrength(ar,sid[ar],asc);

        int home611=0, away611=0;
        for(double v:sid){
            int h=house(v,asc);
            if(h==6||h==11) home611++;
            if(h==12||h==5) away611++;
        }
        hs += .45*home611;
        as += .45*away611;

        int kp=r.nextInt(1,250);
        int kpDir=(kp-1)%3;
        if(kpDir==HOME) hs+=.75;
        else if(kpDir==AWAY) as+=.75;

        int nak=(int)Math.floor(sid[1]/(360.0/27.0))+1;
        double draw=1.25-.30*Math.abs(hs-as)+(kpDir==DRAW?.55:0);
        double[] p=softmax(hs,draw,as);

        double gm=clamp(1.85+.16*(home611+away611)+.12*((kp-1)%7)+.08*Math.abs(hs-as),1.2,5.3);
        double bt=logistic(-.18+.18*(home611+away611)-.12*Math.abs(hs-as));
        double vol=clamp(.27+.035*((kp-1)%9)+.04*Math.abs(home611-away611),.2,.92);

        MethodResult x=method("印度占星 / KP",p,gm,bt,vol);
        x.randomMark="随机时刻："+t.time.toString()+"；KP="+kp;
        x.detail="Lagna="+SIGNS[ascSign]+"；主队宫主="+PLANETS[hr]+"；客队宫主="+PLANETS[ar]
                +"；6/11竞技轴="+home611+"；对手旋宫轴="+away611+"；月宿="+nak
                +"；主客强度="+fmt(hs)+"/"+fmt(as)+"；Lahiri近似岁差="+fmt(ay)+"°";
        return x;
    }

    private static MethodResult method(String name,double[] p,double gm,double bt,double vol){
        MethodResult x=new MethodResult();
        x.name=name; x.pHome=p[0]; x.pDraw=p[1]; x.pAway=p[2];
        x.directionCode=argmax(p); x.direction=DIR[x.directionCode];
        x.goalMean=gm; x.btts=bt; x.volatility=vol;
        return x;
    }

    private static void combine(Result o, Round m2) {
        MethodResult[] a={o.geomancy,o.western,o.indian};
        double h=0,d=0,w=0,mu=0,bt=0,vol=0;
        int[] counts=new int[3];
        for(MethodResult x:a){
            h+=x.pHome; d+=x.pDraw; w+=x.pAway; mu+=x.goalMean; bt+=x.btts; vol+=x.volatility;
            counts[x.directionCode]++;
        }
        h/=3; d/=3; w/=3; mu/=3; bt/=3; vol/=3;

        if(counts[HOME]==3){ h*=1.07; }
        if(counts[AWAY]==3){ w*=1.07; }
        if(counts[DRAW]==3){ d*=1.07; }
        double s=h+d+w; h/=s;d/=s;w/=s;

        if(m2!=null){
            double wk=.30*Math.exp(-.22*(o.k-1));
            double[] one={.05,.05,.05}; one[m2.direction]=.90;
            h=(1-wk)*h+wk*one[0];
            d=(1-wk)*d+wk*one[1];
            w=(1-wk)*w+wk*one[2];

            double m2mu=(m2.g.goalMean+m2.w.goalMean+m2.i.goalMean)/3.0;
            mu=(1-.35*wk)*mu + (.35*wk)*m2mu;
            vol += .10*wk;
        }

        boolean allDifferent=counts[HOME]==1&&counts[DRAW]==1&&counts[AWAY]==1;
        if(allDifferent){ d+=.05; vol+=.10; }
        if(counts[DRAW]>=2){ d+=.07; mu-=.18; }
        if(o.k>=8){ vol+=.18; }
        if(counts[DRAW]==3 && o.k>=8){ vol+=.45; }

        s=h+d+w; h/=s;d/=s;w/=s;

        if(Math.abs(h-w)<.10 && Math.max(h,w)<.45){
            d+=.10;
            s=h+d+w; h/=s;d/=s;w/=s;
        }

        mu=clamp(mu,1.15,5.8);
        vol=clamp(vol,.18,1.25);
        bt=clamp(bt,.15,.85);

        double[][] matrix=scoreMatrix(mu,vol,h,d,w,bt);
        double ph=0,pd=0,pa=0,over=0,odd=0,pb=0,total=0;
        List<Score> scores=new ArrayList<>();
        for(int i=0;i<matrix.length;i++){
            for(int j=0;j<matrix[i].length;j++){
                double p=matrix[i][j]; total+=p;
                if(i>j)ph+=p; else if(i==j)pd+=p; else pa+=p;
                if(i+j>=3)over+=p;
                if(((i+j)&1)==1)odd+=p;
                if(i>0&&j>0)pb+=p;
                scores.add(new Score(i,j,p));
            }
        }
        o.pHome=.58*h+.42*ph;
        o.pDraw=.58*d+.42*pd;
        o.pAway=.58*w+.42*pa;
        s=o.pHome+o.pDraw+o.pAway; o.pHome/=s;o.pDraw/=s;o.pAway/=s;

        o.over25=over/total; o.odd=odd/total; o.btts=pb/total; o.mu=mu; o.volatility=vol;
        int win=argmax(new double[]{o.pHome,o.pDraw,o.pAway});
        o.winner=DIR[win];
        if(o.pHome+o.pDraw>=.68 && o.pHome>=o.pAway) o.protect="主队不败";
        else if(o.pAway+o.pDraw>=.68) o.protect="客队不败";
        else o.protect="双方均需防";

        double max=Math.max(o.pHome,Math.max(o.pDraw,o.pAway));
        o.directionConfidence=max<.43?"低":max<.55?"中等":"中高";
        o.ou=o.over25>=.5?"大2.5":"小2.5";
        o.parity=o.odd>=.5?"单":"双";
        o.bttsText=o.btts>=.5?"YES":"NO";
        o.totalRange=mu<2.0?"1～2球":mu<3.0?"2～3球":mu<4.2?"3～4球":"4～6球";

        Collections.sort(scores, Comparator.comparingDouble((Score q)->q.probability).reversed());
        for(Score q:scores){
            if(q.home<=6&&q.away<=6){
                o.topScores.add(q);
                if(o.topScores.size()==4)break;
            }
        }
        if(Math.abs(o.pHome-o.pAway)<.18 || o.directionConfidence.equals("低")){
            for(Score q:o.topScores){
                if(q.home!=q.away){
                    Score mirror=findScore(scores,q.away,q.home);
                    if(mirror!=null && !contains(o.mirrorScores,mirror.home,mirror.away)) o.mirrorScores.add(mirror);
                }
                if(o.mirrorScores.size()>=2)break;
            }
        }

        if(win==DRAW || mu<3.15) o.half="平";
        else o.half=win==HOME?"主":"客";
        if(o.half.equals("平")) o.halfFull="平/"+DIR[win];
        else o.halfFull=o.half+"/"+DIR[win];

        String scoreText=o.topScores.isEmpty()?"—":o.topScores.get(0).label();
        o.summary="方向："+o.winner+"（"+o.directionConfidence+"）｜"+o.protect
                +"｜"+o.ou+" "+pct(o.over25)+"｜"+o.parity+" "+pct(o.odd>=.5?o.odd:1-o.odd)
                +"｜BTTS "+o.bttsText+" "+pct(o.btts>=.5?o.btts:1-o.btts)
                +"｜"+o.totalRange+"｜首选 "+scoreText;
    }

    private static double[][] scoreMatrix(double mu,double vol,double targetH,double targetD,double targetA,double targetBtts){
        int max=8;
        double[][] m=new double[max+1][max+1];
        int r=(int)Math.round(clamp(7.5-4.5*vol,2,8));
        double p=r/(r+mu);
        double homeShare=clamp(.50+.24*(targetH-targetA),.28,.72);
        double totalProb=Math.pow(p,r);
        for(int goals=0;goals<=2*max;goals++){
            if(goals>0) totalProb=totalProb*((goals-1+r)/(double)goals)*(1-p);
            for(int h=0;h<=goals;h++){
                int a=goals-h;
                if(h>max||a>max)continue;
                m[h][a]+=totalProb*binomial(goals,h)*Math.pow(homeShare,h)*Math.pow(1-homeShare,a);
            }
        }
        normalize(m);
        for(int iter=0;iter<3;iter++){
            double baseB=0;
            for(int i=1;i<m.length;i++)for(int j=1;j<m[i].length;j++)baseB+=m[i][j];
            if(baseB>.001&&baseB<.999){
                double f=(targetBtts/(1-targetBtts))/(baseB/(1-baseB));
                for(int i=1;i<m.length;i++)for(int j=1;j<m[i].length;j++)m[i][j]*=f;
                normalize(m);
            }
            double bh=0,bd=0,ba=0;
            for(int i=0;i<m.length;i++)for(int j=0;j<m[i].length;j++){
                if(i>j)bh+=m[i][j];else if(i==j)bd+=m[i][j];else ba+=m[i][j];
            }
            double fh=targetH/Math.max(.001,bh), fd=targetD/Math.max(.001,bd), fa=targetA/Math.max(.001,ba);
            for(int i=0;i<m.length;i++)for(int j=0;j<m[i].length;j++)m[i][j]*=(i>j?fh:i==j?fd:fa);
            normalize(m);
        }
        return m;
    }

    private static void normalize(double[][] m){
        double s=0;for(double[] row:m)for(double v:row)s+=v;
        if(s==0)return;for(int i=0;i<m.length;i++)for(int j=0;j<m[i].length;j++)m[i][j]/=s;
    }
    private static long binomial(int n,int k){
        if(k<0||k>n)return 0;
        if(k>n-k)k=n-k;
        long c=1;for(int i=1;i<=k;i++)c=c*(n-k+i)/i;return c;
    }

    private static Astro randomAstro(SplittableRandom r){
        long start=ZonedDateTime.of(1900,1,1,0,0,0,0, ZoneOffset.UTC).toEpochSecond();
        long end=ZonedDateTime.of(2099,12,31,23,59,59,0, ZoneOffset.UTC).toEpochSecond();
        long sec=r.nextLong(start,end);
        ZonedDateTime z=Instant.ofEpochSecond(sec).atZone(ZoneOffset.UTC);
        double d=(sec-946728000L)/86400.0;
        Astro a=new Astro();a.time=z;
        a.lon[0]=norm(280.460+0.9856474*d);
        a.lon[1]=norm(218.316+13.176396*d);
        a.lon[2]=norm(252.251+4.09233445*d);
        a.lon[3]=norm(181.980+1.60213034*d);
        a.lon[4]=norm(355.433+0.524039*d);
        a.lon[5]=norm(34.351+0.083091*d);
        a.lon[6]=norm(50.077+0.0334597*d);
        a.asc=norm(280.46061837+360.98564736629*d);
        return a;
    }

    private static double planetStrength(int planet,double lon,double asc){
        int s=sign(lon), h=house(lon,asc);
        double x=0;
        if(RULER[s]==planet)x+=2.8;
        if(h==1||h==4||h==7||h==10)x+=1.6;
        else if(h==2||h==5||h==8||h==11)x+=.7;
        else x-=.15;
        if(planet==0||planet==5)x+=.25;
        if(planet==6)x-=.10;
        return x;
    }
    private static double moonContact(double moon,double planet){
        double diff=angleDiff(moon,planet);
        double[] asp={0,60,90,120,180};
        double best=180;for(double a:asp)best=Math.min(best,Math.abs(diff-a));
        if(best<4)return 1.1;
        if(best<8)return .55;
        return 0;
    }
    private static double aspectOpenness(double a,double b){
        double diff=angleDiff(a,b), best=Math.min(Math.abs(diff-90),Math.min(Math.abs(diff-120),Math.abs(diff-180)));
        return clamp(1.4-best/45.0,0,1.4);
    }
    private static int angular(double lon,double asc){
        int h=house(lon,asc);return (h==1||h==4||h==7||h==10)?1:0;
    }
    private static int house(double lon,double asc){ return ((int)Math.floor(norm(lon-asc)/30.0))+1; }
    private static int sign(double lon){ return ((int)Math.floor(norm(lon)/30.0))%12; }
    private static double angleDiff(double a,double b){ double d=Math.abs(norm(a-b));return d>180?360-d:d; }
    private static double norm(double x){ x%=360;if(x<0)x+=360;return x; }

    private static double[] softmax(double a,double b,double c){
        double mx=Math.max(a,Math.max(b,c));
        double ea=Math.exp(a-mx),eb=Math.exp(b-mx),ec=Math.exp(c-mx),s=ea+eb+ec;
        return new double[]{ea/s,eb/s,ec/s};
    }
    private static int argmax(double[] p){
        int k=0;for(int i=1;i<p.length;i++)if(p[i]>p[k])k=i;return k;
    }
    private static double logistic(double x){return 1.0/(1.0+Math.exp(-x));}
    private static double clamp(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}
    private static String fmt(double x){return String.format(Locale.CHINA,"%.2f",x);}
    public static String pct(double x){return String.format(Locale.CHINA,"%.0f%%",100*x);}

    private static long hash64(String s){
        try{
            byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            long v=0;for(int i=0;i<8;i++)v=(v<<8)|(b[i]&255L);return v;
        }catch(Exception e){return s.hashCode()*0x9E3779B97F4A7C15L;}
    }

    private static Score findScore(List<Score> s,int h,int a){
        for(Score q:s)if(q.home==h&&q.away==a)return q;return null;
    }
    private static boolean contains(List<Score> s,int h,int a){
        for(Score q:s)if(q.home==h&&q.away==a)return true;return false;
    }
}
