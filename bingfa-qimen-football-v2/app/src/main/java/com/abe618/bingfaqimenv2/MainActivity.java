package com.abe618.bingfaqimenv2;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(245, 241, 231);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Theme is already NoActionBar. Avoid redundant requestWindowFeature /
        // WindowInsetsController mutations during startup on Android 16/OEM ROMs.
        try { getWindow().setStatusBarColor(BG); } catch (Throwable ignored) {}
        try { getWindow().setNavigationBarColor(BG); } catch (Throwable ignored) {}

        try {
            setContentView(new QimenView(this));
        } catch (Throwable t) {
            TextView v = new TextView(this);
            v.setBackgroundColor(BG);
            v.setTextColor(Color.rgb(70, 45, 35));
            v.setTextSize(15f);
            v.setPadding(32, 48, 32, 32);
            String m = t.getMessage();
            v.setText("兵法奇门 V2.1 启动保护\\n\\n应用没有退出，但启动阶段发生异常。\\n\\n"
                    + t.getClass().getSimpleName() + (m == null ? "" : ": " + m));
            setContentView(v);
        }
    }

    static final class QimenView extends View {
        private final Random rng = new Random(System.nanoTime());
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF randomButton = new RectF();
        private QimenEngine.Board b;
        private float den;

        QimenView(Activity ctx) {
            super(ctx);
            den = getResources().getDisplayMetrics().density;
            b = QimenEngine.generate(rng);
            setBackgroundColor(BG);
            setFocusable(true);
            setClickable(true);
        }

        private float dp(float x) { return x * den; }
        private void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean bold) {
            p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(dp(size)); p.setTextAlign(align);
            p.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            c.drawText(s == null ? "" : s, x, y, p);
        }
        private void round(Canvas c, RectF r, int fill, int stroke, float sw, float radius) {
            p.setStyle(Paint.Style.FILL); p.setColor(fill); c.drawRoundRect(r, dp(radius), dp(radius), p);
            if (sw > 0) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(sw)); p.setColor(stroke); c.drawRoundRect(r, dp(radius), dp(radius), p); }
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth();
            float pad = dp(12);
            int ink = Color.rgb(37, 34, 30), muted = Color.rgb(105, 96, 82), line = Color.rgb(193, 179, 153);
            QimenEngine.Prediction pr = b.prediction;

            text(c, "兵法奇门·球赛随机盘 V2.1", w/2, dp(29), 20, ink, Paint.Align.CENTER, true);
            String mode = (b.yin ? "阴遁" : "阳遁") + b.ju + "局";
            text(c, String.format(Locale.CHINA, "完全随机取局 #%04d / 1080 · %s · 时柱 %s", b.serial, mode, b.hourGz),
                    w/2, dp(50), 11, muted, Paint.Align.CENTER, false);

            RectF card = new RectF(pad, dp(61), w-pad, dp(148));
            round(c, card, Color.rgb(255, 252, 245), line, 1, 10);
            int resultColor = "主胜".equals(pr.result) ? Color.rgb(176, 56, 44) : ("客胜".equals(pr.result) ? Color.rgb(36, 88, 150) : Color.rgb(117, 84, 37));
            text(c, pr.result, pad+dp(15), dp(91), 25, resultColor, Paint.Align.LEFT, true);
            text(c, String.format(Locale.CHINA, "合参指数 %+.2f · 投票 %s%d · %s", pr.finalIndex, pr.votes>0?"+":"", pr.votes, pr.strengthText()),
                    pad+dp(78), dp(89), 12, ink, Paint.Align.LEFT, true);
            text(c, String.format(Locale.CHINA, "进球 %d · %s · %s · 参考比分 %s", pr.totalGoals, pr.sizeText(), pr.oddEvenText(), pr.scoreText()),
                    pad+dp(15), dp(113), 12, ink, Paint.Align.LEFT, false);
            text(c, "主 " + QimenEngine.palaceName(b.homeGong) + "  vs  客 " + QimenEngine.palaceName(b.awayGong)
                            + "   景门 " + QimenEngine.palaceName(b.jingGong),
                    pad+dp(15), dp(134), 11, muted, Paint.Align.LEFT, false);

            text(c, String.format(Locale.CHINA, "宫态 %+.2f  景门 %+.2f  辛 %+.2f  值使 %+.2f  次判 %+.2f",
                    pr.primary, pr.technique, pr.medal, pr.process, pr.secondary),
                    w/2, dp(164), 9.2f, muted, Paint.Align.CENTER, false);
            if (b.homeGong == b.awayGong) {
                text(c, String.format(Locale.CHINA, "同宫决胜 %+.2f  ·  值符-六庚 %+.2f",
                        pr.collision, pr.fuGeng),
                        w/2, dp(177), 9.2f, Color.rgb(120, 67, 43), Paint.Align.CENTER, true);
            }

            float gridTop = dp(184);
            float cell = (w - pad*2) / 3f;
            int[][] pos = {{4,9,2},{3,5,7},{8,1,6}};
            for (int row=0; row<3; row++) {
                for (int col=0; col<3; col++) {
                    int g = pos[row][col];
                    float l = pad + col*cell, t = gridTop + row*cell;
                    RectF rr = new RectF(l, t, l+cell, t+cell);
                    int fill = g==5 ? Color.rgb(239, 232, 216) : Color.rgb(253, 249, 239);
                    round(c, rr, fill, line, 0.8f, 0);
                    String tag = "";
                    if (g==b.homeGong && g==b.awayGong) tag="主客";
                    else if (g==b.homeGong) tag="主";
                    else if (g==b.awayGong) tag="客";
                    int tagColor = "客".equals(tag) ? Color.rgb(36,88,150) : Color.rgb(176,56,44);
                    text(c, QimenEngine.palaceName(g)+"·"+QimenEngine.element(g), l+dp(6), t+dp(17), 10.5f, ink, Paint.Align.LEFT, true);
                    if (!tag.isEmpty()) text(c, tag, l+cell-dp(6), t+dp(17), 10.5f, tagColor, Paint.Align.RIGHT, true);
                    if (g != 5) {
                        text(c, nz(b.star[g]) + "  " + nz(b.tian[g]), l+dp(6), t+dp(37), 10.5f, ink, Paint.Align.LEFT, false);
                        text(c, nz(b.door[g]) + "  " + nz(b.god[g]), l+dp(6), t+dp(57), 10.5f, ink, Paint.Align.LEFT, false);
                        text(c, "地 " + nz(b.di[g]), l+dp(6), t+dp(77), 10, muted, Paint.Align.LEFT, false);
                    } else {
                        text(c, "中宫", l+cell/2, t+dp(46), 12, muted, Paint.Align.CENTER, true);
                        text(c, "地 " + nz(b.di[g]), l+cell/2, t+dp(67), 10, muted, Paint.Align.CENTER, false);
                    }
                }
            }

            float gridBottom = gridTop + cell*3;
            float infoTop = gridBottom + dp(10);
            text(c, "值符星 " + b.zhiFuStar + " · 原宫 " + QimenEngine.palaceName(b.zhiFuOrigin)
                            + " · 落 " + QimenEngine.palaceName(b.zhiFuGong),
                    pad, infoTop+dp(14), 10.5f, ink, Paint.Align.LEFT, false);
            text(c, "值使 " + b.zhiShiDoor + " · 落 " + QimenEngine.palaceName(b.zhiShiGong)
                            + " · 旬仪 " + b.xunYi,
                    pad, infoTop+dp(33), 10.5f, ink, Paint.Align.LEFT, false);

            float buttonH = dp(47);
            float buttonBottom = getHeight()-dp(16);
            float buttonTop = Math.max(infoTop+dp(48), buttonBottom-buttonH);
            randomButton.set(pad, buttonTop, w-pad, buttonTop+buttonH);
            round(c, randomButton, Color.rgb(111, 55, 38), Color.rgb(111,55,38), 0, 11);
            text(c, "重新随机取局", w/2, buttonTop+dp(30), 15, Color.WHITE, Paint.Align.CENTER, true);
            if (randomButton.bottom + dp(22) < getHeight()) {
                text(c, "民俗算法实验 · 不代表真实比赛概率 · 不作投注依据", w/2, randomButton.bottom+dp(17), 9, muted, Paint.Align.CENTER, false);
            }
        }

        private String nz(String s) { return s == null || s.length()==0 ? "—" : s; }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction()==MotionEvent.ACTION_UP && randomButton.contains(e.getX(), e.getY())) {
                b = QimenEngine.generate(rng);
                invalidate();
                performClick();
                return true;
            }
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
