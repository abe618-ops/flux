package com.abe618.fourshushurandom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Experimental football mapping layer for the four raw divination charts.
 * It never changes the original chart. The same seed always reproduces both
 * the raw chart and this interpretation.
 */
public final class FootballEngine {
    private FootballEngine() {}

    public static final class Pick {
        public final String name;
        public final String direction;
        public final String rank;
        public final String halfFull;
        public final int goals;
        public final String goalRange;
        public final String overUnder;
        public final String oddEven;
        public final String primaryScore;
        public final String secondaryScores;
        public final int strength;
        public final double axis;
        public final String logic;

        Pick(String name, String direction, String rank, String halfFull, int goals,
             String goalRange, String overUnder, String oddEven, String primaryScore,
             String secondaryScores, int strength, double axis, String logic) {
            this.name = name;
            this.direction = direction;
            this.rank = rank;
            this.halfFull = halfFull;
            this.goals = goals;
            this.goalRange = goalRange;
            this.overUnder = overUnder;
            this.oddEven = oddEven;
            this.primaryScore = primaryScore;
            this.secondaryScores = secondaryScores;
            this.strength = strength;
            this.axis = axis;
            this.logic = logic;
        }

        public String text() {
            return "胜平负：" + rank
                    + "\n半全场：" + halfFull
                    + "\n总进球：" + goals + "球（" + goalRange + "）"
                    + "  ·  " + overUnder + "  ·  " + oddEven
                    + "\n比分：" + primaryScore + "  次选 " + secondaryScores
                    + "\n结构强度：" + strength + "/100"
                    + "\n映射：" + logic;
        }
    }

    public static final class Combined {
        public final String text;
        Combined(String text) { this.text = text; }
    }

    public static final class Result {
        public final Pick nine;
        public final Pick ling;
        public final Pick seven;
        public final Pick triangle;
        public final Combined combined;

        Result(Pick nine, Pick ling, Pick seven, Pick triangle, Combined combined) {
            this.nine = nine;
            this.ling = ling;
            this.seven = seven;
            this.triangle = triangle;
            this.combined = combined;
        }
    }

    public static Result analyze(ChartEngine.State state) {
        Pick nine = nine(state.nineYao.text);
        Pick ling = ling(state.lingQi.text);
        Pick seven = seven(state.sevenStar.text);
        Pick tri = triangle(state.triangle.text);
        return new Result(nine, ling, seven, tri, combine(nine, ling, seven, tri));
    }

    private static Pick nine(String raw) {
        String heaven = extractBetween(raw, "天卦：", "\n");
        String human = extractBetween(raw, "人卦：", "\n");
        String earth = extractBetween(raw, "地卦：", "\n");

        String hb = digitsAfter(heaven, "码 ");
        String mb = digitsAfter(human, "码 ");
        String eb = digitsAfter(earth, "码 ");
        if (hb.length() < 3) hb = "111";
        if (mb.length() < 3) mb = "101";
        if (eb.length() < 3) eb = "000";

        String all = eb.substring(0,3) + mb.substring(0,3) + hb.substring(0,3);
        double axis = 0;
        int inverse = 0;
        int trans = 0;
        for (int i = 0; i < 9; i++) {
            boolean yang = all.charAt(i) == '1';
            boolean yangPos = ((i + 1) & 1) == 1;
            if (yang != yangPos) inverse++;
            double w = (i >= 3 && i <= 5) ? 1.30 : 1.00;
            axis += (yang ? 1 : -1) * w;
            if (i > 0 && all.charAt(i) != all.charAt(i - 1)) trans++;
        }

        String dir = direction(axis, 1.45);
        int goals = clamp((int)Math.round(1.0 + trans * 0.34 + inverse * 0.13), 1, 5);
        goals = normalizeGoals(dir, goals);
        boolean fast = eb.charAt(0) != eb.charAt(1) || eb.charAt(1) != eb.charAt(2);
        int strength = clamp((int)Math.round(48 + Math.abs(axis) * 10 + Math.abs(4 - inverse) * 2), 43, 93);
        return pick("九爻易", dir, axis, goals, fast, strength,
                "阳势映射主方、阴势映射客方，人卦权重略高；九爻阴阳转换与逆位数映射比赛开放度。");
    }

