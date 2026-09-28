package com.abe618.sanshifootball;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private EditText homeInput, awayInput;
    private LinearLayout resultRoot, layerButtons, chartHolder;
    private PredictionEngine.PredictionResult current;
    private int selectedLayer = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(36));
        root.setBackgroundColor(Color.rgb(247,247,244));
        scroll.addView(root);

        TextView title = text("四层随机足球预测", 25, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, full());

        TextView sub = text("V2.0｜时家 · 刻家 · 分盘 · 秒盘｜不输入时间", 12, false);
        sub.setGravity(Gravity.CENTER);
        sub.setTextColor(Color.DKGRAY);
        sub.setPadding(0,0,0,dp(12));
        root.addView(sub, full());

        LinearLayout teams = new LinearLayout(this);
        teams.setOrientation(LinearLayout.HORIZONTAL);
        homeInput = input("主队（可选）");
        awayInput = input("客队（可选）");
        LinearLayout.LayoutParams half1=new LinearLayout.LayoutParams(0,dp(52),1f);
        half1.setMargins(0,0,dp(5),0);
        LinearLayout.LayoutParams half2=new LinearLayout.LayoutParams(0,dp(52),1f);
        half2.setMargins(dp(5),0,0,0);
        teams.addView(homeInput,half1);
        teams.addView(awayInput,half2);
        root.addView(teams,full());

        Button next = new Button(this);
        next.setText("下一场 · 安全随机起盘");
        next.setAllCaps(false);
        next.setTextSize(18);
        next.setTypeface(Typeface.DEFAULT_BOLD);
        next.setMinHeight(dp(58));
        next.setOnClickListener(v -> generate());
        LinearLayout.LayoutParams nextLp=full();
        nextLp.setMargins(0,dp(10),0,dp(8));
        root.addView(next,nextLp);

        TextView randomNote=text("随机源：Android SecureRandom 生成256位主种子；四个盘由SHA-256独立派生。每次仅点击“下一场”才重新抽取。",11,false);
        randomNote.setTextColor(Color.GRAY);
        randomNote.setPadding(dp(4),0,dp(4),dp(10));
        root.addView(randomNote,full());

        resultRoot = new LinearLayout(this);
        resultRoot.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultRoot,full());

        setContentView(scroll);
        generate();
    }

    private void generate() {
        current = PredictionEngine.next(homeInput.getText().toString(), awayInput.getText().toString());
        selectedLayer = 0;
        render();
    }

    private void render() {
        resultRoot.removeAllViews();
        if(current==null)return;

        LinearLayout top=card();
        TextView id=text("封盘ID  "+current.drawId,12,true);
        id.setTextColor(Color.DKGRAY);
        top.addView(id,full());

        TextView matchup=text(current.homeName+"  vs  "+current.awayName,20,true);
        matchup.setGravity(Gravity.CENTER);
        matchup.setPadding(0,dp(5),0,dp(8));
        top.addView(matchup,full());

        GridLayout summary=new GridLayout(this);
        summary.setColumnCount(2);
        addSummary(summary,"胜平负",current.outcome);
        addSummary(summary,"半全场",current.halfFull);
        addSummary(summary,"进球数",current.totalGoals+"球  "+current.goalRange);
        addSummary(summary,"比分",current.score+"  /  "+current.altScore);
        addSummary(summary,"大小球",current.overUnder);
        addSummary(summary,"单双",current.parity);
        top.addView(summary,full());

        TextView resonance=text("四盘共振："+current.resonance+"/4",13,true);
        resonance.setGravity(Gravity.CENTER);
        resonance.setPadding(0,dp(8),0,dp(2));
        top.addView(resonance,full());

        resultRoot.addView(top,marginBottom(dp(8)));

        TextView layerTitle=text("四盘独立结果",13,true);
        layerTitle.setTextColor(Color.DKGRAY);
        layerTitle.setPadding(dp(2),dp(4),0,dp(4));
        resultRoot.addView(layerTitle,full());

        for(PredictionEngine.LayerResult l:current.layers){
            LinearLayout c=card();
            TextView name=text(l.name+"　随机码 "+l.randomCode,14,true);
            c.addView(name,full());
            TextView line=text(
                    "胜平负："+l.outcome+"　 半全场："+l.halfFull+"\n"
                    +"进球："+l.totalGoals+"　 比分："+l.score+" / "+l.altScore+"\n"
                    +"大小："+l.overUnder+"　 单双："+l.parity
                    +"　 主/客力："+l.homePower+"/"+l.awayPower,
                    13,false);
            line.setPadding(0,dp(5),0,0);
            c.addView(line,full());
            resultRoot.addView(c,marginBottom(dp(6)));
        }

        layerButtons=new LinearLayout(this);
        layerButtons.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels={"时家盘","刻家盘","分盘","秒盘"};
        for(int i=0;i<4;i++){
            final int index=i;
            Button b=new Button(this);
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setTextSize(12);
            b.setOnClickListener(v->{selectedLayer=index;renderChart();});
            layerButtons.addView(b,new LinearLayout.LayoutParams(0,dp(46),1f));
        }
        resultRoot.addView(layerButtons,marginBottom(dp(5)));

        chartHolder=new LinearLayout(this);
        chartHolder.setOrientation(LinearLayout.VERTICAL);
        resultRoot.addView(chartHolder,full());
        renderChart();

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button copy=new Button(this);
        copy.setText("复制封盘结果");
        copy.setAllCaps(false);
        copy.setOnClickListener(v->copy());
        Button seed=new Button(this);
        seed.setText("复制完整随机种子");
        seed.setAllCaps(false);
        seed.setOnClickListener(v->copySeed());
        actions.addView(copy,new LinearLayout.LayoutParams(0,dp(50),1f));
        actions.addView(seed,new LinearLayout.LayoutParams(0,dp(50),1f));
        LinearLayout.LayoutParams ap=full(); ap.setMargins(0,dp(8),0,0);
        resultRoot.addView(actions,ap);
    }

    private void renderChart() {
        if(chartHolder==null||current==null)return;
        chartHolder.removeAllViews();
        PredictionEngine.LayerResult l=current.layers.get(selectedLayer);
        TraditionalEngine.QimenResult q=l.chart;

        LinearLayout box=card();
        TextView h=text(l.name+"｜"+q.dun+q.bureau+"局｜"+q.yuan
                +"\n主柱："+q.activeGanZhi+"　值符："+q.zhiFu+"　值使："+q.zhiShi,13,true);
        h.setPadding(0,0,0,dp(6));
        box.addView(h,full());

        GridLayout grid=new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(3);
        int[] order={4,9,2,3,5,7,8,1,6};
        for(int i=0;i<order.length;i++){
            TraditionalEngine.QimenPalace p=q.palaces[order[i]];
            TextView cell=text(p.display(),12,false);
            cell.setGravity(Gravity.CENTER);
            cell.setTypeface(Typeface.create("serif",Typeface.NORMAL));
            cell.setPadding(dp(2),dp(4),dp(2),dp(4));
            cell.setBackground(cellBg(i==4));
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(
                    GridLayout.spec(i/3,1,1f),GridLayout.spec(i%3,1,1f));
            lp.width=0; lp.height=dp(118);
            lp.setMargins(dp(2),dp(2),dp(2),dp(2));
            grid.addView(cell,lp);
        }
        box.addView(grid,full());

        TextView note=text("本层判断："+l.outcome+"｜"+l.halfFull+"｜"+l.totalGoals+"球｜"
                +l.score+"/"+l.altScore+"｜"+l.overUnder+"｜"+l.parity+"\n"+q.note,11,false);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0,dp(6),0,0);
        box.addView(note,full());
        chartHolder.addView(box,full());
    }

    private void addSummary(GridLayout g,String k,String v){
        LinearLayout cell=new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(8),dp(7),dp(8),dp(7));
        TextView key=text(k,11,false);key.setTextColor(Color.GRAY);
        TextView val=text(v,17,true);val.setTextColor(Color.rgb(28,28,27));
        cell.addView(key);cell.addView(val);
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
        lp.width=0;
        lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1,1f);
        lp.setMargins(dp(2),dp(2),dp(2),dp(2));
        cell.setBackground(cellBg(false));
        g.addView(cell,lp);
    }

    private LinearLayout card(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(10),dp(10),dp(10),dp(10));
        l.setBackground(cardBg());
        return l;
    }

    private void copy(){
        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("四层随机足球预测",current.fullText()));
        Toast.makeText(this,"已复制封盘结果",Toast.LENGTH_SHORT).show();
    }

    private void copySeed(){
        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("随机种子",current.seedHex));
        Toast.makeText(this,"已复制256位种子",Toast.LENGTH_SHORT).show();
    }

    private EditText input(String hint){
        EditText e=new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(14);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setPadding(dp(10),0,dp(10),0);
        e.setBackgroundColor(Color.WHITE);
        return e;
    }

    private TextView text(String s,int size,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.rgb(30,30,29));
        if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0,1.12f);
        return t;
    }

    private android.graphics.drawable.GradientDrawable cardBg(){
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();
        g.setColor(Color.WHITE);
        g.setStroke(dp(1),Color.rgb(218,218,212));
        g.setCornerRadius(dp(8));
        return g;
    }

    private android.graphics.drawable.GradientDrawable cellBg(boolean center){
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();
        g.setColor(center?Color.rgb(241,238,226):Color.rgb(252,252,250));
        g.setStroke(dp(1),Color.rgb(194,194,188));
        g.setCornerRadius(dp(4));
        return g;
    }

    private LinearLayout.LayoutParams full(){
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams marginBottom(int px){
        LinearLayout.LayoutParams lp=full();lp.setMargins(0,0,0,px);return lp;
    }

    private int dp(int n){
        return Math.round(n*getResources().getDisplayMetrics().density);
    }
}
