package com.abe618.liuyaopick3;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

public final class LiuyaoEngine {
    private LiuyaoEngine() {}

    public enum Mode { TIME, RANDOM, LIVE }

    private static final String GANS = "甲乙丙丁戊己庚辛壬癸";
    private static final String ZHIS = "子丑寅卯辰巳午未申酉戌亥";
    private static final String[] ELEMENTS = {"木","火","土","金","水"};
    private static final String[] TRIS = {"坤","震","坎","兑","艮","离","巽","乾"};
    private static final String[] TRI_ELEMENT = {"土","木","水","金","土","火","木","金"};
    private static final int[] TRI_NUMBER = {2,3,1,7,8,9,4,6};

    // 纳甲地支：下卦初二三爻 / 上卦四五上爻，索引直接使用三位阴阳码0..7。
    private static final String[][] INNER = {
            {"未","巳","卯"}, // 坤
            {"子","寅","辰"}, // 震
            {"寅","辰","午"}, // 坎
            {"巳","卯","丑"}, // 兑
            {"辰","午","申"}, // 艮
            {"卯","丑","亥"}, // 离
            {"丑","亥","酉"}, // 巽
            {"子","寅","辰"}  // 乾
    };
    private static final String[][] OUTER = {
            {"丑","亥","酉"},
            {"午","申","戌"},
            {"申","戌","子"},
            {"亥","酉","未"},
            {"戌","子","寅"},
            {"酉","未","巳"},
            {"未","巳","卯"},
            {"午","申","戌"}
    };

    // 行=上卦，列=下卦；三卦顺序与TRIS一致。
    private static final String[][] HEX_NAMES = {
            {"坤为地","地雷复","地水师","地泽临","地山谦","地火明夷","地风升","地天泰"},
            {"雷地豫","震为雷","雷水解","雷泽归妹","雷山小过","雷火丰","雷风恒","雷天大壮"},
            {"水地比","水雷屯","坎为水","水泽节","水山蹇","水火既济","水风井","水天需"},
            {"泽地萃","泽雷随","泽水困","兑为泽","泽山咸","泽火革","泽风大过","泽天夬"},
            {"山地剥","山雷颐","山水蒙","山泽损","艮为山","山火贲","山风蛊","山天大畜"},
            {"火地晋","火雷噬嗑","火水未济","火泽睽","火山旅","离为火","火风鼎","火天大有"},
            {"风地观","风雷益","风水涣","风泽中孚","风山渐","风火家人","巽为风","风天小畜"},
            {"天地否","天雷无妄","天水讼","天泽履","天山遁","天火同人","天风姤","乾为天"}
    };

    private static final String[] SIX_SPIRITS = {"青龙","朱雀","勾陈","螣蛇","白虎","玄武"};
    private static final List<String> JIAZI = buildJiaZi();

    public static final class Line {
        public int no;
        public int value;
        public boolean yang;
        public boolean moving;
        public String branch;
        public String changedBranch;
        public String element;
        public String relative;
        public String spirit;
        public boolean voided;
        public int strength;
        public String strengthText;
        public int preferred;
        public int alternate;

        public String display() {
            String yao = yang ? "———" : "— —";
            String mov = moving ? (value == 9 ? " ○" : " ×") : "  ";
            String changed = moving ? " →" + changedBranch : "";
            return no + "爻 " + spirit + " " + relative + branch + element + " "
                    + yao + mov + changed + "  " + strengthText
                    + (voided ? " 空" : "") + " 取" + preferred + "（备" + alternate + "）";
        }
    }

    public static final class Analysis {
        public String game;
        public Mode mode;
        public LocalDateTime effectiveTime;
        public String masterSeed;
        public String fourPillars;
        public String lunarText;
        public String hexName;
        public String changedHexName;
        public String palace;
        public int shi;
        public int ying;
        public Line[] lines;
        public double[] digitScores;
        public double[][] positionScores;
        public int[] topDigits;
        public List<String> directTop;
        public List<String> groupTop;
        public int[] sumTop;
        public Integer sumClue;
        public int coreLine;
        public Map<Integer,List<String>> traces;
        public String text;

        public boolean directContains(String n) {
            for (String s : directTop) if (s.startsWith(n + " ")) return true;
            return false;
        }

