package com.abe618.fourdivfootball;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * v0.2 analyst-style four-system football blind-test engine.
 * The four systems are cast independently. The football interpretation layer is experimental.
 */
public final class FourDivinationEngine {
    private FourDivinationEngine() {}

    public static final class SystemResult {
        public final String name, raw, direction, overUnder, oddEven, note, strengthLabel;
        public final int goals;
        public final boolean cleanSheet, burst, stable;
        public final double margin;

        SystemResult(String name, String raw, String direction, int goals, boolean cleanSheet,
                     boolean burst, boolean stable, double margin, String note) {
            this.name = name;
            this.raw = raw;
            this.direction = direction;
            this.goals = goals;
            this.overUnder = goals >= 3 ? "大2.5" : "小2.5";
            this.oddEven = (goals & 1) == 1 ? "单" : "双";
            this.cleanSheet = cleanSheet;
            this.burst = burst;
            this.stable = stable;
            this.margin = margin;
            this.note = note;
            double a = Math.abs(margin);
            this.strengthLabel = a >= 2.2 ? "强" : a >= 1.15 ? "中强" : a >= 0.58 ? "微倾" : "均衡";
        }

        public String summary() {
            return direction + "（" + strengthLabel + "） · " + goals + "球 · " + overUnder + " · " + oddEven
                    + (cleanSheet ? " · 零封信号" : "")
                    + (burst ? " · 单边/爆发信号" : "")
                    + (stable ? " · 稳定象" : "");
        }
    }

    public static final class CombinedResult {
        public final String direction;
        public final String directionRanking;
        public final String safer;
        public final String halfFull;
        public final String halfFullBackup;
        public final String goalRange;
        public final String goalFocus;
        public final String overUnder;
        public final String oddEven;
        public final String primaryScore;
        public final String secondaryScore;
        public final String coldScores;
        public final String scoreCandidates;
        public final String structure;
        public final String rationale;
        public final int totalGoals;
        public final int tendency;
        public final boolean cleanSheet;
        public final boolean burstBranch;
        public final boolean strongRamlConflict;

        CombinedResult(String direction, String directionRanking, String safer,
                       String halfFull, String halfFullBackup,
                       int totalGoals, String goalRange, String goalFocus,
                       String overUnder, String oddEven,
                       String primaryScore, String secondaryScore, String coldScores,
                       String scoreCandidates, int tendency,
                       String structure, String rationale,
                       boolean cleanSheet, boolean burstBranch, boolean strongRamlConflict) {
            this.direction = direction;
            this.directionRanking = directionRanking;
            this.safer = safer;
            this.halfFull = halfFull;
            this.halfFullBackup = halfFullBackup;
            this.totalGoals = totalGoals;
            this.goalRange = goalRange;
            this.goalFocus = goalFocus;
            this.overUnder = overUnder;
            this.oddEven = oddEven;
            this.primaryScore = primaryScore;
            this.secondaryScore = secondaryScore;
            this.coldScores = coldScores;
            this.scoreCandidates = scoreCandidates;
            this.tendency = tendency;
            this.structure = structure;
            this.rationale = rationale;
            this.cleanSheet = cleanSheet;
            this.burstBranch = burstBranch;
            this.strongRamlConflict = strongRamlConflict;
        }
    }

    public static final class CastState {
        public final long seed;
        public final SystemResult maya, sikidy, ifa, raml;
        public final CombinedResult combined;

        CastState(long seed, SystemResult maya, SystemResult sikidy,
                  SystemResult ifa, SystemResult raml, CombinedResult combined) {
            this.seed = seed;
            this.maya = maya;
            this.sikidy = sikidy;
            this.ifa = ifa;
            this.raml = raml;
            this.combined = combined;
        }

        public SystemResult[] systems() {
            return new SystemResult[]{maya, sikidy, ifa, raml};
        }
    }

    private static final String[] MAYA_SIGNS = {
            "Imix'", "Ik'", "Ak'b'al", "K'an", "Chikchan", "Kimi", "Manik'", "Lamat", "Muluk", "Ok",
            "Chuwen", "Eb'", "B'en", "Ix", "Men", "K'ib'", "Kab'an", "Etz'nab'", "Kawak", "Ajaw"
    };

