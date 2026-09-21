import com.abe618.bingfaqimenv2.QimenEngine;

public class ModelCheck {
    static int[] c = new int[3];
    static void add(int[] a, String r) {
        if ("主胜".equals(r)) a[0]++;
        else if ("平".equals(r)) a[1]++;
        else a[2]++;
    }
    static String fmt(int[] a) {
        int n=a[0]+a[1]+a[2];
        return "N="+n+" 主="+a[0]+" 平="+a[1]+" 客="+a[2];
    }
    public static void main(String[] args) {
        int[] all=new int[3], same=new int[3], diff=new int[3], yang=new int[3], yin=new int[3];
        int[][] ju=new int[10][3], stem=new int[10][3];
        int sameZero4=0, sameSecPos=0, sameSecNeg=0, sameSecZero=0;
        double sumP=0,sumT=0,sumM=0,sumProc=0,sumSec=0,sumF=0;
        int pPos=0,pNeg=0,tPos=0,tNeg=0,mPos=0,mNeg=0,procPos=0,procNeg=0,secPos=0,secNeg=0;
        for (int i=1;i<=1080;i++) {
            QimenEngine.Board b=QimenEngine.generateBySerial(i);
            QimenEngine.Prediction p=b.prediction;
            add(all,p.result);
            if (b.homeGong==b.awayGong) {
                add(same,p.result);
                if (Math.abs(p.primary)<1e-9 && Math.abs(p.technique)<1e-9 && Math.abs(p.medal)<1e-9 && Math.abs(p.process)<1e-9) sameZero4++;
                if (p.secondary>0.12) sameSecPos++; else if(p.secondary<-0.12) sameSecNeg++; else sameSecZero++;
            } else add(diff,p.result);
            add(b.yin?yin:yang,p.result);
            add(ju[b.ju],p.result);
            add(stem[b.hourIndex%10],p.result);
            sumP+=p.primary; sumT+=p.technique; sumM+=p.medal; sumProc+=p.process; sumSec+=p.secondary; sumF+=p.finalIndex;
            if(p.primary>0.12)pPos++; else if(p.primary<-0.12)pNeg++;
            if(p.technique>0.12)tPos++; else if(p.technique<-0.12)tNeg++;
            if(p.medal>0.12)mPos++; else if(p.medal<-0.12)mNeg++;
            if(p.process>0.12)procPos++; else if(p.process<-0.12)procNeg++;
            if(p.secondary>0.12)secPos++; else if(p.secondary<-0.12)secNeg++;
        }
        System.out.println("ALL "+fmt(all));
        System.out.println("SAME "+fmt(same)+" zeroPrimary4="+sameZero4+" sec(+/0/-)="+sameSecPos+"/"+sameSecZero+"/"+sameSecNeg);
        System.out.println("DIFF "+fmt(diff));
        System.out.println("YANG "+fmt(yang));
        System.out.println("YIN "+fmt(yin));
        for(int j=1;j<=9;j++) System.out.println("JU"+j+" "+fmt(ju[j]));
        for(int s=0;s<10;s++) System.out.println("STEM"+QimenEngine.GAN[s]+" "+fmt(stem[s]));
        System.out.printf(java.util.Locale.US,"AVG primary=%+.4f tech=%+.4f medal=%+.4f process=%+.4f secondary=%+.4f final=%+.4f%n",
                sumP/1080,sumT/1080,sumM/1080,sumProc/1080,sumSec/1080,sumF/1080);
        System.out.println("SIGNS primary "+pPos+"/"+pNeg+" tech "+tPos+"/"+tNeg+" medal "+mPos+"/"+mNeg+" process "+procPos+"/"+procNeg+" secondary "+secPos+"/"+secNeg);
        double[] ths={0.12,0.16,0.20,0.24,0.28,0.32,0.36,0.40,0.44,0.48};
        for(double th:ths){
            int[] a2=new int[3], a3=new int[3];
            for(int i=1;i<=1080;i++){
                QimenEngine.Board b=QimenEngine.generateBySerial(i);
                QimenEngine.Prediction p=b.prediction;
                if(b.homeGong!=b.awayGong){
                    add(a2,p.result); add(a3,p.result);
                }else{
                    String r2;
                    if(p.collision>th) r2="主胜";
                    else if(p.collision<-th) r2="客胜";
                    else if(p.votes>=2) r2="主胜";
                    else if(p.votes<=-2) r2="客胜";
                    else r2="平";
                    add(a2,r2);
                    String r3;
                    if(p.collision>th) r3="主胜";
                    else if(p.collision<-th) r3="客胜";
                    else if(p.votes>=3) r3="主胜";
                    else if(p.votes<=-3) r3="客胜";
                    else r3="平";
                    add(a3,r3);
                }
            }
            System.out.println(String.format(java.util.Locale.US,"SCAN th=%.2f vote2 %s | vote3 %s",th,fmt(a2),fmt(a3)));
        }

        double sumFG=0,minFG=999,maxFG=-999; int nJia=0,fgp=0,fgn=0,fgz=0;
        int[] fgT={0,0,0};
        double[] fths={0.30,0.50,0.70,0.90,1.10};
        int[][] fh=new int[fths.length][3];
        for(int i=1;i<=1080;i++){
            QimenEngine.Board b=QimenEngine.generateBySerial(i);
            if(b.hourIndex%10!=0) continue;
            QimenEngine.Prediction p=b.prediction;
            nJia++; sumFG+=p.fuGeng; minFG=Math.min(minFG,p.fuGeng); maxFG=Math.max(maxFG,p.fuGeng);
            if(p.fuGeng>0.12)fgp++; else if(p.fuGeng<-0.12)fgn++; else fgz++;
            for(int k=0;k<fths.length;k++){
                String rr=p.fuGeng>fths[k]?"主胜":(p.fuGeng<-fths[k]?"客胜":"平");
                add(fh[k],rr);
            }
        }
        System.out.printf(java.util.Locale.US,"JIA_FUGENG n=%d avg=%+.4f min=%+.3f max=%+.3f sign=%d/%d/%d%n",nJia,sumFG/nJia,minFG,maxFG,fgp,fgz,fgn);
        for(int k=0;k<fths.length;k++) System.out.println(String.format(java.util.Locale.US,"JIA_SCAN th=%.2f %s",fths[k],fmt(fh[k])));

    }
}
