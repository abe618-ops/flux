package com.abe618.fourshushurandom;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class FootballActivity extends Activity {
    private static final int INK = Color.rgb(29, 34, 39);
    private static final int MUTED = Color.rgb(102, 108, 114);
    private static final int BLUE = Color.rgb(36, 100, 174);
    private static final int GOLD = Color.rgb(160, 105, 20);
    private static final int GREEN = Color.rgb(41, 122, 78);
    private static final int SOFT = Color.rgb(247, 248, 250);
    private static final int WARM = Color.rgb(251, 247, 236);

    private LinearLayout root;
    private EditText seedInput;
    private TextView seedView, combinedView;
    private TextView nineCast, ninePred, lingCast, lingPred, sevenCast, sevenPred, triCast, triPred;
    private ChartEngine.State state;
    private FootballEngine.Result football;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        nextMatch();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(22), dp(14), dp(32));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        root.addView(text("四术足球随机盲测 v0.2", 27, INK, true));
        root.addView(text("九爻易 · 灵棋经 · 七星数 · 万事三角定律", 13, MUTED, false));
        gap(9);

        LinearLayout top = card(WARM);
        top.addView(text("四术合参结果", 18, GOLD, true));
        combinedView = text("—", 17, INK, true);
        combinedView.setLineSpacing(dp(4), 1f);
        combinedView.setPadding(0, dp(6), 0, dp(4));
        top.addView(combinedView);
        root.addView(top);
        gap(10);

        LinearLayout controls = card(SOFT);
        controls.addView(text("起盘 / 下一场", 18, BLUE, true));
        seedInput = new EditText(this);
        seedInput.setHint("固定 Seed：数字或文字（可复现）");
        seedInput.setSingleLine(true);
        controls.addView(seedInput, new LinearLayout.LayoutParams(-1, dp(48)));

        Button next = button("下一场 · 随机起盘");
        next.setOnClickListener(v -> nextMatch());
        controls.addView(next, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button fixed = button("固定 Seed 起盘");
        fixed.setOnClickListener(v -> fixedCast());
        Button copy = button("复制全部");
        copy.setOnClickListener(v -> copyAll());
        row.addView(fixed, weight());
        row.addView(copy, weight());
        controls.addView(row);

        seedView = text("", 12, MUTED, false);
        seedView.setPadding(0, dp(5), 0, 0);
        controls.addView(seedView);
        root.addView(controls);
        gap(11);

        TextView[] a = module("九爻易 · 512局", "天—人—地三组三爻；保留原盘，再单独映射到比赛");
        nineCast = a[0]; ninePred = a[1];

        TextView[] b = module("灵棋经 · 125局", "上中下各四棋；一阳、二阴、三重阳、四重阴");
        lingCast = b[0]; lingPred = b[1];

        TextView[] c = module("七星数 · 21牌", "双骰取符 + 21牌 + 三组七牌；资料缺口不补造古断辞");
        sevenCast = c[0]; sevenPred = c[1];

        TextView[] d = module("万事三角定律 · 169位", "0~12数字、阴阳五行、生克；天时/地时/人时映射主客");
        triCast = d[0]; triPred = d[1];

        LinearLayout note = card(Color.rgb(249, 250, 251));
        note.addView(text("盲测方式", 16, GREEN, true));
        note.addView(text("每一术先独立排盘并给出胜平负、半全场、总进球、比分、大小2.5和单双，再进行四术合参。同一 Seed 可完整复现，适合赛前冻结、赛后复盘。", 13, MUTED, false));
        root.addView(note);
    }

    private TextView[] module(String title, String subtitle) {
        LinearLayout c = card(SOFT);
        c.addView(text(title, 19, BLUE, true));
        c.addView(text(subtitle, 12, MUTED, false));

        TextView cast = text("", 13, INK, false);
        cast.setLineSpacing(dp(3), 1f);
        cast.setPadding(0, dp(7), 0, dp(7));
        c.addView(cast);

        TextView divider = new TextView(this);
        divider.setBackgroundColor(Color.rgb(224, 228, 232));
        c.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        TextView pred = text("", 14, INK, true);
        pred.setLineSpacing(dp(3), 1f);
        pred.setPadding(0, dp(7), 0, 0);
        c.addView(pred);

        root.addView(c);
        gap(10);
        return new TextView[]{cast, pred};
    }

    private void nextMatch() {
        state = ChartEngine.castRandom();
        football = FootballEngine.analyze(state);
        show();
    }

    private void fixedCast() {
        String s = seedInput.getText().toString().trim();
        if (s.isEmpty()) {
            Toast.makeText(this, "请输入数字或文字 Seed", Toast.LENGTH_SHORT).show();
            return;
        }
        long seed;
        try { seed = Long.parseLong(s); }
        catch (Exception e) { seed = ChartEngine.seedFromText(s); }

        state = ChartEngine.cast(seed);
        football = FootballEngine.analyze(state);
        show();
    }

    private void show() {
        if (state == null || football == null) return;

        seedView.setText("本局 Seed：" + state.seed + "  ·  同一 Seed 可完整复现");
        combinedView.setText(football.combined.text);

        nineCast.setText(state.nineYao.text);
        ninePred.setText(football.nine.text());

        lingCast.setText(state.lingQi.text);
        lingPred.setText(football.ling.text());

        sevenCast.setText(state.sevenStar.text);
        sevenPred.setText(football.seven.text());

        triCast.setText(state.triangle.text);
        triPred.setText(football.triangle.text());
    }

    private void copyAll() {
        if (state == null || football == null) return;
        String all = "四术足球随机盲测 v0.2\nSeed: " + state.seed
                + "\n\n【四术合参】\n" + football.combined.text
                + "\n\n【九爻易】\n" + state.nineYao.text + "\n" + football.nine.text()
                + "\n\n【灵棋经】\n" + state.lingQi.text + "\n" + football.ling.text()
                + "\n\n【七星数】\n" + state.sevenStar.text + "\n" + football.seven.text()
                + "\n\n【万事三角定律】\n" + state.triangle.text + "\n" + football.triangle.text();

        ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("四术足球随机盲测", all));
        Toast.makeText(this, "已复制全部排盘与预测", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout card(int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.rgb(228, 231, 234));
        c.setBackground(bg);
        return c;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.START);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(14);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private void gap(int h) {
        TextView v = new TextView(this);
        root.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
    }

    private int dp(int x) {
        return (int)(x * getResources().getDisplayMetrics().density + 0.5f);
    }
}