        public boolean groupContains(String n) {
            char[] a=n.toCharArray(); Arrays.sort(a); String key=new String(a);
            for(String s:groupTop) if(s.startsWith(key+" ")) return true;
            return false;
        }
    }

    private static final class PalaceInfo {
        int tri;
        int stage;
        int shi;
        int ying;
    }

    private static final class Combo {
        final int a,b,c;
        final double score;
        Combo(int a,int b,int c,double score){this.a=a;this.b=b;this.c=c;this.score=score;}
        String num(){ return ""+a+b+c; }
        String group(){ int[] x={a,b,c}; Arrays.sort(x); return ""+x[0]+x[1]+x[2]; }
    }

    public static Analysis run(String game, Mode mode, LocalDate date, LocalTime requestedTime, String seedInput) {
        LocalDateTime effective;
        String master;

        if (mode == Mode.TIME) {
            effective = LocalDateTime.of(date, requestedTime);
            master = sha256Hex(game + "|TIME|" + effective + "|liuyao-v1");
        } else if (mode == Mode.RANDOM) {
            effective = LocalDateTime.of(date, requestedTime);
            if (seedInput == null || seedInput.trim().isEmpty()) {
                byte[] b = new byte[32];
                new SecureRandom().nextBytes(b);
                master = hex(b);
            } else {
                master = sha256Hex(game + "|RANDOM|" + seedInput.trim());
            }
        } else {
            LocalTime draw = "福彩3D".equals(game) ? LocalTime.of(21,15) : LocalTime.of(21,25);
            String base;
            if (seedInput == null || seedInput.trim().isEmpty()) {
                byte[] b = new byte[32]; new SecureRandom().nextBytes(b); base = hex(b);
            } else base = sha256Hex("LIVE|" + seedInput.trim());
            long s = longFromHash(base + "|" + game + "|" + date);
            SplittableRandom r = new SplittableRandom(s);
            int max = draw.getHour()*60 + draw.getMinute();
            int minute = r.nextInt(Math.max(1,max));
            effective = LocalDateTime.of(date, LocalTime.of(minute/60, minute%60));
            master = sha256Hex(base + "|" + effective + "|liuyao-live-v1");
        }

        Solar solar = Solar.fromYmdHms(
                effective.getYear(), effective.getMonthValue(), effective.getDayOfMonth(),
                effective.getHour(), effective.getMinute(), 0);
        Lunar lunar = solar.getLunar();
        String yearGz = lunar.getYearInGanZhiExact();
        String monthGz = lunar.getMonthInGanZhiExact();
        String dayGz = lunar.getDayInGanZhiExact();
        String timeGz = lunar.getTimeInGanZhi();

        int[] vals = castLines(master, effective, game);
        int code = hexCode(vals);
        int changedCode = changedCode(vals);
        PalaceInfo pi = palaceInfo(code);

        String monthBranch = monthGz.substring(1,2);
        String dayBranch = dayGz.substring(1,2);
        String dayStem = dayGz.substring(0,1);
        Set<String> voids = voidBranches(dayGz);
        String palaceElement = TRI_ELEMENT[pi.tri];

        Line[] lines = new Line[6];
        for (int i=0;i<6;i++) {
            Line ln = new Line();
            ln.no = i+1;
            ln.value = vals[i];
            ln.yang = vals[i] == 7 || vals[i] == 9;
            ln.moving = vals[i] == 6 || vals[i] == 9;
            ln.branch = lineBranch(code,i);
            ln.changedBranch = lineBranch(changedCode,i);
            ln.element = branchElement(ln.branch);
            ln.relative = relative(palaceElement,ln.element);
            ln.spirit = spirit(dayStem,i);
            ln.voided = voids.contains(ln.branch);
            ln.strength = strength(ln.branch, monthBranch, dayBranch, ln.moving, ln.voided);
            ln.strengthText = ln.strength > 0 ? "旺相" : "休囚";
            int[] pair = elementDigits(ln.element);
            ln.alternate = ln.strength > 0 ? pair[0] : pair[1];
            ln.preferred = ln.strength > 0 ? pair[1] : pair[0];
            if (ln.voided && ln.strength <= 0) {
                ln.alternate = ln.preferred;
                ln.preferred = 0;
            }
            lines[i] = ln;
        }

        Analysis a = new Analysis();
        a.game = game;
        a.mode = mode;
        a.effectiveTime = effective;
        a.masterSeed = master;
        a.fourPillars = yearGz+"年  "+monthGz+"月  "+dayGz+"日  "+timeGz+"时";
        a.lunarText = lunar.toString();
        a.hexName = hexName(code);
        a.changedHexName = hexName(changedCode);
        a.palace = TRIS[pi.tri] + "宫";
        a.shi = pi.shi;
        a.ying = pi.ying;
        a.lines = lines;
        a.traces = new LinkedHashMap<>();
        for(int i=0;i<10;i++) a.traces.put(i,new ArrayList<>());

        a.digitScores = new double[10];
        double[] role = new double[6];
        int core = 0;
        double coreScore = -999;

        for (int i=0;i<6;i++) {
            Line ln=lines[i];
            double w=0.8;
            if ("妻财".equals(ln.relative)) w += 4.0;
            if (ln.no == a.ying) w += 4.0;
            if (ln.no == a.shi) w += 2.0;
            if (ln.moving) w += 1.6;
            if (ln.moving && ln.strength > 0) w += 1.4;
            if (ln.voided) w -= 0.5;
            role[i]=w;

            add(a,ln.preferred,1.0+w,
                    ln.no+"爻"+ln.relative+(ln.no==a.ying?"·应":"")+(ln.no==a.shi?"·世":"")
                    +" "+ln.branch+ln.element+" "+ln.strengthText+"→"+ln.preferred);
            add(a,ln.alternate,Math.max(0.3,(1.0+w)*0.30),
                    ln.no+"爻"+ln.element+"备用数→"+ln.alternate);

            if (ln.voided) {
                double ew = ln.strength > 0 ? 1.1 : 2.8;
                add(a,0,ew,ln.no+"爻旬空→0（空亡支路）");
            }

            double cs = w + ln.strength*0.45;
            if (cs>coreScore) { coreScore=cs; core=i; }
        }
        a.coreLine = core+1;
        Line c = lines[core];

        // 旺动核心、临月建与月序。
        int lunarMonth = Math.abs(lunar.getMonth()) % 10;
        if (c.branch.equals(monthBranch)) {
            add(a,lunarMonth,3.2,"核心"+c.branch+"临月建→农历月序"+lunarMonth);
        }
        int coreBranchNum = branchOrderDigit(c.branch);
        add(a,coreBranchNum,0.7,"核心地支"+c.branch+"序数→"+coreBranchNum);

        // 动变支：变支序数优先作为直接数；若被日建六合，注明合绊但保留候选。
        if (c.moving) {
            int d=branchOrderDigit(c.changedBranch);
            add(a,d,2.6,"核心动变"+c.branch+"→"+c.changedBranch+"，变支序数→"+d);
            if (combine(c.changedBranch,dayBranch)) {
                addTrace(a,d,c.changedBranch+"与日建"+dayBranch+"六合：按“合绊”记录，不删除原候选");
            }
        }

        // 以核心爻向外看六合、六冲、生克、墓，形成关系链候选。
        for (int i=0;i<6;i++) {
            if(i==core) continue;
            Line t=lines[i];
            if (combine(c.branch,t.branch)) {
                int d=branchOrderDigit(t.branch);
                add(a,d,1.8,c.branch+"合"+t.branch+"→取合支序数"+d);
            }
            if (clash(c.branch,t.branch)) {
                int d=branchOrderDigit(t.branch);
                add(a,d,1.1,c.branch+"冲"+t.branch+"→冲支序数"+d);
                if (isTombedBy(t.branch,dayBranch)) {
                    int composite=(d+3)%10;
                    add(a,composite,2.0,c.branch+"冲"+t.branch+"且日建"+dayBranch+"墓"+t.branch
                            +"→“冲+墓”复合数"+composite+"（实验规则）");
                }
            }
            if (generates(c.element,t.element)) {
                add(a,c.preferred,1.3,c.branch+c.element+"生"+t.branch+t.element
                        +"→生出关系强化核心本数"+c.preferred);
            }
            if (controls(c.element,t.element)) {
                add(a,t.preferred,0.8,c.branch+c.element+"克"+t.branch+t.element
                        +"→受制爻候选"+t.preferred+"降权保留");
            }
        }

        // 卦象数字：后天八卦数作为低权辅助。
        int lower=code&7, upper=(code>>3)&7;
        add(a,TRI_NUMBER[lower],0.9,"下卦"+TRIS[lower]+"后天数"+TRI_NUMBER[lower]);
        add(a,TRI_NUMBER[upper],0.9,"上卦"+TRIS[upper]+"后天数"+TRI_NUMBER[upper]);

        // 用户案例中出现的“机锋字形/和值”规则，单独低权标记，避免与五行直接数混淆。
        Integer sumClue = null;
        if ("雷山小过".equals(a.hexName)) {
            add(a,7,1.5,"卦名机锋《雷山小过》：山/小过字形案例强化7");
            add(a,8,1.5,"卦名机锋《雷山小过》：八形/雨部案例强化8");
            sumClue=24;
        } else if ("天风姤".equals(a.hexName)) {
            add(a,7,1.4,"卦名机锋《天风姤》：天/风/午类7象案例");
            add(a,8,1.4,"卦名机锋《天风姤》：八形案例");
            add(a,9,1.6,"卦名机锋《天风姤》：姤九笔/午字增笔案例");
            sumClue=24;
        }
        if ("雷山小过".equals(a.changedHexName) || "天风姤".equals(a.changedHexName)) {
            add(a,7,0.6,"变卦机锋→7（低权复核）");
            add(a,8,0.6,"变卦机锋→8（低权复核）");
        }
        a.sumClue=sumClue;

        a.topDigits=topK(a.digitScores,6);
        a.positionScores=positionScores(a,role);
        List<Combo> combos=rankCombos(a);
        a.directTop=new ArrayList<>();
        for(int i=0;i<Math.min(20,combos.size());i++) {
            Combo x=combos.get(i);
            a.directTop.add(x.num()+"  "+String.format(Locale.CHINA,"%.2f",x.score));
        }

        Map<String,Double> groups=new HashMap<>();
        for(int i=0;i<Math.min(120,combos.size());i++) {
            Combo x=combos.get(i);
            groups.merge(x.group(),x.score,Math::max);
        }
        List<Map.Entry<String,Double>> ge=new ArrayList<>(groups.entrySet());
        ge.sort((x,y)->Double.compare(y.getValue(),x.getValue()));
        a.groupTop=new ArrayList<>();
        for(int i=0;i<Math.min(12,ge.size());i++)
            a.groupTop.add(ge.get(i).getKey()+"  "+String.format(Locale.CHINA,"%.2f",ge.get(i).getValue()));

        a.sumTop = topSums(combos);
        a.text = render(a, monthBranch, dayBranch, voids);
        return a;
    }

