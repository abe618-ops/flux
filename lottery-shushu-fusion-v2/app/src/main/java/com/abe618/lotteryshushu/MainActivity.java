package com.abe618.lotteryshushu;

import android.app.DatePickerDialog;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

public class MainActivity extends Activity {
    private final Map<EightShushuEngine.Dimension, EightShushuEngine.RunResult> runs =
            new EnumMap<>(EightShushuEngine.Dimension.class);

    private Spinner lotterySpinner;
    private EditText seedInput;
    private TextView dateView, resultView, statusView;
    private LocalDate selectedDate = LocalDate.now();
    private String lastRendered = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(24));
        scroll.addView(root);

        TextView title = text("四彩术数合参 V2", 24, true);
        root.addView(title);

        TextView sub = text("8术 × 3维度 × 4彩种｜可复现盲测研究版", 14, false);
        sub.setPadding(0, dp(4), 0, dp(14));
        root.addView(sub);

        lotterySpinner = new Spinner(this);
        String[] lotteries = {"快乐8","福彩3D","排列3","排列5"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, lotteries);
        lotterySpinner.setAdapter(adapter);
        root.addView(lotterySpinner, matchWrap());

        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateView = text("", 16, true);
        updateDateText();
        dateRow.addView(dateView, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button dateBtn = button("选择日期");
        dateBtn.setOnClickListener(v -> chooseDate());
        dateRow.addView(dateBtn);
        root.addView(dateRow);

        seedInput = new EditText(this);
        seedInput.setHint("可选：固定 Seed（数字或文字）；留空则随机");
        seedInput.setSingleLine(true);
        seedInput.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(seedInput, matchWrap());

        statusView = text("请选择一种起盘方式。", 13, false);
        statusView.setPadding(0, dp(8), 0, dp(8));
        root.addView(statusView);

        root.addView(buttonRow(
                makeRunButton("时间盘", EightShushuEngine.Dimension.TIME),
                makeRunButton("随机数盘", EightShushuEngine.Dimension.RANDOM)
        ));
        root.addView(buttonRow(
                makeRunButton("当日活时盘", EightShushuEngine.Dimension.LIVE),
                actionButton("三维合参", v -> combineThree())
        ));
        root.addView(buttonRow(
                actionButton("冻结当前结果", v -> freezeCurrent()),
                actionButton("复制结果", v -> copyCurrent())
        ));

        TextView divider = text("结果", 18, true);
        divider.setPadding(0, dp(16), 0, dp(6));
        root.addView(divider);

        resultView = text("尚未起盘。", 14, false);
        resultView.setTextIsSelectable(true);
        resultView.setPadding(dp(10), dp(10), dp(10), dp(10));
        resultView.setBackgroundColor(0xfff4f4f4);
        root.addView(resultView, matchWrap());

        TextView note = text(
                "说明：奇门、太乙、大六壬复用既有传统排盘底座；九爻易、灵棋经复用既有随机排盘底座；六爻、梅花、河洛以可复现取数层参与本版合参。所有术数首版等权，不根据开奖结果临时调权。",
                12, false);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);

        setContentView(scroll);
    }

    private Button makeRunButton(String label, EightShushuEngine.Dimension d) {
        return actionButton(label, v -> generate(d));
    }

    private void generate(EightShushuEngine.Dimension d) {
        String lottery = String.valueOf(lotterySpinner.getSelectedItem());
        String seed = seedInput.getText().toString();
        try {
            EightShushuEngine.RunResult r = EightShushuEngine.run(lottery, d, selectedDate, seed);
            runs.put(d, r);
            lastRendered = r.text;
            resultView.setText(lastRendered);
            statusView.setText("已生成 " + label(d) + "；三维合参需要三个维度均有结果。");
        } catch (Throwable e) {
            resultView.setText("起盘失败：\n" + e.getClass().getSimpleName() + "："
                    + (e.getMessage()==null?"未知错误":e.getMessage()));
            statusView.setText("本次起盘未写入结果。");
        }
    }

    private void combineThree() {
        String lottery = String.valueOf(lotterySpinner.getSelectedItem());
        String seed = seedInput.getText().toString();

        for (EightShushuEngine.Dimension d : EightShushuEngine.Dimension.values()) {
            EightShushuEngine.RunResult r = runs.get(d);
            if (r == null || !lottery.equals(r.lottery)) {
                r = EightShushuEngine.run(lottery, d, selectedDate, seed);
                runs.put(d, r);
            }
        }

        lastRendered = EightShushuEngine.combineThree(
                runs.get(EightShushuEngine.Dimension.TIME),
                runs.get(EightShushuEngine.Dimension.RANDOM),
                runs.get(EightShushuEngine.Dimension.LIVE));
        resultView.setText(lastRendered);
        statusView.setText("三维合参完成：8术等权，时间盘/随机数盘/活时盘等权。");
    }

    private void freezeCurrent() {
        if (lastRendered.trim().isEmpty()) {
            Toast.makeText(this, "还没有可冻结的结果", Toast.LENGTH_SHORT).show();
            return;
        }
        String key = "frozen_" + selectedDate + "_" + lotterySpinner.getSelectedItem();
        getSharedPreferences("blind_freeze", MODE_PRIVATE).edit()
                .putString(key, lastRendered)
                .putLong(key+"_ts", System.currentTimeMillis())
                .apply();
        Toast.makeText(this, "已冻结到本机，不会因再次起盘自动覆盖", Toast.LENGTH_LONG).show();
        statusView.setText("已冻结：" + key);
    }

    private void copyCurrent() {
        if (lastRendered.trim().isEmpty()) {
            Toast.makeText(this, "还没有结果", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("四彩术数合参", lastRendered));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    private void chooseDate() {
        DatePickerDialog dlg = new DatePickerDialog(this, (v,y,m,d) -> {
            selectedDate = LocalDate.of(y,m+1,d);
            updateDateText();
            runs.clear();
            statusView.setText("日期已变化，旧三维缓存已清空。");
        }, selectedDate.getYear(), selectedDate.getMonthValue()-1, selectedDate.getDayOfMonth());
        dlg.show();
    }

    private void updateDateText() {
        if(dateView!=null) dateView.setText("起盘日期：" + selectedDate);
    }

    private String label(EightShushuEngine.Dimension d) {
        if(d==EightShushuEngine.Dimension.TIME)return "时间盘";
        if(d==EightShushuEngine.Dimension.RANDOM)return "随机数盘";
        return "当日活时盘";
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button b=button(label);
        b.setOnClickListener(listener);
        return b;
    }

    private LinearLayout buttonRow(Button a, Button b) {
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0, dp(52), 1f);
        p.setMarginEnd(dp(4));
        row.addView(a,p);
        LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(0, dp(52),1f);
        q.setMarginStart(dp(4));
        row.addView(b,q);
        return row;
    }

    private Button button(String s) {
        Button b=new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private TextView text(String s,int sp,boolean bold) {
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(0xff202124);
        if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int x) {
        return (int)(x*getResources().getDisplayMetrics().density+0.5f);
    }
}
