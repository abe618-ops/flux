package com.abe618.heluo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class HeluoEngine {
    private HeluoEngine() {}

    private static final String[] STEMS = {"甲","乙","丙","丁","戊","己","庚","辛","壬","癸"};
    private static final String[] BRANCHES = {"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};

    private static final String[] TRIGRAM_BY_NUM = {"","坎","坤","震","巽","中","乾","兑","艮","离"};
    private static final String[] SPIRITS = {"青龙","朱雀","勾陈","螣蛇","白虎","玄武"};

    public static final class Pillar {
        public final String stem;
        public final String branch;
        Pillar(String stem, String branch) {
            this.stem = stem;
            this.branch = branch;
        }
        public String text() { return stem + branch; }
    }

    public static final class LineInfo {
        public final boolean yang;
        public final String spirit;
        public final String kin;
        public final String age;
        public final boolean moving;
        LineInfo(boolean yang, String spirit, String kin, String age, boolean moving) {
            this.yang = yang;
            this.spirit = spirit;
            this.kin = kin;
            this.age = age;
            this.moving = moving;
        }
    }

    public static final class Prediction {
        public final int home;
        public final int draw;
        public final int away;
        public final String result;
        public final String goals;
        public final int over25;
        public final String size;
        public final String btts;
        public final String halfFull;
        public final String[] scores;
        public final String oddEven;
        Prediction(int home, int draw, int away, String result, String goals, int over25,
                   String size, String btts, String halfFull, String[] scores, String oddEven) {
            this.home = home;
            this.draw = draw;
            this.away = away;
            this.result = result;
            this.goals = goals;
            this.over25 = over25;
            this.size = size;
            this.btts = btts;
            this.halfFull = halfFull;
            this.scores = scores;
            this.oddEven = oddEven;
        }
    }

    public static final class Result {
        public final long seed;
        public final Pillar[] pillars;
        public final int tian;
        public final int di;
        public final int tianGuaNum;
        public final int diGuaNum;
        public final String preUpper;
        public final String preLower;
        public final String preName;
        public final String postUpper;
        public final String postLower;
        public final String postName;
        public final int movingLine;
        public final int postMovingLine;
        public final boolean[] preLines;
        public final boolean[] postLines;
        public final LineInfo[] preLineInfo;
        public final LineInfo[] postLineInfo;
        public final Prediction prediction;
        public final String modeInfo;

        Result(long seed, Pillar[] pillars, int tian, int di, int tianGuaNum, int diGuaNum,
               String preUpper, String preLower, String preName,
               String postUpper, String postLower, String postName,
               int movingLine, int postMovingLine,
               boolean[] preLines, boolean[] postLines,
               LineInfo[] preLineInfo, LineInfo[] postLineInfo,
               Prediction prediction, String modeInfo) {
            this.seed = seed;
            this.pillars = pillars;
            this.tian = tian;
            this.di = di;
            this.tianGuaNum = tianGuaNum;
            this.diGuaNum = diGuaNum;
            this.preUpper = preUpper;
            this.preLower = preLower;
            this.preName = preName;
            this.postUpper = postUpper;
            this.postLower = postLower;
            this.postName = postName;
            this.movingLine = movingLine;
            this.postMovingLine = postMovingLine;
            this.preLines = preLines;
            this.postLines = postLines;
            this.preLineInfo = preLineInfo;
            this.postLineInfo = postLineInfo;
            this.prediction = prediction;
            this.modeInfo = modeInfo;
        }
    }

    public static Result generate(long seed) {
        Random r = new Random(seed);
        Pillar[] pillars = new Pillar[4];
        for (int i = 0; i < 4; i++) {
            int jz = r.nextInt(60);
            pillars[i] = new Pillar(STEMS[jz % 10], BRANCHES[jz % 12]);
        }

        int tian = 0;
        int di = 0;
        for (Pillar p : pillars) {
            int s = stemNumber(p.stem);
            if ((s & 1) == 1) tian += s; else di += s;
            int[] bs = branchNumbers(p.branch);
            for (int n : bs) {
                if ((n & 1) == 1) tian += n; else di += n;
            }
        }

        int tianNum = normalizeTian(tian);
        int diNum = normalizeDi(di);

        boolean yangYear = isYangStem(pillars[0].stem);
        String tianTri = resolveFive(tianNum, r, yangYear, true);
        String diTri = resolveFive(diNum, r, yangYear, false);

        // 固定采用“男命式”上下配置，便于复现传统河洛理数的成卦框架：
        // 阳年：天数卦在上；阴年：天数卦在下。
        String preUpper = yangYear ? tianTri : diTri;
        String preLower = yangYear ? diTri : tianTri;

        boolean[] pre = makeHex(preUpper, preLower);

        // 第一版：元堂由随机种子决定，保持完全可复现；后续可再加入完整时辰元堂表。
        int moving = r.nextInt(6);
        boolean[] flipped = pre.clone();
        flipped[moving] = !flipped[moving];

        boolean[] post = new boolean[6];
        System.arraycopy(flipped, 3, post, 0, 3);
        System.arraycopy(flipped, 0, post, 3, 3);
        int postMoving = moving < 3 ? moving + 3 : moving - 3;

        String postLower = trigramFromLines(post, 0);
        String postUpper = trigramFromLines(post, 3);

        String preName = hexName(preUpper, preLower);
        String postName = hexName(postUpper, postLower);

        String[] preAges = ageRanges(pre, moving, 1);
        int lastAge = maxAge(preAges);
        String[] postAges = ageRanges(post, postMoving, lastAge + 1);

        String[] spiritOrder = spiritOrderForDayStem(pillars[2].stem);
        String selfElement = stemElement(pillars[2].stem);

        LineInfo[] preInfo = new LineInfo[6];
        LineInfo[] postInfo = new LineInfo[6];
        for (int i = 0; i < 6; i++) {
            String preElem = i < 3 ? trigramElement(preLower) : trigramElement(preUpper);
            String postElem = i < 3 ? trigramElement(postLower) : trigramElement(postUpper);
            preInfo[i] = new LineInfo(pre[i], spiritOrder[i], kin(selfElement, preElem), preAges[i], i == moving);
            postInfo[i] = new LineInfo(post[i], spiritOrder[i], kin(selfElement, postElem), postAges[i], i == postMoving);
        }

        Prediction prediction = predict(preUpper, preLower, postUpper, postLower, pre, post, moving, seed);

        String modeInfo = yangYear
                ? "阳年男式：天数卦在上、地数卦在下"
                : "阴年男式：地数卦在上、天数卦在下";

        return new Result(seed, pillars, tian, di, tianNum, diNum,
                preUpper, preLower, preName,
                postUpper, postLower, postName,
                moving, postMoving, pre, post,
                preInfo, postInfo, prediction, modeInfo);
    }

    private static int stemNumber(String s) {
        switch (s) {
            case "甲": case "壬": return 6;
            case "乙": case "癸": return 2;
            case "丙": return 8;
            case "丁": return 7;
            case "戊": return 1;
            case "己": return 9;
            case "庚": return 3;
            case "辛": return 4;
            default: return 5;
        }
    }

    private static int[] branchNumbers(String b) {
        if ("子".equals(b) || "亥".equals(b)) return new int[]{1,6};
        if ("寅".equals(b) || "卯".equals(b)) return new int[]{3,8};
        if ("巳".equals(b) || "午".equals(b)) return new int[]{2,7};
        if ("申".equals(b) || "酉".equals(b)) return new int[]{4,9};
        return new int[]{5,10};
    }

    private static boolean isYangStem(String s) {
        return "甲丙戊庚壬".contains(s);
    }

    private static int normalizeTian(int n) {
        if (n == 25) return 5;
        int x = n > 25 ? n - 25 : n;
        return normalizeDigit(x);
    }

    private static int normalizeDi(int n) {
        if (n == 30) return 3;
        int x = n > 30 ? n - 30 : n;
        return normalizeDigit(x);
    }

    private static int normalizeDigit(int x) {
        if (x <= 0) return 1;
        if (x % 10 == 0) {
            int t = x / 10;
            while (t > 9) t %= 10;
            return Math.max(1, t);
        }
        int d = x % 10;
        return d == 0 ? 1 : d;
    }

    private static String resolveFive(int n, Random r, boolean yangYear, boolean isTian) {
        if (n != 5) return TRIGRAM_BY_NUM[n];
        // 遇五寄宫。随机占测版不输入性别、三元，按阴阳年与天地位给出稳定寄宫。
        if (yangYear) return isTian ? "艮" : "坤";
        return isTian ? "坤" : "艮";
    }

    private static boolean[] trigramLines(String tri) {
        switch (tri) {
            case "乾": return new boolean[]{true,true,true};
            case "兑": return new boolean[]{true,true,false};
            case "离": return new boolean[]{true,false,true};
            case "震": return new boolean[]{true,false,false};
            case "巽": return new boolean[]{false,true,true};
            case "坎": return new boolean[]{false,true,false};
            case "艮": return new boolean[]{false,false,true};
            case "坤": default: return new boolean[]{false,false,false};
        }
    }

    private static boolean[] makeHex(String upper, String lower) {
        boolean[] out = new boolean[6];
        boolean[] lo = trigramLines(lower);
        boolean[] up = trigramLines(upper);
        System.arraycopy(lo, 0, out, 0, 3);
        System.arraycopy(up, 0, out, 3, 3);
        return out;
    }

    private static String trigramFromLines(boolean[] six, int offset) {
        boolean a = six[offset];
        boolean b = six[offset + 1];
        boolean c = six[offset + 2];
        if (a && b && c) return "乾";
        if (a && b) return "兑";
        if (a && c) return "离";
        if (a) return "震";
        if (b && c) return "巽";
        if (b) return "坎";
        if (c) return "艮";
        return "坤";
    }

    public static String trigramElement(String tri) {
        if ("乾".equals(tri) || "兑".equals(tri)) return "金";
        if ("震".equals(tri) || "巽".equals(tri)) return "木";
        if ("坎".equals(tri)) return "水";
        if ("离".equals(tri)) return "火";
        return "土";
    }

    private static String stemElement(String stem) {
        if ("甲乙".contains(stem)) return "木";
        if ("丙丁".contains(stem)) return "火";
        if ("戊己".contains(stem)) return "土";
        if ("庚辛".contains(stem)) return "金";
        return "水";
    }

    private static String kin(String self, String other) {
        if (self.equals(other)) return "兄弟";
        if (generates(other, self)) return "父母";
        if (generates(self, other)) return "子孙";
        if (controls(self, other)) return "妻财";
        if (controls(other, self)) return "官鬼";
        return "兄弟";
    }

    private static boolean generates(String a, String b) {
        return ("木".equals(a) && "火".equals(b))
                || ("火".equals(a) && "土".equals(b))
                || ("土".equals(a) && "金".equals(b))
                || ("金".equals(a) && "水".equals(b))
                || ("水".equals(a) && "木".equals(b));
    }

    private static boolean controls(String a, String b) {
        return ("木".equals(a) && "土".equals(b))
                || ("土".equals(a) && "水".equals(b))
                || ("水".equals(a) && "火".equals(b))
                || ("火".equals(a) && "金".equals(b))
                || ("金".equals(a) && "木".equals(b));
    }

    private static String[] spiritOrderForDayStem(String dayStem) {
        int start;
        if ("甲乙".contains(dayStem)) start = 0;
        else if ("丙丁".contains(dayStem)) start = 1;
        else if ("戊".equals(dayStem)) start = 2;
        else if ("己".equals(dayStem)) start = 3;
        else if ("庚辛".contains(dayStem)) start = 4;
        else start = 5;

        String[] out = new String[6];
        for (int i = 0; i < 6; i++) out[i] = SPIRITS[(start + i) % 6];
        return out;
    }

    private static String[] ageRanges(boolean[] lines, int moving, int startAge) {
        String[] out = new String[6];
        int age = startAge;
        for (int k = 0; k < 6; k++) {
            int idx = (moving + k) % 6;
            int years = lines[idx] ? 9 : 6;
            int end = age + years - 1;
            out[idx] = String.format(Locale.US, "%02d-%02d", age, end);
            age = end + 1;
        }
        return out;
    }

    private static int maxAge(String[] ranges) {
        int m = 0;
        for (String s : ranges) {
            int p = s.indexOf('-');
            if (p > 0) {
                try { m = Math.max(m, Integer.parseInt(s.substring(p + 1))); }
                catch (Exception ignored) {}
            }
        }
        return m;
    }

    private static Prediction predict(String preUpper, String preLower,
                                      String postUpper, String postLower,
                                      boolean[] pre, boolean[] post,
                                      int moving, long seed) {
        Random r = new Random(seed ^ 0x5DEECE66DL);
        String h = trigramElement(preUpper);
        String a = trigramElement(preLower);

        double relation = relationScore(h, a);
        int upperYang = countYang(Arrays.copyOfRange(pre, 3, 6));
        int lowerYang = countYang(Arrays.copyOfRange(pre, 0, 3));
        double lineBias = (upperYang - lowerYang) * 0.55;
        double moveBias = pre[moving] ? 0.45 : -0.25;
        double noise = (r.nextDouble() - 0.5) * 1.8;
        double balance = relation + lineBias + moveBias + noise;

        double homeRaw = 33.0 + balance * 5.0;
        double awayRaw = 33.0 - balance * 5.0;
        double drawRaw = 30.0 - Math.min(11.0, Math.abs(balance) * 3.0);
        homeRaw = Math.max(8.0, homeRaw);
        awayRaw = Math.max(8.0, awayRaw);
        drawRaw = Math.max(10.0, drawRaw);
        double sum = homeRaw + awayRaw + drawRaw;
        int home = (int)Math.round(homeRaw * 100.0 / sum);
        int draw = (int)Math.round(drawRaw * 100.0 / sum);
        int away = 100 - home - draw;

        String full;
        if (home >= draw && home >= away) full = "主胜";
        else if (away >= draw) full = "客胜";
        else full = "平";

        double exp = 2.15;
        if ("火".equals(h) || "火".equals(a)) exp += 0.28;
        if ("木".equals(h) || "木".equals(a)) exp += 0.12;
        if ("水".equals(h) && "水".equals(a)) exp -= 0.28;
        if ("土".equals(h) && "土".equals(a)) exp -= 0.18;
        exp += Math.min(0.55, Math.abs(balance) * 0.14);
        exp += (r.nextDouble() - 0.5) * 0.35;
        exp = clamp(exp, 1.05, 4.20);

        int low = Math.max(0, (int)Math.floor(exp - 0.55));
        int high = Math.min(6, (int)Math.ceil(exp + 0.70));
        if (low == high) high = Math.min(6, low + 1);
        String goals = low + "-" + high + "球";

        int over = (int)Math.round(clamp(50 + (exp - 2.5) * 30, 18, 82));
        String size = over >= 55 ? "偏大（>2.5）" : (over <= 45 ? "偏小（≤2.5）" : "大小接近");

        String half;
        double halfBalance = balance * 0.55 + (r.nextDouble() - 0.5) * 1.2;
        if (Math.abs(halfBalance) < 0.72) half = "平";
        else half = halfBalance > 0 ? "主" : "客";
        String fullShort = "主胜".equals(full) ? "主" : ("客胜".equals(full) ? "客" : "平");
        String halfFull = half + "/" + fullShort;

        String btts = (exp > 2.25 && Math.abs(balance) < 2.4) ? "是" : "否";
        int roundedGoals = Math.max(0, (int)Math.round(exp));
        String oddEven = (roundedGoals % 2 == 0) ? "双" : "单";

        String[] scores;
        if ("主胜".equals(full)) {
            scores = exp < 2.35
                    ? new String[]{"1:0","2:0","2:1"}
                    : new String[]{"2:1","3:1","2:0"};
        } else if ("客胜".equals(full)) {
            scores = exp < 2.35
                    ? new String[]{"0:1","0:2","1:2"}
                    : new String[]{"1:2","1:3","0:2"};
        } else {
            scores = exp < 2.15
                    ? new String[]{"0:0","1:1","1:0"}
                    : new String[]{"1:1","2:2","0:0"};
        }

        return new Prediction(home, draw, away, full, goals, over, size, btts, halfFull, scores, oddEven);
    }

    private static double relationScore(String home, String away) {
        if (home.equals(away)) return 0.25;
        if (controls(home, away)) return 1.65;
        if (controls(away, home)) return -1.65;
        if (generates(away, home)) return 0.85;
        if (generates(home, away)) return -0.85;
        return 0.0;
    }

    private static int countYang(boolean[] a) {
        int c = 0;
        for (boolean b : a) if (b) c++;
        return c;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static String hexName(String upper, String lower) {
        String key = upper + lower;
        switch (key) {
            case "乾乾": return "乾为天";
            case "乾兑": return "天泽履";
            case "乾离": return "天火同人";
            case "乾震": return "天雷无妄";
            case "乾巽": return "天风姤";
            case "乾坎": return "天水讼";
            case "乾艮": return "天山遁";
            case "乾坤": return "天地否";

            case "兑乾": return "泽天夬";
            case "兑兑": return "兑为泽";
            case "兑离": return "泽火革";
            case "兑震": return "泽雷随";
            case "兑巽": return "泽风大过";
            case "兑坎": return "泽水困";
            case "兑艮": return "泽山咸";
            case "兑坤": return "泽地萃";

            case "离乾": return "火天大有";
            case "离兑": return "火泽睽";
            case "离离": return "离为火";
            case "离震": return "火雷噬嗑";
            case "离巽": return "火风鼎";
            case "离坎": return "火水未济";
            case "离艮": return "火山旅";
            case "离坤": return "火地晋";

            case "震乾": return "雷天大壮";
            case "震兑": return "雷泽归妹";
            case "震离": return "雷火丰";
            case "震震": return "震为雷";
            case "震巽": return "雷风恒";
            case "震坎": return "雷水解";
            case "震艮": return "雷山小过";
            case "震坤": return "雷地豫";

            case "巽乾": return "风天小畜";
            case "巽兑": return "风泽中孚";
            case "巽离": return "风火家人";
            case "巽震": return "风雷益";
            case "巽巽": return "巽为风";
            case "巽坎": return "风水涣";
            case "巽艮": return "风山渐";
            case "巽坤": return "风地观";

            case "坎乾": return "水天需";
            case "坎兑": return "水泽节";
            case "坎离": return "水火既济";
            case "坎震": return "水雷屯";
            case "坎巽": return "水风井";
            case "坎坎": return "坎为水";
            case "坎艮": return "水山蹇";
            case "坎坤": return "水地比";

            case "艮乾": return "山天大畜";
            case "艮兑": return "山泽损";
            case "艮离": return "山火贲";
            case "艮震": return "山雷颐";
            case "艮巽": return "山风蛊";
            case "艮坎": return "山水蒙";
            case "艮艮": return "艮为山";
            case "艮坤": return "山地剥";

            case "坤乾": return "地天泰";
            case "坤兑": return "地泽临";
            case "坤离": return "地火明夷";
            case "坤震": return "地雷复";
            case "坤巽": return "地风升";
            case "坤坎": return "地水师";
            case "坤艮": return "地山谦";
            case "坤坤": return "坤为地";
            default: return upper + "上" + lower + "下";
        }
    }
}
