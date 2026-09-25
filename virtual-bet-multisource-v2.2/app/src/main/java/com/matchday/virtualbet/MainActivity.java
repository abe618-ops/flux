package com.matchday.virtualbet;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

/** Single-activity local simulation UI. No payment, account or ticket-purchase capability. */
public class MainActivity extends Activity {
    static final int JC=0, BJ=1, GLOBAL=2, RECORDS=3, PAGE_SIZE=12;
    static final int BG=0xfff5f6f8,DARK=0xff222b38,MUTED=0xff697586,ACCENT=0xffdb543d,BLUE=0xff2167b0;
    final Handler ui=new Handler(Looper.getMainLooper());
    final ExecutorService io=Executors.newFixedThreadPool(4), resultIo=Executors.newSingleThreadExecutor();
    final LinkedHashMap<String,JSONObject> cart=new LinkedHashMap<>();
    TicketStore store; DataClient.Scope pageScope=new DataClient.Scope(), syncScope;
    ArrayList<JSONObject> matches=new ArrayList<>(); LinearLayout root,body; TextView info,cartBar; ScrollView scroll;
    String day=FootballData.today(), market="混合", oddsProvider="自动"; int mode=JC,page=0,generation=0; boolean destroyed=false,syncing=false;

    @Override public void onCreate(Bundle state){super.onCreate(state);try{
        store=new TicketStore(this); oddsProvider=store.prefs.getString("odds_provider_220","自动"); if(state!=null){day=state.getString("day",day);mode=state.getInt("mode",JC);market=state.getString("market","混合");oddsProvider=state.getString("oddsProvider",oddsProvider);}
        restoreCart(); build(); if(mode==RECORDS)showRecords(); else load(false);
    }catch(Throwable e){safeStart(e);}}
    @Override protected void onSaveInstanceState(Bundle b){b.putString("day",day);b.putInt("mode",mode);b.putString("market",market);b.putString("oddsProvider",oddsProvider);super.onSaveInstanceState(b);}
    @Override protected void onDestroy(){destroyed=true;generation++;pageScope.cancel();if(syncScope!=null)syncScope.cancel();io.shutdownNow();resultIo.shutdownNow();super.onDestroy();}