    private static Pick ling(String raw) {
        int u = firstIntAfter(raw, "得 ", 0);
        int m = nthIntAfter(raw, "得 ", 2);
        int d = nthIntAfter(raw, "得 ", 3);
        if (m < 0) m = 2;
        if (d < 0) d = 2;

        double axis = 0.80 * lingValue(u) + 1.35 * lingValue(m) + 1.00 * lingValue(d);
        int old = old(u) + old(m) + old(d);
        int signs = signChanges(new double[]{lingValue(u), lingValue(m), lingValue(d)});
        int zeros = (u == 0 ? 1 : 0) + (m == 0 ? 1 : 0) + (d == 0 ? 1 : 0);

        String dir = direction(axis, 1.15);
        int goals = clamp(1 + old + signs + (zeros == 0 ? 1 : 0), 1, 5);
        goals = normalizeGoals(dir, goals);
        boolean fast = d >= 3 || m >= 3;
        int strength = clamp((int)Math.round(50 + Math.abs(axis) * 9 + old * 4 - zeros * 3), 42, 93);

        return pick("灵棋经", dir, axis, goals, fast, strength,
                "按一阳、二阴、三重阳、四重阴解释；阳偏主、阴偏客，中位代表人事对抗并加权，重象与上下冲突映射进球活跃度。");
    }

    private static Pick seven(String raw) {
        int red = firstIntAfter(raw, "红骰：", 3);
        int white = firstIntAfter(raw, "白骰：", 3);
        int[] up = rowNumbers(raw, "上组7牌：");
        int[] mid = rowNumbers(raw, "中组7牌：");
        int[] down = rowNumbers(raw, "下组7牌：");

        double upMean = mean(up), midMean = mean(mid), downMean = mean(down);
        double axis = (red - white) * 0.66 + (upMean - downMean) * 0.10 + (midMean - 11.0) * 0.07;
        double spread = std(up) + std(mid) + std(down);

        String dir = direction(axis, 1.25);
        int goals = clamp((int)Math.round(1.2 + spread / 8.0 + (red + white >= 8 ? 0.8 : 0.0)), 1, 5);
        goals = normalizeGoals(dir, goals);
        boolean fast = red + white >= 8 || midMean >= 12;
        int strength = clamp((int)Math.round(44 + Math.abs(axis) * 11 + Math.min(14, spread)), 38, 89);

        return pick("七星数", dir, axis, goals, fast, strength,
                "依据公开的“双骰取符、21牌、三组七牌、七个数字卦”框架建立实验层：红白骰差给主客初势，上中下三组牌的均值与离散度映射后续走势和进球。");
    }

    private static Pick triangle(String raw) {
        String tri = extractBetween(raw, "三数：", "\n").trim();
        String[] parts = tri.split("-");
        int h = parts.length > 0 ? parseInt(parts[0].trim(), 0) : 0;
        int e = parts.length > 1 ? parseInt(parts[1].trim(), 0) : 0;
        int p = parts.length > 2 ? parseInt(parts[2].trim(), 0) : 0;

        double home = support(h, p);
        double away = support(e, p);
        double axis = home - away + (yinYang(h) - yinYang(e)) * 0.18;

        int controls = 0;
        if (control(element(h), element(p)) || control(element(p), element(h))) controls++;
        if (control(element(e), element(p)) || control(element(p), element(e))) controls++;
        boolean same = element(h) != 0 && element(h) == element(e);

        String dir = direction(axis, 0.72);
        int goals = clamp(1 + controls + (Math.abs(h - e) >= 6 ? 1 : 0)
                + ((h + e + p) % 3 == 0 ? 1 : 0) - (same ? 1 : 0), 1, 5);
        goals = normalizeGoals(dir, goals);
        boolean fast = controls >= 1 || h + p >= 15;
        int strength = clamp((int)Math.round(50 + Math.abs(axis) * 15 + controls * 5 - (same ? 3 : 0)), 42, 94);

        return pick("万事三角定律", dir, axis, goals, fast, strength,
                "天时映射主方环境、地时映射客方环境、人时映射对抗核心；按0~12数字的阴阳五行生克判断双方受生、受克与波动。");
    }

