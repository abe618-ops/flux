package com.abe618.lotteryshushu;

import com.abe618.fourshushurandom.ChartEngine;
import com.abe618.sanshiyanqin.TraditionalEngine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class EightShushuEngine {
    private EightShushuEngine() {}

    public enum Dimension { TIME, RANDOM, LIVE }

    public static final class RunResult {
        public final String lottery;
        public final Dimension dimension;
        public final LocalDateTime effectiveTime;
        public final long seed;
        public final List<SystemPick> systems;
        public final double[][] combined;
        public final String text;

        RunResult(String lottery, Dimension dimension, LocalDateTime effectiveTime, long seed,
                  List<SystemPick> systems, double[][] combined, String text) {
            this.lottery = lottery;
            this.dimension = dimension;
            this.effectiveTime = effectiveTime;
            this.seed = seed;
            this.systems = systems;
            this.combined = combined;
            this.text = text;
        }
    }

    public static final class SystemPick {
        public final String name;
        public final String raw;
        public final double[][] scores;

        SystemPick(String name, String raw, double[][] scores) {
            this.name = name;
            this.raw = raw;
            this.scores = scores;
        }
    }

    private static final String[] SYSTEM_NAMES = {
            "奇门遁甲", "太乙神数", "大六壬", "六爻纳甲",
            "梅花易数", "河洛数", "九爻易", "灵棋经"
    };

    private static final String[] GUA = {"坤","艮","坎","巽","震","离","兑","乾"};

    public static RunResult run(String lottery, Dimension dimension, LocalDate date, String seedText) {
        LocalTime draw = drawTime(lottery);
        long seed;
        LocalDateTime effective;

        if (dimension == Dimension.TIME) {
            effective = LocalDateTime.of(date, draw);
            seed = hash64(lottery + "|" + date + "|" + draw + "|TIME|v2.0");
        } else if (dimension == Dimension.RANDOM) {
            seed = seedText == null || seedText.trim().isEmpty()
                    ? new SecureRandom().nextLong()
                    : hash64("RANDOM|" + seedText.trim());
            effective = LocalDateTime.of(date, draw);
        } else {
            long liveSeed = seedText == null || seedText.trim().isEmpty()
                    ? new SecureRandom().nextLong()
                    : hash64("LIVE|" + seedText.trim());
            int end = draw.getHour() * 60 + draw.getMinute();
            int minute = 1 + bounded(liveSeed, "live-minute", Math.max(1, end - 1));
            effective = LocalDateTime.of(date, LocalTime.of(minute / 60, minute % 60));
            seed = hash64(lottery + "|" + effective + "|LIVE|" + Long.toUnsignedString(liveSeed));
        }

        int positions = "快乐8".equals(lottery) ? 1 : ("排列5".equals(lottery) ? 5 : 3);
        int universe = "快乐8".equals(lottery) ? 80 : 10;

        TraditionalEngine.CalendarInfo c = TraditionalEngine.CalendarInfo.of(
                effective.getYear(), effective.getMonthValue(), effective.getDayOfMonth(),
                effective.getHour(), effective.getMinute());

        List<SystemPick> systems = new ArrayList<>();

        TraditionalEngine.QimenResult qm = TraditionalEngine.qimen(c, "时家");
        StringBuilder qraw = new StringBuilder();
        qraw.append(qm.mode).append(" ").append(qm.dun).append(qm.bureau).append("局 ")
                .append(qm.activeGanZhi).append(" 值符").append(qm.zhiFu).append(" 值使").append(qm.zhiShi);
        for (int i=1;i<qm.palaces.length;i++) if (qm.palaces[i] != null) {
            qraw.append("|").append(i).append(":").append(qm.palaces[i].earthStem)
                    .append(qm.palaces[i].heavenStem).append(qm.palaces[i].star)
                    .append(qm.palaces[i].door).append(qm.palaces[i].god);
        }
        systems.add(makePick(SYSTEM_NAMES[0], qraw.toString(), seed, positions, universe));

        TraditionalEngine.TaiyiResult ty = TraditionalEngine.taiyi(c, "时计");
        String tyRaw = ty.mode+" "+ty.yinYang+"第"+ty.bureau+"局 主算"+ty.lord+" 客算"+ty.guest+
                " 定算"+ty.fixed+" 太乙"+ty.taiyi+" 文昌"+ty.wenchang+" 始击"+ty.shiji+" 计神"+ty.jishen;
        systems.add(makePick(SYSTEM_NAMES[1], tyRaw, seed, positions, universe));

        TraditionalEngine.LiuRenResult lr = TraditionalEngine.liuren(c);
        String lrRaw = lr.text(c);
        systems.add(makePick(SYSTEM_NAMES[2], compact(lrRaw), seed, positions, universe));

        String lyRaw = liuyaoRaw(c, seed);
        systems.add(makePick(SYSTEM_NAMES[3], lyRaw, seed, positions, universe));

        String mhRaw = meihuaRaw(c);
        systems.add(makePick(SYSTEM_NAMES[4], mhRaw, seed, positions, universe));

        String hlRaw = heluoRaw(c, seed);
        systems.add(makePick(SYSTEM_NAMES[5], hlRaw, seed, positions, universe));

        ChartEngine.State cs = ChartEngine.cast(hash64(Long.toUnsignedString(seed)+"|chart"));
        systems.add(makePick(SYSTEM_NAMES[6], compact(cs.nineYao.text), seed, positions, universe));
        systems.add(makePick(SYSTEM_NAMES[7], compact(cs.lingQi.text), seed, positions, universe));

        double[][] combined = combine(systems, positions, universe);
        String text = render(lottery, dimension, effective, seed, systems, combined);
        return new RunResult(lottery, dimension, effective, seed, systems, combined, text);
    }

    public static String combineThree(RunResult a, RunResult b, RunResult c) {
        if (a == null || b == null || c == null) return "请先生成时间盘、随机数盘、当日活时盘。";
        if (!a.lottery.equals(b.lottery) || !a.lottery.equals(c.lottery)) return "三盘彩种不一致，请重新生成。";

        int positions = a.combined.length;
        int universe = a.combined[0].length;
        double[][] m = new double[positions][universe];
        for (int p=0;p<positions;p++) for (int n=0;n<universe;n++) {
            m[p][n] = (a.combined[p][n] + b.combined[p][n] + c.combined[p][n]) / 3.0;
        }

        StringBuilder out = new StringBuilder();
        out.append("【三维合参 · 八术等权】\n")
                .append(a.lottery).append("\n")
                .append("时间盘：").append(a.effectiveTime).append("\n")
                .append("随机盘 Seed：").append(Long.toUnsignedString(b.seed)).append("\n")
                .append("活时盘：").append(c.effectiveTime).append("\n\n");

        if ("快乐8".equals(a.lottery)) {
            int[] top20 = topK(m[0],20);
            out.append("一级核心8：").append(nums(top20,0,8,true)).append("\n")
                    .append("核心12：").append(nums(top20,0,12,true)).append("\n")
                    .append("扩展20：").append(nums(top20,0,20,true)).append("\n\n")
                    .append("三维共振次数（进入各维Top20）：\n");
            for (int i=0;i<20;i++) {
                int n=top20[i];
                int votes = inTop(a.combined[0],n,20)+inTop(b.combined[0],n,20)+inTop(c.combined[0],n,20);
                out.append(String.format(Locale.CHINA,"%02d×%d",n+1,votes));
                if (i<19) out.append(i%5==4?"\n":"  ");
            }
        } else {
            String[] labels = positions==5 ? new String[]{"万","千","百","十","个"} : new String[]{"百","十","个"};
            for (int p=0;p<positions;p++) {
                int[] top=topK(m[p],5);
                out.append(labels[p]).append("位 Top5：");
                for (int i=0;i<5;i++) {
                    int n=top[i];
                    int votes=inTop(a.combined[p],n,3)+inTop(b.combined[p],n,3)+inTop(c.combined[p],n,3);
                    out.append(n).append("×").append(votes);
                    if(i<4)out.append("  ");
                }
                out.append("\n");
            }
            out.append("\n三维组合 Top10：").append(comboTop(m,10));
        }
        out.append("\n\n说明：×3 表示三个维度均进入该维候选区；×2 表示两维共振。");
        return out.toString();
    }

    private static SystemPick makePick(String name, String raw, long seed, int positions, int universe) {
        double[][] scores = new double[positions][universe];
        for (int p=0;p<positions;p++) for (int n=0;n<universe;n++) {
            long x = hash64(name+"|"+raw+"|"+Long.toUnsignedString(seed)+"|"+p+"|"+n);
            double base = Long.remainderUnsigned(x, 1000000L) / 1000000.0;
            long y = hash64(raw+"|rank|"+p+"|"+n);
            double structure = Long.remainderUnsigned(y,10000L)/10000.0;
            scores[p][n] = 0.72*base + 0.28*structure;
        }
        return new SystemPick(name, raw, scores);
    }

    private static double[][] combine(List<SystemPick> systems, int positions, int universe) {
        double[][] m = new double[positions][universe];
        for (SystemPick s:systems) {
            for (int p=0;p<positions;p++) for (int n=0;n<universe;n++) m[p][n]+=s.scores[p][n];
        }
        for (int p=0;p<positions;p++) for (int n=0;n<universe;n++) m[p][n]/=systems.size();
        return m;
    }

    private static String render(String lottery, Dimension d, LocalDateTime t, long seed,
                                 List<SystemPick> systems, double[][] combined) {
        StringBuilder out=new StringBuilder();
        out.append("【").append(lottery).append(" · ").append(dimName(d)).append("】\n")
                .append("起盘：").append(t).append("\n")
                .append("Seed：").append(Long.toUnsignedString(seed)).append("\n")
                .append("算法：8术等权 / v2.0-beta1\n\n")
                .append("【本维合参】\n");

        if ("快乐8".equals(lottery)) {
            int[] top20=topK(combined[0],20);
            out.append("核心8：").append(nums(top20,0,8,true)).append("\n")
                    .append("核心12：").append(nums(top20,0,12,true)).append("\n")
                    .append("扩展20：").append(nums(top20,0,20,true)).append("\n");
        } else {
            int positions=combined.length;
            String[] labels=positions==5?new String[]{"万","千","百","十","个"}:new String[]{"百","十","个"};
            for(int p=0;p<positions;p++) {
                int[] top=topK(combined[p],5);
                out.append(labels[p]).append("位 Top5：").append(nums(top,0,5,false)).append("\n");
            }
            out.append("组合 Top10：").append(comboTop(combined,10)).append("\n");
        }

        out.append("\n【八术单独结果】\n");
        for (SystemPick s:systems) {
            out.append("①".replace("①", "• ")).append(s.name).append("：");
            if ("快乐8".equals(lottery)) {
                int[] x=topK(s.scores[0],8);
                out.append(nums(x,0,8,true));
            } else {
                for(int p=0;p<s.scores.length;p++) {
                    int[] x=topK(s.scores[p],3);
                    if(p>0)out.append(" | ");
                    out.append(nums(x,0,3,false));
                }
            }
            out.append("\n   盘核：").append(shorten(s.raw,72)).append("\n");
        }
        out.append("\n研究用途：术数映射用于可复现盲测，不代表能改变随机开奖概率。");
        return out.toString();
    }

    private static String liuyaoRaw(TraditionalEngine.CalendarInfo c, long seed) {
        StringBuilder b=new StringBuilder("六爻 ");
        int moving=0;
        for(int i=0;i<6;i++) {
            int v=6+bounded(hash64(c.dateTime()+"|"+seed+"|liuyao|"+i),"line",4);
            if(v==6||v==9)moving++;
            b.append(v);
            if(i<5)b.append("-");
        }
        b.append(" 动爻").append(moving).append(" 日").append(c.dayGz).append(" 时").append(c.hourGz);
        return b.toString();
    }

    private static String meihuaRaw(TraditionalEngine.CalendarInfo c) {
        int y=digitSum(c.year);
        int upper=mod(y+c.month+c.day-1,8);
        int lower=mod(y+c.month+c.day+c.hour-1,8);
        int move=mod(y+c.month+c.day+c.hour+c.minute-1,6)+1;
        return "梅花 主卦"+GUA[upper]+GUA[lower]+" 动"+move+"爻 四柱"+c.fourPillars();
    }

    private static String heluoRaw(TraditionalEngine.CalendarInfo c, long seed) {
        int heaven=mod(digitSum(c.year)+c.month+c.day+(int)(seed&15),10)+1;
        int earth=mod(c.hour+c.minute+digitSum(c.dayGz.hashCode()),10)+1;
        int luoshu=mod(heaven*earth+c.day+c.hour-1,9)+1;
        String[] element={"","水","火","木","金","土","水","火","木","金","土"};
        return "河洛 河图天数"+heaven+"("+element[heaven]+") 地数"+earth+"("+element[earth]+") 洛书宫"+luoshu;
    }

    private static LocalTime drawTime(String lottery) {
        if ("福彩3D".equals(lottery)) return LocalTime.of(21,15);
        if ("排列3".equals(lottery) || "排列5".equals(lottery)) return LocalTime.of(21,25);
        return LocalTime.of(21,30);
    }

    private static String dimName(Dimension d) {
        if(d==Dimension.TIME)return "时间盘";
        if(d==Dimension.RANDOM)return "随机数盘";
        return "当日活时盘";
    }

    private static int inTop(double[] a,int n,int k) {
        int[] t=topK(a,k);
        for(int x:t)if(x==n)return 1;
        return 0;
    }

    private static int[] topK(double[] a,int k) {
        Integer[] idx=new Integer[a.length];
        for(int i=0;i<a.length;i++)idx[i]=i;
        Arrays.sort(idx,new Comparator<Integer>() {
            @Override public int compare(Integer x,Integer y){return Double.compare(a[y],a[x]);}
        });
        int n=Math.min(k,a.length);
        int[] out=new int[n];
        for(int i=0;i<n;i++)out[i]=idx[i];
        return out;
    }

    private static String comboTop(double[][] matrix,int count) {
        List<Combo> all=new ArrayList<>();
        buildCombos(matrix,0,new StringBuilder(),0.0,all);
        all.sort(new Comparator<Combo>() {
            @Override public int compare(Combo a,Combo b){return Double.compare(b.score,a.score);}
        });
        StringBuilder b=new StringBuilder();
        for(int i=0;i<Math.min(count,all.size());i++){
            if(i>0)b.append(" / ");
            b.append(all.get(i).digits);
        }
        return b.toString();
    }

    private static void buildCombos(double[][] m,int pos,StringBuilder cur,double score,List<Combo> out) {
        if(pos==m.length){out.add(new Combo(cur.toString(),score));return;}
        int[] top=topK(m[pos],3);
        for(int n:top){
            int len=cur.length();
            cur.append(n);
            buildCombos(m,pos+1,cur,score+m[pos][n],out);
            cur.setLength(len);
        }
    }

    private static final class Combo {
        final String digits; final double score;
        Combo(String digits,double score){this.digits=digits;this.score=score;}
    }

    private static String nums(int[] a,int from,int to,boolean oneBased) {
        StringBuilder b=new StringBuilder();
        int end=Math.min(to,a.length);
        for(int i=from;i<end;i++){
            if(i>from)b.append(" ");
            if(oneBased)b.append(String.format(Locale.CHINA,"%02d",a[i]+1));
            else b.append(a[i]);
        }
        return b.toString();
    }

    private static String compact(String s){return s.replace("\n"," ").replaceAll("\\s+"," ").trim();}
    private static String shorten(String s,int n){return s.length()<=n?s:s.substring(0,n)+"…";}
    private static int digitSum(int x){int n=Math.abs(x),s=0;do{s+=n%10;n/=10;}while(n>0);return s;}
    private static int mod(int a,int n){int r=a%n;return r<0?r+n:r;}

    private static int bounded(long seed,String ns,int n){
        if(n<=1)return 0;
        long x=hash64(Long.toUnsignedString(seed)+"|"+ns);
        return (int)Long.remainderUnsigned(x,(long)n);
    }

    private static long hash64(String s) {
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            byte[] d=md.digest(s.getBytes(StandardCharsets.UTF_8));
            long x=0L;
            for(int i=0;i<8;i++)x=(x<<8)|(d[i]&0xffL);
            return x;
        } catch(Exception e) {
            long x=1469598103934665603L;
            for(char ch:s.toCharArray()){x^=ch;x*=1099511628211L;}
            return x;
        }
    }
}
