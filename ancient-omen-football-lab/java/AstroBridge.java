import com.abe618.triastrofootball.PredictionEngine;
/** Reuses original TriAstro engine; original astronomical positions are APPROXIMATE and randomized.
 * Does NOT claim historical Seven-Politics-Four-Residues or real-time ephemerides.
 */
public class AstroBridge {
  static String q(String s){return s==null?"null":"\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";}
  static String f(String k,String s){return q(k)+":"+q(s);}
  static String m(PredictionEngine.MethodResult r){
    return "{"+f("name",r.name)+","+f("direction",r.direction)+","+f("detail",r.detail)+","+f("random_mark",r.randomMark)+
      ",\"p_home\":"+r.pHome+",\"p_draw\":"+r.pDraw+",\"p_away\":"+r.pAway+",\"goal_mean\":"+r.goalMean+"}";
  }
  public static void main(String[] args){
    if(args.length<1)throw new IllegalArgumentException("Explicit seed required");
    PredictionEngine.Result r=PredictionEngine.run(args[0]);
    System.out.println("{"+f("seed",r.seed)+",\"western\":"+m(r.western)+",\"indian\":"+m(r.indian)+
      ",\"geomancy\":"+m(r.geomancy)+","+f("m1_pattern",r.m1Pattern)+","+f("m2_direction",r.m2Direction)+
      ",\"normalization_draws\":"+r.k+","+f("caution","Original engine uses randomized dates and approximate astronomy. Not a real football kickoff chart and not an independent claim of prediction accuracy.")+"}");
  }
}