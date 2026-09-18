package com.abe618.fourdivfootball;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int INK = Color.rgb(27, 32, 36);
    private static final int MUTED = Color.rgb(102, 110, 116);
    private static final int BLUE = Color.rgb(27, 104, 185);
    private static final int GOLD = Color.rgb(164, 111, 21);
    private static final int SOFT = Color.rgb(246, 248, 249);
    private static final int GREEN = Color.rgb(36, 122, 77);
    private static final int COLD = Color.rgb(147, 67, 45);

    private LinearLayout root;
    private EditText seedInput, labelInput, homeScoreInput, awayScoreInput;
    private TextView directionView, primaryScoreView, secondaryView, coldView;
    private TextView resultDetailView, structureView, seedView;
    private TextView mayaView, sikidyView, ifaView, ramlView, frozenView, historyView;
    private FourDivinationEngine.CastState state;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("four_divination_lab_v2", MODE_PRIVATE);
        buildUi();
        recastRandom();
        refreshFrozen();
        refreshHistory();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(32));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        root.addView(text("四术足球盲测 v0.2", 27, INK, true));
        root.addView(text("玛雅 Tzolk'in · Sikidy · Ifá · 阿拉伯沙占", 13, MUTED, false));
        gap(10);

        LinearLayout resultCard = card(Color.rgb(250, 247, 238));
        TextView rt = text("合参主结论", 17, GOLD, true);
        rt.setGravity(Gravity.CENTER_HORIZONTAL);
        resultCard.addView(rt);

        directionView = text("—", 33, INK, true);
        directionView.setGravity(Gravity.CENTER_HORIZONTAL);
        directionView.setPadding(0, dp(3), 0, dp(2));
        resultCard.addView(directionView);

        primaryScoreView = text("—", 31, BLUE, true);
        primaryScoreView.setGravity(Gravity.CENTER_HORIZONTAL);
        resultCard.addView(primaryScoreView);

        secondaryView = text("", 16, INK, true);
        secondaryView.setGravity(Gravity.CENTER_HORIZONTAL);
        resultCard.addView(secondaryView);

        coldView = text("", 15, COLD, true);
        coldView.setGravity(Gravity.CENTER_HORIZONTAL);
        coldView.setPadding(0, dp(3), 0, 0);
        resultCard.addView(coldView);

        resultDetailView = text("", 16, INK, true);
        resultDetailView.setGravity(Gravity.CENTER_HORIZONTAL);
        resultDetailView.setLineSpacing(dp(4), 1f);
        resultDetailView.setPadding(0, dp(8), 0, 0);
        resultCard.addView(resultDetailView);

        structureView = text("", 12, MUTED, false);
        structureView.setLineSpacing(dp(3), 1f);
        structureView.setPadding(0, dp(9), 0, 0);
        resultCard.addView(structureView);

        root.addView(resultCard);
        gap(12);

        LinearLayout castCard = card(SOFT);
        castCard.addView(text("起盘 / 复现", 19, INK, true));

        labelInput = new EditText(this);
        labelInput.setHint("样本名称/比赛名称（可空）");
        labelInput.setSingleLine(true);
        castCard.addView(labelInput, new LinearLayout.LayoutParams(-1, dp(48)));

        seedInput = new EditText(this);
        seedInput.setHint("固定 Seed：数字或文字");
        seedInput.setSingleLine(true);
        castCard.addView(seedInput, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        Button random = button("随机起盘");
        random.setOnClickListener(v -> recastRandom());

        Button fixed = button("固定起盘");
        fixed.setOnClickListener(v -> recastFixed());

        row1.addView(random, weight());
        row1.addView(fixed, weight());
        castCard.addView(row1);

        Button freeze = button("冻结本场样本");
        freeze.setOnClickListener(v -> freezeCurrent());
        castCard.addView(freeze, new LinearLayout.LayoutParams(-1, dp(50)));

        seedView = text("", 12, MUTED, false);
        seedView.setPadding(0, dp(5), 0, 0);
        castCard.addView(seedView);

        root.addView(castCard);
        gap(12);

        root.addView(text("四术独立判断", 20, INK, true));
        mayaView = systemCard("玛雅 Tzolk'in");
        sikidyView = systemCard("Sikidy");
        ifaView = systemCard("Ifá");
        ramlView = systemCard("阿拉伯沙占");
        gap(10);

        LinearLayout review = card(Color.rgb(244, 249, 246));
        review.addView(text("赛果复盘", 20, GREEN, true));

        frozenView = text("", 13, MUTED, false);
        review.addView(frozenView);

        LinearLayout scores = new LinearLayout(this);
        scores.setOrientation(LinearLayout.HORIZONTAL);

        homeScoreInput = numberBox("主队");
        awayScoreInput = numberBox("客队");

        scores.addView(homeScoreInput, weight());
        scores.addView(awayScoreInput, weight());
        review.addView(scores);

        Button record = button("录入赛果并复盘");
        record.setOnClickListener(v -> recordResult());
        review.addView(record, new LinearLayout.LayoutParams(-1, dp(50)));

        root.addView(review);
        gap(12);

        LinearLayout history = card(SOFT);
        history.addView(text("近期复盘记录", 18, INK, true));

        historyView = text("", 12, MUTED, false);
        historyView.setLineSpacing(dp(3), 1f);
        history.addView(historyView);

        Button clear = button("清空本机复盘记录");
        clear.setOnClickListener(v -> {
            prefs.edit().remove("history").apply();
            refreshHistory();
        });
        history.addView(clear, new LinearLayout.LayoutParams(-1, dp(46)));

        root.addView(history);
        gap(12);

        LinearLayout note = card(Color.rgb(250, 250, 250));
        note.addView(text("v0.2 内核", 17, INK, true));
        note.addView(text(
                "这版不再把四术简单平均。先形成方向票数，再比较每一票的强度；如果多数票与沙占强反向/零封信号冲突，则把“平局”作为独立中间态重新排序。Ifá集中只表示单边化，不自动指定主客；Ifá集中与沙占前静后强同时出现时，强制保留3:0/0:3类爆发比分。Sikidy稳定/双数象只影响结构，不机械等同于小球。最终按“主线→次防→冷防”输出，与聊天中的人工合参流程保持一致。该工具仅用于盲测和统计实验，不代表真实比赛概率。",
                13, MUTED, false
        ));
        root.addView(note);
    }

    private TextView systemCard(String title) {
        LinearLayout c = card(SOFT);
        c.addView(text(title, 17, BLUE, true));

        TextView body = text("", 13, INK, false);
        body.setPadding(0, dp(5), 0, 0);
        body.setLineSpacing(dp(3), 1f);
        c.addView(body);

        root.addView(c);
        gap(8);
        return body;
    }

    private void recastRandom() {
        state = FourDivinationEngine.castRandom();
        showState();
    }

    private void recastFixed() {
        String s = seedInput.getText().toString().trim();
        if (s.isEmpty()) {
            Toast.makeText(this, "请输入数字或文字 Seed", Toast.LENGTH_SHORT).show();
            return;
        }

        long seed;
        try {
            seed = Long.parseLong(s);
        } catch (Exception e) {
            seed = FourDivinationEngine.seedFromText(s);
        }

        state = FourDivinationEngine.cast(seed);
        showState();
    }

    private void showState() {
        if (state == null) return;

        FourDivinationEngine.CombinedResult r = state.combined;

        directionView.setText(r.directionRanking);
        primaryScoreView.setText("首选比分  " + r.primaryScore);
        secondaryView.setText("次选  " + r.secondaryScore + "   ·   " + r.safer);
        coldView.setText("冷防  " + r.coldScores);

        resultDetailView.setText(
                "半全场  " + r.halfFull + "   次防 " + r.halfFullBackup
                        + "\n进球  " + r.goalRange + " · " + r.goalFocus
                        + "\n" + r.overUnder + "   ·   " + r.oddEven
                        + "\n候选比分  " + r.scoreCandidates
        );

        structureView.setText(
                "合参倾向度 " + r.tendency + "/100（内部一致度，不是概率）\n"
                        + r.structure + "\n"
                        + r.rationale
        );

        seedView.setText("本局 Seed：" + state.seed + " · 同一 Seed 可完整复现");

        showSystem(mayaView, state.maya);
        showSystem(sikidyView, state.sikidy);
        showSystem(ifaView, state.ifa);
        showSystem(ramlView, state.raml);
    }

    private void showSystem(TextView v, FourDivinationEngine.SystemResult x) {
        v.setText(x.raw + "\n" + x.summary() + "\n" + x.note);
    }

    private void freezeCurrent() {
        if (state == null) return;

        String label = labelInput.getText().toString().trim();
        if (label.isEmpty()) {
            label = "样本-" + new SimpleDateFormat("MMdd-HHmm", Locale.CHINA).format(new Date());
        }

        prefs.edit()
                .putLong("frozen_seed", state.seed)
                .putString("frozen_label", label)
                .putBoolean("has_frozen", true)
                .apply();

        refreshFrozen();
        Toast.makeText(this, "已冻结：" + label, Toast.LENGTH_SHORT).show();
    }

    private void refreshFrozen() {
        if (!prefs.getBoolean("has_frozen", false)) {
            frozenView.setText("当前没有冻结样本。");
            return;
        }

        long seed = prefs.getLong("frozen_seed", 0L);
        String label = prefs.getString("frozen_label", "样本");
        FourDivinationEngine.CastState s = FourDivinationEngine.cast(seed);
        FourDivinationEngine.CombinedResult r = s.combined;

        frozenView.setText(
                "已冻结：" + label
                        + "\n" + r.directionRanking
                        + " · 首选 " + r.primaryScore
                        + " · 冷防 " + r.coldScores
                        + "\nSeed " + seed
        );
    }

    private void recordResult() {
        if (!prefs.getBoolean("has_frozen", false)) {
            Toast.makeText(this, "请先冻结一个样本", Toast.LENGTH_SHORT).show();
            return;
        }

        String hs = homeScoreInput.getText().toString().trim();
        String as = awayScoreInput.getText().toString().trim();

        if (hs.isEmpty() || as.isEmpty()) {
            Toast.makeText(this, "请输入主客队最终比分", Toast.LENGTH_SHORT).show();
            return;
        }

        int h;
        int a;
        try {
            h = Integer.parseInt(hs);
            a = Integer.parseInt(as);
        } catch (Exception e) {
            Toast.makeText(this, "比分必须是整数", Toast.LENGTH_SHORT).show();
            return;
        }

        long seed = prefs.getLong("frozen_seed", 0L);
        String label = prefs.getString("frozen_label", "样本");

        FourDivinationEngine.CastState s = FourDivinationEngine.cast(seed);
        FourDivinationEngine.CombinedResult r = s.combined;

        String actualDir = h > a ? "主胜" : h < a ? "客胜" : "平局";
        int total = h + a;
        String actualOU = total >= 3 ? "大2.5" : "小2.5";
        String actualOE = (total & 1) == 1 ? "单" : "双";
        String actualScore = h + ":" + a;

        boolean hitDir = r.direction.equals(actualDir);
        boolean hitOU = r.overUnder.startsWith(actualOU);
        boolean hitOE = r.oddEven.startsWith(actualOE) || r.oddEven.contains(actualOE + "数仅作微倾");
        boolean hitScore = r.scoreCandidates.contains(actualScore);

        int low;
        int high;
        if (r.goalRange.startsWith("1–2")) {
            low = 1; high = 2;
        } else if (r.goalRange.startsWith("1–3")) {
            low = 1; high = 3;
        } else if (r.goalRange.startsWith("2–3")) {
            low = 2; high = 3;
        } else if (r.goalRange.startsWith("2–4")) {
            low = 2; high = 4;
        } else {
            low = 3; high = 4;
        }
        boolean hitRange = total >= low && total <= high;

        String line = label + "  " + actualScore
                + "｜主方向" + mark(hitDir)
                + " 大小" + mark(hitOU)
                + " 单双" + mark(hitOE)
                + " 区间" + mark(hitRange)
                + " 比分候选" + mark(hitScore)
                + "\n冻结：" + r.directionRanking
                + " / 首选" + r.primaryScore
                + " / 冷防" + r.coldScores
                + " / " + r.overUnder
                + " / " + r.oddEven
                + "｜Seed " + seed;

        String old = prefs.getString("history", "");
        String merged = line + (old.isEmpty() ? "" : "\n\n" + old);

        if (merged.length() > 10000) {
            merged = merged.substring(0, 10000);
        }

        prefs.edit()
                .putString("history", merged)
                .putBoolean("has_frozen", false)
                .apply();

        refreshHistory();
        refreshFrozen();

        homeScoreInput.setText("");
        awayScoreInput.setText("");

        Toast.makeText(this, "赛果已记录，冻结盘未修改", Toast.LENGTH_SHORT).show();
    }

    private String mark(boolean yes) {
        return yes ? "✓" : "×";
    }

    private void refreshHistory() {
        String h = prefs.getString("history", "");
        historyView.setText(h.isEmpty() ? "暂无记录。" : h);
    }

    private EditText numberBox(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint + "进球");
        e.setGravity(Gravity.CENTER);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    private LinearLayout card(int color) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(13), dp(14), dp(13));

        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(16));
        g.setStroke(dp(1), Color.rgb(225, 229, 232));

        l.setBackground(g);
        return l;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
        p.setMargins(dp(2), 0, dp(2), 0);
        return p;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void gap(int d) {
        View v = new View(this);
        root.addView(v, new LinearLayout.LayoutParams(1, dp(d)));
    }

    private int dp(float x) {
        return (int)(x * getResources().getDisplayMetrics().density + 0.5f);
    }
}
