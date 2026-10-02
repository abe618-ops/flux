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

public class MainActivity extends Activity {
    private static final int INK = Color.rgb(30, 34, 38);
    private static final int MUTED = Color.rgb(100, 106, 112);
    private static final int ACCENT = Color.rgb(40, 104, 176);
    private static final int SOFT = Color.rgb(246, 248, 250);
    private static final int GOLD = Color.rgb(155, 105, 24);

    private LinearLayout root;
    private EditText seedInput;
    private TextView seedView, nineView, lingView, sevenView, triangleView;
    private ChartEngine.State state;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        newRandom();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(32));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        root.addView(text("四术随机排盘 v0.1", 27, INK, true));
        root.addView(text("九爻易 · 灵棋经 · 七星数 · 万事三角定律", 13, MUTED, false));
        gap(10);

        LinearLayout controls = card(Color.rgb(250, 247, 238));
        controls.addView(text("随机数 / Seed", 18, GOLD, true));
        controls.addView(text("随机起盘使用安全随机 Seed；输入任意数字或文字可重复复现同一盘。", 12, MUTED, false));

        seedInput = new EditText(this);
        seedInput.setHint("输入固定 Seed（数字或文字，可空）");
        seedInput.setSingleLine(true);
        controls.addView(seedInput, new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button random = button("随机下一盘");
        random.setOnClickListener(v -> newRandom());
        Button fixed = button("按输入起盘");
        fixed.setOnClickListener(v -> castFixed());
        row.addView(random, weight());
        row.addView(fixed, weight());
        controls.addView(row);

        Button copy = button("复制本盘全部结果");
        copy.setOnClickListener(v -> copyAll());
        controls.addView(copy, new LinearLayout.LayoutParams(-1, dp(48)));

        seedView = text("", 12, MUTED, false);
        seedView.setPadding(0, dp(5), 0, 0);
        controls.addView(seedView);
        root.addView(controls);
        gap(12);

        nineView = section("九爻易 · 512组合", "三组三爻卦叠为天—人—地九爻结构", true);
        lingView = section("灵棋经 · 125组合", "模拟上、中、下各4枚两面棋的真实逐枚抛掷", false);
        sevenView = section("七星数 · 21牌试验盘", "双骰 + 21牌随机洗牌 + 三组七牌；暂不补造未核定古法断辞", false);
        triangleView = section("万事三角定律 · 0~12数字盘", "随机生成天时、地时、人时三数，并显示基础五行/阴阳", false);

        LinearLayout note = card(SOFT);
        note.addView(text("本版范围", 17, INK, true));
        note.addView(text("第一版只做“随机起数 + 排盘 + Seed复现”，不加入足球胜平负、比分、大小球等预测层。后续可以在不改变原始排盘的前提下，再单独增加赛事映射和盲测统计。", 13, MUTED, false));
        root.addView(note);
    }

    private TextView section(String title, String subtitle, boolean mono) {
        LinearLayout c = card(SOFT);
        c.addView(text(title, 19, ACCENT, true));
        c.addView(text(subtitle, 12, MUTED, false));
        TextView body = text("", 14, INK, false);
        body.setPadding(0, dp(8), 0, 0);
        body.setLineSpacing(dp(3), 1f);
        if (mono) body.setTypeface(Typeface.MONOSPACE);
        c.addView(body);
        root.addView(c);
        gap(10);
        return body;
    }

    private void newRandom() {
        state = ChartEngine.castRandom();
        showState();
    }

    private void castFixed() {
        String s = seedInput.getText().toString().trim();
        if (s.isEmpty()) {
            Toast.makeText(this, "请输入数字或文字 Seed", Toast.LENGTH_SHORT).show();
            return;
        }
        long seed;
        try {
            seed = Long.parseLong(s);
        } catch (Exception e) {
            seed = ChartEngine.seedFromText(s);
        }
        state = ChartEngine.cast(seed);
        showState();
    }

    private void showState() {
        if (state == null) return;
        seedView.setText("本局 Seed：" + state.seed + "  ·  同一 Seed 可复现全部四盘");
        nineView.setText(state.nineYao.text);
        lingView.setText(state.lingQi.text);
        sevenView.setText(state.sevenStar.text);
        triangleView.setText(state.triangle.text);
    }

    private void copyAll() {
        if (state == null) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("四术随机排盘", state.fullText()));
        Toast.makeText(this, "已复制本盘", Toast.LENGTH_SHORT).show();
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
        return (int) (x * getResources().getDisplayMetrics().density + 0.5f);
    }
}