    private static double[][] positionScores(Analysis a,double[] role) {
        double[][] m=new double[3][10];
        for(int p=0;p<3;p++) for(int d=0;d<10;d++) m[p][d]=a.digitScores[d]*0.58;

        // 定位实验层：上两爻→百位，中两爻→十位，下两爻→个位；与组选层分开统计。
        int[][] map={{5,4},{3,2},{1,0}};
        for(int p=0;p<3;p++) {
            for(int idx:map[p]) {
                Line ln=a.lines[idx];
                double x=1.5+role[idx]*0.45;
                m[p][ln.preferred]+=x;
                m[p][ln.alternate]+=x*0.28;
                if(ln.voided) m[p][0]+=0.8;
            }
        }
        // 世、应、核心爻所在层再加一点定位权重。
        for(int p=0;p<3;p++) {
            for(int idx:map[p]) {
                Line ln=a.lines[idx];
                if(ln.no==a.ying) m[p][ln.preferred]+=1.2;
                if(ln.no==a.shi) m[p][ln.preferred]+=0.7;
                if(ln.no==a.coreLine) m[p][ln.preferred]+=1.5;
            }
        }
        return m;
    }

    private static List<Combo> rankCombos(Analysis a) {
        List<Combo> out=new ArrayList<>(1000);
        Set<Integer> global=new LinkedHashSet<>();
        for(int i=0;i<Math.min(5,a.topDigits.length);i++) global.add(a.topDigits[i]);
        for(int x=0;x<10;x++) for(int y=0;y<10;y++) for(int z=0;z<10;z++) {
            double s=a.positionScores[0][x]+a.positionScores[1][y]+a.positionScores[2][z];
            if(global.contains(x))s+=0.4;
            if(global.contains(y))s+=0.4;
            if(global.contains(z))s+=0.4;
            int sum=x+y+z;
            if(a.sumClue!=null && sum==a.sumClue)s+=3.0;
            out.add(new Combo(x,y,z,s));
        }
        out.sort((u,v)->Double.compare(v.score,u.score));
        return out;
    }