    public static CastState castRandom() {
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
        boolean clean = (sign == 5 || sign == 9 || sign == 17) && Math.abs(margin) > 0.70;
        if (burst) goals = Math.max(goals, 3);

        String raw = index + "/260 · " + number + " " + MAYA_SIGNS[sign];
        return new SystemResult(
                "玛雅 Tzolk'in", raw, dir(margin, 0.55), goals,
                clean, burst, false, margin,
                "13数×20日符；用于节奏、方向和进球结构的固定实验映射"
        );
    }

    private static SystemResult castSikidy(long seed) {
        Random r = new Random(seed);
        int[][] f = geomancyLike(r);

        double home = strength(f[0]) + 0.75 * strength(f[12]) + 0.35 * strength(f[4]);
        double away = strength(f[3]) + 0.75 * strength(f[13]) + 0.35 * strength(f[7]);
        double margin = home - away;

        int judgeSingles = singles(f[14]);
        int stableCount = 0;
        for (int[] x : f) {
            if (singles(x) == 0 || singles(x) == 4) stableCount++;
        }
        boolean stable = stableCount >= 4 || judgeSingles == 0 || judgeSingles == 4;

        int active = singles(f[12]) + singles(f[13]) + singles(f[14]) + singles(f[15]);
        int goals = clamp((int)Math.round(1.0 + active / 4.0 + Math.abs(margin) * 0.25), 1, 4);
        boolean burst = Math.abs(margin) > 2.7 && active >= 9;
        boolean clean = Math.abs(margin) > 2.2 && judgeSingles <= 1;

        String raw = "母 " + fig(f[0]) + " " + fig(f[1]) + " " + fig(f[2]) + " " + fig(f[3])
                + " · 判 " + fig(f[14]);
        String note = stable
                ? "稳定/双数象只作结构参考，不再机械等同于小球"
                : "奇偶派生后合看见证与判象";

        return new SystemResult("Sikidy", raw, dir(margin, 0.70), goals, clean, burst, stable, margin, note);
    }

    private static SystemResult castIfa(long seed) {
        Random r = new Random(seed);
        int[] bits = new int[8];
        int ones = 0;
        for (int i = 0; i < 8; i++) {
            bits[i] = r.nextBoolean() ? 1 : 0;
            ones += bits[i];
        }

        int index = 0;
        for (int b : bits) index = (index << 1) | b;

        double left = weighted4(bits, 0);
        double right = weighted4(bits, 4);
        double margin = left - right;

        int transitions = 0;
        int longest = 1;
        int run = 1;
        for (int i = 1; i < 8; i++) {
            if (bits[i] != bits[i - 1]) {
                transitions++;
                run = 1;
            } else {
                run++;
                longest = Math.max(longest, run);
            }
        }

        boolean concentrated = longest >= 4 || ones <= 1 || ones >= 7;
        int goals = clamp(1 + ((ones + transitions + index) % 4), 1, 4);
        if (concentrated) goals = Math.max(goals, 3);
        boolean clean = concentrated && Math.abs(margin) > 1.0;

        String raw = bits(bits) + " · Odu实验索引 #" + (index + 1);
        String note = concentrated
                ? "高度集中只解释为单边化/后程增强，不直接把爆发归给主或客"
                : "二元签名较均衡";

        return new SystemResult("Ifá", raw, dir(margin, 0.58), goals, clean, concentrated, false, margin, note);
    }

    private static SystemResult castRaml(long seed) {
        Random r = new Random(seed);
        int[][] f = geomancyLike(r);

        double home = 1.20 * strength(f[0]) + 0.90 * strength(f[12]) + 0.45 * strength(f[4]);
        double away = 1.20 * strength(f[3]) + 0.90 * strength(f[13]) + 0.45 * strength(f[7]);
        double margin = home - away;

        int front = singles(f[0]) + singles(f[1]);
        int rear = singles(f[12]) + singles(f[13]) + singles(f[14]) + singles(f[15]);
        boolean lateBurst = front <= 3 && rear >= 10;
        boolean quietFinish = rear <= 4;

        int goals = clamp((int)Math.round(1.0 + rear / 5.0 + Math.abs(margin) * 0.22), 1, 4);
        if (lateBurst) goals = Math.max(goals, 3);
        if (quietFinish) goals = Math.min(goals, 2);

        boolean clean = Math.abs(margin) > 1.80 && (quietFinish || singles(f[14]) <= 1);

        String raw = "母 " + fig(f[0]) + "/" + fig(f[1]) + "/" + fig(f[2]) + "/" + fig(f[3])
                + " · 法官 " + fig(f[14]);
        String note = lateBurst
                ? "前静后强：开启爆发候选，但仍需另外判断爆发方向"
                : quietFinish
                ? "后程收束：提高零封/低比分候选"
                : "常规沙占结构";

        return new SystemResult("阿拉伯沙占", raw, dir(margin, 0.68), goals, clean, lateBurst, quietFinish, margin, note);
    }

