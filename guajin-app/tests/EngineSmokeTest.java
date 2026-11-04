import com.abe618.guajin.GuajinEngine;

public final class EngineSmokeTest {
    private static void yes(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        yes(GuajinEngine.manual(0,0,0).number==1,"first");
        yes(GuajinEngine.manual(0,0,1).number==2,"second");
        yes(GuajinEngine.manual(2,2,2).number==27,"last");
        for(int a=0;a<3;a++)for(int b=0;b<3;b++)for(int c=0;c<3;c++){
            GuajinEngine.Cast k=GuajinEngine.manual(a,b,c);
            yes(k.number>=1&&k.number<=27,"range");
            yes(k.source().contains("PDF第"),"source");
        }
        GuajinEngine.Cast k=GuajinEngine.time("主vs客",1700000000000L);
        yes(k.code().equals(GuajinEngine.time("主vs客",1700000000000L).code()),"deterministic");
        GuajinEngine.Prediction f=GuajinEngine.football(k,1.5,1.0,false);
        yes(f.display.contains("半全场")&&f.display.contains("胜平负"),"football");
        yes(f.predictedScore.contains(":"),"score");
        GuajinEngine.Prediction b=GuajinEngine.basketball(k,110,107,-3.5,220.5,false);
        yes(b.display.contains("让分判断"),"basketball");
        GuajinEngine.Prediction d=GuajinEngine.digits(k,"seed");
        yes(d.direction.length()==3&&d.digits.length()==5,"digits");
        yes(d.direction.equals(GuajinEngine.digits(k,"seed").direction),"fixed-digit-seed");
        System.out.println("PASS: 27 indexes, reproducible casting, football/basketball/digits");
    }
}