    private static int[] topSums(List<Combo> combos) {
        double[] best=new double[28];
        Arrays.fill(best,-1e9);
        for(int i=0;i<Math.min(200,combos.size());i++) {
            Combo c=combos.get(i);
            int s=c.a+c.b+c.c;
            best[s]=Math.max(best[s],c.score);
        }
        return topK(best,5);
    }

    private static String render(Analysis a,String monthBranch,String dayBranch,Set<String> voids) {
        StringBuilder b=new StringBuilder();
        b.append("【六爻三位数研究 V1】\n")
                .append(a.game).append("｜").append(modeName(a.mode)).append("\n")
                .append("起盘：").append(a.effectiveTime).append("\n")
                .append("农历：").append(a.lunarText).append("\n")
                .append("四柱：").append(a.fourPillars).append("\n")
                .append("月建：").append(monthBranch).append("　日建：").append(dayBranch)
                .append("　旬空：").append(voids).append("\n")
                .append("主卦：").append(a.hexName).append("　变卦：").append(a.changedHexName).append("\n")
                .append("卦宫：").append(a.palace).append("　世").append(a.shi).append("　应").append(a.ying)
                .append("　核心：").append(a.coreLine).append("爻\n")
                .append("Seed：").append(a.masterSeed.substring(0,16)).append("…\n\n");

        b.append("【六爻盘｜上爻在上】\n");
        for(int i=5;i>=0;i--) b.append(a.lines[i].display()).append("\n");

        b.append("\n【多维取数】\n")
                .append("规则：妻财=号码本体；应爻=开奖中心；世爻=求测方/结果；旺动爻=信息枢纽。\n")
                .append("旺相取大数：水6 火7 木8 金9 土5；休囚取小数：水1 火2 木3 金4 土0。\n")
                .append("空亡：弱空优先0；旺空保留五行本数并辅取0。\n")
                .append("关系链：临月建/月序、动变支序、六合、六冲、墓、生克分别计分。\n")
                .append("机锋层与直接五行数分开计分，只在重复共振时增权。\n\n");

        b.append("数字共振 Top6：");
        for(int d:a.topDigits) b.append(d).append("(")
                .append(String.format(Locale.CHINA,"%.1f",a.digitScores[d])).append(") ");
        b.append("\n");
        if(a.sumClue!=null)b.append("卦名机锋和值提示：").append(a.sumClue).append("\n");

        b.append("\n【共振来源】\n");
        for(int i=0;i<Math.min(6,a.topDigits.length);i++) {
            int d=a.topDigits[i];
            b.append(d).append("：");
            List<String> ts=a.traces.get(d);
            for(int j=0;j<Math.min(5,ts.size());j++) {
                if(j>0)b.append("；");
                b.append(ts.get(j));
            }
            b.append("\n");
        }

        String[] labels={"百","十","个"};
        b.append("\n【定位胆实验层】\n");
        for(int p=0;p<3;p++) {
            int[] top=topK(a.positionScores[p],5);
            b.append(labels[p]).append("位 Top5：");
            for(int d:top)b.append(d).append(" ");
            b.append("\n");
        }

        b.append("\n直选 Top20：\n");
        for(int i=0;i<a.directTop.size();i++) {
            b.append(a.directTop.get(i));
            b.append(i%4==3?"\n":"　");
        }
        b.append("\n组选 Top12：\n");
        for(int i=0;i<a.groupTop.size();i++) {
            b.append(a.groupTop.get(i));
            b.append(i%4==3?"\n":"　");
        }
        b.append("\n和值 Top5：").append(Arrays.toString(a.sumTop)).append("\n");

        if(!a.directTop.isEmpty()) {
            String n=a.directTop.get(0).substring(0,3);
            int x=n.charAt(0)-'0',y=n.charAt(1)-'0',z=n.charAt(2)-'0';
            int max=Math.max(x,Math.max(y,z)),min=Math.min(x,Math.min(y,z));
            b.append("首组结构：").append(n).append("｜和值").append(x+y+z)
                    .append("｜跨度").append(max-min)
                    .append("｜奇偶").append((x%2)+(y%2)+(z%2)).append(":").append(3-((x%2)+(y%2)+(z%2)))
                    .append("｜大小").append((x>=5?1:0)+(y>=5?1:0)+(z>=5?1:0)).append(":")
                    .append(3-((x>=5?1:0)+(y>=5?1:0)+(z>=5?1:0))).append("\n");
        }

        b.append("\n说明：这是传统六爻/象数规则的可复现研究与盲测工具，不把任何映射表述为确定性预测。")
                .append("“候选池/组选/定位胆/和值”分开统计，避免开奖后倒推。");
        return b.toString();
    }

