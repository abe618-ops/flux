package com.abe618.sanshiyanqin;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.TimeZone;

public class MainActivity extends Activity {

    private final TimeZone china = TimeZone.getTimeZone("GMT+8");
    private int year, month, day, hour, minute;
    private TextView dateValue, timeValue;
    private Spinner systemSpinner, modeSpinner;
    private LinearLayout resultContainer;
    private String lastText = "";

    private static final String[] SYSTEMS = {
            "奇门遁甲", "太乙神数", "大六壬", "演禽", "资料／古籍"
    };

    private static final String[] QIMEN_MODES = {
            "时家奇门·拆补",
            "刻家·10分钟五马遁",
            "刻家·12分钟十分局",
            "日家奇门",
            "月家奇门",
            "年家奇门"
    };

    private static final String[] TAIYI_MODES = {
            "年计", "月计", "日计", "时计"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Calendar now = Calendar.getInstance(china);
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH) + 1;
        day = now.get(Calendar.DAY_OF_MONTH);
        hour = now.get(Calendar.HOUR_OF_DAY);
        minute = now.get(Calendar.MINUTE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(32));
        root.setBackgroundColor(Color.rgb(247, 247, 244));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("三式 · 演禽排盘");
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(30, 30, 28));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(4));
        root.addView(title, fullWidth());

        TextView subtitle = new TextView(this);
        subtitle.setText("V1.0 · 离线排盘｜奇门 · 太乙 · 六壬 · 演禽");
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, 0, 0, dp(14));
        root.addView(subtitle, fullWidth());

        root.addView(sectionTitle("排盘体系"));
        systemSpinner = spinner(SYSTEMS);
        root.addView(systemSpinner, fullWidth());

        TextView modeLabel = sectionTitle("家别／计式");
        root.addView(modeLabel);
        modeSpinner = spinner(QIMEN_MODES);
        root.addView(modeSpinner, fullWidth());

        LinearLayout dtRow = new LinearLayout(this);
        dtRow.setOrientation(LinearLayout.HORIZONTAL);
        dtRow.setPadding(0, dp(12), 0, 0);

        LinearLayout dateBox = new LinearLayout(this);
        dateBox.setOrientation(LinearLayout.VERTICAL);
        dateBox.setPadding(dp(10), dp(8), dp(10), dp(8));
        dateBox.setBackgroundColor(Color.WHITE);
        TextView dl = smallLabel("日期（东八区民用时）");
        dateValue = valueText();
        dateBox.addView(dl);
        dateBox.addView(dateValue);
        dateBox.setOnClickListener(v -> chooseDate());

        LinearLayout timeBox = new LinearLayout(this);
        timeBox.setOrientation(LinearLayout.VERTICAL);
        timeBox.setPadding(dp(10), dp(8), dp(10), dp(8));
        timeBox.setBackgroundColor(Color.WHITE);
        TextView tl = smallLabel("时间");
        timeValue = valueText();
        timeBox.addView(tl);
        timeBox.addView(timeValue);
        timeBox.setOnClickListener(v -> chooseTime());

        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half.setMargins(0, 0, dp(5), 0);
        dtRow.addView(dateBox, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half2.setMargins(dp(5), 0, 0, 0);
        dtRow.addView(timeBox, half2);
        root.addView(dtRow, fullWidth());

        updateDateTimeLabels();

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, dp(12), 0, dp(8));

        Button nowBtn = button("当前");
        nowBtn.setOnClickListener(v -> {
            Calendar n = Calendar.getInstance(china);
            year=n.get(Calendar.YEAR); month=n.get(Calendar.MONTH)+1; day=n.get(Calendar.DAY_OF_MONTH);
            hour=n.get(Calendar.HOUR_OF_DAY); minute=n.get(Calendar.MINUTE);
            updateDateTimeLabels();
            render();
        });

        Button runBtn = button("排盘");
        runBtn.setTextSize(17);
        runBtn.setTypeface(Typeface.DEFAULT_BOLD);
        runBtn.setOnClickListener(v -> render());

        Button copyBtn = button("复制");
        copyBtn.setOnClickListener(v -> copyResult());

        buttonRow.addView(nowBtn, weighted());
        buttonRow.addView(runBtn, weighted());
        buttonRow.addView(copyBtn, weighted());
        root.addView(buttonRow, fullWidth());

        TextView line = new TextView(this);
        line.setBackgroundColor(Color.rgb(215, 215, 210));
        root.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        resultContainer = new LinearLayout(this);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        resultContainer.setPadding(0, dp(12), 0, 0);
        root.addView(resultContainer, fullWidth());

        systemSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateModes(position);
                render();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                render();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        setContentView(scroll);
        render();
    }

    private void updateModes(int system) {
        if (system == 0) {
            modeSpinner.setVisibility(View.VISIBLE);
            setSpinnerItems(modeSpinner, QIMEN_MODES);
        } else if (system == 1) {
            modeSpinner.setVisibility(View.VISIBLE);
            setSpinnerItems(modeSpinner, TAIYI_MODES);
        } else {
            modeSpinner.setVisibility(View.GONE);
        }
    }

    private void render() {
        if (resultContainer == null || systemSpinner == null) return;
        resultContainer.removeAllViews();
        try {
            TraditionalEngine.CalendarInfo c = TraditionalEngine.CalendarInfo.of(year, month, day, hour, minute);
            int system = systemSpinner.getSelectedItemPosition();

            if (system == 0) {
                String key;
                int m = modeSpinner.getSelectedItemPosition();
                if (m == 1) key = "刻家10";
                else if (m == 2) key = "刻家12";
                else if (m == 3) key = "日家";
                else if (m == 4) key = "月家";
                else if (m == 5) key = "年家";
                else key = "时家";

                TraditionalEngine.QimenResult q = TraditionalEngine.qimen(c, key);
                renderQimen(c, q);
                lastText = buildQimenText(c, q);
            } else if (system == 1) {
                String mode = TAIYI_MODES[Math.max(0, modeSpinner.getSelectedItemPosition())];
                TraditionalEngine.TaiyiResult t = TraditionalEngine.taiyi(c, mode);
                String txt = t.text(c);
                addBody(txt);
                lastText = txt;
            } else if (system == 2) {
                TraditionalEngine.LiuRenResult l = TraditionalEngine.liuren(c);
                String txt = l.text(c);
                addBody(txt);
                lastText = txt;
            } else if (system == 3) {
                TraditionalEngine.YanqinResult y = TraditionalEngine.yanqin(c);
                String txt = y.text(c);
                addBody(txt);
                lastText = txt;
            } else {
                String txt = TraditionalEngine.researchText();
                addBody(txt);
                lastText = txt;
            }
        } catch (Exception e) {
            TextView err = addBody("排盘失败：\n" + e.getClass().getSimpleName() + " · " + e.getMessage());
            err.setTextColor(Color.rgb(170, 30, 30));
            lastText = err.getText().toString();
        }
    }

    private void renderQimen(TraditionalEngine.CalendarInfo c, TraditionalEngine.QimenResult q) {
        TextView h = new TextView(this);
        h.setText(q.header(c));
        h.setTextSize(15);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(Color.rgb(22, 22, 20));
        h.setPadding(dp(8), dp(8), dp(8), dp(10));
        h.setBackgroundColor(Color.WHITE);
        resultContainer.addView(h, fullWidth());

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(3);
        grid.setPadding(0, dp(10), 0, dp(10));
        int[] order = {4,9,2,3,5,7,8,1,6};
        for (int i = 0; i < order.length; i++) {
            TraditionalEngine.QimenPalace p = q.palaces[order[i]];
            TextView cell = new TextView(this);
            cell.setText(p.display());
            cell.setTextSize(13);
            cell.setTextColor(Color.rgb(28, 28, 27));
            cell.setGravity(Gravity.CENTER);
            cell.setTypeface(Typeface.create("serif", Typeface.NORMAL));
            cell.setPadding(dp(3), dp(5), dp(3), dp(5));
            cell.setBackground(createCellBackground(i == 4));

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 3, 1, 1f),
                    GridLayout.spec(i % 3, 1, 1f)
            );
            lp.width = 0;
            lp.height = dp(126);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            grid.addView(cell, lp);
        }
        resultContainer.addView(grid, fullWidth());

        TextView note = addBody("【口径说明】\n" + q.note
                + "\n\nV1先固定可复现的排盘基线；置闰、飞盘、暗干、神煞全层及更多古籍异法继续作为可切换口径加入，不覆盖默认结果。");
        note.setTextSize(12);
        note.setTextColor(Color.DKGRAY);
    }

    private android.graphics.drawable.GradientDrawable createCellBackground(boolean center) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(center ? Color.rgb(240, 237, 226) : Color.WHITE);
        g.setStroke(dp(1), Color.rgb(190, 190, 184));
        g.setCornerRadius(dp(4));
        return g;
    }

    private String buildQimenText(TraditionalEngine.CalendarInfo c, TraditionalEngine.QimenResult q) {
        StringBuilder b = new StringBuilder(q.header(c)).append("\n\n");
        int[] order = {4,9,2,3,5,7,8,1,6};
        for (int n : order) b.append(q.palaces[n].display().replace("\n", "｜")).append("\n");
        b.append("\n").append(q.note);
        return b.toString();
    }

    private TextView addBody(String text) {
        TextView body = new TextView(this);
        body.setText(text);
        body.setTextSize(14);
        body.setTextColor(Color.rgb(35,35,33));
        body.setLineSpacing(0, 1.18f);
        body.setPadding(dp(10), dp(10), dp(10), dp(10));
        body.setTextIsSelectable(true);
        body.setBackgroundColor(Color.WHITE);
        resultContainer.addView(body, fullWidth());
        return body;
    }

    private void chooseDate() {
        new DatePickerDialog(this, (view, y, m, d) -> {
            year=y; month=m+1; day=d;
            updateDateTimeLabels();
            render();
        }, year, month-1, day).show();
    }

    private void chooseTime() {
        new TimePickerDialog(this, (view, h, min) -> {
            hour=h; minute=min;
            updateDateTimeLabels();
            render();
        }, hour, minute, true).show();
    }

    private void updateDateTimeLabels() {
        if (dateValue != null) dateValue.setText(String.format("%04d-%02d-%02d", year, month, day));
        if (timeValue != null) timeValue.setText(String.format("%02d:%02d", hour, minute));
    }

    private void copyResult() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("三式演禽排盘", lastText));
        Toast.makeText(this, "已复制排盘结果", Toast.LENGTH_SHORT).show();
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        setSpinnerItems(s, values);
        s.setBackgroundColor(Color.WHITE);
        s.setPadding(dp(8), 0, dp(8), 0);
        return s;
    }

    private void setSpinnerItems(Spinner s, String[] values) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
    }

    private TextView sectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(Color.GRAY);
        t.setPadding(0, dp(8), 0, dp(4));
        return t;
    }

    private TextView smallLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(11);
        t.setTextColor(Color.GRAY);
        return t;
    }

    private TextView valueText() {
        TextView t = new TextView(this);
        t.setTextSize(18);
        t.setTextColor(Color.BLACK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        return b;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
}
