package com.abe.vedicprashna;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // 历史随机范围：公元1年1月1日至运行时的昨天。
    // 不使用“当前时刻”作为盘面，只把当天日期用作历史区间上限。
    private static final LocalDate HISTORY_START = LocalDate.of(1, 1, 1);

    // 为避免极区上升/宫位计算不稳定，纬度限制在南北65度之间。
    private static final double MIN_LATITUDE = -65.0;
    private static final double MAX_LATITUDE = 65.0;

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
    private final SecureRandom random = new SecureRandom();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();

        analyzeButton.setOnClickListener(v -> randomAnalyze());
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

    private void randomAnalyze() {
        analyzeButton.setEnabled(false);
        verdictText.setText("正在随机起盘…");
        autoInfoText.setText("正在随机抽取历史日期、分钟和全球地点");

        try {
            Inputs in = randomInputs();
            AstroEngine.ChartData chart = astro.calculate(in.time, in.offset, in.latitude, in.longitude);
            PrashnaAnalyzer.AnalysisResult result = analyzer.analyze(chart, "主方", "客方");

            verdictText.setText(result.verdict);
            probText.setText(result.compactProbabilities());
            summaryText.setText(result.summary);
            sensitivityText.setText(buildSensitivity(in));

            autoInfoText.setText(
                    "随机盘：" + in.time.format(TIME_FMT)
                            + " UTC"
                            + " · " + formatCoordinate(in.latitude, in.longitude)
            );

            d1Chart.setChart(chart, false);
            d9Chart.setChart(chart, true);
            chartMetaText.setText(buildChartMeta(chart));
            planetTableText.setText(buildPlanetTable(chart));

            analysisDetailText.setText(
                    "起盘方式：完全随机历史时刻"
                            + "\n历史范围：公元1年1月1日至运行时昨天"
                            + "\n随机精度：分钟"
                            + "\n随机地点：全球经度，纬度限制在南北65°以内"
                            + "\n主客映射：主方=第1宫，客方=第7宫"
                            + "\n每次点击都会重新生成独立随机盘"
                            + "\n\n"
                            + result.detail
            );

            analyzeButton.setText("再随机一盘");
        } catch (Exception e) {
            String message = e.getMessage() == null ? "随机起盘失败" : e.getMessage();
            verdictText.setText("暂未起盘");
            autoInfoText.setText(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            analyzeButton.setText("随机起盘");
        } finally {
            analyzeButton.setEnabled(true);
        }
    }

    private Inputs randomInputs() {
        LocalDate end = LocalDate.now().minusDays(1);
        if (end.isBefore(HISTORY_START)) {
            end = HISTORY_START;
        }

        long startDay = HISTORY_START.toEpochDay();
        long endDay = end.toEpochDay();
        long dayCount = endDay - startDay + 1L;
        long dayOffset = positiveMod(random.nextLong(), dayCount);

        LocalDate date = LocalDate.ofEpochDay(startDay + dayOffset);
        int minuteOfDay = random.nextInt(24 * 60);
        LocalTime time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60);

        Inputs in = new Inputs();
        in.time = LocalDateTime.of(date, time);

        // 统一以UTC表达随机历史瞬间，避免把现代时区制度强行套到古代。
        in.offset = ZoneOffset.UTC;

        in.latitude = MIN_LATITUDE + random.nextDouble() * (MAX_LATITUDE - MIN_LATITUDE);
        in.longitude = -180.0 + random.nextDouble() * 360.0;

        return in;
    }

    private static long positiveMod(long value, long bound) {
        if (bound <= 0L) return 0L;
        long nonNegative = value & Long.MAX_VALUE;
        return nonNegative % bound;
    }

    private String buildSensitivity(Inputs in) {
        AstroEngine.ChartData minus = astro.calculate(in.time.minusMinutes(1), in.offset, in.latitude, in.longitude);
        AstroEngine.ChartData now = astro.calculate(in.time, in.offset, in.latitude, in.longitude);
        AstroEngine.ChartData plus = astro.calculate(in.time.plusMinutes(1), in.offset, in.latitude, in.longitude);

        PrashnaAnalyzer.AnalysisResult a = analyzer.analyze(minus, "主方", "客方");
        PrashnaAnalyzer.AnalysisResult b = analyzer.analyze(now, "主方", "客方");
        PrashnaAnalyzer.AnalysisResult c = analyzer.analyze(plus, "主方", "客方");

        return "随机时刻±1分：-1分 " + shortVerdict(a.verdict)
                + " / 本盘 " + shortVerdict(b.verdict)
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
        return "随机历史时刻：" + chart.localTime.format(TIME_FMT) + " UTC"
                + "\n随机地点：" + formatCoordinate(chart.latitude, chart.longitude)
                + "\nLahiri Ayanamsha：" + String.format(Locale.US, "%.6f°", chart.ayanamsa)
                + "\nD1上升：" + AstroEngine.formatDegree(chart.ascLongitude)
                + " · " + AstroEngine.NAKSHATRAS[chart.ascNakshatra] + " 第" + chart.ascPada + "足"
                + "\nD9上升：" + AstroEngine.SIGNS[chart.d9AscSign];
    }

    private static String formatCoordinate(double latitude, double longitude) {
        return String.format(Locale.US, "%.5f°, %.5f°", latitude, longitude);
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

    private static void toggle(View section, TextView toggle, String label) {
        boolean show = section.getVisibility() != View.VISIBLE;
        section.setVisibility(show ? View.VISIBLE : View.GONE);
        toggle.setText((show ? "▼ " : "▶ ") + label);
    }

    private static final class Inputs {
        LocalDateTime time;
        ZoneOffset offset;
        double latitude;
        double longitude;
    }
}
