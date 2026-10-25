package com.abe618.liuyaopick3;

import java.util.*;
import java.util.Locale;

/**
 * Liuyao-first P3 research score engine.
 * Traditional NaJia and mathematical lottery mapping are clearly separated.
 * No scoring rule uses past winning results or future results.
 */
public final class HexP3V3 {
    public static final String VERSION="hex-p3-v3.0.0";
    public static final String[] MASKS={"不限","大单","大双","小单","小双","大","小","单","双"};
    private static final String[] ZHI={"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};
    private static final int[] ZNUM={9,8,7,6,5,4,9,8,7,6,5,4};
    private static final String[] GAN={"甲","乙","丙","丁","戊","己","庚","辛","壬","癸"};
    private static final int[] GNUM={9,8,7,6,5,9,8,7,6,5};
    private static final String[] POS={"百","十","个"};

    public static final class Result {
        public int rawSingle;
        public int[] rawBolds;
        public int single;
        public int[] bolds;
        public int[][] rawFour=new int[3][];
        public int[][] four=new int[3][];
        public int[][] voteCounts=new int[3][10];
        public double[][] rawScores=new double[3][10];
        public double[][] scores=new double[3][10];
        public ArrayList<String> direct=new ArrayList<>();
        public ArrayList<String> allDirect=new ArrayList<>();
        public ArrayList<String> rawGroups=new ArrayList<>();
        public String detail,summary,method,trace;
        public String[] masks,picks;
        public int externalMethods,combinationCount;
        public String model=VERSION;
    }
    private HexP3V3(){}

    private static int numZ(String s) {
        for(int i=0;i<12;i++)if(ZHI[i].equals(s))return ZNUM[i];
        return -1;
    }
    private static int numG(String s) {
        for(int i=0;i<10;i++)if(GAN[i].equals(s))return GNUM[i];
        return -1;
    }
    private static int tri(String name,boolean upper) {
        if(name==null||name.length()<2)return -1;
        char c=name.length()>=3&&name.charAt(1)=='为'?name.charAt(0):name.charAt(upper?0:1);
        switch(c) {
            case '天':case '乾':return 6; case '地':case '坤':return 2;
            case '雷':case '震':return 3; case '风':case '巽':return 4;
            case '水':case '坎':return 1; case '火':case '离':return 9;
            case '山':case '艮':return 8; case '泽':case '兑':return 7;
            default:return -1;
        }
    }
    private static String stemFor(int trigram,boolean top) {
        switch(trigram) {
            case 6:return top?"壬":"甲";
            case 2:return top?"癸":"乙";
            case 3:return "庚";
            case 4:return "辛";
            case 1:return "戊";
            case 9:return "己";
            case 8:return "丙";
            case 7:return "丁";
            default:return "";
        }
    }
    private static int[] hetu(String e){
        switch(e){
            case "金":return new int[]{4,9};
            case "木":return new int[]{3,8};
            case "水":return new int[]{1,6};
            case "火":return new int[]{2,7};
            default:return new int[]{5,0};
        }
    }
    private static String element(String branch){
        if("子亥".contains(branch))return "水";
        if("寅卯".contains(branch))return "木";
        if("巳午".contains(branch))return "火";
        if("申酉".contains(branch))return "金";
        return "土";
    }
    private static void put(double[][] m,int p,int d,double w){
        if(p>=0&&p<3&&d>=0&&d<10)m[p][d]+=w;
    }
    private static int[] rank(double[] scores){
        Integer[] xs=new Integer[10]; for(int d=0;d<10;d++)xs[d]=d;
        Arrays.sort(xs,(a,b)->{
            int cmp=Double.compare(scores[b],scores[a]);
            return cmp!=0?cmp:Integer.compare(a,b);
        });
        int[] v=new int[10];for(int i=0;i<10;i++)v[i]=xs[i];return v;
    }
    private static int[] global(double[][] m){
        double[] a=new double[10];for(int p=0;p<3;p++)for(int d=0;d<10;d++)a[d]+=m[p][d];
        return rank(a);
    }
    private static String join(int[] a,int limit) {
        StringBuilder x=new StringBuilder();
        for(int i=0;i<Math.min(a.length,limit);i++){if(i>0)x.append("、");x.append(a[i]);}
        return x.toString();
    }
    private static int[] take(int[] a,int count){
        return Arrays.copyOf(a,Math.min(a.length,count));
    }
    private static boolean eligible(int d,String mask,String filter){
        if(filter!=null&&!filter.trim().isEmpty()){
            String z=filter.replaceAll("[^0-9]","");
            if(!z.isEmpty()&&z.indexOf((char)('0'+d))<0)return false;
        }
        boolean large=d>=5,even=d%2==0;
        switch(mask){
            case "大单":return large&&!even;
            case "大双":return large&&even;
            case "小单":return !large&&!even;
            case "小双":return !large&&even;
            case "大":return large;
            case "小":return !large;
            case "单":return !even;
            case "双":return even;
            default:return true;
        }
    }
    private static void distinctAdd(List<Integer> arr,int d){
        if(d>=0&&!arr.contains(d))arr.add(d);
    }
    private static String group(String n){
        char[] c=n.toCharArray();Arrays.sort(c);return new String(c);
    }
    private static int uniq(String n){
        return (n.charAt(0)==n.charAt(1)&&n.charAt(1)==n.charAt(2))?1:
                ((n.charAt(0)==n.charAt(1)||n.charAt(1)==n.charAt(2)||n.charAt(0)==n.charAt(2))?2:3);
    }

    private static double[][] hexScores(LiuyaoEngine.Analysis a,StringBuilder detail){
        double[][] s=new double[3][10];
        // original six-line emphasis is the primary (and largest) score channel
        for(int p=0;p<3;p++){
            double mx=0;
            for(int d=0;d<10;d++)mx=Math.max(mx,a.positionScores[p][d]);
            for(int d=0;d<10;d++)s[p][d]=mx>0?6.0*a.positionScores[p][d]/mx:0;
        }
        int u=tri(a.hexName,true),l=tri(a.hexName,false),
                cu=tri(a.changedHexName,true),cl=tri(a.changedHexName,false);
        put(s,0,u,1.20);put(s,1,l,1.00);put(s,2,cu,0.85);put(s,2,cl,0.60);
        put(s,0,cu,0.40);put(s,1,cl,0.40);
        detail.append("【一 六爻本体】").append(a.hexName).append("→").append(a.changedHexName)
          .append("，世").append(a.shi).append("应").append(a.ying).append("。")
          .append("六亲/旺衰/空亡原评分是主通道，后天本变卦 ")
          .append(u).append('/').append(l).append("→").append(cu).append('/').append(cl).append("。\n");
        int[] trig={l,u},changed={cl,cu};
        for(int i=0;i<6;i++){
            LiuyaoEngine.Line ln=a.lines[i];
            int p=i>=4?0:(i>=2?1:2),k=i>=3?1:0;
            String stem=stemFor(trig[k],i>=3);
            String nextStem=stemFor(changed[k],i>=3);
            int[] h=hetu(ln.element);
            double mul=ln.moving?0.63:0.16;
            put(s,p,h[0],mul);put(s,p,h[1],mul);
            put(s,p,numG(stem),ln.moving?0.55:0.12);
            put(s,p,numZ(ln.branch),ln.moving?0.55:0.12);
            if(ln.moving){
                int[] changedHetu=hetu(element(ln.changedBranch));
                put(s,p,changedHetu[0],0.42);put(s,p,changedHetu[1],0.42);
                put(s,p,numG(nextStem),0.42);put(s,p,numZ(ln.changedBranch),0.42);
            }
            detail.append(i+1).append("爻 ").append(stem).append(ln.branch)
                  .append(ln.element).append(ln.moving?"动→"+nextStem+ln.changedBranch:"静")
                  .append("：河图").append(h[0]).append('/').append(h[1])
                  .append(" 太玄").append(numG(stem)).append('/').append(numZ(ln.branch))
                  .append("。\n");
        }
        detail.append("【约定】河图水1/6、火2/7、木3/8、金4/9、土5/0；")
              .append("太玄干甲己9乙庚8丙辛7丁壬6戊癸5；")
              .append("支子午9丑未8寅申7卯酉6辰戌5巳亥4。")
              .append("纳甲数转彩票位置属于实验映射。\n\n");
        return s;
    }

    private static int[][] readVotes(String raw,Result r,StringBuilder notes){
        int[][] counts=new int[3][10];
        Set<String> seen=new HashSet<>();
        if(raw==null)return counts;
        for(String l:raw.split("\\r?\\n")){
            String[] a=l.trim().split("\\|",-1);
            if(a.length!=3)continue;
            String title=a[0].trim();
            int split=Math.max(title.indexOf(':'),title.indexOf('：'));
            String label=split>=0?title.substring(0,split).trim():title;
            if(label.isEmpty()||seen.contains(label))continue;
            String[] v={split>=0?title.substring(split+1):title,a[1],a[2]};
            int[][] candidate=new int[3][10];
            boolean hasAll=true;
            for(int p=0;p<3;p++){
                String digits=v[p].replaceAll("[^0-9]","");
                if(digits.length()==0)hasAll=false;
                for(int i=0;i<digits.length();i++)candidate[p][digits.charAt(i)-'0']=1;
            }
            if(!hasAll)continue;
            seen.add(label);r.externalMethods++;
            for(int p=0;p<3;p++)for(int d=0;d<10;d++)counts[p][d]+=candidate[p][d];
        }
        notes.append("外部方法 ").append(r.externalMethods).append(" 家（每家每位各数字只计一次；同名方法去重）。\n");
        return counts;
    }

    public static Result calculate(LiuyaoEngine.Analysis main,LiuyaoEngine.Analysis comparison,
                                   String[] masks,String[] picks,String external){
        Result r=new Result();
        r.method=comparison==null?"六爻主盘":"六爻主盘＋辅助盘";
        r.masks=masks.clone();r.picks=picks.clone();
        StringBuilder notes=new StringBuilder();
        double[][] base=hexScores(main,notes);
        double[][] compare=comparison==null?null:hexScores(comparison,new StringBuilder());
        int[][] votes=readVotes(external,r,notes);
        r.voteCounts=votes;

        // primary hexagram 80-90%, secondary 10-15%, manual votes bounded and small
        for(int p=0;p<3;p++)for(int d=0;d<10;d++){
            r.rawScores[p][d]=base[p][d];
            r.scores[p][d]=(compare==null?1.0:0.85)*base[p][d]+(compare==null?0:0.15*compare[p][d])
                    +(r.externalMethods>0?0.90*votes[p][d]/r.externalMethods:0);
        }
        int[] rawRank=global(r.rawScores), allRank=global(r.scores);
        r.rawSingle=rawRank[0];r.rawBolds=take(rawRank,3);
        r.single=allRank[0];r.bolds=take(allRank,3);

        for(int p=0;p<3;p++){
            int[] rr=rank(base[p]),sr=rank(r.scores[p]);
            r.rawFour[p]=take(rr,4);
            List<Integer> selected=new ArrayList<>();
            // Rank-based first two, next one reserved from core hex, fourth from alternative sources.
            for(int d:sr)if(eligible(d,masks[p],picks[p])){distinctAdd(selected,d);if(selected.size()==2)break;}
            for(int d:rr)if(eligible(d,masks[p],picks[p])){distinctAdd(selected,d);if(selected.size()>=3)break;}
            if(r.externalMethods>0){
                int best=-1;
                for(int d=0;d<10;d++)
                    if(eligible(d,masks[p],picks[p])&&!selected.contains(d)
                            &&(best<0||votes[p][d]>votes[p][best]))best=d;
                if(best>=0&&votes[p][best]>0)distinctAdd(selected,best);
            }else if(compare!=null){
                for(int d:rank(compare[p]))if(eligible(d,masks[p],picks[p])&&!selected.contains(d)){
                    distinctAdd(selected,d);break;
                }
            }
            for(int d:sr)if(eligible(d,masks[p],picks[p])){
                distinctAdd(selected,d);if(selected.size()>=4)break;
            }
            r.four[p]=new int[selected.size()];
            for(int i=0;i<selected.size();i++)r.four[p][i]=selected.get(i);
        }

        ArrayList<String[]> ranked=new ArrayList<>();
        for(int a:r.four[0])for(int b:r.four[1])for(int c:r.four[2]){
            String n=""+a+b+c;
            double score=r.scores[0][a]+r.scores[1][b]+r.scores[2][c];
            ranked.add(new String[]{n,String.valueOf(score)});
        }
        ranked.sort((a,b)->{
            int t=Double.compare(Double.parseDouble(b[1]),Double.parseDouble(a[1]));
            return t!=0?t:a[0].compareTo(b[0]);
        });
        for(int i=0;i<ranked.size();i++){
            r.allDirect.add(ranked.get(i)[0]);
            if(i<12)r.direct.add(ranked.get(i)[0]);
        }
        r.combinationCount=r.allDirect.size();

        LinkedHashSet<String> rawGrp=new LinkedHashSet<>();
        for(String row:main.groupTop){
            if(row.length()>=3 &&row.substring(0,3).matches("\\d{3}"))rawGrp.add(row.substring(0,3));
            if(rawGrp.size()>=8)break;
        }
        r.rawGroups.addAll(rawGrp);

        StringBuilder out=new StringBuilder();
        out.append("第一单胆 ").append(r.single).append("　双胆 ").append(join(r.bolds,2))
           .append("　三胆 ").append(join(r.bolds,3)).append("\n");
        out.append("六爻原始单胆 ").append(r.rawSingle).append("（独立保留）\n");
        for(int p=0;p<3;p++){
            out.append(POS[p]).append("位：").append(join(r.four[p],4)).append("  (")
               .append(masks[p]).append(")\n");
        }
        out.append("直选前列：").append(String.join("、",r.direct.subList(0,Math.min(8,r.direct.size())))).append("\n");
        if(!r.direct.isEmpty())out.append("第一直选 ").append(r.direct.get(0))
             .append("　结构").append(uniq(r.direct.get(0))==3?"组六":uniq(r.direct.get(0))==2?"组三":"豹子").append("\n");
        out.append("复式共 ").append(r.combinationCount).append(" 组；")
           .append("原六爻组选独立保护 ").append(String.join("、",r.rawGroups)).append("\n");
        out.append("分位合参是研究规则，非开奖概率。");
        r.summary=out.toString();

        notes.append("\n【六爻主盘单胆】").append(join(r.rawBolds,3))
             .append("\n【多通道合参单胆】").append(join(r.bolds,3)).append("\n");
        for(int p=0;p<3;p++){
            notes.append(POS[p]).append("位：原六爻")
                 .append(join(r.rawFour[p],4))
                 .append("；压缩保护四码").append(join(r.four[p],4))
                 .append("；外票")
                 .append(Arrays.toString(votes[p])).append("\n");
        }
        if(comparison!=null)notes.append("辅助盘：").append(comparison.hexName)
                .append("→").append(comparison.changedHexName)
                .append("（本盘不视为额外独立实验样本）\n");
        notes.append("原始组选不受最终四码删除影响，单独保存。\n")
             .append("重号包含组三/豹子，但不因667、677等个例修改理论基础频率。")
             .append("随机等概率基线：组六72%、组三27%、豹子1%。\n");
        r.detail=notes.toString();
        return r;
    }
}
