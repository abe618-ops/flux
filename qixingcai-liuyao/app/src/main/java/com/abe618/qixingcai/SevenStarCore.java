package com.abe618.qixingcai;

import java.time.*;
import java.util.*;

/**
 * 七星彩七位实验取数模型。前六位0..9，第七位0..14。
 * 所有卦象→号码的映射都是可冻结的实验约定，不是古籍中的博彩公式。
 */
public final class SevenStarCore {
    private SevenStarCore(){}
    public static final String VERSION="QXC_HEX_FUSION_V1.0";
    public static final String[] METHODS={"六爻原盘","纳甲动变","河图五行","太玄干支","后天八卦","梅花时间","禽星覆爻"};
    public static final double[] WEIGHTS={3.0,2.5,1.7,1.6,1.3,1.0,0.4};
    public static final String[] LABELS={"第1位","第2位","第3位","第4位","第5位","第6位","第7位"};
    private static final String[] BRANCHES={"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};
    private static final String[] STEMS={"甲","乙","丙","丁","戊","己","庚","辛","壬","癸"};
    private static final int[] BN={9,8,7,6,5,4,9,8,7,6,5,4};
    private static final int[] GN={9,8,7,6,5,9,8,7,6,5};
    private static final String[] XIUS={"角","亢","氐","房","心","尾","箕","斗","牛","女","虚","危","室","壁","奎","娄","胃","昴","毕","觜","参","井","鬼","柳","星","张","翼","轸"};
    public static final class Forecast {
        public final double[][][] methodScores=new double[METHODS.length][7][15];
        public final int[][][] each=new int[METHODS.length][7][4];
        public final double[][] combined=new double[7][15];
        public final int[][] picks=new int[7][4];
        public final int[][] raw=new int[7][4];
        public final int[] bolds=new int[3];
        public final ArrayList<String> top=new ArrayList<>();
        public final StringBuilder[] traces=new StringBuilder[METHODS.length];
        public String summary,details,pillars,mainHex,changedHex,seed;
        public long comboCount;
    }
    private static class Combo {
        String n; double score;
        Combo(String n,double score){this.n=n;this.score=score;}
    }
    private static final int[] SIZES={10,10,10,10,10,10,15};
    private static int mod(int a,int b){int m=a%b;return m<0?m+b:m;}
    private static int g(String stem){for(int i=0;i<STEMS.length;i++)if(STEMS[i].equals(stem))return GN[i];return -1;}
    private static int z(String branch){for(int i=0;i<BRANCHES.length;i++)if(BRANCHES[i].equals(branch))return BN[i];return -1;}
    private static int zi(String branch){for(int i=0;i<BRANCHES.length;i++)if(BRANCHES[i].equals(branch))return i;return 0;}
    private static int ht(String e,boolean big){
        switch(e){
            case "水":return big?6:1;
            case "火":return big?7:2;
            case "木":return big?8:3;
            case "金":return big?9:4;
            default:return big?0:5;
        }
    }
    private static int tri(char c){
        switch(c){
            case '天':case '乾':return 6;
            case '地':case '坤':return 2;
            case '雷':case '震':return 3;
            case '风':case '巽':return 4;
            case '水':case '坎':return 1;
            case '火':case '离':return 9;
            case '山':case '艮':return 8;
            case '泽':case '兑':return 7;
        }
        return 5;
    }
    private static int[] trigrams(String name){
        if(name.length()<2)return new int[]{6,6};
        if(name.charAt(1)=='为')return new int[]{tri(name.charAt(0)),tri(name.charAt(0))};
        return new int[]{tri(name.charAt(0)),tri(name.charAt(1))};
    }
    private static String stemOf(int trigram,boolean upper){
        switch(trigram){
            case 6:return upper?"壬":"甲";
            case 2:return upper?"癸":"乙";
            case 3:return "庚";
            case 4:return "辛";
            case 1:return "戊";
            case 9:return "己";
            case 8:return "丙";
            case 7:return "丁";
            default:return "戊";
        }
    }
    private static void add(Forecast f,int method,int position,int digit,double score){
        int size=SIZES[position];
        f.methodScores[method][position][mod(digit,size)]+=Math.max(0,score);
    }
    private static int[] best(double[] scores,int size,int n){
        Integer[] all=new Integer[size];
        for(int i=0;i<size;i++)all[i]=i;
        Arrays.sort(all,(a,b)->{
            int c=Double.compare(scores[b],scores[a]);
            return c!=0?c:Integer.compare(a,b);
        });
        int[] out=new int[n];
        for(int i=0;i<n;i++)out[i]=all[i];
        return out;
    }
    private static String fmt(int[] x){
        StringBuilder s=new StringBuilder();
        for(int i=0;i<x.length;i++){if(i>0)s.append('/');s.append(x[i]);}
        return s.toString();
    }
    private static String number(int[] digits){
        StringBuilder s=new StringBuilder();
        for(int i=0;i<6;i++)s.append(digits[i]);
        return s.append("+").append(String.format(Locale.ROOT,"%02d",digits[6])).toString();
    }
    private static void notes(Forecast f,int method,String s){f.traces[method].append(s).append("\n");}
    private static int moveCount(LiuyaoEngine.Analysis a){
        int x=0;for(LiuyaoEngine.Line l:a.lines)if(l.moving)x++;return x;
    }
    private static int role(LiuyaoEngine.Analysis a,LiuyaoEngine.Line l){
        int w=0;
        if(l.no==a.shi)w+=2;
        if(l.no==a.ying)w+=3;
        if("妻财".equals(l.relative))w+=3;
        if(l.strength>0)w++;
        if(l.moving)w+=2;
        return w;
    }

    public static Forecast calculate(LiuyaoEngine.Analysis a, LocalDate date,LocalTime time, String seed) {
        Forecast f=new Forecast();
        f.mainHex=a.hexName;f.changedHex=a.changedHexName;
        f.pillars=a.fourPillars;f.seed=seed;
        for(int m=0;m<METHODS.length;m++)f.traces[m]=new StringBuilder();
        int[] tri=trigrams(a.hexName),changed=trigrams(a.changedHexName);
        int move=moveCount(a);
        notes(f,0,"六爻从上爻到初爻依次对应第1—6位；第7位独立按六爻六数和卦数取余15。");
        notes(f,1,"旺动爻、世应、妻财有加分，静爻保留备用数；纳甲天干按所属八卦取干（实验取数）。");
        notes(f,2,"河图：水1/6 火2/7 木3/8 金4/9 土5/0。");
        notes(f,3,"太玄干：甲己9 乙庚8 丙辛7 丁壬6 戊癸5；支：子午9 丑未8 寅申7 卯酉6 辰戌5 巳亥4。");
        notes(f,4,"后天八卦：乾6 兑7 离9 震3 巽4 坎1 艮8 坤2。");
        notes(f,5,"梅花时间取数：北京日期、农历月日、时辰、上下卦辅助，固定余数映射。");
        notes(f,6,"禽星覆爻为28宿循环覆盖六爻的现代实验层，非《禽星易见》完整传统法。");

        int sumPreferred=0,sumBranch=0,sumStem=0;
        for(int p=0;p<6;p++){
            LiuyaoEngine.Line ln=a.lines[5-p];
            int i=5-p;
            int group=i>=3?0:1;  // upper/lower hexagram
            String stem=stemOf(tri[group],i>=3);
            String changedStem=stemOf(changed[group],i>=3);
            int b=z(ln.branch),newB=z(ln.changedBranch),st=g(stem),newSt=g(changedStem);
            int primary=ln.preferred,backup=ln.alternate;
            int pow=role(a,ln);
            sumPreferred+=primary;
            sumBranch+=b;sumStem+=st;

            // 1 - original Liuyao score, apply consistent per-line rank
            add(f,0,p,primary,5.0+pow*0.52);
            add(f,0,p,backup,2.2+pow*0.15);
            add(f,0,p,ln.voided?0:mod(primary+backup,10),0.65);
            if(ln.moving)add(f,0,p,mod(primary+newB,10),1.3);
            notes(f,0,LABELS[p]+"←第"+ln.no+"爻："+ln.relative+ln.branch+ln.element+
                (ln.no==a.shi?"(世)":"")+(ln.no==a.ying?"(应)":"")+
                (ln.moving?" 动":" 静")+"、"+ln.strengthText+"、"+(ln.voided?"空亡":"非空")+
                " 取"+primary+" 备"+backup);

            // 2 - moving NaJia
            add(f,1,p,primary,1.4);
            add(f,1,p,ln.moving?newB:b,ln.moving?3.4:1.5);
            add(f,1,p,ln.moving?newSt:st,ln.moving?3.2:1.3);
            add(f,1,p,ln.moving?ln.alternate:mod(b+st,10),1.0);
            notes(f,1,LABELS[p]+" "+stem+ln.branch+"→"+changedStem+ln.changedBranch+" 动"+ln.moving+
                    " 干数"+st+"/"+newSt+" 支数"+b+"/"+newB);

            // 3 - Hetu
            int h1=ht(ln.element,false),h2=ht(ln.element,true);
            add(f,2,p,ln.strength>0?h2:h1,3.0);
            add(f,2,p,ln.strength>0?h1:h2,2.3);
            add(f,2,p,ht("土",true),ln.voided?1.5:0.2);
            notes(f,2,LABELS[p]+" "+ln.element+" 河图"+h1+"/"+h2);

            // 4 - stem and branch Taixuan
            add(f,3,p,st,3.1);add(f,3,p,b,3.0);
            if(ln.moving){add(f,3,p,newSt,2.4);add(f,3,p,newB,2.5);}
            notes(f,3,LABELS[p]+" 太玄"+st+"、"+b+(ln.moving?"→"+newSt+"、"+newB:""));

            // 5 - Bagua
            add(f,4,p,tri[group],3.8);
            add(f,4,p,changed[group],ln.moving?3.0:1.1);
            add(f,4,p,tri[1-group],1.3);
            notes(f,4,LABELS[p]+" 本卦"+tri[group]+"→"+changed[group]);

            // 6 - Meihua
            int base=date.getYear()%10+date.getMonthValue()+date.getDayOfMonth()
                    +time.getHour()+time.getMinute()/10 + p*3;
            add(f,5,p,base,3.3);add(f,5,p,base+tri[group],2.7);
            add(f,5,p,base+move+changed[group],2.0);
            notes(f,5,LABELS[p]+" 时间余数"+mod(base,10)+"、"+mod(base+tri[group],10));

            // 7 - poultry star experimental mapping (28宿 and branch cycle)
            int index=mod((int)date.toEpochDay()+time.getHour()/2+p*4,28);
            int digit=mod(index+zi(ln.branch),10);
            add(f,6,p,digit,3.2);add(f,6,p,index,2.1);
            add(f,6,p,index+move,1.2);
            notes(f,6,LABELS[p]+"覆宿"+XIUS[index]+" → "+digit);
        }

        // Seventh position: independent 0..14, not 0..9. Four per method.
        int[] tailSeeds={
                sumPreferred+tri[0]+move,
                sumBranch+changed[1]+move,
                sumPreferred+ht("土",false)+tri[1],
                sumStem+sumBranch,
                tri[0]+tri[1]+changed[0]+changed[1],
                date.getMonthValue()*2+date.getDayOfMonth()+time.getHour(),
                (int)(mod((int)date.toEpochDay(),28)+move+sumBranch)
        };
        for(int m=0;m<METHODS.length;m++){
            int d=mod(tailSeeds[m],15);
            add(f,m,6,d,5.0);
            add(f,m,6,d+move+1,3.0);
            add(f,m,6,d+tri[0],2.0);
            add(f,m,6,d+changed[1]+2,1.4);
            notes(f,m,"第7位0—14特殊取数基点="+d+"，含动爻、卦数变换。");
        }
        for(int m=0;m<METHODS.length;m++)
            for(int p=0;p<7;p++)f.each[m][p]=best(f.methodScores[m][p],SIZES[p],4);

        // ensemble; no tuning by historical winning outcomes; correlated channels are not independent
        for(int p=0;p<7;p++)for(int d=0;d<SIZES[p];d++){
            double sum=0;
            for(int m=0;m<METHODS.length;m++){
                double max=0;for(int k=0;k<SIZES[p];k++)max=Math.max(max,f.methodScores[m][p][k]);
                sum+=WEIGHTS[m]*(max>0?f.methodScores[m][p][d]/max:0);
            }
            f.combined[p][d]=sum;
        }
        for(int p=0;p<7;p++){
            f.raw[p]=f.each[0][p].clone();
            f.picks[p]=best(f.combined[p],SIZES[p],4);
        }
        // Global experimental bolds, preserve 0—9 only (not special seventh 10—14)
        double[] total=new double[10];
        for(int p=0;p<6;p++)for(int d=0;d<10;d++)total[d]+=f.combined[p][d];
        int[] singles=best(total,10,3);
        System.arraycopy(singles,0,f.bolds,0,3);

        long count=1;
        for(int p=0;p<7;p++)count*=f.picks[p].length;
        f.comboCount=count;
        ArrayList<Combo> ranked=new ArrayList<>();
        for(int a0:f.picks[0])for(int a1:f.picks[1])for(int a2:f.picks[2])
            for(int a3:f.picks[3])for(int a4:f.picks[4])for(int a5:f.picks[5])
                for(int a6:f.picks[6]){
                    int[] ns={a0,a1,a2,a3,a4,a5,a6};
                    double sc=0;for(int p=0;p<7;p++)sc+=f.combined[p][ns[p]];
                    ranked.add(new Combo(number(ns),sc));
                }
        ranked.sort((x,y)->{int t=Double.compare(y.score,x.score);return t!=0?t:x.n.compareTo(y.n);});
        for(int i=0;i<Math.min(20,ranked.size());i++)f.top.add(ranked.get(i).n);

        StringBuilder display=new StringBuilder();
        display.append("六爻实验单胆 ").append(f.bolds[0]).append("；三胆 ")
               .append(f.bolds[0]).append('/').append(f.bolds[1]).append('/').append(f.bolds[2]).append("\n");
        for(int p=0;p<7;p++)display.append(LABELS[p]).append("：")
            .append(fmt(f.picks[p])).append("\n");
        display.append("合参Top6：");
        for(int i=0;i<Math.min(6,f.top.size());i++)display.append(i==0?"":"、").append(f.top.get(i));
        display.append("\n组合数 ").append(f.comboCount)
            .append("；仅理论全中覆盖 "+String.format(Locale.ROOT,"%.5f",100.0*f.comboCount/15000000.0)+"%");
        f.summary=display.toString();

        StringBuilder details=new StringBuilder();
        details.append("【排盘】").append(f.pillars).append("\n")
                .append(a.hexName).append("→").append(a.changedHexName).append("；世")
                .append(a.shi).append("／应").append(a.ying).append("；动爻")
                .append(move).append("。\n\n");
        for(int m=0;m<METHODS.length;m++){
            details.append("【").append(METHODS[m]).append("】权重").append(WEIGHTS[m]).append("\n");
            for(int p=0;p<7;p++)details.append(LABELS[p]).append(' ').append(fmt(f.each[m][p])).append("\n");
            details.append(f.traces[m]).append("\n");
        }
        details.append("【原始六爻】\n").append(a.text)
        .append("\n以上六爻→七星彩号码映射属于现代实验；各方法复用了同一张卦，不能按七项独立样本计算概率。");
        f.details=details.toString();
        return f;
    }
}
