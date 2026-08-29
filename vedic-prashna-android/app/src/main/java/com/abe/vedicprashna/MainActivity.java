package com.abe.vedicprashna;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 1001;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final long FRESH_LOCATION_MS = 6L * 60L * 60L * 1000L;

    private TextView verdictText;
    private TextView probText;
    private TextView sensitivityText;
    private TextView summaryText;
    private TextView autoInfoText;
    private TextView chartMetaText;
    private TextView planetTableText;
    private TextView analysisDetailText;
    private TextView chartToggle;
    private TextView analysisToggle;

    private LinearLayout chartSection;
    private LinearLayout analysisSection;

    private VedicChartView d1Chart;
    private VedicChartView d9Chart;
    private Button analyzeButton;

    private final AstroEngine astro = new AstroEngine();
    private final PrashnaAnalyzer analyzer = new PrashnaAnalyzer();

    private LocationManager locationManager;
    private LocationListener oneShotListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        analyzeButton.setOnClickListener(v -> oneTapAnalyze());
        chartToggle.setOnClickListener(v -> toggle(chartSection, chartToggle, "排盘详情（D1 / D9 / 星曜位置）"));
        analysisToggle.setOnClickListener(v -> toggle(analysisSection, analysisToggle, "分析规则与评分明细"));
    }

    private void bindViews() {
        verdictText = findViewById(R.id.verdictText);
        probText = findViewById(R.id.probText);
        sensitivityText = findViewById(R.id.sensitivityText);
        summaryText = findViewById(R.id.summaryText);
        autoInfoText = findViewById(R.id.autoInfoText);
        chartMetaText = findViewById(R.id.chartMetaText);
        planetTableText = findViewById(R.id.planetTableText);
        analysisDetailText = findViewById(R.id.analysisDetailText);

        chartToggle = findViewById(R.id.chartToggle);
        analysisToggle = findViewById(R.id.analysisToggle);
        chartSection = findViewById(R.id.chartSection);
        analysisSection = findViewById(R.id.analysisSection);

        d1Chart = findViewById(R.id.d1Chart);
        d9Chart = findViewById(R.id.d9Chart);
        analyzeButton = findViewById(R.id.analyzeButton);
    }

    private void oneTapAnalyze() {
        analyzeButton.setEnabled(false);
        verdictText.setText("正在起盘…");
        autoInfoText.setText("正在读取当前分钟、系统时区和手机位置");

        if (!hasLocationPermission()) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQ_LOCATION
            );
            return;
        }

        obtainAutomaticLocation();
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void obtainAutomaticLocation() {
        if (locationManager == null) {
            fail("无法读取手机定位服务");
            return;
        }

        try {
            List<String> providers = locationManager.getProviders(true);
            Location best = null;

            for (String provider : providers) {
                Location loc = locationManager.getLastKnownLocation(provider);
                if (loc == null) continue;

                if (best == null
                        || loc.getTime() > best.getTime()
                        || (loc.hasAccuracy() && best.hasAccuracy() && loc.getAccuracy() < best.getAccuracy())) {
                    best = loc;
                }
            }

            if (best != null && System.currentTimeMillis() - best.getTime() <= FRESH_LOCATION_MS) {
                runAnalysis(best);
                return;
            }

            requestFreshLocation(best);
        } catch (SecurityException e) {
            fail("没有定位权限，请允许位置权限后重试");
        }
    }

    private void requestFreshLocation(Location fallback) {
        String provider = null;

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                provider = LocationManager.NETWORK_PROVIDER;
            } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                provider = LocationManager.GPS_PROVIDER;
            } else if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                provider = LocationManager.PASSIVE_PROVIDER;
            }
        } catch (Exception ignored) {
        }

        if (provider == null) {
            if (fallback != null) {
                runAnalysis(fallback);
            } else {
                fail("手机定位未开启，请开启系统定位后再点一次");
            }
            return;
        }

        final Location fallbackLocation = fallback;

        oneShotListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                stopLocationUpdates();
                if (location != null) {
                    runAnalysis(location);
                } else if (fallbackLocation != null) {
                    runAnalysis(fallbackLocation);
                } else {
                    fail("暂时无法取得位置，请开启定位后重试");
                }
            }

            @Override
            public void onProviderDisabled(String provider) {
                if (fallbackLocation != null) {
                    stopLocationUpdates();
                    runAnalysis(fallbackLocation);
                }
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
        };

        try {
            autoInfoText.setText("正在自动定位…");
            locationManager.requestSingleUpdate(provider, oneShotListener, Looper.getMainLooper());
        } catch (SecurityException e) {
            fail("没有定位权限，请允许位置权限后重试");
        } catch (Exception e) {
            if (fallbackLocation != null) {
                runAnalysis(fallbackLocation);
            } else {
                fail("自动定位失败，请开启系统定位后重试");
            }
        }
    }

    private void stopLocationUpdates() {
        if (locationManager != null && oneShotListener != null && hasLocationPermission()) {
            try {
                locationManager.removeUpdates(oneShotListener);
            } catch (Exception ignored) {
            }
        }
        oneShotListener = null;
    }

    private void runAnalysis(Location location) {
        try {
            OffsetDateTime now = OffsetDateTime.now().withSecond(0).withNano(0);
            Inputs in = new Inputs();
            in.time = now.toLocalDateTime();
            in.offset = now.getOffset();
            in.latitude = location.getLatitude();
            in.longitude = location.getLongitude();

            AstroEngine.ChartData chart = astro.calculate(in.time, in.offset, in.latitude, in.longitude);
            PrashnaAnalyzer.AnalysisResult result = analyzer.analyze(chart, "主方", "客方");

            verdictText.setText(result.verdict);
            probText.setText(result.compactProbabilities());
            summaryText.setText(result.summary);
            sensitivityText.setText(buildSensitivity(in));
            autoInfoText.setText(
                    "自动起盘：" + in.time.format(TIME_FMT)
                            + " " + in.offset
                            + " · 已自动定位"
            );

            d1Chart.setChart(chart, false);
            d9Chart.setChart(chart, true);
            chartMetaText.setText(buildChartMeta(chart));
            planetTableText.setText(buildPlanetTable(chart));
            analysisDetailText.setText(
                    "问事方式：当前时刻一键起盘"
                            + "\n主客映射：主方=第1宫，客方=第7宫"
                            + "\n无需输入球队名称或问题"
                            + "\n\n"
                            + result.detail
            );
        } catch (Exception e) {
            fail(e.getMessage() == null ? "起盘失败" : e.getMessage());
            return;
        }

        analyzeButton.setEnabled(true);
        analyzeButton.setText("重新起盘");
    }

    private String buildSensitivity(Inputs in) {
        AstroEngine.ChartData minus = astro.calculate(in.time.minusMinutes(1), in.offset, in.latitude, in.longitude);
        AstroEngine.ChartData now = astro.calculate(in.time, in.offset, in.latitude, in.longitude);
        AstroEngine.ChartData plus = astro.calculate(in.time.plusMinutes(1), in.offset, in.latitude, in.longitude);

        PrashnaAnalyzer.AnalysisResult a = analyzer.analyze(minus, "主方", "客方");
        PrashnaAnalyzer.AnalysisResult b = analyzer.analyze(now, "主方", "客方");
        PrashnaAnalyzer.AnalysisResult c = analyzer.analyze(plus, "主方", "客方");

        return "分钟敏感性：-1分 " + shortVerdict(a.verdict)
                + " / 当前 " + shortVerdict(b.verdict)
                + " / +1分 " + shortVerdict(c.verdict)
                + "；D1升 " + AstroEngine.SIGNS[minus.ascSign] + "→" + AstroEngine.SIGNS[now.ascSign] + "→" + AstroEngine.SIGNS[plus.ascSign]
                + "；D9升 " + AstroEngine.SIGNS[minus.d9AscSign] + "→" + AstroEngine.SIGNS[now.d9AscSign] + "→" + AstroEngine.SIGNS[plus.d9AscSign];
    }

    private static String shortVerdict(String s) {
        if (s.startsWith("主")) return "主";
        if (s.startsWith("客")) return "客";
        return "平";
    }

    private String buildChartMeta(AstroEngine.ChartData chart) {
        return "问事时间：" + chart.localTime.format(TIME_FMT) + " " + chart.offset
                + "\n问事地点：手机自动定位"
                + "\n坐标：" + String.format(Locale.US, "%.5f, %.5f", chart.latitude, chart.longitude)
                + "\nLahiri Ayanamsha：" + String.format(Locale.US, "%.6f°", chart.ayanamsa)
                + "\nD1上升：" + AstroEngine.formatDegree(chart.ascLongitude)
                + " · " + AstroEngine.NAKSHATRAS[chart.ascNakshatra] + " 第" + chart.ascPada + "足"
                + "\nD9上升：" + AstroEngine.SIGNS[chart.d9AscSign];
    }

    private String buildPlanetTable(AstroEngine.ChartData chart) {
        StringBuilder sb = new StringBuilder();
        sb.append("星曜   D1位置                宫  月宿/足             D9\n");
        for (AstroEngine.PlanetPlacement p : chart.planets) {
            sb.append(String.format(
                    Locale.US,
                    "%-4s %-20s %2d  %-16s %d  %s H%d%s\n",
                    p.name,
                    AstroEngine.formatDegree(p.longitude),
                    p.house,
                    AstroEngine.NAKSHATRAS[p.nakshatra],
                    p.pada,
                    AstroEngine.SIGNS[p.d9Sign],
                    p.d9House,
                    p.retrograde ? " R" : ""
            ));
        }
        return sb.toString();
    }

    private void fail(String message) {
        stopLocationUpdates();
        verdictText.setText("暂未起盘");
        autoInfoText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        analyzeButton.setEnabled(true);
        analyzeButton.setText("立即起盘");
    }

    private static void toggle(View section, TextView toggle, String label) {
        boolean show = section.getVisibility() != View.VISIBLE;
        section.setVisibility(show ? View.VISIBLE : View.GONE);
        toggle.setText((show ? "▼ " : "▶ ") + label);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQ_LOCATION) return;

        if (hasLocationPermission()) {
            obtainAutomaticLocation();
        } else {
            fail("需要位置权限才能按问事地点精准排盘");
        }
    }

    @Override
    protected void onDestroy() {
        stopLocationUpdates();
        super.onDestroy();
    }

    private static final class Inputs {
        LocalDateTime time;
        ZoneOffset offset;
        double latitude;
        double longitude;
    }
}