    private static CombinedResult combine(SystemResult maya, SystemResult sikidy,
                                          SystemResult ifa, SystemResult raml) {
        SystemResult[] xs = {maya, sikidy, ifa, raml};

        int homeVotes = 0;
        int awayVotes = 0;
        int drawVotes = 0;
        int highVotes = 0;
        int lowVotes = 0;
        int oddVotes = 0;
        int evenVotes = 0;
        int totalSum = 0;

        double homeStrength = 0;
        double awayStrength = 0;
        double drawStrength = 0;
        double signed = 0;
        double signedWeight = 0;

        for (SystemResult x : xs) {
            double w = systemWeight(x.name);
            double amp = 1.0 + Math.min(1.5, Math.abs(x.margin) / 2.2) * 0.50;

            if ("主胜".equals(x.direction)) {
                homeVotes++;
                homeStrength += w * amp;
            } else if ("客胜".equals(x.direction)) {
                awayVotes++;
                awayStrength += w * amp;
            } else {
                drawVotes++;
                drawStrength += w * (1.15 + Math.max(0, 0.75 - Math.abs(x.margin)));
            }

            signed += w * clampDouble(x.margin, -2.6, 2.6);
            signedWeight += w;

            totalSum += x.goals;
            if (x.goals >= 3) highVotes++; else lowVotes++;
            if ("单".equals(x.oddEven)) oddVotes++; else evenVotes++;
        }

        double signedMean = signed / signedWeight;

        String plurality = plurality(homeVotes, drawVotes, awayVotes);
        boolean ramlStrong = !"平局".equals(raml.direction) && Math.abs(raml.margin) >= 1.80;
        boolean ramlOpposesPlurality = ramlStrong
                && !"平局".equals(plurality)
                && !raml.direction.equals(plurality);

        String primary;
        String second;
        String third;
        boolean strongRamlConflict = false;

        int maxVotes = Math.max(homeVotes, Math.max(drawVotes, awayVotes));

        if (drawVotes >= 2) {
            primary = "平局";
            second = sideByStrength(homeStrength, awayStrength);
            third = oppositeOrDraw(second);
        } else if (homeVotes == 2 && awayVotes == 2) {
            double d = homeStrength - awayStrength;
            if (Math.abs(d) < 0.30) {
                primary = "平局";
                second = d >= 0 ? "主胜" : "客胜";
                third = oppositeSide(second);
            } else {
                primary = d > 0 ? "主胜" : "客胜";
                second = oppositeSide(primary);
                third = "平局";
            }
        } else if (maxVotes == 2 && !"平局".equals(plurality)) {
            if (ramlOpposesPlurality && Math.abs(signedMean) < 0.72) {
                strongRamlConflict = true;
                primary = "平局";
                second = plurality;
                third = raml.direction;
            } else {
                primary = plurality;
                if (drawVotes > 0) {
                    second = "平局";
                    third = oppositeSide(primary);
                } else {
                    second = oppositeSide(primary);
                    third = "平局";
                }
            }
        } else if (maxVotes >= 3) {
            primary = plurality;
            second = drawVotes > 0 ? "平局" : oppositeSide(primary);
            third = second.equals("平局") ? oppositeSide(primary) : "平局";
            if (ramlOpposesPlurality && Math.abs(signedMean) < 0.35) {
                strongRamlConflict = true;
                second = "平局";
                third = raml.direction;
            }
        } else {
            if (Math.abs(signedMean) < 0.30) {
                primary = "平局";
                second = signedMean >= 0 ? "主胜" : "客胜";
                third = oppositeSide(second);
            } else {
                primary = signedMean > 0 ? "主胜" : "客胜";
                second = "平局";
                third = oppositeSide(primary);
            }
        }

        String directionRanking = shortRank(primary) + " > " + shortRank(second) + " > " + shortRank(third);

        boolean burstBranch = ifa.burst && raml.burst;
        boolean ramlCleanCold = raml.cleanSheet && !"平局".equals(raml.direction);

        int coreGoals;
        String goalRange;
        String goalFocus;
        if (highVotes == 4) {
            double avg = totalSum / 4.0;
            coreGoals = avg >= 3.50 ? 4 : 3;
            goalRange = "3–4球";
            goalFocus = coreGoals + "球第一落点";
        } else if (highVotes == 3) {
            coreGoals = clamp((int)Math.round(totalSum / 4.0), 3, 4);
            goalRange = "2–4球";
            goalFocus = coreGoals + "球偏集中";
        } else if (highVotes == 2 && lowVotes == 2) {
            boolean conservative = "平局".equals(primary) || sikidy.stable || raml.stable || raml.cleanSheet;
            coreGoals = conservative ? 2 : 3;
            goalRange = "2–3球";
            goalFocus = coreGoals == 2 ? "2球略优" : "3球略优";
        } else {
            coreGoals = clamp((int)Math.round(totalSum / 4.0), 1, 2);
            goalRange = coreGoals <= 1 ? "1–2球" : "1–3球";
            goalFocus = coreGoals + "球偏集中";
        }

        if (burstBranch) {
            coreGoals = Math.max(coreGoals, 3);
            goalRange = "3–4球";
            goalFocus = "3球起步，防4球";
        }

        String overUnder;
        if (highVotes >= 3) {
            overUnder = highVotes == 4 ? "大2.5明显" : "大2.5偏强";
        } else if (lowVotes >= 3) {
            overUnder = lowVotes == 4 ? "小2.5明显" : "小2.5偏强";
        } else {
            overUnder = coreGoals <= 2 ? "小2.5微倾（接近五五开）" : "大2.5微倾（接近五五开）";
        }

        String oddEven;
        if (oddVotes == 4) oddEven = "单数明显";
        else if (evenVotes == 4) oddEven = "双数明显";
        else if (oddVotes == 3) oddEven = "单数偏强";
        else if (evenVotes == 3) oddEven = "双数偏强";
        else oddEven = (coreGoals & 1) == 1
                ? "单双分裂，单数仅作微倾"
                : "单双分裂，双数仅作微倾";

        boolean primaryClean = raml.cleanSheet
                && raml.direction.equals(primary)
                && !"平局".equals(primary);

        String primaryScore = primaryScore(primary, coreGoals, primaryClean);
        String secondaryScore = secondaryScore(primary, second, coreGoals, primaryClean);

        List<String> cold = new ArrayList<>();

        if (ramlStrong && !raml.direction.equals(primary)) {
            if ("客胜".equals(raml.direction)) {
                addScore(cold, raml.cleanSheet ? "0:1" : "1:2");
                addScore(cold, raml.cleanSheet ? "0:2" : "0:1");
            } else {
                addScore(cold, raml.cleanSheet ? "1:0" : "2:1");
                addScore(cold, raml.cleanSheet ? "2:0" : "1:0");
            }
        }

        if (burstBranch) {
            String burstSide;
            if (raml.direction.equals(ifa.direction) && !"平局".equals(raml.direction)) {
                burstSide = raml.direction;
            } else if (!"平局".equals(raml.direction)) {
                burstSide = raml.direction;
            } else {
                burstSide = !"平局".equals(primary) ? primary : second;
            }
            if ("主胜".equals(burstSide)) {
                addScore(cold, "3:0");
                addScore(cold, "3:1");
            } else if ("客胜".equals(burstSide)) {
                addScore(cold, "0:3");
                addScore(cold, "1:3");
            }
        }

        if (cold.isEmpty()) {
            if ("主胜".equals(primary)) {
                addScore(cold, coreGoals >= 4 ? "1:0" : "3:0");
            } else if ("客胜".equals(primary)) {
                addScore(cold, coreGoals >= 4 ? "0:1" : "0:3");
            } else {
                addScore(cold, "0:0");
                if ("主胜".equals(second)) addScore(cold, "2:1");
                else if ("客胜".equals(second)) addScore(cold, "1:2");
            }
        }

        String coldScores = String.join("、", cold);

        List<String> allScores = new ArrayList<>();
        addScore(allScores, primaryScore);
        addScore(allScores, secondaryScore);
        for (String s : cold) addScore(allScores, s);
        while (allScores.size() > 5) allScores.remove(allScores.size() - 1);
        String scoreCandidates = String.join("、", allScores);

        String halfFull;
        String halfFullBackup;
        if ("平局".equals(primary)) {
            halfFull = "平/平";
            halfFullBackup = "平/" + shortDir(second);
        } else {
            int same = countDirection(xs, primary);
            if (same >= 3 && Math.abs(signedMean) > 0.90) {
                halfFull = shortDir(primary) + "/" + shortDir(primary);
                halfFullBackup = "平/" + shortDir(primary);
            } else {
                halfFull = "平/" + shortDir(primary);
                halfFullBackup = shortDir(primary) + "/" + shortDir(primary);
            }
        }

        String safer;
        if ("平局".equals(primary)) {
            safer = "均衡盘，重点防平";
        } else {
            safer = "主胜".equals(primary) ? "主队不败" : "客队不败";
        }

        int agreement = Math.max(homeVotes, Math.max(drawVotes, awayVotes));
        int tendency = clamp(
                49 + agreement * 6
                        + (int)Math.round(Math.min(7, Math.abs(signedMean) * 4))
                        + (primaryClean || burstBranch ? 4 : 0)
                        - (strongRamlConflict ? 5 : 0),
                50, 84
        );

        String structure = "四术票：主" + homeVotes + " / 平" + drawVotes + " / 客" + awayVotes
                + "；强度：主" + oneDecimal(homeStrength)
                + " / 平" + oneDecimal(drawStrength)
                + " / 客" + oneDecimal(awayStrength) + "。";

        StringBuilder reason = new StringBuilder();
        if (strongRamlConflict) {
            reason.append("多数票与沙占强反向信号冲突，整体强度被拉回均衡，因此平局前置；");
        } else if (homeVotes == 2 && awayVotes == 2) {
            reason.append("方向2:2分裂，改用两侧强度决定主线，不机械判平；");
        } else {
            reason.append("先按票数定框架，再用各盘强度修正排序；");
        }

        if (burstBranch) reason.append("Ifá集中且沙占前静后强，保留3:0/0:3类爆发比分；");
        if (ramlCleanCold) reason.append("沙占零封信号独立进入冷防，不强行覆盖主线；");
        if (sikidy.stable) reason.append("Sikidy稳定象只影响节奏，不直接等于小球；");

        return new CombinedResult(
                primary,
                directionRanking,
                safer,
                halfFull,
                halfFullBackup,
                coreGoals,
                goalRange,
                goalFocus,
                overUnder,
                oddEven,
                primaryScore,
                secondaryScore,
                coldScores,
                scoreCandidates,
                tendency,
                structure,
                reason.toString(),
                primaryClean,
                burstBranch,
                strongRamlConflict
        );
    }

