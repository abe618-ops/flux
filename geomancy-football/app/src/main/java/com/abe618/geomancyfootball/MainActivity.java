package com.abe618.geomancyfootball;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int INK = Color.rgb(30,36,40);
    private static final int MUTED = Color.rgb(105,113,118);
    private static final int BLUE = Color.rgb(25,105,190);
    private static final int SOFT = Color.rgb(245,247,248);
    private static final int GOLD = Color.rgb(159,111,20);

    private LinearLayout root;
    private ChartView chart;
    private GeomancyEngine.State state;
    private TextView direction, details, tendency, votes, seedView, derivation;
    private EditText seedInput;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        recastRandom();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root, new ScrollView.LayoutParams(-1,-2));
        setContentView(scroll);

        TextView title = text("地占足球实验室", 27, INK, true);
        root.addView(title);
        TextView sub = text("传统地占排盘 · 足球实验映射 · 固定规则可复盘", 13, MUTED, false);
        root.addView(sub);
        gap(12);

        // Prediction is intentionally above the chart and visually prominent.
        LinearLayout resultCard = card();
        TextView rtitle = text("本局预测", 16, GOLD, true);
        resultCard.addView(rtitle);
        direction = text("—", 32, INK, true);
        direction.setGravity(Gravity.CENTER_HORIZONTAL);
        resultCard.addView(direction);
        tendency = text("", 14, BLUE, true);
        tendency.setGravity(Gravity.CENTER_HORIZONTAL);
        resultCard.addView(tendency);
        details = text("", 17, INK, true);
        details.setLineSpacing(dp(4),1f);
        details.setGravity(Gravity.CENTER_HORIZONTAL);
        resultCard.addView(details);
        votes = text("", 12, MUTED, false);
        votes.setGravity(Gravity.CENTER_HORIZONTAL);
        votes.setPadding(0,dp(8),0,0);
        resultCard.addView(votes);
        root.addView(resultCard);
        gap(12);

        LinearLayout controls = card();
        TextView ctitle = text("重新排盘", 22, INK, true); ctitle.setGravity(Gravity.CENTER_HORIZONTAL); controls.addView(ctitle);
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        group.setGravity(Gravity.CENTER);
        RadioButton shield = radio("盾形阵列");
        RadioButton traditional = radio("传统十二宫");
        RadioButton crowley = radio("克劳利");
        shield.setId(View.generateViewId()); traditional.setId(View.generateViewId()); crowley.setId(View.generateViewId());
        group.addView(shield); group.addView(traditional); group.addView(crowley); shield.setChecked(true);
        group.setOnCheckedChangeListener((g,id)->{
            if(id==shield.getId()) chart.setMode(ChartView.SHIELD);
            else if(id==traditional.getId()) chart.setMode(ChartView.TRADITIONAL);
            else chart.setMode(ChartView.CROWLEY);
        });
        controls.addView(group);

        seedInput = new EditText(this);
        seedInput.setHint("可选：输入数字/文字作为固定种子");
        seedInput.setSingleLine(true);
        seedInput.setInputType(InputType.TYPE_CLASS_TEXT);
        controls.addView(seedInput, new LinearLayout.LayoutParams(-1,dp(52)));

        LinearLayout br = new LinearLayout(this); br.setOrientation(LinearLayout.HORIZONTAL);
        Button random = button("随机起盘"); random.setOnClickListener(v->recastRandom());
        Button fixed = button("按输入起盘"); fixed.setOnClickListener(v->recastFixed());
        br.addView(random, weight()); br.addView(fixed, weight()); controls.addView(br);
        seedView = text("",12,MUTED,false); seedView.setPadding(0,dp(7),0,0); controls.addView(seedView);
        root.addView(controls);
        gap(12);

        chart = new ChartView(this);
        root.addView(chart, new LinearLayout.LayoutParams(-1,dp(500)));
        gap(12);

        LinearLayout core = card();
        core.addView(text("排盘推导", 19, INK, true));
        derivation = text("", 12, MUTED, false);
        derivation.setLineSpacing(dp(3),1f);
        core.addView(derivation);
        root.addView(core);
        gap(10);

        LinearLayout note = card();
        note.addView(text("冻结规则说明",17,INK,true));
        note.addView(text("四母独立起局；四女、四侄、左右见证、法官、调停者全部按传统奇偶法推导。足球实验映射固定采用1/7宫主客轴、5/11宫攻门轴、10/4宫结局轴、左右见证、法官与调停者。输出顺序固定为：胜平负 → 半全场 → 总进球 → 大小2.5 → 单双 → 双方进球 → 比分。比分先定方向与总球，再分配主客进球，不直接由单一象硬猜。百分比仅为合参倾向度，不是真实概率。",13,MUTED,false));
        root.addView(note);
    }

    private void recastRandom(){
        state = GeomancyEngine.castRandom();
        showState();
    }

    private void recastFixed(){
        String s = seedInput.getText().toString().trim();
        if(s.isEmpty()) { Toast.makeText(this,"请输入一个数字或文字种子",Toast.LENGTH_SHORT).show(); return; }
        long seed;
        try { seed = Long.parseLong(s); } catch(Exception e) { seed = GeomancyEngine.seedFromText(s); }
        state = GeomancyEngine.cast(seed);
        showState();
    }

    private void showState(){
        if(chart!=null) chart.setState(state);
        GeomancyEngine.Result r=state.result;
        direction.setText(r.direction + "   " + r.primaryScore);
        tendency.setText("合参倾向度 " + r.tendency + "/100（非概率）");
        details.setText("半全场  " + r.halfFull + "\n总进球  " + r.totalGoals + "    " + r.overUnder + "    " + r.oddEven + "\n" + r.btts + "\n比分候选  " + r.scoreCandidates);
        votes.setText(r.votes);
        seedView.setText("本局 Seed："+state.seed+"  ·  可复制用于复盘复现");
        StringBuilder b=new StringBuilder();
        for(int i=0;i<4;i++) b.append("母").append(i+1).append(" ").append(GeomancyEngine.figureCode(state.figures[i])).append(i==3?"\n":"   ");
        for(int i=4;i<8;i++) b.append("女").append(i-3).append(" ").append(GeomancyEngine.figureCode(state.figures[i])).append(i==7?"\n":"   ");
        for(int i=8;i<12;i++) b.append("侄").append(i-7).append(" ").append(GeomancyEngine.figureCode(state.figures[i])).append(i==11?"\n":"   ");
        b.append("右见证 ").append(GeomancyEngine.figureCode(state.figures[12])).append("   左见证 ").append(GeomancyEngine.figureCode(state.figures[13])).append("\n");
        b.append("法官 ").append(GeomancyEngine.figureCode(state.figures[14])).append("   调停者 ").append(GeomancyEngine.figureCode(state.figures[15]));
        derivation.setText(b.toString());
    }

    private RadioButton radio(String s){
        RadioButton r=new RadioButton(this); r.setText(s); r.setTextSize(15); r.setTextColor(INK); r.setPadding(dp(4),0,dp(4),0); return r;
    }

    private LinearLayout card(){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(14),dp(13),dp(14),dp(13));
        GradientDrawable g=new GradientDrawable(); g.setColor(SOFT); g.setCornerRadius(dp(16)); g.setStroke(dp(1),Color.rgb(226,230,232)); l.setBackground(g); return l;
    }

    private Button button(String s){
        Button b=new Button(this); b.setText(s); b.setTextSize(15); b.setAllCaps(false); return b;
    }
    private LinearLayout.LayoutParams weight(){ return new LinearLayout.LayoutParams(0,dp(50),1); }
    private TextView text(String s,int sp,int color,boolean bold){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t;
    }
    private void gap(int d){ View v=new View(this); root.addView(v,new LinearLayout.LayoutParams(1,dp(d))); }
    private int dp(float x){ return (int)(x*getResources().getDisplayMetrics().density+0.5f); }
}
