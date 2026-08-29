package com.abe.vedicprashna;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class VedicChartView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint signPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private AstroEngine.ChartData chart;
    private boolean d9;

    public VedicChartView(Context context) {
        super(context);
        init();
    }

    public VedicChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VedicChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackgroundColor(Color.rgb(255, 253, 248));
        linePaint.setColor(Color.rgb(92, 77, 65));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.0f);

        signPaint.setColor(Color.rgb(130, 112, 96));
        signPaint.setTextAlign(Paint.Align.CENTER);
        signPaint.setTextSize(25f);

        planetPaint.setColor(Color.rgb(48, 38, 30));
        planetPaint.setTextAlign(Paint.Align.CENTER);
        planetPaint.setTextSize(30f);
        planetPaint.setFakeBoldText(true);
    }

    public void setChart(AstroEngine.ChartData chart, boolean d9) {
        this.chart = chart;
        this.d9 = d9;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float size = Math.min(w, h) - 28f;
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;
        float right = left + size;
        float bottom = top + size;
        float cx = (left + right) / 2f;
        float cy = (top + bottom) / 2f;

        canvas.drawRect(left, top, right, bottom, linePaint);
        canvas.drawLine(left, top, cx, cy, linePaint);
        canvas.drawLine(right, top, cx, cy, linePaint);
        canvas.drawLine(left, bottom, cx, cy, linePaint);
        canvas.drawLine(right, bottom, cx, cy, linePaint);

        canvas.drawLine(cx, top, right, cy, linePaint);
        canvas.drawLine(right, cy, cx, bottom, linePaint);
        canvas.drawLine(cx, bottom, left, cy, linePaint);
        canvas.drawLine(left, cy, cx, top, linePaint);

        if (chart == null) return;

        float[][] centers = {
                {0.50f, 0.22f}, {0.27f, 0.12f}, {0.12f, 0.28f},
                {0.22f, 0.50f}, {0.12f, 0.72f}, {0.28f, 0.88f},
                {0.50f, 0.78f}, {0.72f, 0.88f}, {0.88f, 0.72f},
                {0.78f, 0.50f}, {0.88f, 0.28f}, {0.72f, 0.12f}
        };

        int asc = d9 ? chart.d9AscSign : chart.ascSign;
        @SuppressWarnings("unchecked")
        List<String>[] housePlanets = new ArrayList[12];
        for (int i = 0; i < 12; i++) housePlanets[i] = new ArrayList<>();

        for (AstroEngine.PlanetPlacement p : chart.planets) {
            int house = d9 ? p.d9House : p.house;
            housePlanets[house - 1].add(p.shortName + (p.retrograde ? "R" : ""));
        }

        for (int i = 0; i < 12; i++) {
            float x = left + centers[i][0] * size;
            float y = top + centers[i][1] * size;
            int sign = (asc + i) % 12;
            canvas.drawText(String.valueOf(sign + 1), x, y - 12f, signPaint);

            String text = join(housePlanets[i]);
            if (!text.isEmpty()) {
                canvas.drawText(text, x, y + 20f, planetPaint);
            }
        }
    }

    private static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(s);
        }
        return sb.toString();
    }
}