    void safeStart(Throwable e){try{getSharedPreferences("virtual_bet",0).edit().putString("last_startup_error",String.valueOf(e)).apply();}catch(Throwable ignored){}
        LinearLayout p=col();p.setPadding(dp(18),dp(26),dp(18),dp(18));p.addView(label("FLUX 体育 · 指数学习",22,DARK,true));p.addView(label("初始化遇到异常，原保存单不会清除。",14,MUTED,false));p.addView(button("重新进入",()->{try{store=new TicketStore(this);build();if(mode==RECORDS)showRecords();else load(true);}catch(Throwable x){toast("仍有异常："+x.getClass().getSimpleName());}}));setContentView(p);}
    int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);} LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    GradientDrawable box(int color,int r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;}
    TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(6),dp(3),dp(6),dp(3));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    TextView button(String s,Runnable r){TextView t=label(s,13,BLUE,true);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(40));t.setBackground(box(Color.WHITE,8));t.setOnClickListener(v->r.run());return t;}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();} String money(double x){return String.format(Locale.CHINA,"%.2f",x);}

    void build(){root=col();root.setBackgroundColor(BG);setContentView(root);root.addView(label("le xu 盘口训练 · 独立版 3.1.0",18,DARK,true));TextView w=label("本地虚拟学习 · 保存时锁赔 · 不接入支付/出票",10,ACCENT,true);w.setGravity(Gravity.CENTER);root.addView(w);
        LinearLayout tabs=new LinearLayout(this);String[] names={"竞彩足球","北京单场","全球投注","保存单"};for(int i=0;i<4;i++){final int m=i;TextView b=button(names[i],()->switchMode(m));if(i==mode){b.setTextColor(Color.WHITE);b.setBackground(box(ACCENT,8));}tabs.addView(b,new LinearLayout.LayoutParams(0,dp(40),1));}root.addView(tabs);
        if(mode!=RECORDS){LinearLayout d=new LinearLayout(this);d.addView(button("‹",()->date(FootballData.shift(day,-1))),new LinearLayout.LayoutParams(dp(44),dp(40)));d.addView(button(FootballData.iso(day),this::pickDate),new LinearLayout.LayoutParams(0,dp(40),1));d.addView(button("›",()->date(FootballData.shift(day,1))),new LinearLayout.LayoutParams(dp(44),dp(40)));d.addView(button("刷新",()->load(true)),new LinearLayout.LayoutParams(dp(64),dp(40)));root.addView(d);root.addView(filterBar());}
        info=label("",11,MUTED,false);root.addView(info);scroll=new ScrollView(this);body=col();body.setPadding(dp(6),dp(2),dp(6),dp(6));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));cartBar=label("",13,DARK,true);root.addView(cartBar);updateCart();}
    View filterBar(){LinearLayout row=new LinearLayout(this);row.addView(marketSpinner(),new LinearLayout.LayoutParams(0,dp(42),1));if(mode==BJ||mode==GLOBAL)row.addView(oddsSpinner(),new LinearLayout.LayoutParams(0,dp(42),1));return row;}
    View oddsSpinner(){Spinner s=new Spinner(this);String[] xs={"自动","12BET","10BET","Bet365","Crown","澳门"};s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,xs));int selected=0;for(int i=0;i<xs.length;i++)if(xs[i].equals(oddsProvider))selected=i;s.setSelection(selected);s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){String next=xs[position];if(next.equals(oddsProvider))return;oddsProvider=next;store.prefs.edit().putString("odds_provider_220",oddsProvider).apply();clearProviderOdds();render();describe();enrich(generation,pageScope);}public void onNothingSelected(AdapterView<?> p){}});return s;}
    void clearProviderOdds(){for(JSONObject f:matches){JSONObject mk=f.optJSONObject("markets");if(mk!=null)mk.remove("HAD");JSONObject src=f.optJSONObject("sources");if(src!=null)src.remove("HAD");}if(!matches.isEmpty())store.cache(mode,day,matches);}
    View marketSpinner(){Spinner s=new Spinner(this);ArrayList<String> xs=new ArrayList<>();xs.add("混合");for(String m:FootballData.MARKETS)if(visible(m))xs.add(FootballData.title(m));s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,xs));int pos=Math.max(0,xs.indexOf(market.equals("混合")?"混合":FootballData.title(market)));s.setSelection(pos);s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){String x=xs.get(position);market=x.equals("混合")?"混合":marketId(x);if(body!=null)render();}public void onNothingSelected(AdapterView<?> p){}});return s;}
    String marketId(String title){for(String m:FootballData.MARKETS)if(FootballData.title(m).equals(title))return m;return "HAD";}
    boolean visible(String m){
        if(mode==JC)return !m.equals("OU")&&!m.equals("AH");
        if(mode==BJ)return !m.equals("HHAD")&&!m.equals("OU")&&!m.equals("AH");
        return mode==GLOBAL&&!m.equals("HHAD");
    }
    void switchMode(int m){if(m==mode)return;generation++;pageScope.cancel();mode=m;market="混合";page=0;matches.clear();build();if(mode==RECORDS){showRecords();sync();}else load(false);}
    void date(String k){generation++;pageScope.cancel();day=k;page=0;matches.clear();build();load(false);} void pickDate(){Calendar c=Calendar.getInstance(FootballData.TZ);c.setTimeInMillis(FootballData.time(FootballData.iso(day)+" 12:00"));new DatePickerDialog(this,(v,y,m,d)->date(String.format(Locale.US,"%04d%02d%02d",y,m+1,d)),c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}

    void load(boolean force){generation++;pageScope.cancel();pageScope=new DataClient.Scope();final int g=generation,md=mode;final String k=day;final DataClient.Scope scope=pageScope;ArrayList<JSONObject> cached=store.cache(md,k);if(!cached.isEmpty()){matches=cached;render();info.setText("缓存秒开："+matches.size()+" 场 · 后台更新中");}
        int sources=md==GLOBAL?1:2;for(int s=0;s<sources;s++){final int source=s;io.execute(()->{try{ArrayList<JSONObject> incoming=DataClient.fixtures(md,source,k,scope);ui.post(()->{if(destroyed||g!=generation||scope.cancelled)return;try{matches=FootballData.merge(matches,incoming,source==1&&md==JC);store.cache(md,k,matches);render();describe();if(md==BJ||md==GLOBAL)enrich(g,scope);}catch(Exception e){info.setText("数据合并失败："+e.getMessage());}});}catch(Exception e){ui.post(()->{if(!destroyed&&g==generation&&matches.isEmpty())info.setText("来源暂不可用："+DataClient.error(e)+" · 可刷新重试");});}});}
        ui.postDelayed(()->{if(g==generation&&!destroyed){scope.cancel();if(matches.isEmpty())info.setText("本次载入已停止，避免长期卡住；请刷新重试");}},16000);}
    void describe(){int priced=0;for(JSONObject f:matches)if(FootballData.priceCount(f)>0)priced++;String extra=(mode==BJ||mode==GLOBAL)?" · 浮动赔率："+oddsProvider:"";info.setText(matches.size()+" 场 · "+priced+" 场已有赔率"+extra);}
    boolean providerSatisfied(JSONObject f){String src=f.optJSONObject("sources")==null?"":f.optJSONObject("sources").optString("HAD");if(src.isEmpty())return false;if("自动".equals(oddsProvider))return src.startsWith("12BET")||src.startsWith("10BET")||src.startsWith("Bet365")||src.startsWith("Crown")||src.startsWith("澳门");return src.startsWith(oddsProvider);}
    void enrich(int g,DataClient.Scope scope){int from=page*PAGE_SIZE,to=Math.min(matches.size(),from+PAGE_SIZE*2);for(int i=from;i<to;i++){JSONObject f=matches.get(i);if(providerSatisfied(f))continue;String id=f.optString("titanId");if(id.isEmpty())continue;long start=f.optLong("kickoff");io.execute(()->{try{JSONObject p=DataClient.euro(id,start,oddsProvider,scope);if(p==null)return;ui.post(()->{if(destroyed||g!=generation)return;try{FootballData.prices(f,"HAD",p.getJSONObject("prices"),p.getString("source"));store.cache(mode,day,matches);render();describe();}catch(Exception ignored){}});}catch(Exception ignored){}});}}
    void render(){if(body==null||mode==RECORDS)return;body.removeAllViews();if(matches.isEmpty()){body.addView(label("暂无比赛数据。",14,MUTED,false));return;}page=Math.min(page,Math.max(0,(matches.size()-1)/PAGE_SIZE));for(int i=page*PAGE_SIZE;i<Math.min(matches.size(),page*PAGE_SIZE+PAGE_SIZE);i++)body.addView(matchCard(matches.get(i)));LinearLayout n=new LinearLayout(this);n.addView(button("上一页",()->{if(page>0){page--;render();enrich(generation,pageScope);}}),new LinearLayout.LayoutParams(0,dp(40),1));n.addView(label((page+1)+" / "+((matches.size()+PAGE_SIZE-1)/PAGE_SIZE),12,MUTED,true),new LinearLayout.LayoutParams(0,dp(40),1));n.addView(button("下一页",()->{if((page+1)*PAGE_SIZE<matches.size()){page++;render();enrich(generation,pageScope);}}),new LinearLayout.LayoutParams(0,dp(40),1));body.addView(n);}
    View matchCard(JSONObject f){LinearLayout c=col();c.setPadding(dp(7),dp(5),dp(7),dp(5));c.setBackground(box(Color.WHITE,10));c.addView(label(f.optString("number")+" · "+f.optString("league"),10,MUTED,false));c.addView(label(f.optString("home")+"  VS  "+f.optString("away"),15,DARK,true));if(market.equals("混合")){
            draw(c,f,"HAD");
            if(mode==GLOBAL){
                draw(c,f,"OU");draw(c,f,"AH");
                c.addView(button("更多玩法：比分 / 总进球 / 半全场 / 单双 / 双方进球",()->chooseExtra(f)));
                c.addView(button("大小球 / 亚洲盘",()->chooseGlobal(f)));
            }else{
                if(mode==JC&&FootballData.market(f,"HHAD").length()>0)draw(c,f,"HHAD");
                draw(c,f,"TTG");
                if(FootballData.market(f,"CRS").length()>0)draw(c,f,"CRS");
                if(FootballData.market(f,"HAFU").length()>0)draw(c,f,"HAFU");
                c.addView(button("更多玩法：比分 / 半全场 / 单双 / 双方进球",()->chooseExtra(f)));
            }
        }else{
            draw(c,f,market);
            if((market.equals("OU")||market.equals("AH")))c.addView(button("录入/更新盘口",()->manualMarket(f,market)));
            else if(FootballData.market(f,market).length()==0)c.addView(button("手工参考录入该玩法赔率",()->manualPick(f,market)));
        }LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(4));c.setLayoutParams(lp);return c;}
    void draw(LinearLayout c,JSONObject f,String m){JSONObject p=FootballData.market(f,m);double line=FootballData.marketLine(f,m);String src=f.optJSONObject("sources")==null?"":f.optJSONObject("sources").optString(m);String suffix=(m.equals("OU")||m.equals("AH"))&&!Double.isNaN(line)?" ("+line+")":"";c.addView(label(FootballData.title(m)+suffix+(src.isEmpty()?"":" · "+src),11,MUTED,true));if(p.length()==0){c.addView(label("暂无可靠赔率",12,MUTED,false));return;}LinearLayout row=new LinearLayout(this);int count=0;for(String l:FootballData.labels(m)){double odd=p.optDouble(l);if(!FootballData.validOdd(odd))continue;TextView b=button(l+"\n"+money(odd),()->toggle(f,m,l,odd));boolean on=picked(f,m,l);b.setTextColor(on?ACCENT:DARK);b.setBackground(box(on?0xffffe0ca:0xfff2f4f7,6));row.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));count++;if(count==3){c.addView(row);row=new LinearLayout(this);count=0;}}if(count>0){while(count++<3)row.addView(new Space(this),new LinearLayout.LayoutParams(0,dp(44),1));c.addView(row);}}
    boolean picked(JSONObject f,String m,String l){JSONObject s=cart.get(f.optString("id"));JSONArray a=s==null?null:s.optJSONArray("picks");for(int i=0;a!=null&&i<a.length();i++){JSONObject p=a.optJSONObject(i);if(m.equals(p.optString("market"))&&l.equals(p.optString("label")))return true;}return false;}
    void toggle(JSONObject f,String m,String l,double odd){try{String id=f.getString("id");JSONObject s=cart.get(id);if(s==null){s=FootballData.copy(f);s.remove("markets");s.put("picks",new JSONArray());cart.put(id,s);}JSONArray a=s.getJSONArray("picks");for(int i=0;i<a.length();i++){JSONObject p=a.getJSONObject(i);if(m.equals(p.optString("market"))&&l.equals(p.optString("label"))){a.remove(i);if(a.length()==0)cart.remove(id);persistCart();render();updateCart();return;}}JSONObject p=new JSONObject().put("market",m).put("label",l).put("odd",odd);JSONObject sources=f.optJSONObject("sources");if(sources!=null)p.put("source",sources.optString(m));if(m.equals("HHAD"))p.put("goalLine",f.optString("goalLine"));if(m.equals("OU")||m.equals("AH"))p.put("line",FootballData.marketLine(f,m));a.put(p);persistCart();render();updateCart();}catch(Exception e){toast("选择失败："+e.getMessage());}}
    void persistCart(){JSONArray a=new JSONArray();for(JSONObject s:cart.values())a.put(s);store.prefs.edit().putString("cart130",a.toString()).apply();}void restoreCart(){try{JSONArray a=new JSONArray(store==null?"[]":store.prefs.getString("cart130","[]"));for(int i=0;i<a.length();i++){JSONObject s=a.getJSONObject(i);cart.put(s.optString("id"),s);}}catch(Exception ignored){}}
    void updateCart(){if(cartBar==null)return;if(mode==RECORDS){cartBar.setText("保存单按锁定赔率本地计算");return;}int picks=0;for(JSONObject s:cart.values())picks+=s.optJSONArray("picks").length();cartBar.setText("已选 "+cart.size()+" 场 · "+picks+" 项");cartBar.setOnClickListener(v->confirm());}

    void chooseGlobal(JSONObject f){new AlertDialog.Builder(this).setTitle("盘口指数学习").setItems(new String[]{"大小球 OU","亚洲让球 AH"},(d,w)->manualMarket(f,w==0?"OU":"AH")).show();}
    void chooseExtra(JSONObject f){
        final String[] ms={"CRS","TTG","HAFU","DS","ODD","BTTS"};
        final String[] names={"比分","总进球","半全场","上下单双","进球单双","双方进球"};
        new AlertDialog.Builder(this).setTitle("更多玩法").setItems(names,(d,w)->{
            market=ms[w];
            if(FootballData.market(f,market).length()==0)manualPick(f,market);
            else render();
        }).show();
    }
    void manualPick(JSONObject f,String m){
        String[] ls=FootballData.labels(m);if(ls.length==0){toast("该玩法暂无可用选项");return;}
        LinearLayout p=col();Spinner pick=new Spinner(this);pick.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ls));
        EditText odd=input("十进制赔率，例如 2.50");EditText src=input("来源，例如 手工参考 / 12BET");
        p.addView(pick);p.addView(odd);p.addView(src);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("录入 "+FootballData.title(m)+" 赔率").setView(p).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(x->{try{
            String label=String.valueOf(pick.getSelectedItem());double o=Double.parseDouble(odd.getText().toString());
            if(!FootballData.validOdd(o))throw new Exception("赔率需大于1且在有效范围内");
            String source=src.getText().toString().trim();if(source.isEmpty())source="手工参考";
            JSONObject prices=FootballData.market(f,m);prices.put(label,o);FootballData.prices(f,m,prices,"手工参考 · "+source);
            store.cache(mode,day,matches);d.dismiss();render();
        }catch(Exception e){toast(e.getMessage());}}));d.show();
    }
    void manualMarket(JSONObject f,String m){String[] ls=FootballData.labels(m);LinearLayout p=col();EditText src=input("来源，例如 澳门 / Bet365 / 12BET");EditText line=input(m.equals("OU")?"盘口，例如 2.5":"主队盘口，例如 -0.25");EditText a=input(ls[0]+" 十进制赔率");EditText b=input(ls[1]+" 十进制赔率");p.addView(src);p.addView(line);p.addView(a);p.addView(b);AlertDialog d=new AlertDialog.Builder(this).setTitle(FootballData.title(m)).setView(p).setPositiveButton("保存",null).setNegativeButton("取消",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(x->{try{double ln=Double.parseDouble(line.getText().toString()),oa=Double.parseDouble(a.getText().toString()),ob=Double.parseDouble(b.getText().toString());if(!MarketMath.quarterLine(ln)||!FootballData.validOdd(oa)||!FootballData.validOdd(ob))throw new Exception("盘口需为0.25整数倍且赔率大于1");String s=src.getText().toString().trim();if(s.isEmpty())throw new Exception("请填写来源");JSONObject q=new JSONObject().put(ls[0],oa).put(ls[1],ob);FootballData.prices(f,m,q,"手工参考 · "+s);FootballData.marketLine(f,m,ln);store.cache(mode,day,matches);d.dismiss();render();}catch(Exception e){toast(e.getMessage());}}));d.show();}
    EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setInputType(InputType.TYPE_CLASS_TEXT);return e;}

    void confirm(){if(cart.isEmpty()){toast("请先选择比赛和玩法");return;}ArrayList<String> passes=new ArrayList<>();passes.add("单关");for(int k=2;k<=Math.min(8,cart.size());k++)passes.add(k+"串1");LinearLayout p=col();Spinner pass=new Spinner(this);pass.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,passes));int defaultPass=cart.size()==1?0:Math.min(7,cart.size()-1);
        pass.setSelection(Math.min(defaultPass,passes.size()-1));
        p.addView(label(cart.size()==1?"已自动选择：单关":cart.size()==2?"已自动选择：2串1":"已按场次数自动选择，可手动改为其他串法",12,MUTED,false));
        p.addView(pass);EditText amount=input("模拟金额");amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);amount.setText(store.prefs.getString("default_stake130","500"));p.addView(amount);AlertDialog d=new AlertDialog.Builder(this).setTitle("确认虚拟保存单").setView(p).setPositiveButton("保存",null).setNegativeButton("返回",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(x->{try{double stake=Double.parseDouble(amount.getText().toString());if(stake<=0)throw new Exception("金额需大于0");int k=pass.getSelectedItemPosition()+1;JSONArray ss=new JSONArray();for(JSONObject s:cart.values())ss.put(FootballData.copy(s));double count=FootballData.count(ss,k);JSONObject t=new JSONObject().put("schema",3).put("id",UUID.randomUUID().toString()).put("created",System.currentTimeMillis()).put("date",FootballData.iso(day)).put("dayKey",day).put("mode",mode==BJ?"北京单场":mode==GLOBAL?"全球指数":"竞彩足球").put("passK",k).put("passType",k==1?"单关":k+"串1").put("stake",stake).put("count",count).put("unitStake",stake/count).put("selections",ss);FootballData.settle(t);store.add(t);store.prefs.edit().putString("default_stake130",amount.getText().toString()).apply();cart.clear();persistCart();d.dismiss();switchMode(RECORDS);toast("已保存；完场比分出现后即可本地结算");}catch(Exception e){toast(e.getMessage());}}));d.show();}

    void showRecords(){if(body==null)return;body.removeAllViews();LinearLayout tools=new LinearLayout(this);tools.addView(button("同步赛果并结算",this::sync),new LinearLayout.LayoutParams(0,dp(44),1));tools.addView(button("按现有比分重算",()->{try{store.recalculate();showRecords();}catch(Exception e){toast(e.getMessage());}}),new LinearLayout.LayoutParams(0,dp(44),1));body.addView(tools);try{JSONArray a=store.read();double stake=0,settledStake=0,ret=0;for(int i=0;i<a.length();i++){JSONObject t=a.getJSONObject(i);stake+=t.optDouble("stake");if(t.optBoolean("settled")){settledStake+=t.optDouble("stake");ret+=t.optDouble("prize");}}body.addView(label("累计投入 ¥"+money(stake)+" · 已结算投入 ¥"+money(settledStake)+"\n回报 ¥"+money(ret)+" · 净收益 ¥"+money(ret-settledStake)+" · 收益率 "+money(settledStake==0?0:(ret-settledStake)/settledStake*100)+"%",14,DARK,true));for(int i=a.length()-1;i>=0;i--)body.addView(ticketCard(a.getJSONObject(i)));info.setText(syncing?"正在读取公开完场比分并即时计算":"完场后可自动同步；也可手工录入比分自行结算");}catch(Exception e){info.setText("记录读取失败，原数据仍保留");}}
    View ticketCard(JSONObject t)throws JSONException{LinearLayout c=col();c.setPadding(dp(10),dp(10),dp(10),dp(10));c.setBackground(box(Color.WHITE,12));c.addView(label(t.optString("date")+" · "+t.optString("mode")+" · "+t.optString("passType"),12,MUTED,false));String roi=t.optBoolean("settled")?money(t.optDouble("roi"))+"%":"--";
        c.addView(label(t.optString("status")+" · 投入 ¥"+money(t.optDouble("stake"))+" · 回报 ¥"+money(t.optDouble("prize"))+" · 净收益 ¥"+money(t.optDouble("net"))+" · 收益率 "+roi,14,DARK,true));JSONArray ss=t.optJSONArray("selections");for(int i=0;ss!=null&&i<ss.length();i++){final int ix=i;JSONObject s=ss.getJSONObject(i);c.addView(label(s.optString("number")+" "+s.optString("home")+" - "+s.optString("away")+" · "+s.optString("resultScore","待同步"),13,BLUE,true));JSONArray ps=s.optJSONArray("picks");StringBuilder q=new StringBuilder();for(int j=0;ps!=null&&j<ps.length();j++){JSONObject p=ps.getJSONObject(j);if(q.length()>0)q.append("；");q.append(FootballData.title(p.optString("market"))).append(" ").append(p.optString("label")).append(" @").append(money(p.optDouble("odd"))).append(" [").append(p.optString("check","待核验")).append("]");}c.addView(label(q.toString(),12,DARK,false));c.addView(button("录入/修正该场比分",()->manualResult(t.optString("id"),ix)));}LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(8));c.setLayoutParams(lp);return c;}
    void manualResult(String id,int ix){try{JSONObject t=store.get(id);JSONObject s=t.getJSONArray("selections").getJSONObject(ix);LinearLayout p=col();EditText ft=input("全场比分，例如 2:1");EditText ht=input("半场比分，可留空");p.addView(ft);p.addView(ht);AlertDialog d=new AlertDialog.Builder(this).setTitle(s.optString("home")+" - "+s.optString("away")).setView(p).setPositiveButton("保存并计算",null).setNegativeButton("取消",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(x->{try{int[] full=FootballData.score(ft.getText().toString()),half=FootballData.score(ht.getText().toString());if(full==null)throw new Exception("全场比分格式应为 2:1");if(!ht.getText().toString().trim().isEmpty()&&half==null)throw new Exception("半场比分格式应为 0:0");JSONObject r=FootballData.result(s,full,half,"用户录入比分",true);r.put("manual",true);store.manual(id,ix,r);d.dismiss();showRecords();}catch(Exception e){toast(e.getMessage());}}));d.show();}catch(Exception e){toast("记录读取失败");}}
    void sync(){if(syncing||destroyed)return;syncing=true;syncScope=new DataClient.Scope();final DataClient.Scope scope=syncScope;if(mode==RECORDS)info.setText("正在同步完场比分…");resultIo.execute(()->{String result=store.sync(scope,null);ui.post(()->{if(destroyed)return;syncing=false;if(mode==RECORDS){showRecords();info.setText(result);}});});}

    void backup(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"football-tickets-"+day+".json");startActivityForResult(i,101);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req!=101||res!=RESULT_OK||data==null||data.getData()==null)return;resultIo.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException("文件未打开");out.write(store.backup().toString(2).getBytes("UTF-8"));ui.post(()->toast("备份已导出"));}catch(Exception e){ui.post(()->toast("导出失败："+e.getMessage()));}});}
}
