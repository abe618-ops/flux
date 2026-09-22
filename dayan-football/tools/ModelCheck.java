import com.abe618.dayanfootball.DayanEngine;

public class ModelCheck {
    public static void main(String[] args) {
        int[] counts=new int[10];
        for (long seed=1;seed<=2000;seed++) {
            DayanEngine.Cast c=DayanEngine.cast(seed, DayanEngine.MappingMode.STANDARD);
            if (c.baseHex==null || c.changedHex==null || c.standard==null || c.mirror==null) {
                throw new IllegalStateException("null model field at seed "+seed);
            }
            for (int v:c.lines) {
                if (v<6 || v>9) throw new IllegalStateException("bad line "+v+" seed "+seed);
                counts[v]++;
            }
            if (c.standard.scoreMain==null || c.standard.halfFull==null || c.standard.ranking==null) {
                throw new IllegalStateException("bad prediction seed "+seed);
            }
        }
        for(int v=6;v<=9;v++) if(counts[v]==0) throw new IllegalStateException("line "+v+" never appeared");
        System.out.println("MODEL_OK 2000 casts / 12000 lines");
        System.out.println("6="+counts[6]+" 7="+counts[7]+" 8="+counts[8]+" 9="+counts[9]);
        DayanEngine.Cast demo=DayanEngine.cast(20260922L,DayanEngine.MappingMode.STANDARD);
        System.out.println(demo.freezeCode);
        System.out.println(demo.standard.ranking+" / "+demo.standard.scoreMain+" / "+demo.standard.overUnder);
    }
}
