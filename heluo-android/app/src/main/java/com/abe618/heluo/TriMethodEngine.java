package com.abe618.heluo;

import java.security.SecureRandom;
import java.util.Locale;

public final class TriMethodEngine {
    private TriMethodEngine() {}

    private static final SecureRandom RNG = new SecureRandom();

    private static final int[] YANG_COUNT = {0, 3, 2, 2, 1, 2, 1, 1, 0};
    private static final int[] AFTER_NUM = {0, 6, 7, 9, 3, 4, 1, 8, 2};
    private static final String[] TRI_NAME = {"", "乾", "兑", "离", "震", "巽", "坎", "艮", "坤"};

    public static final class TaiXuanResult {
        public final int state;
        public final int[] ternary;
        public final String size;

        TaiXuanResult(int state, int[] ternary, String size) {
            this.state = state;
            this.ternary = ternary;
            this.size = size;
        }

        public String detail() {
            return String.format(Locale.CHINA, "第%d态 · %d%d%d%d · %s",
                    state, ternary[0], ternary[1], ternary[2], ternary[3], size);
        }
    }

    public static final class CeGuiResult {
        public final int upper;
        public final int lower;
        public final int moving;
        public final int originalCe;
        public final int originalGui;
        public final int ceFigure;
        public final int guiFigure;
        public final int ceYuan;
        public final int guiYuan;
        public final String parity;

        CeGuiResult(int upper, int lower, int moving, int originalCe, int originalGui,
                    int ceFigure, int guiFigure, int ceYuan, int guiYuan, String parity) {
            this.upper = upper;
            this.lower = lower;
            this.moving = moving;
            this.originalCe = originalCe;
            this.originalGui = originalGui;
            this.ceFigure = ceFigure;
            this.guiFigure = guiFigure;
            this.ceYuan = ceYuan;
            this.guiYuan = guiYuan;
            this.parity = parity;
        }

        public String detail() {
            return String.format(Locale.CHINA,
                    "%s上%s下 · %d爻动 · 策%d(元%d) / 轨%d(元%d) · %s",
                    TRI_NAME[upper], TRI_NAME[lower], moving,
                    ceFigure, ceYuan, guiFigure, guiYuan, parity);
        }
    }

    public static final class ZhouYiCeResult {
        public final int[] lines;
        public final int total;
        public final int oddLines;
        public final String parity;

        ZhouYiCeResult(int[] lines, int total, int oddLines, String parity) {
            this.lines = lines;
            this.total = total;
            this.oddLines = oddLines;
            this.parity = parity;
        }

        public String detail() {
            return String.format(Locale.CHINA,
                    "六爻 %d-%d-%d-%d-%d-%d · 策值和%d · %s",
                    lines[0], lines[1], lines[2], lines[3], lines[4], lines[5], total, parity);
        }
    }

    public static final class Result {
        public final TaiXuanResult taiXuan;
        public final CeGuiResult ceGui;
        public final ZhouYiCeResult zhouYiCe;
        public final String parity;
        public final int attempts;

        Result(TaiXuanResult taiXuan, CeGuiResult ceGui, ZhouYiCeResult zhouYiCe,
               String parity, int attempts) {
            this.taiXuan = taiXuan;
            this.ceGui = ceGui;
            this.zhouYiCe = zhouYiCe;
            this.parity = parity;
            this.attempts = attempts;
        }
    }

    public static Result drawConsensus() {
        TaiXuanResult tx = drawTaiXuan();
        int attempts = 0;
        while (true) {
            attempts++;
            CeGuiResult cg = drawCeGui();
            ZhouYiCeResult zy = drawZhouYiCe();
            if (cg.parity.equals(zy.parity)) {
                return new Result(tx, cg, zy, cg.parity, attempts);
            }
        }
    }

    static TaiXuanResult drawTaiXuan() {
        while (true) {
            int state = RNG.nextInt(81) + 1;
            if (state == 41) continue;
            int x = state - 1;
            int[] t = new int[4];
            for (int i = 3; i >= 0; i--) {
                t[i] = (x % 3) + 1;
                x /= 3;
            }
            return new TaiXuanResult(state, t, state < 41 ? "小" : "大");
        }
    }

    static CeGuiResult drawCeGui() {
        int upper = RNG.nextInt(8) + 1;
        int lower = RNG.nextInt(8) + 1;
        int moving = RNG.nextInt(6) + 1;
        int yang = YANG_COUNT[upper] + YANG_COUNT[lower];
        int yin = 6 - yang;
        int originalCe = yang * 36 + yin * 24;
        int originalGui = yang * 128 + yin * 112;

        int ceFigure = figure(upper, lower, moving, originalCe, false);
        int guiFigure = figure(upper, lower, moving, originalGui, true);
        int ceYuan = yuanDigit(ceFigure);
        int guiYuan = yuanDigit(guiFigure);
        String parity = ((ceYuan + guiYuan) & 1) == 1 ? "单" : "双";
        return new CeGuiResult(upper, lower, moving, originalCe, originalGui,
                ceFigure, guiFigure, ceYuan, guiYuan, parity);
    }

    private static int figure(int upper, int lower, int moving, int original, boolean after) {
        int u = after ? AFTER_NUM[upper] : upper;
        int l = after ? AFTER_NUM[lower] : lower;
        int tail = u + l + moving;
        if (moving > 3) {
            return (moving * 10 + u + 1) * original + tail;
        }
        return (l * 10 + moving + 1) * original + tail;
    }

    private static int yuanDigit(int n) {
        return (n / 1000) % 10;
    }

    static ZhouYiCeResult drawZhouYiCe() {
        int[] lines = new int[6];
        int total = 0;
        int oddLines = 0;
        for (int i = 0; i < 6; i++) {
            int roll = RNG.nextInt(16);
            int v;
            if (roll < 1) v = 6;
            else if (roll < 6) v = 7;
            else if (roll < 13) v = 8;
            else v = 9;
            lines[i] = v;
            total += v;
            if ((v & 1) == 1) oddLines++;
        }
        String parity = (total & 1) == 1 ? "单" : "双";
        return new ZhouYiCeResult(lines, total, oddLines, parity);
    }
}
