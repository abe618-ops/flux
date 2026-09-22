package com.abe618.dayanfootball;

import java.util.*;

public final class DayanEngine {
    public enum MappingMode { STANDARD, MIRROR }

    public static final class Prediction {
        public String outcome;
        public String ranking;
        public String halfFull;
        public int goalsMain;
        public int goalsAlt;
        public String goalsRange;
        public String overUnder;
        public String oddEven;
        public String btts;
        public String scoreMain;
        public String scoreAlt1;
        public String scoreAlt2;
        public String netGoal;
        public double rawScore;
        public double goalMean;
    }

    public static final class Cast {
        public long seed;
        public int[] lines = new int[6];
        public int[][] remains = new int[6][3];
        public boolean[] moving = new boolean[6];
        public String baseHex;
        public String changedHex;
        public Trigram baseUpper;
        public Trigram baseLower;
        public Trigram changedUpper;
        public Trigram changedLower;
        public int shiPos;
        public int yingPos;
        public String shiBranch;
        public String yingBranch;
        public String shiElement;
        public String yingElement;
        public double baseRelation;
        public double changedRelation;
        public double shiYingRelation;
        public Prediction standard;
        public Prediction mirror;
        public Prediction selected;
        public String freezeCode;
    }

    public static final class Trigram {
        public final String code, name, nature, element, symbol;
        Trigram(String code, String name, String nature, String element, String symbol) {
            this.code = code; this.name = name; this.nature = nature; this.element = element; this.symbol = symbol;
        }
        @Override public String toString() { return nature + "(" + name + "·" + element + ")"; }
    }

    private static final Map<String, Trigram> TRIGRAMS = new HashMap<>();
    private static final Map<String, String> HEX = new HashMap<>();
    private static final Map<String, Integer> PALACE_SEQ = new HashMap<>();

    static {
        // 内部三爻码按“初/二/三”即自下而上。
        addTri("111","乾","天","金","☰");
        addTri("110","兑","泽","金","☱");
        addTri("101","离","火","火","☲");
        addTri("100","震","雷","木","☳");
        addTri("011","巽","风","木","☴");
        addTri("010","坎","水","水","☵");
        addTri("001","艮","山","土","☶");
        addTri("000","坤","地","土","☷");

        String hexData =
            "乾,乾,乾为天;乾,巽,天风姤;乾,艮,天山遁;乾,坤,天地否;巽,坤,风地观;艮,坤,山地剥;离,坤,火地晋;离,乾,火天大有;" +
            "坎,坎,坎为水;坎,兑,水泽节;坎,震,水雷屯;坎,离,水火既济;兑,离,泽火革;震,离,雷火丰;坤,离,地火明夷;坤,坎,地水师;" +
            "艮,艮,艮为山;艮,离,山火贲;艮,乾,山天大畜;艮,兑,山泽损;离,兑,火泽睽;乾,兑,天泽履;巽,兑,风泽中孚;巽,艮,风山渐;" +
            "震,震,震为雷;震,坤,雷地豫;震,坎,雷水解;震,巽,雷风恒;坤,巽,地风升;坎,巽,水风井;兑,巽,泽风大过;兑,震,泽雷随;" +
            "巽,巽,巽为风;巽,乾,风天小畜;巽,离,风火家人;巽,震,风雷益;乾,震,天雷无妄;离,震,火雷噬嗑;艮,震,山雷颐;艮,巽,山风蛊;" +
            "离,离,离为火;离,艮,火山旅;离,巽,火风鼎;离,坎,火水未济;艮,坎,山水蒙;巽,坎,风水涣;乾,坎,天水讼;乾,离,天火同人;" +
            "坤,坤,坤为地;坤,震,地雷复;坤,兑,地泽临;坤,乾,地天泰;震,乾,雷天大壮;兑,乾,泽天夬;坎,乾,水天需;坎,坤,水地比;" +
            "兑,兑,兑为泽;兑,坎,泽水困;兑,坤,泽地萃;兑,艮,泽山咸;坎,艮,水山蹇;坤,艮,地山谦;震,艮,雷山小过;震,兑,雷泽归妹";
        for (String row : hexData.split(";")) {
            String[] p = row.split(",");
            HEX.put(p[0] + "-" + p[1], p[2]);
        }

        addPalace("乾为天,天风姤,天山遁,天地否,风地观,山地剥,火地晋,火天大有");
        addPalace("坎为水,水泽节,水雷屯,水火既济,泽火革,雷火丰,地火明夷,地水师");
        addPalace("艮为山,山火贲,山天大畜,山泽损,火泽睽,天泽履,风泽中孚,风山渐");
        addPalace("震为雷,雷地豫,雷水解,雷风恒,地风升,水风井,泽风大过,泽雷随");
        addPalace("巽为风,风天小畜,风火家人,风雷益,天雷无妄,火雷噬嗑,山雷颐,山风蛊");
        addPalace("离为火,火山旅,火风鼎,火水未济,山水蒙,风水涣,天水讼,天火同人");
        addPalace("坤为地,地雷复,地泽临,地天泰,雷天大壮,泽天夬,水天需,水地比");
        addPalace("兑为泽,泽水困,泽地萃,泽山咸,水山蹇,地山谦,雷山小过,雷泽归妹");
    }

