package com.abe618.bingfaqimenv2;

import java.util.Random;
import java.util.Locale;

/**
 * 兵法奇门·球赛随机盘 V2.1
 *
 * 排盘部分按原 APK 的 1080 随机局逻辑复现；预测层改为去偏后的多维合参：
 * 1) 主客宫内门/星/神状态；2) 主客宫五行生克；3) 景门技术；
 * 4) 地/天盘辛辅助；5) 值使过程；6) 值符原宫 vs 值使宫次判。
 *
 * 说明：这是民俗算法实验，不代表真实比赛概率，不作投注依据。
 */
public final class QimenEngine {
    private QimenEngine() {}

    public static final String[] GAN = {"甲","乙","丙","丁","戊","己","庚","辛","壬","癸"};
    public static final String[] ZHI = {"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};
    private static final String[] YI = {"戊","己","庚","辛","壬","癸","丁","丙","乙"};
    private static final int[] LUO = {1,8,3,4,9,2,7,6};
    private static final int[] SHUN = {1,8,3,4,9,2,7,6};
    private static final int[] NI = {1,6,7,2,9,4,3,8};
    private static final String[] GOD = {"值符","腾蛇","太阴","六合","白虎","玄武","九地","九天"};

    public static final class Board {
        public int serial;
        public boolean yin;
        public int ju;
        public int hourIndex;
        public String hourGz;
        public String xunYi;
        public int zhiFuOrigin;
        public String zhiFuStar;
        public int zhiFuGong;
        public int zhiShiGong;
        public String zhiShiDoor;
        public int jingGong;
        public int homeGong;
        public int awayGong;
        public final String[] di = new String[10];
        public final String[] tian = new String[10];
        public final String[] star = new String[10];
        public final String[] door = new String[10];
        public final String[] god = new String[10];
        public Prediction prediction;
    }

    public static final class Prediction {
        public String result;
        public double finalIndex;
        public int votes;
        public double primary;
        public double technique;
        public double medal;
        public double process;
        public double secondary;
        public double collision;
        public double fuGeng;
        public int totalGoals;
        public int homeGoals;
        public int awayGoals;

        public String sizeText() { return totalGoals >= 3 ? "大2.5" : "小2.5"; }
        public String oddEvenText() { return (totalGoals & 1) == 0 ? "双" : "单"; }
        public String scoreText() { return homeGoals + ":" + awayGoals; }
        public String strengthText() {
            double a = Math.abs(finalIndex);
            if (a >= 1.10) return "强";
            if (a >= 0.55) return "中强";
            if (a >= 0.25) return "中";
            return "接近";
        }
    }

    public static Board generate(Random rng) {
        return generateBySerial(rng.nextInt(1080) + 1);
    }

    /** 用于盲测/复盘：固定序号 1..1080 可重复得到同一局。 */
    public static Board generateBySerial(int serial) {
        if (serial < 1 || serial > 1080) throw new IllegalArgumentException("serial must be 1..1080");
        Board b = new Board();
        b.serial = serial;
        int raw = serial - 1;
        b.yin = raw >= 540;
        int within = raw % 540;
        b.ju = within / 60 + 1;
        b.hourIndex = within % 60;
        b.hourGz = jiazi(b.hourIndex);
        b.xunYi = YI[b.hourIndex / 10];

        buildDi(b);
        b.zhiFuOrigin = findStem(b.di, b.xunYi);
        if (b.zhiFuOrigin < 0) b.zhiFuOrigin = 2;
        if (b.zhiFuOrigin == 5) b.zhiFuOrigin = 2;
        b.zhiFuStar = basicStar(b.zhiFuOrigin);

        String hourStem = GAN[b.hourIndex % 10];
        b.homeGong = "甲".equals(hourStem) ? b.zhiFuOrigin : findStem(b.di, hourStem);
        if (b.homeGong < 0) b.homeGong = b.zhiFuOrigin;

        // 保留原盘：值符随时干落宫。
        b.zhiFuGong = b.homeGong;
        buildTianAndStars(b);
        buildDoors(b);
        buildGods(b);

        b.awayGong = "甲".equals(hourStem) ? b.zhiFuGong : findStem(b.tian, hourStem);
        if (b.awayGong < 0) b.awayGong = b.zhiFuGong;

        b.prediction = predict(b);
        return b;
    }

    private static void buildDi(Board b) {
        fill(b.di, "");
        int palace = b.ju;
        for (String s : YI) {
            b.di[palace] = s;
            palace += b.yin ? -1 : 1;
            if (palace == 0) palace = 9;
            if (palace == 10) palace = 1;
        }
    }

    private static void buildTianAndStars(Board b) {
        fill(b.tian, "");
        fill(b.star, "");
        int oi = idx(LUO, b.zhiFuOrigin);
        int hi = idx(LUO, b.zhiFuGong);
        int shift = mod(hi - oi, 8);
        for (int i = 0; i < 8; i++) {
            int src = LUO[i];
            int dst = LUO[mod(i + shift, 8)];
            b.tian[dst] = b.di[src];
            b.star[dst] = src == 2 ? "禽芮" : basicStar(src);
        }
        b.tian[5] = b.di[5];
        b.star[5] = "";
    }

    private static void buildDoors(Board b) {
        fill(b.door, "");
        int p = b.zhiFuOrigin;
        for (int i = 0; i < b.hourIndex % 10; i++) {
            p += b.yin ? -1 : 1;
            if (p == 0) p = 9;
            if (p == 10) p = 1;
        }
        b.zhiShiGong = p == 5 ? 2 : p;
        b.zhiShiDoor = basicDoor(b.zhiFuOrigin);

        int oi = idx(LUO, b.zhiFuOrigin);
        int zi = idx(LUO, b.zhiShiGong);
        int shift = (oi >= 0 && zi >= 0) ? mod(zi - oi, 8) : 0;
        for (int i = 0; i < 8; i++) {
            int src = LUO[i];
            int dst = LUO[mod(i + shift, 8)];
            b.door[dst] = basicDoor(src);
        }
        b.door[5] = "";
        b.jingGong = 1;
        for (int i = 1; i <= 9; i++) if ("景门".equals(b.door[i])) b.jingGong = i;
    }

    private static void buildGods(Board b) {
        fill(b.god, "");
        int[] seq = b.yin ? NI : SHUN;
        int start = idx(seq, b.zhiFuGong);
        if (start < 0) start = idx(seq, 2);
        for (int i = 0; i < 8; i++) {
            int dst = seq[mod(start + i, 8)];
            b.god[dst] = GOD[i];
        }
        b.god[5] = "";
    }

    private static Prediction predict(Board b) {
        Prediction p = new Prediction();
        int h = b.homeGong;
        int a = b.awayGong;

        // 维度1：主客宫内综合状态。值符本身不再给主方结构性加分。
        double primary = baseState(b, h) - baseState(b, a)
                + 0.70 * relationUnit(element(h), element(a));

        // 维度2：景门为技术/战术辅助，用景门宫五行分别作用于主客宫。
        String jingElement = element(b.jingGong);
        double technique = supportFromRef(jingElement, element(h))
                - supportFromRef(jingElement, element(a));

        // 维度3：辛为辅助指标，同时看地盘辛与天盘辛。
        int groundXin = findStem(b.di, "辛");
        int skyXin = findStem(b.tian, "辛");
        double medal = 0.0;
        if (groundXin >= 1) {
            medal += supportFromRef(element(groundXin), element(h))
                    - supportFromRef(element(groundXin), element(a));
        }
        if (skyXin >= 1) {
            medal += supportFromRef(element(skyXin), element(h))
                    - supportFromRef(element(skyXin), element(a));
        }

        // 维度4：值使代表比赛过程。
        String processElement = element(b.zhiShiGong);
        double process = supportFromRef(processElement, element(h))
                - supportFromRef(processElement, element(a));

        // 维度5：值符原宫 vs 值使宫作为次判。
        double secondary = pairScore(b, b.zhiFuOrigin, b.zhiShiGong);

        // V2.1 同宫决胜层：
        // 当地盘时干与天盘时干同宫时，原来的宫态/景门/辛/值使四维会机械归零。
        // 这时改用传统备用主客轴：值符-值使、值符-六庚，并让景门与辛作用到备用轴。
        int groundGeng = findStem(b.di, "庚");
        int skyGeng = findStem(b.tian, "庚");
        double fuGeng = 0.0;
        int gengCount = 0;
        if (groundGeng >= 1) {
            fuGeng += pairScore(b, b.zhiFuOrigin, groundGeng);
            gengCount++;
        }
        if (skyGeng >= 1) {
            fuGeng += pairScore(b, b.zhiFuOrigin, skyGeng);
            gengCount++;
        }
        if (gengCount > 0) fuGeng /= gengCount;

        String altHomeElement = element(b.zhiFuOrigin);
        String altAwayElement = element(b.zhiShiGong);
        double altTechnique = supportFromRef(jingElement, altHomeElement)
                - supportFromRef(jingElement, altAwayElement);
        double altMedal = 0.0;
        int xinCount = 0;
        if (groundXin >= 1) {
            altMedal += supportFromRef(element(groundXin), altHomeElement)
                    - supportFromRef(element(groundXin), altAwayElement);
            xinCount++;
        }
        if (skyXin >= 1) {
            altMedal += supportFromRef(element(skyXin), altHomeElement)
                    - supportFromRef(element(skyXin), altAwayElement);
            xinCount++;
        }
        if (xinCount > 0) altMedal /= xinCount;

        // secondary 在完整 1080 状态空间均值约 +0.1324，中心化后再进入同宫决胜，
        // 防止“解平”以后又结构性偏向主胜。
        double secondaryCentered = secondary - 0.1324;
        double collision = 0.52 * secondaryCentered
                + 0.18 * altTechnique
                + 0.15 * altMedal
                + 0.15 * fuGeng;

        double finalIndex;
        if (h == a) {
            finalIndex = collision;
        } else {
            finalIndex = 0.58 * primary
                    + 0.12 * technique
                    + 0.09 * medal
                    + 0.08 * process
                    + 0.0455 * secondary;
        }

        double[] dims = h == a
                ? new double[]{secondaryCentered, altTechnique, altMedal, fuGeng, collision}
                : new double[]{primary, technique, medal, process, secondary};
        int votes = 0;
        for (double d : dims) {
            if (d > 0.12) votes++;
            else if (d < -0.12) votes--;
        }

        String result;
        if (h == a) {
            // 同宫只保留很窄的真正均势区，不再把“同宫”直接等同“平局”。
            final double collisionDrawThreshold = 0.44;
            if (collision > collisionDrawThreshold) result = "主胜";
            else if (collision < -collisionDrawThreshold) result = "客胜";
            else if (votes >= 3) result = "主胜";
            else if (votes <= -3) result = "客胜";
            else result = "平";
        } else {
            final double drawThreshold = 0.15;
            if (finalIndex > drawThreshold) result = "主胜";
            else if (finalIndex < -drawThreshold) result = "客胜";
            else if (votes >= 3) result = "主胜";
            else if (votes <= -3) result = "客胜";
            else result = "平";
        }

        p.result = result;
        p.finalIndex = finalIndex;
        p.votes = votes;
        p.primary = primary;
        p.technique = technique;
        p.medal = medal;
        p.process = process;
        p.secondary = secondary;
        p.collision = collision;
        p.fuGeng = fuGeng;

        buildGoalEstimate(b, p);
        return p;
    }

    private static void buildGoalEstimate(Board b, Prediction p) {
        int total = 2;
        if (b.jingGong == b.homeGong || b.jingGong == b.awayGong) total++;
        if (isAttackDoor(b.door[b.homeGong])) total++;
        if (isAttackDoor(b.door[b.awayGong])) total++;
        if (isBarrierDoor(b.door[b.homeGong]) && isBarrierDoor(b.door[b.awayGong])) total--;
        if (b.homeGong == b.awayGong) total--;
        if (isAttackDoor(b.zhiShiDoor)) total++;
        if (isBarrierDoor(b.zhiShiDoor)) total--;
        if (Math.abs(p.finalIndex) >= 0.90) total++;
        if (b.serial % 17 == 0) total--;
        total = clamp(total, 0, 6);

        if ("平".equals(p.result)) {
            if ((total & 1) == 1) total = total >= 5 ? 4 : total + 1;
            p.homeGoals = total / 2;
            p.awayGoals = total / 2;
        } else if ("主胜".equals(p.result)) {
            total = Math.max(1, total);
            int away = Math.max(0, (total - 1) / 2);
            int home = total - away;
            if (home <= away) home = away + 1;
            p.homeGoals = home;
            p.awayGoals = away;
            total = home + away;
        } else {
            total = Math.max(1, total);
            int home = Math.max(0, (total - 1) / 2);
            int away = total - home;
            if (away <= home) away = home + 1;
            p.homeGoals = home;
            p.awayGoals = away;
            total = home + away;
        }
        p.totalGoals = total;
    }

    private static double baseState(Board b, int palace) {
        double god = godScore(b.god[palace]);
        // 原版 homeGong == zhiFuGong，值符 +0.75 再叠加值符宫 +0.35，造成主胜硬偏。
        // V2 将值符本身中性化，仅保留其它神的状态信息。
        if ("值符".equals(b.god[palace])) god = 0.0;
        return doorScore(b.door[palace])
                + 0.85 * starScore(b.star[palace])
                + 0.55 * god
                + internalHarmony(b, palace);
    }

    private static double pairScore(Board b, int p1, int p2) {
        return baseState(b, p1) - baseState(b, p2)
                + 0.60 * relationUnit(element(p1), element(p2));
    }

    private static double internalHarmony(Board b, int palace) {
        String pe = element(palace);
        String de = doorElement(b.door[palace]);
        String se = starElement(b.star[palace]);
        double dp = 0.0;
        if (!de.isEmpty()) {
            if (de.equals(pe)) dp += 0.10;
            else if (generates(pe, de)) dp += 0.18;
            else if (generates(de, pe)) dp += 0.10;
            else if (controls(de, pe)) dp += 0.08;
            else if (controls(pe, de)) dp -= 0.22;
        }
        double sd = 0.0;
        if (!se.isEmpty() && !de.isEmpty()) {
            if (se.equals(de)) sd += 0.06;
            else if (controls(se, de)) sd += 0.24;
            else if (controls(de, se)) sd -= 0.24;
            else if (generates(se, de)) sd += 0.14;
            else if (generates(de, se)) sd -= 0.10;
        }
        return dp + sd;
    }

    private static double supportFromRef(String ref, String side) {
        if (ref.isEmpty() || side.isEmpty()) return 0.0;
        if (ref.equals(side)) return 0.10;
        if (generates(ref, side)) return 0.40;
        if (generates(side, ref)) return -0.15;
        if (controls(ref, side)) return -0.40;
        if (controls(side, ref)) return 0.20;
        return 0.0;
    }

    /** 正值偏向第一个宫，负值偏向第二个宫。 */
    private static double relationUnit(String a, String b) {
        if (a.equals(b)) return 0.0;
        if (controls(a, b)) return 1.0;
        if (controls(b, a)) return -1.0;
        if (generates(b, a)) return 0.45;
        if (generates(a, b)) return -0.45;
        return 0.0;
    }

    public static String palaceName(int p) {
        switch (p) {
            case 1: return "坎1";
            case 2: return "坤2";
            case 3: return "震3";
            case 4: return "巽4";
            case 5: return "中5";
            case 6: return "乾6";
            case 7: return "兑7";
            case 8: return "艮8";
            case 9: return "离9";
            default: return "";
        }
    }

    public static String element(int p) {
        switch (p) {
            case 1: return "水";
            case 2: case 5: case 8: return "土";
            case 3: case 4: return "木";
            case 6: case 7: return "金";
            case 9: return "火";
            default: return "";
        }
    }

    public static String basicDoor(int p) {
        switch (p) {
            case 1: return "休门";
            case 2: return "死门";
            case 3: return "伤门";
            case 4: return "杜门";
            case 6: return "开门";
            case 7: return "惊门";
            case 8: return "生门";
            case 9: return "景门";
            default: return "";
        }
    }

    public static String basicStar(int p) {
        switch (p) {
            case 1: return "天蓬";
            case 2: return "天芮";
            case 3: return "天冲";
            case 4: return "天辅";
            case 5: return "天禽";
            case 6: return "天心";
            case 7: return "天柱";
            case 8: return "天任";
            case 9: return "天英";
            default: return "";
        }
    }

    private static double doorScore(String s) {
        if (s == null) return 0.0;
        switch (s) {
            case "开门": return 1.25;
            case "生门": return 1.15;
            case "休门": return 0.80;
            case "景门": return 0.75;
            case "伤门": return -0.55;
            case "杜门": return -0.75;
            case "惊门": return -0.90;
            case "死门": return -1.20;
            default: return 0.0;
        }
    }

    private static double starScore(String s) {
        if (s == null) return 0.0;
        switch (s) {
            case "天辅": return 0.55;
            case "天心": return 0.50;
            case "天任": return 0.35;
            case "天英": return 0.25;
            case "天冲": return 0.15;
            case "天蓬": return 0.00;
            case "天柱": return -0.20;
            case "天芮": case "禽芮": return -0.45;
            default: return 0.0;
        }
    }

    private static double godScore(String s) {
        if (s == null) return 0.0;
        switch (s) {
            case "值符": return 0.75;
            case "六合": return 0.55;
            case "太阴": return 0.45;
            case "九天": return 0.40;
            case "九地": return 0.30;
            case "腾蛇": return -0.30;
            case "玄武": return -0.45;
            case "白虎": return -0.55;
            default: return 0.0;
        }
    }

    private static String doorElement(String s) {
        if (s == null) return "";
        switch (s) {
            case "休门": return "水";
            case "生门": case "死门": return "土";
            case "伤门": case "杜门": return "木";
            case "景门": return "火";
            case "惊门": case "开门": return "金";
            default: return "";
        }
    }

    private static String starElement(String s) {
        if (s == null) return "";
        switch (s) {
            case "天蓬": return "水";
            case "天芮": case "禽芮": case "天任": return "土";
            case "天冲": case "天辅": return "木";
            case "天英": return "火";
            case "天柱": case "天心": return "金";
            default: return "";
        }
    }

    private static boolean isAttackDoor(String s) {
        return "景门".equals(s) || "开门".equals(s) || "伤门".equals(s);
    }

    private static boolean isBarrierDoor(String s) {
        return "杜门".equals(s) || "死门".equals(s) || "休门".equals(s);
    }

    private static boolean controls(String a, String b) {
        return ("木".equals(a) && "土".equals(b))
                || ("土".equals(a) && "水".equals(b))
                || ("水".equals(a) && "火".equals(b))
                || ("火".equals(a) && "金".equals(b))
                || ("金".equals(a) && "木".equals(b));
    }

    private static boolean generates(String a, String b) {
        return ("木".equals(a) && "火".equals(b))
                || ("火".equals(a) && "土".equals(b))
                || ("土".equals(a) && "金".equals(b))
                || ("金".equals(a) && "水".equals(b))
                || ("水".equals(a) && "木".equals(b));
    }

    private static int findStem(String[] a, String s) {
        for (int i = 0; i < a.length; i++) if (s.equals(a[i])) return i;
        return -1;
    }

    private static int idx(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }

    private static int mod(int a, int b) { int r = a % b; return r < 0 ? r + b : r; }
    private static int clamp(int x, int lo, int hi) { return Math.max(lo, Math.min(hi, x)); }
    private static void fill(String[] a, String s) { for (int i = 0; i < a.length; i++) a[i] = s; }

    private static String jiazi(int i) {
        return GAN[i % 10] + ZHI[i % 12];
    }

    public static String debugLine(Board b) {
        Prediction p = b.prediction;
        return String.format(Locale.CHINA,
                "#%04d %s遁%d局 %s 主%s 客%s -> %s index=%+.3f votes=%+d score=%s goals=%d",
                b.serial, b.yin ? "阴" : "阳", b.ju, b.hourGz,
                palaceName(b.homeGong), palaceName(b.awayGong), p.result,
                p.finalIndex, p.votes, p.scoreText(), p.totalGoals);
    }
}
