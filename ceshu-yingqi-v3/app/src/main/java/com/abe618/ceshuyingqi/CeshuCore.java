package com.abe618.ceshuyingqi;
public final class CeshuCore {
 private CeshuCore(){}
 private static final String[] NAME={"乾","兑","离","震","巽","坎","艮","坤"};
 private static final int[][] G={{1,1,1},{1,1,0},{1,0,1},{1,0,0},{0,1,1},{0,1,0},{0,0,1},{0,0,0}};
 private static final int[] XT={1,2,3,4,5,6,7,8};
 private static final int[] HT={6,7,9,3,4,1,8,2};
 private static int idx(String s) {for(int i=0;i<NAME.length;i++)if(NAME[i].equals(s))return i;throw new IllegalArgumentException("卦名无效");}
 public static Result calculate(String upper,String lower,int moving,String system){
  if(moving<1||moving>6)throw new IllegalArgumentException("动爻1至6");
  boolean x="先天".equals(system);
  if(!x&&!"后天".equals(system))throw new IllegalArgumentException("数系无效");
  int u=idx(upper),l=idx(lower),base=0;
  for(int i=0;i<3;i++){base += x?(G[u][i]==1?36:24):(G[u][i]==1?128:112);base += x?(G[l][i]==1?36:24):(G[l][i]==1?128:112);}
  int a=x?XT[u]:HT[u],b=x?XT[l]:HT[l];
  int mult=1+(moving>=4?moving*10+a:b*10+moving);
  int total=base*mult+a+b+moving;
  return new Result(base,mult,total);
 }
 public static final class Result {
  public final int original,multiplier,total;
  public Result(int a,int b,int c){original=a;multiplier=b;total=c;}
  public String toJson(){return "{\"original\":"+original+",\"multiplier\":"+multiplier+",\"total\":"+total+"}";}
 }
}