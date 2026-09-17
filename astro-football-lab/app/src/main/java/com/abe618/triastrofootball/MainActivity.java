package com.abe618.triastrofootball;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int INK = Color.rgb(28, 34, 39);
    private static final int MUTED = Color.rgb(104, 113, 120);
    private static final int CARD = Color.rgb(247, 248, 250);
    private static final int ACCENT = Color.rgb(28, 102, 85);
    private static final int WARN = Color.rgb(154, 91, 28);

    private SharedPreferences prefs;
    private LinearLayout content;
    private EditText homeInput, awayInput, seedInput;
    private PredictionEngine.Result current;
    private String currentHome = "A", currentAway = "B";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("tri_astro_football", MODE_PRIVATE);
        predictionPage();
    }

    private void shell(String title) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = row();
        nav.setPadding(dp(5), dp(4), dp(5), dp(7));
        nav.addView(navButton("预测", this::predictionPage), weight());
        nav.addView(navButton("复盘统计", this::statsPage), weight());
        nav.addView(navButton("模型说明", this::aboutPage), weight());
        root.addView(nav);

        setContentView(root);
        TextView t = text(title, 27, INK, true);
        content.addView(t);
    }

    private void predictionPage() {
        shell("三术足球合参");
        content.addView(text("地占术 × 西洋占星 × 印度占星 / KP × 数学概率层", 14, MUTED, false));
        gap(12);

        LinearLayout input = card();
        input.addView(text("匿名或真实对阵均可", 18, INK, true));
        input.addView(text("不读取赔率、排名或真实开球时间；随机种子一旦生成即可复现。", 13, MUTED, false));
        homeInput = edit("主队 / A", false);
        awayInput = edit("客队 / B", false);
        seedInput = edit("随机种子（留空自动生成）", false);
        input.addView(homeInput);
        input.addView(awayInput);
        input.addView(seedInput);
        Button run = button("开始三术合参");
        run.setOnClickListener(v -> runPrediction());
        input.addView(run);
        content.addView(input);

        if (current != null) renderResult();
    }

    private void runPrediction() {
        currentHome = clean(homeInput.getText().toString(), "A");
        currentAway = clean(awayInput.getText().toString(), "B");
        String seed = seedInput.getText().toString().trim();
        current = PredictionEngine.run(seed);
        seedInput.setText(current.seed);
        predictionPage();
    }

    private void renderResult() {
        gap(12);
        LinearLayout finalCard = card();
        finalCard.addView(text(currentHome + "  vs  " + currentAway, 21, INK, true));
        finalCard.addView(text("Seed  " + current.seed, 12, MUTED, false));
        finalCard.addView(text("M1：" + current.m1Pattern + "    ｜    M2：" + current.m2Direction + "    K=" + current.k, 15, INK, true));
        gapIn(finalCard, 6);
        finalCard.addView(text(current.summary, 17, ACCENT, true));
        finalCard.addView(text("胜平负概率：主 " + PredictionEngine.pct(current.pHome)
                + "  平 " + PredictionEngine.pct(current.pDraw)
                + "  客 " + PredictionEngine.pct(current.pAway), 15, INK, false));
        finalCard.addView(text("总进球 μ=" + fmt(current.mu) + " ｜ 波动=" + fmt(current.volatility)
                + " ｜ 大2.5 " + PredictionEngine.pct(current.over25)
                + " ｜ 单数 " + PredictionEngine.pct(current.odd)
                + " ｜ BTTS " + PredictionEngine.pct(current.btts), 14, MUTED, false));
        finalCard.addView(text("半场：" + current.half + "　半全场：" + current.halfFull
                + "　方向置信度：" + current.directionConfidence, 14, MUTED, false));

        StringBuilder sb = new StringBuilder("候选比分：");
        for (int i=0;i<current.topScores.size();i++) {
            PredictionEngine.Score s=current.topScores.get(i);
            if(i>0) sb.append("  /  ");
            sb.append(s.label()).append(" ").append(PredictionEngine.pct(s.probability));
        }
        finalCard.addView(text(sb.toString(), 16, INK, true));

        if (!current.mirrorScores.isEmpty()) {
            StringBuilder mirror = new StringBuilder("镜像保护：");
            for (int i=0;i<current.mirrorScores.size();i++) {
                if(i>0)mirror.append(" / ");
                mirror.append(current.mirrorScores.get(i).label());
            }
            finalCard.addView(text(mirror.toString(), 14, WARN, true));
        }
        content.addView(finalCard);

        methodCard(current.geomancy);
        methodCard(current.western);
        methodCard(current.indian);
        outcomeCard();

        LinearLayout actions = row();
        Button export = button("分享本次结果");
        export.setOnClickListener(v -> shareCurrent());
        Button rerun = button("换随机种子");
        rerun.setOnClickListener(v -> { seedInput.setText(""); runPrediction(); });
        actions.addView(export, weight());
        actions.addView(rerun, weight());
        content.addView(actions);
    }

    private void methodCard(PredictionEngine.MethodResult m) {
        gap(9);
        LinearLayout c = card();
        c.addView(text(m.name + " → " + m.direction, 18, INK, true));
        c.addView(text("主 " + PredictionEngine.pct(m.pHome) + " ｜ 平 " + PredictionEngine.pct(m.pDraw)
                + " ｜ 客 " + PredictionEngine.pct(m.pAway)
                + " ｜ 进球中心 " + fmt(m.goalMean), 14, ACCENT, true));
        c.addView(text(m.randomMark, 13, MUTED, false));
        c.addView(text(m.detail, 13, INK, false));
        content.addView(c);
    }

    private void outcomeCard() {
        gap(10);
        LinearLayout c = card();
        c.addView(text("赛后复盘", 19, INK, true));
        c.addView(text("比赛结束后录入真实比分，自动统计胜平负、大小、单双、BTTS、首选比分和候选比分覆盖率。", 13, MUTED, false));
        LinearLayout r = row();
        EditText hs = scoreEdit("主");
        EditText as = scoreEdit("客");
        r.addView(hs, weight());
        TextView colon = text(" : ", 22, INK, true);
        colon.setGravity(Gravity.CENTER);
        r.addView(colon);
        r.addView(as, weight());
        c.addView(r);
        Button save = button("保存真实赛果并复盘");
        save.setOnClickListener(v -> {
            try {
                int h=Integer.parseInt(hs.getText().toString().trim());
                int a=Integer.parseInt(as.getText().toString().trim());
                saveOutcome(h,a);
            } catch(Exception e) { toast("请输入有效的主客进球数"); }
        });
        c.addView(save);
        content.addView(c);
    }

    private void saveOutcome(int h, int a) {
        if (current == null) return;
        int actualDir = h>a ? PredictionEngine.HOME : h<a ? PredictionEngine.AWAY : PredictionEngine.DRAW;
        String actual = actualDir==PredictionEngine.HOME ? "主" : actualDir==PredictionEngine.AWAY ? "客" : "平";
        boolean hit1 = current.winner.equals(actual);
        boolean actualOver = h+a>=3;
        boolean hitOU = current.ou.startsWith(actualOver?"大":"小");
        boolean actualOdd = ((h+a)&1)==1;
        boolean hitOdd = current.parity.equals(actualOdd?"单":"双");
        boolean actualBtts = h>0 && a>0;
        boolean hitBtts = current.bttsText.equals(actualBtts?"YES":"NO");
        boolean hitTop = !current.topScores.isEmpty() && current.topScores.get(0).home==h && current.topScores.get(0).away==a;
        boolean hitPool = false;
        for(PredictionEngine.Score s:current.topScores) if(s.home==h&&s.away==a) hitPool=true;
        for(PredictionEngine.Score s:current.mirrorScores) if(s.home==h&&s.away==a) hitPool=true;

        JSONArray arr=history();
        JSONObject o=new JSONObject();
        try {
            o.put("time", System.currentTimeMillis());
            o.put("home", currentHome); o.put("away", currentAway); o.put("seed", current.seed);
            o.put("pred", current.winner); o.put("ou", current.ou); o.put("parity", current.parity);
            o.put("btts", current.bttsText); o.put("score", current.topScores.isEmpty()?"":current.topScores.get(0).label());
            o.put("actual", h+":"+a);
            o.put("hit1",hit1);o.put("hitOU",hitOU);o.put("hitOdd",hitOdd);o.put("hitBtts",hitBtts);
            o.put("hitTop",hitTop);o.put("hitPool",hitPool);
            arr.put(o);
            prefs.edit().putString("history",arr.toString()).apply();
        } catch(JSONException ignored) {}
        new AlertDialog.Builder(this)
                .setTitle("复盘结果  " + h + ":" + a)
                .setMessage("胜平负 " + mark(hit1) + "\n大小球 " + mark(hitOU) + "\n单双 " + mark(hitOdd)
                        + "\nBTTS " + mark(hitBtts) + "\n首选比分 " + mark(hitTop) + "\n候选池 " + mark(hitPool))
                .setPositiveButton("查看统计", (d,w)->statsPage())
                .setNegativeButton("继续", null).show();
    }

    private void statsPage() {
        shell("复盘统计");
        JSONArray a=history();
        int n=a.length(), h1=0,hOu=0,hOdd=0,hB=0,hTop=0,hPool=0;
        for(int i=0;i<n;i++){
            try {
                JSONObject o=a.getJSONObject(i);
                if(o.optBoolean("hit1"))h1++;
                if(o.optBoolean("hitOU"))hOu++;
                if(o.optBoolean("hitOdd"))hOdd++;
                if(o.optBoolean("hitBtts"))hB++;
                if(o.optBoolean("hitTop"))hTop++;
                if(o.optBoolean("hitPool"))hPool++;
            } catch(JSONException ignored){}
        }

        LinearLayout s=card();
        s.addView(text("已复盘 " + n + " 场", 20, INK, true));
        s.addView(statLine("胜平负",h1,n));
        s.addView(statLine("大小球 2.5",hOu,n));
        s.addView(statLine("进球单双",hOdd,n));
        s.addView(statLine("双方进球 BTTS",hB,n));
        s.addView(statLine("首选比分精确",hTop,n));
        s.addView(statLine("候选比分覆盖",hPool,n));
        content.addView(s);

        gap(10);
        content.addView(text("最近记录", 19, INK, true));
        for(int i=n-1;i>=0 && i>=n-20;i--){
            try {
                JSONObject o=a.getJSONObject(i);
                LinearLayout c=card();
                String date=new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(o.optLong("time")));
                c.addView(text(o.optString("home")+" vs "+o.optString("away")+"  →  "+o.optString("actual"),16,INK,true));
                c.addView(text(date+"　预测 "+o.optString("pred")+"　首选 "+o.optString("score"),12,MUTED,false));
                c.addView(text("胜平负 "+mark(o.optBoolean("hit1"))+"　大小 "+mark(o.optBoolean("hitOU"))
                        +"　单双 "+mark(o.optBoolean("hitOdd"))+"　BTTS "+mark(o.optBoolean("hitBtts"))
                        +"　比分池 "+mark(o.optBoolean("hitPool")),13,INK,false));
                content.addView(c); gap(6);
            } catch(JSONException ignored){}
        }

        if(n>0){
            Button clear=button("清空复盘记录");
            clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("确认清空？")
                    .setMessage("这会删除本机保存的全部复盘数据。")
                    .setPositiveButton("清空",(d,w)->{prefs.edit().remove("history").apply();statsPage();})
                    .setNegativeButton("取消",null).show());
            content.addView(clear);
        }
    }

    private void aboutPage() {
        shell("模型说明 · V1.0");
        LinearLayout c=card();
        c.addView(text("三层结构", 20, INK, true));
        c.addView(text("① 地占术：四母→四女→四甥→左右见证→Judge，提取主客、进球能量和波动。\n\n"
                +"② 西洋随机时刻盘：1900—2099 随机UTC时刻，以轻量均值黄经和传统宫主强弱构造实验盘。\n\n"
                +"③ 印度随机时刻 / KP：Lahiri近似恒星黄道、1/7宫、6/11竞技轴、月宿和随机KP 1—249。\n\n"
                +"④ 数学层：M1初盘、M2自动归一、K指数衰减、三术相关性折扣、方向熵、镜像比分、负二项高比分尾部、BTTS/大小/单双独立统计。", 15, INK, false));
        content.addView(c); gap(10);

        LinearLayout c2=card();
        c2.addView(text("当前冻结规则", 20, INK, true));
        c2.addView(text("• M2权重随 K 按指数衰减，深层归一不直接覆盖初盘。\n"
                +"• 三术同向不视为3个完全独立证据，使用相关性折扣。\n"
                +"• 主客概率接近且最高概率不足时，提高平局权重。\n"
                +"• 总进球使用均值 μ + 波动度，并用负二项分布保留4—7球尾部。\n"
                +"• 双单、大小、BTTS从比分概率池分别计算，不互相机械推导。\n"
                +"• 方向不稳时自动显示镜像比分。\n"
                +"• 真实赛果只用于复盘统计，不回改已经冻结的历史预测。", 14, INK, false));
        content.addView(c2); gap(10);

        LinearLayout note=card();
        note.addView(text("实验性质",18,WARN,true));
        note.addView(text("占星与地占没有可靠科学证据证明能够预测足球赛果。本软件把它们作为可复现的随机特征体系，用于盲测、统计和模型研究，不构成投注建议。西洋/印度天体计算为轻量近似算法，不等同于专业星历软件。",13,MUTED,false));
        content.addView(note);
    }

    private void shareCurrent() {
        if(current==null)return;
        StringBuilder b=new StringBuilder();
        b.append("三术足球合参\n").append(currentHome).append(" vs ").append(currentAway).append("\n")
                .append("Seed: ").append(current.seed).append("\nM1: ").append(current.m1Pattern)
                .append(" | M2: ").append(current.m2Direction).append(" K=").append(current.k).append("\n")
                .append(current.summary).append("\n比分：");
        for(PredictionEngine.Score s:current.topScores)b.append(s.label()).append(" ");
        Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,b.toString());
        startActivity(Intent.createChooser(i,"分享预测"));
    }

    private JSONArray history() {
        try { return new JSONArray(prefs.getString("history","[]")); }
        catch(JSONException e){ return new JSONArray(); }
    }

    private TextView statLine(String name,int hit,int n){
        return text(name+"　"+hit+"/"+n+"　"+(n==0?"—":String.format(Locale.CHINA,"%.1f%%",100.0*hit/n)),16,INK,false);
    }

    private String mark(boolean x){ return x?"✅":"❌"; }
    private String clean(String s,String fallback){
        s=s.trim(); return s.isEmpty()||s.equals("主队 / A")||s.equals("客队 / B")?fallback:s;
    }
    private String fmt(double x){return String.format(Locale.CHINA,"%.2f",x);}

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(13),dp(14),dp(13));
        GradientDrawable g=new GradientDrawable();g.setColor(CARD);g.setCornerRadius(dp(15));g.setStroke(dp(1),Color.rgb(229,232,235));c.setBackground(g);
        return c;
    }
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private void gap(int n){View v=new View(this);content.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    private void gapIn(LinearLayout c,int n){View v=new View(this);c.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    private TextView text(String s,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setLineSpacing(0,1.15f);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(0,dp(4),0,dp(4));return t;
    }
    private EditText edit(String hint, boolean number){
        EditText e=new EditText(this);e.setHint(hint);e.setTextSize(16);e.setSingleLine(true);e.setPadding(dp(10),dp(9),dp(10),dp(9));
        if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER);return e;
    }
    private EditText scoreEdit(String hint){
        EditText e=edit(hint,true);e.setGravity(Gravity.CENTER);return e;
    }
    private Button button(String s){
        Button b=new Button(this);b.setText(s);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(Color.WHITE);
        GradientDrawable g=new GradientDrawable();g.setColor(ACCENT);g.setCornerRadius(dp(12));b.setBackground(g);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.setMargins(0,dp(8),0,0);b.setLayoutParams(p);return b;
    }
    private Button navButton(String s,Runnable r){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setOnClickListener(v->r.run());return b;
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
}
