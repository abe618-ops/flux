package com.abe.vedicprashna;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.thmac.swisseph.SweConst;
import de.thmac.swisseph.SweDate;
import de.thmac.swisseph.SwissEph;

public final class AstroEngine {
    public static final String[] SIGNS = {
            "白羊", "金牛", "双子", "巨蟹", "狮子", "处女",
            "天秤", "天蝎", "射手", "摩羯", "水瓶", "双鱼"
    };

    public static final String[] NAKSHATRAS = {
            "Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra",
            "Punarvasu", "Pushya", "Ashlesha", "Magha", "Purva Phalguni", "Uttara Phalguni",
            "Hasta", "Chitra", "Swati", "Vishakha", "Anuradha", "Jyeshtha",
            "Mula", "Purva Ashadha", "Uttara Ashadha", "Shravana", "Dhanishta", "Shatabhisha",
            "Purva Bhadrapada", "Uttara Bhadrapada", "Revati"
    };

    private static final int[] PLANET_IDS = {
            SweConst.SE_SUN, SweConst.SE_MOON, SweConst.SE_MARS,
            SweConst.SE_MERCURY, SweConst.SE_JUPITER, SweConst.SE_VENUS,
            SweConst.SE_SATURN, SweConst.SE_MEAN_NODE
    };

    private static final String[] PLANET_KEYS = {
            "Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu"
    };

    private static final String[] PLANET_NAMES = {
            "太阳", "月亮", "火星", "水星", "木星", "金星", "土星", "罗睺"
    };

    private static final String[] PLANET_SHORT = {
            "日", "月", "火", "水", "木", "金", "土", "罗"
    };

    public ChartData calculate(LocalDateTime localTime, ZoneOffset offset, double latitude, double longitude) {
        ZonedDateTime utc = localTime.toInstant(offset).atZone(ZoneOffset.UTC);
        double utcHour = utc.getHour()
                + utc.getMinute() / 60.0
                + utc.getSecond() / 3600.0;

        SweDate sd = new SweDate(utc.getYear(), utc.getMonthValue(), utc.getDayOfMonth(), utcHour);
        SwissEph sw = new SwissEph();
        sw.swe_set_sid_mode(SweConst.SE_SIDM_LAHIRI, 0, 0);

        double[] cusps = new double[13];
        double[] acsc = new double[10];
        int houseFlags = SweConst.SEFLG_SIDEREAL;
        int houseResult = sw.swe_houses(sd.getJulDay(), houseFlags, latitude, longitude, 'P', cusps, acsc);
        if (houseResult < 0) {
            throw new IllegalStateException("上升点计算失败");
        }

        double ascLongitude = normalize(acsc[0]);
        int ascSign = signOf(ascLongitude);
        int d9AscSign = navamsaSign(ascLongitude);
        int ascNak = nakshatraIndex(ascLongitude);
        int ascPada = pada(ascLongitude);
        double ayanamsa = sw.swe_get_ayanamsa_ut(sd.getJulDay());

        int flags = SweConst.SEFLG_MOSEPH
                | SweConst.SEFLG_SIDEREAL
                | SweConst.SEFLG_SPEED
                | SweConst.SEFLG_NONUT;

        List<PlanetPlacement> placements = new ArrayList<>();
        StringBuffer serr = new StringBuffer();

        for (int i = 0; i < PLANET_IDS.length; i++) {
            double[] xp = new double[6];
            serr.setLength(0);
            int ret = sw.swe_calc_ut(sd.getJulDay(), PLANET_IDS[i], flags, xp, serr);
            if (ret == SweConst.ERR) {
                throw new IllegalStateException("星曜计算失败：" + PLANET_NAMES[i] + " " + serr);
            }
            double lon = normalize(xp[0]);
            placements.add(makePlacement(
                    PLANET_KEYS[i], PLANET_NAMES[i], PLANET_SHORT[i],
                    lon, xp[3] < 0, ascSign, d9AscSign
            ));
        }

        PlanetPlacement rahu = findByKey(placements, "Rahu");
        double ketuLon = normalize(rahu.longitude + 180.0);
        placements.add(makePlacement("Ketu", "计都", "计", ketuLon, rahu.retrograde, ascSign, d9AscSign));

        return new ChartData(
                localTime, offset, latitude, longitude,
                ascLongitude, ascSign, d9AscSign, ascNak, ascPada, ayanamsa,
                Collections.unmodifiableList(placements)
        );
    }

