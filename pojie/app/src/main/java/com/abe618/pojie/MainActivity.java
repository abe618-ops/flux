package com.abe618.pojie;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int VOICE = 7001;
    private static final int AUDIO = 7002;
    private static final int DARK = Color.rgb(30, 36, 40);
    private static final int MUTED = Color.rgb(105, 115, 120);
    private static final int CARD = Color.rgb(247, 248, 246);
    private static final int GREEN = Color.rgb(25, 122, 80);

    private SharedPreferences p;
    private LinearLayout content;
    private TextView timerView;
    private CountDownTimer timer;
    private String voiceTarget = "challenge";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("pojie", MODE_PRIVATE);
        initDay();
        askAudio();
        today();
    }

    private String day() { return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()); }
    private String prettyDay() { return new SimpleDateFormat("M月d日 · EEEE", Locale.CHINA).format(new Date()); }

    private void initDay() {
        if (!day().equals(p.getString("day", ""))) {
            p.edit().putString("day", day())
                    .putBoolean("challenge_done", false).putBoolean("challenge_award", false)
                    .putBoolean("new_done", false).putBoolean("new_award", false)
                    .putBoolean("review_award", false).putString("review", "")
                    .putString("today_blocker", "").putString("tasks", defaultTasks()).apply();
        }
    }

    private String defaultTasks() {
        JSONArray a = new JSONArray();
        try { a.put(task("阅读20分钟")); a.put(task("运动 / 活动身体")); a.put(task("整理最重要的一件事")); }
        catch (JSONException ignored) {}
        return a.toString();
    }

    private JSONObject task(String s) throws JSONException {
        JSONObject o = new JSONObject(); o.put("text", s); o.put("done", false); o.put("award", false); return o;
    }

    private void shell(String title) {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        ScrollView sc = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(18), dp(18), dp(18), dp(24));
        sc.addView(content); root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL); nav.setPadding(dp(4), dp(4), dp(4), dp(7));
        nav(root, nav, "今天", this::today); nav(root, nav, "破界", this::breakPage); nav(root, nav, "统计", this::stats); nav(root, nav, "我的", this::mine);
        root.addView(nav); setContentView(root);
        content.addView(txt(title, 28, DARK, true));
    }

    private void nav(LinearLayout root, LinearLayout nav, String s, Runnable r) {
        Button b = btn(s); b.setOnClickListener(v -> r.run()); nav.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
    }

    private void today() {
        initDay(); shell("破界 · 今天"); content.addView(txt(prettyDay(), 14, MUTED, false)); gap(12);
        LinearLayout score = card(); score.addView(txt("今日目标：完成底线 + 跨出一点舒适区", 16, DARK, true));
        score.addView(txt("执行值 " + p.getInt("points",0) + "  ·  启动 " + p.getInt("starts",0) + " 次", 14, MUTED, false)); content.addView(score); gap(10);
        challengeCard(); gap(10); tasksCard(); gap(10); newTryCard(); gap(10); reviewCard();
    }

    private void challengeCard() {
        LinearLayout c = card(); c.addView(txt("今天最难的一件事",20,DARK,true)); c.addView(txt("不是做得完美，而是先开始。",13,MUTED,false));
        EditText e = edit(p.getString("challenge",""), "例如：给一直拖着的人打电话"); c.addView(e);
        LinearLayout r = row(); Button save=btn("保存"); save.setOnClickListener(v->{p.edit().putString("challenge",e.getText().toString().trim()).apply(); toast("已保存");});
        Button mic=btn("🎙 语音"); mic.setOnClickListener(v->{voiceTarget="challenge"; voice();}); r.addView(save,weight()); r.addView(mic,weight()); c.addView(r);
        TextView diff=txt("破界难度："+stars(p.getInt("difficulty",3))+"（点按切换）",14,MUTED,false); diff.setPadding(0,dp(7),0,dp(7));
        diff.setOnClickListener(v->{int n=p.getInt("difficulty",3)%5+1;p.edit().putInt("difficulty",n).apply();diff.setText("破界难度："+stars(n)+"（点按切换）");}); c.addView(diff);
        CheckBox done=new CheckBox(this); done.setText("我已经面对并完成/推进了它"); done.setTextSize(16); done.setChecked(p.getBoolean("challenge_done",false));
        done.setOnCheckedChangeListener((v,x)->{p.edit().putBoolean("challenge_done",x).apply();if(x&&!p.getBoolean("challenge_award",false)){points(20);inc("break_done");p.edit().putBoolean("challenge_award",true).apply();toast("+20 执行值");}}); c.addView(done);
        LinearLayout a=row(); Button five=btn("🔥 先做5分钟"); five.setOnClickListener(v->fiveMinutes()); Button stuck=btn("我卡住了"); stuck.setOnClickListener(v->blocker()); a.addView(five,weight()); a.addView(stuck,weight()); c.addView(a);
        timerView=txt("",22,GREEN,true); timerView.setGravity(Gravity.CENTER); c.addView(timerView); content.addView(c);
    }

    private void tasksCard() {
        LinearLayout c=card(); c.addView(txt("固定底线",20,DARK,true)); c.addView(txt("只保留真正重要、可以打勾的少数动作。长按任务可编辑。",13,MUTED,false));
        JSONArray a=tasks(); for(int i=0;i<a.length();i++){final int k=i;try{JSONObject o=a.getJSONObject(i);CheckBox cb=new CheckBox(this);cb.setText(o.optString("text"));cb.setTextSize(16);cb.setChecked(o.optBoolean("done"));cb.setOnCheckedChangeListener((v,x)->setTask(k,x));cb.setOnLongClickListener(v->{editTask(k);return true;});c.addView(cb);}catch(Exception ignored){}}
        Button add=btn("＋ 添加底线任务"); add.setOnClickListener(v->addTask()); c.addView(add); content.addView(c);
    }

    private void newTryCard() {
        LinearLayout c=card(); c.addView(txt("🌱 今日新尝试",20,DARK,true)); EditText e=edit(p.getString("new_try",""),"试一个新方法 / 问一个问题 / 接触一点新东西");c.addView(e);
        LinearLayout r=row();Button s=btn("保存");s.setOnClickListener(v->p.edit().putString("new_try",e.getText().toString().trim()).apply());Button m=btn("🎙 语音");m.setOnClickListener(v->{voiceTarget="new_try";voice();});r.addView(s,weight());r.addView(m,weight());c.addView(r);
        CheckBox d=new CheckBox(this);d.setText("今天已经尝试");d.setChecked(p.getBoolean("new_done",false));d.setOnCheckedChangeListener((v,x)->{p.edit().putBoolean("new_done",x).apply();if(x&&!p.getBoolean("new_award",false)){points(10);inc("new_count");p.edit().putBoolean("new_award",true).apply();toast("+10 执行值");}});c.addView(d);content.addView(c);
    }

    private void reviewCard() {
        LinearLayout c=card();c.addView(txt("晚上10秒复盘",20,DARK,true));c.addView(txt("今天最想逃避的是什么？最后有没有开始？真正的堵点是什么？",14,MUTED,false));
        String s=p.getString("review","");c.addView(txt(s.isEmpty()?"还没有复盘":s,15,DARK,false));LinearLayout r=row();Button v=btn("🎙 语音复盘");v.setOnClickListener(x->{voiceTarget="review";voice();});Button t=btn("文字复盘");t.setOnClickListener(x->reviewDialog());r.addView(v,weight());r.addView(t,weight());c.addView(r);content.addView(c);
    }

    private void breakPage() {
        shell("破界训练"); content.addView(txt("每天只跨出一点点，不追求刺激，追求可持续。",15,MUTED,false)); gap(10);
        String[] a={"★ 微不适：换一种小做法、主动问一个问题","★★ 有抵触：做一件通常会拖延的小事","★★★ 明显想逃避：主动联系、公开表达、处理困难任务","★★★★ 较难：面对重要冲突、提交重要成果、主动争取机会","★★★★★ 重大挑战：只在确实安全、必要且准备充分时使用"};
        for(String s:a){LinearLayout c=card();c.addView(txt(s,16,DARK,false));content.addView(c);gap(7);} LinearLayout c=card();c.addView(txt("今日原则",20,DARK,true));c.addView(txt("1. 找到最想回避的一件事\n2. 把下一步缩到5分钟以内\n3. 先启动，再评价\n4. 完成后记录真实堵点，而不是责备自己",16,DARK,false));content.addView(c);
    }

    private void stats() {
        shell("执行统计");stat("执行值",p.getInt("points",0)+"");stat("5分钟启动",p.getInt("starts",0)+" 次");stat("完成破界",p.getInt("break_done",0)+" 次");stat("完成底线任务",p.getInt("task_done",0)+" 次");stat("新尝试",p.getInt("new_count",0)+" 次");gap(8);content.addView(txt("你的主要堵点",20,DARK,true));
        Map<String,Integer> m=blockers();int total=0;for(int n:m.values())total+=n;if(total==0)content.addView(txt("还没有记录。下次卡住时点“我卡住了”。",15,MUTED,false));else for(Map.Entry<String,Integer> e:m.entrySet())content.addView(txt(e.getKey()+"  "+e.getValue()+" 次 · "+Math.round(e.getValue()*100f/total)+"%",16,DARK,false));
    }

    private void mine() {
        shell("我的");LinearLayout c=card();c.addView(txt("破界 v0.1",20,DARK,true));c.addView(txt("本地优先 · 无需登录 · 数据保存在你的手机。",14,MUTED,false));Button ex=btn("导出 / 分享今日记录");ex.setOnClickListener(v->share());c.addView(ex);Button reset=btn("重置今天（调试）");reset.setOnClickListener(v->resetToday());c.addView(reset);content.addView(c);gap(10);
        LinearLayout f=card();f.addView(txt("第一性原理",20,DARK,true));f.addView(txt("执行力不是“更用力提醒自己”，而是持续降低启动阻力：\n\n清晰下一步 × 立即开始 × 即时反馈\n——————————————\n启动阻力 × 不确定性 × 不适感",16,DARK,false));content.addView(f);
    }

    private void fiveMinutes() {
        if(timer!=null)return;inc("starts");timer=new CountDownTimer(300000,1000){public void onTick(long ms){if(timerView!=null)timerView.setText("点火中  "+String.format(Locale.getDefault(),"%02d:%02d",ms/60000,(ms/1000)%60));}public void onFinish(){timer=null;points(2);if(timerView!=null)timerView.setText("✓ 已完成一次启动");new AlertDialog.Builder(MainActivity.this).setTitle("已经开始了").setMessage("最难的是从0到1。现在可以继续，也可以把这5分钟视为一次有效启动。").setPositiveButton("继续做",null).setNegativeButton("今天先到这里",null).show();}}.start();
    }

    private void blocker() {
        String[] o={"不知道怎么开始","事情太大","怕做不好 / 怕失败","不想面对","太麻烦","容易忘","没时间","其实没那么重要"};new AlertDialog.Builder(this).setTitle("真正卡在哪里？").setItems(o,(d,k)->{p.edit().putString("today_blocker",o[k]).apply();inc("blocker_"+k);new AlertDialog.Builder(this).setTitle("把阻力降到最低").setMessage(advice(k)).setPositiveButton("🔥 直接先做5分钟",(x,y)->fiveMinutes()).setNegativeButton("知道了",null).show();}).show();
    }

    private String advice(int k){String[] a={"不要解决整件事。只写出下一步能被肉眼看见的动作：打开文件、拨出号码、写第一句话。","把任务缩小到5分钟能完成的部分。今天先从“完成”改成“启动”。","把标准从“做好”改成“产生第一版”。第一版可以普通，完成后再优化。","回避会让不适感继续变大。给自己一个很小的暴露剂量：只面对5分钟。","删掉不必要步骤。问自己：如果只能做一个动作，哪个动作能让事情继续前进？","绑定固定触发点：例如“吃完早餐后，我立即做X”。","不要寻找整块时间。先找最小可用窗口，5分钟也算有效推进。","如果它真的不重要，删除比拖延更好。明确放弃也是执行力。"};return a[k];}

    private JSONArray tasks(){try{return new JSONArray(p.getString("tasks",defaultTasks()));}catch(Exception e){return new JSONArray();}}
    private void saveTasks(JSONArray a){p.edit().putString("tasks",a.toString()).apply();}
    private void setTask(int k,boolean done){JSONArray a=tasks();try{JSONObject o=a.getJSONObject(k);o.put("done",done);if(done&&!o.optBoolean("award")){o.put("award",true);points(5);inc("task_done");toast("+5 执行值");}saveTasks(a);}catch(Exception ignored){}}

    private void addTask(){EditText e=edit("","一句话写清楚底线任务");new AlertDialog.Builder(this).setTitle("添加底线任务").setView(e).setPositiveButton("添加",(d,w)->{String s=e.getText().toString().trim();if(!s.isEmpty()){JSONArray a=tasks();try{a.put(task(s));saveTasks(a);}catch(Exception ignored){}today();}}).setNegativeButton("取消",null).show();}
    private void editTask(int k){JSONArray a=tasks();String old="";try{old=a.getJSONObject(k).optString("text");}catch(Exception ignored){}EditText e=edit(old,"任务");new AlertDialog.Builder(this).setTitle("编辑底线任务").setView(e).setPositiveButton("保存",(d,w)->{try{a.getJSONObject(k).put("text",e.getText().toString().trim());saveTasks(a);today();}catch(Exception ignored){}}).setNeutralButton("删除",(d,w)->{JSONArray n=new JSONArray();for(int i=0;i<a.length();i++)if(i!=k)try{n.put(a.getJSONObject(i));}catch(Exception ignored){}saveTasks(n);today();}).setNegativeButton("取消",null).show();}

    private void reviewDialog(){EditText e=edit(p.getString("review",""),"一句话：今天逃避了什么、是否开始、堵点是什么");e.setMinLines(4);new AlertDialog.Builder(this).setTitle("今日复盘").setView(e).setPositiveButton("保存",(d,w)->saveReview(e.getText().toString().trim())).setNegativeButton("取消",null).show();}
    private void saveReview(String s){p.edit().putString("review",s).apply();if(!p.getBoolean("review_award",false)){points(5);p.edit().putBoolean("review_award",true).apply();}toast("复盘已保存");today();}

    private void voice(){if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO);return;}Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN");i.putExtra(RecognizerIntent.EXTRA_PROMPT,"请直接说出内容");try{startActivityForResult(i,VOICE);}catch(ActivityNotFoundException e){toast("当前手机没有可用的系统语音识别服务");}}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==VOICE&&res==RESULT_OK&&data!=null){ArrayList<String> r=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(r==null||r.isEmpty())return;String s=r.get(0).trim();if("challenge".equals(voiceTarget))p.edit().putString("challenge",s).apply();else if("new_try".equals(voiceTarget))p.edit().putString("new_try",s).apply();else saveReview(s);today();}}
    private void askAudio(){if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO);}

    private void share(){StringBuilder s=new StringBuilder("# 破界 · "+day()+"\n\n最难的一件事："+p.getString("challenge","")+"\n是否推进："+(p.getBoolean("challenge_done",false)?"是":"否")+"\n今日新尝试："+p.getString("new_try","")+"\n今日堵点："+p.getString("today_blocker","")+"\n复盘："+p.getString("review","")+"\n\n底线任务：\n");JSONArray a=tasks();for(int i=0;i<a.length();i++)try{JSONObject o=a.getJSONObject(i);s.append(o.optBoolean("done")?"☑ ":"☐ ").append(o.optString("text")).append("\n");}catch(Exception ignored){}Intent x=new Intent(Intent.ACTION_SEND);x.setType("text/plain");x.putExtra(Intent.EXTRA_TEXT,s.toString());startActivity(Intent.createChooser(x,"导出今日记录"));}
    private void resetToday(){new AlertDialog.Builder(this).setTitle("重置今天？").setMessage("会清除今天状态，但不会清除累计统计。").setPositiveButton("重置",(d,w)->{p.edit().putString("day","").apply();initDay();today();}).setNegativeButton("取消",null).show();}

    private Map<String,Integer> blockers(){String[] n={"不知道怎么开始","事情太大","怕做不好/失败","不想面对","太麻烦","容易忘","没时间","不重要"};Map<String,Integer> m=new LinkedHashMap<>();for(int i=0;i<n.length;i++){int v=p.getInt("blocker_"+i,0);if(v>0)m.put(n[i],v);}return m;}
    private void stat(String n,String v){LinearLayout c=card(),r=row();TextView a=txt(n,16,MUTED,false),b=txt(v,24,DARK,true);b.setGravity(Gravity.END);r.addView(a,new LinearLayout.LayoutParams(0,dp(52),1));r.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));c.addView(r);content.addView(c);gap(7);}
    private void inc(String k){p.edit().putInt(k,p.getInt(k,0)+1).apply();}
    private void points(int n){p.edit().putInt("points",p.getInt("points",0)+n).apply();}

    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(15),dp(16),dp(15));c.setBackgroundColor(CARD);return c;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,dp(50),1);}
    private EditText edit(String s,String hint){EditText e=new EditText(this);e.setText(s);e.setHint(hint);e.setTextSize(16);e.setTextColor(DARK);e.setHintTextColor(Color.rgb(150,155,155));e.setSingleLine(false);return e;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);return b;}
    private TextView txt(String s,int z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setLineSpacing(0,1.15f);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private void gap(int h){Space s=new Space(this);content.addView(s,new LinearLayout.LayoutParams(1,dp(h)));}
    private String stars(int n){StringBuilder s=new StringBuilder();for(int i=1;i<=5;i++)s.append(i<=n?"★":"☆");return s.toString();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
