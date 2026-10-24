package com.abe618.sanshifootball;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Reuses the original TraditionalEngine and its original lunar-java 1.7.7 dependency.
 * Parses a known civil time; do not invent an event time, location or true solar time.
 * Qimen split-methods and Taiyi calendar editions are labelled by the legacy source.
 */
public final class TraditionalBridge {
  private static String q(Object obj){
    if(obj==null)return "null";
    String x=String.valueOf(obj);
    return "\""+x.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";
  }
  private static void kv(StringBuilder b,String key,Object value){if(b.charAt(b.length()-1)!='{')b.append(',');b.append(q(key)).append(':').append(value instanceof Number||value instanceof Boolean?String.valueOf(value):q(value));}
  private static String qimen(TraditionalEngine.CalendarInfo cal,String mode){
    TraditionalEngine.QimenResult p=TraditionalEngine.qimen(cal,mode);
    StringBuilder b=new StringBuilder("{");
    kv(b,"method",p.mode);kv(b,"active_ganzhi",p.activeGanZhi);kv(b,"dun",p.dun);kv(b,"bureau",p.bureau);
    kv(b,"yuan",p.yuan);kv(b,"zhifu",p.zhiFu);kv(b,"zhishi",p.zhiShi);kv(b,"note",p.note);
    b.append(",\"palaces\":[");
    for(int i=1;i<=9;i++){if(i>1)b.append(',');
      TraditionalEngine.QimenPalace x=p.palaces[i];
      b.append('{');
      kv(b,"number",i);kv(b,"name",x.name);kv(b,"earth",x.earthStem);kv(b,"heaven",x.heavenStem);
      kv(b,"door",x.door);kv(b,"star",x.star);kv(b,"god",x.god);b.append('}');
    }
    b.append("]}");return b.toString();
  }
  private static String taiyi(TraditionalEngine.CalendarInfo cal,String mode){
    TraditionalEngine.TaiyiResult t=TraditionalEngine.taiyi(cal,mode);
    StringBuilder b=new StringBuilder("{");
    kv(b,"mode",t.mode);kv(b,"active_ganzhi",t.activeGz);kv(b,"accumulation",t.accumulated);kv(b,"bureau",t.bureau);
    kv(b,"dun",t.yinYang);kv(b,"taiyi",t.taiyi);kv(b,"taiyi_palace",t.taiyiPalace);kv(b,"wenchang",t.wenchang);kv(b,"wenchang_palace",t.wenchangPalace);
    kv(b,"shiji",t.shiji);kv(b,"shiji_palace",t.shijiPalace);kv(b,"jishen",t.jishen);kv(b,"jishen_palace",t.jishenPalace);
    kv(b,"lord_count",t.lord);kv(b,"guest_count",t.guest);kv(b,"fixed_count",t.fixed);
    kv(b,"lord_general",t.lordGeneral);kv(b,"lord_assistant",t.lordAssistant);
    kv(b,"guest_general",t.guestGeneral);kv(b,"guest_assistant",t.guestAssistant);kv(b,"note",t.note);
    return b.append('}').toString();
  }
  private static String liuren(TraditionalEngine.CalendarInfo cal){
    TraditionalEngine.LiuRenResult x=TraditionalEngine.liuren(cal);
    StringBuilder b=new StringBuilder("{");
    kv(b,"month_general",x.monthGeneral);kv(b,"month_general_name",x.monthGeneralName);
    b.append(",\"heaven_earth\":{");for(Map.Entry<String,String> e:x.earthToSky.entrySet())kv(b,e.getKey(),e.getValue());b.append('}');
    b.append(",\"lessons\":[");for(int i=0;i<x.lessons.length;i++){if(i>0)b.append(',');b.append(q(x.lessons[i]));}b.append(']');
    b.append(",\"relations\":[");for(int i=0;i<x.relations.length;i++){if(i>0)b.append(',');b.append(q(x.relations[i]));}b.append(']');
    b.append(",\"three_pass\":");
    if(x.threePass==null)b.append("null");
    else{b.append('[');for(int i=0;i<x.threePass.length;i++){if(i>0)b.append(',');b.append(q(x.threePass[i]));}b.append(']');}
    kv(b,"note",x.methodNote);return b.append('}').toString();
  }
  private static String yanqin(TraditionalEngine.CalendarInfo cal){
    TraditionalEngine.YanqinResult x=TraditionalEngine.yanqin(cal);
    StringBuilder b=new StringBuilder("{");
    kv(b,"lunar_mansion",x.xiu);kv(b,"animal_mansion",x.fullName);kv(b,"weekday_luminary",x.luminary);
    kv(b,"previous",x.previous);kv(b,"next",x.next);kv(b,"note",x.note);return b.append('}').toString();
  }
  public static void main(String[] args){
    if(args.length!=1)throw new IllegalArgumentException("Require ISO local date-time YYYY-MM-DDTHH:mm; use actual date/time and document timezone separately");
    LocalDateTime time=LocalDateTime.parse(args[0],DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    if(time.getYear()<1900||time.getYear()>2099)throw new IllegalArgumentException("Supported research date 1900—2099");
    TraditionalEngine.CalendarInfo c=TraditionalEngine.CalendarInfo.of(time.getYear(),time.getMonthValue(),time.getDayOfMonth(),time.getHour(),time.getMinute());
    StringBuilder b=new StringBuilder("{");
    kv(b,"civil_time",args[0]);kv(b,"four_pillars",c.fourPillars());kv(b,"jieqi",c.jieQi);
    b.append(",\"qimen\":{");for(String mode:new String[]{"时家","刻家10","刻家12","日家","月家","年家"}){if(b.charAt(b.length()-1)!='{')b.append(',');b.append(q(mode)).append(':').append(qimen(c,mode));}b.append('}');
    b.append(",\"taiyi\":{");for(String mode:new String[]{"年计","月计","日计","时计"}){if(b.charAt(b.length()-1)!='{')b.append(',');b.append(q(mode)).append(':').append(taiyi(c,mode));}b.append('}');
    b.append(",\"liuren\":").append(liuren(c)).append(",\"yanqin\":").append(yanqin(c));
    kv(b,"limitations","Original TraditionalEngine: LiuRen only derives transmissions for unique simple thieves/overcoming; no complete 9 methods; 10/12 minute Qimen differ; no derived football probabilities");
    System.out.println(b.append('}'));
  }
}
