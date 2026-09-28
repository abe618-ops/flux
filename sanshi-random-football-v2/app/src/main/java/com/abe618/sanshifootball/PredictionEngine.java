package com.abe618.sanshifootball;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class PredictionEngine {
    private PredictionEngine() {}

    private static final SecureRandom RNG = createSecureRandom();
    private static final String[] JIAZI = TraditionalEngine.sixtyJiaZi();

    public static final class LayerResult {
        public final String name;
        public final TraditionalEngine.QimenResult chart;
        public final int homePower;
        public final int awayPower;
        public final int totalGoals;
        public final String outcome;
        public final String halfFull;
        public final String score;
        public final String altScore;
        public final String overUnder;
        public final String parity;
        public final String randomCode;

        LayerResult(String name, TraditionalEngine.QimenResult chart,
                    int homePower, int awayPower, int totalGoals,
                    String outcome, String halfFull, String score, String altScore,
                    String overUnder, String parity, String randomCode) {
            this.name=name; this.chart=chart; this.homePower=homePower; this.awayPower=awayPower;
            this.totalGoals=totalGoals; this.outcome=outcome; this.halfFull=halfFull;
            this.score=score; this.altScore=altScore; this.overUnder=overUnder;
            this.parity=parity; this.randomCode=randomCode;
        }

        public String compact() {
            return name+"｜"+outcome+"｜"+halfFull+"｜"+totalGoals+"球｜"+score+" / "+altScore
                    +"｜"+overUnder+"｜"+parity;
        }
    }

    public static final class PredictionResult {
        public final String seedHex;
        public final String drawId;
        public final String homeName;
        public final String awayName;
        public final List<LayerResult> layers;
        public final String outcome;
        public final String halfFull;
        public final int totalGoals;
        public final String goalRange;
        public final String score;
        public final String altScore;
        public final String overUnder;
        public final String parity;
        public final int resonance;

        PredictionResult(String seedHex,String drawId,String homeName,String awayName,
                         List<LayerResult> layers,String outcome,String halfFull,int totalGoals,
                         String goalRange,String score,String altScore,String overUnder,
                         String parity,int resonance) {
            this.seedHex=seedHex; this.drawId=drawId; this.homeName=homeName; this.awayName=awayName;
            this.layers=layers; this.outcome=outcome; this.halfFull=halfFull; this.totalGoals=totalGoals;
            this.goalRange=goalRange; this.score=score; this.altScore=altScore;
            this.overUnder=overUnder; this.parity=parity; this.resonance=resonance;
        }

        public String fullText() {
            StringBuilder b=new StringBuilder();
            b.append("四层随机足球预测 V2\n");
            b.append("封盘ID：").append(drawId).append("\n");
            b.append("主队：").append(homeName).append("　客队：").append(awayName).append("\n\n");
            b.append("【总体合参】\n");
            b.append("胜平负：").append(outcome).append("\n");
            b.append("半全场：").append(halfFull).append("\n");
            b.append("进球数：").append(totalGoals).append("球（").append(goalRange).append("）\n");
            b.append("比分：").append(score).append("　备选 ").append(altScore).append("\n");
            b.append("大小球：").append(overUnder).append("\n");
            b.append("单双：").append(parity).append("\n");
            b.append("四盘共振：").append(resonance).append("/4\n\n");
            b.append("【分层结果】\n");
            for(LayerResult l:layers) b.append(l.compact()).append("\n");
            b.append("\n完整256位种子：").append(seedHex);
            return b.toString();
        }
    }

    public static PredictionResult next(String home,String away) {
        if(home==null || home.trim().isEmpty()) home="主队";
        if(away==null || away.trim().isEmpty()) away="客队";

        byte[] master=new byte[32];
        RNG.nextBytes(master);
        String seedHex=hex(master);
        String drawId=seedHex.substring(0,16).toUpperCase(Locale.ROOT);

        byte[] anchorDigest=sha256(master, "ANCHOR");
        long daySpan=36525L; // 2000-01-01 .. 2099-12-31
        long dayOffset=unsignedLong(anchorDigest,0)%daySpan;
        LocalDate d=LocalDate.of(2000,1,1).plusDays(dayOffset);
        int hour=u(anchorDigest[8])%24;
        int minute=u(anchorDigest[9])%60;
        int second=u(anchorDigest[10])%60;
        TraditionalEngine.CalendarInfo c=TraditionalEngine.CalendarInfo.of(
                d.getYear(),d.getMonthValue(),d.getDayOfMonth(),hour,minute);

        List<LayerResult> layers=new ArrayList<>(4);

        byte[] h1=sha256(master,"HOUR");
        TraditionalEngine.QimenResult q1=TraditionalEngine.qimen(c,"时家");
        layers.add(scoreLayer("时家盘",q1,h1));

        byte[] h2=sha256(master,"KE");
        TraditionalEngine.QimenResult q2=TraditionalEngine.qimen(c,"刻家10");
        layers.add(scoreLayer("刻家盘·10分钟",q2,h2));

        byte[] h3=sha256(master,"MINUTE");
        String minuteGz=minuteGanZhi(hour,minute);
        boolean minuteYang=scaleYang(c, hour);
        int minuteBureau=scaleBureau(c.hourGz,minuteYang);
        TraditionalEngine.QimenResult q3=TraditionalEngine.customQimen(
                c,"分盘·4分钟制",minuteGz,minuteYang,minuteBureau,
                scaleYuan(c.hourGz),
                "随机起盘V2：分层采用4分钟一柱的实验口径；不要求用户输入现实时间。");
        layers.add(scoreLayer("分盘·4分钟",q3,h3));

        byte[] h4=sha256(master,"SECOND");
        String secondGz=secondGanZhi(hour,minute,second);
        boolean secondYang=scaleYang(c,hour);
        int secondBureau=scaleBureau(minuteGz,secondYang);
        TraditionalEngine.QimenResult q4=TraditionalEngine.customQimen(
                c,"秒盘·20秒制",secondGz,secondYang,secondBureau,
                scaleYuan(minuteGz),
                "随机起盘V2：秒层采用20秒一柱的实验口径；作为高频随机细分层独立参与合参。");
        layers.add(scoreLayer("秒盘·20秒",q4,h4));

        int hp=0,ap=0,goals=0;
        for(LayerResult l:layers){hp+=l.homePower;ap+=l.awayPower;goals+=l.totalGoals;}
        double delta=(hp-ap)/4.0;
        String outcome=outcome(delta);
        int total=(int)Math.round(goals/4.0);
        total=clamp(total,0,6);
        byte[] hf=sha256(master,"FUSION");
        ScorePair scores=makeScore(outcome,total,u(hf[0]),u(hf[1]));
        total=scores.total;
        String halfFull=halfFull(outcome,u(hf[2]));
        String ou=total>=3?"大2.5":"小2.5";
        String parity=total%2==0?"双":"单";
        int resonance=0;
        for(LayerResult l:layers)if(l.outcome.equals(outcome))resonance++;
        String range=Math.max(0,total-1)+"-"+Math.min(7,total+1)+"球";

        return new PredictionResult(seedHex,drawId,home.trim(),away.trim(),layers,outcome,halfFull,
                total,range,scores.primary,scores.alt,ou,parity,resonance);
    }

    private static LayerResult scoreLayer(String name,TraditionalEngine.QimenResult q,byte[] digest){
        TraditionalEngine.QimenPalace main=findByStar(q,q.zhiFu);
        TraditionalEngine.QimenPalace guest=findByDoor(q,q.zhiShi);
        int home=palacePower(main)+((u(digest[0])%5)-2);
        int away=palacePower(guest)+((u(digest[1])%5)-2);
        double delta=home-away;
        String outcome=outcome(delta);

        int agg=aggression(q);
        int total=clamp(2 + Math.round(agg/8.0f) + (u(digest[2])%3)-1,0,6);
        ScorePair score=makeScore(outcome,total,u(digest[3]),u(digest[4]));
        total=score.total;
        String hf=halfFull(outcome,u(digest[5]));
        return new LayerResult(name,q,home,away,total,outcome,hf,score.primary,score.alt,
                total>=3?"大2.5":"小2.5",total%2==0?"双":"单",
                hex(Arrays.copyOfRange(digest,0,6)).toUpperCase(Locale.ROOT));
    }

    private static TraditionalEngine.QimenPalace findByStar(TraditionalEngine.QimenResult q,String star){
        for(int i=1;i<q.palaces.length;i++){
            TraditionalEngine.QimenPalace p=q.palaces[i];
            if(p!=null && star.equals(p.star))return p;
        }
        for(int i=1;i<q.palaces.length;i++){
            TraditionalEngine.QimenPalace p=q.palaces[i];
            if(p!=null && "值符".equals(p.god))return p;
        }
        return q.palaces[1];
    }

    private static TraditionalEngine.QimenPalace findByDoor(TraditionalEngine.QimenResult q,String door){
        for(int i=1;i<q.palaces.length;i++){
            TraditionalEngine.QimenPalace p=q.palaces[i];
            if(p!=null && door.equals(p.door))return p;
        }
        return q.palaces[8];
    }

    private static int palacePower(TraditionalEngine.QimenPalace p){
        if(p==null)return 0;
        int v=0;
        v+=doorPower(p.door);
        v+=starPower(p.star);
        v+=godPower(p.god);
        v+=stemPower(p.heavenStem);
        v+=stemPower(p.earthStem)/2;
        return v;
    }

    private static int doorPower(String s){
        if("开门".equals(s)||"生门".equals(s))return 4;
        if("景门".equals(s))return 3;
        if("休门".equals(s))return 1;
        if("伤门".equals(s)||"惊门".equals(s))return -1;
        if("杜门".equals(s))return -2;
        if("死门".equals(s))return -4;
        return 0;
    }

    private static int starPower(String s){
        if("天心".equals(s)||"天辅".equals(s))return 3;
        if("天任".equals(s)||"天英".equals(s)||"天冲".equals(s))return 2;
        if("天禽".equals(s))return 1;
        if("天蓬".equals(s))return 0;
        if("天柱".equals(s))return -1;
        if("天芮".equals(s))return -3;
        return 0;
    }

    private static int godPower(String s){
        if("值符".equals(s)||"九天".equals(s)||"六合".equals(s))return 2;
        if("太阴".equals(s)||"九地".equals(s))return 1;
        if("螣蛇".equals(s)||"朱雀".equals(s)||"勾陈".equals(s))return -1;
        if("玄武".equals(s)||"白虎".equals(s))return -2;
        return 0;
    }

    private static int stemPower(String s){
        if(s==null||s.isEmpty())return 0;
        char g=s.charAt(0);
        if(g=='乙'||g=='丙'||g=='丁')return 2;
        if(g=='戊'||g=='己')return 1;
        if(g=='庚'||g=='辛')return -1;
        return 0;
    }

    private static int aggression(TraditionalEngine.QimenResult q){
        int v=0;
        for(int i=1;i<q.palaces.length;i++){
            TraditionalEngine.QimenPalace p=q.palaces[i];
            if(p==null)continue;
            if("景门".equals(p.door)||"伤门".equals(p.door)||"惊门".equals(p.door)||"开门".equals(p.door))v+=2;
            if("休门".equals(p.door)||"杜门".equals(p.door)||"死门".equals(p.door))v-=1;
            if("天英".equals(p.star)||"天冲".equals(p.star)||"九天".equals(p.god)||"白虎".equals(p.god))v+=1;
        }
        return v;
    }

    private static String outcome(double delta){
        if(delta>=2.0)return "主胜";
        if(delta<=-2.0)return "客胜";
        return "平";
    }

    private static String halfFull(String full,int b){
        int k=b%3;
        if("主胜".equals(full))return new String[]{"平/主","主/主","客/主"}[k];
        if("客胜".equals(full))return new String[]{"平/客","客/客","主/客"}[k];
        return new String[]{"平/平","主/平","客/平"}[k];
    }

    private static final class ScorePair{
        final String primary,alt; final int total;
        ScorePair(String p,String a,int t){primary=p;alt=a;total=t;}
    }

    private static ScorePair makeScore(String outcome,int requested,int b1,int b2){
        int total=clamp(requested,0,6);
        int h,a;
        if("平".equals(outcome)){
            if(total%2==1) total=total==6?4:total+1;
            h=a=total/2;
        }else if("主胜".equals(outcome)){
            if(total==0)total=1;
            a=b1%Math.max(1,(total+1)/2);
            h=total-a;
            if(h<=a){a=Math.max(0,(total-1)/2);h=total-a;}
        }else{
            if(total==0)total=1;
            h=b1%Math.max(1,(total+1)/2);
            a=total-h;
            if(a<=h){h=Math.max(0,(total-1)/2);a=total-h;}
        }
        String primary=h+":"+a;
        int ah=h,aa=a;
        if("平".equals(outcome)){
            int g=clamp(h+(b2%2==0?1:-1),0,3); ah=g;aa=g;
        }else if("主胜".equals(outcome)){
            aa=clamp(a+(b2%3)-1,0,3); ah=Math.max(aa+1,clamp(h+(b2%2),1,5));
        }else{
            ah=clamp(h+(b2%3)-1,0,3); aa=Math.max(ah+1,clamp(a+(b2%2),1,5));
        }
        String alt=ah+":"+aa;
        if(alt.equals(primary)){
            if("主胜".equals(outcome))alt=(h+1)+":"+a;
            else if("客胜".equals(outcome))alt=h+":"+(a+1);
            else alt=(h+1)+":"+(a+1);
        }
        return new ScorePair(primary,alt,h+a);
    }

    private static String minuteGanZhi(int hour,int minute){
        int dayMinute=hour*60+minute;
        int sinceZi=(dayMinute+60)%1440; // 23:00 is the random-scale origin
        int idx=(sinceZi/4)%60;
        return JIAZI[idx];
    }

    private static String secondGanZhi(int hour,int minute,int second){
        int sec=hour*3600+minute*60+second;
        int sinceZi=(sec+3600)%86400;
        int idx=(sinceZi/20)%60;
        return JIAZI[idx];
    }

    private static boolean scaleYang(TraditionalEngine.CalendarInfo c,int hour){
        String term=TraditionalEngine.normalizeJieQiName(c.jieQi);
        boolean winterHalf=Arrays.asList("冬至","小寒","大寒","立春","雨水","惊蛰","春分","清明","谷雨","立夏","小满","芒种").contains(term);
        char branch=c.hourGz.charAt(1);
        boolean firstHalf="子丑寅卯辰巳".indexOf(branch)>=0;
        return winterHalf ? firstHalf : !firstHalf;
    }

    private static int scaleBureau(String parentGz,boolean yang){
        int idx=Arrays.asList(JIAZI).indexOf(parentGz);
        if(idx<0)idx=0;
        int yuan=(idx/5)%3;
        int[] v=yang?new int[]{1,7,4}:new int[]{9,3,6};
        return v[yuan];
    }

    private static String scaleYuan(String parentGz){
        int idx=Arrays.asList(JIAZI).indexOf(parentGz);
        if(idx<0)idx=0;
        return new String[]{"上元","中元","下元"}[(idx/5)%3];
    }

    private static SecureRandom createSecureRandom(){
        try{return SecureRandom.getInstanceStrong();}
        catch(Exception ignored){return new SecureRandom();}
    }

    private static byte[] sha256(byte[] master,String label){
        try{
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            md.update(master);
            md.update((byte)0x00);
            md.update(label.getBytes(StandardCharsets.UTF_8));
            return md.digest();
        }catch(Exception e){throw new IllegalStateException(e);}
    }

    private static long unsignedLong(byte[] b,int off){
        long v=ByteBuffer.wrap(b,off,8).getLong();
        if(v==Long.MIN_VALUE)return 0;
        return Math.abs(v);
    }

    private static int u(byte b){return b&0xff;}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}

    private static String hex(byte[] b){
        StringBuilder s=new StringBuilder(b.length*2);
        for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x&0xff));
        return s.toString();
    }
}
