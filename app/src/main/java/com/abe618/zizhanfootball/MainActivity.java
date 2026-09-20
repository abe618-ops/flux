package com.abe618.zizhanfootball;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity {
    private static final int TEAL = Color.rgb(1, 135, 134);
    private static final int WHITE = Color.WHITE;
    private final Map<String,String> lingQiTitles = new HashMap<>();
    private final Random random = new SecureRandom();

    private EditText wenInput;
    private EditText wenResult;
    private EditText lingResult;
    private final TextView[][] lingCells = new TextView[3][4];
    private TextView footballResult;

    private WenResult lastWen;
    private LingResult lastLing;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadLingQi();
        buildScreen();
    }

    private int dp(float n) {
        return (int)(n * getResources().getDisplayMetrics().density + .5f);
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(WHITE);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(buildWenWangPanel(), new LinearLayout.LayoutParams(-1, dp(535)));

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(232,232,232));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, dp(1));
        dl.setMargins(dp(10), 0, dp(10), 0);
        root.addView(divider, dl);

        root.addView(buildLingQiPanel(), new LinearLayout.LayoutParams(-1, dp(500)));

        TextView fusionTitle = makeText("足球合参", 18, true, TEAL);
        fusionTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ftl = new LinearLayout.LayoutParams(-1, -2);
        ftl.setMargins(dp(12), dp(8), dp(12), dp(4));
        root.addView(fusionTitle, ftl);

        footballResult = makeText("完成上方“起课”和下方“查询”后，这里显示两术合参实验结果。", 15, false, Color.rgb(35,35,35));
        footballResult.setGravity(Gravity.CENTER_VERTICAL);
        footballResult.setPadding(dp(14), dp(10), dp(14), dp(18));
        root.addView(footballResult, new LinearLayout.LayoutParams(-1, -2));

        setContentView(scroll);
    }

    private RelativeLayout buildWenWangPanel() {
        RelativeLayout panel = new RelativeLayout(this);
        panel.setBackgroundColor(WHITE);
        panel.setPadding(0, dp(2), 0, 0);

        Button back = cloneButton("返回", 14, true);
        back.setId(View.generateViewId());
        back.setOnClickListener(v -> finish());
        RelativeLayout.LayoutParams bp = new RelativeLayout.LayoutParams(-2, -2);
        bp.addRule(RelativeLayout.ALIGN_PARENT_START);
        bp.setMargins(dp(5), dp(5), 0, 0);
        panel.addView(back, bp);

        TextView title = makeText("文王八卦", 24, true, TEAL);
        title.setId(View.generateViewId());
        title.setGravity(Gravity.CENTER);
        title.setBackgroundColor(WHITE);
        RelativeLayout.LayoutParams tp = new RelativeLayout.LayoutParams(-2, -2);
        tp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        tp.setMargins(0, dp(12), 0, 0);
        panel.addView(title, tp);

        Button change = cloneButton("爻变", 14, true);
        change.setId(View.generateViewId());
        change.setOnClickListener(v -> showChangedHex());
        RelativeLayout.LayoutParams cp = new RelativeLayout.LayoutParams(-2, -2);
        cp.addRule(RelativeLayout.ALIGN_PARENT_END);
        cp.setMargins(0, dp(5), dp(5), 0);
        panel.addView(change, cp);

        CoinInstructionView coinView = new CoinInstructionView();
        coinView.setId(View.generateViewId());
        RelativeLayout.LayoutParams ip = new RelativeLayout.LayoutParams(-1, dp(235));
        ip.addRule(RelativeLayout.BELOW, title.getId());
        ip.setMargins(0, dp(5), 0, 0);
        panel.addView(coinView, ip);

        Button randomBtn = cloneButton("随机", 14, true);
        randomBtn.setId(View.generateViewId());
        RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(-2, -2);
        rp.addRule(RelativeLayout.BELOW, coinView.getId());
        rp.setMargins(dp(15), 0, 0, 0);
        panel.addView(randomBtn, rp);

        wenInput = new EditText(this);
        wenInput.setId(View.generateViewId());
        wenInput.setText("202202");
        wenInput.setTextSize(18);
        wenInput.setSingleLine(true);
        wenInput.setGravity(Gravity.CENTER);
        wenInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        wenInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        RelativeLayout.LayoutParams ep = new RelativeLayout.LayoutParams(dp(122), -2);
        ep.addRule(RelativeLayout.BELOW, coinView.getId());
        ep.addRule(RelativeLayout.END_OF, randomBtn.getId());
        panel.addView(wenInput, ep);

        Button castBtn = cloneButton("起课", 14, true);
        castBtn.setId(View.generateViewId());
        RelativeLayout.LayoutParams qp = new RelativeLayout.LayoutParams(-2, -2);
        qp.addRule(RelativeLayout.BELOW, coinView.getId());
        qp.addRule(RelativeLayout.END_OF, wenInput.getId());
        qp.setMargins(dp(5), 0, 0, 0);
        panel.addView(castBtn, qp);

        wenResult = cloneResultBox();
        wenResult.setId(View.generateViewId());
        wenResult.setHint("起课结果");
        RelativeLayout.LayoutParams wrp = new RelativeLayout.LayoutParams(-1, dp(185));
        wrp.addRule(RelativeLayout.BELOW, randomBtn.getId());
        wrp.setMargins(dp(10), 0, dp(10), 0);
        panel.addView(wenResult, wrp);

        randomBtn.setOnClickListener(v -> {
            StringBuilder six = new StringBuilder();
            for (int i=0; i<6; i++) {
                int faces = 0;
                for (int c=0; c<3; c++) if (random.nextBoolean()) faces++;
                six.append(faces);
            }
            wenInput.setText(six.toString());
            castWen();
        });
        castBtn.setOnClickListener(v -> castWen());

        return panel;
    }

    private RelativeLayout buildLingQiPanel() {
        RelativeLayout panel = new RelativeLayout(this);
        panel.setBackgroundColor(WHITE);

        Button back = cloneButton("返回", 14, true);
        back.setId(View.generateViewId());
        back.setOnClickListener(v -> finish());
        RelativeLayout.LayoutParams bp = new RelativeLayout.LayoutParams(-2, -2);
        bp.addRule(RelativeLayout.ALIGN_PARENT_START);
        bp.setMargins(dp(5), dp(5), 0, 0);
        panel.addView(back, bp);

        TextView title = makeText("灵棋经", 24, true, TEAL);
        title.setId(View.generateViewId());
        title.setGravity(Gravity.CENTER);
        title.setBackgroundColor(WHITE);
        RelativeLayout.LayoutParams tp = new RelativeLayout.LayoutParams(-2, -2);
        tp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        tp.setMargins(0, dp(12), 0, 0);
        panel.addView(title, tp);

        Button query = cloneButton("查询", 14, true);
        query.setId(View.generateViewId());
        query.setOnClickListener(v -> queryLing());
        RelativeLayout.LayoutParams qp = new RelativeLayout.LayoutParams(-2, -2);
        qp.addRule(RelativeLayout.ALIGN_PARENT_END);
        qp.setMargins(0, dp(5), dp(5), 0);
        panel.addView(query, qp);

        int previousRowFirst = title.getId();
        for (int row=0; row<3; row++) {
            int previousCell = 0;
            for (int col=0; col<4; col++) {
                TextView cell = makeText("空", 24, true, TEAL);
                cell.setId(View.generateViewId());
                cell.setGravity(Gravity.CENTER);
                cell.setBackgroundColor(WHITE);
                final int rr=row;
                cell.setOnClickListener(v -> {
                    String label = rr==0 ? "上" : rr==1 ? "中" : "下";
                    TextView tv=(TextView)v;
                    tv.setText("空".contentEquals(tv.getText()) ? label : "空");
                });
                lingCells[row][col]=cell;

                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(dp(52), dp(48));
                if (col==0) {
                    lp.addRule(RelativeLayout.BELOW, previousRowFirst);
                    lp.setMargins(dp(30), dp(15), 0, 0);
                } else {
                    lp.addRule(RelativeLayout.END_OF, previousCell);
                    if (row==0) lp.addRule(RelativeLayout.BELOW, title.getId());
                    else lp.addRule(RelativeLayout.ALIGN_TOP, lingCells[row][0].getId());
                    lp.setMargins(dp(30), row==0?dp(15):0, 0, 0);
                }
                panel.addView(cell, lp);
                previousCell = cell.getId();
            }
            previousRowFirst = lingCells[row][0].getId();
        }

        Button randomBtn = cloneButton("随机", 24, true);
        randomBtn.setId(View.generateViewId());
        RelativeLayout.LayoutParams rbp = new RelativeLayout.LayoutParams(-2, -2);
        rbp.addRule(RelativeLayout.BELOW, lingCells[2][0].getId());
        rbp.setMargins(dp(15), 0, 0, 0);
        panel.addView(randomBtn, rbp);

        lingResult = cloneResultBox();
        lingResult.setId(View.generateViewId());
        lingResult.setHint("灵棋经查询结果");
        RelativeLayout.LayoutParams lrp = new RelativeLayout.LayoutParams(-1, dp(205));
        lrp.addRule(RelativeLayout.BELOW, randomBtn.getId());
        lrp.setMargins(dp(10), 0, dp(10), 0);
        panel.addView(lingResult, lrp);

        randomBtn.setOnClickListener(v -> {
            for (int row=0; row<3; row++) {
                String label = row==0 ? "上" : row==1 ? "中" : "下";
                for (int col=0; col<4; col++) {
                    lingCells[row][col].setText(random.nextBoolean() ? label : "空");
                }
            }
            queryLing();
        });

        return panel;
    }

    private Button cloneButton(String text, int sp, boolean bold) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(sp);
        b.setTextColor(TEAL);
        b.setAllCaps(false);
        if (bold) b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackgroundTintList(ColorStateList.valueOf(WHITE));
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        return b;
    }

    private TextView makeText(String text, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private EditText cloneResultBox() {
        EditText e = new EditText(this);
        e.setTextSize(15);
        e.setTextColor(Color.rgb(25,25,25));
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setBackgroundColor(WHITE);
        e.setFocusable(false);
        e.setLongClickable(true);
        e.setTextIsSelectable(true);
        e.setVerticalScrollBarEnabled(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setPadding(dp(8), dp(6), dp(8), dp(6));
        return e;
    }

    private void castWen() {
        String raw = wenInput.getText().toString().trim();
        if (raw.length() < 6) {
            Toast.makeText(this, "请输入6位数字，每位只能是0、1、2、3", Toast.LENGTH_SHORT).show();
            return;
        }
        raw = raw.substring(0,6);
        int[] sums = new int[6];
        int[] primary = new int[6];
        int[] changed = new int[6];
        int moving=0, lowYang=0, highYang=0, lowMove=0, highMove=0;

        for (int i=0; i<6; i++) {
            char ch=raw.charAt(i);
            if (ch<'0' || ch>'3') {
                Toast.makeText(this, "每一位必须是0～3", Toast.LENGTH_SHORT).show();
                return;
            }
            int faceCount=ch-'0';
            int sum=9-faceCount; // 原图规则：字面=2，背面=3
            sums[i]=sum;
            primary[i]=(sum==7 || sum==9)?1:0;
            changed[i]=(sum==6)?1:(sum==9?0:primary[i]);
            if (primary[i]==1) { if(i<3) lowYang++; else highYang++; }
            if (sum==6 || sum==9) {
                moving++;
                if(i<3) lowMove++; else highMove++;
            }
        }

        String primaryName=hexName(primary);
        String changedName=hexName(changed);
        int host=lowYang*2+lowMove;
        int away=highYang*2+highMove;
        String vote=host>away?"主":host<away?"客":"平";

        StringBuilder lineText=new StringBuilder();
        for(int i=5;i>=0;i--) {
            lineText.append(chineseLine(i)).append("　")
                    .append(raw.charAt(i)).append(" → ")
                    .append(sums[i]).append("　")
                    .append(lineSymbol(sums[i])).append("\n");
        }

        String text="文王八卦\n"+
                "取数："+raw+"（初爻→上爻）\n"+
                "本卦："+primaryName+"\n"+
                "变卦："+changedName+"\n"+
                "动爻："+moving+"\n\n"+
                lineText+
                "\n足球实验映射：下卦主方 "+host+"｜上卦客方 "+away+" → "+vote+"向";
        wenResult.setText(text);
        lastWen=new WenResult(raw,sums,primary,changed,moving,primaryName,changedName,host,away,vote);
        updateFootballFusion();
    }

    private void showChangedHex() {
        if (lastWen==null) {
            castWen();
            if(lastWen==null) return;
        }
        StringBuilder moves=new StringBuilder();
        for(int i=0;i<6;i++) {
            if(lastWen.sums[i]==6 || lastWen.sums[i]==9) {
                if(moves.length()>0) moves.append("、");
                moves.append(chineseLine(i));
            }
        }
        if(moves.length()==0) moves.append("无动爻");
        Toast.makeText(this, "爻变："+lastWen.primaryName+" → "+lastWen.changedName+"；"+moves, Toast.LENGTH_LONG).show();
    }

    private void queryLing() {
        int[] count=new int[3];
        for(int row=0;row<3;row++) {
            for(int col=0;col<4;col++) {
                if(!"空".contentEquals(lingCells[row][col].getText())) count[row]++;
            }
        }
        String key=""+count[0]+count[1]+count[2];
        String title=lingQiTitles.get(key);
        if(title==null) title="未找到对应卦辞";
        int delta=count[0]-count[2];
        String vote=delta>0?"主":delta<0?"客":"平";
        lingResult.setText("灵棋经\n"+
                "上棋："+count[0]+"　中棋："+count[1]+"　下棋："+count[2]+"\n"+
                "键值："+key+"\n"+
                title.replace("\\n","\n")+"\n\n"+
                "足球实验映射：上−下="+delta+" → "+vote+"向");
        lastLing=new LingResult(count[0],count[1],count[2],key,title,vote);
        updateFootballFusion();
    }

    private void updateFootballFusion() {
        if(lastWen==null || lastLing==null) {
            footballResult.setText("完成上方“起课”和下方“查询”后，这里显示两术合参实验结果。");
            return;
        }
        int score=(lastWen.hostPower-lastWen.awayPower)+(lastLing.up-lastLing.down);
        String outcome=score>1?"主胜向":score<-1?"客胜向":"平局/不败区间";
        int totalSignal=lastWen.moving+lastLing.up+lastLing.mid+lastLing.down;
        String size=totalSignal>=8?"大2.5象":"小2.5象";
        int parity=lastWen.moving+lastLing.up+lastLing.mid+lastLing.down;
        String odd=(parity&1)==1?"单":"双";
        footballResult.setText("两术合参："+outcome+"　｜　"+size+"　｜　进球"+odd+
                "\n文王："+lastWen.vote+"向　灵棋："+lastLing.vote+"向"+
                "\n仅作传统术数盲测记录，不代表统计概率。");
    }

    private String chineseLine(int i) {
        return new String[]{"初爻","二爻","三爻","四爻","五爻","上爻"}[i];
    }

    private String lineSymbol(int sum) {
        if(sum==6) return "⚋ × 老阴";
        if(sum==7) return "⚊　少阳";
        if(sum==8) return "⚋　少阴";
        return "⚊ ○ 老阳";
    }

    private void loadLingQi() {
        try(BufferedReader br=new BufferedReader(new InputStreamReader(
                getAssets().open("lingqi_titles.tsv"), StandardCharsets.UTF_8))) {
            String s;
            while((s=br.readLine())!=null) {
                int p=s.indexOf('\t');
                if(p>0) lingQiTitles.put(s.substring(0,p), s.substring(p+1));
            }
        } catch(Exception ignored) {}
    }

    private String hexName(int[] bits) {
        String lower=""+bits[0]+bits[1]+bits[2];
        String upper=""+bits[3]+bits[4]+bits[5];
        String lo=tri(lower), up=tri(upper);
        String n=HEX.get(up+"/"+lo);
        return n==null ? up+"上"+lo+"下" : n;
    }

    private String tri(String s) {
        switch(s) {
            case "111": return "乾";
            case "110": return "兑";
            case "101": return "离";
            case "100": return "震";
            case "011": return "巽";
            case "010": return "坎";
            case "001": return "艮";
            default: return "坤";
        }
    }

    private static final Map<String,String> HEX=new HashMap<>();
    static {
        putHex("乾","乾","乾为天"); putHex("坤","坤","坤为地"); putHex("坎","震","水雷屯"); putHex("艮","坎","山水蒙");
        putHex("坎","乾","水天需"); putHex("乾","坎","天水讼"); putHex("坤","坎","地水师"); putHex("坎","坤","水地比");
        putHex("巽","乾","风天小畜"); putHex("乾","兑","天泽履"); putHex("坤","乾","地天泰"); putHex("乾","坤","天地否");
        putHex("乾","离","天火同人"); putHex("离","乾","火天大有"); putHex("坤","艮","地山谦"); putHex("震","坤","雷地豫");
        putHex("兑","震","泽雷随"); putHex("艮","巽","山风蛊"); putHex("坤","兑","地泽临"); putHex("巽","坤","风地观");
        putHex("离","震","火雷噬嗑"); putHex("艮","离","山火贲"); putHex("艮","坤","山地剥"); putHex("坤","震","地雷复");
        putHex("乾","震","天雷无妄"); putHex("艮","乾","山天大畜"); putHex("艮","震","山雷颐"); putHex("兑","巽","泽风大过");
        putHex("坎","坎","坎为水"); putHex("离","离","离为火"); putHex("兑","艮","泽山咸"); putHex("震","巽","雷风恒");
        putHex("乾","艮","天山遁"); putHex("震","乾","雷天大壮"); putHex("离","坤","火地晋"); putHex("坤","离","地火明夷");
        putHex("巽","离","风火家人"); putHex("离","兑","火泽睽"); putHex("坎","艮","水山蹇"); putHex("震","坎","雷水解");
        putHex("艮","兑","山泽损"); putHex("巽","震","风雷益"); putHex("兑","乾","泽天夬"); putHex("乾","巽","天风姤");
        putHex("兑","坤","泽地萃"); putHex("坤","巽","地风升"); putHex("兑","坎","泽水困"); putHex("坎","巽","水风井");
        putHex("兑","离","泽火革"); putHex("离","巽","火风鼎"); putHex("震","震","震为雷"); putHex("艮","艮","艮为山");
        putHex("巽","艮","风山渐"); putHex("震","兑","雷泽归妹"); putHex("震","离","雷火丰"); putHex("离","艮","火山旅");
        putHex("巽","巽","巽为风"); putHex("兑","兑","兑为泽"); putHex("巽","坎","风水涣"); putHex("坎","兑","水泽节");
        putHex("巽","兑","风泽中孚"); putHex("震","艮","雷山小过"); putHex("坎","离","水火既济"); putHex("离","坎","火水未济");
    }
    private static void putHex(String up,String lo,String name){ HEX.put(up+"/"+lo,name); }

    private class CoinInstructionView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        CoinInstructionView() {
            super(MainActivity.this);
            setBackgroundColor(WHITE);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w=getWidth();
            float cy=dp(77);
            float r=dp(43);
            float[] xs={w*.25f,w*.50f,w*.75f};

            for(int i=0;i<3;i++) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.rgb(205,171,91));
                c.drawCircle(xs[i],cy,r,p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(3));
                p.setColor(Color.rgb(112,82,37));
                c.drawCircle(xs[i],cy,r,p);
                c.drawCircle(xs[i],cy,r-dp(8),p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.rgb(98,70,34));
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD));
                p.setTextSize(dp(23));
                c.drawText(i==1?"字":"币",xs[i],cy+dp(8),p);
            }

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT);
            p.setColor(Color.rgb(45,45,45));
            p.setTextSize(dp(16));
            c.drawText("将三枚硬币摇摇　看字面的各个数", w/2f, dp(160), p);
            c.drawText("如上为2，重复6次依次填入下方", w/2f, dp(188), p);
            p.setTextSize(dp(12));
            p.setColor(Color.GRAY);
            c.drawText("字面计2，另一面计3；输入每次出现“字面”的数量0～3", w/2f, dp(216), p);
        }
    }

    static class WenResult {
        String raw,primaryName,changedName,vote;
        int[] sums,primary,changed;
        int moving,hostPower,awayPower;
        WenResult(String r,int[]s,int[]p,int[]c,int m,String pn,String cn,int h,int a,String v){
            raw=r;sums=s;primary=p;changed=c;moving=m;primaryName=pn;changedName=cn;hostPower=h;awayPower=a;vote=v;
        }
    }
    static class LingResult {
        int up,mid,down;
        String key,title,vote;
        LingResult(int u,int m,int d,String k,String t,String v){up=u;mid=m;down=d;key=k;title=t;vote=v;}
    }
}
