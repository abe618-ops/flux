package com.abe618.liuyaopick3;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends Activity {
    private Spinner game;
    private EditText date;
    private EditText time;
    private EditText seed;
    private EditText actual;
    private TextView output;
    private LiuyaoEngine.Analysis last;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs=getSharedPreferences("liuyao_lab",MODE_PRIVATE);

        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(14),dp(14),dp(28));
        scroll.addView(root);

        TextView title=new TextView(this);
        title.setText("六爻三位数研究｜排列3 · 福彩3D");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title,matchWrap());

        TextView sub=new TextView(this);
        sub.setText("妻财 / 应爻 / 世爻 / 旺动爻 + 五行旺衰数 + 空亡0 + 生克冲合墓化 + 卦名机锋 + 和值结构");
        sub.setTextSize(13);
        sub.setPadding(0,dp(4),0,dp(12));
        root.addView(sub,matchWrap());

        game=new Spinner(this);
        ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"福彩3D","体彩排列3"});
        game.setAdapter(ad);
        root.addView(label("彩种"));
        root.addView(game,matchWrap());

        LinearLayout row1=row();
        date=input("日期 YYYY-MM-DD",LocalDate.now().toString());
        time=input("时间 HH:mm",LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        row1.addView(date,weight());
        row1.addView(time,weight());
        root.addView(row1,matchWrap());

        seed=input("随机数/文字 Seed（可空；留空自动生成，可复现时请保存Seed）","");
        root.addView(seed,matchWrap());

        LinearLayout buttons=row();
        Button btTime=button("时间盘");
        Button btRandom=button("随机数盘");
        Button btLive=button("当日活时盘");
        buttons.addView(btTime,weight());
        buttons.addView(btRandom,weight());
        buttons.addView(btLive,weight());
        root.addView(buttons,matchWrap());

        LinearLayout audit=row();
        Button freeze=button("冻结本次");
        Button restore=button("查看冻结");
        audit.addView(freeze,weight());
        audit.addView(restore,weight());
        root.addView(audit,matchWrap());

        actual=input("输入已开奖号复盘，例如 784","");
        actual.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(actual,matchWrap());
        Button review=button("复盘命中层级");
        root.addView(review,matchWrap());

        output=new TextView(this);
        output.setTextSize(14);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setPadding(0,dp(14),0,dp(24));
        output.setText("先选择一种起盘方式。\n\n研究流程建议：事前起盘 → 冻结 → 开奖后输入号码 → 复盘。");
        root.addView(output,matchWrap());

        btTime.setOnClickListener(v->run(LiuyaoEngine.Mode.TIME));
        btRandom.setOnClickListener(v->run(LiuyaoEngine.Mode.RANDOM));
        btLive.setOnClickListener(v->run(LiuyaoEngine.Mode.LIVE));
        freeze.setOnClickListener(v->freeze());
        restore.setOnClickListener(v->restore());
        review.setOnClickListener(v->review());

        setContentView(scroll);
    }

    private void run(LiuyaoEngine.Mode mode) {
        try {
            LocalDate d=LocalDate.parse(date.getText().toString().trim());
            LocalTime t=LocalTime.parse(time.getText().toString().trim());
            String g=String.valueOf(game.getSelectedItem());
            last=LiuyaoEngine.run(g,mode,d,t,seed.getText().toString());
            output.setText(last.text);
        } catch(Exception e) {
            output.setText("起盘失败："+e.getClass().getSimpleName()+"\n"+e.getMessage()
                    +"\n\n请检查日期格式 YYYY-MM-DD、时间格式 HH:mm。");
        }
    }

    private void freeze() {
        if(last==null){toast("请先起盘");return;}
        String stamp=LocalDateTime.now().toString();
        String sha=sha256(last.text+"|"+last.masterSeed+"|"+stamp);
        prefs.edit()
                .putString("frozen_text",last.text)
                .putString("frozen_seed",last.masterSeed)
                .putString("frozen_at",stamp)
                .putString("frozen_sha",sha)
                .apply();
        toast("已冻结：审计号 "+sha.substring(0,12));
    }

    private void restore() {
        String text=prefs.getString("frozen_text","");
        if(text.isEmpty()){toast("暂无冻结记录");return;}
        output.setText("【冻结记录】\n冻结时间："+prefs.getString("frozen_at","")
                +"\n审计号："+prefs.getString("frozen_sha","")
                +"\n完整Seed："+prefs.getString("frozen_seed","")
                +"\n\n"+text);
    }

    private void review() {
        if(last==null){toast("请先起盘；复盘仅针对当前盘");return;}
        String n=actual.getText().toString().trim();
        if(!n.matches("\\d{3}")){toast("请输入3位号码，如784；000也可以");return;}

        int[] ds={n.charAt(0)-'0',n.charAt(1)-'0',n.charAt(2)-'0'};
        String[] labels={"百","十","个"};
        StringBuilder b=new StringBuilder(last.text);
        b.append("\n\n==============================\n【开奖后复盘】\n实际：").append(n).append("\n");

        int posHit=0;
        for(int p=0;p<3;p++) {
            int[] top=topK(last.positionScores[p],5);
            boolean hit=contains(top,ds[p]);
            if(hit)posHit++;
            b.append(labels[p]).append("位 ").append(ds[p]).append(" ∈ Top5 ")
                    .append(Arrays.toString(top)).append("：").append(hit?"命中":"未中").append("\n");
        }

        int globalHit=0;
        for(int d:ds)if(contains(last.topDigits,d))globalHit++;
        int sum=ds[0]+ds[1]+ds[2];
        boolean sumHit=contains(last.sumTop,sum);

        b.append("定位胆 Top5：").append(posHit).append("/3\n")
                .append("全局共振 Top6 覆盖：").append(globalHit).append("/3\n")
                .append("直选 Top20：").append(last.directContains(n)?"命中":"未中").append("\n")
                .append("组选 Top12：").append(last.groupContains(n)?"命中":"未中").append("\n")
                .append("和值 ").append(sum).append(" ∈ ").append(Arrays.toString(last.sumTop))
                .append("：").append(sumHit?"命中":"未中").append("\n")
                .append("跨度：").append(Math.max(ds[0],Math.max(ds[1],ds[2]))
                        -Math.min(ds[0],Math.min(ds[1],ds[2]))).append("\n")
                .append("\n复盘原则：组选、定位胆、候选池、和值分别记账；不因开奖结果事后改权。");
        output.setText(b.toString());
    }

    private static int[] topK(double[] v,int k){
        Integer[] idx=new Integer[v.length];
        for(int i=0;i<v.length;i++)idx[i]=i;
        Arrays.sort(idx,(a,b)->Double.compare(v[b],v[a]));
        int[] out=new int[Math.min(k,idx.length)];
        for(int i=0;i<out.length;i++)out[i]=idx[i];
        return out;
    }

    private static boolean contains(int[] a,int x){for(int v:a)if(v==x)return true;return false;}

    private TextView label(String s){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(13);v.setPadding(0,dp(8),0,dp(3));return v;
    }

    private EditText input(String hint,String value){
        EditText e=new EditText(this);
        e.setHint(hint);e.setText(value);e.setTextSize(14);e.setSingleLine(true);
        e.setPadding(dp(8),dp(8),dp(8),dp(8));
        return e;
    }

    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f);}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    private static String sha256(String s){
        try{
            byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder x=new StringBuilder();
            for(byte v:b)x.append(String.format(Locale.ROOT,"%02x",v&255));
            return x.toString();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
