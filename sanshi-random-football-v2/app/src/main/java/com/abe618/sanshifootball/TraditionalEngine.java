package com.abe618.sanshifootball;

import com.nlf.calendar.JieQi;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TraditionalEngine {
    private TraditionalEngine() {}

    private static final String GANS = "甲乙丙丁戊己庚辛壬癸";
    private static final String ZHIS = "子丑寅卯辰巳午未申酉戌亥";
    private static final List<String> JIAZI = buildJiaZi();

    public static final class CalendarInfo {
        public final int year, month, day, hour, minute;
        public final String yearGz, monthGz, dayGz, hourGz, jieQi, lunarText, xiu;

        CalendarInfo(int year, int month, int day, int hour, int minute,
                     String yearGz, String monthGz, String dayGz, String hourGz,
                     String jieQi, String lunarText, String xiu) {
            this.year = year; this.month = month; this.day = day;
            this.hour = hour; this.minute = minute;
            this.yearGz = yearGz; this.monthGz = monthGz;
            this.dayGz = dayGz; this.hourGz = hourGz;
            this.jieQi = normalizeTerm(jieQi);
            this.lunarText = lunarText;
            this.xiu = xiu;
        }

        public static CalendarInfo of(int year, int month, int day, int hour, int minute) {
            Solar solar = Solar.fromYmdHms(year, month, day, hour, minute, 0);
            Lunar lunar = solar.getLunar();
            JieQi prev = lunar.getPrevJieQi();
            String term = prev == null ? "" : prev.getName();
            return new CalendarInfo(
                    year, month, day, hour, minute,
                    lunar.getYearInGanZhiExact(),
                    lunar.getMonthInGanZhiExact(),
                    lunar.getDayInGanZhiExact(),
                    lunar.getTimeInGanZhi(),
                    term,
                    lunar.toString(),
                    lunar.getXiu()
            );
        }

        public String fourPillars() {
            return yearGz + "年  " + monthGz + "月  " + dayGz + "日  " + hourGz + "时";
        }

        public String dateTime() {
            return String.format(Locale.CHINA, "%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute);
        }
    }

    // ---------------------------------------------------------------------
    // 奇门遁甲
    // ---------------------------------------------------------------------

    public static final class QimenPalace {
        public final int number;
        public final String name;
        public String earthStem = "", heavenStem = "", star = "", door = "", god = "", extra = "";

        QimenPalace(int number, String name) {
            this.number = number; this.name = name;
        }

        public String display() {
            StringBuilder b = new StringBuilder();
            b.append(name);
            if (!god.isEmpty()) b.append("\n").append(god);
            if (!star.isEmpty() || !door.isEmpty()) b.append("\n").append(star).append("  ").append(door);
            if (!heavenStem.isEmpty() || !earthStem.isEmpty()) {
                b.append("\n天").append(heavenStem.isEmpty() ? "—" : heavenStem)
                 .append(" / 地").append(earthStem.isEmpty() ? "—" : earthStem);
            }
            if (!extra.isEmpty()) b.append("\n").append(extra);
            return b.toString();
        }
    }

    public static final class QimenResult {
        public final String mode, activeGanZhi, dun, yuan, note, zhiFu, zhiShi;
        public final int bureau;
        public final QimenPalace[] palaces;

        QimenResult(String mode, String activeGanZhi, String dun, int bureau, String yuan,
                    String note, String zhiFu, String zhiShi, QimenPalace[] palaces) {
            this.mode = mode; this.activeGanZhi = activeGanZhi; this.dun = dun;
            this.bureau = bureau; this.yuan = yuan; this.note = note;
            this.zhiFu = zhiFu; this.zhiShi = zhiShi; this.palaces = palaces;
        }

        public String header(CalendarInfo c) {
            return c.dateTime() + "　" + c.jieQi + "\n"
                    + c.fourPillars() + "\n"
                    + mode + "｜" + dun + bureau + "局｜" + yuan
                    + "\n主柱：" + activeGanZhi + "　值符：" + zhiFu + "　值使：" + zhiShi;
        }
    }

    private static final String[] FIXED_STARS = {
            "", "天蓬", "天芮", "天冲", "天辅", "天禽", "天心", "天柱", "天任", "天英"
    };
    private static final String[] PALACE_NAMES = {
            "", "坎一", "坤二", "震三", "巽四", "中五", "乾六", "兑七", "艮八", "离九"
    };
    private static final String[] SANQI_LIUYI = {"戊","己","庚","辛","壬","癸","丁","丙","乙"};
    private static final int[] LUOSHU_PATH = {1,8,3,4,9,2,7,6};
    private static final String[] ROTATE_STARS = {"天蓬","天任","天冲","天辅","天英","天芮","天柱","天心"};
    private static final String[] ROTATE_DOORS = {"休门","生门","伤门","杜门","景门","死门","惊门","开门"};
    private static final String[] GODS_YANG = {"值符","螣蛇","太阴","六合","勾陈","朱雀","九地","九天"};
    private static final String[] GODS_YIN = {"值符","螣蛇","太阴","六合","白虎","玄武","九地","九天"};

    public static QimenResult qimen(CalendarInfo c, String modeKey) {
        Ju ju;
        String activeGz;
        String mode;
        String note;

        switch (modeKey) {
            case "刻家10":
                activeGz = tenMinuteGanZhi(c);
                ju = quarterTenJu(c);
                mode = "刻家奇门·10分钟五马遁";
                note = "刻家A：参照堅奇門公开算法，按10分钟成一刻；子至巳时取阳遁、午至亥时取阴遁。";
                break;
            case "刻家12":
                Ju base = chaibuJu(c.jieQi, c.dayGz);
                int k = twelveMinuteIndex(c.hour, c.minute);
                activeGz = advanceJiaZi(c.hourGz, k);
                int moved = wrap9(base.bureau + (base.yang ? k : -k));
                ju = new Ju(base.yang, moved, "十分局第" + (k + 1) + "刻");
                mode = "刻家奇门·12分钟十分局";
                note = "刻家B：对照口径；一时辰分十刻，每刻12分钟，初刻承时家局，阳顺阴逆逐刻移局。";
                break;
            case "日家":
                activeGz = c.dayGz;
                ju = chaibuJu(c.jieQi, c.dayGz);
                mode = "日家奇门";
                note = "日家：以日柱作主柱；局数以节气三元拆补为V1基线。";
                break;
            case "月家":
                activeGz = c.monthGz;
                ju = monthJu(c.monthGz);
                mode = "月家奇门";
                note = "月家：V1采用月支逐月归一化定局，保留与《遁甲演义》月家法进一步逐表校验的接口。";
                break;
            case "年家":
                activeGz = c.yearGz;
                ju = yearJu(c.year, c.yearGz);
                mode = "年家奇门";
                note = "年家：按180年三元与年干分组归一化定局；用于古籍口径对照研究。";
                break;
            case "时家":
            default:
                activeGz = c.hourGz;
                ju = chaibuJu(c.jieQi, c.dayGz);
                mode = "时家奇门·拆补";
                note = "时家：二十四节气三元局数＋五日一元拆补；转盘九宫。";
                break;
        }

        QimenPalace[] p = arrangeQimen(ju.yang, ju.bureau, activeGz);
        int xunPalace = xunHeadPalace(activeGz, p);
        String zhiFu = FIXED_STARS[xunPalace];
        String zhiShi = fixedDoor(xunPalace);
        return new QimenResult(mode, activeGz, ju.yang ? "阳遁" : "阴遁",
                ju.bureau, ju.yuan, note, zhiFu, zhiShi, p);
    }

    private static QimenPalace[] arrangeQimen(boolean yang, int bureau, String activeGz) {
        QimenPalace[] p = new QimenPalace[10];
        for (int i = 1; i <= 9; i++) p[i] = new QimenPalace(i, PALACE_NAMES[i] + "宫");

        for (int i = 0; i < 9; i++) {
            int palace = yang ? wrap9(bureau + i) : wrap9(bureau - i);
            p[palace].earthStem = SANQI_LIUYI[i];
        }

        int xunPalace = xunHeadPalace(activeGz, p);
        String zhiFu = FIXED_STARS[xunPalace];
        String zhiShi = fixedDoor(xunPalace);

        String activeStem = dunStem(activeGz);
        int zhiFuLanding = findEarthStem(p, activeStem);
        if (zhiFuLanding == 5) zhiFuLanding = 2;

        int steps = GANS.indexOf(activeGz.charAt(0));
        int zhiShiLanding = advanceNine(xunPalace, steps, yang);
        if (zhiShiLanding == 5) zhiShiLanding = 2;

        String effectiveFu = "天禽".equals(zhiFu) ? "天芮" : zhiFu;
        int starStart = indexOf(ROTATE_STARS, effectiveFu);
        int palaceStart = indexOf(LUOSHU_PATH, zhiFuLanding);
        for (int i = 0; i < 8; i++) {
            int palace = LUOSHU_PATH[(palaceStart + i) % 8];
            String star = ROTATE_STARS[(starStart + i) % 8];
            p[palace].star = star;
            int home = starHome(star);
            p[palace].heavenStem = p[home].earthStem;
            if ("天芮".equals(star)) {
                p[palace].extra = "辅禽·" + p[5].earthStem;
            }
        }
        p[5].star = "天禽";

        int doorStart = indexOf(ROTATE_DOORS, zhiShi);
        int doorPalaceStart = indexOf(LUOSHU_PATH, zhiShiLanding);
        for (int i = 0; i < 8; i++) {
            int palace = LUOSHU_PATH[(doorPalaceStart + i) % 8];
            p[palace].door = ROTATE_DOORS[(doorStart + i) % 8];
        }

        int[] godPath = yang ? LUOSHU_PATH : reverse(LUOSHU_PATH);
        int godStart = indexOf(godPath, zhiFuLanding);
        String[] gods = yang ? GODS_YANG : GODS_YIN;
        for (int i = 0; i < 8; i++) {
            int palace = godPath[(godStart + i) % 8];
            p[palace].god = gods[i];
        }
        return p;
    }

    private static int xunHeadPalace(String gz, QimenPalace[] p) {
        int gi = GANS.indexOf(gz.charAt(0));
        int zi = ZHIS.indexOf(gz.charAt(1));
        int xunZhiIndex = mod(zi - gi, 12);
        String xun = "甲" + ZHIS.charAt(xunZhiIndex);
        String hidden;
        switch (xun) {
            case "甲子": hidden = "戊"; break;
            case "甲戌": hidden = "己"; break;
            case "甲申": hidden = "庚"; break;
            case "甲午": hidden = "辛"; break;
            case "甲辰": hidden = "壬"; break;
            case "甲寅": hidden = "癸"; break;
            default: hidden = "戊";
        }
        return findEarthStem(p, hidden);
    }

    private static String dunStem(String gz) {
        if (gz.charAt(0) != '甲') return gz.substring(0,1);
        switch (gz) {
            case "甲子": return "戊";
            case "甲戌": return "己";
            case "甲申": return "庚";
            case "甲午": return "辛";
            case "甲辰": return "壬";
            case "甲寅": return "癸";
            default: return "戊";
        }
    }

    private static int findEarthStem(QimenPalace[] p, String stem) {
        for (int i = 1; i <= 9; i++) if (stem.equals(p[i].earthStem)) return i;
        return 1;
    }

    private static String fixedDoor(int palace) {
        switch (palace) {
            case 1: return "休门";
            case 8: return "生门";
            case 3: return "伤门";
            case 4: return "杜门";
            case 9: return "景门";
            case 2: return "死门";
            case 7: return "惊门";
            case 6: return "开门";
            case 5:
            default: return "死门";
        }
    }

    private static int starHome(String star) {
        for (int i = 1; i <= 9; i++) if (FIXED_STARS[i].equals(star)) return i;
        return 5;
    }

    private static final class Ju {
        final boolean yang; final int bureau; final String yuan;
        Ju(boolean yang, int bureau, String yuan) {
            this.yang = yang; this.bureau = bureau; this.yuan = yuan;
        }
    }

    private static Ju chaibuJu(String rawTerm, String dayGz) {
        String term = normalizeTerm(rawTerm);
        boolean yang = Arrays.asList("冬至","小寒","大寒","立春","雨水","惊蛰","春分","清明","谷雨","立夏","小满","芒种").contains(term);
        int[] triple;
        switch (term) {
            case "冬至": case "惊蛰": triple = new int[]{1,7,4}; break;
            case "小寒": triple = new int[]{2,8,5}; break;
            case "大寒": case "春分": triple = new int[]{3,9,6}; break;
            case "立春": triple = new int[]{8,5,2}; break;
            case "雨水": triple = new int[]{9,6,3}; break;
            case "清明": case "立夏": triple = new int[]{4,1,7}; break;
            case "谷雨": case "小满": triple = new int[]{5,2,8}; break;
            case "芒种": triple = new int[]{6,3,9}; break;
            case "夏至": case "白露": triple = new int[]{9,3,6}; break;
            case "小暑": triple = new int[]{8,2,5}; break;
            case "大暑": case "秋分": triple = new int[]{7,1,4}; break;
            case "立秋": triple = new int[]{2,5,8}; break;
            case "处暑": triple = new int[]{1,4,7}; break;
            case "霜降": case "小雪": triple = new int[]{5,8,2}; break;
            case "寒露": case "立冬": triple = new int[]{6,9,3}; break;
            case "大雪": triple = new int[]{4,7,1}; break;
            default: triple = yang ? new int[]{1,7,4} : new int[]{9,3,6};
        }
        int idx = JIAZI.indexOf(dayGz);
        if (idx < 0) idx = 0;
        int yuanIndex = (idx / 5) % 3;
        String yuan = new String[]{"上元","中元","下元"}[yuanIndex];
        return new Ju(yang, triple[yuanIndex], yuan);
    }

    private static Ju quarterTenJu(CalendarInfo c) {
        String branch = c.hourGz.substring(1,2);
        boolean yang = "子丑寅卯辰巳".contains(branch);
        int idx = JIAZI.indexOf(c.hourGz);
        if (idx < 0) idx = 0;
        int yi = (idx / 5) % 3;
        int[] js = yang ? new int[]{1,7,4} : new int[]{9,3,6};
        return new Ju(yang, js[yi], new String[]{"上元","中元","下元"}[yi]);
    }

    private static Ju monthJu(String monthGz) {
        String order = "寅卯辰巳午未申酉戌亥子丑";
        int m = order.indexOf(monthGz.charAt(1)) + 1;
        if (m <= 0) m = 1;
        boolean yang = m <= 6;
        int ju = yang ? ((m - 1) % 9) + 1 : ((9 - ((m - 1) % 9)) % 9) + 1;
        return new Ju(yang, ju, "月局");
    }

    private static Ju yearJu(int year, String yearGz) {
        int pos = mod(year - 1864, 180);
        String yuan = pos < 60 ? "上元" : pos < 120 ? "中元" : "下元";
        boolean yang = !"中元".equals(yuan);
        char g = yearGz.charAt(0);
        int ju;
        if ("甲己丁壬".indexOf(g) >= 0) ju = 1;
        else if ("乙庚戊癸".indexOf(g) >= 0) ju = 7;
        else ju = 4;
        return new Ju(yang, ju, yuan);
    }

    private static String tenMinuteGanZhi(CalendarInfo c) {
        Lunar zero = Solar.fromYmdHms(c.year, c.month, c.day, 0, 0, 0).getLunar();
        char ziStem = zero.getTimeInGanZhi().charAt(0);
        String start;
        if ("丙辛".indexOf(ziStem) >= 0) start = "甲午";
        else if ("丁壬".indexOf(ziStem) >= 0) start = "丙午";
        else if ("戊癸".indexOf(ziStem) >= 0) start = "戊午";
        else if ("甲己".indexOf(ziStem) >= 0) start = "庚午";
        else start = "壬午";
        int step = c.hour * 6 + c.minute / 10;
        return advanceJiaZi(start, step);
    }

    private static int twelveMinuteIndex(int hour, int minute) {
        int fromStart;
        if (hour == 23) fromStart = minute;
        else if (hour == 0) fromStart = 60 + minute;
        else fromStart = (hour % 2 == 0 ? 60 : 0) + minute;
        return Math.max(0, Math.min(9, fromStart / 12));
    }

    public static QimenResult customQimen(CalendarInfo c, String mode, String activeGz,
                                          boolean yang, int bureau, String yuan, String note) {
        QimenPalace[] p = arrangeQimen(yang, bureau, activeGz);
        int xunPalace = xunHeadPalace(activeGz, p);
        String zhiFu = FIXED_STARS[xunPalace];
        String zhiShi = fixedDoor(xunPalace);
        return new QimenResult(mode, activeGz, yang ? "阳遁" : "阴遁",
                bureau, yuan, note, zhiFu, zhiShi, p);
    }

    public static String[] sixtyJiaZi() {
        return JIAZI.toArray(new String[0]);
    }

    public static String normalizeJieQiName(String t) {
        return normalizeTerm(t);
    }

    // ---------------------------------------------------------------------
    // 太乙神数：V1 四计七十二局基础盘
    // ---------------------------------------------------------------------

    public static final class TaiyiResult {
        public final String mode, activeGz, yinYang;
        public final long accumulated;
        public final int bureau;
        public final String taiyi, wenchang, shiji, jishen;
        public final int taiyiPalace, wenchangPalace, shijiPalace, jishenPalace;
        public final int lord, guest, fixed;
        public final int lordGeneral, lordAssistant, guestGeneral, guestAssistant;
        public final String note;

        TaiyiResult(String mode, String activeGz, String yinYang, long accumulated, int bureau,
                    String taiyi, int taiyiPalace, String wenchang, int wenchangPalace,
                    String shiji, int shijiPalace, String jishen, int jishenPalace,
                    int lord, int guest, int fixed, int lordGeneral, int lordAssistant,
                    int guestGeneral, int guestAssistant, String note) {
            this.mode=mode; this.activeGz=activeGz; this.yinYang=yinYang; this.accumulated=accumulated;
            this.bureau=bureau; this.taiyi=taiyi; this.taiyiPalace=taiyiPalace;
            this.wenchang=wenchang; this.wenchangPalace=wenchangPalace;
            this.shiji=shiji; this.shijiPalace=shijiPalace; this.jishen=jishen; this.jishenPalace=jishenPalace;
            this.lord=lord; this.guest=guest; this.fixed=fixed;
            this.lordGeneral=lordGeneral; this.lordAssistant=lordAssistant;
            this.guestGeneral=guestGeneral; this.guestAssistant=guestAssistant; this.note=note;
        }

        public String text(CalendarInfo c) {
            return c.dateTime()+"　"+c.jieQi+"\n"+c.fourPillars()+"\n\n"
                    +"【"+mode+"】 "+yinYang+"第"+bureau+"局　主柱："+activeGz+"\n"
                    +"积数："+accumulated+"\n\n"
                    +"太乙："+taiyi+"（"+palaceNameTaiyi(taiyiPalace)+"）\n"
                    +"文昌："+wenchang+"（"+palaceNameTaiyi(wenchangPalace)+"）\n"
                    +"始击："+shiji+"（"+palaceNameTaiyi(shijiPalace)+"）\n"
                    +"计神："+jishen+"（"+palaceNameTaiyi(jishenPalace)+"）\n\n"
                    +"主算 "+lord+"（"+countNature(lord)+"）　客算 "+guest+"（"+countNature(guest)+"）　定算 "+fixed+"（"+countNature(fixed)+"）\n"
                    +"主大将"+lordGeneral+"宫／参将"+lordAssistant+"宫　客大将"+guestGeneral+"宫／参将"+guestAssistant+"宫\n\n"
                    +note;
        }
    }

    private static final long TAIYI_BASE = 10153917L;
    private static final String TY_POINTS =
            "乾乾乾午午午艮艮艮卯卯卯酉酉酉坤坤坤子子子巽巽巽乾乾乾午午午艮艮艮卯卯卯酉酉酉坤坤坤子子子巽巽巽乾乾乾午午午艮艮艮卯卯卯酉酉酉坤坤坤子子子巽巽巽";
    private static final String WC_POINTS =
            "申酉戌乾乾亥子丑艮寅卯辰巽巳午未坤坤申酉戌乾乾亥子丑艮寅卯辰巽巳午未坤坤申酉戌乾乾亥子丑艮寅卯辰巽巳午未坤坤申酉戌乾乾亥子丑艮寅卯辰巽巳午未坤坤";
    private static final String WC_YIN_POINTS =
            "寅卯辰巽巽巳午未坤申酉戌乾亥子丑艮艮寅卯辰巽巽巳午未坤申酉戌乾亥子丑艮艮寅卯辰巽巽巳午未坤申酉戌乾亥子丑艮艮寅卯辰巽巽巳午未坤申酉戌乾亥子丑艮艮";
    private static final String SJ_POINTS =
            "坤戌亥丑寅辰巳坤酉乾丑寅辰午坤酉亥子艮辰巳未申戌亥艮卯巽未丑戌子艮卯巳午坤戌亥丑寅辰巳坤酉乾丑寅辰午坤酉亥子艮辰巳未申戌亥艮卯巽未丑戌子艮卯巳午";

    private static final int[][] TY_YANG_COUNTS = {
            {7,13,13},{6,1,1},{1,40,32},{25,17,10},{25,14,1},{25,10,12},
            {8,25,9},{1,22,3},{3,15,33},{1,12,25},{4,4,13},{37,1,4},
            {18,19,19},{10,9,9},{9,7,6},{1,33,26},{7,27,16},{7,26,11},
            {8,32,14},{7,26,2},{2,17,33},{16,30,1},{16,23,32},{16,17,23},
            {39,40,40},{32,31,31},{31,28,31},{14,9,38},{13,39,26},{10,32,17},
            {33,10,34},{25,8,24},{24,3,15},{26,4,11},{25,28,1},{25,27,36},
            {1,7,7},{6,35,35},{35,34,26},{27,19,12},{27,16,3},{27,12,34},
            {8,17,1},{23,14,32},{32,7,25},{5,16,29},{4,8,17},{1,5,8},
            {24,25,25},{16,15,15},{15,13,6},{39,31,24},{38,25,14},{38,24,9},
            {16,3,22},{15,34,10},{10,25,10},{12,26,27},{12,19,28},{12,13,19},
            {33,34,34},{26,25,25},{25,22,18},{16,11,7},{15,1,28},{12,34,19},
            {25,2,26},{17,8,16},{16,32,7},{30,4,15},{29,32,5},{29,31,9}
    };

    private static final int[][] TY_YIN_COUNTS = {
            {5,29,7},{4,17,1},{1,16,30},{25,33,2},{25,30,1},{17,26,10},
            {2,3,3},{1,7,7},{7,33,27},{1,24,25},{6,26,19},{35,23,8},
            {12,37,12},{12,27,11},{11,25,4},{1,15,24},{3,9,16},{3,8,9},
            {14,16,16},{13,10,10},{10,1,39},{24,14,1},{24,7,40},{16,1,29},
            {31,16,32},{30,7,29},{29,4,26},{8,25,32},{7,15,26},{2,8,15},
            {27,28,28},{27,26,26},{26,18,15},{29,22,9},{25,10,1},{25,9,34},
            {1,25,3},{4,13,37},{37,12,26},{33,1,10},{33,38,9},{25,34,38},
            {2,1,1},{39,38,38},{38,31,25},{7,1,31},{6,32,25},{1,29,14},
            {16,1,17},{16,31,15},{15,29,4},{33,7,16},{32,1,8},{32,8,1},
            {16,18,18},{15,12,12},{12,3,1},{18,8,35},{18,1,34},{10,35,25},
            {27,22,28},{26,3,25},{25,4,12},{16,33,3},{15,23,34},{10,16,23},
            {25,26,26},{25,24,24},{24,16,13},{32,28,15},{31,16,7},{31,15,1}
    };

    public static TaiyiResult taiyi(CalendarInfo c, String modeKey) {
        String mode, activeGz;
        long acc;
        switch (modeKey) {
            case "月计":
                mode="太乙月计"; activeGz=c.monthGz;
                String monthOrder="寅卯辰巳午未申酉戌亥子丑";
                int mo=monthOrder.indexOf(activeGz.charAt(1))+1; if(mo<1) mo=1;
                String julyGz=Solar.fromYmdHms(c.year,7,1,12,0,0).getLunar().getYearInGanZhiExact();
                int sy=c.yearGz.equals(julyGz)?c.year:c.year-1;
                acc=(TAIYI_BASE+sy-1L)*12L+2L+mo;
                break;
            case "日计":
                mode="太乙日计"; activeGz=c.dayGz;
                acc=708011105L-185L+daysBetween(1900,6,19,c.year,c.month,c.day);
                acc=alignToGanZhi(acc,activeGz);
                break;
            case "时计":
                mode="太乙时计"; activeGz=c.hourGz;
                long ad=708011105L+daysBetween(1900,12,21,c.year,c.month,c.day);
                int ti=(c.hour+1)/2;
                acc=(ad-1L)*12L+ti+1L;
                break;
            case "年计":
            default:
                mode="太乙年计"; activeGz=c.yearGz; acc=TAIYI_BASE+c.year;
                break;
        }

        boolean yin="时计".equals(modeKey) && isSummerHalf(c.jieQi);
        String yy=yin?"阴遁":"阳遁";
        int bureau=(int)positiveOneBased(acc,72);
        int idx=bureau-1;
        String typ=yin?reverseString(TY_POINTS):TY_POINTS;
        String wcp=yin?WC_YIN_POINTS:WC_POINTS;
        String ty=String.valueOf(typ.charAt(idx));
        String wc=String.valueOf(wcp.charAt(idx));
        String sj=String.valueOf(SJ_POINTS.charAt(idx));
        String branch=activeGz.substring(1,2);
        String js=yin?yinJiShen(branch):yangJiShen(branch);
        int tp=pointToTaiyiPalace(ty), wp=pointToTaiyiPalace(wc),
                sp=pointToTaiyiPalace(sj), jp=pointToTaiyiPalace(js);
        int[] counts=(yin?TY_YIN_COUNTS:TY_YANG_COUNTS)[idx];
        int lg=generalPalace(counts[0],true), la=assistantPalace(lg);
        int gg=generalPalace(counts[1],false), ga=assistantPalace(gg);
        String note="V1基础盘：四计/七十二局、太乙/文昌/始击/计神、主客定算及将参。分计属于现代延伸，暂单列研究，不混入古法四计。";
        return new TaiyiResult(mode,activeGz,yy,acc,bureau,ty,tp,wc,wp,sj,sp,js,jp,
                counts[0],counts[1],counts[2],lg,la,gg,ga,note);
    }

    private static long alignToGanZhi(long value,String gz){
        int idx=JIAZI.indexOf(gz);
        if(idx<0)return value;
        int expected=idx+1;
        int current=(int)positiveOneBased(value,60);
        return value+mod(expected-current,60);
    }

    private static boolean isSummerHalf(String term){
        return Arrays.asList("夏至","小暑","大暑","立秋","处暑","白露","秋分","寒露","霜降","立冬","小雪","大雪")
                .contains(normalizeTerm(term));
    }

    private static int pointToTaiyiPalace(String p){
        if("戌乾".contains(p))return 1;
        if("巳午".contains(p))return 2;
        if("丑艮".contains(p))return 3;
        if("寅卯".contains(p))return 4;
        if("申酉".contains(p))return 6;
        if("未坤".contains(p))return 7;
        if("亥子".contains(p))return 8;
        if("辰巽".contains(p))return 9;
        return 5;
    }

    private static String palaceNameTaiyi(int p){
        switch(p){
            case 1:return "1乾·西北·金";
            case 2:return "2离·南·火";
            case 3:return "3艮·东北·土";
            case 4:return "4震·东·木";
            case 6:return "6兑·西·金";
            case 7:return "7坤·西南·土";
            case 8:return "8坎·北·水";
            case 9:return "9巽·东南·木";
            default:return p+"宫";
        }
    }

    private static String yangJiShen(String z){
        String keys="子丑寅卯辰巳午未申酉戌亥", vals="寅丑子亥戌酉申未午巳辰卯";
        int i=keys.indexOf(z); return i>=0?String.valueOf(vals.charAt(i)):"寅";
    }
    private static String yinJiShen(String z){
        String keys="子丑寅卯辰巳午未申酉戌亥", vals="申未午巳辰卯寅丑子亥戌酉";
        int i=keys.indexOf(z); return i>=0?String.valueOf(vals.charAt(i)):"申";
    }

    private static int generalPalace(int value,boolean lord){
        if(lord && value%10==0)return 1;
        int r=value%10; return r==0?5:r;
    }
    private static int assistantPalace(int g){int r=(g*3)%10;return r==0?5:r;}

    private static String countNature(int v){
        switch(v){
            case 1:return "杂阴"; case 2:return "纯阴"; case 3:return "纯阳"; case 4:return "杂阳";
            case 6:return "纯阴"; case 7:return "杂阴"; case 8:return "杂阳"; case 9:return "纯阳";
            case 11:return "阴中重阳"; case 12:return "下和"; case 13:return "杂重阳"; case 14:return "上和";
            case 16:return "下和"; case 17:return "阴中重阳"; case 18:return "上和"; case 19:return "杂重阳";
            case 22:return "纯阴"; case 23:return "次和"; case 24:return "杂重阴"; case 26:return "纯阴";
            case 27:return "下和"; case 28:return "杂重阴"; case 29:return "次和"; case 31:return "杂重阳";
            case 32:return "次和"; case 33:return "纯阳"; case 34:return "下和"; case 37:return "杂重阳";
            case 38:return "下和"; case 39:return "纯阳"; default:return "常数";
        }
    }

    // ---------------------------------------------------------------------
    // 大六壬：月将、天地盘、四课 + 可确定的贼克三传
    // ---------------------------------------------------------------------

    public static final class LiuRenResult {
        public final String monthGeneral, monthGeneralName;
        public final LinkedHashMap<String,String> earthToSky;
        public final String[] lessons, relations, threePass;
        public final String methodNote;

        LiuRenResult(String mg,String mgn,LinkedHashMap<String,String> e2s,String[] l,String[] r,String[] t,String n){
            monthGeneral=mg;monthGeneralName=mgn;earthToSky=e2s;lessons=l;relations=r;threePass=t;methodNote=n;
        }

        public String text(CalendarInfo c){
            StringBuilder b=new StringBuilder();
            b.append(c.dateTime()).append("　").append(c.jieQi).append("\n").append(c.fourPillars()).append("\n\n");
            b.append("月将：").append(monthGeneral).append("·").append(monthGeneralName).append("\n");
            b.append("天地盘（地→天）：\n");
            int k=0;
            for(Map.Entry<String,String> e:earthToSky.entrySet()){
                b.append(e.getKey()).append("→").append(e.getValue()).append("　");
                if(++k%4==0)b.append("\n");
            }
            b.append("\n四课（下/上结构按堅六壬公开起课逻辑复核）：\n");
            String[] names={"四课","三课","二课","一课"};
            for(int i=0;i<4;i++)b.append(names[i]).append(" ").append(lessons[i]).append("　").append(relations[i]).append("\n");
            if(threePass!=null)b.append("\n三传：初").append(threePass[0]).append(" → 中").append(threePass[1]).append(" → 末").append(threePass[2]).append("\n");
            else b.append("\n三传：本课涉及多克/涉害/比用/遥克/昴星/别责/八专/伏返吟等复杂取传，V1不伪造，保留到V1.1完整九宗门引擎。\n");
            b.append("\n").append(methodNote);
            return b.toString();
        }
    }

    public static LiuRenResult liuren(CalendarInfo c){
        String mg=monthGeneral(c.jieQi);
        String[] earth=rotateBranches(c.hourGz.substring(1,2));
        String[] sky=rotateBranches(mg);
        LinkedHashMap<String,String> map=new LinkedHashMap<>();
        for(int i=0;i<12;i++)map.put(earth[i],sky[i]);

        String stem=c.dayGz.substring(0,1), branch=c.dayGz.substring(1,2);
        String yLower=stemJigong(stem);
        String yike=map.get(yLower)+stem;
        String erke=map.get(yike.substring(0,1))+yike.substring(0,1);
        String sanke=map.get(branch)+branch;
        String sike=map.get(sanke.substring(0,1))+sanke.substring(0,1);
        String[] lessons={sike,sanke,erke,yike};
        String[] rel=new String[4];
        int thieves=0,controls=0,ti=-1,ci=-1;
        for(int i=0;i<4;i++){
            rel[i]=relation(lessons[i].substring(0,1),lessons[i].substring(1,2));
            if("下贼上".equals(rel[i])){thieves++;ti=i;}
            if("上克下".equals(rel[i])){controls++;ci=i;}
        }
        String[] three=null;
        String first=null;
        if(thieves==1)first=lessons[ti].substring(0,1);
        else if(thieves==0&&controls==1)first=lessons[ci].substring(0,1);
        if(first!=null){
            String second=map.get(first), third=map.get(second);
            three=new String[]{first,second,third};
        }
        return new LiuRenResult(mg,monthGeneralName(mg),map,lessons,rel,three,
                "V1六壬先落地可核验的月将、天地盘与四课；唯一贼克/元首时给出三传，其余复杂九宗门明确留空，避免用简化算法冒充完整古法。");
    }

    private static String monthGeneral(String raw){
        String t=normalizeTerm(raw);
        if(Arrays.asList("雨水","惊蛰").contains(t))return "亥";
        if(Arrays.asList("春分","清明").contains(t))return "戌";
        if(Arrays.asList("谷雨","立夏").contains(t))return "酉";
        if(Arrays.asList("小满","芒种").contains(t))return "申";
        if(Arrays.asList("夏至","小暑").contains(t))return "未";
        if(Arrays.asList("大暑","立秋").contains(t))return "午";
        if(Arrays.asList("处暑","白露").contains(t))return "巳";
        if(Arrays.asList("秋分","寒露").contains(t))return "辰";
        if(Arrays.asList("霜降","立冬").contains(t))return "卯";
        if(Arrays.asList("小雪","大雪").contains(t))return "寅";
        if(Arrays.asList("冬至","小寒").contains(t))return "丑";
        return "子";
    }
    private static String monthGeneralName(String z){
        String keys="亥戌酉申未午巳辰卯寅丑子";
        String[] vals={"登明","河魁","从魁","传送","小吉","胜光","太乙","天罡","太冲","功曹","大吉","神后"};
        int i=keys.indexOf(z);return i>=0?vals[i]:"";
    }
    private static String stemJigong(String g){
        String keys="甲乙丙丁戊己庚辛壬癸",vals="寅辰巳未巳未申戌亥丑";
        int i=keys.indexOf(g);return i>=0?String.valueOf(vals.charAt(i)):"寅";
    }
    private static String relation(String upper,String lower){
        String u=element(upper),l=element(lower);
        if(u.equals(l))return "比和";
        if(controls(u,l))return "上克下";
        if(controls(l,u))return "下贼上";
        if(generates(u,l))return "上生下";
        if(generates(l,u))return "下生上";
        return "";
    }
    private static String element(String x){
        if("甲乙寅卯".contains(x))return "木";
        if("丙丁巳午".contains(x))return "火";
        if("戊己丑辰未戌".contains(x))return "土";
        if("庚辛申酉".contains(x))return "金";
        return "水";
    }
    private static boolean controls(String a,String b){
        return ("木".equals(a)&&"土".equals(b))||("土".equals(a)&&"水".equals(b))||
                ("水".equals(a)&&"火".equals(b))||("火".equals(a)&&"金".equals(b))||
                ("金".equals(a)&&"木".equals(b));
    }
    private static boolean generates(String a,String b){
        return ("木".equals(a)&&"火".equals(b))||("火".equals(a)&&"土".equals(b))||
                ("土".equals(a)&&"金".equals(b))||("金".equals(a)&&"水".equals(b))||
                ("水".equals(a)&&"木".equals(b));
    }

    // ---------------------------------------------------------------------
    // 演禽：值日二十八宿与禽星基础层
    // ---------------------------------------------------------------------

    public static final class YanqinResult {
        public final String xiu, fullName, luminary, previous, next, ring, note;
        YanqinResult(String x,String f,String l,String p,String n,String r,String note){
            xiu=x;fullName=f;luminary=l;previous=p;next=n;ring=r;this.note=note;
        }
        public String text(CalendarInfo c){
            return c.dateTime()+"　"+c.jieQi+"\n"+c.fourPillars()+"\n\n"
                    +"值日宿："+fullName+"\n七曜："+luminary+"曜\n"
                    +"前宿："+previous+"　后宿："+next+"\n\n二十八宿环：\n"+ring+"\n\n"+note;
        }
    }

    private static final String[] XQ = {
            "角木蛟","亢金龙","氐土貉","房日兔","心月狐","尾火虎","箕水豹",
            "斗木獬","牛金牛","女土蝠","虚日鼠","危月燕","室火猪","壁水貐",
            "奎木狼","娄金狗","胃土雉","昴日鸡","毕月乌","觜火猴","参水猿",
            "井木犴","鬼金羊","柳土獐","星日马","张月鹿","翼火蛇","轸水蚓"
    };

    public static YanqinResult yanqin(CalendarInfo c){
        int idx=0;
        for(int i=0;i<XQ.length;i++)if(XQ[i].startsWith(c.xiu)){idx=i;break;}
        DayOfWeek w=LocalDate.of(c.year,c.month,c.day).getDayOfWeek();
        String lum;
        switch(w){
            case MONDAY:lum="月";break;case TUESDAY:lum="火";break;case WEDNESDAY:lum="水";break;
            case THURSDAY:lum="木";break;case FRIDAY:lum="金";break;case SATURDAY:lum="土";break;
            default:lum="日";
        }
        StringBuilder ring=new StringBuilder();
        for(int i=0;i<XQ.length;i++){
            if(i==idx)ring.append("【").append(XQ[i]).append("】");
            else ring.append(XQ[i]);
            if(i!=XQ.length-1)ring.append(" · ");
            if((i+1)%7==0)ring.append("\n");
        }
        return new YanqinResult(c.xiu,XQ[idx],lum,XQ[mod(idx-1,28)],XQ[(idx+1)%28],ring.toString(),
                "V1演禽采用可核验的二十八宿/禽星基础层。翻禽倒将、《禽星易见》七元甲子局与《万化仙禽》命法属于不同支系，下一小版本分别实现，不把它们混成同一算法。");
    }

    public static String researchText(){
        return "【第一版资料整合】\n\n"
                +"一、体系关系\n"
                +"• 三式：奇门遁甲、太乙神数、大六壬。\n"
                +"• 演禽/禽星：以二十八宿、禽星、七曜等为核心的另一条传统；古籍中常与太乙、壬遁并列研究，但不是太乙的同义词。\n\n"
                +"二、奇门家别\n"
                +"• 年家、月家、日家、时家：古籍可见明确条文。\n"
                +"• 刻家：现存开源实现有不同口径。本软件同时保留10分钟五马遁与12分钟十分局两种对照。\n"
                +"• 分家、秒家：目前缺少足够一致的古籍证据，V1不把现代扩展冒充古法。\n\n"
                +"三、太乙\n"
                +"• V1实现年计、月计、日计、时计七十二局基础层。\n"
                +"• 分计在现代开源实现中明确属于细时间扩展，暂不并入古法四计。\n\n"
                +"四、大六壬\n"
                +"• V1先完成月将、天地盘、四课，并对唯一贼克/元首情形给出三传。\n"
                +"• 涉害、比用、遥克、昴星、别责、八专、伏吟、返吟等完整九宗门留到V1.1。\n\n"
                +"五、演禽\n"
                +"• V1完成二十八宿禽星基础盘。\n"
                +"• 后续拆分《禽星易见》翻禽倒将、演禽通纂/禄命、万化仙禽等支系。\n\n"
                +"【主要校验资料】\n"
                +"《遁甲演义》《奇门旨归》《禽星易见》《演禽通纂》《太乙金镜式经》；"
                +"GitHub: kentang2017/kinqimen、kintaiyi、kinliuren、yanqin_studies、kinastro；"
                +"历法基础使用 6tail/lunar-java。";
    }

    // ---------------------------------------------------------------------
    // Common helpers
    // ---------------------------------------------------------------------

    private static List<String> buildJiaZi(){
        List<String> out=new ArrayList<>(60);
        for(int i=0;i<60;i++)out.add(""+GANS.charAt(i%10)+ZHIS.charAt(i%12));
        return out;
    }

    private static String advanceJiaZi(String gz,int step){
        int i=JIAZI.indexOf(gz);if(i<0)i=0;return JIAZI.get(mod(i+step,60));
    }

    private static String normalizeTerm(String t){
        if(t==null)return "";
        return t.replace("驚","惊").replace("穀","谷").replace("處","处");
    }

    private static String[] rotateBranches(String start){
        String[] a=new String[12];int i=ZHIS.indexOf(start);
        if(i<0)i=0;
        for(int k=0;k<12;k++)a[k]=String.valueOf(ZHIS.charAt((i+k)%12));
        return a;
    }

    private static int advanceNine(int start,int steps,boolean yang){
        return wrap9(start+(yang?steps:-steps));
    }

    private static int wrap9(int n){return mod(n-1,9)+1;}
    private static int mod(int a,int n){int r=a%n;return r<0?r+n:r;}
    private static long positiveOneBased(long v,int cycle){
        long r=((v%cycle)+cycle)%cycle;return r==0?cycle:r;
    }
    private static long daysBetween(int y1,int m1,int d1,int y2,int m2,int d2){
        return ChronoUnit.DAYS.between(LocalDate.of(y1,m1,d1),LocalDate.of(y2,m2,d2));
    }
    private static int indexOf(String[] arr,String s){for(int i=0;i<arr.length;i++)if(arr[i].equals(s))return i;return 0;}
    private static int indexOf(int[] arr,int v){for(int i=0;i<arr.length;i++)if(arr[i]==v)return i;return 0;}
    private static int[] reverse(int[] a){int[] b=new int[a.length];for(int i=0;i<a.length;i++)b[i]=a[a.length-1-i];return b;}
    private static String reverseString(String s){return new StringBuilder(s).reverse().toString();}
}