    private static void addTri(String c,String n,String nature,String e,String s){
        TRIGRAMS.put(c,new Trigram(c,n,nature,e,s));
    }

    private static void addPalace(String csv) {
        String[] a = csv.split(",");
        for (int i=0;i<a.length;i++) PALACE_SEQ.put(a[i], i);
    }

    public static Cast cast(long seed, MappingMode mode) {
        Random r = new Random(seed);
        Cast c = new Cast();
        c.seed = seed;

        for (int i=0;i<6;i++) {
            int total = 49;
            for (int t=0;t<3;t++) {
                int left = 1 + r.nextInt(total - 1);
                int right = total - left;
                right -= 1; // 挂一
                int remL = remainderFour(left);
                int remR = remainderFour(right);
                total -= (1 + remL + remR);
                c.remains[i][t] = total;
            }
            int line = total / 4;
            if (line < 6 || line > 9) throw new IllegalStateException("非法爻值 " + line);
            c.lines[i] = line;
            c.moving[i] = line == 6 || line == 9;
        }

        int[] baseBits = new int[6], changedBits = new int[6];
        for (int i=0;i<6;i++) {
            int v=c.lines[i];
            baseBits[i] = (v==7 || v==9) ? 1 : 0;
            changedBits[i] = v==6 ? 1 : (v==9 ? 0 : baseBits[i]);
        }

        c.baseLower = tri(baseBits,0);
        c.baseUpper = tri(baseBits,3);
        c.changedLower = tri(changedBits,0);
        c.changedUpper = tri(changedBits,3);
        c.baseHex = hex(c.baseUpper,c.baseLower);
        c.changedHex = hex(c.changedUpper,c.changedLower);

        int seq = PALACE_SEQ.getOrDefault(c.baseHex, 0);
        int[] shiBySeq = {6,1,2,3,4,5,4,3};
        c.shiPos = shiBySeq[seq];
        c.yingPos = ((c.shiPos + 2) % 6) + 1;
        c.shiBranch = branchAt(c.baseLower,c.baseUpper,c.shiPos);
        c.yingBranch = branchAt(c.baseLower,c.baseUpper,c.yingPos);
        c.shiElement = branchElement(c.shiBranch);
        c.yingElement = branchElement(c.yingBranch);

        c.baseRelation = relationScore(c.baseUpper.element,c.baseLower.element);
        c.changedRelation = relationScore(c.changedUpper.element,c.changedLower.element);
        c.shiYingRelation = relationScore(c.shiElement,c.yingElement);

        double score = c.baseRelation * 1.15 + c.changedRelation * 1.55 + c.shiYingRelation * 1.35;
        score += (c.changedRelation - c.baseRelation) * 0.40;

        if (c.moving[c.shiPos-1]) score += Math.signum(c.changedRelation) * 0.35;
        if (c.moving[c.yingPos-1]) score -= Math.signum(-c.changedRelation) * 0.25;

        c.standard = predict(c, score);
        c.mirror = predict(c, -score);
        c.selected = mode == MappingMode.MIRROR ? c.mirror : c.standard;
        c.freezeCode = "DY-" + lineString(c.lines) + "-" + c.baseHex + (c.baseHex.equals(c.changedHex) ? "" : "→" + c.changedHex);
        return c;
    }

    private static int remainderFour(int n) {
        if (n == 0) return 0;
        int r=n%4;
        return r==0 ? 4 : r;
    }

    private static Trigram tri(int[] bits,int start) {
        String code = "" + bits[start] + bits[start+1] + bits[start+2];
        Trigram t=TRIGRAMS.get(code);
        if (t==null) throw new IllegalStateException("未知三爻码 "+code);
        return t;
    }

    private static String hex(Trigram upper,Trigram lower) {
        String n=HEX.get(upper.name+"-"+lower.name);
        return n==null ? upper.nature+lower.nature : n;
    }

    private static String lineString(int[] a) {
        StringBuilder sb=new StringBuilder();
        for (int v:a) sb.append(v);
        return sb.toString();
    }

    private static boolean generates(String a,String b) {
        return (a.equals("木")&&b.equals("火")) || (a.equals("火")&&b.equals("土")) ||
               (a.equals("土")&&b.equals("金")) || (a.equals("金")&&b.equals("水")) ||
               (a.equals("水")&&b.equals("木"));
    }

