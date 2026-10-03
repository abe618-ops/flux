
package com.quicklottery.blindbacktest;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.*;
import android.content.res.AssetManager;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.io.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    private static final String[] GAMES = {"快乐8", "福彩3D", "排列3", "排列5"};
    private static final String[] FILES = {"kl8_desc.txt", "3d_desc.txt", "pl3_desc.txt", "pl5_desc.txt"};
    private static final int[] NEED = {20, 3, 3, 5};

    private LinearLayout root, dynamicBetBox;
    private Spinner gameSpinner, modeSpinner;
    private TextView issueTv, hiddenTv, statusTv, statsTv, dataTv, rulesTv;
    private EditText searchEt, numbersEt, multipleEt;
    private Button lockBtn, revealBtn, prevBtn, nextBtn, randomDrawBtn, randomBetBtn, searchBtn;

    private final ArrayList<Draw> draws = new ArrayList<>();
    private final HashMap<String, HashMap<String, Double>> kl8PrizeMap = new HashMap<>();
    private String dataManifest = "";
    private int gameIndex = 0;
    private int currentIndex = 0;
    private Ticket lockedTicket = null;
    private boolean revealed = false;

    private int sessionCount = 0;
    private double totalCost = 0;
    private double totalReturn = 0;
    private int unresolvedFloating = 0;
    private double settledCost=0;
    private JSONArray ledger=new JSONArray();
    private TextView ledgerTv;
    private int activeRecord=-1;
    private final DecimalFormat moneyFmt = new DecimalFormat("0.##");

    static class Draw {
        String issue;
        String date;
        int[] nums;
        Draw(String issue, String date, int[] nums) { this.issue=issue; this.date=date; this.nums=nums; }
    }
    static class Ticket {
        int game;
        int mode; // KL8: 1..10, digit games: 0 direct, 1 group3, 2 group6
        int[] nums;
        int multiple;
        double cost;
    }
    static class Prize {
        boolean win;
        boolean floating;
        double amountPerBet;
        String label;
        Prize(boolean w, boolean f, double a, String l) { win=w; floating=f; amountPerBet=a; label=l; }
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        restoreStats();
        buildUi();
        loadBuildMetadata();
        loadGame(0);
    }

    private int dp(int n){ return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView tv(String text, int sp, int color, boolean bold){
        TextView v=new TextView(this); v.setText(text); v.setTextSize(sp); v.setTextColor(color);
        v.setPadding(dp(2),dp(5),dp(2),dp(5)); if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return v;
    }
    private Button btn(String text){
        Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(15); return b;
    }
    private LinearLayout row(){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    private void addWeight(View v, LinearLayout parent, float w){
        parent.addView(v,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,w));
    }
    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14),dp(10),dp(14),dp(10));
        GradientDrawableCompat.setBackground(c, Color.WHITE, dp(10), 0xFFE0E0E0);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(dp(10),dp(7),dp(10),dp(7)); c.setLayoutParams(p); return c;
    }

    private void buildUi(){
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFFF5F5F5);
        sv.addView(root,new ScrollView.LayoutParams(-1,-2));

        TextView title=tv("四彩 · 历史盲投回测",24,Color.WHITE,true);
        title.setPadding(dp(18),dp(18),dp(18),dp(6));
        TextView sub=tv("先锁定模拟投注，再揭晓真实历史开奖",13,0xFFFFEBEE,false);
        sub.setPadding(dp(18),0,dp(18),dp(14));
        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.VERTICAL); head.setBackgroundColor(0xFFB71C1C);
        head.addView(title); head.addView(sub); root.addView(head);

        LinearLayout gameCard=card();
        gameCard.addView(tv("① 选择彩种与历史期次",18,0xFF222222,true));
        gameSpinner=new Spinner(this); gameSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,GAMES));
        gameCard.addView(gameSpinner);
        issueTv=tv("",18,0xFFB71C1C,true); gameCard.addView(issueTv);
        hiddenTv=tv("开奖号码：••••••",20,0xFF333333,true); gameCard.addView(hiddenTv);
        LinearLayout nav=row();
        prevBtn=btn("上一期"); nextBtn=btn("下一期"); randomDrawBtn=btn("随机一期");
        addWeight(prevBtn,nav,1); addWeight(nextBtn,nav,1); addWeight(randomDrawBtn,nav,1); gameCard.addView(nav);
        LinearLayout sr=row(); searchEt=new EditText(this); searchEt.setHint("期号或日期，如 20260131"); searchEt.setSingleLine();
        searchBtn=btn("查找"); addWeight(searchEt,sr,2.2f); addWeight(searchBtn,sr,1); gameCard.addView(sr);
        dataTv=tv("",12,0xFF666666,false); gameCard.addView(dataTv); root.addView(gameCard);

        LinearLayout betCard=card();
        betCard.addView(tv("② 先做模拟投注",18,0xFF222222,true));
        dynamicBetBox=new LinearLayout(this); dynamicBetBox.setOrientation(LinearLayout.VERTICAL); betCard.addView(dynamicBetBox);
        LinearLayout br=row(); randomBetBtn=btn("机选"); lockBtn=btn("锁定模拟投注");
        addWeight(randomBetBtn,br,1); addWeight(lockBtn,br,1.7f); betCard.addView(br);
        root.addView(betCard);

        LinearLayout revealCard=card();
        revealCard.addView(tv("③ 一键揭晓并判奖",18,0xFF222222,true));
        revealBtn=btn("查看是否中奖"); revealBtn.setEnabled(false); revealCard.addView(revealBtn);
        statusTv=tv("先完成上方模拟投注。",16,0xFF444444,false); revealCard.addView(statusTv);
        root.addView(revealCard);

        LinearLayout statCard=card();
        statCard.addView(tv("回测统计",18,0xFF222222,true));
        statsTv=tv("",16,0xFF222222,false); statCard.addView(statsTv);
        Button resume=btn("继续未揭晓投注");resume.setOnClickListener(v->continuePending());statCard.addView(resume);
        root.addView(statCard);
        LinearLayout records=card(); records.addView(tv("已保存投注记录",18,0xFF222222,true));
        ledgerTv=tv("",12,0xFF555555,false); records.addView(ledgerTv); root.addView(records);
        showLedger();

        LinearLayout ruleCard=card();
        ruleCard.addView(tv("规则口径",18,0xFF222222,true));
        rulesTv=tv("",13,0xFF555555,false); ruleCard.addView(rulesTv);
        ruleCard.addView(tv("仅用于历史数据学习与回测，不接入真实购买、支付或下单。",12,0xFF777777,false));
        root.addView(ruleCard);

        sv.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(sv);
        updateStats();

        gameSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id){ if(pos!=gameIndex || draws.isEmpty()) loadGame(pos); }
        });
        prevBtn.setOnClickListener(v->{ if(!draws.isEmpty()){ currentIndex=Math.min(draws.size()-1,currentIndex+1); setDraw(); }});
        nextBtn.setOnClickListener(v->{ if(!draws.isEmpty()){ currentIndex=Math.max(0,currentIndex-1); setDraw(); }});
        randomDrawBtn.setOnClickListener(v->{ if(!draws.isEmpty()){ currentIndex=new Random().nextInt(draws.size()); setDraw(); }});
        searchBtn.setOnClickListener(v->searchDraw());
        randomBetBtn.setOnClickListener(v->randomBet());
        lockBtn.setOnClickListener(v->lockTicket());
        revealBtn.setOnClickListener(v->reveal());
    }

    private void buildBetControls(){
        dynamicBetBox.removeAllViews();
        modeSpinner=new Spinner(this);
        if(gameIndex==0){
            String[] a=new String[10]; for(int i=0;i<10;i++) a[i]="选"+cn(i+1)+"（"+(i+1)+"个号码）";
            modeSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,a));
        } else if(gameIndex==3) {
            modeSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"直选"}));
        } else {
            modeSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"直选/单选","组选3","组选6"}));
        }
        dynamicBetBox.addView(modeSpinner);
        numbersEt=new EditText(this);
        numbersEt.setSingleLine(false);
        numbersEt.setMinLines(2);
        numbersEt.setHint(gameIndex==0 ? "输入号码，如 03 17 28（数量按玩法）" : (gameIndex==3 ? "输入5位号码，如 01357" : "输入3位号码，如 012"));
        dynamicBetBox.addView(numbersEt);
        LinearLayout mr=row(); mr.addView(tv("倍数：",15,0xFF333333,false));
        multipleEt=new EditText(this); multipleEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); multipleEt.setText("1"); multipleEt.setSingleLine();
        mr.addView(multipleEt,new LinearLayout.LayoutParams(dp(100),-2)); dynamicBetBox.addView(mr);
    }

    private void loadGame(int idx){
        gameIndex=idx; draws.clear(); currentIndex=0; lockedTicket=null; revealed=false;
        buildBetControls();
        try{
            String raw=readAsset(FILES[idx]);
            parseHistory(raw, idx);
        }catch(Exception e){
            statusTv.setText("历史数据载入失败："+e.getMessage());
        }
        if(draws.isEmpty()){
            issueTv.setText(GAMES[idx]+"：暂无历史快照");
            hiddenTv.setText("开奖号码：—");
            dataTv.setText("构建时数据快照缺失。请重新运行 GitHub Actions 构建。");
        }else{
            Collections.sort(draws,(a,b)->{
                int c=b.date.compareTo(a.date); return c!=0?c:b.issue.compareTo(a.issue);
            });
            setDraw();
            dataTv.setText("官方公告核对："+draws.size()+"期；"+draws.get(draws.size()-1).date+" 至 "+draws.get(0).date+"\n仅覆盖已收录期次，未收录日期不补造开奖结果。离线快照，不自动联网更新。");
        }
        setRules();
    }


    private void loadBuildMetadata(){
        try{ dataManifest=readAsset("data_manifest.txt").trim(); }catch(Exception ignored){}
        try{
            String raw=readAsset("kl8_prizes.tsv");
            for(String line:raw.split("\\r?\\n")){
                line=line.trim(); if(line.length()==0||line.startsWith("#"))continue;
                String[] a=line.split("\\t"); if(a.length<3)continue;
                String issue=a[0].trim(), type=a[1].trim().toLowerCase(Locale.US);
                double money=Double.parseDouble(a[2].trim());
                HashMap<String,Double> m=kl8PrizeMap.get(issue);
                if(m==null){m=new HashMap<>();kl8PrizeMap.put(issue,m);}
                m.put(type,money);
            }
        }catch(Exception e){ kl8PrizeMap.clear(); }
    }

    private String readAsset(String name) throws IOException{
        InputStream in=getAssets().open(name); ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0) out.write(buf,0,n); in.close();
        return out.toString("UTF-8");
    }

    private void parseHistory(String raw, int game){
        HashSet<String> seen=new HashSet<>();
        String[] lines=raw.split("\\r?\\n");
        for(String line:lines){
            Draw d=parseLine(line, game);
            if(d!=null && seen.add(d.issue+"|"+d.date)) draws.add(d);
        }
    }

    private Draw parseLine(String line, int game){
        line=line.trim(); if(line.length()<5) return null;
        String date="";
        Matcher dm=Pattern.compile("(20\\d{2})[-/年\\.](\\d{1,2})[-/月\\.](\\d{1,2})").matcher(line);
        if(dm.find()) date=String.format(Locale.US,"%s-%02d-%02d",dm.group(1),Integer.parseInt(dm.group(2)),Integer.parseInt(dm.group(3)));
        if(date.length()==0){
            Matcher d8=Pattern.compile("(?<!\\d)(20\\d{6})(?!\\d)").matcher(line);
            if(d8.find()){
                String s=d8.group(1); date=s.substring(0,4)+"-"+s.substring(4,6)+"-"+s.substring(6,8);
            }
        }
        String clean=line.replaceAll("[,，;；|\\t]+"," ");
        String[] tok=clean.split("\\s+");
        String issue="";
        int issuePos=-1;
        for(int i=0;i<Math.min(tok.length,5);i++){
            String t=tok[i].replaceAll("\\D","");
            if(t.matches("\\d{5,9}") && !(t.length()==8 && t.startsWith("20"))){ issue=t; issuePos=i; break; }
        }
        if(issue.length()==0){
            Matcher im=Pattern.compile("(?<!\\d)(\\d{5,9})(?!\\d)").matcher(line);
            while(im.find()){
                String s=im.group(1);
                if(!(s.length()==8 && s.startsWith("20"))){ issue=s; break; }
            }
        }
        if(issue.length()==0 || date.length()==0) return null;

        int need=NEED[game];
        ArrayList<Integer> vals=new ArrayList<>();
        boolean passedIssue=false, passedDate=false;
        for(int i=0;i<tok.length;i++){
            String t=tok[i].trim();
            if(t.equals(issue) || (issuePos==i)){ passedIssue=true; continue; }
            if(t.matches("20\\d{2}[-/年\\.]\\d{1,2}[-/月\\.]\\d{1,2}.*") || t.equals(date.replace("-",""))){ passedDate=true; continue; }
            if(!passedIssue) continue;
            // Most source files place date before the draw numbers. If date is embedded with weekday, allow after issue too.
            String digits=t.replaceAll("\\D","");
            if(game>0 && digits.length()==need && t.matches("\\d+")){
                vals.clear();
                for(char c:digits.toCharArray()) vals.add(c-'0');
                break;
            }
            if(t.matches("\\d{1,2}")){
                int x=Integer.parseInt(t);
                if(game==0){
                    if(x>=1 && x<=80) vals.add(x);
                }else{
                    if(x>=0 && x<=9) vals.add(x);
                }
                if(vals.size()==need) break;
            }
        }
        if(vals.size()<need){
            // fallback: scan substring after the detected date, then take first plausible values
            int p=line.indexOf(date.substring(0,4));
            String sub=p>=0?line.substring(p):line;
            Matcher nm=Pattern.compile("(?<!\\d)(\\d{1,2})(?!\\d)").matcher(sub);
            vals.clear();
            while(nm.find() && vals.size()<need){
                int x=Integer.parseInt(nm.group(1));
                if(game==0 && x>=1 && x<=80) vals.add(x);
                else if(game>0 && x>=0 && x<=9) vals.add(x);
            }
        }
        if(vals.size()!=need) return null;
        if(game==0 && new HashSet<Integer>(vals).size()!=20) return null;
        int[] nums=new int[need]; for(int i=0;i<need;i++) nums[i]=vals.get(i);
        return new Draw(issue,date,nums);
    }

    private void setDraw(){
        activeRecord=-1; lockedTicket=null; revealed=false; revealBtn.setEnabled(false); lockBtn.setEnabled(true);
        if(draws.isEmpty()) return;
        Draw d=draws.get(currentIndex);
        issueTv.setText(GAMES[gameIndex]+" · 第"+d.issue+"期 · "+d.date);
        hiddenTv.setText("开奖号码：已隐藏");
        statusTv.setText("请先选择玩法和号码，然后锁定模拟投注。");
        if(numbersEt!=null) numbersEt.setText("");
    }

    private void searchDraw(){
        String q=searchEt.getText().toString().trim().replace("年","-").replace("月","-").replace("日","");
        String d=q.replaceAll("[^0-9]","");
        for(int i=0;i<draws.size();i++){
            Draw x=draws.get(i);
            String dd=x.date.replace("-","");
            if(x.issue.equals(d) || x.issue.equals(q) || dd.equals(d) || x.date.equals(q)){
                currentIndex=i; setDraw(); hideKeyboard(); return;
            }
        }
        Toast.makeText(this,"未找到对应期号/日期",Toast.LENGTH_SHORT).show();
    }

    private void randomBet(){
        Random r=new Random();
        if(gameIndex==0){
            int n=modeSpinner.getSelectedItemPosition()+1;
            TreeSet<Integer> s=new TreeSet<>(); while(s.size()<n) s.add(1+r.nextInt(80));
            StringBuilder b=new StringBuilder(); for(int x:s){ if(b.length()>0)b.append(" "); b.append(String.format(Locale.US,"%02d",x)); }
            numbersEt.setText(b.toString());
        }else{
            int n=gameIndex==3?5:3;
            StringBuilder b=new StringBuilder();
            int mode=modeSpinner.getSelectedItemPosition();
            if(gameIndex!=3 && mode==1){
                int a=r.nextInt(10), c; do{c=r.nextInt(10);}while(c==a);
                int[] z={a,a,c}; shuffle(z,r); for(int x:z)b.append(x);
            } else if(gameIndex!=3 && mode==2){
                ArrayList<Integer> z=new ArrayList<>(); while(z.size()<3){int x=r.nextInt(10);if(!z.contains(x))z.add(x);}
                Collections.shuffle(z,r); for(int x:z)b.append(x);
            } else {
                for(int i=0;i<n;i++) b.append(r.nextInt(10));
            }
            numbersEt.setText(b.toString());
        }
    }

    private void shuffle(int[] a,Random r){ for(int i=a.length-1;i>0;i--){int j=r.nextInt(i+1),t=a[i];a[i]=a[j];a[j]=t;} }

    private void lockTicket(){
        if(draws.isEmpty()) return;
        try{
            int mult=Integer.parseInt(multipleEt.getText().toString().trim());
            int max=gameIndex==0?15:99;
            if(mult<1 || mult>max) throw new IllegalArgumentException("倍数须为1-"+max);
            Ticket t=new Ticket(); t.game=gameIndex; t.multiple=mult; t.cost=2.0*mult;
            if(gameIndex==0){
                int n=modeSpinner.getSelectedItemPosition()+1; t.mode=n;
                t.nums=parseHappy(numbersEt.getText().toString(),n);
            }else{
                int n=gameIndex==3?5:3; t.mode=modeSpinner.getSelectedItemPosition();
                t.nums=parseDigits(numbersEt.getText().toString(),n);
                if(gameIndex!=3 && t.mode==1 && pattern(t.nums)!=2) throw new IllegalArgumentException("组选3必须恰有两个相同数字");
                if(gameIndex!=3 && t.mode==2 && pattern(t.nums)!=3) throw new IllegalArgumentException("组选6必须三个数字各不相同");
            }
            lockedTicket=t; revealed=false; revealBtn.setEnabled(true); lockBtn.setEnabled(false);
            sessionCount++; totalCost+=t.cost;
            Draw selected=draws.get(currentIndex); JSONObject item=new JSONObject();
            item.put("game",GAMES[t.game]);item.put("issue",selected.issue);item.put("date",selected.date);
            item.put("nums",numbersEt.getText().toString());item.put("mode",t.mode);item.put("multiple",t.multiple);
            item.put("cost",t.cost);item.put("status","已锁定，尚未揭晓");
            ledger.put(item);activeRecord=ledger.length()-1;saveStats();showLedger();
            statusTv.setText("已锁定：¥"+moneyFmt.format(t.cost)+"。现在可点击“查看是否中奖”。");
            updateStats(); hideKeyboard();
        }catch(Exception e){ Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
    }

    private int[] parseHappy(String s,int n){
        Matcher m=Pattern.compile("\\d{1,2}").matcher(s);
        ArrayList<Integer> a=new ArrayList<>(); HashSet<Integer> seen=new HashSet<>();
        while(m.find()){ int x=Integer.parseInt(m.group()); if(x<1||x>80) throw new IllegalArgumentException("快乐8号码须为01-80"); if(!seen.add(x)) throw new IllegalArgumentException("号码不可重复"); a.add(x); }
        if(a.size()!=n) throw new IllegalArgumentException("当前玩法必须选择"+n+"个号码");
        int[] r=new int[n]; for(int i=0;i<n;i++)r[i]=a.get(i); return r;
    }
    private int[] parseDigits(String s,int n){
        String d=s.replaceAll("\\D","");
        if(d.length()!=n) throw new IllegalArgumentException("请输入恰好"+n+"位数字");
        int[] r=new int[n]; for(int i=0;i<n;i++)r[i]=d.charAt(i)-'0'; return r;
    }
    private int pattern(int[] a){
        HashSet<Integer> s=new HashSet<>(); for(int x:a)s.add(x); return s.size()==1?1:(s.size()==2?2:3);
    }

    private void reveal(){
        if(lockedTicket==null || revealed || draws.isEmpty()) return;
        revealed=true; Draw d=draws.get(currentIndex);
        hiddenTv.setText("开奖号码："+formatDraw(d));
        Prize p=settle(lockedTicket,d);
        if(p.floating){
            unresolvedFloating++;
            statusTv.setText(p.label+"：缺少可适用的该期浮动奖金额；返奖待核算，该笔投入与返奖均排除在已结算ROI之外。");
        }else{
            double ret=p.amountPerBet*lockedTicket.multiple;
            totalReturn+=ret; settledCost+=lockedTicket.cost;
            if(p.win) statusTv.setText("中奖："+p.label+"，本次模拟返奖 ¥"+moneyFmt.format(ret));
            else statusTv.setText("未中奖。本次模拟返奖 ¥0");
        }
        try{JSONObject item=ledger.getJSONObject(activeRecord);item.put("status",p.floating?"奖金待核算":(p.win?"已中奖":"未中奖"));item.put("result",formatDraw(d));if(!p.floating)item.put("return",p.amountPerBet*lockedTicket.multiple);}catch(Exception ignored){}
        saveStats();showLedger();revealBtn.setEnabled(false); updateStats();
    }

    private String formatDraw(Draw d){
        if(gameIndex==0){
            StringBuilder b=new StringBuilder(); for(int x:d.nums){if(b.length()>0)b.append(" ");b.append(String.format(Locale.US,"%02d",x));} return b.toString();
        }
        StringBuilder b=new StringBuilder(); for(int x:d.nums)b.append(x); return b.toString();
    }

    private Prize settle(Ticket t,Draw d){
        if(t.game==0) return settleHappy(t,d);
        if(t.game==3){
            boolean ok=Arrays.equals(t.nums,d.nums); return new Prize(ok,false,ok?100000:0,ok?"一等奖":"未中奖");
        }
        if(t.mode==0){
            boolean ok=Arrays.equals(t.nums,d.nums); return new Prize(ok,false,ok?1040:0,ok?(t.game==1?"单选":"直选"):"未中奖");
        }
        int pt=pattern(d.nums);
        int[] a=t.nums.clone(), b=d.nums.clone(); Arrays.sort(a); Arrays.sort(b);
        boolean same=Arrays.equals(a,b);
        if(t.mode==1){boolean ok=pt==2&&same;return new Prize(ok,false,ok?346:0,ok?"组选3":"未中奖");}
        boolean ok=pt==3&&same;return new Prize(ok,false,ok?173:0,ok?"组选6":"未中奖");
    }

    private Prize settleHappy(Ticket t,Draw d){
        HashSet<Integer> win=new HashSet<>(); for(int x:d.nums)win.add(x);
        int hit=0; for(int x:t.nums)if(win.contains(x))hit++;
        int n=t.mode;
        String key="x"+n+"z"+hit;
        HashMap<String,Double> drawPrizes=kl8PrizeMap.get(d.issue);
        Double amount=drawPrizes==null?null:drawPrizes.get(key);
        String label="选"+cn(n)+(hit==0?"全不中":"中"+cn(hit));
        if((n==9||n==10)&&hit==n&&(amount==null||amount<=0)) return new Prize(true,true,0,label+"（浮动奖）");
        if(amount==null) return new Prize(false,true,0,"该期奖表缺失，暂停结算");
        if(amount<=0){
            return new Prize(false,false,0,"未中奖");
        }
        return new Prize(true,false,amount,label);
    }


    private void restoreStats(){
        android.content.SharedPreferences p=getSharedPreferences("history_v11",MODE_PRIVATE);
        sessionCount=p.getInt("count",0);totalCost=Double.longBitsToDouble(p.getLong("cost",0));totalReturn=Double.longBitsToDouble(p.getLong("return",0));settledCost=Double.longBitsToDouble(p.getLong("settled",0));unresolvedFloating=p.getInt("unresolved",0);
        try{ledger=new JSONArray(p.getString("ledger","[]"));}catch(Exception e){ledger=new JSONArray();}
    }
    private void saveStats(){
        getSharedPreferences("history_v11",MODE_PRIVATE).edit().putInt("count",sessionCount).putLong("cost",Double.doubleToLongBits(totalCost)).putLong("return",Double.doubleToLongBits(totalReturn)).putLong("settled",Double.doubleToLongBits(settledCost)).putInt("unresolved",unresolvedFloating).putString("ledger",ledger.toString()).commit();
    }
    private void showLedger(){
        if(ledgerTv==null)return;StringBuilder b=new StringBuilder();
        for(int i=ledger.length()-1;i>=Math.max(0,ledger.length()-30);i--){JSONObject x=ledger.optJSONObject(i);if(x==null)continue;b.append(x.optString("game")).append(" ").append(x.optString("issue")).append(" ").append(x.optString("date")).append("\n").append(x.optString("nums")).append(" ×").append(x.optInt("multiple")).append("；投入¥").append(x.optDouble("cost")).append("；").append(x.optString("status"));if(x.has("return"))b.append("，返奖¥").append(x.optDouble("return"));b.append("\n\n");}
        ledgerTv.setText(b.length()==0?"暂无记录；锁定投注后自动保存。":b.toString());
    }

    private void continuePending(){
        for(int i=ledger.length()-1;i>=0;i--){
            JSONObject x=ledger.optJSONObject(i);if(x==null||!x.optString("status").equals("已锁定，尚未揭晓"))continue;
            int g=Arrays.asList(GAMES).indexOf(x.optString("game"));if(g<0)continue;
            gameSpinner.setSelection(g);loadGame(g);
            for(int j=0;j<draws.size();j++)if(draws.get(j).issue.equals(x.optString("issue"))){
                currentIndex=j;setDraw();Ticket t=new Ticket();t.game=g;t.mode=x.optInt("mode");t.multiple=x.optInt("multiple");t.cost=x.optDouble("cost");
                try{t.nums=g==0?parseHappy(x.optString("nums"),t.mode):parseDigits(x.optString("nums"),g==3?5:3);}catch(Exception e){continue;}
                lockedTicket=t;activeRecord=i;revealed=false;revealBtn.setEnabled(true);lockBtn.setEnabled(false);
                numbersEt.setText(x.optString("nums"));multipleEt.setText(String.valueOf(t.multiple));statusTv.setText("已恢复锁定投注，点击查看是否中奖即可结算。");return;
            }
        }
        Toast.makeText(this,"没有可继续的未揭晓投注",Toast.LENGTH_SHORT).show();
    }

    private void updateStats(){
        double net=totalReturn-settledCost;
        String roi=settledCost>0?new DecimalFormat("0.00").format(net/settledCost*100)+"%":"—";
        String s="模拟票数："+sessionCount+"  ·  累计投入：¥"+moneyFmt.format(totalCost)+"\n"
                +"已结算投入：¥"+moneyFmt.format(settledCost)+"\n"
                +"已核算返奖：¥"+moneyFmt.format(totalReturn)+"  ·  净收益：¥"+moneyFmt.format(net)+"\n"
                +"已结算ROI："+roi+"\n未结算投入：¥"+moneyFmt.format(totalCost-settledCost);
        if(unresolvedFloating>0) s+="\n浮动奖待当期开奖公告核算："+unresolvedFloating+"笔（投入与返奖均未计入已结算ROI）";
        statsTv.setText(s);
    }

    private void setRules(){
        if(gameIndex==0){
            rulesTv.setText("快乐8：01-80中选择1至10个号码，每注2元；每期摇出20个号码。当前收录期次均适用2025-12-30起的新规则。固定奖按官方规则核算；选九中九与选十中十为浮动奖，缺少当期可适用金额时仅记录命中并等待核算。公告中无人中奖时的0元不作为模拟中奖奖金。");
        }else if(gameIndex==1){
            rulesTv.setText("福彩3D：000-999，单选/组选3/组选6每注2元；本工具首版支持这三种核心玩法。固定奖：单选1040元、组选3为346元、组选6为173元。");
        }else if(gameIndex==2){
            rulesTv.setText("排列3：000-999，每注2元；直选1040元、组选3为346元、组选6为173元。排列3开奖号码取同期排列5开奖号码前三位。");
        }else{
            rulesTv.setText("排列5：00000-99999，每注2元；本工具按直选结算，5位号码与开奖号码顺序完全一致中一等奖，单注固定奖金100000元。");
        }
    }

    private String cn(int n){
        String[] c={"零","一","二","三","四","五","六","七","八","九","十"}; return n>=0&&n<c.length?c[n]:String.valueOf(n);
    }
    private void hideKeyboard(){
        View v=getCurrentFocus(); if(v!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);
    }

    // Small helper avoids requiring AndroidX/CardView.
    static class GradientDrawableCompat {
        static void setBackground(View v,int fill,int radius,int stroke){
            android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();
            g.setColor(fill);g.setCornerRadius(radius);g.setStroke(1,stroke);v.setBackground(g);
        }
    }
}
