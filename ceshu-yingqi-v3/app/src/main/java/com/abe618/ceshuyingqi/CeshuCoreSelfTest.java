package com.abe618.ceshuyingqi;
public final class CeshuCoreSelfTest {
 public static void main(String[] args){
  chk(180,59,10634,"坤","乾",5,"先天");
  chk(720,42,30254,"坎","离",4,"后天");
  chk(216,12,2595,"乾","乾",1,"先天");
  boolean error=false;
  try{CeshuCore.calculate("乾","坤",0,"先天");}catch(IllegalArgumentException e){error=true;}
  if(!error)throw new AssertionError("动爻校验未生效");
  System.out.println("CeshuCore 4 groups passed");
 }
 static void chk(int base,int multi,int total,String up,String low,int mov,String sys){
  CeshuCore.Result r=CeshuCore.calculate(up,low,mov,sys);
  if(r.original!=base||r.multiplier!=multi||r.total!=total)throw new AssertionError(sys+" "+up+low+": "+r.toJson());
 }
}