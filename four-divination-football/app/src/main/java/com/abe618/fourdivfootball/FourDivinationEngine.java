package com.abe618.fourdivfootball;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Four-system football blind-test engine.
 * Traditional structures are preserved where practical; football mappings are experimental.
 */
public final class FourDivinationEngine {
    private FourDivinationEngine() {}

    public static final class SystemResult {
        public final String name, raw, direction, overUnder, oddEven, note;
        public final int goals;
        public final boolean cleanSheet, burst, stable;
        public final double margin;

        SystemResult(String name, String raw, String direction, int goals, boolean cleanSheet,
                     boolean burst, boolean stable, double margin, String note) {
            this.name = name; this.raw = raw; this.direction = direction; this.goals = goals;
            this.overUnder = goals >= 3 ? "大2.5" : "小2.5";
            this.oddEven = (goals & 1) == 1 ? "单" : "双";
            this.cleanSheet = cleanSheet; this.burst = burst; this.stable = stable;
            this.margin = margin; this.note = note;
        }

        public String summary() {
            return direction + " · " + goals + "球 · " + overUnder + " · " + oddEven
                    + (cleanSheet ? " · 零封信号" : "") + (burst ? " · 爆发信号" : "");
        }
    }

    public static final class CombinedResult {
        public final String direction, safer, halfFull, goalRange, overUnder, oddEven;
        public final String primaryScore, scoreCandidates, structure;
        public final int totalGoals, tendency;
        public final boolean cleanSheet, burstBranch;

        CombinedResult(String direction, String safer, String halfFull, int totalGoals,
                       String goalRange, String overUnder, String oddEven, String primaryScore,
                       String scoreCandidates, int tendency, String structure,
                       boolean cleanSheet, boolean burstBranch) {
            this.direction = direction; this.safer = safer; this.halfFull = halfFull;
            this.totalGoals = totalGoals; this.goalRange = goalRange; this.overUnder = overUnder;
            this.oddEven = oddEven; this.primaryScore = primaryScore;
            this.scoreCandidates = scoreCandidates; this.tendency = tendency;
            this.structure = structure; this.cleanSheet = cleanSheet; this.burstBranch = burstBranch;
        }
    }

    public static final class CastState {
        public final long seed;
        public final SystemResult maya, sikidy, ifa, raml;
        public final CombinedResult combined;
        CastState(long seed, SystemResult maya, SystemResult sikidy,
                  SystemResult ifa, SystemResult raml, CombinedResult combined) {
            this.seed = seed; this.maya = maya; this.sikidy = sikidy;
            this.ifa = ifa; this.raml = raml; this.combined = combined;
        }
        public SystemResult[] systems() { return new SystemResult[]{maya, sikidy, ifa, raml}; }
    }

    private static final String[] MAYA_SIGNS = {
            "Imix'", "Ik'", "Ak'b'al", "K'an", "Chikchan", "Kimi", "Manik'", "Lamat", "Muluk", "Ok",
            "Chuwen", "Eb'", "B'en", "Ix", "Men", "K'ib'", "Kab'an", "Etz'nab'", "Kawak", "Ajaw"
    };

    public static CastState castRandom() { return cast(new SecureRandom().nextLong()); }

