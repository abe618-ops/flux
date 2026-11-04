package com.abe618.guajin;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int INK=Color.rgb(29,38,51), GOLD=Color.rgb(151,109,46);
    private static final String PREFS="guajin_history_v1", KEY="records";
    private Spinner category,method,s1,s2,s3;
    private EditText home,away,kickoff,xgHome,xgAway,ptsHome,ptsAway,handicap,totalLine,seed;
    private LinearLayout footballFields,basketFields,manualFields;
    private TextView result;
    private SharedPreferences prefs;

    private int dp(float x) {return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable bg(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private LinearLayout col() {
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private TextView text(String value,int sp,boolean bold,int color) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void space(LinearLayout root,int height) {
        View v=new View(this);root.addView(v,new LinearLayout.LayoutParams(1,dp(height)));
    }
    private void title(LinearLayout root,String value) {root.addView(text(value,17,true,INK));space(root,8);}
    private EditText input(LinearLayout root,String label,String initial,boolean decimal) {
        TextView t=text(label,13,false,Color.DKGRAY);root.addView(t);
        EditText edit=new EditText(this);
        edit.setTextSize(16);edit.setSingleLine(true);edit.setText(initial);
        if(decimal)edit.setInputType(InputType.TYPE_CLASS_NUMBER|
            InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        else edit.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(edit,new LinearLayout.LayoutParams(-1,dp(48)));
        space(root,8);return edit;
    }
    private Spinner spinner(LinearLayout root,String label,String[] items) {
        root.addView(text(label,13,false,Color.DKGRAY));
        Spinner sp=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);root.addView(sp,new LinearLayout.LayoutParams(-1,dp(47)));
        space(root,7);return sp;
    }
    private Button button(LinearLayout root,String label,boolean primary,Runnable act) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(16);
        b.setTextColor(primary?Color.WHITE:INK);
        b.setBackground(bg(primary?GOLD:Color.rgb(235,235,233),10));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(49));
        p.bottomMargin=dp(9);root.addView(b,p);b.setOnClickListener(v->act.run());
        return b;
    }
    private LinearLayout section(LinearLayout root) {
        LinearLayout card=col();card.setPadding(dp(15),dp(15),dp(15),dp(14));
        card.setBackground(bg(Color.WHITE,14));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);
        root.addView(card,p);return card;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        ScrollView scroll=new ScrollView(this);
        LinearLayout main=col();main.setPadding(dp(16),dp(20),dp(16),dp(28));
        main.setBackgroundColor(Color.rgb(247,245,239));scroll.addView(main);
        TextView heading=text("卦金书 · 三态排盘",26,true,INK);main.addView(heading);
        main.addView(text("第二册27卦原书索引 × 足球/篮球/数字实验模型",13,false,GOLD));
        space(main,13);
        LinearLayout first=section(main);
        title(first,"① 设定所占事项");
        category=spinner(first,"预测项目",new String[]{"足球","篮球","排列三/福彩3D"});
        home=input(first,"主队（数字模式可留空）","","false".equals("true"));
        away=input(first,"客队（数字模式可留空）","",false);
        kickoff=input(first,"比赛开始时间（选填，设备当地时间 yyyy-MM-dd HH:mm）","",false);
        seed=input(first,"固定实验种子/备注（可留空）","",false);

        footballFields=col();first.addView(footballFields);
        xgHome=input(footballFields,"足球：主队预期进球xG（手动）","1.35",true);
        xgAway=input(footballFields,"足球：客队预期进球xG（手动）","1.12",true);
        basketFields=col();first.addView(basketFields);
        ptsHome=input(basketFields,"篮球：主队预期得分（手动）","110",true);
        ptsAway=input(basketFields,"篮球：客队预期得分（手动）","107",true);
        handicap=input(basketFields,"篮球：主队让分线（如让5.5，填-5.5）","-3.5",true);
        totalLine=input(basketFields,"篮球：大小分盘口","218.5",true);

        LinearLayout second=section(main);
        title(second,"② 生成卦象");
        method=spinner(second,"排盘模式",new String[]{"手动三态","正时盘（软件实验）","随机盘"});
        manualFields=col();second.addView(manualFields);
        s1=spinner(manualFields,"第一位",GuajinEngine.STATES);
        s2=spinner(manualFields,"第二位",GuajinEngine.STATES);
        s3=spinner(manualFields,"第三位",GuajinEngine.STATES);
        CheckBox tuning=new CheckBox(this);
        tuning.setText("启用实验性三态微调（未做准确率验证）");
        tuning.setChecked(false);second.addView(tuning);
        button(second,"排盘并冻结预测",true,()->generate(tuning.isChecked()));

        LinearLayout out=section(main);title(out,"③ 结果");
        result=text("尚未排盘。原书断辞及预测算法分层存储，预测并非古籍原有结论。",15,false,INK);
        result.setTextIsSelectable(true);
        result.setLineSpacing(dp(5),1.0f);out.addView(result);

        LinearLayout more=section(main);title(more,"④ 古籍查阅与复盘");
        button(more,"查看第二册27卦索引",false,this::showIndex);
        button(more,"三册结构及研究限制",false,this::showVolumes);
        button(more,"查看已冻结记录 / 录入赛果",false,this::showHistory);
        button(more,"统计命中率（自录赛果）",false,this::showStats);
        more.addView(text("离线运行，无联网抓取赔率或赛程。足球以手动xG为基准的泊松模型；篮球以手动均值与正态近似演示；数字取数是确定性模拟。任何术数加权都未获实证验证。",12,false,Color.GRAY));

        category.setOnItemSelectedListener(listener(this::updateVisibility));
        method.setOnItemSelectedListener(listener(this::updateVisibility));
        updateVisibility();setContentView(scroll);
    }
    private android.widget.AdapterView.OnItemSelectedListener listener(Runnable update) {
        return new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View v,int p,long id){update.run();}
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        };
    }
    private void updateVisibility() {
        if(category==null||method==null)return;
        footballFields.setVisibility(category.getSelectedItemPosition()==0?View.VISIBLE:View.GONE);
        basketFields.setVisibility(category.getSelectedItemPosition()==1?View.VISIBLE:View.GONE);
        manualFields.setVisibility(method.getSelectedItemPosition()==0?View.VISIBLE:View.GONE);
    }
    private double decimal(EditText edit,double fallback) {
        try {return Double.parseDouble(edit.getText().toString().trim());}
        catch(Exception e){return fallback;}
    }
    private String value(EditText e){return e.getText().toString().trim();}
    private JSONArray load() {
        try{return new JSONArray(prefs.getString(KEY,"[]"));}
        catch(Exception e){return new JSONArray();}
    }
    private void save(JSONArray rows) {
        JSONArray limited=new JSONArray();
        for(int i=Math.max(0,rows.length()-300);i<rows.length();i++)limited.put(rows.optJSONObject(i));
        prefs.edit().putString(KEY,limited.toString()).apply();
    }
    private String stamp(long epoch) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date(epoch));
    }
    private void alert(String title,String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("确定",null).show();
    }
    private void generate(boolean tuning) {
        try {
            int mode=category.getSelectedItemPosition(),kind=method.getSelectedItemPosition();
            String h=value(home),a=value(away),salt=value(seed);
            String question=(mode==2?"数字取数":h+"vs"+a)+"|"+salt;
            long now=System.currentTimeMillis();
            GuajinEngine.Cast cast=kind==0?GuajinEngine.manual(s1.getSelectedItemPosition(),
                s2.getSelectedItemPosition(),s3.getSelectedItemPosition()):
                kind==1?GuajinEngine.time(question,now):GuajinEngine.random();
            GuajinEngine.Prediction prediction;
            if(mode==0)prediction=GuajinEngine.football(cast,decimal(xgHome,1.35),
                        decimal(xgAway,1.12),tuning);
            else if(mode==1)prediction=GuajinEngine.basketball(cast,decimal(ptsHome,110),
                        decimal(ptsAway,107),decimal(handicap,-3.5),decimal(totalLine,218.5),tuning);
            else prediction=GuajinEngine.digits(cast,salt.isEmpty()?UUID.randomUUID().toString():salt);
            String source="第二册第"+cast.number+"卦："+cast.symbols()+" / "+cast.code()+"\n"+cast.source();
            String reason=kind==0?"手动录入":kind==1?"按设备时间的分钟哈希起盘（软件规则）":"SecureRandom随机起盘";
            String display=source+"\n起盘方法："+reason+"\n"+
                "创建时间："+stamp(now)+"\n\n"+prediction.display+
                "\n\n模型版本："+GuajinEngine.VERSION+
                "\n注意：与原书不同，运动胜负/数字映射均属实验。";
            result.setText(display);
            JSONObject record=new JSONObject();
            record.put("id",UUID.randomUUID().toString()).put("created",now)
                  .put("mode",prediction.kind).put("home",h).put("away",a)
                  .put("kickoff",value(kickoff)).put("seed",salt)
                  .put("cast",cast.code()).put("ordinal",cast.number)
                  .put("pages",cast.source()).put("castingMethod",reason)
                  .put("tuning",tuning).put("modelVersion",prediction.version)
                  .put("forecast",prediction.display).put("predictedScore",prediction.predictedScore)
                  .put("direction",prediction.direction).put("size",prediction.size)
                  .put("parity",prediction.parity).put("quadrant",prediction.quadrant)
                  .put("halfFull",prediction.halfFull).put("digits",prediction.digits)
                  .put("handicap",prediction.handicap).put("totalLine",prediction.totalLine);
            long kick=0;
            try {
                if(!value(kickoff).isEmpty()) {
                    SimpleDateFormat parser=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA);
                    parser.setLenient(false);Date d=parser.parse(value(kickoff));
                    if(d!=null)kick=d.getTime();
                }
            } catch(Exception ignored){}
            record.put("eligibleBlind",kick>now);
            JSONArray rows=load();rows.put(record);save(rows);
            Toast.makeText(this,"已冻结保存；可在历史记录中录入赛果",Toast.LENGTH_SHORT).show();
        } catch(Exception e){alert("排盘失败",e.getMessage()==null?"请检查输入数据":e.getMessage());}
    }
    private void showIndex() {
        alert("第二册：27卦原书索引",GuajinEngine.index27()+
             "\n提示：这里只提供核对过的卦序与PDF页码，不擅自补写原书断语。");
    }
    private void showVolumes() {
        alert("三册研究范围",
           "第一册：PDF32页，前半为请师/本命三卦，后段有病情三卦。内容仍需逐字校勘。\n\n"+
           "第二册：PDF55页；第4—52页按圣/阳/阴三态三位列27卦，已建立可用的结构索引。\n\n"+
           "第三册：PDF41页；多套卦例与补充黑色卦形、九宫、五行季节图，部分形状不能等同三态3³。\n\n"+
           "软件目前只对第二册的27卦执行可靠的编码查表。其余两册保留为待校勘资料，不能直接套用。\n\n"+
           "所谓正时盘、足球/篮球/数字判断和现代概率模型，均是新增的实验规则，并非原书明示。");
    }
    private void showHistory() {
        JSONArray rows=load();
        if(rows.length()==0){alert("历史","尚无记录");return;}
        int n=Math.min(60,rows.length());
        String[] labels=new String[n];
        for(int k=0;k<n;k++) {
            JSONObject r=rows.optJSONObject(rows.length()-k-1);
            labels[k]=stamp(r.optLong("created"))+" "+r.optString("mode")+" "+
                      r.optString("home")+" "+r.optString("away")+" "+
                      r.optString("cast")+(r.has("actual")?" ✓已结算":" ·未结算");
        }
        new AlertDialog.Builder(this).setTitle("选择已冻结记录")
          .setItems(labels,(dialog,index)->showRecord(rows.length()-index-1)).show();
    }
    private void showRecord(int index) {
        JSONArray rows=load();JSONObject r=rows.optJSONObject(index);if(r==null)return;
        String detail="时间："+stamp(r.optLong("created"))+
           "\n卦序："+r.optString("ordinal")+"　"+r.optString("cast")+
           "\n开赛："+r.optString("kickoff")+
           "\n盲测时间条件："+(r.optBoolean("eligibleBlind")?"满足（未核验外部赛程）":"不满足/未填")+
           "\n\n"+r.optString("forecast")+
           (r.has("actual")?"\n\n实际："+r.optString("actual")+"\n复盘："+r.optString("review"):"");
        AlertDialog.Builder dlg=new AlertDialog.Builder(this).setTitle("冻结记录").setMessage(detail)
                .setNegativeButton("关闭",null);
        if(!r.has("actual"))dlg.setPositiveButton("录入结果",(dialog,which)->settle(index));
        dlg.show();
    }
    private void settle(int index) {
        JSONArray rows=load();JSONObject r=rows.optJSONObject(index);
        if(r==null||r.has("actual"))return;
        EditText actual=new EditText(this);
        actual.setSingleLine(true);actual.setPadding(dp(18),dp(8),dp(18),dp(8));
        String mode=r.optString("mode");
        actual.setHint(mode.equals("数字")?"实际三位号码，如583":"实际比分，如2:1");
        new AlertDialog.Builder(this).setTitle("录入真实结果（只录一次）")
          .setView(actual).setNegativeButton("取消",null)
          .setPositiveButton("保存", (d,w)->{
              try{
                  String raw=actual.getText().toString().trim();
                  String review;
                  if(mode.equals("数字")) {
                      if(!raw.matches("[0-9]{3}"))throw new IllegalArgumentException("请输入三位数字");
                      review=raw.equals(r.optString("direction"))?"直选正确":"直选不正确";
                  } else {
                      String[] p=raw.split("[:：]");
                      if(p.length!=2)throw new IllegalArgumentException("比分格式应为2:1");
                      int h=Integer.parseInt(p[0].trim()),a=Integer.parseInt(p[1].trim());
                      if(h<0||a<0||h>250||a>250)throw new IllegalArgumentException("比分超出支持范围");
                      raw=h+":"+a;
                      if(mode.equals("足球")) {
                          String win=h>a?"胜":h==a?"平":"负";
                          String size=h+a>=3?"大":"小",par=(h+a)%2==0?"双":"单";
                          review="胜平负"+(win.equals(r.optString("direction"))?"✓":"×")+
                             "；大小"+(size.equals(r.optString("size"))?"✓":"×")+
                             "；单双"+(par.equals(r.optString("parity"))?"✓":"×")+
                             "；精确比分"+(raw.equals(r.optString("predictedScore"))?"✓":"×");
                      } else {
                          double spread=h+r.optDouble("handicap")-a,tot=h+a-r.optDouble("totalLine");
                          String direction=spread>0?"主队上盘":spread<0?"主队下盘":"走盘";
                          String size=tot>0?"大分":tot<0?"小分":"走盘";
                          review="让分"+(direction.equals("走盘")?"走盘":direction.equals(r.optString("direction"))?"✓":"×")+
                                 "；大小"+(size.equals("走盘")?"走盘":size.equals(r.optString("size"))?"✓":"×");
                      }
                  }
                  r.put("actual",raw);r.put("review",review);r.put("settled",System.currentTimeMillis());
                  save(rows);alert("复盘结果",review+"\n原始预测保持冻结不变。");
              }catch(Exception e){alert("无法保存结果",e.getMessage());}
          }).show();
    }
    private void showStats() {
        JSONArray rows=load();int settled=0,blind=0;
        int football=0,win=0,size=0,parity=0,exact=0;
        int basket=0,cover=0,ou=0;
        int digitCount=0,digitHit=0;
        for(int i=0;i<rows.length();i++) {
            JSONObject r=rows.optJSONObject(i);
            if(r==null||!r.has("actual"))continue;
            settled++;if(r.optBoolean("eligibleBlind"))blind++;
            String mode=r.optString("mode"),s=r.optString("actual");
            if(mode.equals("数字")) {
                digitCount++;if(s.equals(r.optString("direction")))digitHit++;
            } else {
                String[] p=s.split(":");if(p.length!=2)continue;
                int h,a;try{h=Integer.parseInt(p[0]);a=Integer.parseInt(p[1]);}catch(Exception e){continue;}
                if(mode.equals("足球")) {
                    football++;
                    if((h>a?"胜":h==a?"平":"负").equals(r.optString("direction")))win++;
                    if((h+a>=3?"大":"小").equals(r.optString("size")))size++;
                    if(((h+a)%2==0?"双":"单").equals(r.optString("parity")))parity++;
                    if(s.equals(r.optString("predictedScore")))exact++;
                } else if(mode.equals("篮球")) {
                    basket++;
                    double sp=h+r.optDouble("handicap")-a,tot=h+a-r.optDouble("totalLine");
                    if(sp!=0&&(sp>0?"主队上盘":"主队下盘").equals(r.optString("direction")))cover++;
                    if(tot!=0&&(tot>0?"大分":"小分").equals(r.optString("size")))ou++;
                }
            }
        }
        alert("历史复盘（未经独立核验）",
           "冻结记录："+rows.length()+"；已录赛果："+settled+
           "；录入未来开赛时间的已结算记录："+blind+"\n\n"+
           "足球 "+football+" 场：胜平负 "+win+" / "+football+
           "，大小球 "+size+" / "+football+"，单双 "+parity+" / "+football+
           "，精确比分 "+exact+" / "+football+"\n"+
           "篮球 "+basket+" 场：让分 "+cover+" / "+basket+"，大小分 "+ou+" / "+basket+
           "（走盘未单独计分）\n"+
           "数字 "+digitCount+" 期：直选 "+digitHit+" / "+digitCount+
           "\n\n注意：统计包括自录数据和赛后起盘，不代表真正的事前盲测命中率。");
    }
}
