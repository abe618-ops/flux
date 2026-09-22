package com.abe618.dayanfootball;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.*;
import java.security.SecureRandom;
import java.util.Locale;

public class MainActivity extends Activity {
    private final SecureRandom random = new SecureRandom();
    private int round = 0;
    private int accentIndex = 0;

    private final int[] ACCENTS = {
        Color.rgb(56,86,120),
        Color.rgb(92,75,112),
        Color.rgb(67,103,82),
        Color.rgb(120,82,62)
    };

    private ScrollView scroll;
    private TextView top, headlineTitle, outcome, ranking, quick;
    private TextView chartTitle, gua, trigram, yaoNote;
    private final TextView[] yaoViews = new TextView[6];
    private TextView detailsTitle, winLoseValue, halfFullValue, goalsValue, ouValue, oddEvenValue, bttsValue, scoresValue, netGoalValue, logic;
    private TextView seedTitle, seedText;
    private Button nextThumb;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        buildStaticPage();
        showNextRound();
    }

    private void buildStaticPage() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(28));
        root.setBackgroundColor(Color.rgb(247,247,245));
        scroll.addView(root);

        top = text("", 24, true);
        root.addView(top);

        TextView hint = text("点击“下一场”只更新本页数据，页面不会自动跳转；需要看下方内容时可手动滚动。", 12, false);
        hint.setTextColor(Color.DKGRAY);
        hint.setPadding(0, dp(3), 0, dp(12));
        root.addView(hint);

        LinearLayout headline = card();
        headlineTitle = text("合参预测结果", 13, true);
        headline.addView(headlineTitle);

        outcome = text("", 31, true);
        outcome.setTextColor(Color.rgb(25,25,25));
        outcome.setPadding(0, dp(4), 0, dp(2));
        headline.addView(outcome);

        ranking = text("", 16, true);
        headline.addView(ranking);

        quick = text("", 16, false);
        quick.setPadding(0, dp(8), 0, 0);
        headline.addView(quick);
        root.addView(headline, fullWithBottom(10));

        nextThumb = nextButton();
        nextThumb.setFocusable(false);
        nextThumb.setFocusableInTouchMode(false);
        root.addView(nextThumb, fullWithBottom(12));

        LinearLayout chart = card();
        chartTitle = sectionTitle("大衍排盘");
        chart.addView(chartTitle);

        gua = text("", 22, true);
        chart.addView(gua);

        trigram = text("", 14, false);
        trigram.setPadding(0, dp(6), 0, dp(8));
        chart.addView(trigram);

        LinearLayout yaoRow = new LinearLayout(this);
        yaoRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i=0;i<6;i++) {
            TextView y = text("", 19, true);
            y.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams yp = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (i>0) yp.setMargins(dp(5),0,0,0);
            yaoViews[i] = y;
            yaoRow.addView(y, yp);
        }
        chart.addView(yaoRow);

        yaoNote = text("", 13, false);
        yaoNote.setPadding(0, dp(9), 0, 0);
        chart.addView(yaoNote);
        root.addView(chart, fullWithBottom(12));

        LinearLayout details = card();
        detailsTitle = sectionTitle("多维判断");
        details.addView(detailsTitle);

        winLoseValue = addKV(details, "胜平负");
        halfFullValue = addKV(details, "半全场");
        goalsValue = addKV(details, "总进球");
        ouValue = addKV(details, "大小2.5");
        oddEvenValue = addKV(details, "单双");
        bttsValue = addKV(details, "双方进球");
        scoresValue = addKV(details, "比分池");
        netGoalValue = addKV(details, "净胜球");

        logic = text("", 13, false);
        logic.setTextColor(Color.DKGRAY);
        details.addView(logic);
        root.addView(details, fullWithBottom(12));

        LinearLayout seedCard = card();
        seedTitle = sectionTitle("本场冻结信息");
        seedCard.addView(seedTitle);

        seedText = text("", 12, false);
        seedCard.addView(seedText);
        root.addView(seedCard, fullWithBottom(14));

        TextView foot = text("仅作传统筮法盲测实验，不构成投注建议。", 11, false);
        foot.setGravity(android.view.Gravity.CENTER);
        foot.setTextColor(Color.GRAY);
        root.addView(foot);

        setContentView(scroll);
    }

    private void showNextRound() {
        final int oldScrollY = scroll == null ? 0 : scroll.getScrollY();

        round++;
        long seed = nextSeed();
        DayanEngine.Cast c = DayanEngine.cast(seed, DayanEngine.MappingMode.STANDARD);
        accentIndex = (round - 1) % ACCENTS.length;
        updateStaticPage(c);

        // 不自动回顶、不自动下滚；仅恢复点击前的同一滚动位置。
        if (scroll != null) {
            scroll.post(() -> scroll.scrollTo(0, oldScrollY));
        }
    }

    private void updateStaticPage(DayanEngine.Cast c) {
        DayanEngine.Prediction p = c.standard;
        int accent = ACCENTS[accentIndex];

        top.setText("大衍足球 · 第 " + round + " 场");
        top.setTextColor(accent);

        headlineTitle.setTextColor(accent);
        outcome.setText(p.outcome);
        ranking.setText(p.ranking);
        quick.setText(
            "半全场  " + p.halfFull +
            "\n进球  " + p.goalsMain + "球（次 " + p.goalsAlt + "球）" +
            "\n大小  " + p.overUnder + "    单双  " + p.oddEven +
            "\n比分  " + p.scoreMain + "（次 " + p.scoreAlt1 + "、" + p.scoreAlt2 + "）"
        );

        styleNextButton(accent);

        chartTitle.setTextColor(accent);
        gua.setText(c.baseHex + (c.baseHex.equals(c.changedHex) ? "" : "  →  " + c.changedHex));
        gua.setTextColor(accent);
        trigram.setText(
            "本卦：上 " + c.baseUpper + "｜下 " + c.baseLower +
            "\n变卦：上 " + c.changedUpper + "｜下 " + c.changedLower
        );

        int movingCount = 0;
        for (int i=0;i<6;i++) {
            if (c.moving[i]) movingCount++;
            TextView y = yaoViews[i];
            y.setText(String.valueOf(c.lines[i]));
            y.setTextColor(c.moving[i] ? accent : Color.rgb(55,55,55));
            GradientDrawable yd = new GradientDrawable();
            yd.setColor(c.moving[i] ? tint(accent, 0.88f) : Color.rgb(235,235,232));
            yd.setCornerRadius(dp(8));
            y.setBackground(yd);
        }

        yaoNote.setText(
            "六爻（初→上）：" + lineString(c.lines) +
            "｜动爻 " + movingPositions(c) +
            "\n世 " + c.shiPos + "爻（" + c.shiBranch + "·" + c.shiElement + "）" +
            "｜应 " + c.yingPos + "爻（" + c.yingBranch + "·" + c.yingElement + "）"
        );

        detailsTitle.setTextColor(accent);
        winLoseValue.setText(p.ranking);
        halfFullValue.setText(p.halfFull);
        goalsValue.setText(p.goalsMain + "球｜次选 " + p.goalsAlt + "球｜" + p.goalsRange);
        ouValue.setText(p.overUnder);
        oddEvenValue.setText(p.oddEven);
        bttsValue.setText(p.btts);
        scoresValue.setText(p.scoreMain + "｜" + p.scoreAlt1 + "｜" + p.scoreAlt2);
        netGoalValue.setText(p.netGoal);

        logic.setText(
            "\n结构判断：" + directionText(c) +
            "\n生克分：本卦 " + fmt(c.baseRelation) +
            "｜变卦 " + fmt(c.changedRelation) +
            "｜世应 " + fmt(c.shiYingRelation) +
            "\n动爻数：" + movingCount +
            "｜模型分 " + fmt(p.rawScore) +
            "｜进球强度 " + fmt(p.goalMean)
        );

        seedTitle.setTextColor(accent);
        seedText.setText(
            "随机种子：" + c.seed +
            "\n冻结码：" + c.freezeCode +
            "\n十八变余策：" + remainsText(c)
        );
    }

    private long nextSeed() {
        long v = random.nextLong();
        if (v == Long.MIN_VALUE) v = 1;
        return Math.abs(v);
    }

    private String directionText(DayanEngine.Cast c) {
        String b = relName(c.baseRelation);
        String ch = relName(c.changedRelation);
        String sy = relName(c.shiYingRelation);
        if (Math.signum(c.baseRelation) != 0 && Math.signum(c.changedRelation) != 0 &&
            Math.signum(c.baseRelation) != Math.signum(c.changedRelation)) {
            return "本卦 " + b + " → 变卦 " + ch + "，存在前后方向反转；世应 " + sy + "。";
        }
        return "本卦 " + b + "；变卦 " + ch + "；世应 " + sy + "。";
    }

    private String relName(double v) {
        if (v > 1.0) return "偏主";
        if (v < -1.0) return "偏客";
        return "接近均衡";
    }

    private String movingPositions(DayanEngine.Cast c) {
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<6;i++) {
            if (c.moving[i]) {
                if (sb.length()>0) sb.append("、");
                sb.append(i+1);
            }
        }
        return sb.length()==0 ? "无" : sb.toString();
    }

    private String remainsText(DayanEngine.Cast c) {
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<6;i++) {
            if (i>0) sb.append("；");
            sb.append(i+1).append("爻 ")
              .append(c.remains[i][0]).append("→")
              .append(c.remains[i][1]).append("→")
              .append(c.remains[i][2]);
        }
        return sb.toString();
    }

    private TextView addKV(LinearLayout box, String k) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView key = text(k, 14, true);
        key.setTextColor(Color.rgb(80,80,80));
        row.addView(key, new LinearLayout.LayoutParams(dp(90), -2));

        TextView val = text("", 15, false);
        val.setTextColor(Color.rgb(25,25,25));
        row.addView(val, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row);
        return val;
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.rgb(225,225,221));
        v.setBackground(bg);
        return v;
    }

    private TextView sectionTitle(String s) {
        TextView v = text(s, 14, true);
        v.setPadding(0,0,0,dp(7));
        return v;
    }

    private int tint(int color, float ratio) {
        int r = (int)(Color.red(color) + (255 - Color.red(color)) * ratio);
        int g = (int)(Color.green(color) + (255 - Color.green(color)) * ratio);
        int b = (int)(Color.blue(color) + (255 - Color.blue(color)) * ratio);
        return Color.rgb(Math.min(255,r), Math.min(255,g), Math.min(255,b));
    }

    private Button nextButton() {
        Button next = new Button(this);
        next.setText("下一场  →");
        next.setTextSize(20);
        next.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        next.setTextColor(Color.WHITE);
        next.setAllCaps(false);
        next.setMinHeight(dp(58));
        next.setOnClickListener(v -> showNextRound());
        return next;
    }

    private void styleNextButton(int accent) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(accent);
        bg.setCornerRadius(dp(14));
        nextThumb.setBackground(bg);
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(35,35,35));
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setLineSpacing(0, 1.16f);
        return v;
    }

    private LinearLayout.LayoutParams fullWithBottom(int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,dp(bottomDp));
        return p;
    }

    private int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String lineString(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int v:a) sb.append(v);
        return sb.toString();
    }

    private static String fmt(double d) {
        return String.format(Locale.US, "%.2f", d);
    }
}
