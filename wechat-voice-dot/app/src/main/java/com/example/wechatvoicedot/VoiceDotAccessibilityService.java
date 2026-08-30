package com.example.wechatvoicedot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.List;

public class VoiceDotAccessibilityService extends AccessibilityService {
    private static final int SLOT_COUNT = 7;
    private static final long SEGMENT_MS = 140;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private TextView dot;
    private WindowManager.LayoutParams dotLp;
    private View keyboardMask;
    private WindowManager.LayoutParams maskLp;

    private boolean pressed = false;
    private boolean voiceGestureStarted = false;
    private boolean manualKeyboard = false;
    private boolean swipingForKeyboard = false;
    private float downRawX, downRawY;
    private int startX, startY;
    private GestureDescription.StrokeDescription currentStroke;
    private Path holdPath;
    private float holdX, holdY;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo i = getServiceInfo();
        i.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(i);

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        createDot();
        hideSoftKeyboard();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (dot == null) return;
        updateDotEnabledState();

        if (manualKeyboard && event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            handler.postDelayed(() -> {
                if (!hasImeWindow()) {
                    manualKeyboard = false;
                    hideSoftKeyboard();
                }
            }, 200);
        }
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        removeMask();
        if (wm != null && dot != null) {
            try { wm.removeView(dot); } catch (Exception ignored) { }
        }
        getSoftKeyboardController().setShowMode(
                AccessibilityService.SHOW_MODE_AUTO);
        super.onDestroy();
    }

    private void createDot() {
        dot = new TextView(this);
        dot.setText("🎙");
        dot.setTextSize(26);
        dot.setGravity(Gravity.CENTER);
        dot.setTextColor(Color.WHITE);
        dot.setElevation(dp(8));
        setDotBackground(false);

        int size = dp(58);
        dotLp = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        dotLp.gravity = Gravity.TOP | Gravity.START;

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int edge = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getInt("edge", 1);
        int slot = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getInt("slot", 4);
        dotLp.x = edge == 0 ? dp(10) : screenW - size - dp(10);
        dotLp.y = slotY(slot, screenH, size);

        dot.setOnTouchListener(this::onDotTouch);
        wm.addView(dot, dotLp);
    }

    private boolean onDotTouch(View v, MotionEvent e) {
        boolean adjust = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getBoolean(MainActivity.KEY_ADJUST, false);

        if (adjust) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = e.getRawX(); downRawY = e.getRawY();
                    startX = dotLp.x; startY = dotLp.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    dotLp.x = startX + Math.round(e.getRawX() - downRawX);
                    dotLp.y = startY + Math.round(e.getRawY() - downRawY);
                    clampDot();
                    wm.updateViewLayout(dot, dotLp);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    snapAndSaveDot();
                    return true;
            }
            return true;
        }

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = e.getRawX(); downRawY = e.getRawY();
                swipingForKeyboard = false;
                pressed = true;
                setDotBackground(true);
                handler.postDelayed(() -> {
                    if (pressed && !swipingForKeyboard) startVoiceProxy();
                }, 130);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - downRawX;
                int screenW = getResources().getDisplayMetrics().widthPixels;
                boolean dotOnRight = dotLp.x > screenW / 2;
                boolean inward = dotOnRight ? dx < -dp(70) : dx > dp(70);
                if (!voiceGestureStarted && inward) {
                    swipingForKeyboard = true;
                    pressed = false;
                    showKeyboardNormally();
                    setDotBackground(false);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                if (voiceGestureStarted) stopVoiceProxy();
                setDotBackground(false);
                return true;
        }
        return true;
    }

    private void startVoiceProxy() {
        if (voiceGestureStarted || swipingForKeyboard) return;

        manualKeyboard = false;
        getSoftKeyboardController().setShowMode(
                AccessibilityService.SHOW_MODE_AUTO);
        nudgeFocusedEditor();

        handler.postDelayed(() -> {
            Rect ime = findImeBounds();
            if (ime == null) {
                nudgeFocusedEditor();
                handler.postDelayed(this::locateAndHoldSpace, 350);
            } else {
                locateAndHoldSpace();
            }
        }, 330);
    }

    private void locateAndHoldSpace() {
        if (!pressed) {
            hideSoftKeyboard();
            return;
        }

        Rect ime = findImeBounds();
        if (ime == null) {
            flashError();
            hideSoftKeyboard();
            return;
        }

        Rect space = findSpaceNodeBounds();
        if (space != null && space.width() > dp(30)) {
            holdX = space.centerX();
            holdY = space.centerY();
        } else {
            holdX = ime.centerX();
            holdY = ime.bottom - dp(54);
        }

        showKeyboardMask(ime);
        voiceGestureStarted = true;

        holdPath = new Path();
        holdPath.moveTo(holdX, holdY);
        holdPath.lineTo(holdX + 0.1f, holdY + 0.1f);

        currentStroke = new GestureDescription.StrokeDescription(
                holdPath, 0, SEGMENT_MS, true);
        dispatchHoldSegment(currentStroke);
    }

    private void dispatchHoldSegment(GestureDescription.StrokeDescription stroke) {
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(stroke);

        boolean ok = dispatchGesture(b.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                if (!voiceGestureStarted || currentStroke == null) return;
                try {
                    if (pressed) {
                        currentStroke = currentStroke.continueStroke(
                                holdPath, 0, SEGMENT_MS, true);
                        dispatchHoldSegment(currentStroke);
                    } else {
                        GestureDescription.StrokeDescription release =
                                currentStroke.continueStroke(holdPath, 0, 1, false);
                        GestureDescription.Builder rb = new GestureDescription.Builder();
                        rb.addStroke(release);
                        dispatchGesture(rb.build(), new GestureResultCallback() {
                            @Override
                            public void onCompleted(GestureDescription g) {
                                finishVoiceProxy();
                            }

                            @Override
                            public void onCancelled(GestureDescription g) {
                                finishVoiceProxy();
                            }
                        }, null);
                        currentStroke = null;
                    }
                } catch (Exception ex) {
                    finishVoiceProxy();
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                finishVoiceProxy();
            }
        }, null);

        if (!ok) finishVoiceProxy();
    }

    private void stopVoiceProxy() {
        pressed = false;
    }

    private void finishVoiceProxy() {
        voiceGestureStarted = false;
        currentStroke = null;

        handler.postDelayed(() -> {
            removeMask();
            hideSoftKeyboard();
        }, 2200);
    }

    private void showKeyboardNormally() {
        removeMask();
        voiceGestureStarted = false;
        currentStroke = null;
        manualKeyboard = true;
        getSoftKeyboardController().setShowMode(
                AccessibilityService.SHOW_MODE_AUTO);
        nudgeFocusedEditor();
    }

    private void hideSoftKeyboard() {
        if (!manualKeyboard) {
            getSoftKeyboardController().setShowMode(
                    AccessibilityService.SHOW_MODE_HIDDEN);
        }
    }

    private void nudgeFocusedEditor() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f != null) {
            if (f.isClickable()) f.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            else f.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        }
    }

    private Rect findImeBounds() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return null;

        Rect r = new Rect();
        for (AccessibilityWindowInfo w : windows) {
            if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                w.getBoundsInScreen(r);
                if (!r.isEmpty()) return new Rect(r);
            }
        }
        return null;
    }

    private boolean hasImeWindow() {
        return findImeBounds() != null;
    }

    private Rect findSpaceNodeBounds() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return null;

        for (AccessibilityWindowInfo w : windows) {
            if (w.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) continue;
            Rect found = bfsForSpace(root);
            if (found != null) return found;
        }
        return null;
    }

    private Rect bfsForSpace(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(AccessibilityNodeInfo.obtain(root));

        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();

            CharSequence t = n.getText();
            CharSequence d = n.getContentDescription();
            String s = ((t == null ? "" : t.toString()) + " "
                    + (d == null ? "" : d.toString())).toLowerCase();

            if (s.contains("空格") || s.contains("space")) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                return r;
            }

            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo c = n.getChild(i);
                if (c != null) q.add(c);
            }
        }
        return null;
    }

    private void showKeyboardMask(Rect ime) {
        removeMask();

        keyboardMask = new View(this);
        keyboardMask.setBackgroundColor(Color.rgb(22, 22, 22));

        maskLp = new WindowManager.LayoutParams(
                ime.width(), ime.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE
        );
        maskLp.gravity = Gravity.TOP | Gravity.START;
        maskLp.x = ime.left;
        maskLp.y = ime.top;

        try {
            wm.addView(keyboardMask, maskLp);
        } catch (Exception ignored) {
            keyboardMask = null;
        }
    }

    private void removeMask() {
        if (wm != null && keyboardMask != null) {
            try { wm.removeView(keyboardMask); } catch (Exception ignored) { }
            keyboardMask = null;
        }
    }

    private void updateDotEnabledState() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            dot.setAlpha(0.55f);
            return;
        }

        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        boolean editable = f != null && f.isEditable();
        dot.setAlpha(editable ? 1f : 0.55f);
    }

    private void setDotBackground(boolean recording) {
        if (dot == null) return;

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(recording ? Color.rgb(220, 50, 55) : Color.rgb(36, 123, 86));
        gd.setStroke(dp(1), Color.argb(100, 255, 255, 255));
        dot.setBackground(gd);
    }

    private void flashError() {
        if (dot == null) return;
        dot.setText("!");
        handler.postDelayed(() -> {
            if (dot != null) dot.setText("🎙");
        }, 900);
    }

    private void clampDot() {
        int size = dotLp.width;
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;

        dotLp.x = Math.max(dp(4), Math.min(w - size - dp(4), dotLp.x));
        dotLp.y = Math.max(dp(40), Math.min(h - size - dp(70), dotLp.y));
    }

    private void snapAndSaveDot() {
        int size = dotLp.width;
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;

        int edge = dotLp.x + size / 2 < w / 2 ? 0 : 1;
        int slot = nearestSlot(dotLp.y, h, size);

        dotLp.x = edge == 0 ? dp(10) : w - size - dp(10);
        dotLp.y = slotY(slot, h, size);
        wm.updateViewLayout(dot, dotLp);

        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putInt("edge", edge)
                .putInt("slot", slot)
                .apply();
    }

    private int nearestSlot(int y, int h, int size) {
        int best = 0;
        int bestD = Integer.MAX_VALUE;

        for (int s = 0; s < SLOT_COUNT; s++) {
            int d = Math.abs(y - slotY(s, h, size));
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    private int slotY(int slot, int h, int size) {
        int top = dp(90);
        int bottom = h - size - dp(120);
        if (bottom <= top) return top;

        slot = Math.max(0, Math.min(SLOT_COUNT - 1, slot));
        return top + Math.round(
                (bottom - top) * (slot / (float) (SLOT_COUNT - 1)));
    }
}
