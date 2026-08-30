package com.abe618.heluo;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(248, 244, 233);
    private static final int CARD = Color.rgb(255, 251, 240);
    private static final int AMBER = Color.rgb(241, 193, 86);
    private static final int BLUE = Color.rgb(32, 112, 180);
    private static final int BROWN = Color.rgb(91, 54, 28);
    private static final int RED = Color.rgb(218, 46, 45);
    private static final int GREEN = Color.rgb(41, 128, 92);
    private static final int MUTED = Color.rgb(110, 103, 91);

    private LinearLayout root;
    private HeluoEngine.Result current;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Color.rgb(250, 247, 239));
        w.setNavigationBarColor(Color.rgb(250, 247, 239));
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scroll);
        generateNew();
    }

    private void generateNew() {
        long seed = secureRandom.nextLong() ^ System.currentTimeMillis() ^ System.nanoTime();
        current = HeluoEngine.generate(seed);
        render();
    }

    private void render() {
        root.removeAllViews();
        addHeader();
        addPrediction();
        addPillarsAndNumbers();
        addHexagrams();
        addNinePalace();
        addMethodNotes();
    }

    private void addHeader() {
        TextView title = text("河洛数 · 随机赛事起盘", 24, BROWN, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, mp(dp(6), dp(2)));

        TextView subtitle = text("传统河洛理数框架 × 随机种子 × 足球实验映射", 13, MUTED, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(subtitle, mp(0, dp(10)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        Button random = button("随机起盘");
        random.setOnClickListener(v -> generateNew());
        actions.addView(random, new LinearLayout.LayoutParams(0, dp(48), 1f));

        View gap = new View(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));

        Button copy = button("复制结果");
        copy.setOnClickListener(v -> copyCurrent());
        actions.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1f));

        root.addView(actions, mp(0, dp(8)));

        TextView seed = text("种子：" + Long.toUnsignedString(current.seed) + "  ·  同一种子可复演同一盘", 12, MUTED, Typeface.NORMAL);
        seed.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(seed, mp(0, dp(10)));
    }

    private void addPrediction() {
        sectionTitle("赛事预测 · 结果优先");
        LinearLayout card = card();

        HeluoEngine.Prediction p = current.prediction;

        TextView main = text(p.result + "倾向", 30, RED, Typeface.BOLD);
        main.setGravity(Gravity.CENTER);
        card.addView(main, mp(dp(8), dp(4)));

        TextView pct = text("主胜 " + p.home + "%    平 " + p.draw + "%    客胜 " + p.away + "%", 20, BROWN, Typeface.BOLD);
        pct.setGravity(Gravity.CENTER);
        card.addView(pct, mp(dp(4), dp(8)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(stat("总进球", p.goals), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(stat("大小球", p.size), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row, mp(0, dp(6)));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(stat("半全场", p.halfFull), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row2.addView(stat("双方进球", p.btts), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row2, mp(0, dp(6)));

        TextView over = text("大2.5倾向 " + p.over25 + "%    ｜    单双：" + p.oddEven, 16, BLUE, Typeface.BOLD);
        over.setGravity(Gravity.CENTER);
        card.addView(over, mp(dp(5), dp(7)));

        TextView scores = text("比分候选：" + p.scores[0] + "   " + p.scores[1] + "   " + p.scores[2], 19, GREEN, Typeface.BOLD);
        scores.setGravity(Gravity.CENTER);
        card.addView(scores, mp(dp(4), dp(9)));

        TextView note = text("“%”为术数映射后的倾向度，不是统计概率；本版用于实验回测，不作为投注依据。", 12, MUTED, Typeface.NORMAL);
        note.setGravity(Gravity.CENTER);
        card.addView(note, mp(dp(4), dp(5)));

        root.addView(card, mp(0, dp(12)));
    }

    private void addPillarsAndNumbers() {
        sectionTitle("随机四柱与天地数");
        LinearLayout card = card();

        LinearLayout heads = new LinearLayout(this);
        heads.setOrientation(LinearLayout.HORIZONTAL);
        String[] hs = {"年柱","月柱","日柱","时柱"};
        for (String h : hs) {
            TextView v = text(h, 16, BROWN, Typeface.BOLD);
            v.setGravity(Gravity.CENTER);
            v.setBackgroundColor(Color.rgb(250, 224, 163));
            heads.addView(v, new LinearLayout.LayoutParams(0, dp(38), 1f));
        }
        card.addView(heads);

        LinearLayout vals = new LinearLayout(this);
        vals.setOrientation(LinearLayout.HORIZONTAL);
        for (HeluoEngine.Pillar p : current.pillars) {
            TextView v = text(p.text(), 22, Color.BLACK, Typeface.BOLD);
            v.setGravity(Gravity.CENTER);
            vals.addView(v, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        card.addView(vals);

        TextView td = text(
                "天数：" + current.tian + " → " + current.tianGuaNum +
                        "    地数：" + current.di + " → " + current.diGuaNum +
                        "\n" + current.modeInfo,
                15, BROWN, Typeface.BOLD);
        td.setGravity(Gravity.CENTER);
        td.setPadding(dp(4), dp(8), dp(4), dp(8));
        card.addView(td);

        TextView rule = text("干取洛书数；支取河图生成数；奇数相加为天数、偶数相加为地数。", 13, MUTED, Typeface.NORMAL);
        rule.setGravity(Gravity.CENTER);
        card.addView(rule, mp(dp(4), dp(6)));

        root.addView(card, mp(0, dp(12)));
    }

    private void addHexagrams() {
        sectionTitle("先天卦 / 后天卦");

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);

        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        pair.setPadding(0, 0, dp(4), 0);

        pair.addView(hexCard("先天卦", current.preName,
                current.preUpper, current.preLower,
                current.preLines, current.preLineInfo, current.movingLine),
                new LinearLayout.LayoutParams(dp(326), ViewGroup.LayoutParams.WRAP_CONTENT));

        View gap = new View(this);
        pair.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));

        pair.addView(hexCard("后天卦", current.postName,
                current.postUpper, current.postLower,
                current.postLines, current.postLineInfo, current.postMovingLine),
                new LinearLayout.LayoutParams(dp(326), ViewGroup.LayoutParams.WRAP_CONTENT));

        hsv.addView(pair);
        root.addView(hsv, mp(0, dp(12)));
    }

    private LinearLayout hexCard(String label, String name, String upper, String lower,
                                 boolean[] lines, HeluoEngine.LineInfo[] info, int moving) {
        LinearLayout c = card();

        TextView t = text(label + "：" + name + "（" + upper + "上" + lower + "下）", 18, BLUE, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        c.addView(t, mp(dp(4), dp(6)));

        TextView sub = text("元堂：" + (moving + 1) + "爻    五行：" +
                HeluoEngine.trigramElement(upper) + "/" + HeluoEngine.trigramElement(lower), 13, MUTED, Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER);
        c.addView(sub, mp(dp(2), dp(6)));

        for (int visual = 5; visual >= 0; visual--) {
            HeluoEngine.LineInfo li = info[visual];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(5), dp(3), dp(5), dp(3));

            TextView spirit = text(li.spirit, 15, BROWN, Typeface.BOLD);
            row.addView(spirit, new LinearLayout.LayoutParams(dp(52), dp(34)));

            TextView line = text(li.yang ? "━━━━━━" : "━━  ━━", 20,
                    li.moving ? RED : Color.BLACK, Typeface.BOLD);
            line.setGravity(Gravity.CENTER);
            row.addView(line, new LinearLayout.LayoutParams(dp(100), dp(34)));

            TextView kin = text(li.kin + (li.moving ? "  ●" : ""), 14,
                    li.moving ? RED : BROWN, Typeface.BOLD);
            row.addView(kin, new LinearLayout.LayoutParams(dp(74), dp(34)));

            TextView age = text(li.age, 14, BROWN, Typeface.BOLD);
            age.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            row.addView(age, new LinearLayout.LayoutParams(0, dp(34), 1f));

            c.addView(row);
        }

        TextView hint = text("六神按日干起；六亲为第一版五行关系简化显示；阳爻9年、阴爻6年。", 11, MUTED, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        c.addView(hint, mp(dp(4), dp(5)));
        return c;
    }

    private void addNinePalace() {
        sectionTitle("洛书九宫");
        LinearLayout card = card();
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(3);

        String[] cells = {
                "4 巽\n木", "9 离\n火", "2 坤\n土",
                "3 震\n木", "5 中\n土", "7 兑\n金",
                "8 艮\n土", "1 坎\n水", "6 乾\n金"
        };

        for (String s : cells) {
            TextView v = text(s, 18, BROWN, Typeface.BOLD);
            v.setGravity(Gravity.CENTER);
            v.setBackgroundColor(Color.rgb(250, 236, 199));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(70);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            grid.addView(v, lp);
        }
        card.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView r = text("戴九履一，左三右七，二四为肩，六八为足，五居中央。", 13, MUTED, Typeface.NORMAL);
        r.setGravity(Gravity.CENTER);
        card.addView(r, mp(dp(5), dp(5)));

        root.addView(card, mp(0, dp(12)));
    }

    private void addMethodNotes() {
        sectionTitle("本版算法说明");
        LinearLayout card = card();

        String body =
                "① 传统核心：采用河洛理数常见的干支取数、天地数、洛书配卦、先天卦→元堂变爻→上下卦相荡生成后天卦。\n\n" +
                "② 随机化：不要求输入出生时间、球队名称或经纬度。每次点击“随机起盘”生成一个种子，再由种子生成随机四柱和元堂，因此整盘可复现。\n\n" +
                "③ 赛事映射：先天卦上/下卦分别作为主客两方的象，结合五行生克、阴阳爻数量、动爻与后天变化，给出胜平负、总进球、大小球、双方进球、半全场和比分候选。\n\n" +
                "④ 版本边界：传统河洛理数本来主要用于命理与流年推演，并没有公认的“足球标准断法”。本版赛事部分属于实验性映射，适合后续用真实比赛做固定规则回测。\n\n" +
                "⑤ 下一版可继续补：完整元堂时辰表、纳甲六亲/世应、元气化工、至尊卦、大运/流年/流月，以及按赛事数据库进行回测评分。";

        TextView note = text(body, 14, Color.rgb(48, 45, 40), Typeface.NORMAL);
        note.setLineSpacing(dp(4), 1.0f);
        card.addView(note, mp(dp(3), dp(3)));

        TextView footer = text("河洛数·随机赛事盘  v1.0", 12, MUTED, Typeface.BOLD);
        footer.setGravity(Gravity.CENTER);
        card.addView(footer, mp(dp(10), dp(2)));

        root.addView(card, mp(0, dp(4)));
    }

    private void copyCurrent() {
        HeluoEngine.Prediction p = current.prediction;
        String s =
                "河洛数随机赛事盘\n" +
                "种子：" + Long.toUnsignedString(current.seed) + "\n" +
                "四柱：" + current.pillars[0].text() + " " + current.pillars[1].text() + " " +
                current.pillars[2].text() + " " + current.pillars[3].text() + "\n" +
                "天数/地数：" + current.tian + "/" + current.di + "\n" +
                "先天卦：" + current.preName + "  后天卦：" + current.postName + "\n" +
                "胜平负：" + p.result + "（主" + p.home + " 平" + p.draw + " 客" + p.away + "）\n" +
                "总进球：" + p.goals + "  " + p.size + "\n" +
                "半全场：" + p.halfFull + "  双方进球：" + p.btts + "\n" +
                "比分：" + p.scores[0] + " / " + p.scores[1] + " / " + p.scores[2] + "\n" +
                "注：倾向度为实验术数映射，不是统计概率。";

        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("河洛数随机赛事盘", s));
        Toast.makeText(this, "已复制本盘结果", Toast.LENGTH_SHORT).show();
    }

    private TextView stat(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(5), dp(7), dp(5), dp(7));
        box.setBackgroundColor(Color.rgb(253, 241, 211));

        TextView a = text(label, 12, MUTED, Typeface.BOLD);
        a.setGravity(Gravity.CENTER);
        box.addView(a);

        TextView b = text(value, 16, BROWN, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        box.addView(b, mp(dp(2), 0));

        // Return a TextView-compatible wrapper is not possible, so use a carrier TextView?
        // This method is unused; kept only for source compatibility.
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(9), dp(9), dp(9), dp(9));
        c.setBackgroundColor(CARD);
        c.setElevation(dp(1));
        return c;
    }

    private void sectionTitle(String s) {
        TextView t = text(s, 19, BLUE, Typeface.BOLD);
        root.addView(t, mp(dp(4), dp(5)));
    }

    private TextView text(String s, float sp, int color, int style) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, style);
        v.setIncludeFontPadding(false);
        return v;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(17);
        b.setTextColor(BROWN);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackgroundColor(AMBER);
        return b;
    }

    private LinearLayout.LayoutParams mp(int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = top;
        lp.bottomMargin = bottom;
        return lp;
    }

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }
}
