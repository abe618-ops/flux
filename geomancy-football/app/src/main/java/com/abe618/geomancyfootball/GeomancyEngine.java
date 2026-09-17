package com.abe618.geomancyfootball;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Frozen v0.1 geomancy core.
 * 1 = one dot / odd / active; 0 = two dots / even / passive.
 * Daughters are transposed from Mothers; all later figures are parity/XOR derivations.
 */
public final class GeomancyEngine {
    private GeomancyEngine() {}

    public static final int M1 = 0, M2 = 1, M3 = 2, M4 = 3;
    public static final int D1 = 4, D2 = 5, D3 = 6, D4 = 7;
    public static final int N1 = 8, N2 = 9, N3 = 10, N4 = 11;
    public static final int RIGHT_WITNESS = 12, LEFT_WITNESS = 13, JUDGE = 14, SENTENCE = 15;

    public static final class State {
        public final long seed;
        public final int[][] figures;
        public final Result result;
        State(long seed, int[][] figures, Result result) {
            this.seed = seed;
            this.figures = figures;
            this.result = result;
        }
    }

    public static final class Result {
        public final String direction;
        public final String halfFull;
        public final int totalGoals;
        public final String overUnder;
        public final String oddEven;
        public final String btts;
        public final String primaryScore;
        public final String scoreCandidates;
        public final int tendency;
        public final String votes;
        public final double homeIndex;
        public final double awayIndex;

        Result(String direction, String halfFull, int totalGoals, String overUnder,
               String oddEven, String btts, String primaryScore, String scoreCandidates,
               int tendency, String votes, double homeIndex, double awayIndex) {
            this.direction = direction;
            this.halfFull = halfFull;
            this.totalGoals = totalGoals;
            this.overUnder = overUnder;
            this.oddEven = oddEven;
            this.btts = btts;
            this.primaryScore = primaryScore;
            this.scoreCandidates = scoreCandidates;
            this.tendency = tendency;
            this.votes = votes;
            this.homeIndex = homeIndex;
            this.awayIndex = awayIndex;
        }
    }

    public static State castRandom() {
        return cast(new SecureRandom().nextLong());
    }

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

    public static State cast(long seed) {
        Random r = new Random(seed);
        int[][] f = new int[16][4];

        // Four Mothers are the only independently cast figures.
        for (int i = 0; i < 4; i++)
            for (int row = 0; row < 4; row++)
                f[i][row] = r.nextBoolean() ? 1 : 0;

        // Daughters: transpose rows of Mothers.
        for (int d = 0; d < 4; d++)
            for (int row = 0; row < 4; row++)
                f[4 + d][row] = f[row][d];

        // Nieces, Witnesses, Judge, Sentence: traditional parity combination.
        combine(f[M1], f[M2], f[N1]);
        combine(f[M3], f[M4], f[N2]);
        combine(f[D1], f[D2], f[N3]);
        combine(f[D3], f[D4], f[N4]);
        combine(f[N1], f[N2], f[RIGHT_WITNESS]);
        combine(f[N3], f[N4], f[LEFT_WITNESS]);
        combine(f[RIGHT_WITNESS], f[LEFT_WITNESS], f[JUDGE]);
        combine(f[JUDGE], f[M1], f[SENTENCE]);

        return new State(seed, f, predict(f));
    }

    private static void combine(int[] a, int[] b, int[] out) {
        for (int i = 0; i < 4; i++) out[i] = a[i] ^ b[i];
    }

    private static double strength(int[] x) {
        double[] w = {1.15, 0.95, 0.75, 0.60};
        double s = 0;
        for (int i = 0; i < 4; i++) s += w[i] * (x[i] == 1 ? 1 : -1);
        int changes = 0;
        for (int i = 1; i < 4; i++) if (x[i] != x[i - 1]) changes++;
        s += (changes - 1.5) * 0.22;
        return s;
    }

    private static double topStrength(int[] x) {
        return 0.65 * (x[0] == 1 ? 1 : -1) + 0.35 * (x[1] == 1 ? 1 : -1);
    }

    private static int singles(int[] x) {
        int n = 0;
        for (int v : x) n += v;
        return n;
    }

    private static int dots(int[] x) {
        int n = 0;
        for (int v : x) n += (v == 1 ? 1 : 2);
        return n;
    }

    private static double affinity(int[] a, int[] b) {
        int same = 0;
        for (int i = 0; i < 4; i++) if (a[i] == b[i]) same++;
        return same - 2.0;
    }

    private static int sign(double d, double deadZone) {
        if (d > deadZone) return 1;
        if (d < -deadZone) return -1;
        return 0;
    }

    private static String label(int dir) {
        return dir > 0 ? "主胜" : dir < 0 ? "客胜" : "平局";
    }