    private static Combined combine(Pick nine, Pick ling, Pick seven, Pick tri) {
        Pick[] xs = {nine, ling, seven, tri};
        double[] weights = {1.00, 1.12, 0.82, 1.00};
        double home = 0, draw = 0, away = 0, goals = 0, totalW = 0;
        int odd = 0, fastHome = 0, fastAway = 0;

        for (int i = 0; i < xs.length; i++) {
            Pick p = xs[i];
            double w = weights[i] * (0.65 + p.strength / 100.0 * 0.35);
            if ("主胜".equals(p.direction)) home += w;
            else if ("客胜".equals(p.direction)) away += w;
            else draw += w;
            goals += p.goals * w;
            totalW += w;
            if ((p.goals & 1) == 1) odd++;
            if (p.halfFull.startsWith("主/主")) fastHome++;
            if (p.halfFull.startsWith("客/客")) fastAway++;
        }
        if (Math.abs(home - away) < 0.65) draw += 0.55;

        List<Rank> rs = new ArrayList<>();
        rs.add(new Rank("主胜", home));
        rs.add(new Rank("平局", draw));
        rs.add(new Rank("客胜", away));
        Collections.sort(rs, Comparator.comparingDouble((Rank r) -> r.score).reversed());

        String d1 = rs.get(0).name, d2 = rs.get(1).name, d3 = rs.get(2).name;
        int g = clamp((int)Math.round(goals / Math.max(0.1, totalW)), 1, 5);
        g = normalizeGoals(d1, g);
        String hf = "平局".equals(d1) ? "平/平"
                : "主胜".equals(d1) ? (fastHome >= 2 ? "主/主" : "平/主")
                : (fastAway >= 2 ? "客/客" : "平/客");

        int same = 0;
        for (Pick p : xs) if (d1.equals(p.direction)) same++;
        int agreement = clamp(40 + same * 12 + (int)Math.round((rs.get(0).score - rs.get(1).score) * 10), 35, 94);
        String oe = odd >= 3 ? "单" : odd <= 1 ? "双" : ((g & 1) == 1 ? "单（2:2分歧）" : "双（2:2分歧）");

        String text = d1 + " > " + d2 + " > " + d3
                + "\n半全场：" + hf
                + "\n总进球：" + g + "球（" + goalRange(g) + "）  ·  " + (g >= 3 ? "大2.5" : "小2.5") + "  ·  " + oe
                + "\n比分：" + score(d1, g)
                + "  次选 " + score(d2, normalizeGoals(d2, g))
                + "  冷防 " + score(d3, normalizeGoals(d3, Math.max(1, g - 1)))
                + "\n四术同向度：" + agreement + "/100"
                + "\n票面：主 " + fmt(home) + " / 平 " + fmt(draw) + " / 客 " + fmt(away)
                + "\n合参规则：灵棋经因公开结构较完整略加权；七星数公开规则残缺略降权；九爻易与三角定律居中。这里的权重表示规则完整度，不表示真实命中率。";
        return new Combined(text);
    }

    private static final class Rank {
        final String name; final double score;
        Rank(String n, double s) { name = n; score = s; }
    }

    private static Pick pick(String name, String dir, double axis, int goals, boolean fast,
                             int strength, String logic) {
        String rank = rank(dir, axis);
        return new Pick(
                name, dir, rank, halfFull(dir, fast), goals, goalRange(goals),
                goals >= 3 ? "大2.5" : "小2.5",
                (goals & 1) == 1 ? "单" : "双",
                score(dir, goals), secondary(dir, goals), strength, axis, logic
        );
    }

    private static String rank(String dir, double axis) {
        if ("主胜".equals(dir)) return "主胜 > 平局 > 客胜";
        if ("客胜".equals(dir)) return "客胜 > 平局 > 主胜";
        return axis >= 0 ? "平局 > 主胜 > 客胜" : "平局 > 客胜 > 主胜";
    }

    private static String halfFull(String dir, boolean fast) {
        if ("平局".equals(dir)) return "平/平（次防 平/主、平/客）";
        if ("主胜".equals(dir)) return fast ? "主/主（次防 平/主）" : "平/主（次防 主/主）";
        return fast ? "客/客（次防 平/客）" : "平/客（次防 客/客）";
    }

    private static String direction(double axis, double threshold) {
        if (axis > threshold) return "主胜";
        if (axis < -threshold) return "客胜";
        return "平局";
    }

    private static int normalizeGoals(String dir, int g) {
        g = clamp(g, 0, 6);
        if ("平局".equals(dir) && (g & 1) == 1) {
            if (g <= 1) g = 2;
            else if (g >= 5) g = 4;
            else g++;
        }
        if (!"平局".equals(dir) && g == 0) g = 1;
        return g;
    }

    private static String score(String dir, int g) {
        g = normalizeGoals(dir, g);
        if ("平局".equals(dir)) {
            int x = g / 2;
            return x + ":" + x;
        }
        int h, a;
        if (g == 1) { h = 1; a = 0; }
        else if (g == 2) { h = 2; a = 0; }
        else if (g == 3) { h = 2; a = 1; }
        else if (g == 4) { h = 3; a = 1; }
        else if (g == 5) { h = 3; a = 2; }
        else { h = 4; a = 2; }
        return "客胜".equals(dir) ? a + ":" + h : h + ":" + a;
    }

    private static String secondary(String dir, int g) {
        if ("平局".equals(dir)) return g <= 2 ? "0:0 / 1:1 / 2:2" : "1:1 / 2:2 / 0:0";
        int lo = normalizeGoals(dir, Math.max(1, g - 1));
        int hi = normalizeGoals(dir, Math.min(6, g + 1));
        return score(dir, lo) + " / " + score(dir, hi) + " / " + score("平局", g <= 2 ? 2 : 4);
    }

