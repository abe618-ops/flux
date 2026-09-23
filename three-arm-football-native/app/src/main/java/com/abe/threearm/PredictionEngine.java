package com.abe.threearm;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/**
 * Pure-Java offline port of the original Python/HTML engine.
 * No Android or network dependency; the same decimal Seed deterministically
 * produces the same four casts, lambdas and Dixon-Coles distributions.
 */
public final class PredictionEngine {
    private PredictionEngine() {}

    public static final int MAX_GOALS = 10;
    private static final SecureRandom RNG = new SecureRandom();
    private static final String[] DAY_SIGNS = {"Imix'","Ik'","Ak'b'al","K'an","Chikchan","Kimi","Manik'","Lamat","Muluk","Ok","Chuwen","Eb'","B'en","Ix","Men","K'ib'","Kab'an","Etz'nab'","Kawak","Ajaw"};
    private static final String[] STRENGTHS = {"微","中","强","很强","极强"};
    private static final Map<String,Integer> STRENGTH = Map.of("微",1,"中",2,"强",3,"很强",4,"极强",5);
    private static final Map<String,Double> TAG_TOTAL = Map.of(
            "closing",-0.25, "clean_sheet",-0.10, "stable",-0.10,
            "burst",0.30, "open",0.20, "late_strength",0.15,
            "unilateral",0.0, "bimodal",0.0);
    private static final Map<String,Double> TAG_SUP = Map.of(
            "unilateral",0.35, "clean_sheet",0.20, "burst",0.15,
            "open",-0.10, "stable",-0.10, "closing",-0.05, "late_strength",0.05);
    public static final Map<String,String> TAG_ZH = Map.of(
            "burst","爆发", "stable","稳守", "unilateral","单边", "open","开放",
            "closing","收束", "clean_sheet","零封", "late_strength","后程发力", "bimodal","双峰");

    public static String newSeed() {
        byte[] b = new byte[8];
        RNG.nextBytes(b);
        return new BigInteger(1, b).toString();
    }

    private static BigInteger hBig(String seed, String ns, int counter, BigInteger n) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((seed + "|" + ns + "|" + counter).getBytes(StandardCharsets.UTF_8));
            byte[] first8 = Arrays.copyOfRange(digest, 0, 8);
            return new BigInteger(1, first8).mod(n);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int h(String seed, String ns, int counter, int n) {
        return hBig(seed, ns, counter, BigInteger.valueOf(n)).intValue();
    }

    public static final class Reading {
        public final String system, cast, direction, strength;
        public final int goals;
        public final List<String> tags;
        Reading(String system, String cast, String direction, String strength, int goals, List<String> tags) {
            this.system=system; this.cast=cast; this.direction=direction; this.strength=strength; this.goals=goals; this.tags=List.copyOf(tags);
        }
    }

    public static final class Meta {
        public final double supremacy, total;
        public final Map<String,Integer> votes;
        public final List<String> tags;
        Meta(double supremacy, double total, Map<String,Integer> votes, List<String> tags) {
            this.supremacy=supremacy; this.total=total; this.votes=Map.copyOf(votes); this.tags=List.copyOf(tags);
        }
    }

    public static final class Markets {
        public double home, draw, away, expGoals, over25, odd, even, btts;
        public final double[] goals = new double[MAX_GOALS*2+1];
        public final List<ScoreProb> topScores = new ArrayList<>();
        public double oneX(){return home+draw;} public double xTwo(){return draw+away;} public double twelve(){return home+away;}
    }

    public static final class ScoreProb {
        public final int home, away; public final double p;
        ScoreProb(int h, int a, double p){home=h; away=a; this.p=p;}
        public String score(){return home+":"+away;}
    }

    public static final class HtFt {
        public final Map<String,Double> flat = new LinkedHashMap<>();
        public double htHome, htDraw, htAway;
    }

    public static final class Arm {
        public final String name; public final double lh, la; public final Markets mk; public final HtFt hf;
        Arm(String name,double lh,double la,Markets mk,HtFt hf){this.name=name;this.lh=lh;this.la=la;this.mk=mk;this.hf=hf;}
    }

    public static final class Forecast {
        public final String seed; public final List<Reading> readings; public final Meta meta;
        public final Arm A, B1, B2;
        Forecast(String seed,List<Reading> readings,Meta meta,Arm A,Arm B1,Arm B2){this.seed=seed;this.readings=readings;this.meta=meta;this.A=A;this.B1=B1;this.B2=B2;}
    }

