package com.abe.vedicprashna;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PrashnaAnalyzer {
    private static final String[] SIGN_LORD = {
            "Mars", "Venus", "Mercury", "Moon", "Sun", "Mercury",
            "Venus", "Mars", "Jupiter", "Saturn", "Saturn", "Jupiter"
    };

    private static final String[] NAK_LORD_CYCLE = {
            "Ketu", "Venus", "Sun", "Moon", "Mars", "Rahu", "Jupiter", "Saturn", "Mercury"
    };

    private static final int[] HOME_SUPPORT = {1, 6, 10, 11};
    private static final int[] AWAY_SUPPORT = {7, 12, 4, 5};

    public AnalysisResult analyze(AstroEngine.ChartData chart, String homeName, String awayName) {
        String home = blankTo(homeName, "主队");
        String away = blankTo(awayName, "客队");

        int seventhSign = (chart.ascSign + 6) % 12;
        String homeLord = SIGN_LORD[chart.ascSign];
        String awayLord = SIGN_LORD[seventhSign];

        List<String> homeNotes = new ArrayList<>();
        List<String> awayNotes = new ArrayList<>();

        double homeScore = teamLordScore(chart, homeLord, 1, homeNotes, "主方");
        double awayScore = teamLordScore(chart, awayLord, 7, awayNotes, "客方");

        homeScore += supportHouseScore(chart, HOME_SUPPORT, 1, homeNotes, "主方");
        awayScore += supportHouseScore(chart, AWAY_SUPPORT, 7, awayNotes, "客方");

        double[] moonFlow = moonFlow(chart, homeNotes, awayNotes);
        homeScore += moonFlow[0];
        awayScore += moonFlow[1];

        double diff = homeScore - awayScore;
        double draw = clamp(0.16 + 0.25 * Math.exp(-Math.abs(diff) / 2.8), 0.16, 0.42);
        double homeShare = 1.0 / (1.0 + Math.exp(-diff / 2.2));
        double remaining = 1.0 - draw;
        double homeP = remaining * homeShare;
        double awayP = remaining * (1.0 - homeShare);

        String verdict;
        if (draw >= homeP && draw >= awayP) {
            verdict = "平局优先";
        } else if (homeP > awayP) {
            verdict = diff > 2.7 ? "主胜倾向较强" : "主胜倾向";
        } else {
            verdict = diff < -2.7 ? "客胜倾向较强" : "客胜倾向";
        }

        String summary = buildSummary(chart, home, away, homeLord, awayLord, diff);
        String detail = buildDetail(chart, home, away, homeLord, awayLord, homeScore, awayScore, homeNotes, awayNotes);

        return new AnalysisResult(
                verdict, homeScore, awayScore,
                homeP, draw, awayP, summary, detail
        );
    }

    private double teamLordScore(
            AstroEngine.ChartData chart, String lordKey, int referenceHouse,
            List<String> notes, String label
    ) {
        AstroEngine.PlanetPlacement p = chart.find(lordKey);
        int relD1 = relativeHouse(p.house, referenceHouse);
        int relD9 = relativeHouse(p.d9House, referenceHouse);

        double d1Dignity = dignityScore(lordKey, p.sign);
        double d9Dignity = dignityScore(lordKey, p.d9Sign);
        double placement = placementScore(relD1);
        double d9Placement = placementScore(relD9) * 0.45;

        double score = d1Dignity + placement + d9Dignity * 0.55 + d9Placement;

        if (p.retrograde && !lordKey.equals("Sun") && !lordKey.equals("Moon")) {
            score += 0.25;
            notes.add(label + "宫主" + p.name + "逆行：仅作轻微力量加成");
        }

        notes.add(String.format(Locale.US,
                "%s宫主%s：D1尊贵 %.1f，D1相对宫位%d得分 %.1f，D9尊贵 %.1f",
                label, p.name, d1Dignity, relD1, placement, d9Dignity));

        return score;
    }

    private double supportHouseScore(
            AstroEngine.ChartData chart, int[] absoluteHouses, int referenceHouse,
            List<String> notes, String label
    ) {
        double total = 0.0;
        for (int house : absoluteHouses) {
            int sign = chart.signAtHouse(house);
            String lord = SIGN_LORD[sign];
            AstroEngine.PlanetPlacement p = chart.find(lord);
            int rel = relativeHouse(p.house, referenceHouse);
            double piece = dignityScore(lord, p.sign) * 0.18 + placementScore(rel) * 0.16;
            total += piece;
        }
        notes.add(String.format(Locale.US,
                "%s胜利宫组综合：%+.2f（主方看1/6/10/11；客方按7宫旋转为7/12/4/5）",
                label, total));
        return total;
    }

    private double[] moonFlow(
            AstroEngine.ChartData chart, List<String> homeNotes, List<String> awayNotes
    ) {
        double home = 0.0;
        double away = 0.0;
        AstroEngine.PlanetPlacement moon = chart.find("Moon");

        if (contains(HOME_SUPPORT, moon.house)) {
            home += 1.1;
            homeNotes.add("月亮落主方支持宫：" + moon.house + "宫，事件流向偏主");
        }
        if (contains(AWAY_SUPPORT, moon.house)) {
            away += 1.1;
            awayNotes.add("月亮落客方支持宫：" + moon.house + "宫，事件流向偏客");
        }

        String nakLord = NAK_LORD_CYCLE[moon.nakshatra % 9];
        AstroEngine.PlanetPlacement lord = chart.find(nakLord);

        if (contains(HOME_SUPPORT, lord.house)) {
            home += nakLord.equals("Rahu") || nakLord.equals("Ketu") ? 0.45 : 0.7;
            homeNotes.add("月宿主" + lord.name + "落主方支持宫");
        }
        if (contains(AWAY_SUPPORT, lord.house)) {
            away += nakLord.equals("Rahu") || nakLord.equals("Ketu") ? 0.45 : 0.7;
            awayNotes.add("月宿主" + lord.name + "落客方支持宫");
        }

        return new double[]{home, away};
    }

    private String buildSummary(
            AstroEngine.ChartData chart, String home, String away,
            String homeLord, String awayLord, double diff
    ) {
        AstroEngine.PlanetPlacement hp = chart.find(homeLord);
        AstroEngine.PlanetPlacement ap = chart.find(awayLord);
        AstroEngine.PlanetPlacement moon = chart.find("Moon");
        String balance = Math.abs(diff) < 1.0
                ? "双方力量接近，平局权重明显上升。"
                : (diff > 0 ? "问事盘整体更偏向主方。" : "问事盘整体更偏向客方。");

        return home + "以1宫为主方，" + away + "以7宫为客方。"
                + "主方宫主为" + hp.name + "，客方宫主为" + ap.name + "。"
                + balance
                + " 月亮位于" + moon.house + "宫、"
                + AstroEngine.NAKSHATRAS[moon.nakshatra]
                + "第" + moon.pada + "足。";
    }

    private String buildDetail(
            AstroEngine.ChartData chart, String home, String away,
            String homeLord, String awayLord, double homeScore, double awayScore,
            List<String> homeNotes, List<String> awayNotes
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("固定映射\n");
        sb.append("• ").append(home).append(" = 第1宫（Lagna）\n");
        sb.append("• ").append(away).append(" = 第7宫（对方）\n");
        sb.append("• 主方胜利宫：1、6、10、11\n");
        sb.append("• 客方按第7宫旋转：7、12、4、5\n\n");

        sb.append("核心宫主\n");
        sb.append("• 主方宫主：").append(chart.find(homeLord).name).append("\n");
        sb.append("• 客方宫主：").append(chart.find(awayLord).name).append("\n\n");

        sb.append(String.format(Locale.US, "主方规则分 %.2f\n", homeScore));
        for (String s : homeNotes) sb.append("• ").append(s).append("\n");
        sb.append("\n");
        sb.append(String.format(Locale.US, "客方规则分 %.2f\n", awayScore));
        for (String s : awayNotes) sb.append("• ").append(s).append("\n");

        sb.append("\n判定原则\n");
        sb.append("1. 先比较1宫主与7宫主的D1尊贵、相对宫位与D9强化。\n");
        sb.append("2. 再比较双方各自的6/10/11类胜利宫；客方全部从第7宫旋转。\n");
        sb.append("3. 月亮及其月宿主用于判断事件流向。\n");
        sb.append("4. 分差接近时提高平局权重。\n");
        sb.append("5. 当前百分比是规则分布，不是历史回测得到的真实胜率。\n");

        return sb.toString();
    }

    private static double dignityScore(String planet, int sign) {
        int exalt = exaltationSign(planet);
        if (exalt >= 0 && sign == exalt) return 4.0;
        if (exalt >= 0 && sign == (exalt + 6) % 12) return -4.0;
        if (owns(planet, sign)) return 3.0;
        return 0.0;
    }

    private static int exaltationSign(String planet) {
        switch (planet) {
            case "Sun": return 0;
            case "Moon": return 1;
            case "Mars": return 9;
            case "Mercury": return 5;
            case "Jupiter": return 3;
            case "Venus": return 11;
            case "Saturn": return 6;
            default: return -1;
        }
    }

    private static boolean owns(String planet, int sign) {
        switch (planet) {
            case "Sun": return sign == 4;
            case "Moon": return sign == 3;
            case "Mars": return sign == 0 || sign == 7;
            case "Mercury": return sign == 2 || sign == 5;
            case "Jupiter": return sign == 8 || sign == 11;
            case "Venus": return sign == 1 || sign == 6;
            case "Saturn": return sign == 9 || sign == 10;
            default: return false;
        }
    }

    private static double placementScore(int relativeHouse) {
        double score = 0.0;
        if (relativeHouse == 1) score += 2.4;
        if (relativeHouse == 4 || relativeHouse == 7 || relativeHouse == 10) score += 1.25;
        if (relativeHouse == 5 || relativeHouse == 9) score += 1.35;
        if (relativeHouse == 3 || relativeHouse == 6 || relativeHouse == 10 || relativeHouse == 11) score += 0.75;
        if (relativeHouse == 8 || relativeHouse == 12) score -= 1.65;
        return score;
    }

    private static int relativeHouse(int absoluteHouse, int referenceHouse) {
        return ((absoluteHouse - referenceHouse + 12) % 12) + 1;
    }

    private static boolean contains(int[] array, int value) {
        for (int x : array) if (x == value) return true;
        return false;
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    private static String blankTo(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s.trim();
    }

    public static final class AnalysisResult {
        public final String verdict;
        public final double homeScore;
        public final double awayScore;
        public final double homeProbability;
        public final double drawProbability;
        public final double awayProbability;
        public final String summary;
        public final String detail;

        AnalysisResult(
                String verdict, double homeScore, double awayScore,
                double homeProbability, double drawProbability, double awayProbability,
                String summary, String detail
        ) {
            this.verdict = verdict;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
            this.homeProbability = homeProbability;
            this.drawProbability = drawProbability;
            this.awayProbability = awayProbability;
            this.summary = summary;
            this.detail = detail;
        }

        public String compactProbabilities() {
            return String.format(
                    Locale.US,
                    "主胜 %.0f%%   平 %.0f%%   客胜 %.0f%%",
                    homeProbability * 100.0,
                    drawProbability * 100.0,
                    awayProbability * 100.0
            );
        }
    }
}
