"""Execute the actual Java parser, settlement and ROI methods without Android UI."""
from pathlib import Path
import subprocess, tempfile
source=Path('app/src/main/java/com/quicklottery/blindbacktest/MainActivity.java').read_text()
def block(signature):
    start=source.index(signature);left=source.index('{',start);depth=1;i=left+1
    while depth:
        if source[i]=='{':depth+=1
        elif source[i]=='}':depth-=1
        i+=1
    return source[start:i]
parts=[block(x) for x in ['static class Draw','static class Ticket','static class Prize','private int pattern(','private Prize settle(','private Prize settleHappy(','private String cn(','private Draw parseLine(','private void updateStats(']]
program='''import java.util.*;import java.util.regex.*;import java.text.*;
public class SettlementHarness {
 static final int[] NEED={20,3,3,5};
 HashMap<String,HashMap<String,Double>> kl8PrizeMap=new HashMap<>();
 int sessionCount=0,unresolvedFloating=0; double totalCost=0,totalReturn=0,settledCost=0;
 DecimalFormat moneyFmt=new DecimalFormat("0.##");
 static class TextView {String text;void setText(String s){text=s;}}
 TextView statsTv=new TextView();
'''+ '\n'.join(parts)+'''
 static void require(boolean b,String label){if(!b)throw new AssertionError(label);}
 static Ticket ticket(int game,int mode,int... nums){Ticket t=new Ticket();t.game=game;t.mode=mode;t.nums=nums;t.multiple=1;t.cost=2;return t;}
 public static void main(String[] args)throws Exception{
  SettlementHarness h=new SettlementHarness();
  for(String line:java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[0]))){if(line.startsWith("#"))continue;String[] a=line.split("\\t");h.kl8PrizeMap.computeIfAbsent(a[0],k->new HashMap<>()).put(a[1],Double.parseDouble(a[2]));}
  Draw d=h.parseLine("2026263 2026-09-30 07 08 09 10 11 13 22 24 25 26 31 33 37 45 50 51 62 67 70 80",0);
  require(d!=null&&d.nums.length==20&&d.nums[0]==7,"KL8 normalized parser");
  Draw p=h.parseLine("2026256 2026-09-23 0 5 1",1);require(p!=null&&p.nums[0]==0&&p.nums[2]==1,"leading zero parser");
  require(h.settle(ticket(0,1,7),d).amountPerBet==4.5,"decimal fixed award");
  Prize miss=h.settle(ticket(0,1,1),d);require(!miss.win&&!miss.floating&&miss.amountPerBet==0,"normal loss must not be pending");
  require(h.settle(ticket(0,7,1,2,3,4,5,6,12),d).amountPerBet==2,"zero-hit win");
  Prize floating=h.settle(ticket(0,9,7,8,9,10,11,13,22,24,25),d);require(floating.win&&floating.floating,"floating hit unresolved");
  h.kl8PrizeMap.get(d.issue).put("x9z9",0d);require(h.settle(ticket(0,9,7,8,9,10,11,13,22,24,25),d).floating,"zero actual winners cannot define counterfactual payout");
  Draw g3=new Draw("a","2026-09-01",new int[]{1,1,2});require(h.settle(ticket(1,1,2,1,1),g3).amountPerBet==346,"group3 ignores order");require(!h.settle(ticket(1,1,1,2,2),g3).win,"group3 multiplicity");
  Draw g6=new Draw("b","2026-09-01",new int[]{0,5,1});require(h.settle(ticket(2,2,5,1,0),g6).amountPerBet==173,"group6 leading zero");require(!h.settle(ticket(2,0,5,1,0),g6).win,"direct requires order");
  Draw p5=new Draw("c","2026-09-01",new int[]{4,4,9,1,0});require(h.settle(ticket(3,0,4,4,9,1,0),p5).amountPerBet==100000,"P5 direct award");
  h.totalCost=20;h.settledCost=2;h.totalReturn=4.5;h.unresolvedFloating=1;h.updateStats();require(h.statsTv.text.contains("125.00%")&&h.statsTv.text.contains("未结算投入：¥18"),"ROI excludes all pending stakes");
  System.out.println("PASS: actual Java parser, order/multiplicity, KL8 losses, zero-hit awards, decimals, floating awards and settled ROI");
 }
}'''
with tempfile.TemporaryDirectory() as temp:
    p=Path(temp)/'SettlementHarness.java';p.write_text(program)
    subprocess.run(['javac','-encoding','UTF-8',str(p)],check=True)
    subprocess.run(['java','-cp',temp,'SettlementHarness','app/src/main/assets/kl8_prizes.tsv'],check=True)
