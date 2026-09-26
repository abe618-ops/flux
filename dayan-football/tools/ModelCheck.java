import com.abe618.dayanfootball.DayanEngine;

public class ModelCheck {
    private static int[] lines(String s){
        int[] a=new int[6];
        for(int i=0;i<6;i++) a[i]=s.charAt(i)-'0';
        return a;
    }
    private static void expect(String id,String pattern,String outcome,int goals,String ouPrefix,String oePrefix,String score){
        DayanEngine.Cast c=DayanEngine.analyzeLines(lines(pattern));
        DayanEngine.Prediction p=c.standard;
        if(!outcome.equals(p.outcome) || goals!=p.goalsMain ||
           !p.overUnder.startsWith(ouPrefix) || !p.oddEven.startsWith(oePrefix) ||
           !score.equals(p.scoreMain)){
            throw new IllegalStateException(id+" 回归失败: "+p.outcome+" / "+p.goalsMain+" / "+p.overUnder+" / "+p.oddEven+" / "+p.scoreMain+" | "+c.baseHex+"→"+c.changedHex);
        }
        System.out.println(id+" OK "+pattern+" "+p.outcome+" "+p.goalsMain+"球 "+p.overUnder+" "+p.oddEven+" "+p.scoreMain);
    }

    public static void main(String[] args){
        int[] counts=new int[10];
        for(long seed=1;seed<=2000;seed++){
            DayanEngine.Cast c=DayanEngine.cast(seed,DayanEngine.MappingMode.STANDARD);
            if(c.baseHex==null||c.changedHex==null||c.standard==null)throw new IllegalStateException("null @ "+seed);
            for(int v:c.lines){if(v<6||v>9)throw new IllegalStateException("bad line");counts[v]++;}
        }
        for(int v=6;v<=9;v++)if(counts[v]==0)throw new IllegalStateException("missing line "+v);

        // 对话盲测冻结样本：同样六爻必须复现当时的最终主判断。
        expect("DY-10","797898","主胜",3,"大","单","2:1");
        expect("DY-11","788997","客胜",4,"大","双","1:3");
        expect("DY-12","787878","主胜",3,"大","单","3:0");
        expect("DY-13","789989","客胜",3,"大","单","1:2");
        expect("DY-14","778979","主胜",2,"小","双","2:0");
        expect("DY-15","977897","客胜",2,"小","双","0:2");
        expect("DY-16","778897","客胜",2,"小","双","0:1");
        expect("DY-17","798987","客胜",3,"大","单","1:2");
        expect("DY-18","878878","主胜",1,"小","单","1:0");
        expect("DY-19","789888","客胜",2,"小","双","1:2");

        System.out.println("MODEL_OK 2000 casts + DY10-DY19 regression");
        System.out.println("6="+counts[6]+" 7="+counts[7]+" 8="+counts[8]+" 9="+counts[9]);
    }
}
