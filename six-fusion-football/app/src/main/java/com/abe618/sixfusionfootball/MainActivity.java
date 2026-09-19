package com.abe618.sixfusionfootball;

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
    private static final int INK = Color.rgb(27,32,36);
    private static final int MUTED = Color.rgb(101,108,114);
    private static final int BLUE = Color.rgb(26,93,170);
    private static final int GOLD = Color.rgb(166,108,12);
    private static final int GREEN = Color.rgb(34,124,77);
    private static final int RED = Color.rgb(158,63,50);
    private static final int SOFT = Color.rgb(246,248,250);

    private LinearLayout root;
    private EditText seedInput, labelInput, homeScoreInput, awayScoreInput;
    private TextView rankingView, badgeView, scoreView, secondScoreView, detailView, structureView, seedView;
    private TextView mayaView, sikidyView, ifaView, ramlView, bingView, correctionView, frozenView, historyView;
    private FusionEngine.CastState state;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("six_fusion_football_v20", MODE_PRIVATE);
        buildUi();
        recastRandom();
        refreshFrozen();
        refreshHistory();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(24),dp(14),dp(32));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        setContentView(scroll);

        root.addView(text("六维足球合参 V2.0",28,INK,true));
        root.addView(text("四术 D1–D4 + 数字兵占 D5 + 主客/盘口方向校正 D6",13,MUTED,false));
        gap(10);

        LinearLayout top = card(Color.rgb(251,247,236));
        TextView t = text("最终合参结果",17,GOLD,true);
        t.setGravity(Gravity.CENTER);
        top.addView(t);

        rankingView = text("—",34,INK,true);
        rankingView.setGravity(Gravity.CENTER);
        rankingView.setPadding(0,dp(5),0,dp(2));
        top.addView(rankingView);

        badgeView = text("—",16,RED,true);
        badgeView.setGravity(Gravity.CENTER);
        top.addView(badgeView);

        scoreView = text("—",31,BLUE,true);
        scoreView.setGravity(Gravity.CENTER);
        scoreView.setPadding(0,dp(5),0,0);
        top.addView(scoreView);

        secondScoreView = text("",16,INK,true);
        secondScoreView.setGravity(Gravity.CENTER);
        top.addView(secondScoreView);

        detailView = text("",15,INK,true);
        detailView.setGravity(Gravity.CENTER);
        detailView.setLineSpacing(dp(4),1f);
        detailView.setPadding(0,dp(8),0,0);
        top.addView(detailView);

        structureView = text("",12,MUTED,false);
        structureView.setLineSpacing(dp(3),1f);
        structureView.setPadding(0,dp(8),0,0);
        top.addView(structureView);

        root.addView(top);
        gap(12);

        LinearLayout cast = card(SOFT);
        cast.addView(text("起盘 / 冻结",19,INK,true));

        labelInput = new EditText(this);
        labelInput.setHint("比赛/样本名称（可空）");
        labelInput.setSingleLine(true);
        cast.addView(labelInput,new LinearLayout.LayoutParams(-1,dp(48)));

        seedInput = new EditText(this);
        seedInput.setHint("固定 Seed：数字或文字");
        seedInput.setSingleLine(true);
        cast.addView(seedInput,new LinearLayout.LayoutParams(-1,dp(48)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button random = button("随机起盘");
        random.setOnClickListener(v -> recastRandom());
        Button fixed = button("固定起盘");
        fixed.setOnClickListener(v -> recastFixed());
        row.addView(random,weight());
        row.addView(fixed,weight());
        cast.addView(row);

        Button freeze = button("冻结本场");
        freeze.setOnClickListener(v -> freezeCurrent());
        cast.addView(freeze,new LinearLayout.LayoutParams(-1,dp(50)));

        seedView = text("",12,MUTED,false);
        seedView.setPadding(0,dp(5),0,0);
        cast.addView(seedView);

        root.addView(cast);
        gap(12);

        root.addView(text("六维独立判断",20,INK,true));
        mayaView = systemCard("D1 玛雅 Tzolk'in");
        sikidyView = systemCard("D2 Sikidy");
        ifaView = systemCard("D3 Ifá");
        ramlView = systemCard("D4 阿拉伯沙占");
        bingView = systemCard("D5 数字兵占 V1.6");
        correctionView = systemCard("D6 方向校正 V1.7");
        gap(8);

        LinearLayout review = card(Color.rgb(243,249,245));
        review.addView(text("赛果复盘",20,GREEN,true));
        frozenView = text("",13,MUTED,false);
        review.addView(frozenView);

        LinearLayout scoreRow = new LinearLayout(this);
        scoreRow.setOrientation(LinearLayout.HORIZONTAL);
        homeScoreInput = numberBox("主队");
        awayScoreInput = numberBox("客队");
        scoreRow.addView(homeScoreInput,weight());
        scoreRow.addView(awayScoreInput,weight());
        review.addView(scoreRow);

        Button record = button("录入终场比分并复盘");
        record.setOnClickListener(v -> recordResult());
        review.addView(record,new LinearLayout.LayoutParams(-1,dp(50)));
        root.addView(review);
        gap(12);

        LinearLayout hist = card(SOFT);
        hist.addView(text("近期盲测记录",18,INK,true));
        historyView = text("",12,MUTED,false);
        historyView.setLineSpacing(dp(3),1f);
        hist.addView(historyView);
        Button clear = button("清空本机记录");
        clear.setOnClickListener(v -> {
            prefs.edit().remove("history").apply();
            refreshHistory();
        });
        hist.addView(clear,new LinearLayout.LayoutParams(-1,dp(46)));
        root.addView(hist);
        gap(12);

        LinearLayout note = card(Color.rgb(250,250,250));
        note.addView(text("V2.0 融合内核",17,INK,true));
        note.addView(text(
                "胜平负采用：四术40% + 数字兵占30% + 方向校正30%；总进球采用：四术45% + 数字兵占45% + 校正10%。"
                + "方向层与结构层分离；共振等级、镜像风险、净胜球单独显示。"
                + "两高两低保留双峰；单边/后程增强时主动扩展3:0/0:3与4:0/0:4镜像尾部。"
                + "单双长期降权。冻结后赛果只做复盘，不回写预测。该应用用于盲测实验，不代表真实比赛概率。",
                13,MUTED,false
        ));
        root.addView(note);
    }

    private TextView systemCard(String title) {
        LinearLayout c = card(SOFT);
        c.addView(text(title,17,BLUE,true));
        TextView body = text("",13,INK,false);
        body.setPadding(0,dp(5),0,0);
        body.setLineSpacing(dp(3),1f);
        c.addView(body);
        root.addView(c);
        gap(8);
        return body;
    }

    private void recastRandom() {
        state = FusionEngine.castRandom();
        showState();
    }

    private void recastFixed() {
        String s = seedInput.getText().toString().trim();
        if (s.isEmpty()) {
            Toast.makeText(this,"请输入 Seed",Toast.LENGTH_SHORT).show();
            return;
        }
        long seed;
        try { seed = Long.parseLong(s); }
        catch(Exception e) { seed = FusionEngine.seedFromText(s); }
        state = FusionEngine.cast(seed);
        showState();
    }

    private void showState() {
        if (state == null) return;
        FusionEngine.Combined r = state.combined;
        rankingView.setText(r.ranking);
        badgeView.setText(r.resonance + "  ·  镜像风险 " + r.mirrorRisk);
        scoreView.setText("首选比分  " + r.primaryScore);
        secondScoreView.setText("次选 " + r.secondaryScore + "  ·  " + r.safer);

        detailView.setText(
                r.handicap
                + "\n半全场 " + r.halfFull + "  /  次防 " + r.halfFullBackup
                + "\n总进球 " + r.goalRange + " · " + r.goalFocus
                + "\n" + r.overUnder + "  ·  " + r.parity + "  ·  " + r.netGoal
                + "\n防守 " + r.defenseScores
                + "\n高端尾部 " + r.tailScores
                + "\n镜像 " + r.mirrorScores
        );

        structureView.setText(
                "合参倾向度 " + r.tendency + "/100（内部一致度，不是概率）\n"
                + r.structure + "\n" + r.rationale
        );

        seedView.setText("Seed：" + Long.toUnsignedString(state.seed) + " · 固定 Seed 可复现本局");

        showSignal(mayaView,state.maya);
        showSignal(sikidyView,state.sikidy);
        showSignal(ifaView,state.ifa);
        showSignal(ramlView,state.raml);

        bingView.setText(
                state.bingzhan.raw + "\n"
                + state.bingzhan.summary() + "\n"
                + state.bingzhan.note
        );

        correctionView.setText(
                state.correction.raw + "\n"
                + state.correction.summary() + "\n"
                + state.correction.note
        );
    }

    private void showSignal(TextView v,FusionEngine.Signal s) {
        v.setText(s.raw + "\n" + s.summary() + "\n" + s.note);
    }

    private void freezeCurrent() {
        if (state == null) return;
        String label = labelInput.getText().toString().trim();
        if (label.isEmpty()) label = "样本-" + new SimpleDateFormat("MMdd-HHmm",Locale.CHINA).format(new Date());

        prefs.edit()
                .putLong("frozen_seed",state.seed)
                .putString("frozen_label",label)
                .putBoolean("has_frozen",true)
                .apply();
        refreshFrozen();
        Toast.makeText(this,"已冻结："+label,Toast.LENGTH_SHORT).show();
    }

    private void refreshFrozen() {
        if (!prefs.getBoolean("has_frozen",false)) {
            frozenView.setText("当前没有冻结样本。");
            return;
        }
        long seed = prefs.getLong("frozen_seed",0L);
        String label = prefs.getString("frozen_label","样本");
        FusionEngine.CastState s = FusionEngine.cast(seed);
        FusionEngine.Combined r = s.combined;
        frozenView.setText(
                "已冻结：" + label
                + "\n" + r.ranking + " · " + r.resonance + " · 镜像" + r.mirrorRisk
                + "\n首选 " + r.primaryScore + " · 次选 " + r.secondaryScore
                + "\nSeed " + Long.toUnsignedString(seed)
        );
    }

    private void recordResult() {
        if (!prefs.getBoolean("has_frozen",false)) {
            Toast.makeText(this,"请先冻结一个样本",Toast.LENGTH_SHORT).show();
            return;
        }
        String hs=homeScoreInput.getText().toString().trim();
        String as=awayScoreInput.getText().toString().trim();
        if (hs.isEmpty() || as.isEmpty()) {
            Toast.makeText(this,"请输入终场比分",Toast.LENGTH_SHORT).show();
            return;
        }
        int h,a;
        try { h=Integer.parseInt(hs); a=Integer.parseInt(as); }
        catch(Exception e) {
            Toast.makeText(this,"比分必须是整数",Toast.LENGTH_SHORT).show();
            return;
        }

        long seed=prefs.getLong("frozen_seed",0L);
        String label=prefs.getString("frozen_label","样本");
        FusionEngine.CastState s=FusionEngine.cast(seed);
        FusionEngine.Combined r=s.combined;

        String actualDir=h>a?"主胜":h<a?"客胜":"平局";
        String topDir = r.ranking.startsWith("主胜")?"主胜":r.ranking.startsWith("客胜")?"客胜":"平局";
        int total=h+a;
        String actualScore=h+":"+a;
        boolean hitDir=topDir.equals(actualDir);
        boolean hitOU=(total>=3 && r.overUnder.startsWith("大")) || (total<3 && r.overUnder.startsWith("小"));
        boolean hitScore=r.scorePool.contains(actualScore);
        boolean hitMirror=r.mirrorScores.contains(actualScore);
        boolean hitNet = hitNet(r.netGoal,Math.abs(h-a));

        String line = label + "  " + actualScore
                + "｜主方向" + mark(hitDir)
                + " 大小" + mark(hitOU)
                + " 净胜" + mark(hitNet)
                + " 比分池" + mark(hitScore)
                + " 镜像" + mark(hitMirror)
                + "\n冻结：" + r.ranking + " / " + r.resonance + " / 镜像" + r.mirrorRisk
                + " / 首选" + r.primaryScore + " / 次选" + r.secondaryScore
                + " / " + r.goalRange + " / " + r.netGoal
                + "｜Seed " + Long.toUnsignedString(seed);

        String old=prefs.getString("history","");
        String merged=line+(old.isEmpty()?"":"\n\n"+old);
        if (merged.length()>14000) merged=merged.substring(0,14000);
        prefs.edit().putString("history",merged).putBoolean("has_frozen",false).apply();

        homeScoreInput.setText("");
        awayScoreInput.setText("");
        refreshHistory();
        refreshFrozen();
        Toast.makeText(this,"赛果已记录；冻结预测未修改",Toast.LENGTH_SHORT).show();
    }

    private boolean hitNet(String predicted,int actual) {
        if (predicted.contains("3+")) return actual>=3;
        if (predicted.contains("净胜0")) return actual==0;
        if (predicted.contains("净胜1")) return actual==1;
        if (predicted.contains("净胜2")) return actual==2;
        return false;
    }

    private String mark(boolean yes){return yes?"✓":"×";}

    private void refreshHistory() {
        String h=prefs.getString("history","");
        historyView.setText(h.isEmpty()?"暂无记录。":h);
    }

    private EditText numberBox(String hint) {
        EditText e=new EditText(this);
        e.setHint(hint+"进球");
        e.setGravity(Gravity.CENTER);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    private LinearLayout card(int color) {
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14),dp(13),dp(14),dp(13));
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(16));
        g.setStroke(dp(1),Color.rgb(225,229,232));
        l.setBackground(g);
        return l;
    }

    private Button button(String s) {
        Button b=new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);
        p.setMargins(dp(2),0,dp(2),0);
        return p;
    }

    private TextView text(String s,int sp,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }

    private void gap(int d) {
        View v=new View(this);
        root.addView(v,new LinearLayout.LayoutParams(1,dp(d)));
    }

    private int dp(float x) {
        return (int)(x*getResources().getDisplayMetrics().density+0.5f);
    }
}
