package com.abe.vedicprashna;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private EditText questionInput;
    private EditText homeInput;
    private EditText awayInput;
    private EditText timeInput;
    private EditText timezoneInput;
    private EditText latitudeInput;
    private EditText longitudeInput;

    private TextView verdictText;
    private TextView probText;
    private TextView sensitivityText;
    private TextView summaryText;
    private TextView chartMetaText;
    private TextView planetTableText;
    private TextView analysisDetailText;
    private TextView chartToggle;
    private TextView analysisToggle;

    private LinearLayout chartSection;
    private LinearLayout analysisSection;

    private VedicChartView d1Chart;
    private VedicChartView d9Chart;

    private final AstroEngine astro = new AstroEngine();
    private final PrashnaAnalyzer analyzer = new PrashnaAnalyzer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setCurrentMinute();

        Button analyzeButton = findViewById(R.id.analyzeButton);
        Button nowButton = findViewById(R.id.nowButton);
        Button minusMinute = findViewById(R.id.minusMinute);
        Button plusMinute = findViewById(R.id.plusMinute);

        analyzeButton.setOnClickListener(v -> runAnalysis());
        nowButton.setOnClickListener(v -> setCurrentMinute());
        minusMinute.setOnClickListener(v -> shiftMinute(-1));
        plusMinute.setOnClickListener(v -> shiftMinute(1));

        chartToggle.setOnClickListener(v -> toggle(chartSection, chartToggle, "排盘详情（D1 / D9 / 星曜位置）"));
        analysisToggle.setOnClickListener(v -> toggle(analysisSection, analysisToggle, "分析规则与评分明细"));
    }

    private void bindViews() {
        questionInput = findViewById(R.id.questionInput);
        homeInput = findViewById(R.id.homeInput);
        awayInput = findViewById(R.id.awayInput);
        timeInput = findViewById(R.id.timeInput);
        timezoneInput = findViewById(R.id.timezoneInput);
        latitudeInput = findViewById(R.id.latitudeInput);
        longitudeInput = findViewById(R.id.longitudeInput);

        verdictText = findViewById(R.id.verdictText);
        probText = findViewById(R.id.probText);
        sensitivityText = findViewById(R.id.sensitivityText);
        summaryText = findViewById(R.id.summaryText);
        chartMetaText = findViewById(R.id.chartMetaText);
        planetTableText = findViewById(R.id.planetTableText);
        analysisDetailText = findViewById(R.id.analysisDetailText);

        chartToggle = findViewById(R.id.chartToggle);
        analysisToggle = findViewById(R.id.analysisToggle);
        chartSection = findViewById(R.id.chartSection);
        analysisSection = findViewById(R.id.analysisSection);

        d1Chart = findViewById(R.id.d1Chart);
        d9Chart = findViewById(R.id.d9Chart);
    }

    private void setCurrentMinute() {
        OffsetDateTime now = OffsetDateTime.now().withSecond(0).withNano(0);
        timeInput.setText(now.toLocalDateTime().format(TIME_FMT));
        timezoneInput.setText(now.getOffset().getId());
    }

    private void shiftMinute(int delta) {
        try {
            LocalDateTime t = LocalDateTime.parse(timeInput.getText().toString().trim(), TIME_FMT);
            timeInput.setText(t.plusMinutes(delta).format(TIME_FMT));
        } catch (DateTimeParseException e) {
            Toast.makeText(this, "时间格式应为 yyyy-MM-dd HH:mm", Toast.LENGTH_SHORT).show();
        }
    }

    private void runAnalysis() {
        try {
            Inputs in = readInputs();
            AstroEngine.ChartData chart = astro.calculate(in.time, in.offset, in.latitude, in.longitude);
            PrashnaAnalyzer.AnalysisResult result = analyzer.analyze(chart, in.home, in.away);

            verdictText.setText(result.verdict);
            probText.setText(result.compactProbabilities());
            summaryText.setText(result.summary);
            analysisDetailText.setText(buildQuestionHeader(in) + "\n\n" + result.detail);

            d1Chart.setChart(chart, false);
            d9Chart.setChart(chart, true);
            chartMetaText.setText(buildChartMeta(chart));
            planetTableText.setText(buildPlanetTable(chart));
            sensitivityText.setText(buildSensitivity(in));
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage() == null ? "起盘失败" : e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Inputs readInputs() {
        String timeText = timeInput.getText().toString().trim();
        String offsetText = timezoneInput.getText().toString().trim();
        String latText = latitudeInput.getText().toString().trim();
        String lonText = longitudeInput.getText().toString().trim();

        if (latText.isEmpty() || lonText.isEmpty()) {
            throw new IllegalArgumentException("请填写问事地点的纬度和经度");
        }

        LocalDateTime time;
        try {
            time = LocalDateTime.parse(timeText, TIME_FMT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("问事时间格式应为 yyyy-MM-dd HH:mm");
        }

        ZoneOffset offset;
        try {
            offset = ZoneOffset.of(offsetText);
        } catch (Exception e) {
            throw new IllegalArgumentException("时区格式示例：+08:00、-05:00");
        }

        double lat;
        double lon;
        try {
            lat = Double.parseDouble(latText);
            lon = Double.parseDouble(lonText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("经纬度请输入数字");
        }

        if (lat < -90 || lat > 90) throw new IllegalArgumentException("纬度范围应为 -90 到 90");
        if (lon < -180 || lon > 180) throw new IllegalArgumentException("经度范围应为 -180 到 180");

        Inputs in = new Inputs();
        in.time = time;
        in.offset = offset;
        in.latitude = lat;
        in.longitude = lon;
        in.home = homeInput.getText().toString().trim();
        in.away = awayInput.getText().toString().trim();
        in.question = questionInput.getText().toString().trim();
        return in;
    }

    private String buildSensitivity(Inputs in) {
        AstroEngine.ChartData minus = astro.calculate(in.time.minusMinutes(1), in.offset, in.latitude, in.longitude);
        AstroEngine.ChartData now = astro.calculate(in.time, in.offset, in.latitude, in.longitude);
        AstroEngine.ChartData plus = astro.calculate(in.time.plusMinutes(1), in.offset, in.latitude, in.longitude);

        PrashnaAnalyzer.AnalysisResult a = analyzer.analyze(minus, in.home, in.away);
        PrashnaAnalyzer.AnalysisResult b = analyzer.analyze(now, in.home, in.away);
        PrashnaAnalyzer.AnalysisResult c = analyzer.analyze(plus, in.home, in.away);

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
                + "\n问事地点：" + String.format(Locale.US, "%.5f, %.5f", chart.latitude, chart.longitude)
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

    private String buildQuestionHeader(Inputs in) {
        String q = in.question.isEmpty() ? "未填写具体问题" : in.question;
        String h = in.home.isEmpty() ? "主队" : in.home;
        String a = in.away.isEmpty() ? "客队" : in.away;
        return "问题：" + q + "\n对阵：" + h + " vs " + a;
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
        String home;
        String away;
        String question;
    }
}