    private static String primaryScore(String direction, int goals, boolean clean) {
        if ("平局".equals(direction)) {
            if (goals <= 1) return "0:0";
            if (goals <= 3) return "1:1";
            return "2:2";
        }

        if ("主胜".equals(direction)) {
            if (clean) {
                if (goals <= 1) return "1:0";
                if (goals == 2) return "2:0";
                return "3:0";
            }
            if (goals <= 1) return "1:0";
            if (goals == 2) return "2:0";
            if (goals == 3) return "2:1";
            if (goals == 4) return "3:1";
            return "3:2";
        }

        if (clean) {
            if (goals <= 1) return "0:1";
            if (goals == 2) return "0:2";
            return "0:3";
        }
        if (goals <= 1) return "0:1";
        if (goals == 2) return "0:2";
        if (goals == 3) return "1:2";
        if (goals == 4) return "1:3";
        return "2:3";
    }

    private static String secondaryScore(String primary, String second, int goals, boolean clean) {
        if ("平局".equals(primary)) {
            if ("主胜".equals(second)) return goals <= 2 ? "1:0" : "2:1";
            if ("客胜".equals(second)) return goals <= 2 ? "0:1" : "1:2";
            return "0:0";
        }

        if ("主胜".equals(primary)) {
            if (goals >= 4) return "2:1";
            if (goals == 3) return clean ? "2:0" : "2:0";
            if (goals == 2) return "1:0";
            return "2:0";
        }

        if (goals >= 4) return "1:2";
        if (goals == 3) return "0:2";
        if (goals == 2) return "0:1";
        return "0:2";
    }