    private static int[] castLines(String master,LocalDateTime t,String game) {
        long seed=longFromHash(master+"|cast|"+t+"|"+game);
        SplittableRandom r=new SplittableRandom(seed);
        int[] v=new int[6];
        for(int i=0;i<6;i++) {
            int sum=0;
            for(int k=0;k<3;k++)sum+=r.nextBoolean()?3:2;
            v[i]=sum;
        }
        return v;
    }

    private static int hexCode(int[] vals) {
        int code=0;
        for(int i=0;i<6;i++) if(vals[i]==7||vals[i]==9) code|=(1<<i);
        return code;
    }
    private static int changedCode(int[] vals) {
        int code=hexCode(vals);
        for(int i=0;i<6;i++) if(vals[i]==6||vals[i]==9) code^=(1<<i);
        return code;
    }

    private static String lineBranch(int code,int lineIndex) {
        if(lineIndex<3) return INNER[code&7][lineIndex];
        return OUTER[(code>>3)&7][lineIndex-3];
    }

    private static String hexName(int code) {
        return HEX_NAMES[(code>>3)&7][code&7];
    }

    private static PalaceInfo palaceInfo(int code) {
        int[] shi={6,1,2,3,4,5,4,3};
        for(int tri=0;tri<8;tri++) {
            int base=tri|(tri<<3);
            int[] seq=new int[8];
            seq[0]=base;
            seq[1]=seq[0]^(1<<0);
            seq[2]=seq[1]^(1<<1);
            seq[3]=seq[2]^(1<<2);
            seq[4]=seq[3]^(1<<3);
            seq[5]=seq[4]^(1<<4);
            seq[6]=seq[5]^(1<<3);
            seq[7]=seq[6]^0b111;
            for(int k=0;k<8;k++) if(seq[k]==code) {
                PalaceInfo p=new PalaceInfo();
                p.tri=tri;p.stage=k;p.shi=shi[k];
                p.ying=p.shi<=3?p.shi+3:p.shi-3;
                return p;
            }
        }
        PalaceInfo p=new PalaceInfo();
        p.tri=code&7;p.stage=0;p.shi=6;p.ying=3;
        return p;
    }