    private static String goalRange(int g) {
        return Math.max(0, g - 1) + "–" + Math.min(6, g + 1) + "球";
    }

    private static double lingValue(int n) {
        if (n == 1) return 1.0;
        if (n == 2) return -0.9;
        if (n == 3) return 1.85;
        if (n == 4) return -1.85;
        return 0;
    }

    private static int old(int n) { return (n == 3 || n == 4) ? 1 : 0; }

    private static int signChanges(double[] xs) {
        int n = 0;
        for (int i = 1; i < xs.length; i++) {
            int a = xs[i - 1] > 0 ? 1 : xs[i - 1] < 0 ? -1 : 0;
            int b = xs[i] > 0 ? 1 : xs[i] < 0 ? -1 : 0;
            if (a != 0 && b != 0 && a != b) n++;
        }
        return n;
    }

    private static int element(int n) {
        if (n == 1 || n == 2 || n == 12) return 1; // water
        if (n == 4 || n == 5) return 2;            // wood
        if (n == 6 || n == 7) return 3;            // fire
        if (n == 3 || n == 8 || n == 9) return 4; // earth
        if (n == 10 || n == 11) return 5;          // metal
        return 0;
    }

    private static int yinYang(int n) {
        if (n == 0) return 0;
        return (n == 1 || n == 2 || n == 3 || n == 4 || n == 11 || n == 12) ? 1 : -1;
    }

    private static boolean generate(int a, int b) {
        return (a == 1 && b == 2) || (a == 2 && b == 3) || (a == 3 && b == 4)
                || (a == 4 && b == 5) || (a == 5 && b == 1);
    }

    private static boolean control(int a, int b) {
        return (a == 1 && b == 3) || (a == 3 && b == 5) || (a == 5 && b == 2)
                || (a == 2 && b == 4) || (a == 4 && b == 1);
    }

    private static double support(int env, int human) {
        int a = element(env), b = element(human);
        if (a == 0 || b == 0) return 0;
        double mag = 0.55 + (Math.abs(env - human) % 5) * 0.13;
        if (a == b) return 0.65 * mag;
        if (generate(a, b)) return 1.35 * mag;
        if (generate(b, a)) return 0.72 * mag;
        if (control(a, b)) return -1.28 * mag;
        if (control(b, a)) return -0.82 * mag;
        return 0;
    }

    private static int[] rowNumbers(String raw, String key) {
        String row = extractBetween(raw, key, "\n");
        String[] parts = row.split("·");
        int[] x = new int[parts.length];
        for (int i = 0; i < parts.length; i++) x[i] = parseInt(parts[i].trim(), 11);
        return x;
    }

    private static double mean(int[] x) {
        if (x.length == 0) return 0;
        double s = 0;
        for (int v : x) s += v;
        return s / x.length;
    }

    private static double std(int[] x) {
        if (x.length == 0) return 0;
        double m = mean(x), s = 0;
        for (int v : x) s += (v - m) * (v - m);
        return Math.sqrt(s / x.length);
    }

    private static String extractBetween(String s, String start, String end) {
        int a = s.indexOf(start);
        if (a < 0) return "";
        a += start.length();
        int b = s.indexOf(end, a);
        if (b < 0) b = s.length();
        return s.substring(a, b);
    }

    private static String digitsAfter(String s, String key) {
        int p = s.indexOf(key);
        if (p < 0) return "";
        p += key.length();
        StringBuilder out = new StringBuilder();
        while (p < s.length()) {
            char c = s.charAt(p++);
            if (c == '0' || c == '1') out.append(c);
            else if (out.length() > 0) break;
        }
        return out.toString();
    }

    private static int firstIntAfter(String s, String key, int fallback) {
        int p = s.indexOf(key);
        if (p < 0) return fallback;
        p += key.length();
        while (p < s.length() && !Character.isDigit(s.charAt(p)) && s.charAt(p) != '-') p++;
        int q = p;
        if (q < s.length() && s.charAt(q) == '-') q++;
        while (q < s.length() && Character.isDigit(s.charAt(q))) q++;
        if (q <= p) return fallback;
        return parseInt(s.substring(p, q), fallback);
    }

    private static int nthIntAfter(String s, String key, int occurrence) {
        int from = 0;
        for (int i = 1; i <= occurrence; i++) {
            int p = s.indexOf(key, from);
            if (p < 0) return -1;
            if (i == occurrence) return firstIntAfter(s.substring(p), key, -1);
            from = p + key.length();
        }
        return -1;
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private static String fmt(double x) { return String.format(Locale.US, "%.2f", x); }
    private static int clamp(int x, int lo, int hi) { return Math.max(lo, Math.min(hi, x)); }
}
