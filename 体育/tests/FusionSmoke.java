package com.matchday.virtualbet;
import org.json.*;
import java.util.*;
public class FusionSmoke {
  static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
  static void eq(double a,double b,String m){check(Math.abs(a-b)<1e-8,m+" got="+a+" expected="+b);}
  static JSONObject pick(String market,String label,double odd,double line)throws Exception{return new JSONObject().put("market",market).put("label",label).put("odd",odd).put("line",line);}
  static JSONObject result(String ft)throws Exception{return new JSONObject().put("ft",ft).put("finished",true).put("source","test");}
  public static void main(String[] args)throws Exception{
    eq(MarketMath.asian(2,2.25,true,2.0),0.5,"OU 2.25 half loss");
    eq(MarketMath.asian(3,2.75,true,2.0),1.5,"OU 2.75 half win");
    eq(FootballData.factor(pick("OU","大",2.0,2.25),result("1:1")),0.5,"FootballData OU");
    eq(FootballData.factor(pick("AH","主",2.0,-0.25),result("0:0")),0.5,"FootballData AH home -0.25 draw");
    String html="<tr sId='77'><td>测试联赛</td><td>15日20:00</td><td>未</td><td>主队A</td><td>-</td><td>客队B</td><td>-</td></tr>";
    ArrayList<JSONObject> g=FootballData.globalTitan(html,"20260915");
    check(g.size()==1,"global row parsed");check(g.get(0).optInt("mode")==2,"global mode");check("77".equals(g.get(0).optString("titanId")),"titan id");
    System.out.println("PASS fusion smoke");
  }
}
