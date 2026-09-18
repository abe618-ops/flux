package com.abe618.meihuav09;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PredictionEngine {
    public static final String[] POS = {"万", "元", "会", "运", "世"};
    public static final String[] ELEMENTS = {"木", "火", "土", "金", "水"};

    private PredictionEngine() {}

    public static final class Result {
        public int totalGoals, rangeLow, rangeHigh, homeGoals, awayGoals;
        public int strongCount, controlCount, homeStrong, awayStrong, homeZeros, awayZeros;
        public int directionCode;
        public String direction, size, parity, bothScore, half, halfFull, shape;
        public String scoreCandidates, weights, details;
    }

    public static Result analyze(int[] p, int[] t, String[] pe, String[] te) {
        Result r = new Result();
        int sumP = 0, sumT = 0, coreP = 0, coreT = 0;
        int pStrong = 0, tStrong = 0, A = 0, K = 0;
        int pControl = 0, tControl = 0, pGenerate = 0, tGenerate = 0;
        int zeroP = 0, zeroT = 0;
        StringBuilder relText = new StringBuilder();

        for (int i = 0; i < 5; i++) {
            sumP += p[i];
            sumT += t[i];
            if (i >= 1 && i <= 3) {
                coreP += p[i];
                coreT += t[i];
            }
            if (p[i] == 0) zeroP++;
            if (t[i] == 0) zeroT++;

            int d = p[i] - t[i];
            if (Math.abs(d) >= 3) {
                A++;
                if (d > 0) pStrong++;
                else tStrong++;
            }

            String rel = relation(pe[i], te[i]);
            if (rel.contains("克")) {
                K++;
                if (rel.startsWith("策")) pControl++;
                else tControl++;
            }
            if (rel.contains("生")) {
                if (rel.startsWith("策")) pGenerate++;
                else tGenerate++;
            }
            if (i > 0) relText.append("；");
            relText.append(POS[i]).append("：").append(rel);
        }

        r.strongCount = A;
        r.controlCount = K;
        r.homeStrong = pStrong;
        r.awayStrong = tStrong;
        r.homeZeros = zeroP;
        r.awayZeros = zeroT;

        int g = A;
        double concentration = A == 0 ? 0.0 : (double)Math.max(pStrong, tStrong) / A;
        if (K == 3) {
            g = Math.max(0, A - 1);
        } else if (K >= 4) {
            g = Math.max(0, A - (K - 1));
        }
        if (K == 0 && A >= 4 && concentration >= 0.75) {
            g = Math.min(6, g + 1);
        }
        g = clamp(g, 0, 6);

        int sumDiff = sumP - sumT;
        int coreDiff = coreP - coreT;
        int dir = (pStrong - tStrong) * 4
                + boundedSignal(sumDiff, 4, 3)
                + boundedSignal(coreDiff, 4, 3)
                + (zeroT - zeroP)
                + (pControl - tControl)
                + (pGenerate - tGenerate);

        boolean splitTwo = A == 2 && pStrong == 1 && tStrong == 1;
        boolean doubleZeroReverse = zeroP >= 2 && zeroT == 0 && K == 0 && g >= 4;
        boolean singleZeroSignature = zeroP == 1 && zeroT == 0
                && tControl >= 2 && pGenerate >= 1 && g >= 2;

        int hg;
        int ag;
        String shape;

        if (g == 0) {
            hg = 0; ag = 0; shape = "低事件僵持";
        } else if (splitTwo) {
            hg = 1; ag = 1; shape = "双强点对冲";
        } else if (doubleZeroReverse) {
            hg = 0; ag = g; shape = "双零断裂·轨侧扩张";
            dir = -8;
        } else if (singleZeroSignature) {
            hg = g; ag = 0; shape = "单零反转签名·策侧集中";
            dir = 8;
        } else if (A > 0 && pStrong == A) {
            hg = g; ag = 0; shape = "策侧强点全占";
            dir = Math.max(dir, 8);
        } else if (A > 0 && tStrong == A) {
            hg = 0; ag = g; shape = "轨侧强点全占";
            dir = Math.min(dir, -8);
        } else if (A == 3 && Math.abs(pStrong - tStrong) == 1
                && Integer.signum(sumDiff) == Integer.signum(coreDiff)
                && Integer.signum(sumDiff) != Integer.signum(pStrong - tStrong)
                && g == 3) {
            if (sumDiff > 0) { hg = 2; ag = 1; dir = 3; }
            else { hg = 1; ag = 2; dir = -3; }
            shape = "三强点·总量反校正";
        } else {
            if (g == 1) {
                if (dir >= 0) { hg = 1; ag = 0; }
                else { hg = 0; ag = 1; }
            } else {
                double ratio = 0.5 + Math.max(-0.35, Math.min(0.35, dir * 0.045));
                hg = clamp((int)Math.round(g * ratio), 0, g);
                ag = g - hg;
                if (zeroP >= 3 && tStrong > pStrong) { hg = 0; ag = g; }
                if (zeroT >= 3 && pStrong > tStrong) { hg = g; ag = 0; }
            }
            shape = Math.abs(dir) <= 2 ? "胶着盘" : (dir > 0 ? "策侧偏强" : "轨侧偏强");
        }

        r.totalGoals = g;
        r.rangeLow = Math.max(0, g - 1);
        r.rangeHigh = Math.min(6, g + 1);
        r.homeGoals = hg;
        r.awayGoals = ag;
        r.directionCode = Integer.compare(hg, ag);
        r.direction = r.directionCode > 0 ? "主胜" : (r.directionCode < 0 ? "客胜" : "平局");
        r.size = g >= 3 ? "大2.5" : "小2.5";
        r.parity = (g % 2 == 0) ? "双" : "单";
        r.bothScore = (hg > 0 && ag > 0) ? "是" : "否";
        r.shape = shape;

        String half = halfResult(p, t);
        r.half = half;
        String full = r.directionCode > 0 ? "胜" : (r.directionCode < 0 ? "负" : "平");
        r.halfFull = half + full;

        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(hg + ":" + ag);
        if (g >= 1) {
            if (hg > 0) candidates.add((hg - 1) + ":" + (ag + 1));
            if (ag > 0) candidates.add((hg + 1) + ":" + (ag - 1));
        }
        if (g == 2) candidates.add("1:1");
        if (g == 3) {
            candidates.add("2:1");
            candidates.add("1:2");
            candidates.add("3:0");
            candidates.add("0:3");
        }
        if (g >= 4) {
            if (r.directionCode > 0) {
                candidates.add((g - 1) + ":1");
                candidates.add(g + ":0");
            } else if (r.directionCode < 0) {
                candidates.add("1:" + (g - 1));
                candidates.add("0:" + g);
            }
        }
        List<String> top = new ArrayList<>(candidates);
        if (top.size() > 6) top = top.subList(0, 6);
        r.scoreCandidates = String.join(" / ", top);

        double homeW = 33 + dir * 2.7 + (r.directionCode > 0 ? 10 : 0);
        double awayW = 33 - dir * 2.7 + (r.directionCode < 0 ? 10 : 0);
        double drawW = 34 - Math.min(18, Math.abs(dir) * 1.6) + (r.directionCode == 0 ? 10 : 0);
        homeW = Math.max(6, homeW);
        awayW = Math.max(6, awayW);
        drawW = Math.max(8, drawW);
        double totalW = homeW + drawW + awayW;
        homeW = homeW * 100.0 / totalW;
        drawW = drawW * 100.0 / totalW;
        awayW = awayW * 100.0 / totalW;
        r.weights = String.format(Locale.CHINA,
                "模型权重 主 %.0f%%｜平 %.0f%%｜客 %.0f%%",
                homeW, drawW, awayW);

        r.details =
                "强激活 A=" + A + "（策" + pStrong + " / 轨" + tStrong + "）"
                + "；克数 K=" + K
                + "\n总量 策" + sumP + " : 轨" + sumT + "（差" + signed(sumDiff) + "）"
                + "；核心三区 策" + coreP + " : 轨" + coreT + "（差" + signed(coreDiff) + "）"
                + "\n零值 策" + zeroP + " / 轨" + zeroT
                + "；生出 策" + pGenerate + " / 轨" + tGenerate
                + "；相克 策" + pControl + " / 轨" + tControl
                + "\n五行关系：" + relText
                + "\nV0.9：A负责总球主体，K负责压缩；强点归属优先分配比分；零值、核心差、总量和生克作修正。";

        return r;
    }

    private static String halfResult(int[] p, int[] t) {
        int ps = 0, ts = 0;
        for (int i = 0; i < 2; i++) {
            int d = p[i] - t[i];
            if (Math.abs(d) >= 3) {
                if (d > 0) ps++; else ts++;
            }
        }
        if (ps > ts) return "胜";
        if (ts > ps) return "负";
        int dp = (p[0] + p[1]) - (t[0] + t[1]);
        if (Math.abs(dp) <= 2) return "平";
        return dp > 0 ? "胜" : "负";
    }

    public static String relation(String p, String t) {
        if (p.equals(t)) return "比和";
        if (generates(p, t)) return "策生轨";
        if (generates(t, p)) return "轨生策";
        if (controls(p, t)) return "策克轨";
        if (controls(t, p)) return "轨克策";
        return "无";
    }

    public static boolean generates(String a, String b) {
        return ("木".equals(a) && "火".equals(b))
                || ("火".equals(a) && "土".equals(b))
                || ("土".equals(a) && "金".equals(b))
                || ("金".equals(a) && "水".equals(b))
                || ("水".equals(a) && "木".equals(b));
    }

    public static boolean controls(String a, String b) {
        return ("木".equals(a) && "土".equals(b))
                || ("土".equals(a) && "水".equals(b))
                || ("水".equals(a) && "火".equals(b))
                || ("火".equals(a) && "金".equals(b))
                || ("金".equals(a) && "木".equals(b));
    }

    public static String trigramForNumber(int n) {
        int x = n % 8;
        if (x <= 0) x = 8;
        switch (x) {
            case 1: return "乾";
            case 2: return "兑";
            case 3: return "离";
            case 4: return "震";
            case 5: return "巽";
            case 6: return "坎";
            case 7: return "艮";
            default: return "坤";
        }
    }

    public static String elementForNumber(int n) {
        String g = trigramForNumber(n);
        if ("乾".equals(g) || "兑".equals(g)) return "金";
        if ("离".equals(g)) return "火";
        if ("震".equals(g) || "巽".equals(g)) return "木";
        if ("坎".equals(g)) return "水";
        return "土";
    }

    private static int boundedSignal(int value, int divisor, int max) {
        if (value == 0) return 0;
        int magnitude = Math.min(max, Math.max(1, Math.abs(value) / divisor));
        return value > 0 ? magnitude : -magnitude;
    }

    private static String signed(int n) {
        return n > 0 ? "+" + n : String.valueOf(n);
    }

    private static int clamp(int n, int lo, int hi) {
        return Math.max(lo, Math.min(hi, n));
    }
}
