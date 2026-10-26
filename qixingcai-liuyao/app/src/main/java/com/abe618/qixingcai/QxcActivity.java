package com.abe618.qixingcai;

import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class QxcActivity extends Activity {
    private final ZoneId TZ=ZoneId.of("Asia/Shanghai");
    private LinearLayout body;
    private EditText dateBox,timeBox,issueBox,seedBox,resultBox;
    private final EditText[] posBoxes=new EditText[7];
    private TextView prediction,status,methods,details,historyDisplay;
    private SevenStarCore.Forecast forecast;
    private LiuyaoEngine.Analysis cast;
    private final int[][] selected=new int[7][];
    private final String[] PLACES={"第1位","第2位","第3位","第4位","第5位","第6位","第7位(0–14)"};
    private boolean randomMode=false;
    private SharedPreferences pref;
    private final int DARK=0xFF15222D, MUTED=0xFF5F6A75, BLUE=0xFF225ECB;
    private static final int MAX_RECORDS=500;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        pref=getSharedPreferences("QXC_FROZEN_V1",MODE_PRIVATE);
        ScrollView scroll=new ScrollView(this);
        body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(0xFFF3F6F9);
        body.setPadding(dp(12),dp(12),dp(12),dp(20));
        scroll.addView(body);
        body.addView(text("七星彩 · 六爻七位合参",23,true));
        body.addView(text("六爻纳甲为主 · 七路独立取数 · 每位最多4码 · 事前冻结复盘",12,false));

        LinearLayout home=card();
        home.addView(text("【合参总览】",16,true));
        prediction=text("正在使用北京时间起盘…",18,true);
        prediction.setTextIsSelectable(true);
        home.addView(prediction);
        status=text("前六位0—9；第七位0—14。",12,false);
        home.addView(status);

        LinearLayout controls=card();
        controls.addView(text("起盘参数 · 北京时间",15,true));
        ZonedDateTime now=ZonedDateTime.now(TZ);
        LinearLayout dt=row();
        dateBox=edit("yyyy-MM-dd",now.toLocalDate().toString());
        timeBox=edit("HH:mm",now.format(DateTimeFormatter.ofPattern("HH:mm")));
        dt.addView(dateBox,weight());dt.addView(timeBox,weight());
        controls.addView(dt);
        issueBox=edit("期次/记录名",now.toLocalDate().toString());
        controls.addView(issueBox);
        seedBox=edit("随机种子（可复现）","");
        controls.addView(seedBox);
        LinearLayout buttons=row();
        Button time=button("北京时间盘");
        Button rand=button("随机盘");
        Button next=button("随机下一盘");
        buttons.addView(time,weight());buttons.addView(rand,weight());buttons.addView(next,weight());
        controls.addView(buttons);
        time.setOnClickListener(v->run(false,false));
        rand.setOnClickListener(v->run(true,false));
        next.setOnClickListener(v->{seedBox.setText("");run(true,true);});

        LinearLayout positions=card();
        positions.addView(text("【七位综合四码 · 可修改】",16,true));
        positions.addView(text("前六位只能填0—9，第七位可填0—14，逗号分隔；每位置最多4个不同号码。",12,false));
        for(int p=0;p<7;p++){
            LinearLayout line=row();
            TextView label=text(PLACES[p],13,true);
            line.addView(label,new LinearLayout.LayoutParams(dp(116),-2));
            EditText input=edit("如 1,3,6,9","");
            input.setTextSize(14);
            posBoxes[p]=input;
            line.addView(input,new LinearLayout.LayoutParams(0,-2,1));
            positions.addView(line);
        }
        Button apply=button("应用分位修改 / 重排候选");
        positions.addView(apply);
        apply.setOnClickListener(v->applySelection());
        TextView help=text("说明：综合候选四码是实验筛选。选择4×4×4×4×4×4×4会生成16,384种组合，理论相当于32,768元的单注总额，并非推荐实际投注。",12,false);
        positions.addView(help);

        LinearLayout multi=card();
        multi.addView(text("【各术数独立预测】",16,true));
        methods=text("起盘后展示各家七位四码",13,false);
        methods.setTextIsSelectable(true);
        multi.addView(methods);
        Button toggle=button("展开 / 收起全部六爻盘面与取数过程");
        multi.addView(toggle);
        details=text("",12,false);
        details.setVisibility(View.GONE);
        details.setTextIsSelectable(true);
        multi.addView(details);
        toggle.setOnClickListener(v->details.setVisibility(
            details.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));

        LinearLayout freeze=card();
        freeze.addView(text("【冻结预测 · 开奖复盘】",16,true));
        LinearLayout auditButtons=row();
        Button lock=button("冻结本期");
        Button show=button("历史冻结");
        auditButtons.addView(lock,weight());auditButtons.addView(show,weight());
        freeze.addView(auditButtons);
        resultBox=edit("开奖号，如 123456+07","");
        freeze.addView(resultBox);
        LinearLayout reviewButtons=row();
        Button review=button("复盘本期");
        Button summary=button("累计统计");
        reviewButtons.addView(review,weight());reviewButtons.addView(summary,weight());
        freeze.addView(reviewButtons);
        historyDisplay=text("仅使用冻结时保存的预测快照复盘，不把当前重新起的盘记入成绩。",13,false);
        historyDisplay.setTextIsSelectable(true);
        freeze.addView(historyDisplay);
        lock.setOnClickListener(v->freeze());
        show.setOnClickListener(v->showHistory());
        review.setOnClickListener(v->review());
        summary.setOnClickListener(v->stats());

        LinearLayout footer=card();
        footer.addView(text("【七星彩玩法边界】",16,true));
        footer.addView(text("按中国体育彩票7星彩现行规则：前六位0—9，第七位0—14。单一完整号码总空间为15,000,000种。每位4码的16,384种组合全部对应的彩票金额远高于一般单张票限额。本工具不联网购票，也不能证明术数可提高开奖概率。",13,false));
        footer.addView(text("玩法规则：https://www.sporttery.cn/help/2857.html?gid=1",12,false));

        setContentView(scroll);
        run(false,false);
    }

    private void run(boolean random,boolean next){
        try{
            LocalDate date=LocalDate.parse(dateBox.getText().toString().trim());
            LocalTime time=LocalTime.parse(timeBox.getText().toString().trim());
            randomMode=random;
            String seed=seedBox.getText().toString().trim();
            if(random&&seed.isEmpty()){
                byte[] nonce=new byte[16];
                new SecureRandom().nextBytes(nonce);
                StringBuilder tmp=new StringBuilder();
                for(byte x:nonce)tmp.append(String.format(Locale.ROOT,"%02x",x&255));
                seed=tmp.toString();seedBox.setText(seed);
            }
            cast=LiuyaoEngine.run("七星彩",random?LiuyaoEngine.Mode.RANDOM:LiuyaoEngine.Mode.TIME,date,time,seed);
            forecast=SevenStarCore.calculate(cast,date,time,seed);
            for(int p=0;p<7;p++){
                selected[p]=forecast.picks[p].clone();
                posBoxes[p].setText(join(selected[p]));
            }
            render(next);
        }catch(Exception ex){
            cast=null;forecast=null;
            prediction.setText("起盘失败："+ex.getMessage()+"\n请检查日期或时间格式。");
        }
    }
    private String join(int[] ds){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<ds.length;i++){if(i>0)b.append(",");b.append(ds[i]);}
        return b.toString();
    }
    private String issue(){
        String x=issueBox.getText().toString().trim();
        return x.isEmpty()?dateBox.getText().toString().trim():x;
    }
    private void render(boolean next){
        if(forecast==null)return;
        prediction.setText(forecast.summary);
        status.setText("模式："+(randomMode?"随机盘":"北京时间盘")
            +"；期次："+issue()+"；六爻："+cast.hexName+"→"+cast.changedHexName
            +(next?"；已刷新随机Seed":""));
        details.setText(forecast.details);
        StringBuilder all=new StringBuilder();
        for(int i=0;i<SevenStarCore.METHODS.length;i++){
            all.append(SevenStarCore.METHODS[i]).append("  ×")
                .append(SevenStarCore.WEIGHTS[i]).append("\n");
            for(int p=0;p<7;p++){
                all.append("  ").append(p+1).append("位 ")
                   .append(join(forecast.each[i][p]));
                all.append(p==6?"\n":"   ");
                if(p==2)all.append("\n");
            }
            all.append("\n");
        }
        methods.setText(all.toString());
    }
    private boolean applySelection(){
        if(forecast==null){toast("请先起盘");return false;}
        for(int p=0;p<7;p++){
            String raw=posBoxes[p].getText().toString().trim().replace('，',',').replace('、',',')
                .replace('／',',').replace('/',',');
            if(raw.isEmpty()){toast(PLACES[p]+"不能留空");return false;}
            String[] vals=raw.split(",");
            LinkedHashSet<Integer> set=new LinkedHashSet<>();
            for(String x:vals){
                if(!x.trim().matches("\\d{1,2}")){toast(PLACES[p]+"格式不正确，请用逗号分隔");return false;}
                int n=Integer.parseInt(x.trim());
                if(n<0||n>(p==6?14:9)){toast(PLACES[p]+"取值超出范围");return false;}
                set.add(n);
            }
            if(set.size()==0||set.size()>4){toast(PLACES[p]+"每位请选择1—4个不同数字");return false;}
            int[] v=new int[set.size()];int j=0;for(int n:set)v[j++]=n;
            selected[p]=v;
        }
        long combinations=1;for(int[] a:selected)combinations*=a.length;
        ArrayList<String> top=customTop();
        StringBuilder txt=new StringBuilder("【自定义七位合参】\n");
        for(int p=0;p<7;p++)txt.append(p+1).append("位 ").append(join(selected[p])).append("\n");
        txt.append("组合数 ").append(combinations)
            .append("；理论完整覆盖 ")
            .append(String.format(Locale.ROOT,"%.5f",combinations*100.0/15000000.0)).append("%\n")
            .append("Top ").append(String.join("、",top.subList(0,Math.min(8,top.size()))));
        prediction.setText(txt.toString());
        toast("已按限制重排："+combinations+"组");
        return true;
    }
    private static class Candidate{
        String code;double score;
        Candidate(String c,double s){code=c;score=s;}
    }
    private ArrayList<String> customTop(){
        ArrayList<Candidate> all=new ArrayList<>();
        int[] n=new int[7];
        enumerate(0,n,0,all);
        all.sort((x,y)->{
            int c=Double.compare(y.score,x.score);
            return c!=0?c:x.code.compareTo(y.code);
        });
        ArrayList<String> top=new ArrayList<>();
        for(int i=0;i<Math.min(20,all.size());i++)top.add(all.get(i).code);
        return top;
    }
    private void enumerate(int p,int[] n,double sc,ArrayList<Candidate> all){
        if(p==7){
            StringBuilder b=new StringBuilder();
            for(int i=0;i<6;i++)b.append(n[i]);
            b.append('+').append(String.format(Locale.ROOT,"%02d",n[6]));
            all.add(new Candidate(b.toString(),sc));return;
        }
        for(int d:selected[p]){n[p]=d;enumerate(p+1,n,sc+forecast.combined[p][d],all);}
    }
    private JSONArray saved(){
        try{return new JSONArray(pref.getString("records","[]"));}
        catch(Exception ex){return new JSONArray();}
    }
    private String checksum(String data) throws Exception{
        byte[] d=MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format(Locale.ROOT,"%02x",x&255));
        return b.toString();
    }
    private JSONObject existing(JSONArray rows,String issue){
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);
            if(r!=null&&issue.equals(r.optString("issue")))return r;
        }
        return null;
    }
    private void freeze(){
        if(!applySelection())return;
        JSONArray rows=saved();
        if(existing(rows,issue())!=null){
            historyDisplay.setText("这一期已有冻结快照，不允许覆盖，以防止赛后重写。");
            return;
        }
        if(rows.length()>=MAX_RECORDS){historyDisplay.setText("本地最多保存"+MAX_RECORDS+"期；请先备份或整理历史记录。");return;}
        try{
            JSONObject r=new JSONObject();
            r.put("issue",issue()).put("version",SevenStarCore.VERSION)
            .put("createdBeijing",ZonedDateTime.now(TZ).toString())
            .put("castingBeijing",dateBox.getText().toString()+" "+timeBox.getText().toString())
            .put("mode",randomMode?"随机盘":"北京时间盘")
            .put("seed",seedBox.getText().toString())
            .put("pillars",forecast.pillars)
            .put("hex",forecast.mainHex+"→"+forecast.changedHex)
            .put("report",forecast.details)
            .put("summary",prediction.getText().toString());
            JSONArray picks=new JSONArray();
            JSONArray perMethod=new JSONArray();
            for(int p=0;p<7;p++){
                JSONArray list=new JSONArray();
                for(int n:selected[p])list.put(n);
                picks.put(list);
            }
            for(int m=0;m<SevenStarCore.METHODS.length;m++){
                JSONArray method=new JSONArray();
                for(int p=0;p<7;p++){
                    JSONArray list=new JSONArray();
                    for(int n:forecast.each[m][p])list.put(n);
                    method.put(list);
                }
                perMethod.put(method);
            }
            r.put("picks",picks).put("methods",perMethod);
            String h=checksum(r.toString());
            r.put("hash",h);
            rows.put(r);
            pref.edit().putString("records",rows.toString()).apply();
            historyDisplay.setText("已冻结 "+issue()+"，七位组合数 "+count(picks)
                +"\n校验SHA-256："+h+"\n开奖后只读取此快照。");
        }catch(Exception ex){historyDisplay.setText("冻结失败："+ex.getMessage());}
    }
    private long count(JSONArray picks){
        long n=1;for(int p=0;p<7;p++)n*=picks.optJSONArray(p).length();return n;
    }
    private int[] actual(){
        String raw=resultBox.getText().toString().trim().replaceAll("\\s+","")
            .replace('＋','+').replace('-','+').replace('/','+');
        if(!raw.matches("\\d{6}\\+\\d{1,2}"))return null;
        String[] parts=raw.split("\\+");
        int last=Integer.parseInt(parts[1]);
        if(last<0||last>14)return null;
        int[] ans=new int[7];
        for(int i=0;i<6;i++)ans[i]=parts[0].charAt(i)-'0';
        ans[6]=last;return ans;
    }
    private boolean contains(JSONArray a,int number){
        if(a==null)return false;
        for(int i=0;i<a.length();i++)if(a.optInt(i,-1)==number)return true;
        return false;
    }
    private void review(){
        int[] winning=actual();
        if(winning==null){historyDisplay.setText("请填官方开奖结果：前6位+末位0—14，例如 123456+07。");return;}
        JSONArray rows=saved();JSONObject r=existing(rows,issue());
        if(r==null){historyDisplay.setText("该期没有事前冻结，不能事后补记预测。");return;}
        try{
            String official=resultBox.getText().toString().trim();
            if(r.has("actual")&&!r.getString("actual").equals(official)){
                historyDisplay.setText("该期已录入其他开奖结果；本版不允许覆盖。");return;
            }
            if(!r.has("actual"))r.put("actual",official);
            pref.edit().putString("records",rows.toString()).apply();
            JSONArray pools=r.getJSONArray("picks"),methods=r.getJSONArray("methods");
            StringBuilder report=new StringBuilder("【"+issue()+" 冻结复盘】\n");
            int matched=0;
            for(int p=0;p<7;p++){
                boolean yes=contains(pools.getJSONArray(p),winning[p]);
                if(yes)matched++;
                report.append(PLACES[p]).append(" 实开").append(winning[p])
                     .append("　综合四码").append(yes?"✓":"×").append("\n");
            }
            report.append("综合位置覆盖 ").append(matched).append("/7；")
              .append(matched==7?"全部位置覆盖（含完整直选）":"没有完整七位覆盖").append("\n\n");
            for(int m=0;m<SevenStarCore.METHODS.length;m++){
                int hit=0;JSONArray mp=methods.getJSONArray(m);
                for(int p=0;p<7;p++)if(contains(mp.getJSONArray(p),winning[p]))hit++;
                report.append(SevenStarCore.METHODS[m]).append(" ").append(hit).append("/7\n");
            }
            report.append("\n快照SHA：").append(r.optString("hash")).append("\n");
            report.append("只对原冻结的候选检验；手工录入开奖不等于已联网官方核验。");
            historyDisplay.setText(report.toString());
        }catch(Exception ex){historyDisplay.setText("复盘失败："+ex.getMessage());}
    }
    private void showHistory(){
        JSONArray rows=saved();
        StringBuilder b=new StringBuilder("【本机历史冻结："+rows.length()+"期】\n");
        for(int i=rows.length()-1;i>=0;i--){
            JSONObject r=rows.optJSONObject(i);if(r==null)continue;
            b.append(r.optString("issue")).append(' ').append(r.optString("hex"))
             .append("  ").append(r.has("actual")?"已复盘":"未复盘")
             .append(" SHA:").append(r.optString("hash").substring(0,Math.min(10,r.optString("hash").length()))).append("\n");
        }
        historyDisplay.setText(b.toString());
    }
    private void stats(){
        JSONArray rows=saved();
        int[] methodHits=new int[SevenStarCore.METHODS.length],methodFull=new int[methodHits.length];
        int[] posHits=new int[7];int count=0,full=0;long pool=0;
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);
            if(r==null||!r.has("actual"))continue;
            String raw=r.optString("actual").replaceAll("\\s+","").replace('＋','+').replace('-','+').replace('/','+');
            if(!raw.matches("\\d{6}\\+\\d{1,2}"))continue;
            int last=Integer.parseInt(raw.split("\\+")[1]);if(last>14)continue;
            int[] wins=new int[7];for(int p=0;p<6;p++)wins[p]=raw.charAt(p)-'0';wins[6]=last;
            JSONArray picks=r.optJSONArray("picks"),methods=r.optJSONArray("methods");if(picks==null||methods==null)continue;
            count++;int hit=0;pool+=count(picks);
            for(int p=0;p<7;p++)if(contains(picks.optJSONArray(p),wins[p])){posHits[p]++;hit++;}
            if(hit==7)full++;
            for(int m=0;m<methodHits.length;m++){
                JSONArray method=methods.optJSONArray(m);
                if(method==null)continue;
                int got=0;
                for(int p=0;p<7;p++)if(contains(method.optJSONArray(p),wins[p]))got++;
                methodHits[m]+=got;if(got==7)methodFull[m]++;
            }
        }
        StringBuilder b=new StringBuilder("【已冻结且复盘 "+count+" 期】\n");
        if(count==0){historyDisplay.setText(b.append("没有可统计记录。").toString());return;}
        for(int p=0;p<7;p++)b.append(PLACES[p]).append(' ').append(posHits[p]).append('/').append(count).append("\n");
        b.append("综合七位完整覆盖 ").append(full).append('/').append(count).append("\n")
         .append("对应随机平均期望完整覆盖约 ").append(String.format(Locale.ROOT,"%.4f",pool/15000000.0))
         .append(" 次（按各期真实组合数）。\n\n");
        for(int m=0;m<methodHits.length;m++)b.append(SevenStarCore.METHODS[m])
            .append(" 位置总覆盖 ").append(methodHits[m]).append('/').append(count*7)
            .append("；七位全覆盖 ").append(methodFull[m]).append('/').append(count).append("\n");
        b.append("样本来自用户手工录入；没有置信区间或样本外证据证明超过随机。");
        historyDisplay.setText(b.toString());
    }

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(0xFFFFFFFF);
        c.setPadding(dp(10),dp(10),dp(10),dp(10));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(10);
        body.addView(c,lp);return c;
    }
    private TextView text(String content,int size,boolean bold){
        TextView t=new TextView(this);t.setText(content);t.setTextColor(bold?DARK:MUTED);
        t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(dp(4),dp(5),dp(4),dp(5));return t;
    }
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private EditText edit(String hint,String value){
        EditText e=new EditText(this);e.setText(value);e.setHint(hint);
        e.setSingleLine(true);e.setTextSize(13);
        e.setPadding(dp(6),dp(7),dp(6),dp(7));return e;
    }
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setTextSize(11);b.setAllCaps(false);return b;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
