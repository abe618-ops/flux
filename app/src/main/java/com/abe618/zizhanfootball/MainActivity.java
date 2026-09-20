package com.abe618.zizhanfootball;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainActivity extends Activity {
    private final Map<String,String> lingQiTitles = new HashMap<>();
    private EditText matchInput, seedInput;
    private TextView consensusText, lingqiText, wenwangText, ziweiText;
    private GridLayout ziweiGrid;

    private static final String[] BRANCHES = {"寅","卯","辰","巳","午","未","申","酉","戌","亥","子","丑"};
    private static final String[] PALACES = {"命宫","兄弟","夫妻","子女","财帛","疾厄","迁移","交友","官禄","田宅","福德","父母"};
    private static final String[] ZIWEI_GROUP = {"紫微","天机","","太阳","武曲","天同","","","廉贞"};
    private static final String[] TIANFU_GROUP = {"天府","太阴","贪狼","巨门","天相","天梁","七杀","","","","破军"};

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        loadLingQi();
        buildUi();
    }

    private int dp(int n){ return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    private void buildUi(){
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(18),dp(14),dp(32));
        sv.addView(root);

        TextView title = text("紫占 · 灵棋经 · 文王八卦\n足球三术合参", 24, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(-1,-2,0,8));
        TextView sub = text("从上传 APK 反编译确认排盘结构后重构；随机盘可用固定种子复现。仅用于传统术数实验、盲测与复盘。", 13, false);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, lp(-1,-2,0,14));

        matchInput = new EditText(this);
        matchInput.setHint("比赛名称，例如：主队 vs 客队");
        matchInput.setSingleLine(true);
        root.addView(matchInput, lp(-1,-2,0,6));
        seedInput = new EditText(this);
        seedInput.setHint("可选：固定种子/三位数（留空则真随机）");
        seedInput.setSingleLine(true);
        root.addView(seedInput, lp(-1,-2,0,10));

        Button run = new Button(this);
        run.setText("一键起盘并合参");
        run.setTextSize(18);
        run.setOnClickListener(v -> runAll());
        root.addView(run, lp(-1,dp(58),0,12));

        consensusText = card(root, "合参结果", 18);
        lingqiText = card(root, "① 灵棋经 · 十二棋", 16);
        wenwangText = card(root, "② 文王八卦 · 三钱六爻", 16);

        TextView zt = text("③ 紫占 · 随机紫微十二宫", 17, true);
        root.addView(zt, lp(-1,-2,0,6));
        ziweiGrid = new GridLayout(this);
        ziweiGrid.setColumnCount(4);
        ziweiGrid.setRowCount(4);
        root.addView(ziweiGrid, lp(-1,-2,0,6));
        ziweiText = card(root, "紫占判读", 15);

        TextView footer = text("说明：原 APK 的灵棋经随机入口为三组 0–4 计数；文王八卦自动入口返回本卦/变卦；紫微随机入口使用“11|suiji”进入完整盘面。本版不复制闭源 native 库，而按已确认的数据结构重新实现。",12,false);
        root.addView(footer, lp(-1,-2,0,0));
        setContentView(sv);
        runAll();
    }

    private TextView card(LinearLayout root, String heading, int size){
        TextView h=text(heading,17,true);
        root.addView(h,lp(-1,-2,0,4));
        TextView t=text("尚未起盘",size,false);
        t.setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable gd=new GradientDrawable();
        gd.setColor(0xfff7f7f7);
        gd.setCornerRadius(dp(10));
        gd.setStroke(dp(1),0xffd0d0d0);
        t.setBackground(gd);
        root.addView(t,lp(-1,-2,0,14));
        return t;
    }

    private TextView text(String s,int sp,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(32,32,32));
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w,int h,int l,int b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);
        p.setMargins(0,dp(l),0,dp(b));
        return p;
    }

    private Random rng(){
        String seed=seedInput==null?"":seedInput.getText().toString().trim();
        String match=matchInput==null?"":matchInput.getText().toString().trim();
        if(seed.isEmpty()) return new SecureRandom();
        try{
            byte[] h=MessageDigest.getInstance("SHA-256").digest((seed+"|"+match).getBytes(StandardCharsets.UTF_8));
            long x=0;
            for(int i=0;i<8;i++) x=(x<<8)|(h[i]&255L);
            return new Random(x);
        }catch(Exception e){
            return new Random((seed+match).hashCode());
        }
    }

    private void runAll(){
        Random r=rng();
        LingResult l=castLing(r);
        WenResult w=castWen(r);
        ZiResult z=castZi(r);
        lingqiText.setText(l.display);
        wenwangText.setText(w.display);
        ziweiText.setText(z.display);
        renderZiwei(z);
        renderConsensus(l,w,z);
    }

    private LingResult castLing(Random r){
        boolean[][] pieces=new boolean[3][4];
        int[] c=new int[3];
        for(int g=0;g<3;g++){
            for(int i=0;i<4;i++){
                pieces[g][i]=r.nextBoolean();
                if(pieces[g][i])c[g]++;
            }
        }
        String key=""+c[0]+c[1]+c[2];
        String title=lingQiTitles.get(key);
        if(title==null)title="灵棋组合 "+key;
        int delta=c[0]-c[2];
        String vote=delta>0?"主":delta<0?"客":"平";
        String d="键值 "+key+"｜"+title+"\n"+
                "上棋 "+marks(pieces[0])+"  中棋 "+marks(pieces[1])+"  下棋 "+marks(pieces[2])+"\n"+
                "足球实验映射：上−下="+delta+" → "+vote+"向";
        return new LingResult(c[0],c[1],c[2],vote,d);
    }

    private String marks(boolean[] a){
        StringBuilder s=new StringBuilder();
        for(boolean b:a)s.append(b?'●':'○');
        return s.toString();
    }

    private WenResult castWen(Random r){
        int[] line=new int[6];
        int[] p=new int[6];
        int[] ch=new int[6];
        int moving=0,lower=0,upper=0,lowMove=0,upMove=0;
        StringBuilder ls=new StringBuilder();
        for(int i=0;i<6;i++){
            int sum=0;
            for(int k=0;k<3;k++)sum+=2+(r.nextBoolean()?1:0);
            line[i]=sum;
            p[i]=(sum==7||sum==9)?1:0;
            ch[i]=(sum==6)?1:(sum==9?0:p[i]);
            if(sum==6||sum==9){
                moving++;
                if(i<3)lowMove++;else upMove++;
            }
            if(p[i]==1){
                if(i<3)lower++;else upper++;
            }
        }
        String primary=hexName(p), changed=hexName(ch);
        for(int i=5;i>=0;i--) ls.append(i+1).append("爻 ").append(lineSymbol(line[i])).append("  ");
        int host=lower*2+lowMove, away=upper*2+upMove;
        String vote=host>away?"主":host<away?"客":"平";
        String d="本卦："+primary+"　变卦："+changed+"　动爻："+moving+"\n"+ls+"\n"+
                "足球实验映射：下卦主方="+host+"，上卦客方="+away+" → "+vote+"向";
        return new WenResult(line,p,ch,moving,vote,primary,changed,host,away,d);
    }

    private String lineSymbol(int v){
        if(v==6)return "⚋×(老阴)";
        if(v==7)return "⚊(少阳)";
        if(v==8)return "⚋(少阴)";
        return "⚊○(老阳)";
    }

    private String hexName(int[] bits){
        String lower=""+bits[0]+bits[1]+bits[2];
        String upper=""+bits[3]+bits[4]+bits[5];
        String lo=tri(lower),up=tri(upper);
        String n=HEX.get(up+"/"+lo);
        return n==null?(up+"上"+lo+"下"):n;
    }

    private String tri(String s){
        switch(s){
            case"111":return"乾";
            case"110":return"兑";
            case"101":return"离";
            case"100":return"震";
            case"011":return"巽";
            case"010":return"坎";
            case"001":return"艮";
            default:return"坤";
        }
    }

    private ZiResult castZi(Random r){
        int zi=r.nextInt(12);
        int fu=(12-zi)%12;
        int soul=r.nextInt(12);
        List<List<String>> stars=new ArrayList<>();
        for(int i=0;i<12;i++)stars.add(new ArrayList<>());
        for(int i=0;i<ZIWEI_GROUP.length;i++){
            if(!ZIWEI_GROUP[i].isEmpty())stars.get(mod(zi-i)).add(ZIWEI_GROUP[i]);
        }
        for(int i=0;i<TIANFU_GROUP.length;i++){
            if(!TIANFU_GROUP[i].isEmpty())stars.get(mod(fu+i)).add(TIANFU_GROUP[i]);
        }
        String[] palace=new String[12];
        for(int i=0;i<12;i++)palace[i]=PALACES[mod(soul-i)];
        int away=mod(soul+6);
        int hs=stars.get(soul).size(), as=stars.get(away).size();
        String vote=hs>as?"主":hs<as?"客":"平";
        String d="紫微落"+BRANCHES[zi]+"，天府落"+BRANCHES[fu]+"，命宫落"+BRANCHES[soul]+"\n"+
                "命宫主星数="+hs+"，迁移宫主星数="+as+" → "+vote+"向\n"+
                "排星规则：紫微星系逆布；天府星系顺布；天府位置与紫微镜像。";
        return new ZiResult(zi,fu,soul,palace,stars,vote,hs,as,d);
    }

    private int mod(int x){
        x%=12;
        return x<0?x+12:x;
    }

    private void renderZiwei(ZiResult z){
        ziweiGrid.removeAllViews();
        int[][] branchAt={{3,4,5,6},{2,-1,-1,7},{1,-1,-1,8},{0,11,10,9}};
        for(int row=0;row<4;row++){
            for(int col=0;col<4;col++){
                int b=branchAt[row][col];
                TextView t=new TextView(this);
                t.setGravity(Gravity.CENTER);
                t.setPadding(dp(3),dp(6),dp(3),dp(6));
                t.setTextSize(11);
                GradientDrawable gd=new GradientDrawable();
                gd.setStroke(dp(1),0xff999999);
                gd.setColor(b<0?0xfff3efe6:0xffffffff);
                t.setBackground(gd);
                if(b<0){
                    t.setText((row==1&&col==1)?"紫占\n随机盘":(row==2&&col==2?"三术\n合参":""));
                }else{
                    StringBuilder s=new StringBuilder();
                    s.append(BRANCHES[b]).append("·").append(z.palace[b]);
                    for(String star:z.stars.get(b))s.append("\n").append(star);
                    t.setText(s.toString());
                    if(b==z.soul)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                }
                GridLayout.LayoutParams gp=new GridLayout.LayoutParams();
                gp.width=0;
                gp.height=dp(104);
                gp.columnSpec=GridLayout.spec(col,1,1f);
                gp.rowSpec=GridLayout.spec(row);
                ziweiGrid.addView(t,gp);
            }
        }
    }

    private void renderConsensus(LingResult l,WenResult w,ZiResult z){
        int h=0,d=0,a=0;
        String[] votes={l.vote,w.vote,z.vote};
        for(String v:votes){
            if(v.equals("主"))h++;
            else if(v.equals("客"))a++;
            else d++;
        }
        String out;
        if(h>d&&h>a)out="主胜向";
        else if(a>d&&a>h)out="客胜向";
        else if(d>h&&d>a)out="平局向";
        else{
            int score=(l.up-l.down)+(w.hostPower-w.awayPower)+(z.hostStars-z.awayStars);
            out=score>0?"主队不败/主向":score<0?"客队不败/客向":"平衡局";
        }

        int dynamic=w.moving+Math.abs(l.up-l.down)+z.hostStars+z.awayStars;
        String size=dynamic>=5?"大2.5象":"小2.5象";
        int parity=(l.up+l.mid+l.down+z.zi+z.soul);
        for(int x:w.lines)parity+=x;
        String odd=(parity&1)==1?"单":"双";
        String half=w.hostPower>w.awayPower?"主":w.hostPower<w.awayPower?"客":"平";
        String scoreCandidates=scores(out,size);
        String match=matchInput.getText().toString().trim();
        if(match.isEmpty())match="未命名比赛";
        String seed=seedInput.getText().toString().trim();

        consensusText.setText(match+"\n\n"+
                "胜平负象意："+out+"　｜　三术票：主"+h+" 平"+d+" 客"+a+"\n"+
                "大小球象意："+size+"　｜　单双："+odd+"\n"+
                "半全场象意："+half+" / "+(out.startsWith("主")?"主":out.startsWith("客")?"客":"平")+"\n"+
                "比分候选："+scoreCandidates+"\n"+
                "固定种子："+(seed.isEmpty()?"未启用（每次随机）":seed)+"\n\n"+
                "仅为传统术数数字化实验结果，不是统计概率或投注建议。建议用固定种子冻结事前结果，再以真实赛果复盘。");
    }

    private String scores(String outcome,String size){
        boolean big=size.startsWith("大");
        if(outcome.startsWith("主"))return big?"2:1 / 3:1 / 3:2":"1:0 / 2:0 / 2:1";
        if(outcome.startsWith("客"))return big?"1:2 / 1:3 / 2:3":"0:1 / 0:2 / 1:2";
        return big?"2:2 / 1:1 / 3:3":"0:0 / 1:1";
    }

    private void loadLingQi(){
        try(BufferedReader br=new BufferedReader(new InputStreamReader(getAssets().open("lingqi_titles.tsv"),StandardCharsets.UTF_8))){
            String s;
            while((s=br.readLine())!=null){
                int p=s.indexOf('\t');
                if(p>0)lingQiTitles.put(s.substring(0,p),s.substring(p+1));
            }
        }catch(Exception ignored){}
    }

    private static final Map<String,String> HEX=new HashMap<>();
    static{
        putHex("乾","乾","乾为天"); putHex("坤","坤","坤为地"); putHex("坎","震","水雷屯"); putHex("艮","坎","山水蒙");
        putHex("坎","乾","水天需"); putHex("乾","坎","天水讼"); putHex("坤","坎","地水师"); putHex("坎","坤","水地比");
        putHex("巽","乾","风天小畜"); putHex("乾","兑","天泽履"); putHex("坤","乾","地天泰"); putHex("乾","坤","天地否");
        putHex("乾","离","天火同人"); putHex("离","乾","火天大有"); putHex("坤","艮","地山谦"); putHex("震","坤","雷地豫");
        putHex("兑","震","泽雷随"); putHex("艮","巽","山风蛊"); putHex("坤","兑","地泽临"); putHex("巽","坤","风地观");
        putHex("离","震","火雷噬嗑"); putHex("艮","离","山火贲"); putHex("艮","坤","山地剥"); putHex("坤","震","地雷复");
        putHex("乾","震","天雷无妄"); putHex("艮","乾","山天大畜"); putHex("艮","震","山雷颐"); putHex("兑","巽","泽风大过");
        putHex("坎","坎","坎为水"); putHex("离","离","离为火"); putHex("兑","艮","泽山咸"); putHex("震","巽","雷风恒");
        putHex("乾","艮","天山遁"); putHex("震","乾","雷天大壮"); putHex("离","坤","火地晋"); putHex("坤","离","地火明夷");
        putHex("巽","离","风火家人"); putHex("离","兑","火泽睽"); putHex("坎","艮","水山蹇"); putHex("震","坎","雷水解");
        putHex("艮","兑","山泽损"); putHex("巽","震","风雷益"); putHex("兑","乾","泽天夬"); putHex("乾","巽","天风姤");
        putHex("兑","坤","泽地萃"); putHex("坤","巽","地风升"); putHex("兑","坎","泽水困"); putHex("坎","巽","水风井");
        putHex("兑","离","泽火革"); putHex("离","巽","火风鼎"); putHex("震","震","震为雷"); putHex("艮","艮","艮为山");
        putHex("巽","艮","风山渐"); putHex("震","兑","雷泽归妹"); putHex("震","离","雷火丰"); putHex("离","艮","火山旅");
        putHex("巽","巽","巽为风"); putHex("兑","兑","兑为泽"); putHex("巽","坎","风水涣"); putHex("坎","兑","水泽节");
        putHex("巽","兑","风泽中孚"); putHex("震","艮","雷山小过"); putHex("坎","离","水火既济"); putHex("离","坎","火水未济");
    }

    private static void putHex(String up,String low,String name){
        HEX.put(up+"/"+low,name);
    }

    static class LingResult{
        int up,mid,down;
        String vote,display;
        LingResult(int u,int m,int d,String v,String s){
            up=u;mid=m;down=d;vote=v;display=s;
        }
    }

    static class WenResult{
        int[] lines,p,ch;
        int moving,hostPower,awayPower;
        String vote,primary,changed,display;
        WenResult(int[]l,int[]p,int[]c,int m,String v,String a,String b,int h,int aw,String d){
            lines=l;this.p=p;ch=c;moving=m;vote=v;primary=a;changed=b;hostPower=h;awayPower=aw;display=d;
        }
    }

    static class ZiResult{
        int zi,fu,soul,hostStars,awayStars;
        String[] palace;
        List<List<String>>stars;
        String vote,display;
        ZiResult(int z,int f,int s,String[]p,List<List<String>>st,String v,int h,int a,String d){
            zi=z;fu=f;soul=s;palace=p;stars=st;vote=v;hostStars=h;awayStars=a;display=d;
        }
    }
}
