package com.matchday.virtualbet;

import java.util.*;

/** Decimal-odds simulation. One leg per event; same-event picks are alternatives. */
public final class MarketMath {
    private MarketMath() {}
    public static boolean validOdd(double x) { return Double.isFinite(x) && x > 1 && x <= 100000; }
    public static boolean quarterLine(double x) { return Double.isFinite(x) && Math.abs(x * 4 - Math.rint(x * 4)) < 0.000001; }
    public static double combinations(List<Double> weights, int k) {
        if (k < 1 || k > weights.size() || k > 15) return 0;
        double[] dp = new double[k + 1]; dp[0] = 1;
        for (double w : weights) {
            if (!Double.isFinite(w) || w < 0) throw new IllegalArgumentException("无效串关项");
            for (int j = k; j > 0; j--) dp[j] += dp[j - 1] * w;
        }
        return dp[k];
    }
    private static double result(double difference, double odds) {
        return difference > 0.000001 ? odds : difference < -0.000001 ? 0 : 1;
    }
    public static double asian(double value, double line, boolean over, double odds) {
        if (!validOdd(odds) || !quarterLine(line)) throw new IllegalArgumentException("盘口必须是0.25的整数倍，赔率须大于1");
        long q = Math.round(line * 4);
        if ((Math.abs(q) & 1) == 1) {
            double lo = Math.floor(line * 2) / 2, hi = lo + .5;
            return (result((value - lo) * (over ? 1 : -1), odds) + result((value - hi) * (over ? 1 : -1), odds)) / 2;
        }
        return result((value - line) * (over ? 1 : -1), odds);
    }
    public static String outcome(int h, int a) { return h > a ? "胜" : h < a ? "负" : "平"; }
    public static double settle(String market, String label, double odds, double line,
                                int h, int a, int hh, int ha, String first, Set<String> listedScores) {
        if (!validOdd(odds) || h < 0 || a < 0) return Double.NaN;
        String full = outcome(h,a); int total = h+a; boolean win;
        switch (market) {
            case "OU": return asian(total,line,label.equals("大"),odds);
            case "AH": return asian(h-a,-line,label.equals("主"),odds);
            case "HAD": win=label.equals(full); break;
            case "HHAD":
                double d=h+line-a; win=label.equals(d>0?"让胜":d<0?"让负":"让平"); break;
            case "BDHAD":
                double bd=h+line-a; win=label.equals(bd>0?"胜":bd<0?"负":"平"); break;
            case "TTG": win=label.equals(total+"球") || label.equals("7+球") && total>=7; break;
            case "HAFU":
                if(hh<0 || ha<0 || hh>h || ha>a) return Double.NaN;
                win=label.replace("/","").equals(outcome(hh,ha)+full); break;
            case "ODD": win=label.equals(total%2==0?"双":"单"); break;
            case "DS": win=label.equals((total>=3?"上":"下")+(total%2==0?"双":"单")); break;
            case "BTTS": win=label.equals(h>0 && a>0?"是":"否"); break;
            case "FIRST":
                if (total==0) first="无进球";
                if(!Arrays.asList("主队","客队","无进球").contains(first)) return Double.NaN;
                if(total>0 && "无进球".equals(first) || h==0 && "主队".equals(first) || a==0 && "客队".equals(first)) return Double.NaN;
                win=label.equals(first); break;
            case "CRS":
                String exact=h+":"+a;
                win=label.equals(exact) || label.equals(full+"其他") && listedScores!=null && !listedScores.contains(exact); break;
            default:return Double.NaN;
        }
        return win?odds:0;
    }
    public static String[] labels(String m, boolean beidan) {
        switch(m) {
            case "HAD": case "BDHAD": return new String[]{"胜","平","负"};
            case "HHAD": return new String[]{"让胜","让平","让负"};
            case "TTG":return new String[]{"0球","1球","2球","3球","4球","5球","6球","7+球"};
            case "HAFU":return new String[]{"胜/胜","胜/平","胜/负","平/胜","平/平","平/负","负/胜","负/平","负/负"};
            case "DS":return new String[]{"上单","上双","下单","下双"};
            case "OU":return new String[]{"大","小"};
            case "AH":return new String[]{"主","客"};
            case "ODD":return new String[]{"单","双"};
            case "BTTS":return new String[]{"是","否"};
            case "FIRST":return new String[]{"主队","客队","无进球"};
            case "CRS":return (beidan?"1:0 2:0 2:1 3:0 3:1 3:2 4:0 4:1 4:2 胜其他 0:0 1:1 2:2 3:3 平其他 0:1 0:2 1:2 0:3 1:3 2:3 0:4 1:4 2:4 负其他":"1:0 2:0 2:1 3:0 3:1 3:2 4:0 4:1 4:2 5:0 5:1 5:2 胜其他 0:0 1:1 2:2 3:3 平其他 0:1 0:2 1:2 0:3 1:3 2:3 0:4 1:4 2:4 0:5 1:5 2:5 负其他").split(" ");
            default:return new String[0];
        }
    }
}
