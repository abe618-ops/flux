import com.abe618.bingfaqimenv2.QimenEngine;
import com.abe618.fourdivfootball.FourDivinationEngine;
import com.abe618.sixfusionfootball.FusionEngine;

/**
 * JVM bridge: compiles the ORIGINAL repository Java engines in-place, with no copy of algorithms.
 * All football outputs are legacy experiments; do not treat numeric margins as calibrated odds.
 */
public final class LegacyBridge {
    private static String q(String s) {
        if(s==null)return "null";
        return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";
    }
    private static String f(String name,String value){return q(name)+":"+q(value);}
    private static String f(String name,int value){return q(name)+":"+value;}
    private static String f(String name,boolean value){return q(name)+":"+value;}
    private static String f(String name,double value){return q(name)+":"+value;}
    private static String signals(FourDivinationEngine.SystemResult s){
        return "{"+f("name",s.name)+","+f("plate",s.raw)+","+f("direction",s.direction)+","+f("goals",s.goals)+","+f("over_under",s.overUnder)+","+f("parity",s.oddEven)+","+f("margin",s.margin)+"}";
    }
    public static void main(String[] args){
        if(args.length>0 && "--audit-qimen".equals(args[0])){
            int home=0,draw=0,away=0;
            for(int i=1;i<=1080;i++){
                QimenEngine.Board b=QimenEngine.generateBySerial(i);
                if(b.serial!=i || b.prediction==null || b.prediction.result==null)
                    throw new IllegalStateException("盘号或结果缺失: "+i);
                if("主胜".equals(b.prediction.result))home++;
                else if("平".equals(b.prediction.result))draw++;
                else if("客胜".equals(b.prediction.result))away++;
                else throw new IllegalStateException("非法方向: "+b.prediction.result);
            }
            System.out.println("{"+f("states",1080)+","+f("home",home)+","+f("draw",draw)+","+f("away",away)+"}");
            return;
        }
        String seed=args.length>0?args[0]:"DEMO-001";
        long n=FourDivinationEngine.seedFromText(seed);
        int serial=seed.startsWith("serial:")?Integer.parseInt(seed.substring(7)):Math.floorMod(n,1080L)==0?1:(int)Math.floorMod(n,1080L)+1;
        if(serial<1||serial>1080)throw new IllegalArgumentException("奇门局数必须为1—1080");
        QimenEngine.Board qm=QimenEngine.generateBySerial(serial);
        FourDivinationEngine.CastState four=FourDivinationEngine.cast(n);
        FusionEngine.CastState fusion=FusionEngine.cast(n);
        StringBuilder o=new StringBuilder();
        o.append("{").append(f("seed",seed)).append(",");
        o.append("\"qimen\":{").append(f("serial",qm.serial)).append(",");
        o.append(f("dun",qm.yin?"阴":"阳")).append(",");
        o.append(f("ju",qm.ju)).append(",");
        o.append(f("hour_ganzhi",qm.hourGz)).append(",");
        o.append(f("zhifu",qm.zhiFuStar)).append(",");
        o.append(f("zhishi",qm.zhiShiDoor)).append(",");
        o.append(f("home_palace",qm.homeGong)).append(",");
        o.append(f("away_palace",qm.awayGong)).append(",");
        o.append(f("direction",qm.prediction.result)).append(",");
        o.append(f("goals",qm.prediction.totalGoals)).append(",");
        o.append(f("score",qm.prediction.scoreText())).append(",");
        o.append("\"palaces\":[");
        for(int i=1;i<=9;i++){if(i>1)o.append(",");
          o.append("{").append(f("number",i)).append(",").append(f("earth_stem",qm.di[i])).append(",");
          o.append(f("heaven_stem",qm.tian[i])).append(",").append(f("star",qm.star[i])).append(",");
          o.append(f("door",qm.door[i])).append(",").append(f("god",qm.god[i])).append("}");
        }
        o.append("]},\"four_tracks\":{");
        o.append("\"maya\":").append(signals(four.maya)).append(",\"sikidy\":").append(signals(four.sikidy));
        o.append(",\"ifa\":").append(signals(four.ifa)).append(",\"raml\":").append(signals(four.raml));
        o.append(",").append(f("combined_direction",four.combined.direction));
        o.append(",").append(f("half_full",four.combined.halfFull));
        o.append(",").append(f("primary_score",four.combined.primaryScore));
        o.append("},\"fusion_bingzhan\":{");
        o.append(f("raw",fusion.bingzhan.raw)).append(",");
        o.append(f("direction",fusion.bingzhan.direction)).append(",");
        o.append(f("goals",fusion.bingzhan.goals)).append(",");
        o.append(f("net_goal_hint",fusion.bingzhan.netGoalHint));
        o.append("},").append(f("notice","原始安卓民俗预测引擎的JVM运行封装；没有真实赛事输入或统计校准")).append("}");
        System.out.println(o);
    }
}
