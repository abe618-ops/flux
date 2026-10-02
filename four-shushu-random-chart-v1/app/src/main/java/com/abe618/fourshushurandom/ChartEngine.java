package com.abe618.fourshushurandom;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class ChartEngine {
    private ChartEngine() {}

    public static final class State {
        public final long seed;
        public final NineYao nineYao;
        public final LingQi lingQi;
        public final SevenStar sevenStar;
        public final Triangle triangle;

        State(long seed, NineYao nineYao, LingQi lingQi, SevenStar sevenStar, Triangle triangle) {
            this.seed = seed;
            this.nineYao = nineYao;
            this.lingQi = lingQi;
            this.sevenStar = sevenStar;
            this.triangle = triangle;
        }

        public String fullText() {
            return "四术随机排盘\nSeed: " + seed
                    + "\n\n【九爻易】\n" + nineYao.text
                    + "\n\n【灵棋经】\n" + lingQi.text
                    + "\n\n【七星数·试验盘】\n" + sevenStar.text
                    + "\n\n【万事三角定律·数字盘】\n" + triangle.text;
        }
    }

    public static final class NineYao {
        public final String text;
        NineYao(String text) { this.text = text; }
    }

    public static final class LingQi {
        public final String text;
        LingQi(String text) { this.text = text; }
    }

    public static final class SevenStar {
        public final String text;
        SevenStar(String text) { this.text = text; }
    }

    public static final class Triangle {
        public final String text;
        Triangle(String text) { this.text = text; }
    }

    private static final String[] GUA_NAME = {"坤", "艮", "坎", "巽", "震", "离", "兑", "乾"};
    private static final String[] GUA_SYMBOL = {"☷", "☶", "☵", "☴", "☳", "☲", "☱", "☰"};

    public static State castRandom() {
        return cast(new SecureRandom().nextLong());
    }

    public static State cast(long seed) {
        NineYao nine = castNineYao(mix64(seed ^ 0x4E494E4559414FL));
        LingQi ling = castLingQi(mix64(seed ^ 0x4C494E475149L));
        SevenStar seven = castSevenStar(mix64(seed ^ 0x534556454E5354L));
        Triangle tri = castTriangle(mix64(seed ^ 0x545249414E474CL));
        return new State(seed, nine, ling, seven, tri);
    }

    public static long seedFromText(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            long x = 0;
            for (int i = 0; i < 8; i++) x = (x << 8) | (digest[i] & 0xffL);
            return x;
        } catch (Exception e) {
            return ((long) text.hashCode() << 32) ^ 0x9E3779B97F4A7C15L;
        }
    }

    private static NineYao castNineYao(long seed) {
        Random r = new Random(seed);
        int earth = r.nextInt(8);
        int human = r.nextInt(8);
        int heaven = r.nextInt(8);
        int idx = heaven * 64 + human * 8 + earth + 1;

        StringBuilder sb = new StringBuilder();
        sb.append("天卦：").append(GUA_SYMBOL[heaven]).append(" ").append(GUA_NAME[heaven])
                .append("  码 ").append(bits(heaven)).append("\n");
        appendTrigram(sb, heaven);
        sb.append("人卦：").append(GUA_SYMBOL[human]).append(" ").append(GUA_NAME[human])
                .append("  码 ").append(bits(human)).append("\n");
        appendTrigram(sb, human);
        sb.append("地卦：").append(GUA_SYMBOL[earth]).append(" ").append(GUA_NAME[earth])
                .append("  码 ").append(bits(earth)).append("\n");
        appendTrigram(sb, earth);
        sb.append("程序组合号：").append(idx).append(" / 512\n")
                .append("说明：三组八卦独立随机；组合号仅用于软件复现，不冒充古籍卦序。");
        return new NineYao(sb.toString());
    }

    private static void appendTrigram(StringBuilder sb, int code) {
        for (int line = 2; line >= 0; line--) {
            boolean yang = ((code >> line) & 1) == 1;
            sb.append(yang ? "    ━━━━━\n" : "    ━━  ━━\n");
        }
    }

    private static String bits(int code) {
        return "" + ((code >> 2) & 1) + ((code >> 1) & 1) + (code & 1);
    }

    private static LingQi castLingQi(long seed) {
        Random r = new Random(seed);
        boolean[] up = flips4(r);
        boolean[] mid = flips4(r);
        boolean[] down = flips4(r);
        int u = count(up), m = count(mid), d = count(down);
        int index = u * 25 + m * 5 + d + 1;

        StringBuilder sb = new StringBuilder();
        sb.append("上：").append(marks(up)).append("  得 ").append(u).append("\n")
                .append("中：").append(marks(mid)).append("  得 ").append(m).append("\n")
                .append("下：").append(marks(down)).append("  得 ").append(d).append("\n")
                .append("组合：").append(u).append("上 · ").append(m).append("中 · ").append(d).append("下\n")
                .append("程序组合号：").append(index).append(" / 125\n")
                .append("随机方式：真实模拟12枚两面棋，每组4枚逐枚掷出，因此0~4不是等概率抽取。");
        return new LingQi(sb.toString());
    }

    private static boolean[] flips4(Random r) {
        boolean[] x = new boolean[4];
        for (int i = 0; i < 4; i++) x[i] = r.nextBoolean();
        return x;
    }

    private static int count(boolean[] x) {
        int n = 0;
        for (boolean b : x) if (b) n++;
        return n;
    }

    private static String marks(boolean[] x) {
        StringBuilder sb = new StringBuilder();
        for (boolean b : x) sb.append(b ? "● " : "○ ");
        return sb.toString().trim();
    }

    private static SevenStar castSevenStar(long seed) {
        Random r = new Random(seed);
        int red = r.nextInt(6) + 1;
        int white = r.nextInt(6) + 1;
        List<Integer> cards = new ArrayList<>();
        for (int i = 1; i <= 21; i++) cards.add(i);
        Collections.shuffle(cards, r);

        StringBuilder sb = new StringBuilder();
        sb.append("红骰：").append(red).append("    白骰：").append(white).append("\n")
                .append("上组7牌：").append(join(cards, 0, 7)).append("\n")
                .append("中组7牌：").append(join(cards, 7, 14)).append("\n")
                .append("下组7牌：").append(join(cards, 14, 21)).append("\n\n")
                .append("七列：\n");
        for (int i = 0; i < 7; i++) {
            sb.append(i + 1).append("星  ")
                    .append(cards.get(i)).append(" / ")
                    .append(cards.get(7 + i)).append(" / ")
                    .append(cards.get(14 + i));
            if (i < 6) sb.append("\n");
        }
        sb.append("\n说明：这是21牌+双骰的原始随机试验盘。公开资料不足以核定完整古法牌名、符兆和断辞，本版不擅自补造。");
        return new SevenStar(sb.toString());
    }

    private static String join(List<Integer> xs, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) sb.append(" · ");
            sb.append(xs.get(i));
        }
        return sb.toString();
    }

    private static Triangle castTriangle(long seed) {
        Random r = new Random(seed);
        int heaven = r.nextInt(13);
        int earth = r.nextInt(13);
        int human = r.nextInt(13);
        StringBuilder sb = new StringBuilder();
        sb.append("天时：").append(descNumber(heaven)).append("\n")
                .append("地时：").append(descNumber(earth)).append("\n")
                .append("人时：").append(descNumber(human)).append("\n")
                .append("三数：").append(heaven).append(" - ").append(earth).append(" - ").append(human).append("\n")
                .append("双数程序位：").append(heaven * 13 + earth + 1).append(" / 169\n")
                .append("说明：按0~12十三数随机。1~12采用公开资料中的数字五行与阴阳分组；0在公开基础表中没有统一五行归属，本版保留为空位，不强配五行。");
        return new Triangle(sb.toString());
    }

    private static String descNumber(int n) {
        if (n == 0) return "0 · 空位/保留";
        return n + " · " + element(n) + " · " + yinYang(n);
    }

    private static String element(int n) {
        if (n == 1 || n == 2 || n == 12) return "水";
        if (n == 4 || n == 5) return "木";
        if (n == 6 || n == 7) return "火";
        if (n == 3 || n == 8 || n == 9) return "土";
        if (n == 10 || n == 11) return "金";
        return "未定";
    }

    private static String yinYang(int n) {
        if (n == 1 || n == 2 || n == 3 || n == 4 || n == 11 || n == 12) return "阳数";
        if (n >= 5 && n <= 10) return "阴数";
        return "未定";
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdl;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53l;
        return z ^ (z >>> 33);
    }
}
