package com.abe618.sixfusionfootball;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FusionEngine {
    private FusionEngine() {}

    public static final class Signal {
        public final String name, raw, direction, strengthLabel, ou, parity, note;
        public final int goals;
        public final double margin;
        public final boolean unilateral, closing, lateStrength, cleanSheet, burst;

        Signal(String name, String raw, String direction, double margin, int goals,
               boolean unilateral, boolean closing, boolean lateStrength,
               boolean cleanSheet, boolean burst, String note) {
            this.name = name;
            this.raw = raw;
            this.direction = direction;
            this.margin = margin;
            this.goals = clamp(goals, 1, 4);
            double a = Math.abs(margin);
            this.strengthLabel = a >= 2.6 ? "很强" : a >= 1.7 ? "强" : a >= 0.9 ? "中强" : a >= 0.45 ? "微倾" : "均衡";
            this.ou = this.goals >= 3 ? "大2.5" : "小2.5";
            this.parity = (this.goals & 1) == 1 ? "单" : "双";
            this.unilateral = unilateral;
            this.closing = closing;
            this.lateStrength = lateStrength;
            this.cleanSheet = cleanSheet;
            this.burst = burst;
            this.note = note;
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(direction).append("（").append(strengthLabel).append("）")
              .append(" · ").append(goals).append("球 · ").append(ou).append(" · ").append(parity);
            if (unilateral) sb.append(" · 单边");
            if (closing) sb.append(" · 收束");
            if (lateStrength) sb.append(" · 后程增强");
            if (cleanSheet) sb.append(" · 零封");
            if (burst) sb.append(" · 爆发");
            return sb.toString();
        }
    }

    public static final class BingZhanResult {
        public final String raw, direction, strengthLabel, note;
        public final int goals, netGoalHint;
        public final double margin;
        public final boolean unilateral, closing, burst;

        BingZhanResult(String raw, String direction, double margin, int goals, int netGoalHint,
                       boolean unilateral, boolean closing, boolean burst, String note) {
            this.raw = raw;
            this.direction = direction;
            this.margin = margin;
            this.goals = clamp(goals, 1, 5);
            this.netGoalHint = clamp(netGoalHint, 0, 4);
            this.unilateral = unilateral;
            this.closing = closing;
            this.burst = burst;
            double a = Math.abs(margin);
            this.strengthLabel = a >= 3.0 ? "很强" : a >= 2.0 ? "强" : a >= 1.0 ? "中强" : a >= 0.5 ? "微倾" : "均衡";
            this.note = note;
        }

        public String summary() {
            return direction + "（" + strengthLabel + "） · " + goals + "球 · 净胜提示" + netGoalHint + "球"
                    + (unilateral ? " · 单边" : "")
                    + (closing ? " · 收束" : "")
                    + (burst ? " · 爆发" : "");
        }
    }

    public static final class DirectionCorrection {
        public final String direction, strengthLabel, mirrorRisk, handicap, raw, note;
        public final double margin;
        public final int goalHint;

        DirectionCorrection(String direction, double margin, String mirrorRisk, String handicap,
                            int goalHint, String raw, String note) {
            this.direction = direction;
            this.margin = margin;
            double a = Math.abs(margin);
            this.strengthLabel = a >= 2.3 ? "强" : a >= 1.3 ? "中强" : a >= 0.65 ? "微倾" : "均衡";
            this.mirrorRisk = mirrorRisk;
            this.handicap = handicap;
            this.goalHint = clamp(goalHint, 1, 5);
            this.raw = raw;
            this.note = note;
        }

        public String summary() {
            return direction + "（" + strengthLabel + "） · 镜像风险" + mirrorRisk + " · " + handicap;
        }
    }

    public static final class Combined {
        public final String ranking, safer, resonance, mirrorRisk, handicap;
        public final String halfFull, halfFullBackup;
        public final String goalRange, goalFocus, overUnder, parity, netGoal;
        public final String primaryScore, secondaryScore, defenseScores, tailScores, mirrorScores;
        public final String scorePool, structure, rationale;
        public final int tendency;

        Combined(String ranking, String safer, String resonance, String mirrorRisk, String handicap,
                 String halfFull, String halfFullBackup, String goalRange, String goalFocus,
                 String overUnder, String parity, String netGoal,
                 String primaryScore, String secondaryScore, String defenseScores,
                 String tailScores, String mirrorScores, String scorePool,
                 String structure, String rationale, int tendency) {
            this.ranking = ranking;
            this.safer = safer;
            this.resonance = resonance;
            this.mirrorRisk = mirrorRisk;
            this.handicap = handicap;
            this.halfFull = halfFull;
            this.halfFullBackup = halfFullBackup;
            this.goalRange = goalRange;
            this.goalFocus = goalFocus;
            this.overUnder = overUnder;
            this.parity = parity;
            this.netGoal = netGoal;
            this.primaryScore = primaryScore;
            this.secondaryScore = secondaryScore;
            this.defenseScores = defenseScores;
            this.tailScores = tailScores;
            this.mirrorScores = mirrorScores;
            this.scorePool = scorePool;
            this.structure = structure;
            this.rationale = rationale;
            this.tendency = tendency;
        }
    }

    public static final class CastState {
        public final long seed;
        public final Signal maya, sikidy, ifa, raml;
        public final BingZhanResult bingzhan;
        public final DirectionCorrection correction;
        public final Combined combined;

        CastState(long seed, Signal maya, Signal sikidy, Signal ifa, Signal raml,
                  BingZhanResult bingzhan, DirectionCorrection correction, Combined combined) {
            this.seed = seed;
            this.maya = maya;
            this.sikidy = sikidy;
            this.ifa = ifa;
            this.raml = raml;
            this.bingzhan = bingzhan;
            this.correction = correction;
            this.combined = combined;
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
        Signal maya = castMaya(seed);
        Signal sikidy = castSikidy(seed);
        Signal ifa = castIfa(seed);
        Signal raml = castRaml(seed);
        BingZhanResult bz = castBingZhan(seed);
        DirectionCorrection dc = castDirectionCorrection(seed, maya, sikidy, ifa, raml, bz);
        Combined c = combine(maya, sikidy, ifa, raml, bz, dc);
        return new CastState(seed, maya, sikidy, ifa, raml, bz, dc, c);
    }

    private static Signal castMaya(long seed) {
        int index = 1 + rv(seed, "maya", 0, 260);
        int number = 1 + rv(seed, "maya", 1, 13);
        int sign = rv(seed, "maya", 2, 20);
        double margin = ((rv(seed, "maya", 3, 2001) - 1000) / 1000.0) * 2.0;
        int goals = 1 + rv(seed, "maya", 4, 4);
        boolean burst = goals >= 3 && (number >= 10 || sign == 18 || rv(seed, "maya", 5, 5) == 0);
        boolean clean = Math.abs(margin) > 1.4 && rv(seed, "maya", 6, 4) == 0;
        boolean closing = goals <= 2 && rv(seed, "maya", 7, 3) == 0;
        return new Signal(
                "D1 玛雅 Tzolk'in",
                index + "/260 · " + number + " " + MAYA_SIGNS[sign],
                dir(margin, 0.46), margin, goals,
                burst && Math.abs(margin) > 1.0, closing, false, clean, burst,
                "13数×20日符：承担节奏、方向与进球结构的独立映射"
        );
    }

    private static Signal castSikidy(long seed) {
        int[] d = digits12(seed, "sikidy", 16, 1, 2);
        double home = 1.3*v(d[0]) + 1.0*v(d[4]) + 0.8*v(d[8]) + 1.2*v(d[12]);
        double away = 1.3*v(d[3]) + 1.0*v(d[7]) + 0.8*v(d[11]) + 1.2*v(d[15]);
        double margin = home - away;
        int active = 0;
        int stableLines = 0;
        for (int i=0;i<16;i++) {
            if (d[i] == 2) active++;
        }
        for (int g=0; g<4; g++) {
            int s = d[g*4] + d[g*4+1] + d[g*4+2] + d[g*4+3];
            if (s == 4 || s == 8) stableLines++;
        }
        boolean closing = stableLines >= 2 || active <= 6;
        boolean burst = active >= 11 && Math.abs(margin) >= 1.4;
        int goals = clamp(1 + (active + (int)Math.round(Math.abs(margin))) / 4, 1, 4);
        if (closing) goals = Math.min(goals, 2);
        if (burst) goals = Math.max(goals, 3);
        boolean clean = closing && Math.abs(margin) > 1.7;
        return new Signal(
                "D2 Sikidy", groups(d),
                dir(margin, 0.70), margin, goals,
                burst, closing, false, clean, burst,
                "稳定/奇偶结构只作为比赛形态参考，不机械等同于小球"
        );
    }

    private static Signal castIfa(long seed) {
        int[] bits = new int[8];
        int ones = 0;
        int index = 0;
        for (int i=0;i<8;i++) {
            bits[i] = rv(seed, "ifa", i, 2);
            ones += bits[i];
            index = (index << 1) | bits[i];
        }
        double left = 1.4*bits[0] + 1.2*bits[1] + 1.0*bits[2] + 0.8*bits[3];
        double right = 1.4*bits[4] + 1.2*bits[5] + 1.0*bits[6] + 0.8*bits[7];
        double margin = (left - right) * 0.85;
        int transitions = 0, longest=1, run=1;
        for (int i=1;i<8;i++) {
            if (bits[i] == bits[i-1]) { run++; longest=Math.max(longest,run); }
            else { transitions++; run=1; }
        }
        boolean unilateral = longest >= 4 || ones <= 1 || ones >= 7;
        int goals = 1 + rv(seed, "ifa", 9, 4);
        if (unilateral) goals = Math.max(goals, 3);
        boolean clean = unilateral && Math.abs(margin) > 1.2;
        StringBuilder b = new StringBuilder();
        for (int x:bits) b.append(x);
        return new Signal(
                "D3 Ifá", b + " · #" + (index + 1),
                dir(margin, 0.55), margin, goals,
                unilateral, false, unilateral && transitions <= 3, clean, unilateral,
                "集中度优先解释为单边化/爆发结构，不自动指定爆发归属"
        );
    }

    private static Signal castRaml(long seed) {
        int[] d = digits12(seed, "raml", 16, 1, 2);
        double home = 1.5*v(d[0]) + 1.0*v(d[4]) + 1.3*v(d[12]) + 0.7*v(d[14]);
        double away = 1.5*v(d[3]) + 1.0*v(d[7]) + 1.3*v(d[13]) + 0.7*v(d[15]);
        double margin = home - away;
        int front = count2(d,0,8);
        int rear = count2(d,8,16);
        boolean late = rear - front >= 3;
        boolean closing = rear <= 3 || rv(seed,"raml",17,5)==0;
        boolean burst = late && rear >= 6;
        int goals = 1 + rv(seed,"raml",18,4);
        if (closing) goals = Math.min(goals,2);
        if (burst) goals = Math.max(goals,3);
        boolean clean = closing && Math.abs(margin)>1.5;
        return new Signal(
                "D4 阿拉伯沙占", groups(d),
                dir(margin,0.65), margin, goals,
                burst && Math.abs(margin)>1.0, closing, late, clean, burst,
                late ? "前静后强：提高高端尾部，但爆发归属仍由方向层单独判断"
                     : closing ? "后程收束：提高低比分/零封结构，不等同于固定主客"
                     : "常规沙占结构"
        );
    }

    private static BingZhanResult castBingZhan(long seed) {
        int[] a = new int[3];
        int[] b = new int[3];
        for (int i=0;i<3;i++) {
            a[i] = rv(seed,"bingzhan",i,10);
            b[i] = rv(seed,"bingzhan",i+3,10);
        }
        int aSum = a[0]+a[1]+a[2], bSum=b[0]+b[1]+b[2];
        int aEdge = a[0]+a[2], bEdge=b[0]+b[2];
        int aSecond = Math.abs(a[0]-2*a[1]+a[2]);
        int bSecond = Math.abs(b[0]-2*b[1]+b[2]);
        int aScatter = Math.max(a[0],Math.max(a[1],a[2])) - Math.min(a[0],Math.min(a[1],a[2]));
        int bScatter = Math.max(b[0],Math.max(b[1],b[2])) - Math.min(b[0],Math.min(b[1],b[2]));
        int aNine = 1 + ((aSum + a[0]*2 + a[2]*3) % 9);
        int bNine = 1 + ((bSum + b[0]*2 + b[2]*3) % 9);
        int hidden = rv(seed,"bingzhan",7,9)-4;

        double margin = (aEdge-bEdge)*0.22 + (aNine-bNine)*0.28
                + (bSecond-aSecond)*0.12 + hidden*0.18;
        boolean unilateral = Math.abs(margin) >= 2.0 || Math.abs(aScatter-bScatter) >= 5;
        int attack = aSum+bSum+aSecond+bSecond+aScatter+bScatter;
        int goals = clamp(1 + (attack % 5),1,5);
        boolean closing = goals <= 2 && (aScatter+bScatter)<=7;
        boolean burst = goals >= 4 || (unilateral && attack%3==0);
        int net = "平局".equals(dir(margin,0.6)) ? 0 :
                clamp((int)Math.round(Math.abs(margin)/1.3) + (unilateral?1:0),1,4);
        String raw = String.format(Locale.CHINA,"%d%d%d｜%d%d%d · 九宫%d/%d",a[0],a[1],a[2],b[0],b[1],b[2],aNine,bNine);
        String note = "逐位五行代理 + 九宫主客 + 二阶差 + 聚散 + 奇偶 + 伏兵暗层；结构与主客归属分开解释";
        return new BingZhanResult(raw,dir(margin,0.60),margin,goals,net,unilateral,closing,burst,note);
    }

    private static DirectionCorrection castDirectionCorrection(long seed, Signal maya, Signal sikidy,
                                                               Signal ifa, Signal raml, BingZhanResult bz) {
        double fourMean = (maya.margin+sikidy.margin+ifa.margin+raml.margin)/4.0;
        double upperLower = (rv(seed,"direction_calibration",0,2001)-1000)/1000.0;
        double marketSideProxy = (rv(seed,"direction_calibration",1,3)-1) * 0.55;
        double margin = fourMean*0.35 + bz.margin*0.45 + upperLower*0.65 + marketSideProxy*0.35;
        String d = dir(margin,0.48);

        boolean familyConflict = sign(fourMean) != 0 && sign(bz.margin) != 0 && sign(fourMean) != sign(bz.margin);
        String mirrorRisk;
        if (familyConflict || Math.abs(margin)<0.65) mirrorRisk="高";
        else if (Math.abs(margin)<1.35 || Math.abs(upperLower)>0.72) mirrorRisk="中";
        else mirrorRisk="低";

        String handicap;
        if ("平局".equals(d)) handicap="盘口方向：谨慎，强方不宜深追";
        else if (Math.abs(margin)>=1.6) handicap="盘口方向：" + ("主胜".equals(d)?"主侧":"客侧") + "可考虑打穿";
        else handicap="盘口方向：" + ("主胜".equals(d)?"主侧":"客侧") + "仅轻倾，不强判打穿";

        int avgGoal = clamp((int)Math.round((maya.goals+sikidy.goals+ifa.goals+raml.goals+bz.goals)/5.0),1,5);
        String raw = String.format(Locale.CHINA,"四术均势 %.2f · 兵占 %.2f · 校正 %.2f",fourMean,bz.margin,margin);
        String note = "D6只校正主客/盘口归属与镜像风险，不把结构信号重复计票";
        return new DirectionCorrection(d,margin,mirrorRisk,handicap,avgGoal,raw,note);
    }

    private static Combined combine(Signal maya, Signal sikidy, Signal ifa, Signal raml,
                                    BingZhanResult bz, DirectionCorrection dc) {
        Signal[] four = {maya,sikidy,ifa,raml};

        double home=0,away=0,draw=0;
        int homeVotes=0,awayVotes=0,drawVotes=0, highFour=0, lowFour=0, odd=0,even=0;
        double fourGoal=0;
        for (Signal s:four) {
            double w=0.10;
            double amp=1.0+Math.min(2.0,Math.abs(s.margin))*0.30;
            if ("主胜".equals(s.direction)) { home += w*amp; homeVotes++; }
            else if ("客胜".equals(s.direction)) { away += w*amp; awayVotes++; }
            else { draw += w*(1.15+Math.max(0,0.7-Math.abs(s.margin))); drawVotes++; }
            fourGoal += s.goals;
            if (s.goals>=3) highFour++; else lowFour++;
            if ((s.goals&1)==1) odd++; else even++;
        }

        double bzAmp=0.30*(1.0+Math.min(2.3,Math.abs(bz.margin))*0.25);
        if ("主胜".equals(bz.direction)) home+=bzAmp; else if ("客胜".equals(bz.direction)) away+=bzAmp; else draw+=bzAmp*1.2;

        double dcAmp=0.30*(1.0+Math.min(2.0,Math.abs(dc.margin))*0.30);
        if ("主胜".equals(dc.direction)) home+=dcAmp; else if ("客胜".equals(dc.direction)) away+=dcAmp; else draw+=dcAmp*1.2;

        if (home>0.25 && away>0.25) draw += 0.12;
        if (drawVotes>=2) draw += 0.08;

        List<Rank> ranks = new ArrayList<>();
        ranks.add(new Rank("主胜",home));
        ranks.add(new Rank("平局",draw));
        ranks.add(new Rank("客胜",away));
        Collections.sort(ranks, new Comparator<Rank>() {
            @Override public int compare(Rank a, Rank b) { return Double.compare(b.score,a.score); }
        });
        String first=ranks.get(0).name, second=ranks.get(1).name, third=ranks.get(2).name;
        String ranking=shortDir(first)+" > "+shortDir(second)+" > "+shortDir(third);

        int samePrimary=0;
        for (Signal s:four) if (s.direction.equals(first)) samePrimary++;
        if (bz.direction.equals(first)) samePrimary++;
        if (dc.direction.equals(first)) samePrimary++;

        String resonance;
        if (samePrimary>=5) resonance="A+ 六维强共振";
        else if (samePrimary==4) resonance="A 四维同向";
        else if (samePrimary==3 && bz.direction.equals(first) && dc.direction.equals(first)) resonance="A- 双校正共振";
        else if (samePrimary>=3) resonance="B+ 中度共振";
        else resonance="C 冲突盘";

        double gap = ranks.get(0).score-ranks.get(1).score;
        String mirrorRisk = dc.mirrorRisk;
        if (gap<0.10) mirrorRisk="高";
        else if (gap<0.22 && !"高".equals(mirrorRisk)) mirrorRisk="中";

        String safer = "平局".equals(first) ? "重点防平" : ("主胜".equals(first)?"主队不败":"客队不败");

        double goalMean = (fourGoal/4.0)*0.45 + bz.goals*0.45 + dc.goalHint*0.10;
        boolean twoHighTwoLow = highFour==2 && lowFour==2;
        boolean highStructure = bz.goals>=4 || bz.burst || ifa.unilateral || raml.lateStrength || raml.burst;
        boolean doubleClosing = countClosing(four)>=2 || (bz.closing && countClosing(four)>=1);

        int center = clamp((int)Math.round(goalMean),1,5);
        String goalRange;
        if (twoHighTwoLow && highStructure && doubleClosing) {
            goalRange="1–4球（双峰）";
            center=Math.max(2,Math.min(3,center));
        } else if (center<=1 && doubleClosing) goalRange="0–2球";
        else if (center<=2) goalRange=highStructure?"1–3球":"1–2球";
        else if (center==3) goalRange=highStructure?"2–4球":"2–3球";
        else if (center==4) goalRange="3–5球";
        else goalRange="4–6球";

        if ((center==2 || center==3) && (ifa.unilateral || raml.lateStrength || bz.unilateral) && (raml.lateStrength || bz.burst)) {
            goalRange="2–4球";
            center=3;
        }

        String goalFocus=center+"球第一落点";
        if (goalRange.contains("双峰")) goalFocus="低端1–2球 / 高端3–4球同时保留";

        String overUnder;
        if (center>=4) overUnder="大2.5明显";
        else if (center==3) overUnder=highStructure?"大2.5偏强":"大2.5微倾";
        else if (center<=1) overUnder="小2.5明显";
        else overUnder=highStructure?"大小接近，小2.5微倾":"小2.5偏强";

        String parity;
        int totalParityVotes = odd-even + ((bz.goals&1)==1?1:-1);
        if (Math.abs(totalParityVotes)>=3) parity=totalParityVotes>0?"单数偏强（降权）":"双数偏强（降权）";
        else parity="单双弱，不作为核心判断";

        int netHint = bz.netGoalHint;
        if ("平局".equals(first)) netHint=0;
        else {
            if (resonance.startsWith("A+")) netHint=Math.max(netHint,2);
            else if (gap<0.16) netHint=Math.min(Math.max(netHint,1),1);
            else netHint=Math.max(1,netHint);
            if (center<=2) netHint=Math.min(netHint,2);
        }
        String netGoal = netHint==0?"净胜0球（平局结构）":netHint>=3?"净胜3+球":"净胜"+netHint+"球";

        boolean clean = raml.cleanSheet || ifa.cleanSheet || maya.cleanSheet || sikidy.cleanSheet;
        boolean unilateral = bz.unilateral || ifa.unilateral || raml.burst;
        List<String> mainScores = scoreCandidates(first, center, netHint, clean, unilateral);
        String primary = mainScores.get(0);
        String secondary = mainScores.size()>1?mainScores.get(1):primary;

        List<String> defense=new ArrayList<>();
        if ("平局".equals(first)) {
            add(defense,"1:1"); add(defense,"0:0");
            if (center>=4) add(defense,"2:2");
        } else {
            if ("平局".equals(second)) add(defense,center>=4?"2:2":"1:1");
            add(defense, center<=2 ? ("主胜".equals(first)?"1:0":"0:1") : ("主胜".equals(first)?"3:0":"0:3"));
        }

        List<String> tails=new ArrayList<>();
        if (center>=4 || highStructure) {
            if ("主胜".equals(first)) { add(tails,"3:2"); add(tails,"4:1"); if (unilateral) add(tails,"4:0"); }
            else if ("客胜".equals(first)) { add(tails,"2:3"); add(tails,"1:4"); if (unilateral) add(tails,"0:4"); }
            else { add(tails,"2:2"); add(tails,"3:3"); }
        } else tails.add("—");

        List<String> mirrors=new ArrayList<>();
        if (!"低".equals(mirrorRisk)) {
            for (String s:mainScores) add(mirrors,mirror(s));
            if (unilateral) {
                if ("主胜".equals(first)) add(mirrors,"0:4");
                else if ("客胜".equals(first)) add(mirrors,"4:0");
            }
        } else mirrors.add("—");

        String halfFull;
        String halfBackup;
        if ("主胜".equals(first)) { halfFull="平/胜"; halfBackup="胜/胜"; }
        else if ("客胜".equals(first)) { halfFull="平/负"; halfBackup="负/负"; }
        else { halfFull="平/平"; halfBackup= "客胜".equals(second)?"平/负":"平/胜"; }

        String structure = "结构层：" + (unilateral?"单边倾向":"拉扯/均衡")
                + " · " + (doubleClosing?"有收束":"无双重收束")
                + " · " + (highStructure?"保留高端尾部":"常规尾部")
                + "；方向层与结构层独立。";

        int tendency = clamp((int)Math.round(55 + gap*90 + samePrimary*3 - ("高".equals(mirrorRisk)?8:0)),40,96);

        String rationale = "权重：胜平负=四术40% + 数字兵占30% + 方向校正30%；"
                + "总进球=四术45% + 数字兵占45% + 校正10%。"
                + " 共振" + resonance + "，镜像风险" + mirrorRisk + "。";

        String scorePool=primary+" / "+secondary+"｜防 "+join(defense)+"｜尾 "+join(tails)+"｜镜 "+join(mirrors);
        return new Combined(
                ranking,safer,resonance,mirrorRisk,dc.handicap,
                halfFull,halfBackup,goalRange,goalFocus,overUnder,parity,netGoal,
                primary,secondary,join(defense),join(tails),join(mirrors),scorePool,
                structure,rationale,tendency
        );
    }

    private static List<String> scoreCandidates(String direction, int center, int net, boolean clean, boolean unilateral) {
        List<String> out=new ArrayList<>();
        if ("平局".equals(direction)) {
            if (center<=1) { add(out,"0:0"); add(out,"1:1"); }
            else if (center<=3) { add(out,"1:1"); add(out,"0:0"); add(out,"2:2"); }
            else { add(out,"2:2"); add(out,"1:1"); add(out,"3:3"); }
            return out;
        }

        boolean home="主胜".equals(direction);
        if (center<=1) { add(out,home?"1:0":"0:1"); }
        else if (center==2) {
            add(out,home?"2:0":"0:2");
            add(out,home?"1:0":"0:1");
        } else if (center==3) {
            if (net>=3 || (clean&&unilateral)) add(out,home?"3:0":"0:3");
            add(out,home?"2:1":"1:2");
            add(out,home?"3:0":"0:3");
        } else if (center==4) {
            if (net>=3 && clean) add(out,home?"4:0":"0:4");
            add(out,home?"3:1":"1:3");
            add(out,home?"4:0":"0:4");
            add(out,home?"3:0":"0:3");
        } else {
            add(out,home?"3:2":"2:3");
            add(out,home?"4:1":"1:4");
            add(out,home?"5:0":"0:5");
        }
        return out;
    }

    private static final class Rank {
        final String name; final double score;
        Rank(String name,double score){this.name=name;this.score=score;}
    }

    private static int countClosing(Signal[] xs) {
        int c=0; for (Signal s:xs) if (s.closing) c++; return c;
    }

    private static int[] digits12(long seed,String ns,int count,int min,int max) {
        int[] d=new int[count];
        int span=max-min+1;
        for (int i=0;i<count;i++) d[i]=min+rv(seed,ns,i,span);
        return d;
    }

    private static String groups(int[] d) {
        StringBuilder s=new StringBuilder();
        for (int i=0;i<d.length;i++) {
            if (i>0 && i%4==0) s.append(" / ");
            s.append(d[i]);
        }
        return s.toString();
    }

    private static int count2(int[] d,int from,int to) {
        int c=0; for (int i=from;i<to;i++) if (d[i]==2)c++; return c;
    }

    private static int v(int x) { return x==2?1:-1; }

    private static String dir(double margin,double threshold) {
        if (margin>threshold) return "主胜";
        if (margin<-threshold) return "客胜";
        return "平局";
    }

    private static String shortDir(String d) {
        return "平局".equals(d)?"平":d;
    }

    private static int sign(double x) { return x>0?1:x<0?-1:0; }

    private static String mirror(String s) {
        String[] p=s.split(":");
        if (p.length!=2) return s;
        return p[1]+":"+p[0];
    }

    private static void add(List<String> list,String s) {
        if (!list.contains(s)) list.add(s);
    }

    private static String join(List<String> xs) {
        StringBuilder b=new StringBuilder();
        for (int i=0;i<xs.size();i++) {
            if (i>0) b.append(" / ");
            b.append(xs.get(i));
        }
        return b.toString();
    }

    private static int rv(long seed,String namespace,int counter,int n) {
        if (n<=0) return 0;
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            String s=Long.toUnsignedString(seed)+"|"+namespace+"|"+counter;
            byte[] d=md.digest(s.getBytes(StandardCharsets.UTF_8));
            long x=0L;
            for (int i=0;i<8;i++) x=(x<<8)|(d[i]&0xffL);
            return (int)Long.remainderUnsigned(x,(long)n);
        } catch(Exception e) {
            long x=seed ^ namespace.hashCode() ^ (counter*0x9e3779b97f4a7c15L);
            return (int)Long.remainderUnsigned(x,(long)n);
        }
    }

    private static int clamp(int x,int a,int b){return Math.max(a,Math.min(b,x));}
}