    public static List<Reading> castFour(String seed) {
        List<Reading> out = new ArrayList<>();
        int idx = 1 + h(seed,"maya",0,260);
        int num = (idx-1)%13 + 1;
        String sign = DAY_SIGNS[(idx-1)%20];
        List<String> tags = new ArrayList<>(); if(num>=11)tags.add("burst"); if(num<=3)tags.add("stable");
        out.add(new Reading("Maya", idx+"/260 · "+num+" "+sign,
                new String[]{"主","平","客"}[idx%3], STRENGTHS[Math.min(4,(num-1)/3)], 1+(idx%4), tags));

        StringBuilder bits = new StringBuilder(); int ones=0;
        for(int i=0;i<8;i++){int b=h(seed,"ifa",i,2);bits.append(b);ones+=b;}
        int bv=Integer.parseInt(bits.toString(),2);
        tags=new ArrayList<>(); if(ones==0||ones==1||ones==7||ones==8)tags.add("unilateral"); if(ones==4)tags.add("open");
        out.add(new Reading("Ifá", bits+" · #"+(1+bv), ones==4?"平":(ones>4?"主":"客"),
                STRENGTHS[Math.min(4,Math.abs(ones-4)+1)],1+(bv%4),tags));

        for(String[] pair:new String[][]{{"sikidy","Sikidy"},{"raml","Raml"}}){
            int[] d=new int[16]; int sum=0; StringBuilder cast=new StringBuilder();
            for(int i=0;i<16;i++){d[i]=1+h(seed,pair[0],i,2);sum+=d[i]; if(i>0&&i%4==0)cast.append(" / ");cast.append(d[i]);}
            tags=new ArrayList<>(); if(sum<=20){tags.add("closing");tags.add("clean_sheet");} if(sum>=28)tags.add("late_strength");
            out.add(new Reading(pair[1],cast.toString(),new String[]{"主","平","客"}[sum%3],
                    STRENGTHS[Math.min(4,Math.abs(sum-24)/2)],1+(sum%4),tags));
        }
        return out;
    }

    private static Object[] oracleLambdas(List<Reading> readings) {
        double supRaw=0;
        for(Reading r:readings) supRaw += STRENGTH.get(r.strength) * (r.direction.equals("主")?1:r.direction.equals("客")?-1:0);
        double sup = 0.30 + 1.15*Math.tanh(supRaw/7.0);
        double total=0; List<String> tags=new ArrayList<>();
        for(Reading r:readings){total+=r.goals;tags.addAll(r.tags);} total/=readings.size();
        for(String t:tags) total+=TAG_TOTAL.getOrDefault(t,0.0);
        total=0.5*total+0.5*2.70; total=Math.max(1.6,Math.min(4.6,total));
        double tagSup=0; for(String t:tags)tagSup+=TAG_SUP.getOrDefault(t,0.0); sup*=1.0+tagSup/Math.max(tags.size(),1);
        double lh=Math.max(0.15,(total+sup)/2), la=Math.max(0.15,(total-sup)/2);
        Map<String,Integer> votes=new LinkedHashMap<>(); for(String d:new String[]{"主","平","客"})votes.put(d,0);
        for(Reading r:readings)votes.put(r.direction,votes.get(r.direction)+1);
        List<String> uniq=new ArrayList<>(new TreeSet<>(tags));
        return new Object[]{lh,la,new Meta(round3(sup),round3(total),votes,uniq)};
    }

    private static double round3(double x){return Math.round(x*1000.0)/1000.0;}
    private static double factorial(int k){double f=1;for(int i=2;i<=k;i++)f*=i;return f;}
    private static double pois(double lam,int k){return Math.exp(-lam)*Math.pow(lam,k)/factorial(k);}

