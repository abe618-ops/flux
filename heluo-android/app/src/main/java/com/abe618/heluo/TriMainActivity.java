package com.abe618.heluo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TriMainActivity extends MainActivity {
    private static final String PREFS = "tri_method_prefs";
    private static final String ORDER_KEY = "method_order";
    private static final int BG = Color.rgb(250, 247, 239);
    private static final int CARD = Color.rgb(255, 251, 240);
    private static final int BROWN = Color.rgb(91, 54, 28);
    private static final int RED = Color.rgb(205, 49, 45);
    private static final int BLUE = Color.rgb(32, 112, 180);
    private static final int GREEN = Color.rgb(41, 128, 92);
    private static final int MUTED = Color.rgb(105, 99, 89);

    private final List<String> methodOrder = new ArrayList<>();
    private LinearLayout methodRows;
    private TextView consensusView;
    private TextView whoView;
    private TextView statsView;
    private TriMethodEngine.Result triResult;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadOrder();
        triResult = TriMethodEngine.drawConsensus();
        installTopPanel();
    }

    private void installTopPanel() {
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;

        View original = content.getChildAt(0);
        content.removeView(original);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);

        View panel = buildTopPanel();
        shell.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(original, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View buildTopPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));
        panel.setBackgroundColor(CARD);

        TextView title = text("三法联判 · 顺序可调", 20, BROWN, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, lpMatch(dp(30)));

        consensusView = text("", 18, RED, Typeface.BOLD);
        consensusView.setGravity(Gravity.CENTER);
        consensusView.setBackgroundColor(Color.rgb(255, 239, 210));
        panel.addView(consensusView, lpMatch(dp(40)));

        whoView = text("", 13, BROWN, Typeface.BOLD);
        whoView.setGravity(Gravity.CENTER_VERTICAL);
        whoView.setPadding(dp(8), dp(5), dp(8), dp(5));
        panel.addView(whoView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statsView = text("", 15, BLUE, Typeface.BOLD);
        statsView.setGravity(Gravity.CENTER);
        statsView.setBackgroundColor(Color.rgb(238, 245, 252));
        statsView.setPadding(dp(6), dp(5), dp(6), dp(5));
        panel.addView(statsView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        methodRows = new LinearLayout(this);
        methodRows.setOrientation(LinearLayout.VERTICAL);
        panel.addView(methodRows, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(5), 0, 0);

        Button redraw = button("重新抽取三法");
        redraw.setOnClickListener(v -> {
            triResult = TriMethodEngine.drawConsensus();
            renderTop();
        });
        actions.addView(redraw, new LinearLayout.LayoutParams(0, dp(42), 1f));

        View gap = new View(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));

        Button copy = button("复制统计结果");
        copy.setOnClickListener(v -> copyTriResult());
        actions.addView(copy, new LinearLayout.LayoutParams(0, dp(42), 1f));
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        renderTop();
        return panel;
    }

    private void renderTop() {
        if (triResult == null || methodRows == null) return;

        consensusView.setText("最终：" + triResult.taiXuan.size + " ｜ " + triResult.parity);

        int single = 0;
        int dbl = 0;
        List<String> singleNames = new ArrayList<>();
        List<String> doubleNames = new ArrayList<>();

        if ("单".equals(triResult.ceGui.parity)) {
            single++;
            singleNames.add("策轨数");
        } else {
            dbl++;
            doubleNames.add("策轨数");
        }

        if ("单".equals(triResult.zhouYiCe.parity)) {
            single++;
            singleNames.add("周易策数");
        } else {
            dbl++;
            doubleNames.add("周易策数");
        }

        int big = "大".equals(triResult.taiXuan.size) ? 1 : 0;
        int small = "小".equals(triResult.taiXuan.size) ? 1 : 0;
        String bigNames = big == 1 ? "太玄数" : "无";
        String smallNames = small == 1 ? "太玄数" : "无";

        whoView.setText(
                "单：" + names(singleNames) + "    双：" + names(doubleNames) + "\n" +
                "大：" + bigNames + "    小：" + smallNames);

        statsView.setText(
                "单双统计：单 " + single + " 家 · 双 " + dbl + " 家    ｜    " +
                "大小统计：大 " + big + " 家 · 小 " + small + " 家");

        methodRows.removeAllViews();
        for (int i = 0; i < methodOrder.size(); i++) {
            final int pos = i;
            final String key = methodOrder.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(2), dp(2), dp(2));

            TextView info = text(methodText(key), 14,
                    "TX".equals(key) ? RED : ("CG".equals(key) ? BLUE : GREEN), Typeface.BOLD);
            info.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(info, new LinearLayout.LayoutParams(0, dp(40), 1f));

            Button up = miniButton("↑");
            up.setEnabled(pos > 0);
            up.setOnClickListener(v -> move(pos, -1));
            row.addView(up, new LinearLayout.LayoutParams(dp(42), dp(36)));

            Button down = miniButton("↓");
            down.setEnabled(pos < methodOrder.size() - 1);
            down.setOnClickListener(v -> move(pos, 1));
            row.addView(down, new LinearLayout.LayoutParams(dp(42), dp(36)));

            methodRows.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private String names(List<String> xs) {
        if (xs.isEmpty()) return "无";
        return String.join("、", xs);
    }

    private String methodText(String key) {
        if ("TX".equals(key)) {
            return "太玄数：" + triResult.taiXuan.size;
        }
        if ("CG".equals(key)) {
            return "策轨数：" + triResult.ceGui.parity;
        }
        return "周易策数：" + triResult.zhouYiCe.parity;
    }

    private void move(int pos, int delta) {
        int to = pos + delta;
        if (to < 0 || to >= methodOrder.size()) return;
        String x = methodOrder.remove(pos);
        methodOrder.add(to, x);
        saveOrder();
        renderTop();
    }

    private void loadOrder() {
        methodOrder.clear();
        String saved = prefs.getString(ORDER_KEY, "TX,CG,ZY");
        if (saved != null) {
            for (String s : saved.split(",")) {
                if (("TX".equals(s) || "CG".equals(s) || "ZY".equals(s)) && !methodOrder.contains(s)) {
                    methodOrder.add(s);
                }
            }
        }
        for (String k : Arrays.asList("TX", "CG", "ZY")) {
            if (!methodOrder.contains(k)) methodOrder.add(k);
        }
    }

    private void saveOrder() {
        prefs.edit().putString(ORDER_KEY, String.join(",", methodOrder)).apply();
    }

    private void copyTriResult() {
        if (triResult == null) return;

        int single = 0;
        int dbl = 0;
        List<String> singleNames = new ArrayList<>();
        List<String> doubleNames = new ArrayList<>();
        if ("单".equals(triResult.ceGui.parity)) {
            single++; singleNames.add("策轨数");
        } else {
            dbl++; doubleNames.add("策轨数");
        }
        if ("单".equals(triResult.zhouYiCe.parity)) {
            single++; singleNames.add("周易策数");
        } else {
            dbl++; doubleNames.add("周易策数");
        }
        int big = "大".equals(triResult.taiXuan.size) ? 1 : 0;
        int small = "小".equals(triResult.taiXuan.size) ? 1 : 0;

        String s = "三法联判\n"
                + "最终：" + triResult.taiXuan.size + "｜" + triResult.parity + "\n"
                + "太玄数：" + triResult.taiXuan.size + "\n"
                + "策轨数：" + triResult.ceGui.parity + "\n"
                + "周易策数：" + triResult.zhouYiCe.parity + "\n"
                + "单：" + names(singleNames) + "｜双：" + names(doubleNames) + "\n"
                + "单双统计：单" + single + "家｜双" + dbl + "家\n"
                + "大小统计：大" + big + "家｜小" + small + "家";

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("三法联判统计", s));
            Toast.makeText(this, "统计结果已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView text(String s, int sp, int color, int style) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, style);
        v.setPadding(dp(4), 0, dp(4), 0);
        return v;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackgroundColor(BROWN);
        b.setAllCaps(false);
        return b;
    }

    private Button miniButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(18);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(0, 0, 0, 0);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams lpMatch(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
}
