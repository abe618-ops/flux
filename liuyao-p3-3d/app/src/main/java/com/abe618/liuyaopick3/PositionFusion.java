package com.abe618.liuyaopick3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** V2 independent positional voting layer; experimental mappings, not calibrated probabilities. */
public final class PositionFusion {
    private PositionFusion() {}
    public static final String VERSION="p3-pos-fusion-v2.0.0";
    public static final String[] MASKS={"不限","大单","大双","小单","小双","大","小","单","双"};
    private static final String[] BRANCHES={"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};
    private static final int[] TAI={9,8,7,6,5,4,9,8,7,6,5,4};

    public static final class Result {
        public double[][] scores=new double[3][10];
        public int[][] votes=new int[3][10];
        public String[] masks;
        public String[] customPicks;
        public List<String> choices=new ArrayList<>();
        public List<String> groups=new ArrayList<>();
        public int[] singleOrder=new int[10];
        public int[][] positionOrder=new int[3][10];
        public int validCount;
        public int voteMethods;
        public int single;
        public String details;
        public String summary;
        public String frozenKey;
    }

    private static int tri(String name, boolean upper) {
        if(name==null||name.isEmpty())return -1;
        char c;
        if(name.length()>=3 && name.charAt(1)=='为') c=name.charAt(0);
        else c=name.charAt(upper?0:1);
        switch(c) {
            case '坎':case '水':return 1;
            case '坤':case '地':return 2;
            case '震':case '雷':return 3;
            case '巽':case '风':return 4;
            case '乾':case '天':return 6;
            case '兑':case '泽':return 7;
            case '艮':case '山':return 8;
            case '离':case '火':return 9;
            default:return -1;
        }
    }
    private static int xiantian(int houtian) {
        switch(houtian) {
            case 6:return 1;case 7:return 2;case 9:return 3;case 3:return 4;
            case 4:return 5;case 1:return 6;case 8:return 7;case 2:return 8;
            default:return -1;
        }
    }
    private static int[] pair(String e) {
        switch(e) {
            case "水":return new int[]{1,6};
            case "火":return new int[]{2,7};
            case "木":return new int[]{3,8};
            case "金":return new int[]{4,9};
            default:return new int[]{5,0};
        }
    }
    private static int tai(String branch) {
        for(int i=0;i<12;i++)if(BRANCHES[i].equals(branch))return TAI[i];
        return -1;
    }
    private static String element(String branch) {
        if("亥子".contains(branch))return "水";
        if("寅卯".contains(branch))return "木";
        if("巳午".contains(branch))return "火";
        if("申酉".contains(branch))return "金";
        return "土";
    }
    private static void add(double[][] m,int p,int d,double w){
        if(p>=0&&p<3&&d>=0&&d<10)m[p][d]+=w;
    }
    private static boolean valid(int digit, String mask, String picks) {
        if(picks!=null&&!picks.trim().isEmpty()){
            int[] allowed=parseDigits(picks);
            if(allowed.length>0){boolean found=false;for(int n:allowed)if(n==digit)found=true;if(!found)return false;}
        }
        boolean large=digit>=5, even=digit%2==0;
        switch(mask) {
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
    private static int[] sort(double[] a){
        Integer[] n=new Integer[10];for(int i=0;i<10;i++)n[i]=i;
        Arrays.sort(n,(x,y)->{int c=Double.compare(a[y],a[x]);return c!=0?c:Integer.compare(x,y);});
        int[] v=new int[10];for(int i=0;i<10;i++)v[i]=n[i];return v;
    }
    private static String digits(int[] arr,int limit){
        StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(limit,arr.length);i++){if(i>0)b.append("、");b.append(arr[i]);}return b.toString();
    }
    private static String group(String n) {
        char[] c=n.toCharArray();Arrays.sort(c);return new String(c);
    }
    private static int countRepeated(String n) {
        return new HashSet<>(Arrays.asList(n.substring(0,1),n.substring(1,2),n.substring(2,3))).size();
    }
    private static int[] parseDigits(String str){
        LinkedHashSet<Integer> set=new LinkedHashSet<>();
        String z=str.replaceAll("[^0-9]","");
        for(int i=0;i<z.length();i++)set.add(z.charAt(i)-'0');
        int[] out=new int[set.size()];int i=0;for(int d:set)out[i++]=d;
        return out;
    }
    public static Result analyze(LiuyaoEngine.Analysis base,String[] filter,String[] customPicks,String voteText){
        Result r=new Result();
        r.masks=filter.clone();
        r.customPicks=customPicks.clone();
        double[][] s=r.scores;
        StringBuilder trace=new StringBuilder();

        // Old engine: bounded by per-position max to prevent it swallowing all external votes.
        for(int p=0;p<3;p++){
            double hi=0;for(int d=0;d<10;d++)hi=Math.max(hi,base.positionScores[p][d]);
            for(int d=0;d<10;d++)s[p][d]+= hi>0?2.4*base.positionScores[p][d]/hi:0;
        }
        trace.append("① 原六爻引擎：妻财/世应/动爻/旺衰，单列评分；保留原始预测。\n");

        // HouTian: own and changed upper/lower, fixed position mapping.
        int up=tri(base.hexName,true),lo=tri(base.hexName,false);
        int cup=tri(base.changedHexName,true),clo=tri(base.changedHexName,false);
        int[] h={up,lo,cup,clo};
        add(s,0,up,1.8);add(s,1,lo,1.8);add(s,2,cup,1.3);add(s,2,clo,1.0);
        add(s,0,cup,0.6);add(s,1,clo,0.6);
        trace.append("② 后天卦数：本卦 ").append(up).append("/").append(lo)
                .append(" → 变卦 ").append(cup).append("/").append(clo).append("。\n");

        // Earlier first heaven trigram mapping, low weight; no pretending independence.
        add(s,0,xiantian(up),0.55);add(s,1,xiantian(lo),0.55);
        add(s,2,xiantian(cup),0.40);add(s,2,xiantian(clo),0.30);
        trace.append("③ 先天八卦数：低权辅助，与后天卦数分开。\n");

        // Fixed 6-line mapping (6/5->hundreds, 4/3->tens, 2/1->ones).
        for(int i=0;i<6;i++){
            LiuyaoEngine.Line line=base.lines[i];
            int p=i>=4?0:(i>=2?1:2);
            int[] e=pair(line.element);
            double w=line.moving?1.05:0.38;
            add(s,p,e[0],w*0.65);add(s,p,e[1],w*0.65);
            add(s,p,tai(line.branch),line.moving?0.95:0.18);
            if(line.moving){
                int[] changed=pair(element(line.changedBranch));
                add(s,p,changed[0],0.65);add(s,p,changed[1],0.65);
                add(s,p,tai(line.changedBranch),0.9);
            }
        }
        trace.append("④ 河图五行：水1/6、火2/7、木3/8、金4/9、土5/0；动爻比静爻权重大。\n");
        trace.append("⑤ 动变支太玄数：子午9、丑未8、寅申7、卯酉6、辰戌5、巳亥4（仅地支，不冒充完整干支数）。\n");

        // Importable outside-method *position* votes. A snapshot only affects analysis when pasted.
        String[] lines=(voteText==null?"":voteText).split("\\r?\\n");
        for(String raw:lines){
            if(raw.trim().isEmpty())continue;
            String[] segments=raw.split("\\|",-1);
            if(segments.length!=3)continue;
            String first=segments[0];
            int colon=Math.max(first.lastIndexOf(':'),first.lastIndexOf('：'));
            if(colon>=0)first=first.substring(colon+1);
            String[] positions={first,segments[1],segments[2]};
            int[][] parsed=new int[3][];
            boolean ok=true;
            for(int p=0;p<3;p++) {
                parsed[p]=parseDigits(positions[p]);
                if(parsed[p].length<1||parsed[p].length>10)ok=false;
            }
            if(!ok)continue;
            r.voteMethods++;
            for(int p=0;p<3;p++)for(int d:parsed[p])r.votes[p][d]++;
        }
        if(r.voteMethods>0){
            for(int p=0;p<3;p++)for(int d=0;d<10;d++)
                s[p][d]+=1.8*r.votes[p][d]/r.voteMethods;
        }
        trace.append("⑥ 外部九术分位投票：录入").append(r.voteMethods)
            .append("家，按各位置候选覆盖计数；同一体系不当作独立概率。\n");

        for(int p=0;p<3;p++)r.positionOrder[p]=sort(s[p]);
        double[] global=new double[10];
        for(int d=0;d<10;d++)for(int p=0;p<3;p++)
            global[d]+=s[p][d] / 3.0;
        r.singleOrder=sort(global);
        r.single=r.singleOrder[0];

        ArrayList<String[]> combos=new ArrayList<>();
        for(int a=0;a<10;a++)for(int b=0;b<10;b++)for(int c=0;c<10;c++) {
            if(!valid(a,filter[0],customPicks[0])||!valid(b,filter[1],customPicks[1])||!valid(c,filter[2],customPicks[2]))continue;
            r.validCount++;
            String num=""+a+b+c;
            double score=s[0][a]+s[1][b]+s[2][c];
            combos.add(new String[]{num,String.valueOf(score)});
        }
        combos.sort((x,y)->{
            int cmp=Double.compare(Double.parseDouble(y[1]),Double.parseDouble(x[1]));
            return cmp!=0?cmp:x[0].compareTo(y[0]);
        });
        Set<String> groups=new LinkedHashSet<>();
        for(int i=0;i<Math.min(30,combos.size());i++){
            String n=combos.get(i)[0];
            if(i<12)r.choices.add(n);
            groups.add(group(n));
        }
        r.groups.addAll(groups);
        StringBuilder sb=new StringBuilder();
        sb.append("【新版合参 v2.0】\n");
        sb.append("胆码（模型排序）：").append(r.single).append("；双胆：")
          .append(digits(r.singleOrder,2)).append("\n");
        for(int p=0;p<3;p++)sb.append(new String[]{"百位","十位","个位"}[p])
            .append(" ").append(filter[p]).append(customPicks[p].trim().isEmpty()?"":"[限定"+customPicks[p]+"]").append("：")
            .append(digits(r.positionOrder[p],4)).append("\n");
        sb.append("直选研究序列：").append(String.join("、",r.choices)).append("\n");
        sb.append("组选研究：").append(String.join("、",r.groups.subList(0,Math.min(8,r.groups.size())))).append("\n");
        sb.append("满足位置条件的组合：").append(r.validCount).append("/1000\n");
        sb.append("结构：组六/组三/豹子均参与，未用开奖后数据调整；模型分数非中奖概率。");
        r.summary=sb.toString();

        trace.append("\n【逐术位置与合参】\n");
        for(int p=0;p<3;p++){
            trace.append(new String[]{"百","十","个"}[p]).append("位 Top5 ");
            for(int k=0;k<5;k++){
                int d=r.positionOrder[p][k];
                trace.append(d).append("[")
                    .append(String.format(Locale.ROOT,"%.2f",s[p][d])).append(";")
                    .append(r.votes[p][d]).append("票] ");
            }
            trace.append("\n");
        }
        trace.append("\n复盘纪律：原始盘、条件变动、投票快照应分别冻结；事后筛选不可记为事前命中。\n");
        r.details=trace.toString();
        r.frozenKey=VERSION+"|"+base.masterSeed+"|"+base.game+"|"+Arrays.toString(filter)+"|"+Arrays.toString(customPicks)+"|"+voteText+"|"+r.summary;
        return r;
    }

    public static String demoVotes(){
        return "太玄数:4,6,7|0,2,1|5,8,1\n"+
            "六壬数目占:1,7,4|1,6,5|1,6,3\n"+
            "河洛数:3,8,1|2,7,3|6,1,9\n"+
            "焦氏易林:3,6,9|9,2,5|3,2,4\n"+
            "皇极经世:7,6,3|4,0,7|7,6,2\n"+
            "演禽取数:0,1,5|5,2,1|5,8,1\n"+
            "五兆卜:1,6,2|2,7,4|2,7,4\n"+
            "铁板条文:9,2,5|9,2,5|1,0,2\n"+
            "射覆取数:2,3,4|3,8,7|8,1,6";
    }
}
