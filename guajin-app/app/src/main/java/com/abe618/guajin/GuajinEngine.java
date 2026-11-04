package com.abe618.guajin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

/**
 * 第2册27卦原始编码 + 明确标记的实验预测模型。
 * 圣/阳/阴的序号是软件数据库编码，并非古书自称数理。
 * 第一、三册断辞不完整，故不在这里伪造。
 */
public final class GuajinEngine {
    private GuajinEngine() {}
    public static final String VERSION = "GUJIN-EXPERIMENT-1.0";
    public static final String[] STATES = {"圣", "阳", "阴"};
    private static final int[][] PAGES = {
        {4,6},{7,7},{8,9},{10,11},{12,12},{13,14},{15,16},{17,18},{19,20},
        {21,22},{23,23},{24,25},{26,27},{28,28},{29,30},{31,32},{33,34},
        {35,35},{36,37},{38,39},{40,40},{41,42},{43,44},{45,46},{47,48},
        {49,50},{51,52}
    };

    public static final class Cast {
        public final int a,b,c,number;
        Cast(int aa,int bb,int cc) {
            a=Math.floorMod(aa,3); b=Math.floorMod(bb,3); c=Math.floorMod(cc,3);
            number=1+a*9+b*3+c;
        }
        public String code() {return ""+a+b+c;}
        public String symbols() {return STATES[a]+"·"+STATES[b]+"·"+STATES[c];}
        public String source() {
            int[] p=PAGES[number-1];
            return "《卦金书》第二册，第"+number+"卦，PDF第"+p[0]+(p[0]==p[1]?"":"—"+p[1])+"页";
        }
    }
    public static Cast manual(int a,int b,int c) {return new Cast(a,b,c);}
    public static Cast random() {
        SecureRandom r=new SecureRandom();
        return new Cast(r.nextInt(3),r.nextInt(3),r.nextInt(3));
    }
    public static Cast time(String question, long epochMs) {
        // 按分钟取数是软件实验，不是原书起卦法；保存epochMs即可复现。
        byte[] d=hash("TIME-V1|"+(epochMs/60000L)+"|"+question);
        return new Cast((d[0]&255)%3,(d[1]&255)%3,(d[2]&255)%3);
    }
    private static byte[] hash(String s) {
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            return md.digest(s.getBytes(StandardCharsets.UTF_8));
        } catch(Exception e) {throw new IllegalStateException(e);}
    }
    private static double clamp(double n,double low,double high) {
        return Math.max(low,Math.min(high,n));
    }
    private static double shift(int state) {return state==1?0.10:state==2?-0.10:0;}
    private static double[] poisson(double mu) {
        double[] p=new double[9];p[0]=Math.exp(-mu);
        for(int i=1;i<p.length;i++)p[i]=p[i-1]*mu/i;
        return p;
    }
    private static String winner(int h,int a) {return h>a?"胜":h==a?"平":"负";}
    private static String fmt(double x) {return String.format(Locale.CHINA,"%.1f%%",100*x);}
    private static int max(double[] p) {
        int ix=0;for(int i=1;i<p.length;i++)if(p[i]>p[ix])ix=i;return ix;
    }
    public static final class Prediction {
        public final String kind, display, direction, size, parity, quadrant, halfFull;
        public final String predictedScore, digits, sourceCode, version;
        public final double handicap, totalLine;
        Prediction(String k,String d,String direction,String size,String parity,String quadrant,
                   String halfFull,String score,String digits,String code,double handicap,double line) {
            this.kind=k;this.display=d;this.direction=direction;this.size=size;
            this.parity=parity;this.quadrant=quadrant;this.halfFull=halfFull;
            this.predictedScore=score;this.digits=digits;this.sourceCode=code;
            this.handicap=handicap;this.totalLine=line;this.version=VERSION;
        }
    }
    public static Prediction football(Cast cast,double homeXg,double awayXg,boolean useCast) {
        double originalHome=clamp(homeXg,0.05,4.0), originalAway=clamp(awayXg,0.05,4.0);
        double h=originalHome,a=originalAway;
        if(useCast) {
            h=clamp(h+shift(cast.a)+shift(cast.b)*0.4,0.05,4.0);
            a=clamp(a+shift(cast.c)+shift(cast.b)*0.4,0.05,4.0);
        }
        double[] hp=poisson(h),ap=poisson(a);
        double[] w=new double[3],quad=new double[4];
        double big=0, odd=0, sum=0;
        double[][] scores=new double[9][9];
        int bestH=0,bestA=0;
        for(int i=0;i<9;i++)for(int j=0;j<9;j++) {
            double p=hp[i]*ap[j];scores[i][j]=p;sum+=p;
            if(i>j)w[0]+=p;else if(i==j)w[1]+=p;else w[2]+=p;
            if(i+j>=3)big+=p;if((i+j)%2!=0)odd+=p;
            int q=(i+j>=3?2:0)+((i+j)%2==0?1:0);quad[q]+=p;
            if(p>scores[bestH][bestA]){bestH=i;bestA=j;}
        }
        for(int i=0;i<w.length;i++)w[i]/=sum;
        for(int i=0;i<quad.length;i++)quad[i]/=sum;
        big/=sum;odd/=sum;
        String[] directions={"胜","平","负"};
        String[] quadrants={"小单","小双","大单","大双"};
        String dir=directions[max(w)],sz=big>=0.5?"大":"小",par=odd>=0.5?"单":"双";
        String q=quadrants[max(quad)];
        // 以独立的上下半场泊松分布合成9种半全场概率。
        double[] htH=poisson(h*0.46),htA=poisson(a*0.46);
        double[] shH=poisson(h*0.54),shA=poisson(a*0.54);
        double[] hafu=new double[9];
        for(int x=0;x<9;x++)for(int y=0;y<9;y++)for(int z=0;z<9;z++)for(int v=0;v<9;v++) {
            int first=x>y?0:x==y?1:2,full=(x+z)>(y+v)?0:(x+z)==(y+v)?1:2;
            hafu[first*3+full]+=htH[x]*htA[y]*shH[z]*shA[v];
        }
        int hf=max(hafu);
        String halfFull=directions[hf/3]+"/"+directions[hf%3];
        StringBuilder top=new StringBuilder();boolean[][] selected=new boolean[9][9];
        for(int t=0;t<3;t++) {
            int bi=0,bj=0;double mx=-1;
            for(int i=0;i<9;i++)for(int j=0;j<9;j++)
                if(!selected[i][j]&&scores[i][j]>mx){mx=scores[i][j];bi=i;bj=j;}
            selected[bi][bj]=true;
            if(t>0)top.append("　");
            top.append(bi).append(":").append(bj).append("(").append(fmt(mx/sum)).append(")");
        }
        StringBuilder out=new StringBuilder();
        out.append("胜平负　").append(dir).append(" 〔胜").append(fmt(w[0]))
           .append(" / 平").append(fmt(w[1])).append(" / 负").append(fmt(w[2])).append("〕\n")
           .append("大小球　").append(sz).append("2.5 〔大").append(fmt(big)).append("〕\n")
           .append("单双　").append(par).append(" 〔单").append(fmt(odd)).append("〕\n")
           .append("四象　").append(q).append("（小≤2球；大≥3球）\n")
           .append("半全场　").append(halfFull).append("（模型概率最高组合）\n")
           .append("比分候选　").append(top).append("\n")
           .append("泊松期望进球　主").append(String.format(Locale.CHINA,"%.2f",h))
           .append(" / 客").append(String.format(Locale.CHINA,"%.2f",a)).append("\n")
           .append("基础输入：主").append(originalHome).append(" / 客").append(originalAway).append("\n")
           .append(useCast?"已启用三态微调：各态改变期望进球约±0.10，未经验证。":
                    "未启用三态微调：仅为手动xG基准泊松模型。");
        return new Prediction("足球",out.toString(),dir,sz,par,q,halfFull,
                              bestH+":"+bestA,"",cast.code(),0,2.5);
    }
    private static double normalCdf(double z) {
        // 常用误差函数近似，足够用于界面方向参考，不替代校准模型。
        double x=Math.abs(z)/Math.sqrt(2.0);
        double t=1.0/(1.0+0.3275911*x);
        double erf=1.0-(((((1.061405429*t-1.453152027)*t)
          +1.421413741)*t-0.284496736)*t+0.254829592)*t)*Math.exp(-x*x);
        return 0.5*(1+(z<0?-erf:erf));
    }
    public static Prediction basketball(Cast cast,double expectedHome,double expectedAway,
                                        double homeHandicap,double totalLine,boolean useCast) {
        double h=clamp(expectedHome,50,160),a=clamp(expectedAway,50,160);
        if(useCast) {
            h+=shift(cast.a)*12+shift(cast.b)*5;
            a+=shift(cast.c)*12+shift(cast.b)*5;
        }
        double cover=normalCdf((h-a+homeHandicap)/12.0);
        double big=normalCdf((h+a-totalLine)/18.0);
        String dir=cover>=0.5?"主队上盘":"主队下盘";
        String size=big>=0.5?"大分":"小分";
        String score=Math.round(h)+":"+Math.round(a);
        String text=String.format(Locale.CHINA,
          "让分判断　%s 〔主队上盘 %.1f%%〕\n大小分　%s 〔大分 %.1f%%〕\n单双　无可靠优势（默认近似50%%）\n预测参考比分　%s\n主队让分线　%+.1f；总分线　%.1f\n正态近似：分差标准差12分，总分标准差18分。\n%s",
          dir,cover*100,size,big*100,score,homeHandicap,totalLine,
          useCast?"三态对预期得分的实验偏移约±1.2分，未经验证。":"当前仅按人工输入均值估计。");
        return new Prediction("篮球",text,dir,size,"未判","", "",score,"",cast.code(),homeHandicap,totalLine);
    }
    public static Prediction digits(Cast cast,String salt) {
        byte[] b=hash("DIGITS-V1|"+cast.code()+"|"+salt);
        StringBuilder three=new StringBuilder(),five=new StringBuilder();
        for(int i=0;i<5;i++) {
            int d=(b[i]&255)%10;
            five.append(d);if(i<3)three.append(d);
        }
        String digits=three.toString();
        String text="排列三/福彩3D　"+digits+"\n排列五　"+five+
          "\n百十个位　"+digits.charAt(0)+" / "+digits.charAt(1)+" / "+digits.charAt(2)+
          "\n规则：SHA-256固定种子取数，是可复现的实验随机编码，不具有已证实的中奖优势。";
        return new Prediction("数字",text,digits,"","", "", "",digits,five.toString(),cast.code(),0,0);
    }
    public static String index27() {
        StringBuilder out=new StringBuilder();
        for(int a=0;a<3;a++)for(int b=0;b<3;b++)for(int c=0;c<3;c++) {
            Cast k=manual(a,b,c);
            out.append(String.format(Locale.CHINA,"%02d  %s  %s\n",
                     k.number,k.symbols(),k.source().replace("《卦金书》第二册，","")));
        }
        return out.toString();
    }
}
