package com.abe618.dayanfootball;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private EditText matchInput, seedInput;
    private Spinner modeSpinner;
    private TextView resultView, statusView;
    private DayanEngine.Cast current;
    private String currentMode = "标准";

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs=getSharedPreferences("dayan_football",MODE_PRIVATE);

        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(14),dp(16),dp(24));
        scroll.addView(root);

        TextView title=text("大衍足球·盲测",25,true);
        root.addView(title);
        TextView sub=text("49策大衍十八变｜事前冻结｜赛后复盘｜标准/镜像双映射",14,false);
        root.addView(sub);

        TextView warn=text("实验性传统筮法模型，不代表科学预测能力，也不构成投注建议。",12,false);
        warn.setPadding(0,dp(4),0,dp(12));
        root.addView(warn);

        matchInput=new EditText(this);
        matchInput.setHint("比赛名称（可不填，如：主队 vs 客队）");
        matchInput.setSingleLine(true);
        root.addView(matchInput, full());

        seedInput=new EditText(this);
        seedInput.setHint("随机种子（留空=自动生成，可填数字复盘同一盘）");
        seedInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        root.addView(seedInput, full());

        modeSpinner=new Spinner(this);
        String[] modes={"自动校准","标准映射（上卦/世=主）","镜像诊断（反向）"};
        modeSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));
        root.addView(modeSpinner, full());

        Button castBtn=button("大衍起盘并冻结");
        castBtn.setOnClickListener(v->castAndFreeze());
        root.addView(castBtn, full());

        Button scoreBtn=button("录入最近一场真实赛果");
        scoreBtn.setOnClickListener(v->enterScore());
        root.addView(scoreBtn, full());

        Button historyBtn=button("历史复盘与命中统计");
        historyBtn.setOnClickListener(v->showHistory());
        root.addView(historyBtn, full());

        Button rulesBtn=button("查看模型规则");
        rulesBtn.setOnClickListener(v->showRules());
        root.addView(rulesBtn, full());

        statusView=text(statsSummary(),13,true);
        statusView.setPadding(0,dp(12),0,dp(8));
        root.addView(statusView);

        resultView=text("点击“大衍起盘并冻结”开始。",15,false);
        resultView.setTextIsSelectable(true);
        resultView.setPadding(dp(12),dp(12),dp(12),dp(12));
        root.addView(resultView, full());

        setContentView(scroll);
    }

    private void castAndFreeze() {
        long seed=parseSeed();
        String mode=chooseMode();
        DayanEngine.MappingMode engineMode="镜像".equals(mode)?DayanEngine.MappingMode.MIRROR:DayanEngine.MappingMode.STANDARD;
        current=DayanEngine.cast(seed,engineMode);
        currentMode=mode;

        DayanEngine.Prediction std=current.standard, mir=current.mirror;
        StringBuilder out=new StringBuilder();
        out.append("【当前模式】").append(mode).append("\n");
        out.append("标准映射：").append(std.ranking).append("｜").append(std.scoreMain).append("\n");
        out.append("镜像诊断：").append(mir.ranking).append("｜").append(mir.scoreMain).append("\n\n");
        out.append(DayanEngine.describe(current,"镜像".equals(mode)));
        resultView.setText(out.toString());

        saveFrozen(current,mode);
        seedInput.setText(String.valueOf(seed));
        toast("本场已冻结并写入历史");
    }

    private long parseSeed() {
        String s=seedInput.getText().toString().trim();
        if (!s.isEmpty()) {
            try { return Long.parseLong(s); } catch(Exception ignored) {}
        }
        long v=new SecureRandom().nextLong();
        if (v==Long.MIN_VALUE) v=1;
        return Math.abs(v);
    }

    private String chooseMode() {
        int pos=modeSpinner.getSelectedItemPosition();
        if (pos==1) return "标准";
        if (pos==2) return "镜像";

        Stats s=computeStats();
        if (s.resolved>=10 && s.mirrorRate() >= s.standardRate()+0.15) return "镜像";
        return "标准";
    }

    private void saveFrozen(DayanEngine.Cast c,String mode) {
        JSONArray arr=loadHistory();
        JSONObject o=new JSONObject();
        try {
            o.put("id",System.currentTimeMillis());
            o.put("time",new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(new Date()));
            o.put("match",matchInput.getText().toString().trim());
            o.put("seed",c.seed);
            o.put("freeze",c.freezeCode);
            o.put("lines",lineString(c.lines));
            o.put("base",c.baseHex);
            o.put("changed",c.changedHex);
            o.put("mode",mode);
            o.put("stdOutcome",c.standard.outcome);
            o.put("mirrorOutcome",c.mirror.outcome);
            DayanEngine.Prediction p="镜像".equals(mode)?c.mirror:c.standard;
            o.put("selectedOutcome",p.outcome);
            o.put("halfFull",p.halfFull);
            o.put("goals",p.goalsMain);
            o.put("goalsAlt",p.goalsAlt);
            o.put("ou",p.overUnder);
            o.put("oddEven",p.oddEven);
            o.put("btts",p.btts);
            o.put("scoreMain",p.scoreMain);
            o.put("scoreAlt1",p.scoreAlt1);
            o.put("scoreAlt2",p.scoreAlt2);
            o.put("netGoal",p.netGoal);
            arr.put(o);
        } catch(JSONException ignored) {}
        saveHistory(arr);
    }

    private void enterScore() {
        JSONArray arr=loadHistory();
        int idx=-1;
        for (int i=arr.length()-1;i>=0;i--) {
            JSONObject o=arr.optJSONObject(i);
            if (o!=null && !o.has("actualHome")) { idx=i; break; }
        }
        if (idx<0) { toast("没有等待录入赛果的冻结记录"); return; }

        JSONObject target=arr.optJSONObject(idx);
        LinearLayout box=new LinearLayout(this);
        box.setPadding(dp(20),0,dp(20),0);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView label=text((target.optString("match").isEmpty()?"最近冻结场次":target.optString("match"))+"\n"+target.optString("freeze"),13,false);
        box.addView(label);
        EditText home=new EditText(this); home.setHint("主队进球"); home.setInputType(2); box.addView(home);
        EditText away=new EditText(this); away.setHint("客队进球"); away.setInputType(2); box.addView(away);
        EditText halfHome=new EditText(this); halfHome.setHint("半场主队进球（可留空）"); halfHome.setInputType(2); box.addView(halfHome);
        EditText halfAway=new EditText(this); halfAway.setHint("半场客队进球（可留空）"); halfAway.setInputType(2); box.addView(halfAway);

        final int targetIdx=idx;
        new AlertDialog.Builder(this)
            .setTitle("录入真实赛果")
            .setView(box)
            .setPositiveButton("保存",(d,w)->{
                try {
                    int h=Integer.parseInt(home.getText().toString().trim());
                    int a=Integer.parseInt(away.getText().toString().trim());
                    JSONObject o=arr.getJSONObject(targetIdx);
                    o.put("actualHome",h); o.put("actualAway",a);
                    if (!halfHome.getText().toString().trim().isEmpty() && !halfAway.getText().toString().trim().isEmpty()) {
                        o.put("halfHome",Integer.parseInt(halfHome.getText().toString().trim()));
                        o.put("halfAway",Integer.parseInt(halfAway.getText().toString().trim()));
                    }
                    saveHistory(arr);
                    statusView.setText(statsSummary());
                    showOneReview(o);
                } catch(Exception e) { toast("比分格式不正确"); }
            })
            .setNegativeButton("取消",null).show();
    }

    private void showOneReview(JSONObject o) {
        int h=o.optInt("actualHome"), a=o.optInt("actualAway");
        String actual=actualOutcome(h,a);
        int total=h+a;
        String actualOU=total>=3?"大2.5":"小2.5";
        String actualOE=total%2==0?"双":"单";
        String score=h+":"+a;
        StringBuilder s=new StringBuilder();
        s.append("真实赛果：").append(score).append(" ").append(actual).append("\n");
        s.append("标准方向：").append(o.optString("stdOutcome")).append(eq(o.optString("stdOutcome"),actual)).append("\n");
        s.append("镜像方向：").append(o.optString("mirrorOutcome")).append(eq(o.optString("mirrorOutcome"),actual)).append("\n");
        s.append("正式方向：").append(o.optString("selectedOutcome")).append(eq(o.optString("selectedOutcome"),actual)).append("\n");
        s.append("总进球：预测 ").append(o.optInt("goals")).append(" / 次 ").append(o.optInt("goalsAlt")).append("；实际 ").append(total).append("\n");
        s.append("大小：").append(o.optString("ou")).append(eq(o.optString("ou"),actualOU)).append("\n");
        s.append("单双：").append(o.optString("oddEven")).append(eq(o.optString("oddEven"),actualOE)).append("\n");
        s.append("比分池：").append(o.optString("scoreMain")).append("、").append(o.optString("scoreAlt1")).append("、").append(o.optString("scoreAlt2"));
        if (score.equals(o.optString("scoreMain")) || score.equals(o.optString("scoreAlt1")) || score.equals(o.optString("scoreAlt2"))) s.append(" ✓");
        else s.append(" ✗");
        new AlertDialog.Builder(this).setTitle("本场复盘").setMessage(s.toString()).setPositiveButton("确定",null).show();
    }

    private void showHistory() {
        JSONArray arr=loadHistory();
        Stats st=computeStats();
        StringBuilder sb=new StringBuilder();
        sb.append(statsSummary()).append("\n\n");
        int start=Math.max(0,arr.length()-12);
        for (int i=arr.length()-1;i>=start;i--) {
            JSONObject o=arr.optJSONObject(i); if (o==null) continue;
            sb.append("#").append(i+1).append(" ");
            String m=o.optString("match"); if (!m.isEmpty()) sb.append(m).append(" ");
            sb.append(o.optString("lines")).append(" ").append(o.optString("base"));
            if (!o.optString("base").equals(o.optString("changed"))) sb.append("→").append(o.optString("changed"));
            sb.append("\n预测：").append(o.optString("selectedOutcome")).append(" ")
              .append(o.optString("scoreMain")).append(" ").append(o.optString("ou"));
            if (o.has("actualHome")) sb.append("｜实 ").append(o.optInt("actualHome")).append(":").append(o.optInt("actualAway"));
            else sb.append("｜待揭晓");
            sb.append("\n\n");
        }
        new AlertDialog.Builder(this).setTitle("历史复盘").setMessage(sb.toString()).setPositiveButton("关闭",null).show();
    }

    private void showRules() {
        String msg=
            "1. 大衍起卦：50策虚一，用49策；每爻三变，六爻十八变。\n\n"+
            "2. 每一变执行：分二→挂一→揲四→归奇；最终余24/28/32/36，对应6/7/8/9。6、9为动爻。\n\n"+
            "3. 主客层：标准映射固定“上卦+世”为主、“下卦+应”为客；同时计算镜像诊断，防止主客映射出现系统性反向。\n\n"+
            "4. 胜平负：本卦上下五行、变卦上下五行、世应纳甲五行、动爻位置综合评分。\n\n"+
            "5. 半全场：前三爻看前程，后三爻看后程；本变关系发生反向时提高翻转型半全场。\n\n"+
            "6. 进球数：不再使用“0动=小球”的旧规则。综合动爻数、生克冲突强度、本变反转、老阳密度计算。\n\n"+
            "7. 复盘：每次起盘自动冻结；录入真实比分后分别统计标准映射、镜像映射和正式预测命中率。自动模式只有样本≥10且镜像领先≥15个百分点时才切换镜像。";
        new AlertDialog.Builder(this).setTitle("大衍足球 V1.0 规则").setMessage(msg).setPositiveButton("知道了",null).show();
    }

    private String statsSummary() {
        Stats s=computeStats();
        if (s.resolved==0) return "复盘样本：0场｜等待录入真实赛果";
        return String.format(Locale.getDefault(),
            "复盘样本：%d场｜正式方向 %.1f%%｜标准 %.1f%%｜镜像 %.1f%%｜大小 %.1f%%｜单双 %.1f%%｜比分池 %.1f%%",
            s.resolved,100*s.selectedRate(),100*s.standardRate(),100*s.mirrorRate(),100*s.ouRate(),100*s.oeRate(),100*s.scoreRate());
    }

    private Stats computeStats() {
        JSONArray arr=loadHistory(); Stats s=new Stats();
        for (int i=0;i<arr.length();i++) {
            JSONObject o=arr.optJSONObject(i);
            if (o==null || !o.has("actualHome")) continue;
            s.resolved++;
            int h=o.optInt("actualHome"),a=o.optInt("actualAway"),total=h+a;
            String actual=actualOutcome(h,a), score=h+":"+a;
            if (actual.equals(o.optString("stdOutcome"))) s.stdHit++;
            if (actual.equals(o.optString("mirrorOutcome"))) s.mirHit++;
            if (actual.equals(o.optString("selectedOutcome"))) s.selHit++;
            if ((total>=3?"大2.5":"小2.5").equals(o.optString("ou"))) s.ouHit++;
            if ((total%2==0?"双":"单").equals(o.optString("oddEven"))) s.oeHit++;
            if (score.equals(o.optString("scoreMain")) || score.equals(o.optString("scoreAlt1")) || score.equals(o.optString("scoreAlt2"))) s.scoreHit++;
        }
        return s;
    }

    private static final class Stats {
        int resolved,stdHit,mirHit,selHit,ouHit,oeHit,scoreHit;
        double standardRate(){return resolved==0?0:(double)stdHit/resolved;}
        double mirrorRate(){return resolved==0?0:(double)mirHit/resolved;}
        double selectedRate(){return resolved==0?0:(double)selHit/resolved;}
        double ouRate(){return resolved==0?0:(double)ouHit/resolved;}
        double oeRate(){return resolved==0?0:(double)oeHit/resolved;}
        double scoreRate(){return resolved==0?0:(double)scoreHit/resolved;}
    }

    private JSONArray loadHistory() {
        try { return new JSONArray(prefs.getString("history","[]")); }
        catch(Exception e) { return new JSONArray(); }
    }

    private void saveHistory(JSONArray a){ prefs.edit().putString("history",a.toString()).apply(); }

    private static String actualOutcome(int h,int a){ return h>a?"主胜":(h<a?"客胜":"平"); }
    private static String eq(String a,String b){ return a.equals(b)?" ✓":" ✗"; }
    private static String lineString(int[] lines){StringBuilder s=new StringBuilder();for(int v:lines)s.append(v);return s.toString();}

    private TextView text(String s,int sp,boolean bold) {
        TextView v=new TextView(this); v.setText(s); v.setTextSize(sp);
        if (bold) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setLineSpacing(0,1.15f); return v;
    }
    private Button button(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    private LinearLayout.LayoutParams full(){ return new LinearLayout.LayoutParams(-1,-2); }
    private int dp(int n){ return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
}
