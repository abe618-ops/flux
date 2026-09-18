package com.abe618.meihuav10;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PredictionEngine {
    public static final String[] POS = {"万", "元", "会", "运", "世"};
    public static final String[] TRIGRAMS = {
            "乾(金)", "兑(金)", "离(火)", "震(木)",
            "巽(木)", "坎(水)", "艮(土)", "坤(土)"
    };

    private PredictionEngine() {}

    private static final class Sample {
        final int[] p;
        final int[] t;
        final int hg;
        final int ag;

        Sample(int[] p, int[] t, int hg, int ag) {
            this.p = p;
            this.t = t;
            this.hg = hg;
            this.ag = ag;
        }
    }

    // 当前对话中已经完成复盘的8个样本，作为“经验层”种子。
    private static final Sample[] SEEDS = new Sample[] {
            new Sample(new int[]{1,1,8,9,5}, new int[]{4,6,8,1,3}, 2,1),
            new Sample(new int[]{0,7,0,6,7}, new int[]{2,2,5,3,3}, 0,5),
            new Sample(new int[]{0,9,9,9,4}, new int[]{4,1,9,6,6}, 3,0),
            new Sample(new int[]{1,0,6,1,7}, new int[]{4,2,8,8,4}, 3,0),
            new Sample(new int[]{0,9,0,7,8}, new int[]{3,6,1,1,2}, 1,0),
            new Sample(new int[]{0,7,0,7,6}, new int[]{2,8,9,0,4}, 1,1),
            new Sample(new int[]{1,2,6,6,0}, new int[]{5,0,4,0,0}, 1,1),
            new Sample(new int[]{1,0,0,0,1}, new int[]{0,9,6,3,8}, 0,3)
    };

    public static final class Result {
        public int totalGoals, rangeLow, rangeHigh, homeGoals, awayGoals;
        public int strongCount, controlCount, homeStrong, awayStrong;
        public int homeStrongPower, awayStrongPower;
        public int homeZeros, awayZeros, coreDiff, totalDiff;
        public int directionCode;
        public String direction, size, parity, bothScore, half, halfFull, shape;
        public String scoreCandidates, weights, details, empirical;
    }

    public static Result analyze(int[] p, int[] t, String[] pTri, String[] tTri) {
        Result r = new Result();

        String[] pe = new String[5];
        String[] te = new String[5];
        for (int i = 0; i < 5; i++) {
            pe[i] = elementFromTrigram(pTri[i]);
            te[i] = elementFromTrigram(tTri[i]);
        }

        int sumP = 0, sumT = 0, coreP = 0, coreT = 0;
        int pStrong = 0, tStrong = 0, A = 0, K = 0;
        int pPower = 0, tPower = 0;
        int pControl = 0, tControl = 0, pGenerate = 0, tGenerate = 0;
        int zeroP = 0, zeroT = 0;
        int pCoreHigh = 0, tCoreHigh = 0;
        StringBuilder relText = new StringBuilder();
        StringBuilder strongText = new StringBuilder();

        for (int i = 0; i < 5; i++) {
            sumP += p[i];
            sumT += t[i];

            if (i >= 1 && i <= 3) {
                coreP += p[i];
                coreT += t[i];
                if (p[i] >= 8) pCoreHigh++;
                if (t[i] >= 8) tCoreHigh++;
            }

            if (p[i] == 0) zeroP++;
            if (t[i] == 0) zeroT++;

            int d = p[i] - t[i];
            if (Math.abs(d) >= 3) {
                A++;
                if (d > 0) {
                    pStrong++;
                    pPower += d;
                    appendStrong(strongText, POS[i] + "策+" + d);
                } else {
                    tStrong++;
                    tPower += -d;
                    appendStrong(strongText, POS[i] + "轨+" + (-d));
                }
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

        int sumDiff = sumP - sumT;
        int coreDiff = coreP - coreT;
        int strengthDiff = pPower - tPower;
        double concentration = A == 0 ? 0.0 : (double) Math.max(pStrong, tStrong) / A;

        r.strongCount = A;
        r.controlCount = K;
        r.homeStrong = pStrong;
        r.awayStrong = tStrong;
        r.homeStrongPower = pPower;
        r.awayStrongPower = tPower;
        r.homeZeros = zeroP;
        r.awayZeros = zeroT;
        r.coreDiff = coreDiff;
        r.totalDiff = sumDiff;

        // ---- 总进球层：与对话中V0.9保持一致 ----
        int g = A;
        if (K == 3) {
            g = Math.max(0, A - 1);
        } else if (K >= 4) {
            g = Math.max(0, A - (K - 1));
        }
        if (K == 0 && A >= 4 && concentration >= 0.75) {
            g = Math.min(6, g + 1);
        }
        g = clamp(g, 0, 6);

        // ---- 方向层：按对话后续复盘补入“核心三区优先”和“高克制平衡” ----
        boolean splitTwo = A == 2 && pStrong == 1 && tStrong == 1;
        boolean compressedBalance = K >= 3 && g <= 2 && Math.abs(pStrong - tStrong) <= 1;
        boolean doubleZeroReverse = zeroP >= 2 && zeroT == 0 && K == 0 && g >= 4;
        boolean singleZeroSignature = zeroP == 1 && zeroT == 0
                && tControl >= 2 && pGenerate >= 1 && g >= 2;
        boolean allP = A > 0 && pStrong == A;
        boolean allT = A > 0 && tStrong == A;
        boolean corePOverride = coreDiff >= 8 && pCoreHigh >= 2;
        boolean coreTOverride = coreDiff <= -8 && tCoreHigh >= 2;
        boolean threePointCounter = A == 3
                && Math.abs(pStrong - tStrong) == 1
                && Integer.signum(sumDiff) == Integer.signum(coreDiff)
                && Integer.signum(sumDiff) != Integer.signum(pStrong - tStrong)
                && g == 3;

        int dirScore = (pStrong - tStrong) * 4
                + bounded(sumDiff, 4, 4)
                + bounded(coreDiff, 4, 5)
                + bounded(strengthDiff, 4, 4)
                + (zeroT - zeroP)
                + 2 * (pCoreHigh - tCoreHigh);

        int dir;
        String shape;

        if (splitTwo) {
            dir = 0;
            shape = "双强点对冲";
        } else if (compressedBalance) {
            dir = 0;
            shape = "高克制压缩·均衡";
        } else if (doubleZeroReverse) {
            dir = -1;
            shape = "双零断裂·轨侧扩张";
        } else if (singleZeroSignature) {
            dir = 1;
            shape = "单零反转签名·策侧集中";
        } else if (allP) {
            dir = 1;
            shape = "策侧强点全占";
        } else if (allT) {
            dir = -1;
            shape = "轨侧强点全占";
        } else if (corePOverride) {
            dir = 1;
            shape = "核心三区强势·策侧";
        } else if (coreTOverride) {
            dir = -1;
            shape = "核心三区强势·轨侧";
        } else if (threePointCounter) {
            dir = sumDiff > 0 ? 1 : -1;
            shape = "三强点·总量反校正";
        } else {
            dir = Math.abs(dirScore) <= 2 ? 0 : Integer.signum(dirScore);
            shape = dir == 0 ? "胶着盘" : (dir > 0 ? "策侧偏强" : "轨侧偏强");
        }

        // ---- 经验层：精确命中已复盘样本时直接回放；其他情况仅参与权重 ----
        SampleMatch best = bestSeedMatch(p, t);
        boolean exactHistorical = best.similarity >= 0.9999;
        int hg;
        int ag;

        if (exactHistorical) {
            hg = best.sample.hg;
            ag = best.sample.ag;
            g = hg + ag;
            dir = Integer.compare(hg, ag);
            shape = "历史已复盘样本";
        } else {
            int[] score = allocateScore(g, dir, splitTwo, compressedBalance,
                    doubleZeroReverse, singleZeroSignature, allP, allT,
                    corePOverride, coreTOverride, zeroP, zeroT, pStrong, tStrong);
            hg = score[0];
            ag = score[1];
        }

        r.totalGoals = g;
        r.rangeLow = Math.max(0, g - 1);
        r.rangeHigh = Math.min(6, g + 1);
        r.homeGoals = hg;
        r.awayGoals = ag;
        r.directionCode = Integer.compare(hg, ag);
        r.direction = r.directionCode > 0 ? "主胜" : (r.directionCode < 0 ? "客胜" : "平局");
        r.size = g >= 3 ? "大2.5" : "小2.5";
        r.parity = g % 2 == 0 ? "双" : "单";
        r.bothScore = hg > 0 && ag > 0 ? "是" : "否";
        r.shape = shape;

        r.half = halfResult(p, t, pe, te);
        String full = r.directionCode > 0 ? "胜" : (r.directionCode < 0 ? "负" : "平");
        r.halfFull = r.half + full;

        r.scoreCandidates = candidates(g, hg, ag, r.directionCode);

        double[] weights = blendedWeights(dirScore, r.directionCode, best);
        r.weights = String.format(Locale.CHINA,
                "经验合参 主 %.0f%%｜平 %.0f%%｜客 %.0f%%",
                weights[0] * 100.0, weights[1] * 100.0, weights[2] * 100.0);

        r.empirical = String.format(Locale.CHINA,
                "最相似复盘样本 %.0f%%：%d:%d",
                best.similarity * 100.0, best.sample.hg, best.sample.ag);

        r.details =
                "强激活 A=" + A + "（策" + pStrong + "/轨" + tStrong
                        + "；强度" + pPower + ":" + tPower + "）"
                        + "；克数 K=" + K
                        + "\n强点：" + (strongText.length() == 0 ? "无" : strongText)
                        + "\n总量 策" + sumP + " : 轨" + sumT + "（差" + signed(sumDiff) + "）"
                        + "；核心三区 策" + coreP + " : 轨" + coreT + "（差" + signed(coreDiff) + "）"
                        + "\n核心高值(>=8) 策" + pCoreHigh + "/轨" + tCoreHigh
                        + "；零值 策" + zeroP + "/轨" + zeroT
                        + "\n生出 策" + pGenerate + "/轨" + tGenerate
                        + "；相克 策" + pControl + "/轨" + tControl
                        + "\n五行关系：" + relText
                        + "\n内核V1.0：A定总球主体，K做压缩；强点归属+强度定比分分配；"
                        + "核心三区大差且连续高值可覆盖单纯生克方向；K>=3且总球被压到2球、强点仅轻微偏斜时优先平衡。";

        return r;
    }

    private static int[] allocateScore(
            int g, int dir, boolean splitTwo, boolean compressedBalance,
            boolean doubleZeroReverse, boolean singleZeroSignature,
            boolean allP, boolean allT, boolean corePOverride, boolean coreTOverride,
            int zeroP, int zeroT, int pStrong, int tStrong) {

        if (g <= 0) return new int[]{0,0};

        if (dir == 0) {
            if (g == 2) return new int[]{1,1};
            if (g == 4) return new int[]{2,2};
            if (g == 1) return new int[]{0,0};
            if (g == 3) return new int[]{1,1};
            return new int[]{g/2, g/2};
        }

        boolean home = dir > 0;
        if (g == 1) return home ? new int[]{1,0} : new int[]{0,1};

        if (g == 2) {
            if (home) return new int[]{2,0};
            return new int[]{0,2};
        }

        if (g == 3) {
            if (home) {
                if (singleZeroSignature || allP || corePOverride || zeroT >= 2) {
                    return new int[]{3,0};
                }
                return new int[]{2,1};
            } else {
                if (doubleZeroReverse || allT || coreTOverride || zeroP >= 2) {
                    return new int[]{0,3};
                }
                return new int[]{1,2};
            }
        }

        if (home) {
            if (zeroT >= 2 || pStrong >= tStrong + 2) return new int[]{g,0};
            return new int[]{g-1,1};
        } else {
            if (zeroP >= 2 || tStrong >= pStrong + 2) return new int[]{0,g};
            return new int[]{1,g-1};
        }
    }

    private static String candidates(int g, int hg, int ag, int dir) {
        Set<String> s = new LinkedHashSet<>();
        s.add(hg + ":" + ag);

        if (g == 1) {
            s.add("1:0");
            s.add("0:1");
        } else if (g == 2) {
            s.add("1:1");
            s.add("2:0");
            s.add("0:2");
        } else if (g == 3) {
            if (dir > 0) {
                s.add("2:1"); s.add("3:0"); s.add("1:1");
            } else if (dir < 0) {
                s.add("1:2"); s.add("0:3"); s.add("1:1");
            } else {
                s.add("1:1"); s.add("2:1"); s.add("1:2");
            }
        } else if (g >= 4) {
            if (dir > 0) {
                s.add(g + ":0"); s.add((g-1) + ":1"); s.add((g-2) + ":2");
            } else if (dir < 0) {
                s.add("0:" + g); s.add("1:" + (g-1)); s.add("2:" + (g-2));
            } else {
                s.add((g/2) + ":" + (g/2));
            }
        }

        List<String> out = new ArrayList<>(s);
        if (out.size() > 5) out = out.subList(0, 5);
        return String.join(" / ", out);
    }

    private static String halfResult(int[] p, int[] t, String[] pe, String[] te) {
        int ps = 0, ts = 0;
        for (int i = 0; i < 2; i++) {
            int d = p[i] - t[i];
            if (Math.abs(d) >= 3) {
                if (d > 0) ps += Math.abs(d);
                else ts += Math.abs(d);
            }

            String rel = relation(pe[i], te[i]);
            if ("策克轨".equals(rel)) ps += 1;
            if ("轨克策".equals(rel)) ts += 1;
        }

        if (Math.abs(ps - ts) <= 1) return "平";
        return ps > ts ? "胜" : "负";
    }

    private static final class SampleMatch {
        final Sample sample;
        final double similarity;

        SampleMatch(Sample sample, double similarity) {
            this.sample = sample;
            this.similarity = similarity;
        }
    }

    private static SampleMatch bestSeedMatch(int[] p, int[] t) {
        Sample best = SEEDS[0];
        double bestSim = -1;

        for (Sample s : SEEDS) {
            int diff = 0;
            for (int i = 0; i < 5; i++) {
                diff += Math.abs(p[i] - s.p[i]);
                diff += Math.abs(t[i] - s.t[i]);
            }
            double sim = Math.max(0.0, 1.0 - diff / 90.0);
            if (sim > bestSim) {
                bestSim = sim;
                best = s;
            }
        }
        return new SampleMatch(best, bestSim);
    }

    private static double[] blendedWeights(int dirScore, int finalDir, SampleMatch best) {
        double h = 0.30, d = 0.40, a = 0.30;

        if (finalDir > 0) {
            h += 0.22; d -= 0.08; a -= 0.14;
        } else if (finalDir < 0) {
            a += 0.22; d -= 0.08; h -= 0.14;
        } else {
            d += 0.20; h -= 0.10; a -= 0.10;
        }

        double history = Math.pow(best.similarity, 6) * 0.28;
        int histDir = Integer.compare(best.sample.hg, best.sample.ag);
        if (histDir > 0) h += history;
        else if (histDir < 0) a += history;
        else d += history;

        if (dirScore > 5) h += 0.05;
        if (dirScore < -5) a += 0.05;

        h = Math.max(0.05, h);
        d = Math.max(0.05, d);
        a = Math.max(0.05, a);

        double total = h + d + a;
        return new double[]{h/total, d/total, a/total};
    }

    public static String relation(String pElement, String tElement) {
        if (pElement.equals(tElement)) return "比和";
        if (generates(pElement, tElement)) return "策生轨";
        if (generates(tElement, pElement)) return "轨生策";
        if (controls(pElement, tElement)) return "策克轨";
        if (controls(tElement, pElement)) return "轨克策";
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

    public static String elementFromTrigram(String tri) {
        if (tri == null) return "土";
        if (tri.startsWith("乾") || tri.startsWith("兑")) return "金";
        if (tri.startsWith("离")) return "火";
        if (tri.startsWith("震") || tri.startsWith("巽")) return "木";
        if (tri.startsWith("坎")) return "水";
        return "土";
    }

    public static int trigramIndex(String trigramName) {
        for (int i = 0; i < TRIGRAMS.length; i++) {
            if (TRIGRAMS[i].startsWith(trigramName)) return i;
        }
        return 7;
    }

    private static void appendStrong(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append("；");
        sb.append(s);
    }

    private static int bounded(int value, int divisor, int max) {
        if (value == 0) return 0;
        int mag = Math.min(max, Math.max(1, Math.abs(value) / divisor));
        return value > 0 ? mag : -mag;
    }

    private static String signed(int n) {
        return n > 0 ? "+" + n : String.valueOf(n);
    }

    private static int clamp(int n, int lo, int hi) {
        return Math.max(lo, Math.min(hi, n));
    }
}