    private static String relative(String palaceElem,String lineElem) {
        if(palaceElem.equals(lineElem))return "兄弟";
        if(generates(palaceElem,lineElem))return "子孙";
        if(generates(lineElem,palaceElem))return "父母";
        if(controls(palaceElem,lineElem))return "妻财";
        return "官鬼";
    }

    private static int strength(String branch,String month,String day,boolean moving,boolean voided) {
        String e=branchElement(branch), me=branchElement(month), de=branchElement(day);
        int s=0;
        if(e.equals(me))s+=3;
        else if(generates(me,e))s+=2;
        else if(controls(me,e))s-=3;
        else if(controls(e,me))s-=2;
        else if(generates(e,me))s-=1;

        if(e.equals(de))s+=2;
        else if(generates(de,e))s+=1;
        else if(controls(de,e))s-=2;
        else if(controls(e,de))s-=1;

        if(branch.equals(month))s+=4;
        if(branch.equals(day))s+=3;
        if(clash(branch,month))s-=3;
        if(clash(branch,day))s-=2;
        if(moving)s+=1;
        if(voided)s-=2;
        return s;
    }

    private static String spirit(String dayStem,int lineIndex) {
        int start;
        switch(dayStem) {
            case "甲": case "乙": start=0;break;
            case "丙": case "丁": start=1;break;
            case "戊": start=2;break;
            case "己": start=3;break;
            case "庚": case "辛": start=4;break;
            default: start=5;
        }
        return SIX_SPIRITS[(start+lineIndex)%6];
    }