    public static long seedFromText(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(text.getBytes(StandardCharsets.UTF_8));
            long x = 0L;
            for (int i = 0; i < 8; i++) x = (x << 8) | (b[i] & 0xffL);
            return x;
        } catch (Exception e) {
            return text.hashCode() * 2654435761L;
        }
    }

    public static CastState cast(long seed) {
        SystemResult maya = castMaya(mix64(seed ^ 0x4d415941L));
        SystemResult sikidy = castSikidy(mix64(seed ^ 0x53494b494459L));
        SystemResult ifa = castIfa(mix64(seed ^ 0x494641L));
        SystemResult raml = castRaml(mix64(seed ^ 0x52414d4cL));
        return new CastState(seed, maya, sikidy, ifa, raml, combine(maya, sikidy, ifa, raml));
    }

    private static SystemResult castMaya(long seed) {
        Random r = new Random(seed);
        int index = r.nextInt(260) + 1;
        int number = ((index - 1) % 13) + 1;
        int sign = (index - 1) % 20;
        double signAxis = (((sign * 7 + 3) % 11) - 5) * 0.34;
        double numberAxis = ((number % 5) - 2) * 0.30;
        double margin = signAxis + numberAxis;
        int goals = clamp(1 + ((number + sign * 3 + Integer.bitCount(index)) % 4), 1, 4);
        boolean burst = (number >= 11 || sign == 4 || sign == 18) && goals >= 3;
        boolean clean = (sign == 5 || sign == 9 || sign == 17) && Math.abs(margin) > 0.7;
        if (burst) goals = Math.max(goals, 3);
        String raw = index + "/260 · " + number + " " + MAYA_SIGNS[sign];
        return new SystemResult("玛雅 Tzolk'in", raw, dir(margin, 0.55), goals,
                clean, burst, false, margin, "13数×20日符；足球方向为固定实验映射");
    }

    private static SystemResult castSikidy(long seed) {
        Random r = new Random(seed);
        int[][] f = geomancyLike(r);
        double home = strength(f[0]) + 0.75 * strength(f[12]) + 0.35 * strength(f[4]);
        double away = strength(f[3]) + 0.75 * strength(f[13]) + 0.35 * strength(f[7]);
        double margin = home - away;
        int judgeSingles = singles(f[14]);
        int stableCount = 0;
        for (int[] x : f) if (singles(x) == 0 || singles(x) == 4) stableCount++;
        boolean stable = stableCount >= 4 || judgeSingles == 0 || judgeSingles == 4;
        int active = singles(f[12]) + singles(f[13]) + singles(f[14]) + singles(f[15]);
        int goals = clamp((int)Math.round(1.0 + active / 4.0 + Math.abs(margin) * 0.25), 1, 4);
        boolean burst = Math.abs(margin) > 2.7 && active >= 9;
        boolean clean = Math.abs(margin) > 2.2 && judgeSingles <= 1;
        String raw = "母 " + fig(f[0]) + " " + fig(f[1]) + " " + fig(f[2]) + " " + fig(f[3]) + " · 判 " + fig(f[14]);
        String note = stable ? "稳定象明显；不再自动等同于小球" : "奇偶派生后合看见证与判象";
        return new SystemResult("Sikidy", raw, dir(margin, 0.70), goals, clean, burst, stable, margin, note);
    }

    private static SystemResult castIfa(long seed) {
        Random r = new Random(seed);
        int[] bits = new int[8];
        int ones = 0;
        for (int i = 0; i < 8; i++) { bits[i] = r.nextBoolean() ? 1 : 0; ones += bits[i]; }
        int index = 0;
        for (int b : bits) index = (index << 1) | b;
        double left = weighted4(bits, 0), right = weighted4(bits, 4);
        double margin = left - right;
        int transitions = 0, longest = 1, run = 1;
        for (int i = 1; i < 8; i++) {
            if (bits[i] != bits[i-1]) { transitions++; run = 1; }
            else { run++; longest = Math.max(longest, run); }
        }
        boolean concentrated = longest >= 4 || ones <= 1 || ones >= 7;
        int goals = clamp(1 + ((ones + transitions + index) % 4), 1, 4);
        if (concentrated) goals = Math.max(goals, 3);
        boolean clean = concentrated && Math.abs(margin) > 1.0;
        String raw = bits(bits) + " · Odu实验索引 #" + (index + 1);
        String note = concentrated ? "高度集中：只表示单边化/后程增强，不直接指定主客" : "二元签名较均衡";
        return new SystemResult("Ifá", raw, dir(margin, 0.58), goals, clean, concentrated, false, margin, note);
    }

    private static SystemResult castRaml(long seed) {
        Random r = new Random(seed);
        int[][] f = geomancyLike(r);
        double home = 1.2 * strength(f[0]) + 0.9 * strength(f[12]) + 0.45 * strength(f[4]);
        double away = 1.2 * strength(f[3]) + 0.9 * strength(f[13]) + 0.45 * strength(f[7]);
        double margin = home - away;
        int front = singles(f[0]) + singles(f[1]);
        int rear = singles(f[12]) + singles(f[13]) + singles(f[14]) + singles(f[15]);
        boolean lateBurst = front <= 3 && rear >= 10;
        boolean quietFinish = rear <= 4;
        int goals = clamp((int)Math.round(1.0 + rear / 5.0 + Math.abs(margin) * 0.22), 1, 4);
        if (lateBurst) goals = Math.max(goals, 3);
        if (quietFinish) goals = Math.min(goals, 2);
        boolean clean = Math.abs(margin) > 1.8 && (quietFinish || singles(f[14]) <= 1);
        String raw = "母 " + fig(f[0]) + "/" + fig(f[1]) + "/" + fig(f[2]) + "/" + fig(f[3]) + " · 法官 " + fig(f[14]);
        String note = lateBurst ? "前静后强：记录爆发分支" : quietFinish ? "后程收束：记录零封/低比分分支" : "常规沙占结构";
        return new SystemResult("阿拉伯沙占", raw, dir(margin, 0.68), goals, clean, lateBurst, quietFinish, margin, note);
    }

    private static CombinedResult combine(SystemResult maya, SystemResult sikidy, SystemResult ifa, SystemResult raml) {
        SystemResult[] xs = {maya, sikidy, ifa, raml};
        int homeVotes = 0, awayVotes = 0, drawVotes = 0, oddVotes = 0, evenVotes = 0;
        double margin = 0;
        int totalSum = 0;
        for (SystemResult x : xs) {
            if ("主胜".equals(x.direction)) homeVotes++; else if ("客胜".equals(x.direction)) awayVotes++; else drawVotes++;
            margin += Math.max(-2.5, Math.min(2.5, x.margin));
            totalSum += x.goals;
            if ("单".equals(x.oddEven)) oddVotes++; else evenVotes++;
        }
        margin /= 4.0;
        String direction;
        if (homeVotes >= 3) direction = "主胜";
        else if (awayVotes >= 3) direction = "客胜";
        else if (drawVotes >= 2 || Math.abs(margin) < 0.28) direction = "平局";
        else direction = margin > 0 ? "主胜" : "客胜";

        int goals = clamp((int)Math.round(totalSum / 4.0), 1, 5);
        boolean burstBranch = ifa.burst && raml.burst;
        if (burstBranch) goals = Math.max(goals, 3);

        int sameDirectionSupport = 0;
        for (SystemResult x : xs) if (x.direction.equals(direction)) sameDirectionSupport++;
        boolean cleanSheet = raml.cleanSheet && sameDirectionSupport >= 2;
        if (!cleanSheet && ifa.cleanSheet && raml.direction.equals(direction) && !"平局".equals(direction)) cleanSheet = true;

        if (sikidy.stable && !burstBranch && goals > 3) goals = 3;

        String oddEven;
        if (oddVotes > evenVotes) oddEven = "单";
        else if (evenVotes > oddVotes) oddEven = "双";
        else oddEven = (goals & 1) == 1 ? "单" : "双";
        if (((goals & 1) == 1) != "单".equals(oddEven)) {
            if (goals < 5) goals++; else goals--;
        }

        String safer = "平局".equals(direction) ? "均衡盘，重点防平" : ("主胜".equals(direction) ? "主队不败" : "客队不败");
        String halfFull;
        if ("平局".equals(direction)) halfFull = "平/平";
        else if (sameDirectionSupport >= 3 && Math.abs(margin) > 0.9) halfFull = shortDir(direction) + "/" + shortDir(direction);
        else halfFull = "平/" + shortDir(direction);

        int[] primary = allocate(goals, direction, cleanSheet, margin);
        String primaryScore = primary[0] + ":" + primary[1];
        List<String> cands = new ArrayList<>();
        addScore(cands, primary[0], primary[1]);

        boolean balancedLow = (homeVotes <= 2 && awayVotes <= 2 && goals <= 2);
        if (balancedLow) addScore(cands, 1, 1);
        if (cleanSheet && !"平局".equals(direction)) {
            if ("主胜".equals(direction)) { addScore(cands, 1, 0); addScore(cands, 2, 0); }
            else { addScore(cands, 0, 1); addScore(cands, 0, 2); }
        }
        if (burstBranch && !"平局".equals(direction)) {
            if ("主胜".equals(direction)) { addScore(cands, 3, 0); addScore(cands, 3, 1); }
            else { addScore(cands, 0, 3); addScore(cands, 1, 3); }
        }
        if ("平局".equals(direction)) { addScore(cands, 1,1); addScore(cands,0,0); addScore(cands,2,2); }
        else if ("主胜".equals(direction)) { addScore(cands,2,1); addScore(cands,2,0); addScore(cands,1,0); }
        else { addScore(cands,1,2); addScore(cands,0,2); addScore(cands,0,1); }
        while (cands.size() > 4) cands.remove(cands.size() - 1);

        int agreement = Math.max(homeVotes, Math.max(awayVotes, drawVotes));
        int tendency = clamp(48 + agreement * 7 + (burstBranch || cleanSheet ? 5 : 0)
                + (int)Math.round(Math.min(8, Math.abs(margin) * 4)), 50, 84);
        String structure = "四术票：主" + homeVotes + " / 平" + drawVotes + " / 客" + awayVotes + "；"
                + (burstBranch ? "爆发反转分支开启；" : "")
                + (cleanSheet ? "零封分支开启；" : "")
                + (sikidy.stable ? "Sikidy稳定象只作节奏参考。" : "当前等权合参。");

        return new CombinedResult(direction, safer, halfFull, goals,
                Math.max(0, goals - 1) + "–" + Math.min(6, goals + 1) + "球",
                goals >= 3 ? "大2.5" : "小2.5", oddEven,
                primaryScore, String.join("、", cands), tendency,
                structure, cleanSheet, burstBranch);
    }

    private static int[][] geomancyLike(Random r) {
        int[][] f = new int[16][4];
        for (int m = 0; m < 4; m++) for (int row = 0; row < 4; row++) f[m][row] = r.nextBoolean() ? 1 : 0;
        for (int d = 0; d < 4; d++) for (int row = 0; row < 4; row++) f[4 + d][row] = f[row][d];
        combineBits(f[0], f[1], f[8]); combineBits(f[2], f[3], f[9]);
        combineBits(f[4], f[5], f[10]); combineBits(f[6], f[7], f[11]);
        combineBits(f[8], f[9], f[12]); combineBits(f[10], f[11], f[13]);
        combineBits(f[12], f[13], f[14]); combineBits(f[14], f[0], f[15]);
        return f;
    }

    private static int[] allocate(int total, String dir, boolean clean, double margin) {
        if ("平局".equals(dir)) { int g = total / 2; return new int[]{g, g}; }
        if (clean) return "主胜".equals(dir) ? new int[]{Math.max(1, total), 0} : new int[]{0, Math.max(1, total)};
        double edge = Math.min(0.28, 0.08 + Math.abs(margin) * 0.08);
        double homeShare = "主胜".equals(dir) ? 0.5 + edge : 0.5 - edge;
        int h = clamp((int)Math.round(total * homeShare), 0, total), a = total - h;
        if ("主胜".equals(dir) && h <= a) { h = Math.min(total, a + 1); a = total - h; }
        if ("客胜".equals(dir) && a <= h) { a = Math.min(total, h + 1); h = total - a; }
        return new int[]{h, a};
    }

    private static void addScore(List<String> out, int h, int a) {
        String s = h + ":" + a; if (!out.contains(s)) out.add(s);
    }
    private static String shortDir(String s) { return "主胜".equals(s) ? "主" : "客胜".equals(s) ? "客" : "平"; }
    private static String dir(double margin, double dead) {
        if (margin > dead) return "主胜"; if (margin < -dead) return "客胜"; return "平局";
    }
    private static double weighted4(int[] bits, int off) {
        double[] w = {1.0, 0.8, 0.65, 0.5}; double x = 0;
        for (int i = 0; i < 4; i++) x += w[i] * (bits[off + i] == 1 ? 1 : -1);
        return x;
    }
    private static double strength(int[] x) {
        double[] w = {1.1, 0.9, 0.7, 0.55}; double s = 0;
        for (int i = 0; i < 4; i++) s += w[i] * (x[i] == 1 ? 1 : -1);
        return s;
    }
    private static int singles(int[] x) { int n = 0; for (int v : x) n += v; return n; }
    private static void combineBits(int[] a, int[] b, int[] out) { for (int i = 0; i < 4; i++) out[i] = a[i] ^ b[i]; }
    private static String fig(int[] x) {
        StringBuilder b = new StringBuilder(); for (int v : x) b.append(v == 1 ? '1' : '2'); return b.toString();
    }
    private static String bits(int[] x) {
        StringBuilder b = new StringBuilder(); for (int v : x) b.append(v); return b.toString();
    }
    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    public static String debug(CastState s) {
        return String.format(Locale.CHINA, "seed=%d %s %s", s.seed, s.combined.direction, s.combined.scoreCandidates);
    }
}