    private PlanetPlacement makePlacement(
            String key, String name, String shortName, double lon, boolean retrograde,
            int ascSign, int d9AscSign
    ) {
        int sign = signOf(lon);
        int house = ((sign - ascSign + 12) % 12) + 1;
        int d9Sign = navamsaSign(lon);
        int d9House = ((d9Sign - d9AscSign + 12) % 12) + 1;
        int nak = nakshatraIndex(lon);
        int pada = pada(lon);
        return new PlanetPlacement(
                key, name, shortName, lon, sign, house, retrograde, nak, pada, d9Sign, d9House
        );
    }

    public static int signOf(double longitude) {
        return ((int) Math.floor(normalize(longitude) / 30.0)) % 12;
    }

    public static int navamsaSign(double longitude) {
        double lon = normalize(longitude);
        int rashi = signOf(lon);
        double within = lon - rashi * 30.0;
        int part = Math.min(8, (int) Math.floor(within / (10.0 / 3.0)));
        return (rashi * 9 + part) % 12;
    }

    public static int nakshatraIndex(double longitude) {
        double size = 360.0 / 27.0;
        return Math.min(26, (int) Math.floor(normalize(longitude) / size));
    }

    public static int pada(double longitude) {
        double padaSize = 360.0 / 108.0;
        return ((int) Math.floor(normalize(longitude) / padaSize) % 4) + 1;
    }

    public static double normalize(double degree) {
        double d = degree % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    public static String formatDegree(double longitude) {
        double lon = normalize(longitude);
        int sign = signOf(lon);
        double within = lon - sign * 30.0;
        int deg = (int) Math.floor(within);
        double m = (within - deg) * 60.0;
        int min = (int) Math.floor(m);
        int sec = (int) Math.round((m - min) * 60.0);
        if (sec == 60) {
            sec = 0;
            min++;
        }
        if (min == 60) {
            min = 0;
            deg++;
        }
        return String.format(Locale.US, "%s %02d°%02d′%02d″", SIGNS[sign], deg, min, sec);
    }

    private static PlanetPlacement findByKey(List<PlanetPlacement> list, String key) {
        for (PlanetPlacement p : list) {
            if (p.key.equals(key)) return p;
        }
        throw new IllegalStateException("Missing planet " + key);
    }

    public static final class PlanetPlacement {
        public final String key;
        public final String name;
        public final String shortName;
        public final double longitude;
        public final int sign;
        public final int house;
        public final boolean retrograde;
        public final int nakshatra;
        public final int pada;
        public final int d9Sign;
        public final int d9House;

        PlanetPlacement(
                String key, String name, String shortName, double longitude,
                int sign, int house, boolean retrograde, int nakshatra, int pada,
                int d9Sign, int d9House
        ) {
            this.key = key;
            this.name = name;
            this.shortName = shortName;
            this.longitude = longitude;
            this.sign = sign;
            this.house = house;
            this.retrograde = retrograde;
            this.nakshatra = nakshatra;
            this.pada = pada;
            this.d9Sign = d9Sign;
            this.d9House = d9House;
        }
    }

    public static final class ChartData {
        public final LocalDateTime localTime;
        public final ZoneOffset offset;
        public final double latitude;
        public final double longitude;
        public final double ascLongitude;
        public final int ascSign;
        public final int d9AscSign;
        public final int ascNakshatra;
        public final int ascPada;
        public final double ayanamsa;
        public final List<PlanetPlacement> planets;

        ChartData(
                LocalDateTime localTime, ZoneOffset offset, double latitude, double longitude,
                double ascLongitude, int ascSign, int d9AscSign,
                int ascNakshatra, int ascPada, double ayanamsa,
                List<PlanetPlacement> planets
        ) {
            this.localTime = localTime;
            this.offset = offset;
            this.latitude = latitude;
            this.longitude = longitude;
            this.ascLongitude = ascLongitude;
            this.ascSign = ascSign;
            this.d9AscSign = d9AscSign;
            this.ascNakshatra = ascNakshatra;
            this.ascPada = ascPada;
            this.ayanamsa = ayanamsa;
            this.planets = planets;
        }

        public PlanetPlacement find(String key) {
            for (PlanetPlacement p : planets) {
                if (p.key.equals(key)) return p;
            }
            throw new IllegalArgumentException("Unknown planet " + key);
        }

        public int signAtHouse(int house) {
            return (ascSign + house - 1) % 12;
        }
    }
}