    private static Set<String> voidBranches(String dayGz) {
        int idx=JIAZI.indexOf(dayGz);
        String[][] pairs={{"戌","亥"},{"申","酉"},{"午","未"},{"辰","巳"},{"寅","卯"},{"子","丑"}};
        if(idx<0)return new LinkedHashSet<>();
        int x=idx/10;
        return new LinkedHashSet<>(Arrays.asList(pairs[x]));
    }

    private static List<String> buildJiaZi() {
        List<String> a=new ArrayList<>();
        for(int i=0;i<60;i++)a.add(""+GANS.charAt(i%10)+ZHIS.charAt(i%12));
        return a;
    }

    private static String branchElement(String b) {
        if("寅卯".contains(b))return "木";
        if("巳午".contains(b))return "火";
        if("申酉".contains(b))return "金";
        if("亥子".contains(b))return "水";
        return "土";
    }

    private static int[] elementDigits(String e) {
        switch(e) {
            case "水": return new int[]{1,6};
            case "火": return new int[]{2,7};
            case "木": return new int[]{3,8};
            case "金": return new int[]{4,9};
            default: return new int[]{0,5};
        }
    }

    private static boolean generates(String a,String b) {
        return ("木".equals(a)&&"火".equals(b))
                ||("火".equals(a)&&"土".equals(b))
                ||("土".equals(a)&&"金".equals(b))
                ||("金".equals(a)&&"水".equals(b))
                ||("水".equals(a)&&"木".equals(b));
    }

    private static boolean controls(String a,String b) {
        return ("木".equals(a)&&"土".equals(b))
                ||("土".equals(a)&&"水".equals(b))
                ||("水".equals(a)&&"火".equals(b))
                ||("火".equals(a)&&"金".equals(b))
                ||("金".equals(a)&&"木".equals(b));
    }

    private static boolean combine(String a,String b) {
        String p=a+b;
        return p.equals("子丑")||p.equals("丑子")||p.equals("寅亥")||p.equals("亥寅")
                ||p.equals("卯戌")||p.equals("戌卯")||p.equals("辰酉")||p.equals("酉辰")
                ||p.equals("巳申")||p.equals("申巳")||p.equals("午未")||p.equals("未午");
    }

    private static boolean clash(String a,String b) {
        String p=a+b;
        return p.equals("子午")||p.equals("午子")||p.equals("丑未")||p.equals("未丑")
                ||p.equals("寅申")||p.equals("申寅")||p.equals("卯酉")||p.equals("酉卯")
                ||p.equals("辰戌")||p.equals("戌辰")||p.equals("巳亥")||p.equals("亥巳");
    }

    private static boolean isTombedBy(String target,String day) {
        String e=branchElement(target);
        if("木".equals(e))return "未".equals(day);
        if("火".equals(e))return "戌".equals(day);
        if("金".equals(e))return "丑".equals(day);
        if("水".equals(e))return "辰".equals(day);
        return "辰戌丑未".contains(day);
    }

    private static int branchOrderDigit(String b) {
        int i=ZHIS.indexOf(b);
        if(i<0)return 0;
        return (i+1)%10;
    }

    private static void add(Analysis a,int digit,double score,String trace) {
        if(digit<0||digit>9)return;
        a.digitScores[digit]+=score;
        addTrace(a,digit,trace);
    }

    private static void addTrace(Analysis a,int digit,String trace) {
        List<String> list=a.traces.get(digit);
        if(list!=null && !list.contains(trace))list.add(trace);
    }

    private static int[] topK(double[] v,int k) {
        Integer[] idx=new Integer[v.length];
        for(int i=0;i<v.length;i++)idx[i]=i;
        Arrays.sort(idx,(a,b)->Double.compare(v[b],v[a]));
        k=Math.min(k,idx.length);
        int[] out=new int[k];
        for(int i=0;i<k;i++)out[i]=idx[i];
        return out;
    }

    private static String modeName(Mode m) {
        switch(m){
            case TIME:return "时间盘";
            case RANDOM:return "随机数盘";
            default:return "当日活时盘";
        }
    }

    private static long longFromHash(String s) {
        try {
            byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(b,0,8).getLong();
        } catch(Exception e) { throw new IllegalStateException(e); }
    }

    private static String sha256Hex(String s) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch(Exception e) { throw new IllegalStateException(e); }
    }

    private static String hex(byte[] b) {
        StringBuilder s=new StringBuilder();
        for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x&255));
        return s.toString();
    }
}