    private static boolean controls(String a,String b) {
        return (a.equals("木")&&b.equals("土")) || (a.equals("土")&&b.equals("水")) ||
               (a.equals("水")&&b.equals("火")) || (a.equals("火")&&b.equals("金")) ||
               (a.equals("金")&&b.equals("木"));
    }

    // 正值=主方/世方有利；负值=客方/应方有利。
    private static double relationScore(String host,String away) {
        if (host.equals(away)) return 0.0;
        if (controls(host,away)) return 2.0;
        if (controls(away,host)) return -2.0;
        if (generates(away,host)) return 1.20;
        if (generates(host,away)) return -1.20;
        return 0.0;
    }

    private static String branchAt(Trigram lower,Trigram upper,int pos) {
        String[] arr;
        if (pos<=3) arr=innerBranches(lower.name);
        else arr=outerBranches(upper.name);
        return arr[(pos-1)%3];
    }

    private static String[] innerBranches(String tri) {
        switch(tri) {
            case "乾": case "震": return new String[]{"子","寅","辰"};
            case "兑": return new String[]{"巳","卯","丑"};
            case "离": return new String[]{"卯","丑","亥"};
            case "巽": return new String[]{"丑","亥","酉"};
            case "坎": return new String[]{"寅","辰","午"};
            case "艮": return new String[]{"辰","午","申"};
            case "坤": return new String[]{"未","巳","卯"};
        }
        return new String[]{"子","寅","辰"};
    }

    private static String[] outerBranches(String tri) {
        switch(tri) {
            case "乾": case "震": return new String[]{"午","申","戌"};
            case "兑": return new String[]{"亥","酉","未"};
            case "离": return new String[]{"酉","未","巳"};
            case "巽": return new String[]{"未","巳","卯"};
            case "坎": return new String[]{"申","戌","子"};
            case "艮": return new String[]{"戌","子","寅"};
            case "坤": return new String[]{"丑","亥","酉"};
        }
        return new String[]{"午","申","戌"};
    }

    private static String branchElement(String b) {
        if ("子亥".contains(b)) return "水";
        if ("寅卯".contains(b)) return "木";
        if ("巳午".contains(b)) return "火";
        if ("申酉".contains(b)) return "金";
        return "土";
    }

    private static Prediction predict(Cast c,double score) {
        Prediction p=new Prediction();
        p.rawScore=score;
        int moving=0, oldYang=0, early=0;
        for (int i=0;i<6;i++) {
            if (c.moving[i]) { moving++; if (i<3) early++; }
            if (c.lines[i]==9) oldYang++;
        }

        String outcome = score>1.20 ? "主胜" : (score<-1.20 ? "客胜" : "平");
        p.outcome=outcome;
        if ("主胜".equals(outcome)) p.ranking="主胜 ＞ 平 ＞ 客胜";
        else if ("客胜".equals(outcome)) p.ranking="客胜 ＞ 平 ＞ 主胜";
        else p.ranking = score>=0 ? "平 ＞ 主胜 ＞ 客胜" : "平 ＞ 客胜 ＞ 主胜";

        double conflict = Math.abs(c.baseRelation) + Math.abs(c.changedRelation) + Math.min(2.4,Math.abs(c.shiYingRelation));
        boolean flip = c.baseRelation * c.changedRelation < 0;
        double mean = 2.05 + moving*0.18 + conflict*0.22 + (flip?0.45:0.0) + oldYang*0.08;
        if (moving==0 && conflict>=3.0) mean += 0.30;
        mean=Math.max(1.2,Math.min(4.8,mean));
        p.goalMean=mean;
        p.goalsMain=Math.max(1,Math.min(5,(int)Math.round(mean)));
        int alt = mean>=p.goalsMain ? p.goalsMain+1 : p.goalsMain-1;
        if (alt<1) alt=2; if (alt>5) alt=4;
        p.goalsAlt=alt;
        int lo=Math.max(0,Math.min(p.goalsMain,p.goalsAlt)-1);
        int hi=Math.min(6,Math.max(p.goalsMain,p.goalsAlt));
        p.goalsRange=lo+"～"+hi+"球";
        p.overUnder=mean>=2.55 ? "大2.5" : "小2.5";
        p.oddEven=(p.goalsMain%2==0) ? "双" : "单";

        String finalSide = "平".equals(outcome) ? "平" : ("主胜".equals(outcome)?"主":"客");
        if ("平".equals(outcome)) {
            p.halfFull="平/平";
        } else {
            boolean baseAgainstFinal = ("主胜".equals(outcome) && c.baseRelation< -1.0) ||
                                       ("客胜".equals(outcome) && c.baseRelation> 1.0);
            boolean changedSupportsFinal = ("主胜".equals(outcome) && c.changedRelation>0) ||
                                           ("客胜".equals(outcome) && c.changedRelation<0);
            if (baseAgainstFinal && changedSupportsFinal && moving>0) {
                p.halfFull=("主胜".equals(outcome)?"客":"主")+"/"+finalSide;
            } else if (early>=2 && Math.abs(score)>=2.8) {
                p.halfFull=finalSide+"/"+finalSide;
            } else {
                p.halfFull="平/"+finalSide;
            }
        }

        String[] scores=scoreCandidates(outcome,p.goalsMain,Math.abs(score));
        p.scoreMain=scores[0]; p.scoreAlt1=scores[1]; p.scoreAlt2=scores[2];

        int margin = "平".equals(outcome) ? 0 : (Math.abs(score)>=4.0 ? 2 : 1);
        p.netGoal = margin==0 ? "净胜0球" : (("主胜".equals(outcome)?"主":"客")+"净胜"+margin+"球");

        if ("平".equals(outcome)) p.btts = p.goalsMain>=2 ? "是" : "否";
        else p.btts = (p.goalsMain>=3 && Math.abs(score)<4.4) ? "是" : "否";
        return p;
    }

