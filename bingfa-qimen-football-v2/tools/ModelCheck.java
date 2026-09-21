import com.abe618.bingfaqimenv2.QimenEngine;

public class ModelCheck {
    public static void main(String[] args) {
        int home=0, draw=0, away=0;
        for (int i=1;i<=1080;i++) {
            QimenEngine.Board b=QimenEngine.generateBySerial(i);
            switch (b.prediction.result) {
                case "主胜": home++; break;
                case "客胜": away++; break;
                default: draw++;
            }
        }
        System.out.println("主胜="+home+" 平="+draw+" 客胜="+away);
    }
}
