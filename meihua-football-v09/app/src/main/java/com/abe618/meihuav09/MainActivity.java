package com.abe618.meihuav09;

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
    private final Spinner[] pElem = new Spinner[5];
    private final Spinner[] tElem = new Spinner[5];

    private EditText homeName;
    private EditText awayName;
    private TextView resultBox;
    private TextView detailBox;
    private TextView statsBox;
    private EditText actualHome;
    private EditText actualAway;
    private PredictionEngine.Result lastResult;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("review_stats", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(28));
        root.setBackgroundColor(Color.rgb(247, 247, 249));
        scroll.addView(root);

        TextView title = text("梅花足球合参 V0.9", 25, Typeface.BOLD, Color.rgb(165, 32, 28));
        root.addView(title);

        TextView sub = text("起盘 + 万元会运世 + 数差 / 生克 / 零值 / 强点合参",
                14, Typeface.NORMAL, Color.DKGRAY);
        sub.setPadding(0, dp(2), 0, dp(10));
        root.addView(sub);

        resultBox = text("合参结果\n请先随机起盘、时间起盘，或手动录入盘面。",
                22, Typeface.BOLD, Color.WHITE);
        resultBox.setPadding(dp(16), dp(16), dp(16), dp(16));
        resultBox.setBackground(roundRect(Color.rgb(187, 48, 41), 16));
        root.addView(resultBox, matchWrap(0, dp(12)));

        LinearLayout names = horizontal();
        homeName = edit("主队 / 策侧", false);
        awayName = edit("客队 / 轨侧", false);
        names.addView(homeName, weight());
        names.addView(awayName, weight());
        root.addView(names, matchWrap(0, dp(8)));

        LinearLayout buttons = horizontal();
        Button randomBtn = button("随机起盘");
        Button timeBtn = button("时间起盘");
        Button analyzeBtn = button("合参预测");
        buttons.addView(randomBtn, weight());
        buttons.addView(timeBtn, weight());
        buttons.addView(analyzeBtn, weight());
        root.addView(buttons, matchWrap(0, dp(10)));

        TextView hint = text("盘面录入（数字0–9；五行可手动修改）",
                16, Typeface.BOLD, Color.rgb(55, 55, 55));
        root.addView(hint);

        LinearLayout header = horizontal();
        header.addView(cell("位", 38, true));
        header.addView(cell("策数", 52, true));
        header.addView(cell("策五行", 82, true));
        header.addView(cell("轨数", 52, true));
        header.addView(cell("轨五行", 82, true));
        root.addView(header);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                PredictionEngine.ELEMENTS);

        for (int i = 0; i < 5; i++) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(cell(PredictionEngine.POS[i], 38, true));

            pNum[i] = digitEdit();
            tNum[i] = digitEdit();
            pElem[i] = new Spinner(this);
            tElem[i] = new Spinner(this);
            pElem[i].setAdapter(adapter);
            tElem[i].setAdapter(adapter);

            row.addView(pNum[i], fixed(52));
            row.addView(pElem[i], fixed(82));
            row.addView(tNum[i], fixed(52));
            row.addView(tElem[i], fixed(82));
            root.addView(row);
        }

        TextView autoHint = text(
                "随机/时间起盘采用数字取八卦→五行；手工复刻截图时，可直接改数字和五行。",
                12, Typeface.NORMAL, Color.GRAY);
        autoHint.setPadding(0, dp(8), 0, dp(10));
        root.addView(autoHint);

        TextView detailTitle = text("合参依据", 18, Typeface.BOLD, Color.rgb(165, 32, 28));
        root.addView(detailTitle);

        detailBox = text("尚未计算。", 15, Typeface.NORMAL, Color.rgb(45, 45, 45));
        detailBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        detailBox.setBackground(roundRect(Color.WHITE, 12));
        root.addView(detailBox, matchWrap(0, dp(14)));

        TextView reviewTitle = text("赛后复盘（本机统计）", 18, Typeface.BOLD, Color.rgb(165, 32, 28));
        root.addView(reviewTitle);

        LinearLayout reviewRow = horizontal();
        actualHome = edit("主队实际", true);
        actualAway = edit("客队实际", true);
        Button saveBtn = button("记录赛果");
        reviewRow.addView(actualHome, weight());
        reviewRow.addView(actualAway, weight());
        reviewRow.addView(saveBtn, weight());
        root.addView(reviewRow);

        statsBox = text("", 14, Typeface.NORMAL, Color.DKGRAY);
        statsBox.setPadding(0, dp(8), 0, dp(8));
        root.addView(statsBox);
        updateStats();

        TextView disclaimer = text(
                "说明：V0.9 是当前小样本迭代的实验模型；“模型权重”不是统计学真实概率，仅供研究与娱乐复盘。",
                12, Typeface.NORMAL, Color.GRAY);
        root.addView(disclaimer);

        randomBtn.setOnClickListener(v -> randomCast(new SecureRandom()));
        timeBtn.setOnClickListener(v -> randomCast(new Random(System.currentTimeMillis())));
        analyzeBtn.setOnClickListener(v -> analyze());
        saveBtn.setOnClickListener(v -> saveActual());

        randomCast(new SecureRandom());
        setContentView(scroll);
    }

    private void randomCast(Random rng) {
        for (int i = 0; i < 5; i++) {
            int a = rng.nextInt(10);
            int b = rng.nextInt(10);
            pNum[i].setText(String.valueOf(a));
            tNum[i].setText(String.valueOf(b));
            setSpinner(pElem[i], PredictionEngine.elementForNumber(a));
            setSpinner(tElem[i], PredictionEngine.elementForNumber(b));
        }
        analyze();
    }

    private void analyze() {
        int[] p = new int[5];
        int[] t = new int[5];
        String[] pe = new String[5];
        String[] te = new String[5];

        for (int i = 0; i < 5; i++) {
            p[i] = readDigit(pNum[i]);
            t[i] = readDigit(tNum[i]);
            pe[i] = String.valueOf(pElem[i].getSelectedItem());
            te[i] = String.valueOf(tElem[i].getSelectedItem());
        }

        lastResult = PredictionEngine.analyze(p, t, pe, te);

        String hn = cleanName(homeName.getText().toString(), "主队");
        String an = cleanName(awayName.getText().toString(), "客队");

        resultBox.setText(
                "合参：" + lastResult.direction
                        + "｜总球 " + lastResult.totalGoals
                        + "｜" + lastResult.size
                        + "｜" + lastResult.parity + "\n"
                        + "中心比分 " + lastResult.homeGoals + ":" + lastResult.awayGoals
                        + "｜双方进球 " + lastResult.bothScore
                        + "｜半全场 " + lastResult.halfFull + "\n"
                        + hn + " vs " + an + "\n"
                        + "参考：" + lastResult.scoreCandidates
        );

        StringBuilder trigram = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (i > 0) trigram.append("；");
            trigram.append(PredictionEngine.POS[i]).append(" ")
                    .append("策").append(PredictionEngine.trigramForNumber(p[i]))
                    .append("(").append(pe[i]).append(")")
                    .append(" / 轨").append(PredictionEngine.trigramForNumber(t[i]))
                    .append("(").append(te[i]).append(")");
        }

        detailBox.setText(
                lastResult.weights
                        + "\n比赛形态：" + lastResult.shape
                        + "\n总球核心：" + lastResult.totalGoals
                        + "｜参考范围 " + lastResult.rangeLow + "–" + lastResult.rangeHigh
                        + "｜半场 " + lastResult.half
                        + "\n比分组：" + lastResult.scoreCandidates
                        + "\n\n" + lastResult.details
                        + "\n\n取卦：" + trigram
        );

        hideKeyboard();
    }

    private void saveActual() {
        if (lastResult == null) {
            Toast.makeText(this, "请先合参预测", Toast.LENGTH_SHORT).show();
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
        int score = prefs.getInt("score", 0);
        int parity = prefs.getInt("parity", 0);
        int size = prefs.getInt("size", 0);

        int actualDir = Integer.compare(ah, aa);
        int actualGoals = ah + aa;

        if (actualDir == lastResult.directionCode) wdl++;
        if (actualGoals == lastResult.totalGoals) exactGoals++;
        if (actualGoals >= lastResult.rangeLow && actualGoals <= lastResult.rangeHigh) range++;
        if (ah == lastResult.homeGoals && aa == lastResult.awayGoals) score++;
        if ((actualGoals % 2 == 0) == (lastResult.totalGoals % 2 == 0)) parity++;
        if ((actualGoals >= 3) == (lastResult.totalGoals >= 3)) size++;

        prefs.edit()
                .putInt("total", total)
                .putInt("wdl", wdl)
                .putInt("exactGoals", exactGoals)
                .putInt("range", range)
                .putInt("score", score)
                .putInt("parity", parity)
                .putInt("size", size)
                .apply();

        Toast.makeText(this, "已记录本场 " + ah + ":" + aa, Toast.LENGTH_SHORT).show();
        actualHome.setText("");
        actualAway.setText("");
        updateStats();
    }

    private void updateStats() {
        int total = prefs.getInt("total", 0);
        if (total == 0) {
            statsBox.setText("尚无本机复盘样本。");
            return;
        }

        statsBox.setText(
                "累计 " + total + " 场｜胜平负 " + pct(prefs.getInt("wdl", 0), total)
                        + "｜总球精确 " + pct(prefs.getInt("exactGoals", 0), total)
                        + "｜总球范围 " + pct(prefs.getInt("range", 0), total)
                        + "\n比分精确 " + pct(prefs.getInt("score", 0), total)
                        + "｜单双 " + pct(prefs.getInt("parity", 0), total)
                        + "｜大小 " + pct(prefs.getInt("size", 0), total)
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

    private void setSpinner(Spinner s, String value) {
        for (int i = 0; i < PredictionEngine.ELEMENTS.length; i++) {
            if (PredictionEngine.ELEMENTS[i].equals(value)) {
                s.setSelection(i);
                return;
            }
        }
    }

    private String cleanName(String s, String fallback) {
        s = s.trim();
        return s.isEmpty() ? fallback : s;
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
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setPadding(dp(8), dp(7), dp(8), dp(7));
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setBackground(roundRect(Color.WHITE, 10));
        return e;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        return b;
    }

    private TextView cell(String s, int width, boolean bold) {
        TextView t = text(s, 14, bold ? Typeface.BOLD : Typeface.NORMAL, Color.DKGRAY);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(2), dp(8), dp(2), dp(8));
        t.setLayoutParams(fixed(width));
        return t;
    }

    private TextView text(String s, int sp, int style, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setTextColor(color);
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

    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }
}