    private static Result predict(int[][] f) {
        // Frozen football mapping:
        // home: H1 + Right Witness + H10 + H5
        // away: H7 + Left Witness + H4 + H11
        // Judge/Sentence are terminal and overall-trend modifiers.
        double home = 1.35 * strength(f[0])
                + 0.95 * strength(f[RIGHT_WITNESS])
                + 0.65 * strength(f[9])
                + 0.65 * strength(f[4])
                + 0.35 * affinity(f[JUDGE], f[0])
                + 0.20 * affinity(f[SENTENCE], f[0]);
        double away = 1.35 * strength(f[6])
                + 0.95 * strength(f[LEFT_WITNESS])
                + 0.65 * strength(f[3])
                + 0.65 * strength(f[10])
                + 0.35 * affinity(f[JUDGE], f[6])
                + 0.20 * affinity(f[SENTENCE], f[6]);

        double margin = home - away;
        int fullDir = sign(margin, 1.15);

        double halfMargin = 0.95 * (strength(f[0]) - strength(f[6]))
                + 0.70 * (topStrength(f[RIGHT_WITNESS]) - topStrength(f[LEFT_WITNESS]))
                + 0.35 * (topStrength(f[4]) - topStrength(f[10]));
        int halfDir = sign(halfMargin, 0.78);

        int attack = singles(f[4]) + singles(f[10]);
        int court = singles(f[RIGHT_WITNESS]) + singles(f[LEFT_WITNESS])
                + singles(f[JUDGE]) + singles(f[SENTENCE]);
        int axis = singles(f[0]) + singles(f[6]) + singles(f[9]) + singles(f[3]);
        double rawGoals = 0.30 * attack + 0.12 * court + 0.07 * axis + 0.08 * Math.abs(margin);
        int total = clamp((int) Math.round(rawGoals), 0, 6);

        // Yin/yang parity vote. Then keep logical consistency with football outcome.
        int parity = (dots(f[JUDGE]) + dots(f[SENTENCE]) + dots(f[4]) + dots(f[10])) & 1;
        if (fullDir == 0) {
            if ((total & 1) == 1) total = total < 6 ? total + 1 : total - 1;
        } else {
            if (total == 0) total = 1;
            if ((total & 1) != parity) {
                if (total < 6) total++;
                else total--;
                if (total == 0) total = 1;
            }
        }

        int[] score = distribute(total, fullDir, margin);
        String primary = score[0] + ":" + score[1];
        String candidates = candidateScores(total, fullDir, margin, primary);
        String htft = labelShort(halfDir) + "/" + labelShort(fullDir);

        int v1 = sign(strength(f[0]) - strength(f[6]), 0.20);
        int v2 = sign(strength(f[RIGHT_WITNESS]) - strength(f[LEFT_WITNESS]), 0.20);
        int v3 = sign(strength(f[9]) - strength(f[3]), 0.20);
        int v4 = sign(strength(f[4]) - strength(f[10]), 0.20);
        int agree = 0;
        for (int v : new int[]{v1, v2, v3, v4}) if (v == fullDir) agree++;
        int tendency = clamp((int) Math.round(50 + Math.min(18, Math.abs(margin) * 2.2) + agree * 3.5), 50, 82);

        String votes = "1/7宫 " + label(v1) + " · 见证 " + label(v2)
                + " · 10/4宫 " + label(v3) + " · 5/11宫 " + label(v4);

        return new Result(
                label(fullDir), htft, total,
                total >= 3 ? "大2.5" : "小2.5",
                (total & 1) == 1 ? "单" : "双",
                score[0] > 0 && score[1] > 0 ? "双方进球：是" : "双方进球：否",
                primary, candidates, tendency, votes, home, away
        );
    }

    private static String labelShort(int dir) {
        return dir > 0 ? "主" : dir < 0 ? "客" : "平";
    }

    private static int[] distribute(int total, int dir, double margin) {
        if (dir == 0) {
            int g = total / 2;
            return new int[]{g, g};
        }
        if (total <= 0) return dir > 0 ? new int[]{1, 0} : new int[]{0, 1};
        double edge = Math.min(0.22, 0.06 + Math.abs(margin) / 45.0);
        double homeShare = dir > 0 ? 0.5 + edge : 0.5 - edge;
        int h = clamp((int) Math.round(total * homeShare), 0, total);
        int a = total - h;
        if (dir > 0 && h <= a) { h = Math.min(total, a + 1); a = total - h; }
        if (dir < 0 && a <= h) { a = Math.min(total, h + 1); h = total - a; }
        return new int[]{h, a};
    }

    private static String candidateScores(int total, int dir, double margin, String primary) {
        List<String> out = new ArrayList<>();
        out.add(primary);
        int[] deltas = {-1, 1, 2, -2};
        for (int d : deltas) {
            int t = clamp(total + d, 0, 7);
            if (dir == 0 && (t & 1) == 1) continue;
            if (dir != 0 && t == 0) continue;
            int[] s = distribute(t, dir, margin);
            String x = s[0] + ":" + s[1];
            if (!out.contains(x)) out.add(x);
            if (out.size() == 3) break;
        }
        return String.join("、", out);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static String figureCode(int[] f) {
        StringBuilder b = new StringBuilder();
        for (int v : f) b.append(v == 1 ? '1' : '2');
        return b.toString();
    }

    public static String debugSummary(State s) {
        return String.format(Locale.CHINA, "seed=%d home=%.2f away=%.2f", s.seed, s.result.homeIndex, s.result.awayIndex);
    }
}
