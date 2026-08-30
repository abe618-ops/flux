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
        title.setText("微信语音点 · 原型 V1");
        title.setTextSize(24);
        title.setTextColor(Color.BLACK);
        title.setPadding(0, 0, 0, dp(14));
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText(
            "目标：平时隐藏虚拟键盘，只保留一个固定麦克风。\n\n" +
            "按住麦克风：临时唤出微信输入法，并代理长按空格语音。\n" +
            "松开麦克风：结束语音，等待微信输入法上屏后再次折叠键盘。\n" +
            "向屏幕中央快速滑动麦克风：临时展开完整微信输入法键盘。\n\n" +
            "首次使用请确认：\n" +
            "1. 默认输入法已经设为“微信输入法”；\n" +
            "2. 微信输入法中已经开启“长按空格语音转文字”；\n" +
            "3. 在系统辅助功能中开启“微信语音点辅助服务”。"
        );
        desc.setTextSize(16);
        desc.setTextColor(Color.DKGRAY);
        desc.setLineSpacing(0, 1.2f);
        root.addView(desc);

        Button inputSettings = new Button(this);
        inputSettings.setText("打开系统输入法设置");
        inputSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(inputSettings, new LinearLayout.LayoutParams(-1, -2));

        Button accessibility = new Button(this);
        accessibility.setText("打开辅助功能设置");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility, new LinearLayout.LayoutParams(-1, -2));

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        CheckBox adjust = new CheckBox(this);
        adjust.setText("调整语音点位置（开启后拖动；关闭后锁定）");
        adjust.setTextSize(16);
        adjust.setChecked(prefs.getBoolean(KEY_ADJUST, false));
        adjust.setPadding(0, dp(12), 0, dp(8));
        adjust.setOnCheckedChangeListener((buttonView, isChecked) ->
            prefs.edit().putBoolean(KEY_ADJUST, isChecked).apply()
        );
        root.addView(adjust);

        TextView note = new TextView(this);
        note.setText("说明：这是微信输入法专用验证版，不包含任何自有语音模型，也不会录音；语音识别仍由微信输入法完成。不同微信输入法版本的空格键节点可能不同，因此代码同时包含“节点定位”和“键盘区域坐标回退”两条路径。");
        note.setTextSize(14);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(12), 0, 0);
        root.addView(note);

        setContentView(root);
    }
}
