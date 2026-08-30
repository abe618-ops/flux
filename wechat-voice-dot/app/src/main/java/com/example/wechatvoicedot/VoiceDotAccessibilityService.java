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
    private static final long SEGMENT_MS = 150;

    private static final int STATE_IDLE = 0;
    private static final int STATE_STARTING = 1;
    private static final int STATE_ACTIVE = 2;
    private static final int STATE_WAITING = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WindowManager wm;
    private TextView dot;
    private WindowManager.LayoutParams dotLp;
    private View keyboardMask;

    private int state = STATE_IDLE;
    private boolean stopRequested = false;
    private boolean manualKeyboard = false;
    private boolean swipingForKeyboard = false;

    private float downRawX;
    private float downRawY;
    private int startX;
    private int startY;

    private final Rect lastEditorRect = new Rect();

    private GestureDescription.StrokeDescription currentStroke;
    private float holdX;
    private float holdY;
    private boolean endpointAlt = true;

    private final Runnable recognitionFallback = this::completeRecognition;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);

        // V2: never keep the whole system in SHOW_MODE_HIDDEN.
        // Let the editor obtain real focus first, then fold the IME with BACK.
        getSoftKeyboardController().setShowMode(AccessibilityService.SHOW_MODE_AUTO);

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        createDot();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        rememberEditor(event.getSource());
        updateDotEnabledState();

        if (state == STATE_WAITING
                && event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                && isProbablyEditor(event.getSource())) {
            handler.removeCallbacks(recognitionFallback);
            handler.postDelayed(this::completeRecognition, 300);
        }

        if (manualKeyboard && event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            handler.postDelayed(() -> {
                if (!hasImeWindow()) {
                    manualKeyboard = false;
                }
            }, 220);
        }

        if (state == STATE_IDLE && !manualKeyboard) {
            int type = event.getEventType();
            if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED
                    || type == AccessibilityEvent.TYPE_VIEW_CLICKED
                    || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                    || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                scheduleFold(0);
            }
        }
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        removeMask();

        if (wm != null && dot != null) {
            try { wm.removeView(dot); } catch (Exception ignored) { }
        }

        getSoftKeyboardController().setShowMode(AccessibilityService.SHOW_MODE_AUTO);
        super.onDestroy();
    }

    private void createDot() {
        dot = new TextView(this);
        dot.setText("🎙");
        dot.setTextSize(26);
        dot.setGravity(Gravity.CENTER);
        dot.setTextColor(Color.WHITE);
        dot.setElevation(dp(8));
        setDotVisual(STATE_IDLE);

        int size = dp(58);
        dotLp = new WindowManager.LayoutParams(
                size,
                size,
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

    private boolean onDotTouch(View view, MotionEvent event) {
        boolean adjust = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getBoolean(MainActivity.KEY_ADJUST, false);

        if (adjust) {
            return onAdjustTouch(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                swipingForKeyboard = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                int screenW = getResources().getDisplayMetrics().widthPixels;
                boolean onRight = dotLp.x > screenW / 2;
                boolean inward = onRight ? dx < -dp(70) : dx > dp(70);

                if (Math.abs(dx) > Math.abs(dy) && inward && state == STATE_IDLE) {
                    swipingForKeyboard = true;
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (swipingForKeyboard) {
                    showKeyboardNormally();
                } else {
                    toggleSpeech();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                return true;
        }

        return true;
    }

    private boolean onAdjustTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = dotLp.x;
                startY = dotLp.y;
                return true;

            case MotionEvent.ACTION_MOVE:
                dotLp.x = startX + Math.round(event.getRawX() - downRawX);
                dotLp.y = startY + Math.round(event.getRawY() - downRawY);
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

    private void toggleSpeech() {
        if (state == STATE_IDLE) {
            startSpeech();
        } else if (state == STATE_ACTIVE) {
            requestStopSpeech();
        } else if (state == STATE_STARTING) {
            cancelStart();
        }
    }

    private void startSpeech() {
        if (state != STATE_IDLE) return;

        manualKeyboard = false;
        stopRequested = false;
        state = STATE_STARTING;
        setDotVisual(STATE_STARTING);

        getSoftKeyboardController().setShowMode(AccessibilityService.SHOW_MODE_AUTO);

        AccessibilityNodeInfo editor = resolveEditor();
        if (editor == null) {
            flashError();
            state = STATE_IDLE;
            setDotVisual(STATE_IDLE);
            return;
        }

        Rect r = new Rect();
        editor.getBoundsInScreen(r);
        if (!r.isEmpty()) {
            lastEditorRect.set(r);
        }

        // First try the semantic click. Some Compose/WebView editors need the
        // physical tap fallback below, so both paths are kept.
        editor.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        editor.performAction(AccessibilityNodeInfo.ACTION_CLICK);

        handler.postDelayed(() -> waitForIme(0), 90);
    }

    private void waitForIme(int attempt) {
        if (state != STATE_STARTING) return;

        Rect ime = findImeBounds();
        if (ime != null) {
            handler.postDelayed(() -> beginSpaceHold(ime), 100);
            return;
        }

        if (attempt == 2 || attempt == 6) {
            tapEditorForKeyboard();
        }

        if (attempt >= 18) {
            flashError();
            state = STATE_IDLE;
            setDotVisual(STATE_IDLE);
            return;
        }

        handler.postDelayed(() -> waitForIme(attempt + 1), 90);
    }

    private void tapEditorForKeyboard() {
        Rect r = new Rect(lastEditorRect);
        if (r.isEmpty()) {
            AccessibilityNodeInfo editor = resolveEditor();
            if (editor != null) editor.getBoundsInScreen(r);
        }

        if (r.isEmpty()) return;

        float x = Math.max(r.left + dp(8), Math.min(r.right - dp(8), r.centerX()));
        float y = r.centerY();

        Path p = new Path();
        p.moveTo(x, y);

        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, 1));
        dispatchGesture(b.build(), null, null);
    }

    private void beginSpaceHold(Rect ime) {
        if (state != STATE_STARTING) return;

        Rect freshIme = findImeBounds();
        if (freshIme != null) ime = freshIme;

        Rect space = findSpaceNodeBounds();
        if (space != null && space.width() > dp(30)) {
            holdX = space.centerX();
            holdY = space.centerY();
        } else {
            // Fallback for WeChat Input versions which do not expose key nodes.
            holdX = ime.centerX();
            holdY = ime.bottom - dp(54);
        }

        showKeyboardMask(ime);

        state = STATE_ACTIVE;
        stopRequested = false;
        setDotVisual(STATE_ACTIVE);

        float x2 = holdX + 0.12f;
        float y2 = holdY + 0.12f;

        Path first = new Path();
        first.moveTo(holdX, holdY);
        first.lineTo(x2, y2);

        endpointAlt = true;
        currentStroke = new GestureDescription.StrokeDescription(
                first, 0, SEGMENT_MS, true
        );

        dispatchHoldSegment(currentStroke);
    }

    private void dispatchHoldSegment(GestureDescription.StrokeDescription stroke) {
        if (stroke == null) {
            enterWaitingForResult();
            return;
        }

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        boolean ok = dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                if (currentStroke == null) {
                    enterWaitingForResult();
                    return;
                }

                try {
                    if (state == STATE_ACTIVE && !stopRequested) {
                        Path next = nextHoldPath();
                        currentStroke = currentStroke.continueStroke(
                                next, 0, SEGMENT_MS, true
                        );
                        dispatchHoldSegment(currentStroke);
                    } else {
                        Path releasePath = nextHoldPath();
                        GestureDescription.StrokeDescription release =
                                currentStroke.continueStroke(
                                        releasePath, 0, 1, false
                                );

                        GestureDescription.Builder releaseBuilder =
                                new GestureDescription.Builder();
                        releaseBuilder.addStroke(release);

                        currentStroke = null;

                        boolean sent = dispatchGesture(
                                releaseBuilder.build(),
                                new GestureResultCallback() {
                                    @Override
                                    public void onCompleted(GestureDescription g) {
                                        enterWaitingForResult();
                                    }

                                    @Override
                                    public void onCancelled(GestureDescription g) {
                                        enterWaitingForResult();
                                    }
                                },
                                null
                        );

                        if (!sent) enterWaitingForResult();
                    }
                } catch (Exception e) {
                    currentStroke = null;
                    enterWaitingForResult();
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                currentStroke = null;
                if (state == STATE_ACTIVE || stopRequested) {
                    enterWaitingForResult();
                } else {
                    resetIdle();
                }
            }
        }, null);

        if (!ok) {
            currentStroke = null;
            if (state == STATE_ACTIVE || stopRequested) enterWaitingForResult();
            else resetIdle();
        }
    }

    private Path nextHoldPath() {
        float x0 = holdX;
        float y0 = holdY;
        float x1 = holdX + 0.12f;
        float y1 = holdY + 0.12f;

        Path p = new Path();
        if (endpointAlt) {
            p.moveTo(x1, y1);
            p.lineTo(x0, y0);
        } else {
            p.moveTo(x0, y0);
            p.lineTo(x1, y1);
        }
        endpointAlt = !endpointAlt;
        return p;
    }

    private void requestStopSpeech() {
        if (state != STATE_ACTIVE) return;
        stopRequested = true;
        setDotVisual(STATE_WAITING);
    }

    private void cancelStart() {
        state = STATE_IDLE;
        stopRequested = false;
        setDotVisual(STATE_IDLE);
        handler.postDelayed(this::foldKeyboardIfVisible, 80);
    }

    private void enterWaitingForResult() {
        if (state == STATE_WAITING) return;

        state = STATE_WAITING;
        stopRequested = false;
        currentStroke = null;
        setDotVisual(STATE_WAITING);

        handler.removeCallbacks(recognitionFallback);
        handler.postDelayed(recognitionFallback, 5000);
    }

    private void completeRecognition() {
        if (state != STATE_WAITING) return;

        handler.removeCallbacks(recognitionFallback);
        removeMask();

        if (hasImeWindow()) {
            performGlobalAction(GLOBAL_ACTION_BACK);
        }

        handler.postDelayed(this::resetIdle, 180);
    }

    private void resetIdle() {
        removeMask();
        currentStroke = null;
        stopRequested = false;
        state = STATE_IDLE;
        setDotVisual(STATE_IDLE);
    }

    private void showKeyboardNormally() {
        if (state != STATE_IDLE) return;

        removeMask();
        manualKeyboard = true;
        getSoftKeyboardController().setShowMode(AccessibilityService.SHOW_MODE_AUTO);

        AccessibilityNodeInfo editor = resolveEditor();
        if (editor != null) {
            Rect r = new Rect();
            editor.getBoundsInScreen(r);
            if (!r.isEmpty()) lastEditorRect.set(r);
            editor.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            editor.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }

        handler.postDelayed(() -> {
            if (!hasImeWindow()) {
                tapEditorForKeyboard();
            }
        }, 120);
    }

    private void scheduleFold(int attempt) {
        handler.postDelayed(() -> {
            if (state != STATE_IDLE || manualKeyboard) return;

            if (hasImeWindow()) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                return;
            }

            if (attempt < 8 && hasFocusedEditor()) {
                scheduleFold(attempt + 1);
            }
        }, attempt == 0 ? 70 : 60);
    }

    private void foldKeyboardIfVisible() {
        if (state == STATE_IDLE && !manualKeyboard && hasImeWindow()) {
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    private boolean hasFocusedEditor() {
        AccessibilityNodeInfo editor = resolveEditor();
        return editor != null;
    }

    private AccessibilityNodeInfo resolveEditor() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;

        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (isProbablyEditor(focused)) {
            rememberEditor(focused);
            return focused;
        }

        return findFirstEditable(root);
    }

    private AccessibilityNodeInfo findFirstEditable(AccessibilityNodeInfo root) {
        if (root == null) return null;

        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);

        AccessibilityNodeInfo best = null;
        int bestBottom = -1;

        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();

            if (isProbablyEditor(n) && n.isVisibleToUser()) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                if (!r.isEmpty() && r.bottom > bestBottom) {
                    bestBottom = r.bottom;
                    best = n;
                }
            }

            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }

        if (best != null) rememberEditor(best);
        return best;
    }

    private void rememberEditor(AccessibilityNodeInfo node) {
        if (!isProbablyEditor(node)) return;

        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (!r.isEmpty()) {
            lastEditorRect.set(r);
        }
    }

    private boolean isProbablyEditor(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isEditable()) return true;

        List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
        if (actions != null) {
            for (AccessibilityNodeInfo.AccessibilityAction action : actions) {
                if (action.getId() == AccessibilityNodeInfo.ACTION_SET_TEXT) {
                    return true;
                }
            }
        }
        return false;
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

            Rect result = bfsForSpace(root);
            if (result != null) return result;
        }

        return null;
    }

    private Rect bfsForSpace(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);

        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();

            CharSequence text = n.getText();
            CharSequence desc = n.getContentDescription();
            String s = ((text == null ? "" : text.toString()) + " "
                    + (desc == null ? "" : desc.toString())).toLowerCase();

            if (s.contains("空格") || s.contains("space")) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                if (!r.isEmpty()) return r;
            }

            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }

        return null;
    }

    private void showKeyboardMask(Rect ime) {
        removeMask();

        keyboardMask = new View(this);
        keyboardMask.setBackgroundColor(Color.rgb(18, 18, 18));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ime.width(),
                ime.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE
        );

        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = ime.left;
        lp.y = ime.top;

        try {
            wm.addView(keyboardMask, lp);
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
        if (dot == null || state != STATE_IDLE) return;
        dot.setAlpha(hasFocusedEditor() ? 1f : 0.72f);
    }

    private void setDotVisual(int visualState) {
        if (dot == null) return;

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);

        if (visualState == STATE_ACTIVE) {
            bg.setColor(Color.rgb(216, 48, 54));
            dot.setText("■");
        } else if (visualState == STATE_WAITING) {
            bg.setColor(Color.rgb(219, 132, 31));
            dot.setText("…");
        } else if (visualState == STATE_STARTING) {
            bg.setColor(Color.rgb(45, 110, 190));
            dot.setText("…");
        } else {
            bg.setColor(Color.rgb(36, 123, 86));
            dot.setText("🎙");
        }

        bg.setStroke(dp(1), Color.argb(100, 255, 255, 255));
        dot.setBackground(bg);
    }

    private void flashError() {
        if (dot == null) return;

        dot.setText("!");
        handler.postDelayed(() -> {
            if (dot != null && state == STATE_IDLE) {
                setDotVisual(STATE_IDLE);
            }
        }, 1000);
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

        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .edit()
                .putInt("edge", edge)
                .putInt("slot", slot)
                .apply();
    }

    private int nearestSlot(int y, int h, int size) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;

        for (int s = 0; s < SLOT_COUNT; s++) {
            int distance = Math.abs(y - slotY(s, h, size));
            if (distance < bestDistance) {
                bestDistance = distance;
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
                (bottom - top) * (slot / (float) (SLOT_COUNT - 1))
        );
    }
}
