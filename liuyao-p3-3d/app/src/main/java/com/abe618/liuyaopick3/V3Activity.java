package com.abe618.liuyaopick3;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.Color;
import android.view.View;
import android.view.Gravity;
import android.text.InputType;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class V3Activity extends Activity {
    private final String[] POS={"百","十","个"};
    private final Spinner[] masks=new Spinner[3];
    private final EditText[] pools=new EditText[3];
    private EditText dateInput,timeInput,periodInput,seedInput,rawInput,actualInput;
    private TextView mainView,detailView,reviewView,statusView;
    private LiuyaoEngine.Analysis main,extra;
    private HexP3V3.Result fused;
    private SharedPreferences store;
    private String castLabel="正时盘";
    private final ZoneId BEIJING=ZoneId.of("Asia/Shanghai");
    private static final String STORE_KEY="v3_frozen_history";
    private static final String VERSION="3.0.0";

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        store=getSharedPreferences("liuyao_p3_v3_storage",MODE_PRIVATE);
        ScrollView sc=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setPadding(dp(12),dp(12),dp(12),dp(24));
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F7FA);
        sc.addView(root);
        TextView title=txt("六爻排列三 · 胆位合参 V3",23,true);
        root.addView(title);
        root.addView(txt("六爻纳甲主导 · 动变太玄 / 河图取数 · 双盘对照 · 冻结复盘",12,false));

        LinearLayout result=card(root);
        result.addView(txt("【综合预测】",15,true));
        mainView=txt("正在起盘…",19,true);
        mainView.setTextIsSelectable(true);
        mainView.setPadding(dp(6),dp(8),dp(6),dp(8));
        result.addView(mainView);

        LinearLayout basics=card(root);
        basics.addView(txt("日期与起卦设置（北京时间）",16,true));
        ZonedDateTime now=ZonedDateTime.now(BEIJING);
        LinearLayout dates=row();
        dateInput=edit("日期 yyyy-MM-dd",now.toLocalDate().toString());
        timeInput=edit("时间 HH:mm",now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        dates.addView(dateInput,weight());
        dates.addView(timeInput,weight());
        basics.addView(dates);
        periodInput=edit("期次/记录标识，如2026288",now.toLocalDate().toString());
        basics.addView(periodInput);
        seedInput=edit("随机 Seed（可空）；冻结后保存", "");
        basics.addView(seedInput);

        LinearLayout buttons1=row();
        Button clock=button("正时盘");
        Button random=button("随机盘");
        Button two=button("双盘合参");
        buttons1.addView(clock,weight());buttons1.addView(random,weight());buttons1.addView(two,weight());
        basics.addView(buttons1);
        LinearLayout buttons2=row();
        Button live=button("活时盘");
        Button next=button("随机下一盘");
        buttons2.addView(live,weight());
        buttons2.addView(next,weight());
        basics.addView(buttons2);

        LinearLayout controls=card(root);
        controls.addView(txt("百／十／个位分别限码（默认不限）",16,true));
        LinearLayout filters=row();
        for(int p=0;p<3;p++){
            Spinner sp=new Spinner(this);
            sp.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,HexP3V3.MASKS));
            masks[p]=sp;filters.addView(sp,weight());
        }
        controls.addView(filters);
        LinearLayout picks=row();
        String[] hints={"百位候选","十位候选","个位候选"};
        for(int p=0;p<3;p++){
            EditText e=edit(hints[p],"");
            e.setInputType(InputType.TYPE_CLASS_TEXT);
            pools[p]=e;picks.addView(e,weight());
        }
        controls.addView(picks);
        controls.addView(txt("留空按六爻自动取每位4码；也可填“6,7,8”自定义约束，不能追溯修改已冻结记录。",12,false));
        rawInput=new EditText(this);
        rawInput.setSingleLine(false);
        rawInput.setMinLines(2);rawInput.setMaxLines(6);
        rawInput.setTextSize(13);
        rawInput.setHint("可选：外部术数，每行：方法名:百位候选|十位候选|个位候选\n例：梅花:2,5,8|3,5,7|1,6,8");
        rawInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        controls.addView(rawInput);
        Button recompute=button("应用条件 · 重新筛选当前盘");
        controls.addView(recompute);
        Button copy=button("复制本次复式/定胆");
        controls.addView(copy);

        LinearLayout history=card(root);
        history.addView(txt("赛前冻结与开奖后复盘",16,true));
        LinearLayout freezing=row();
        Button freeze=button("冻结本期");
        Button show=button("查看历史");
        freezing.addView(freeze,weight());freezing.addView(show,weight());
        history.addView(freezing);
        actualInput=edit("实际开奖号码（三位，例如 067）","");
        actualInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        history.addView(actualInput);
        LinearLayout after=row();
        Button review=button("按冻结快照复盘");
        Button stats=button("累计命中统计");
        after.addView(review,weight());after.addView(stats,weight());
        history.addView(after);
        reviewView=txt("未开奖前请先冻结；开奖结果需自行输入。\n只有冻结快照参与复盘统计。",13,false);
        reviewView.setTextIsSelectable(true);
        history.addView(reviewView);

        LinearLayout details=card(root);
        Button open=button("展开/收起 六爻四课六亲、动变取数明细");
        details.addView(open);
        detailView=txt("",12,false);
        detailView.setVisibility(View.GONE);
        detailView.setTextIsSelectable(true);
        details.addView(detailView);
        statusView=txt("不联网，不自动读取开奖结果；预测仅供象数研究，彩票号码为随机事件。",12,false);
        root.addView(statusView);

        setContentView(sc);
        clock.setOnClickListener(v->cast(0,false));
        random.setOnClickListener(v->cast(1,false));
        two.setOnClickListener(v->cast(2,false));
        live.setOnClickListener(v->cast(3,false));
        next.setOnClickListener(v->{seedInput.setText("");cast(1,true);});
        recompute.setOnClickListener(v->refreshFusion());
        copy.setOnClickListener(v->copyCurrent());
        freeze.setOnClickListener(v->freeze());
        show.setOnClickListener(v->showHistory());
        review.setOnClickListener(v->review());
        stats.setOnClickListener(v->showStats());
        open.setOnClickListener(v->detailView.setVisibility(
                detailView.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        cast(0,false);
    }

    private void cast(int mode,boolean next){
        try{
            LocalDate d=LocalDate.parse(dateInput.getText().toString().trim());
            LocalTime t=LocalTime.parse(timeInput.getText().toString().trim());
            String typed=seedInput.getText().toString().trim();
            if((mode==1||mode==2||mode==3)&&typed.isEmpty()){
                byte[] buf=new byte[16];
                new SecureRandom().nextBytes(buf);
                StringBuilder x=new StringBuilder();
                for(byte b:buf)x.append(String.format(Locale.ROOT,"%02x",b&255));
                typed=x.toString();
                seedInput.setText(typed);
            }
            final String game="体彩排列3";
            if(mode==0){
                castLabel="正时盘";
                main=LiuyaoEngine.run(game,LiuyaoEngine.Mode.TIME,d,t,"");
                extra=null;
            }else if(mode==1){
                castLabel="随机盘";
                main=LiuyaoEngine.run(game,LiuyaoEngine.Mode.RANDOM,d,t,typed);
                extra=null;
            }else if(mode==2){
                castLabel="双盘合参";
                main=LiuyaoEngine.run(game,LiuyaoEngine.Mode.TIME,d,t,"");
                extra=LiuyaoEngine.run(game,LiuyaoEngine.Mode.RANDOM,d,t,typed);
            }else{
                castLabel="活时盘";
                main=LiuyaoEngine.run(game,LiuyaoEngine.Mode.LIVE,d,t,typed);
                extra=null;
            }
            refreshFusion();
            statusView.setText("起卦模式："+castLabel+
                    "，期次："+getPeriod()+"；当地展示日时按北京时间录入。"+
                    (next?" 已更换随机Seed。":""));
        }catch(Exception e){
            main=null;extra=null;fused=null;
            mainView.setText("起盘失败："+e.getMessage()+"\n请检查日期/时间格式。");
        }
    }
    private String getPeriod(){
        String p=periodInput.getText().toString().trim();
        return p.isEmpty()?dateInput.getText().toString().trim():p;
    }
    private void refreshFusion(){
        if(main==null){toast("请先起盘");return;}
        try{
            String[] filters=new String[3],picks=new String[3];
            for(int p=0;p<3;p++){
                filters[p]=String.valueOf(masks[p].getSelectedItem());
                picks[p]=pools[p].getText().toString();
            }
            fused=HexP3V3.calculate(main,extra,filters,picks,rawInput.getText().toString());
            mainView.setText("【"+castLabel+"】\n"+fused.summary);
            detailView.setText("起盘时刻："+main.effectiveTime+"；复现Seed："+seedInput.getText()
                    +"\n\n"+fused.detail+"\n\n【六爻主盘】\n"+main.text
                    +(extra==null?"":"\n\n【随机辅助盘】\n"+extra.text));
        }catch(Exception e){
            fused=null;mainView.setText("合参失败："+e.getMessage());
        }
    }
    private void copyCurrent(){
        if(fused==null){toast("请先起盘");return;}
        String data=HexP3V3.VERSION+" "+getPeriod()+" "+castLabel+"\n"+fused.summary
                +"\n完整直选复式 "+String.join("、",fused.allDirect)
                +"\n原始组选保护："+String.join("、",fused.rawGroups);
        ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("六爻排列三",data));
        toast("已复制："+fused.combinationCount+"组");
    }

    private JSONArray readRecords(){
        try{return new JSONArray(store.getString(STORE_KEY,"[]"));}
        catch(Exception e){return new JSONArray();}
    }
    private void persist(JSONArray data){store.edit().putString(STORE_KEY,data.toString()).apply();}
    private static String digest(String s){
        try{
            byte[] v=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();
            for(byte b:v)out.append(String.format(Locale.ROOT,"%02x",b&255));
            return out.toString();
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private static JSONArray jsonInts(int[] src){
        JSONArray a=new JSONArray();for(int d:src)a.put(d);return a;
    }
    private static JSONArray jsonStrings(List<String> src){
        JSONArray a=new JSONArray();for(String v:src)a.put(v);return a;
    }
    private static boolean included(JSONArray arr,int d){
        for(int i=0;i<arr.length();i++)if(arr.optInt(i,-1)==d)return true;
        return false;
    }
    private static boolean includedStr(JSONArray arr,String n){
        for(int i=0;i<arr.length();i++)if(n.equals(arr.optString(i)))return true;
        return false;
    }
    private static String sorted(String s){
        char[] a=s.toCharArray();Arrays.sort(a);return new String(a);
    }
    private int find(JSONArray data,String issue){
        for(int i=0;i<data.length();i++){
            JSONObject v=data.optJSONObject(i);
            if(v!=null&&issue.equals(v.optString("issue")))return i;
        }
        return -1;
    }
    private void freeze(){
        if(main==null||fused==null){toast("请先预测并合参");return;}
        String issue=getPeriod();
        JSONArray rows=readRecords();
        if(find(rows,issue)>=0){
            reviewView.setText("本期 "+issue+" 已存在冻结记录；不能事后覆盖。请查看历史或用新的期次记录。");
            return;
        }
        try{
            JSONObject record=new JSONObject();
            record.put("issue",issue).put("version",HexP3V3.VERSION)
                    .put("date",dateInput.getText().toString())
                    .put("mode",castLabel)
                    .put("at",ZonedDateTime.now(BEIJING).toString())
                    .put("seed",seedInput.getText().toString())
                    .put("original",main.text)
                    .put("compare",extra==null?"":extra.text)
                    .put("details",fused.detail)
                    .put("summary",fused.summary)
                    .put("rawSingle",fused.rawSingle)
                    .put("single",fused.single)
                    .put("three",jsonInts(fused.bolds))
                    .put("top12",jsonStrings(fused.direct))
                    .put("fullDirect",jsonStrings(fused.allDirect))
                    .put("rawGroups",jsonStrings(fused.rawGroups))
                    .put("votes",rawInput.getText().toString());
            JSONArray selected=new JSONArray(),rawSelected=new JSONArray();
            for(int p=0;p<3;p++){
                selected.put(jsonInts(fused.four[p]));
                rawSelected.put(jsonInts(fused.rawFour[p]));
            }
            record.put("four",selected).put("rawFour",rawSelected);
            String hash=digest(record.toString());
            record.put("hash",hash);
            rows.put(record);
            persist(rows);
            reviewView.setText("本期 "+issue+" 已冻结！\n原胆"+fused.rawSingle
                    +"，综合胆"+fused.single+"；复式"+fused.combinationCount+"组"
                    +"\nSHA-256 "+hash+"\n不可覆盖同一期次。");
        }catch(Exception e){reviewView.setText("冻结失败："+e.getMessage());}
    }
    private void review(){
        JSONArray records=readRecords();
        int i=find(records,getPeriod());
        if(i<0){reviewView.setText("没有这期的事前冻结快照，请先冻结后再复盘。");return;}
        String real=actualInput.getText().toString().trim();
        if(!real.matches("[0-9]{3}")){reviewView.setText("请输入三位号码，例如 067。");return;}
        JSONObject saved=records.optJSONObject(i);
        if(saved==null)return;
        if(saved.has("actual")&&!real.equals(saved.optString("actual"))){
            reviewView.setText("该期已复盘号码："+saved.optString("actual")+
                "，为避免事后改写记录，本版不允许直接覆盖。");
            return;
        }
        try{
            if(!saved.has("actual"))saved.put("actual",real);
            persist(records);
            JSONArray fours=saved.getJSONArray("four"),raw=saved.getJSONArray("rawFour");
            int loc=0,old=0;
            StringBuilder b=new StringBuilder("【冻结记录复盘】 "+saved.optString("issue")+
                    " 开奖 "+real+"\n");
            for(int p=0;p<3;p++){
                int d=real.charAt(p)-'0';
                boolean a=included(fours.getJSONArray(p),d);
                boolean c=included(raw.getJSONArray(p),d);
                if(a)loc++;if(c)old++;
                b.append(POS[p]).append("位 ").append(d).append("：最终4码")
                 .append(a?"✓":"×").append("；原六爻4码").append(c?"✓":"×").append("\n");
            }
            b.append("原六爻单胆").append(saved.optInt("rawSingle"))
               .append(real.contains(""+saved.optInt("rawSingle"))?"✓":"×").append("\n");
            b.append("合参单胆").append(saved.optInt("single"))
               .append(real.contains(""+saved.optInt("single"))?"✓":"×").append("\n");
            b.append("最终复式：").append(includedStr(saved.getJSONArray("fullDirect"),real)?"直选命中":"直选未中")
               .append("；Top12：").append(includedStr(saved.getJSONArray("top12"),real)?"命中":"未中").append("\n");
            b.append("原始组选保护：").append(includedStr(saved.getJSONArray("rawGroups"),sorted(real))?"覆盖":"未覆盖").append("\n");
            b.append("定位胆覆盖 ").append(loc).append("/3；六爻原位 ").append(old).append("/3\n");
            b.append("原始预测SHA ").append(saved.optString("hash")).append("\n");
            b.append("注意：不按新生成的卦或者开奖结果重算。");
            reviewView.setText(b.toString());
        }catch(Exception e){reviewView.setText("复盘读取失败："+e.getMessage());}
    }
    private void showHistory(){
        JSONArray rows=readRecords();
        StringBuilder s=new StringBuilder("【历史冻结｜共").append(rows.length()).append("条】\n");
        for(int i=rows.length()-1;i>=0;i--){
            JSONObject r=rows.optJSONObject(i);if(r==null)continue;
            s.append(r.optString("issue")).append("  ").append(r.optString("mode"))
             .append(" 原胆").append(r.optInt("rawSingle"))
             .append(" 合参").append(r.optInt("single"))
             .append("  SHA ").append(r.optString("hash").substring(0,Math.min(12,r.optString("hash").length())))
             .append(r.has("actual")?" 实开"+r.optString("actual"):" 待复盘").append("\n");
        }
        reviewView.setText(s.toString());
    }
    private void showStats(){
        JSONArray rows=readRecords();
        int count=0,rawHit=0,allHit=0,loc=0,rawLoc=0,exact=0,top12=0,rawGroup=0;
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);if(r==null||!r.has("actual"))continue;
            String actual=r.optString("actual");
            if(!actual.matches("[0-9]{3}"))continue;
            count++;
            if(actual.contains(""+r.optInt("rawSingle")))rawHit++;
            if(actual.contains(""+r.optInt("single")))allHit++;
            if(includedStr(r.optJSONArray("fullDirect"),actual))exact++;
            if(includedStr(r.optJSONArray("top12"),actual))top12++;
            if(includedStr(r.optJSONArray("rawGroups"),sorted(actual)))rawGroup++;
            JSONArray f=r.optJSONArray("four"),old=r.optJSONArray("rawFour");
            if(f==null||old==null)continue;
            for(int p=0;p<3;p++){
                if(included(f.optJSONArray(p),actual.charAt(p)-'0'))loc++;
                if(included(old.optJSONArray(p),actual.charAt(p)-'0'))rawLoc++;
            }
        }
        reviewView.setText("【本机已复盘 "+count+" 期】\n"
            +"原始六爻独胆出现 "+rawHit+"/"+count+"\n"
            +"综合独胆出现 "+allHit+"/"+count+"\n"
            +"原始六爻分位4码 "+rawLoc+"/"+(count*3)+"\n"
            +"综合分位4码 "+loc+"/"+(count*3)+"\n"
            +"64码或更少直选 "+exact+"/"+count+"\n"
            +"直选Top12 "+top12+"/"+count+"\n"
            +"原六爻组选保护 "+rawGroup+"/"+count+"\n"
            +"理论随机基线：单胆至少出现27.1%，每位四码40%，完整64组6.4%。"
            +"\n仅本机自填样本，不等于经第三方核实的开奖数据库。");
    }
    private LinearLayout card(LinearLayout parent){
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(11),dp(11),dp(11),dp(11));
        c.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(10);parent.addView(c,lp);return c;
    }
    private TextView txt(String value,int size,boolean bold){
        TextView v=new TextView(this);v.setText(value);v.setTextColor(0xFF202A35);
        v.setTextSize(size);v.setPadding(dp(3),dp(6),dp(3),dp(6));
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private EditText edit(String hint,String value){
        EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setTextSize(13);
        e.setSingleLine(true);e.setPadding(dp(7),dp(9),dp(7),dp(9));return e;
    }
    private Button button(String label){
        Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setAllCaps(false);return b;
    }
    private LinearLayout row(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);return l;
    }
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1f);}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();}
}
