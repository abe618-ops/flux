package com.example.wechatvoicedot;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    public static final String PREFS = "voice_dot_prefs";
    public static final String KEY_ADJUST = "adjust_mode";

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(24));
        root.setGravity(Gravity.TOP);

        TextView title = new TextView(this);
        title.setText("微信语音点 · V2");
        title.setTextSize(24);
        title.setTextColor(Color.BLACK);
        title.setPadding(0, 0, 0, dp(14));
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText(
            "V2 已改为“单击切换”模式：\n\n" +
            "• 点输入框：先正常取得光标，再自动折叠微信输入法键盘。\n" +
            "• 第一次单击 🎙：在底层唤出微信输入法，并持续模拟长按空格。\n" +
            "• 第二次单击：松开空格，等待微信输入法完成识别并上屏。\n" +
            "• 向屏幕中央滑动 🎙：临时展开完整微信输入法键盘。\n\n" +
            "这一版不录音、不安装语音模型，识别仍完全由微信输入法完成。"
        );
        desc.setTextSize(16);
        desc.setTextColor(Color.DKGRAY);
        desc.setLineSpacing(0, 1.2f);
        root.addView(desc);

        Button inputSettings = new Button(this);
        inputSettings.setText("打开系统输入法设置");
        inputSettings.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(inputSettings, new LinearLayout.LayoutParams(-1, -2));

        Button accessibility = new Button(this);
        accessibility.setText("打开辅助功能设置");
        accessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility, new LinearLayout.LayoutParams(-1, -2));

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        CheckBox adjust = new CheckBox(this);
        adjust.setText("调整语音点位置（开启后拖动；关闭后锁定）");
        adjust.setTextSize(16);
        adjust.setChecked(prefs.getBoolean(KEY_ADJUST, false));
        adjust.setPadding(0, dp(12), 0, dp(8));
        adjust.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(KEY_ADJUST, isChecked).apply());
        root.addView(adjust);

        TextView note = new TextView(this);
        note.setText(
            "状态提示：绿色 🎙=待机；蓝色 …=正在唤出微信输入法；" +
            "红色 ■=微信空格处于持续按住状态；橙色 …=已松开，等待识别结果。\n\n" +
            "建议测试时先在微信输入法设置中确认“长按空格语音”可正常使用。"
        );
        note.setTextSize(14);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(12), 0, 0);
        root.addView(note);

        setContentView(root);
    }
}
