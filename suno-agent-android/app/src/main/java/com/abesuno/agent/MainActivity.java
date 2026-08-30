package com.abesuno.agent;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.speech.RecognizerIntent;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_SPEECH = 1001;
    private EditText prompt;
    private TextView status, modeText;
    private WebView web;
    private String pendingPrompt = "";
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("suno_agent", MODE_PRIVATE);
        buildMain();
    }

    private TextView tv(String s, int sp) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.rgb(25,25,25));
        v.setPadding(0,8,0,8); return v;
    }
    private Button btn(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b; }

    private void buildMain() {
        ScrollView sc = new ScrollView(this);
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); main.setPadding(28,28,28,40);
        TextView title = tv("Suno Agent", 28); title.setTypeface(null,1); main.addView(title);
        modeText = tv("模式：" + (prefs.getBoolean("api_mode", false) ? "OpenSuno API" : "手机网页登录"), 14); main.addView(modeText);
        prompt = new EditText(this); prompt.setHint("直接说你想要什么音乐，例如：做一首中文男声怀旧摇滚，雨夜开车回家，副歌爆发……");
        prompt.setGravity(Gravity.TOP); prompt.setMinLines(6); prompt.setMaxLines(12); prompt.setTextSize(17); main.addView(prompt, new LinearLayout.LayoutParams(-1,-2));

        LinearLayout row1 = new LinearLayout(this); row1.setOrientation(LinearLayout.HORIZONTAL);
        Button mic = btn("🎤 语音输入"); Button gen = btn("生成音乐");
        row1.addView(mic,new LinearLayout.LayoutParams(0,-2,1)); row1.addView(gen,new LinearLayout.LayoutParams(0,-2,1)); main.addView(row1);
        mic.setOnClickListener(v -> speech()); gen.setOnClickListener(v -> generate());

        LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
        Button login = btn("登录 / 打开 Suno"); Button credits = btn("Credits");
        row2.addView(login,new LinearLayout.LayoutParams(0,-2,1)); row2.addView(credits,new LinearLayout.LayoutParams(0,-2,1)); main.addView(row2);
        login.setOnClickListener(v -> openWeb(false)); credits.setOnClickListener(v -> credits());

        LinearLayout row3 = new LinearLayout(this); row3.setOrientation(LinearLayout.HORIZONTAL);
        Button recent = btn("最近输入"); Button settings = btn("设置");
        row3.addView(recent,new LinearLayout.LayoutParams(0,-2,1)); row3.addView(settings,new LinearLayout.LayoutParams(0,-2,1)); main.addView(row3);
        recent.setOnClickListener(v -> showRecent()); settings.setOnClickListener(v -> settings());

        status = tv("首次使用：点“登录 / 打开 Suno”，在内置网页完成登录。之后可直接一句话生成。", 14); main.addView(status);
        TextView note = tv("提示：网页登录自动填充依赖 Suno 当前网页结构，若网页更新导致自动按钮失效，会停留在创建页并保留你的登录态；API 模式可填写 OpenSuno Bridge/REST 地址。",12); note.setTextColor(Color.DKGRAY); main.addView(note);
        sc.addView(main); setContentView(sc);
    }

    private void speech() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "说说你想做什么音乐");
        try { startActivityForResult(i, REQ_SPEECH); } catch(Exception e) { toast("系统没有可用语音识别"); }
    }
    @Override protected void onActivityResult(int r,int c,Intent d){ super.onActivityResult(r,c,d); if(r==REQ_SPEECH&&c==RESULT_OK&&d!=null){ ArrayList<String>x=d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS); if(x!=null&&!x.isEmpty())prompt.setText(x.get(0)); }}

    private void generate() {
        String p = prompt.getText().toString().trim(); if(p.isEmpty()){ toast("先输入一句音乐要求"); return; }
        saveRecent(p); pendingPrompt = p;
        if(prefs.getBoolean("api_mode", false)) generateApi(p); else openWeb(true);
    }

    private void openWeb(boolean inject) {
        web = new WebView(this); web.setBackgroundColor(Color.WHITE);
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true); s.setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String url){ super.onPageFinished(v,url); if(inject && !pendingPrompt.isEmpty() && url.contains("suno")) { v.postDelayed(() -> injectPrompt(), 1800); } }
        });
        setContentView(web); web.loadUrl("https://suno.com/create");
    }

    private void injectPrompt() {
        if(web==null) return;
        String q = JSONObject.quote(pendingPrompt);
        String js = "(function(){const p="+q+"; let e=document.querySelector('textarea'); if(!e)e=document.querySelector('[contenteditable=\\\"true\\\"]'); if(!e){return 'NO_INPUT';} if(e.tagName==='TEXTAREA'||e.tagName==='INPUT'){const d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value'); if(d&&d.set)d.set.call(e,p); else e.value=p;}else{e.focus();document.execCommand('selectAll',false,null);document.execCommand('insertText',false,p);} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); setTimeout(()=>{const bs=[...document.querySelectorAll('button')]; const b=bs.find(x=>!x.disabled&&/(create|generate|生成|创建)/i.test((x.innerText||x.textContent||'').trim())); if(b)b.click();},1200); return 'OK';})()";
        web.evaluateJavascript(js, r -> toast(r.contains("NO_INPUT") ? "已打开 Suno，请在网页确认创建输入框" : "已自动填入并尝试提交"));
    }

    private void generateApi(String p) {
        String base = prefs.getString("base_url", "").trim(); if(base.isEmpty()){ toast("请先在设置中填写 OpenSuno 地址"); settings(); return; }
        status.setText("正在提交生成任务……");
        new Thread(() -> {
            try {
                JSONObject j = new JSONObject(); j.put("prompt", p); j.put("make_instrumental", false);
                String r = request("POST", base + "/api/generate", j.toString());
                runOnUiThread(() -> { status.setText("已提交。返回：\n" + shrink(r)); new AlertDialog.Builder(this).setTitle("Suno 返回").setMessage(shrink(r)).setPositiveButton("打开 Suno",(d,w)->openWeb(false)).setNegativeButton("关闭",null).show(); });
            } catch(Exception e){ runOnUiThread(() -> status.setText("提交失败："+e.getMessage())); }
        }).start();
    }

    private void credits() {
        if(!prefs.getBoolean("api_mode", false)){ toast("网页登录模式下请在 Suno 页面查看 Credits；API 模式可直接查询"); openWeb(false); return; }
        String base = prefs.getString("base_url", "").trim(); if(base.isEmpty()){ settings(); return; }
        status.setText("正在查询 Credits……"); new Thread(() -> { try { String r=request("GET",base+"/api/get_limit",null); runOnUiThread(() -> status.setText("Credits："+shrink(r))); } catch(Exception e){runOnUiThread(() -> status.setText("查询失败："+e.getMessage()));}}).start();
    }

    private String request(String method,String u,String body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(60000); c.setRequestMethod(method); c.setRequestProperty("Content-Type","application/json");
        if(body!=null){ c.setDoOutput(true); try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));} }
        InputStream in=(c.getResponseCode()>=200&&c.getResponseCode()<400)?c.getInputStream():c.getErrorStream(); BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)); StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null)sb.append(line); return sb.toString();
    }

    private void settings() {
        LinearLayout l=new LinearLayout(this); l.setPadding(28,8,28,0); l.setOrientation(LinearLayout.VERTICAL);
        CheckBox api=new CheckBox(this); api.setText("使用 OpenSuno API 模式"); api.setChecked(prefs.getBoolean("api_mode",false)); l.addView(api);
        EditText base=new EditText(this); base.setHint("例如 http://192.168.1.10:3001"); base.setText(prefs.getString("base_url","")); l.addView(base);
        new AlertDialog.Builder(this).setTitle("Suno Agent 设置").setView(l).setPositiveButton("保存",(d,w)->{prefs.edit().putBoolean("api_mode",api.isChecked()).putString("base_url",base.getText().toString().trim().replaceAll("/$","")).apply(); modeText.setText("模式："+(api.isChecked()?"OpenSuno API":"手机网页登录"));}).setNegativeButton("取消",null).show();
    }

    private void saveRecent(String p){ String old=prefs.getString("recent",""); String n=p+"\n——\n"+old; if(n.length()>5000)n=n.substring(0,5000); prefs.edit().putString("recent",n).apply(); }
    private void showRecent(){ String r=prefs.getString("recent","暂无记录"); new AlertDialog.Builder(this).setTitle("最近输入").setMessage(r).setPositiveButton("关闭",null).show(); }
    private String shrink(String s){ if(s==null)return ""; return s.length()>1800?s.substring(0,1800)+"…":s; }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed(){ if(web!=null){ if(web.canGoBack()){web.goBack();return;} web=null; buildMain(); return;} super.onBackPressed(); }
}