    private static String[] scoreCandidates(String outcome,int goals,double strength) {
        if ("平".equals(outcome)) {
            if (goals<=1) return new String[]{"0:0","1:1","1:0"};
            if (goals<=3) return new String[]{"1:1","0:0","2:2"};
            return new String[]{"2:2","1:1","3:3"};
        }
        boolean home="主胜".equals(outcome);
        String[] base;
        switch(goals) {
            case 1: base=new String[]{"1:0","2:0","2:1"}; break;
            case 2: base=new String[]{"2:0","1:0","2:1"}; break;
            case 3:
                base = strength>=4.0 ? new String[]{"3:0","2:1","3:1"} : new String[]{"2:1","3:0","1:0"};
                break;
            case 4:
                base = strength>=4.0 ? new String[]{"3:1","4:0","2:1"} : new String[]{"3:1","2:1","2:0"};
                break;
            default:
                base=new String[]{"3:2","4:1","3:1"};
        }
        if (home) return base;
        String[] out=new String[3];
        for (int i=0;i<3;i++) {
            String[] q=base[i].split(":");
            out[i]=q[1]+":"+q[0];
        }
        return out;
    }

    public static String describe(Cast c, boolean selectedMirror) {
        Prediction p = selectedMirror ? c.mirror : c.standard;
        StringBuilder sb=new StringBuilder();
        sb.append("随机种子：").append(c.seed).append("\n");
        sb.append("六爻（初→上）：").append(lineString(c.lines)).append("\n");
        for (int i=0;i<6;i++) {
            sb.append("第").append(i+1).append("爻：")
              .append(c.remains[i][0]).append("→")
              .append(c.remains[i][1]).append("→")
              .append(c.remains[i][2]).append(" = ").append(c.lines[i]);
            if (c.moving[i]) sb.append("（动）");
            sb.append("\n");
        }
        sb.append("\n本卦：").append(c.baseHex).append("  ")
          .append(c.baseUpper).append(" / ").append(c.baseLower).append("\n");
        sb.append("变卦：").append(c.changedHex).append("  ")
          .append(c.changedUpper).append(" / ").append(c.changedLower).append("\n");
        sb.append("世爻：").append(c.shiPos).append("（").append(c.shiBranch).append("·").append(c.shiElement).append("）  ")
          .append("应爻：").append(c.yingPos).append("（").append(c.yingBranch).append("·").append(c.yingElement).append("）\n");
        sb.append("生克分：本卦 ").append(fmt(c.baseRelation))
          .append("｜变卦 ").append(fmt(c.changedRelation))
          .append("｜世应 ").append(fmt(c.shiYingRelation)).append("\n\n");

        sb.append("【综合预测】\n");
        sb.append("胜平负：").append(p.ranking).append("\n");
        sb.append("半全场：").append(p.halfFull).append("\n");
        sb.append("总进球：").append(p.goalsMain).append("球；次选 ").append(p.goalsAlt).append("球；范围 ").append(p.goalsRange).append("\n");
        sb.append("大小：").append(p.overUnder).append("\n");
        sb.append("单双：").append(p.oddEven).append("\n");
        sb.append("双方进球：").append(p.btts).append("\n");
        sb.append("比分：").append(p.scoreMain).append("；次 ").append(p.scoreAlt1).append("、").append(p.scoreAlt2).append("\n");
        sb.append("净胜球：").append(p.netGoal).append("\n");
        sb.append("模型分：").append(fmt(p.rawScore)).append("｜进球强度：").append(fmt(p.goalMean)).append("\n");
        sb.append("\n冻结码：").append(c.freezeCode);
        return sb.toString();
    }

    private static String fmt(double d){ return String.format(Locale.US,"%.2f",d); }
}