    private static int countDirection(SystemResult[] xs, String direction) {
        int n = 0;
        for (SystemResult x : xs) if (direction.equals(x.direction)) n++;
        return n;
    }

    private static double systemWeight(String name) {
        if (name.startsWith("阿拉伯")) return 1.20;
        if (name.startsWith("Sikidy")) return 1.05;
        return 1.00;
    }

    private static String plurality(int home, int draw, int away) {
        if (home > draw && home > away) return "主胜";
        if (away > home && away > draw) return "客胜";
        if (draw > home && draw > away) return "平局";
        return "平局";
    }

    private static String sideByStrength(double home, double away) {
        return home >= away ? "主胜" : "客胜";
    }

    private static String oppositeOrDraw(String s) {
        if ("主胜".equals(s)) return "客胜";
        if ("客胜".equals(s)) return "主胜";
        return "平局";
    }

    private static String oppositeSide(String s) {
        return "主胜".equals(s) ? "客胜" : "主胜";
    }

    private static String shortRank(String s) {
        return "主胜".equals(s) ? "主胜" : "客胜".equals(s) ? "客胜" : "平";
    }

    private static String shortDir(String s) {
        return "主胜".equals(s) ? "胜" : "客胜".equals(s) ? "负" : "平";
    }

    private static int[][] geomancyLike(Random r) {
        int[][] f = new int[16][4];
        for (int m = 0; m < 4; m++) {
            for (int row = 0; row < 4; row++) {
                f[m][row] = r.nextBoolean() ? 1 : 0;
            }
        }

        for (int d = 0; d < 4; d++) {
            for (int row = 0; row < 4; row++) {
                f[4 + d][row] = f[row][d];
            }
        }

        combineBits(f[0], f[1], f[8]);
        combineBits(f[2], f[3], f[9]);
        combineBits(f[4], f[5], f[10]);
        combineBits(f[6], f[7], f[11]);
        combineBits(f[8], f[9], f[12]);
        combineBits(f[10], f[11], f[13]);
        combineBits(f[12], f[13], f[14]);
        combineBits(f[14], f[0], f[15]);
        return f;
    }

