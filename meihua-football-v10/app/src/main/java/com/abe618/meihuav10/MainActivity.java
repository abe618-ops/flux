package com.abe618.meihuav10;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;
import java.util.Random;

public class MainActivity extends Activity {
    private final EditText[] pNum = new EditText[5];
    private final EditText[] tNum = new EditText[5];
    private final Spinner[] pTri = new Spinner[5];
    private final Spinner[] tTri = new Spinner[5];

    private EditText homeName;
    private EditText awayName;
    private TextView resultBox;
    private TextView dimensionBox;
    private TextView statsBox;
    private EditText actualHome;
    private EditText actualAway;
    private PredictionEngine.Result lastResult;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("review_stats_v10", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(28));
        root.setBackgroundColor(Color.rgb(246, 246, 248));
        scroll.addView(root);

        TextView title = text("梅花足球合参 V1.0", 24, Typeface.BOLD, Color.rgb(185, 49, 38));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = text("强点 × 克制 × 核心三区 × 零值 × 生克 × 经验样本",
                13, Typeface.NORMAL, Color.DKGRAY);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(2), 0, dp(9));
        root.addView(sub);

        resultBox = text("合参结果\n请起盘或录入盘面", 21, Typeface.BOLD, Color.WHITE);
        resultBox.setPadding(dp(15), dp(15), dp(15), dp(15));
        resultBox.setBackground(roundRect(Color.rgb(194, 48, 38), 16));
        root.addView(resultBox, matchWrap(0, dp(10)));

        LinearLayout names = horizontal();
        homeName = edit("主队 / 策侧", false);
        awayName = edit("客队 / 轨侧", false);
        names.addView(homeName, weight());
        names.addView(awayName, weight());
        root.addView(names, matchWrap(0, dp(7)));

        LinearLayout buttons = horizontal();
        Button randomBtn = button("随机起盘");
        Button timeBtn = button("活时起盘");
        Button analyzeBtn = button("立即合参");
        buttons.addView(randomBtn, weight());
        buttons.addView(timeBtn, weight());
        buttons.addView(analyzeBtn, weight());
        root.addView(buttons, matchWrap(0, dp(9)));

        TextView section = text("数局", 18, Typeface.BOLD, Color.rgb(55, 55, 55));
        root.addView(section);

        LinearLayout header = horizontal();
        header.addView(cell("位", 34, true));
        header.addView(cell("策数", 46, true));
        header.addView(cell("策卦", 92, true));
        header.addView(cell("轨数", 46, true));
        header.addView(cell("轨卦", 92, true));
        root.addView(header);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, PredictionEngine.TRIGRAMS);

        for (int i = 0; i < 5; i++) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);

            row.addView(cell(PredictionEngine.POS[i], 34, true));

            pNum[i] = digitEdit();
            tNum[i] = digitEdit();
            pTri[i] = new Spinner(this);
            tTri[i] = new Spinner(this);
            pTri[i].setAdapter(adapter);
            tTri[i].setAdapter(adapter);

            row.addView(pNum[i], fixed(46));
            row.addView(pTri[i], fixed(92));
            row.addView(tNum[i], fixed(46));
            row.addView(tTri[i], fixed(92));
            root.addView(row);
        }

        TextView hint = text(
                "复刻截图：按“万、元、会、运、世”依次输入策数/轨数，并选择对应演卦；五行由八卦自动映射。",
                12, Typeface.NORMAL, Color.GRAY);
        hint.setPadding(0, dp(7), 0, dp(10));
        root.addView(hint);

        TextView dimTitle = text("多维合参依据", 18, Typeface.BOLD, Color.rgb(185, 49, 38));
        root.addView(dimTitle);

        dimensionBox = text("尚未计算。", 14, Typeface.NORMAL, Color.rgb(45,45,45));
        dimensionBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        dimensionBox.setBackground(roundRect(Color.WHITE, 12));
        root.addView(dimensionBox, matchWrap(0, dp(12)));

        TextView reviewTitle = text("赛后复盘", 18, Typeface.BOLD, Color.rgb(185, 49, 38));
        root.addView(reviewTitle);

        LinearLayout review = horizontal();
        actualHome = edit("主队实际", true);
        actualAway = edit("客队实际", true);
        Button saveBtn = button("记录");
        review.addView(actualHome, weight());
        review.addView(actualAway, weight());
        review.addView(saveBtn, weight());
        root.addView(review);

        statsBox = text("", 14, Typeface.NORMAL, Color.DKGRAY);
        statsBox.setPadding(0, dp(7), 0, dp(7));
        root.addView(statsBox);
        updateStats();

        TextView note = text(
                "V1.0 已把本轮对话中的完整判断顺序写入：总球先看A/K；比分先看强点归属与强度；"
                        + "核心三区大差且连续高值可覆盖单纯生克方向；高克制压缩到2球且强点仅轻微偏斜时提高平局权重。"
                        + "经验权重仅为小样本研究参考。",
                12, Typeface.NORMAL, Color.GRAY);
        root.addView(note);

        randomBtn.setOnClickListener(v -> cast(new SecureRandom()));
        timeBtn.setOnClickListener(v -> cast(new Random(System.currentTimeMillis())));
        analyzeBtn.setOnClickListener(v -> analyze());
        saveBtn.setOnClickListener(v -> saveActual());

        cast(new SecureRandom());
        setContentView(scroll);
    }

    private void cast(Random rng) {
        for (int i = 0; i < 5; i++) {
            pNum[i].setText(String.valueOf(rng.nextInt(10)));
            tNum[i].setText(String.valueOf(rng.nextInt(10)));
            pTri[i].setSelection(rng.nextInt(PredictionEngine.TRIGRAMS.length));
            tTri[i].setSelection(rng.nextInt(PredictionEngine.TRIGRAMS.length));
        }
        analyze();
    }

    private void analyze() {
        int[] p = new int[5];
        int[] t = new int[5];
        String[] pt = new String[5];
        String[] tt = new String[5];

        for (int i = 0; i < 5; i++) {
            p[i] = readDigit(pNum[i]);
            t[i] = readDigit(tNum[i]);
            pt[i] = String.valueOf(pTri[i].getSelectedItem());
            tt[i] = String.valueOf(tTri[i].getSelectedItem());
        }

        lastResult = PredictionEngine.analyze(p, t, pt, tt);

        String hn = cleanName(homeName.getText().toString(), "主队");
        String an = cleanName(awayName.getText().toString(), "客队");

        resultBox.setText(
                "【合参】" + lastResult.direction
                        + "｜总球 " + lastResult.totalGoals
                        + "（" + lastResult.rangeLow + "–" + lastResult.rangeHigh + "）"
                        + "｜" + lastResult.size
                        + "｜" + lastResult.parity + "\n"
                        + "中心比分 " + lastResult.homeGoals + ":" + lastResult.awayGoals
                        + "｜双方进球 " + lastResult.bothScore
                        + "｜半全场 " + lastResult.halfFull + "\n"
                        + hn + " vs " + an + "\n"
                        + "比分参考：" + lastResult.scoreCandidates
        );

        StringBuilder table = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (i > 0) table.append("\n");
            table.append(PredictionEngine.POS[i]).append("  ")
                    .append("策").append(p[i]).append(" ")
                    .append(pt[i])
                    .append("  ｜  轨").append(t[i]).append(" ")
                    .append(tt[i]);
        }

        dimensionBox.setText(
                lastResult.weights
                        + "\n" + lastResult.empirical
                        + "\n比赛形态：" + lastResult.shape
                        + "\n总球中心：" + lastResult.totalGoals
                        + "｜核心范围 " + lastResult.rangeLow + "–" + lastResult.rangeHigh
                        + "｜半场 " + lastResult.half
                        + "\n\n" + lastResult.details
                        + "\n\n盘面：\n" + table
        );

        hideKeyboard();
    }

    private void saveActual() {
        if (lastResult == null) {
            Toast.makeText(this, "请先合参", Toast.LENGTH_SHORT).show();
            return;
        }

        int ah = readNullable(actualHome);
        int aa = readNullable(actualAway);
        if (ah < 0 || aa < 0) {
            Toast.makeText(this, "请输入实际比分", Toast.LENGTH_SHORT).show();
            return;
        }

        int total = prefs.getInt("total", 0) + 1;
        int wdl = prefs.getInt("wdl", 0);
        int exactGoals = prefs.getInt("exactGoals", 0);
        int range = prefs.getInt("range", 0);
        int exactScore = prefs.getInt("exactScore", 0);
        int parity = prefs.getInt("parity", 0);
        int size = prefs.getInt("size", 0);
        int btts = prefs.getInt("btts", 0);

        int actualGoals = ah + aa;
        int actualDir = Integer.compare(ah, aa);

        if (actualDir == lastResult.directionCode) wdl++;
        if (actualGoals == lastResult.totalGoals) exactGoals++;
        if (actualGoals >= lastResult.rangeLow && actualGoals <= lastResult.rangeHigh) range++;
        if (ah == lastResult.homeGoals && aa == lastResult.awayGoals) exactScore++;
        if ((actualGoals % 2 == 0) == (lastResult.totalGoals % 2 == 0)) parity++;
        if ((actualGoals >= 3) == (lastResult.totalGoals >= 3)) size++;

        boolean actualBtts = ah > 0 && aa > 0;
        boolean predictedBtts = lastResult.homeGoals > 0 && lastResult.awayGoals > 0;
        if (actualBtts == predictedBtts) btts++;

        prefs.edit()
                .putInt("total", total)
                .putInt("wdl", wdl)
                .putInt("exactGoals", exactGoals)
                .putInt("range", range)
                .putInt("exactScore", exactScore)
                .putInt("parity", parity)
                .putInt("size", size)
                .putInt("btts", btts)
                .apply();

        Toast.makeText(this, "已记录 " + ah + ":" + aa, Toast.LENGTH_SHORT).show();
        actualHome.setText("");
        actualAway.setText("");
        updateStats();
    }

    private void updateStats() {
        int total = prefs.getInt("total", 0);
        if (total == 0) {
            statsBox.setText("尚无本机复盘。内置8场已完成样本用于经验相似度参考。");
            return;
        }

        statsBox.setText(
                "累计 " + total + " 场｜胜平负 " + pct(prefs.getInt("wdl",0), total)
                        + "｜总球精确 " + pct(prefs.getInt("exactGoals",0), total)
                        + "｜范围 " + pct(prefs.getInt("range",0), total)
                        + "\n精确比分 " + pct(prefs.getInt("exactScore",0), total)
                        + "｜单双 " + pct(prefs.getInt("parity",0), total)
                        + "｜大小 " + pct(prefs.getInt("size",0), total)
                        + "｜双方进球 " + pct(prefs.getInt("btts",0), total)
        );
    }

    private String pct(int n, int d) {
        return Math.round(n * 100f / d) + "%";
    }

    private int readDigit(EditText e) {
        String s = e.getText().toString().trim();
        if (s.isEmpty()) return 0;
        try {
            return Math.max(0, Math.min(9, Integer.parseInt(s)));
        } catch (Exception ex) {
            return 0;
        }
    }

    private int readNullable(EditText e) {
        String s = e.getText().toString().trim();
        if (s.isEmpty()) return -1;
        try {
            return Integer.parseInt(s);
        } catch (Exception ex) {
            return -1;
        }
    }

    private EditText digitEdit() {
        EditText e = edit("", true);
        e.setGravity(Gravity.CENTER);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)});
        return e;
    }

    private EditText edit(String hint, boolean number) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setPadding(dp(7), dp(7), dp(7), dp(7));
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setBackground(roundRect(Color.WHITE, 9));
        return e;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        return b;
    }

    private TextView text(String value, int sp, int style, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setTextColor(color);
        return t;
    }

    private TextView cell(String value, int width, boolean bold) {
        TextView t = text(value, 14, bold ? Typeface.BOLD : Typeface.NORMAL, Color.DKGRAY);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(2), dp(7), dp(2), dp(7));
        t.setLayoutParams(fixed(width));
        return t;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        return p;
    }

    private LinearLayout.LayoutParams fixed(int widthDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                dp(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        return p;
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, top, 0, bottom);
        return p;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private String cleanName(String s, String fallback) {
        s = s.trim();
        return s.isEmpty() ? fallback : s;
    }

    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }
}