    public static double[][] dcMatrix(double lh,double la){
        double rho=-0.13; lh=Math.max(0.05,Math.min(6.0,lh));la=Math.max(0.05,Math.min(6.0,la));
        double[][] m=new double[MAX_GOALS+1][MAX_GOALS+1];double s=0;
        for(int i=0;i<=MAX_GOALS;i++)for(int j=0;j<=MAX_GOALS;j++)m[i][j]=pois(lh,i)*pois(la,j);
        m[0][0]*=1-lh*la*rho;m[0][1]*=1+lh*rho;m[1][0]*=1+la*rho;m[1][1]*=1-rho;
        for(int i=0;i<=MAX_GOALS;i++)for(int j=0;j<=MAX_GOALS;j++){m[i][j]=Math.max(m[i][j],1e-15);s+=m[i][j];}
        for(int i=0;i<=MAX_GOALS;i++)for(int j=0;j<=MAX_GOALS;j++)m[i][j]/=s;return m;
    }

    public static Markets markets(double[][] m){
        Markets r=new Markets();
        for(int i=0;i<=MAX_GOALS;i++)for(int j=0;j<=MAX_GOALS;j++){
            double p=m[i][j]; if(i>j)r.home+=p; else if(i==j)r.draw+=p; else r.away+=p;
            r.goals[i+j]+=p; if(i>0&&j>0)r.btts+=p; if(i<6&&j<6)r.topScores.add(new ScoreProb(i,j,p));
        }
        for(int t=0;t<r.goals.length;t++){r.expGoals+=t*r.goals[t];if(t>=3)r.over25+=r.goals[t];if(t%2==0)r.even+=r.goals[t];else r.odd+=r.goals[t];}
        r.topScores.sort((a,b)->Double.compare(b.p,a.p)); while(r.topScores.size()>8)r.topScores.remove(r.topScores.size()-1); return r;
    }

    public static HtFt htft(double lh,double la){
        double rho=-0.13, share=0.45; int cap=7; double[][] M=new double[3][3];double S=0;
        for(int h1=0;h1<=cap;h1++)for(int a1=0;a1<=cap;a1++)for(int h2=0;h2<=cap;h2++)for(int a2=0;a2<=cap;a2++){
            double p=pois(lh*share,h1)*pois(la*share,a1)*pois(lh*(1-share),h2)*pois(la*(1-share),a2);
            int fh=h1+h2,fa=a1+a2;
            if(fh==0&&fa==0)p*=1-lh*la*rho;else if(fh==0&&fa==1)p*=1+lh*rho;else if(fh==1&&fa==0)p*=1+la*rho;else if(fh==1&&fa==1)p*=1-rho;
            p=Math.max(p,1e-18);M[side(h1,a1)][side(fh,fa)]+=p;S+=p;
        }
        HtFt out=new HtFt();String[] L={"胜","平","负"};
        for(int i=0;i<3;i++)for(int j=0;j<3;j++){M[i][j]/=S;out.flat.put(L[i]+"/"+L[j],M[i][j]);}
        out.htHome=M[0][0]+M[0][1]+M[0][2];out.htDraw=M[1][0]+M[1][1]+M[1][2];out.htAway=M[2][0]+M[2][1]+M[2][2];return out;
    }
    private static int side(int h,int a){return h>a?0:(h==a?1:2);}

    public static Forecast forecast(String seed){
        List<Reading> rr=castFour(seed);Object[] oa=oracleLambdas(rr);double lh=(double)oa[0],la=(double)oa[1];Meta meta=(Meta)oa[2];
        double b1h=(2.70+0.32)/2, b1a=(2.70-0.32)/2;
        String ctrl=hBig(seed,"control",0,new BigInteger("9223372036854775807")).toString();Object[] ob=oracleLambdas(castFour(ctrl));double b2h=(double)ob[0],b2a=(double)ob[1];
        Arm A=new Arm("A 四术",lh,la,markets(dcMatrix(lh,la)),htft(round3(lh),round3(la)));
        Arm B1=new Arm("B1 气候",b1h,b1a,markets(dcMatrix(b1h,b1a)),null);
        Arm B2=new Arm("B2 随机",b2h,b2a,markets(dcMatrix(b2h,b2a)),null);
        return new Forecast(seed,rr,meta,A,B1,B2);
    }

    public static int outcomeIndex(int home,int away){return home>away?0:(home==away?1:2);}
    public static double rps(Markets m,int idx){
        double[] p={m.home,m.draw,m.away};double[] o={0,0,0};o[idx]=1;
        double c1=p[0]-o[0], c2=p[0]+p[1]-o[0]-o[1];return (c1*c1+c2*c2)/2.0;
    }
}
