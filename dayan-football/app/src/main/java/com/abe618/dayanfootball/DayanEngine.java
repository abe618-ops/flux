package com.abe618.dayanfootball;

import java.util.*;

public final class DayanEngine {
    public enum MappingMode { STANDARD, MIRROR }

    public static final class Prediction {
        public String outcome;
        public String ranking;
        public String halfFull;
        public String halfFullAlt;
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
        public String rhythm;
        public String risk;
        public String pattern;
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
            this.code=code; this.name=name; this.nature=nature; this.element=element; this.symbol=symbol;
        }
        @Override public String toString() { return nature+"("+name+"·"+element+")"; }
    }

    private static final Map<String,Trigram> TRIGRAMS=new HashMap<>();
    private static final Map<String,String> HEX=new HashMap<>();
    private static final Map<String,Integer> PALACE_SEQ=new HashMap<>();

    static {
        addTri("111","乾","天","金","☰"); addTri("110","兑","泽","金","☱");
        addTri("101","离","火","火","☲"); addTri("100","震","雷","木","☳");
        addTri("011","巽","风","木","☴"); addTri("010","坎","水","水","☵");
        addTri("001","艮","山","土","☶"); addTri("000","坤","地","土","☷");

        String data=
            "乾,乾,乾为天;乾,巽,天风姤;乾,艮,天山遁;乾,坤,天地否;巽,坤,风地观;艮,坤,山地剥;离,坤,火地晋;离,乾,火天大有;"+
            "坎,坎,坎为水;坎,兑,水泽节;坎,震,水雷屯;坎,离,水火既济;兑,离,泽火革;震,离,雷火丰;坤,离,地火明夷;坤,坎,地水师;"+
            "艮,艮,艮为山;艮,离,山火贲;艮,乾,山天大畜;艮,兑,山泽损;离,兑,火泽睽;乾,兑,天泽履;巽,兑,风泽中孚;巽,艮,风山渐;"+
            "震,震,震为雷;震,坤,雷地豫;震,坎,雷水解;震,巽,雷风恒;坤,巽,地风升;坎,巽,水风井;兑,巽,泽风大过;兑,震,泽雷随;"+
            "巽,巽,巽为风;巽,乾,风天小畜;巽,离,风火家人;巽,震,风雷益;乾,震,天雷无妄;离,震,火雷噬嗑;艮,震,山雷颐;艮,巽,山风蛊;"+
            "离,离,离为火;离,艮,火山旅;离,巽,火风鼎;离,坎,火水未济;艮,坎,山水蒙;巽,坎,风水涣;乾,坎,天水讼;乾,离,天火同人;"+
            "坤,坤,坤为地;坤,震,地雷复;坤,兑,地泽临;坤,乾,地天泰;震,乾,雷天大壮;兑,乾,泽天夬;坎,乾,水天需;坎,坤,水地比;"+
            "兑,兑,兑为泽;兑,坎,泽水困;兑,坤,泽地萃;兑,艮,泽山咸;坎,艮,水山蹇;坤,艮,地山谦;震,艮,雷山小过;震,兑,雷泽归妹";
        for(String row:data.split(";")){
            String[] p=row.split(",");
            HEX.put(p[0]+"-"+p[1],p[2]);
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

    private static void addTri(String c,String n,String nature,String e,String s){TRIGRAMS.put(c,new Trigram(c,n,nature,e,s));}
    private static void addPalace(String csv){String[] a=csv.split(",");for(int i=0;i<a.length;i++)PALACE_SEQ.put(a[i],i);}

    public static Cast cast(long seed, MappingMode mode) {
        Random r=new Random(seed);
        int[] lines=new int[6];
        int[][] remains=new int[6][3];
        for(int i=0;i<6;i++){
            int total=49;
            for(int t=0;t<3;t++){
                int left=1+r.nextInt(total-1);
                int right=total-left;
                right-=1;
                int remL=remainderFour(left), remR=remainderFour(right);
                total-=(1+remL+remR);
                remains[i][t]=total;
            }
            lines[i]=total/4;
        }
        Cast c=analyzeLines(lines);
        c.seed=seed;
        c.remains=remains;
        c.selected=mode==MappingMode.MIRROR?c.mirror:c.standard;
        return c;
    }

    public static Cast analyzeLines(int[] input) {
        if(input==null||input.length!=6) throw new IllegalArgumentException("必须是6个爻值");
        Cast c=new Cast();
        c.lines=input.clone();
        for(int i=0;i<6;i++){
            int v=c.lines[i];
            if(v<6||v>9) throw new IllegalArgumentException("非法爻值 "+v);
            c.moving[i]=(v==6||v==9);
        }

        int[] baseBits=new int[6], changedBits=new int[6];
        for(int i=0;i<6;i++){
            int v=c.lines[i];
            baseBits[i]=(v==7||v==9)?1:0;
            changedBits[i]=(v==6)?1:(v==9?0:baseBits[i]);
        }
        c.baseLower=tri(baseBits,0); c.baseUpper=tri(baseBits,3);
        c.changedLower=tri(changedBits,0); c.changedUpper=tri(changedBits,3);
        c.baseHex=hex(c.baseUpper,c.baseLower);
        c.changedHex=hex(c.changedUpper,c.changedLower);

        int seq=PALACE_SEQ.getOrDefault(c.baseHex,0);
        int[] shiBySeq={6,1,2,3,4,5,4,3};
        c.shiPos=shiBySeq[seq];
        c.yingPos=((c.shiPos+2)%6)+1;
        c.shiBranch=branchAt(c.baseLower,c.baseUpper,c.shiPos);
        c.yingBranch=branchAt(c.baseLower,c.baseUpper,c.yingPos);
        c.shiElement=branchElement(c.shiBranch);
        c.yingElement=branchElement(c.yingBranch);

        c.baseRelation=relationScore(c.baseUpper.element,c.baseLower.element);
        c.changedRelation=relationScore(c.changedUpper.element,c.changedLower.element);
        c.shiYingRelation=relationScore(c.shiElement,c.yingElement);

        c.standard=predict(c,false);
        c.mirror=mirror(c.standard);
        c.selected=c.standard;
        c.freezeCode=lineString(c.lines)+"｜"+c.baseHex+(c.baseHex.equals(c.changedHex)?"":"→"+c.changedHex)+"｜动"+movingPositions(c);
        return c;
    }

    private static Prediction predict(Cast c, boolean ignored) {
        Prediction p=new Prediction();
        int moving=0, lowMoves=0, highMoves=0;
        for(int i=0;i<6;i++) if(c.moving[i]){moving++; if(i<3)lowMoves++; else highMoves++;}

        boolean flip=c.baseRelation*c.changedRelation<0;
        boolean sameDir=Math.signum(c.baseRelation)!=0 && Math.signum(c.baseRelation)==Math.signum(c.changedRelation);

        double score;
        if(moving==0){
            if(Math.abs(c.baseRelation)>=1.8) score=c.baseRelation+0.35*c.shiYingRelation;
            else if(Math.abs(c.baseRelation)>=1.0) score=0.80*c.baseRelation+0.50*c.shiYingRelation;
            else score=0.90*c.shiYingRelation;
        } else {
            score=0.65*c.baseRelation+1.35*c.changedRelation+1.10*c.shiYingRelation;
            if(sameDir) score+=0.35*Math.signum(c.changedRelation);
        }
        p.rawScore=score;

        if(score>0.90){p.outcome="主胜";p.ranking="主胜 ＞ 平 ＞ 客胜";}
        else if(score<-0.90){p.outcome="客胜";p.ranking="客胜 ＞ 平 ＞ 主胜";}
        else {p.outcome="平";p.ranking=score>=0?"平 ＞ 主胜 ＞ 客胜":"平 ＞ 客胜 ＞ 主胜";}

        if(moving==0){
            if(Math.abs(c.baseRelation)>=1.8) p.goalsMain=3;
            else if(Math.abs(c.baseRelation)>=1.0) p.goalsMain=2;
            else p.goalsMain=1;
            p.pattern="0动稳定盘";
        } else if(moving==1){
            int pos=firstMoving(c)+1;
            p.goalsMain=(flip && pos>=4)?3:2;
            p.pattern="单动转折盘";
        } else if(moving==2){
            if(flip) p.goalsMain=(highMoves==2&&lowMoves==0)?4:3;
            else if(sameDir && Math.abs(c.baseRelation)<=1.25 && Math.abs(c.changedRelation)<=1.25) p.goalsMain=3;
            else p.goalsMain=2;
            p.pattern=flip?"双动反转盘":"双动延续盘";
        } else if(moving==3){
            p.goalsMain=3; p.pattern="三动中高波动盘";
        } else {
            p.goalsMain=4; p.pattern="多动高波动盘";
        }

        p.goalsAlt=p.goalsMain>=3?p.goalsMain-1:p.goalsMain+1;
        int lo=Math.max(0,Math.min(p.goalsMain,p.goalsAlt)-1);
        int hi=Math.min(6,Math.max(p.goalsMain,p.goalsAlt));
        p.goalsRange=lo+"～"+hi+"球";
        p.goalMean=p.goalsMain;

        if(p.goalsMain>=3){
            if(moving==0 || (moving==2&&flip)) p.overUnder="大2.5微优";
            else p.overUnder="大2.5";
        } else {
            if(moving==1 && sameDir && Math.abs(score)>=1.4) p.overUnder="小2.5";
            else p.overUnder="小2.5微优";
        }

        String parity=(p.goalsMain%2==0)?"双":"单";
        if(p.goalsMain==4 || (moving==2&&!flip&&p.goalsMain==3)) p.oddEven=parity;
        else p.oddEven=parity+"微优";

        if(p.goalsMain>=4) p.btts="是微优";
        else if(p.goalsMain==3) p.btts=(moving==0)?"否微优":"是微优";
        else if(moving==1 && flip && "客胜".equals(p.outcome)) p.btts="是微优";
        else p.btts="否微优";

        p.halfFull=halfFull(c,p,moving,flip);
        p.halfFullAlt=halfFullAlt(p);

        String[] scores=scoreCandidates(c,p,score,moving,flip);
        p.scoreMain=scores[0];p.scoreAlt1=scores[1];p.scoreAlt2=scores[2];

        if("平".equals(p.outcome)) p.netGoal="净胜0球";
        else if(Math.abs(score)>=4.5 && p.goalsMain>=3) p.netGoal=("主胜".equals(p.outcome)?"主":"客")+"净胜2球";
        else p.netGoal=("主胜".equals(p.outcome)?"主":"客")+"净胜1球";

        if(moving==0){
            p.rhythm="整体稳定，变化较少";
            p.risk="0动只用于判断低变化，不自动强化某一方；方向由本卦与世应共同决定。";
        } else if(lowMoves>0&&highMoves>0){
            p.rhythm="前后段均有动爻，中段附近容易出现转向";
            p.risk=flip?"本卦与变卦方向反转，重点防中后程翻转。":"前后均有变化，但主方向延续。";
        } else if(highMoves>0){
            p.rhythm="前段相对稳定，后程变化更明显";
            p.risk=flip?"高位动爻叠加方向反转，后程翻转权重提高。":"高位动爻主要影响后程。";
        } else {
            p.rhythm="变化主要集中在前段";
            p.risk=flip?"前段出现方向切换信号。":"前段有变化，后程以变卦方向为主。";
        }

        return p;
    }

    private static String halfFull(Cast c,Prediction p,int moving,boolean flip){
        if("平".equals(p.outcome)) return "平 / 平";
        String f="主胜".equals(p.outcome)?"主":"客";
        if(moving==0){
            if(Math.abs(p.rawScore)>=1.8 && Math.abs(c.baseRelation)>=1.8) return f+" / "+f;
            return "平 / "+f;
        }
        if(flip){
            if("客".equals(f)&&c.baseRelation>=1.8) return "主 / 客";
            if("主".equals(f)&&c.baseRelation<=-1.8) return "客 / 主";
            return "平 / "+f;
        }
        return "平 / "+f;
    }

    private static String halfFullAlt(Prediction p){
        if("主胜".equals(p.outcome)) return "平 / 平";
        if("客胜".equals(p.outcome)) return "平 / 平";
        return p.rawScore>=0?"平 / 主":"平 / 客";
    }

    private static String[] scoreCandidates(Cast c,Prediction p,double score,int moving,boolean flip){
        boolean home="主胜".equals(p.outcome), away="客胜".equals(p.outcome);
        if(!home&&!away){
            if(p.goalsMain<=1)return new String[]{"0:0","1:1","1:0"};
            if(p.goalsMain==2)return new String[]{"1:1","0:0","2:2"};
            return new String[]{"2:2","1:1","3:3"};
        }
        boolean yes=p.btts.startsWith("是");
        if(home){
            if(p.goalsMain==1)return new String[]{"1:0","1:1","0:0"};
            if(p.goalsMain==2)return yes?new String[]{"2:1","1:0","1:1"}:new String[]{"2:0","1:0","1:1"};
            if(p.goalsMain==3)return yes?new String[]{"2:1","3:0","1:1"}:new String[]{"3:0","2:0","2:1"};
            return yes?new String[]{"3:1","4:0","2:1"}:new String[]{"4:0","3:0","2:0"};
        } else {
            if(p.goalsMain==1)return new String[]{"0:1","1:1","0:0"};
            if(p.goalsMain==2){
                if(yes)return new String[]{"1:2","0:1","1:1"};
                if(Math.abs(score)>=3.0)return new String[]{"0:2","1:2","1:1"};
                return new String[]{"0:1","1:1","0:2"};
            }
            if(p.goalsMain==3)return new String[]{"1:2","1:3","2:2"};
            return new String[]{"1:3","0:3","1:2"};
        }
    }

    private static Prediction mirror(Prediction s){
        Prediction p=new Prediction();
        p.outcome=swapOutcome(s.outcome);
        p.ranking=swapText(s.ranking);
        p.halfFull=swapText(s.halfFull);
        p.halfFullAlt=swapText(s.halfFullAlt);
        p.goalsMain=s.goalsMain;p.goalsAlt=s.goalsAlt;p.goalsRange=s.goalsRange;
        p.overUnder=s.overUnder;p.oddEven=s.oddEven;p.btts=s.btts;
        p.scoreMain=swapScore(s.scoreMain);p.scoreAlt1=swapScore(s.scoreAlt1);p.scoreAlt2=swapScore(s.scoreAlt2);
        p.netGoal=swapText(s.netGoal);p.rhythm=s.rhythm;p.risk=s.risk;p.pattern=s.pattern;
        p.rawScore=-s.rawScore;p.goalMean=s.goalMean;
        return p;
    }

    private static int firstMoving(Cast c){for(int i=0;i<6;i++)if(c.moving[i])return i;return -1;}
    private static int remainderFour(int n){int r=n%4;return r==0?4:r;}
    private static Trigram tri(int[] bits,int start){String code=""+bits[start]+bits[start+1]+bits[start+2];return TRIGRAMS.get(code);}
    private static String hex(Trigram upper,Trigram lower){return HEX.get(upper.name+"-"+lower.name);}
    private static boolean generates(String a,String b){return(a.equals("木")&&b.equals("火"))||(a.equals("火")&&b.equals("土"))||(a.equals("土")&&b.equals("金"))||(a.equals("金")&&b.equals("水"))||(a.equals("水")&&b.equals("木"));}
    private static boolean controls(String a,String b){return(a.equals("木")&&b.equals("土"))||(a.equals("土")&&b.equals("水"))||(a.equals("水")&&b.equals("火"))||(a.equals("火")&&b.equals("金"))||(a.equals("金")&&b.equals("木"));}
    private static double relationScore(String host,String away){if(host.equals(away))return 0;if(controls(host,away))return 2;if(controls(away,host))return-2;if(generates(away,host))return 1.2;if(generates(host,away))return-1.2;return 0;}

    private static String branchAt(Trigram lower,Trigram upper,int pos){
        String[] a=pos<=3?innerBranches(lower.name):outerBranches(upper.name);
        return a[(pos-1)%3];
    }
    private static String[] innerBranches(String t){
        switch(t){
            case"乾":case"震":return new String[]{"子","寅","辰"};
            case"兑":return new String[]{"巳","卯","丑"};
            case"离":return new String[]{"卯","丑","亥"};
            case"巽":return new String[]{"丑","亥","酉"};
            case"坎":return new String[]{"寅","辰","午"};
            case"艮":return new String[]{"辰","午","申"};
            case"坤":return new String[]{"未","巳","卯"};
        }return new String[]{"子","寅","辰"};
    }
    private static String[] outerBranches(String t){
        switch(t){
            case"乾":case"震":return new String[]{"午","申","戌"};
            case"兑":return new String[]{"亥","酉","未"};
            case"离":return new String[]{"酉","未","巳"};
            case"巽":return new String[]{"未","巳","卯"};
            case"坎":return new String[]{"申","戌","子"};
            case"艮":return new String[]{"戌","子","寅"};
            case"坤":return new String[]{"丑","亥","酉"};
        }return new String[]{"午","申","戌"};
    }
    private static String branchElement(String b){if("子亥".contains(b))return"水";if("寅卯".contains(b))return"木";if("巳午".contains(b))return"火";if("申酉".contains(b))return"金";return"土";}

    public static String movingPositions(Cast c){
        StringBuilder s=new StringBuilder();
        for(int i=0;i<6;i++)if(c.moving[i]){if(s.length()>0)s.append("/");s.append(i+1);}
        return s.length()==0?"0":s.toString();
    }
    public static String lineString(int[] a){StringBuilder s=new StringBuilder();for(int v:a)s.append(v);return s.toString();}
    private static String swapOutcome(String s){if("主胜".equals(s))return"客胜";if("客胜".equals(s))return"主胜";return s;}
    private static String swapText(String s){return s.replace("主","§").replace("客","主").replace("§","客");}
    private static String swapScore(String s){String[] q=s.split(":");return q.length==2?q[1]+":"+q[0]:s;}
}
