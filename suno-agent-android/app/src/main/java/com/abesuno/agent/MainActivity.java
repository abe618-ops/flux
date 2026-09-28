package com.abesuno.agent;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private EditText lyricsInput, styleInput;
    private RadioGroup genderGroup;
    private TextView status, modeText;
    private WebView web;
    private String pendingLyrics = "";
    private String pendingStyle = "";
    private String pendingGender = "male";
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("suno_agent", MODE_PRIVATE);
        buildMain();
    }

    private TextView tv(String s, int sp) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(sp); v.setTextColor(Color.rgb(25,25,25));
        v.setPadding(0,8,0,8);
        return v;
    }

    private Button btn(String s) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b;
    }

    private EditText field(String hint, int minLines) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setGravity(Gravity.TOP); e.setMinLines(minLines); e.setTextSize(16);
        e.setPadding(18,14,18,14);
        return e;
    }

    private void buildMain() {
        ScrollView sc = new ScrollView(this);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(28,28,28,42);

        TextView title = tv("Suno Agent", 28); title.setTypeface(null,1); main.addView(title);
        modeText = tv("模式：" + (prefs.getBoolean("api_mode", false) ? "OpenSuno API" : "手机网页登录"), 14); main.addView(modeText);

        TextView l1 = tv("歌词", 17); l1.setTypeface(null,1); main.addView(l1);
        lyricsInput = field("直接输入完整歌词……", 8);
        lyricsInput.setText(prefs.getString("draft_lyrics", ""));
        main.addView(lyricsInput, new LinearLayout.LayoutParams(-1,-2));

        TextView l2 = tv("风格", 17); l2.setTypeface(null,1); main.addView(l2);
        styleInput = field("例如：90年代港台抒情摇滚，钢琴、电吉他，副歌爆发，温暖怀旧", 3);
        styleInput.setText(prefs.getString("draft_style", ""));
        main.addView(styleInput, new LinearLayout.LayoutParams(-1,-2));

        TextView l3 = tv("声音", 17); l3.setTypeface(null,1); main.addView(l3);
        genderGroup = new RadioGroup(this); genderGroup.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton male = new RadioButton(this); male.setId(2001); male.setText("男声");
        RadioButton female = new RadioButton(this); female.setId(2002); female.setText("女声");
        genderGroup.addView(male,new RadioGroup.LayoutParams(0,-2,1));
        genderGroup.addView(female,new RadioGroup.LayoutParams(0,-2,1));
        String savedGender = prefs.getString("draft_gender", "male");
        genderGroup.check("female".equals(savedGender) ? 2002 : 2001);
        main.addView(genderGroup);

        Button gen = btn("一键载入 Suno 并生成");
        main.addView(gen,new LinearLayout.LayoutParams(-1,-2));
        gen.setOnClickListener(v -> generate());

        LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
        Button login = btn("登录 / 打开 Suno"); Button credits = btn("Credits");
        row2.addView(login,new LinearLayout.LayoutParams(0,-2,1));
        row2.addView(credits,new LinearLayout.LayoutParams(0,-2,1));
        main.addView(row2);
        login.setOnClickListener(v -> openWeb(false)); credits.setOnClickListener(v -> credits());

        LinearLayout row3 = new LinearLayout(this); row3.setOrientation(LinearLayout.HORIZONTAL);
        Button clear = btn("清空"); Button settings = btn("设置");
        row3.addView(clear,new LinearLayout.LayoutParams(0,-2,1));
        row3.addView(settings,new LinearLayout.LayoutParams(0,-2,1));
        main.addView(row3);
        clear.setOnClickListener(v -> { lyricsInput.setText(""); styleInput.setText(""); });
        settings.setOnClickListener(v -> settings());

        status = tv("首次使用只需点“登录 / 打开 Suno”完成登录。以后填写歌词、风格、男/女声后点一次生成。", 14);
        main.addView(status);
        TextView note = tv("自动载入会分别寻找 Suno 的 Lyrics 与 Style 输入区域。若页面没有独立男女声控件，软件会自动把 male vocal / female vocal 加进 Style。",12);
        note.setTextColor(Color.DKGRAY); main.addView(note);

        sc.addView(main); setContentView(sc);
    }

    private String selectedGender() {
        return genderGroup != null && genderGroup.getCheckedRadioButtonId() == 2002 ? "female" : "male";
    }

    private void generate() {
        String lyrics = lyricsInput.getText().toString().trim();
        String style = styleInput.getText().toString().trim();
        String gender = selectedGender();
        if(lyrics.isEmpty()){ toast("请先填写歌词"); return; }
        if(style.isEmpty()){ toast("请先填写风格"); return; }

        prefs.edit().putString("draft_lyrics",lyrics).putString("draft_style",style).putString("draft_gender",gender).apply();
        pendingLyrics = lyrics; pendingStyle = style; pendingGender = gender;
        if(prefs.getBoolean("api_mode", false)) generateApi(); else openWeb(true);
    }

    private void openWeb(boolean inject) {
        web = new WebView(this); web.setBackgroundColor(Color.WHITE);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true); s.setMediaPlaybackRequiresUserGesture(false);
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String url){
                super.onPageFinished(v,url);
                if(inject && !pendingLyrics.isEmpty() && url.contains("suno")) v.postDelayed(() -> injectFields(), 2200);
            }
        });
        setContentView(web);
        web.loadUrl("https://suno.com/create");
    }

    private void injectFields() {
        if(web==null) return;
        String l = JSONObject.quote(pendingLyrics);
        String styleWithVoice = pendingStyle + ("female".equals(pendingGender) ? ", female vocal, female singer" : ", male vocal, male singer");
        String st = JSONObject.quote(styleWithVoice);
        String gender = JSONObject.quote(pendingGender);

        String js = "(function(){"+
            "const lyrics="+l+",style="+st+",gender="+gender+";"+
            "function textOf(el){return ((el.getAttribute('placeholder')||'')+' '+(el.getAttribute('aria-label')||'')+' '+(el.getAttribute('name')||'')+' '+(el.id||'')).toLowerCase();}"+
            "function nearby(el){let s=textOf(el); let p=el.parentElement; for(let i=0;i<3&&p;i++,p=p.parentElement){s+=' '+(p.innerText||'').slice(0,250).toLowerCase();} return s;}"+
            "function setv(el,val){if(!el)return false; el.focus(); if(el.tagName==='TEXTAREA'||el.tagName==='INPUT'){let proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype; let d=Object.getOwnPropertyDescriptor(proto,'value'); if(d&&d.set)d.set.call(el,val); else el.value=val;}else{el.innerText=val;} el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:val})); el.dispatchEvent(new Event('change',{bubbles:true})); return true;}"+
            "let fields=[...document.querySelectorAll('textarea,input[type=text],[contenteditable=true]')].filter(e=>e.offsetParent!==null);"+
            "let le=fields.find(e=>/(lyrics|lyric|歌词)/i.test(nearby(e)));"+
            "let se=fields.find(e=>/(style|styles|风格|genre|music style)/i.test(nearby(e)) && e!==le);"+
            "if(!le && fields.length>0) le=fields[0];"+
            "if(!se && fields.length>1) se=fields.find(e=>e!==le);"+
            "let okL=setv(le,lyrics), okS=setv(se,style);"+
            "let controls=[...document.querySelectorAll('button,[role=button],[role=radio],label')].filter(e=>e.offsetParent!==null);"+
            "let gpat=gender==='female'?/(female|woman|女声|女)/i:/(male|man|男声|男)/i;"+
            "let gc=controls.find(e=>gpat.test((e.innerText||e.textContent||'').trim())); if(gc) try{gc.click();}catch(e){}"+
            "setTimeout(()=>{let bs=[...document.querySelectorAll('button')].filter(e=>e.offsetParent!==null&&!e.disabled); let b=bs.find(x=>/(create|generate|生成|创建)/i.test((x.innerText||x.textContent||'').trim())); if(b)b.click();},1400);"+
            "return JSON.stringify({lyrics:okL,style:okS,genderControl:!!gc});"+
        "})()";

        web.evaluateJavascript(js, r -> {
            if(r==null){ toast("已打开 Suno，请确认页面输入框"); return; }
            if(r.contains("\\\"lyrics\\\":true") && r.contains("\\\"style\\\":true")) toast("歌词和风格已自动载入，并尝试生成");
            else toast("已打开 Suno；部分输入框未识别，请检查当前页面");
        });
    }

    private String autoTitle() {
        String[] lines = pendingLyrics.split("\\n");
        for(String line: lines){ String t=line.trim(); if(!t.isEmpty()) return t.length()>24?t.substring(0,24):t; }
        return "Suno Agent";
    }

    private void generateApi() {
        String base = prefs.getString("base_url", "").trim();
        if(base.isEmpty()){ toast("请先在设置中填写 OpenSuno 地址"); settings(); return; }
        status.setText("正在提交生成任务……");
        new Thread(() -> {
            try {
                JSONObject j = new JSONObject();
                j.put("prompt", pendingLyrics);
                j.put("tags", pendingStyle + ("female".equals(pendingGender) ? ", female vocal" : ", male vocal"));
                j.put("title", autoTitle());
                String r = request("POST", base + "/api/custom_generate", j.toString());
                runOnUiThread(() -> {
                    status.setText("已提交。返回：\n" + shrink(r));
                    new AlertDialog.Builder(this).setTitle("Suno 返回").setMessage(shrink(r)).setPositiveButton("打开 Suno",(d,w)->openWeb(false)).setNegativeButton("关闭",null).show();
                });
            } catch(Exception e){ runOnUiThread(() -> status.setText("提交失败："+e.getMessage())); }
        }).start();
    }

    private void credits() {
        if(!prefs.getBoolean("api_mode", false)){ toast("网页登录模式下在 Suno 页面查看 Credits"); openWeb(false); return; }
        String base = prefs.getString("base_url", "").trim(); if(base.isEmpty()){ settings(); return; }
        status.setText("正在查询 Credits……");
        new Thread(() -> { try { String r=request("GET",base+"/api/get_limit",null); runOnUiThread(() -> status.setText("Credits："+shrink(r))); } catch(Exception e){runOnUiThread(() -> status.setText("查询失败："+e.getMessage()));}}).start();
    }

    private String request(String method,String u,String body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(60000); c.setRequestMethod(method); c.setRequestProperty("Content-Type","application/json");
        if(body!=null){ c.setDoOutput(true); try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));} }
        InputStream in=(c.getResponseCode()>=200&&c.getResponseCode()<400)?c.getInputStream():c.getErrorStream();
        BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)); StringBuilder sb=new StringBuilder(); String line;
        while((line=br.readLine())!=null)sb.append(line); return sb.toString();
    }

    private void settings() {
        LinearLayout l=new LinearLayout(this); l.setPadding(28,8,28,0); l.setOrientation(LinearLayout.VERTICAL);
        CheckBox api=new CheckBox(this); api.setText("使用 OpenSuno API 模式"); api.setChecked(prefs.getBoolean("api_mode",false)); l.addView(api);
        EditText base=new EditText(this); base.setHint("例如 http://192.168.1.10:3001"); base.setText(prefs.getString("base_url","")); l.addView(base);
        new AlertDialog.Builder(this).setTitle("Suno Agent 设置").setView(l).setPositiveButton("保存",(d,w)->{
            prefs.edit().putBoolean("api_mode",api.isChecked()).putString("base_url",base.getText().toString().trim().replaceAll("/$","")).apply();
            modeText.setText("模式："+(api.isChecked()?"OpenSuno API":"手机网页登录"));
        }).setNegativeButton("取消",null).show();
    }

    private String shrink(String s){ if(s==null)return ""; return s.length()>1800?s.substring(0,1800)+"…":s; }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }

    @Override public void onBackPressed(){
        if(web!=null){ if(web.canGoBack()){web.goBack();return;} web=null; buildMain(); return; }
        super.onBackPressed();
    }
}