    private static void addScore(List<String> out, String s) {
        if (s != null && !s.isEmpty() && !out.contains(s)) out.add(s);
    }

    private static String dir(double margin, double dead) {
        if (margin > dead) return "主胜";
        if (margin < -dead) return "客胜";
        return "平局";
    }

    private static double weighted4(int[] bits, int off) {
        double[] w = {1.0, 0.8, 0.65, 0.5};
        double x = 0;
        for (int i = 0; i < 4; i++) {
            x += w[i] * (bits[off + i] == 1 ? 1 : -1);
        }
        return x;
    }

    private static double strength(int[] x) {
        double[] w = {1.1, 0.9, 0.7, 0.55};
        double s = 0;
        for (int i = 0; i < 4; i++) {
            s += w[i] * (x[i] == 1 ? 1 : -1);
        }
        return s;
    }

    private static int singles(int[] x) {
        int n = 0;
        for (int v : x) n += v;
        return n;
    }

    private static void combineBits(int[] a, int[] b, int[] out) {
        for (int i = 0; i < 4; i++) out[i] = a[i] ^ b[i];
    }

    private static String fig(int[] x) {
        StringBuilder b = new StringBuilder();
        for (int v : x) b.append(v == 1 ? '1' : '2');
        return b.toString();
    }

    private static String bits(int[] x) {
        StringBuilder b = new StringBuilder();
        for (int v : x) b.append(v);
        return b.toString();
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clampDouble(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String oneDecimal(double x) {
        return String.format(Locale.CHINA, "%.1f", x);
    }

    public static String debug(CastState s) {
        return String.format(
                Locale.CHINA,
                "seed=%d ranking=%s scores=%s",
                s.seed,
                s.combined.directionRanking,
                s.combined.scoreCandidates
        );
    }
}
