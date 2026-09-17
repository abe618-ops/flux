package com.abe618.geomancyfootball;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class ChartView extends View {
    public static final int SHIELD = 0;
    public static final int TRADITIONAL = 1;
    public static final int CROWLEY = 2;

    private int mode = SHIELD;
    private GeomancyEngine.State state;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ChartView(Context c) { super(c); init(); }
    public ChartView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        line.setColor(Color.rgb(30, 36, 40));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2));
        dot.setColor(Color.rgb(20, 25, 29));
        dot.setStyle(Paint.Style.FILL);
        text.setColor(Color.rgb(70, 78, 82));
        text.setTextSize(dp(10));
        text.setTextAlign(Paint.Align.CENTER);
        setBackgroundColor(Color.WHITE);
    }

    public void setState(GeomancyEngine.State s) { state = s; invalidate(); }
    public void setMode(int m) { mode = m; invalidate(); }

    @Override protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        int wanted = mode == SHIELD ? dp(500) : dp(430);
        setMeasuredDimension(width, resolveSize(wanted, h));
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (state == null) return;
        if (mode == SHIELD) drawShield(c);
        else if (mode == TRADITIONAL) drawTraditional(c);
        else drawCrowley(c);
    }

    private void drawShield(Canvas c) {
        float l = dp(10), r = getWidth() - dp(10), t = dp(15), bottom = getHeight() - dp(18);
        float h1 = t + (bottom - t) * 0.28f;
        float h2 = t + (bottom - t) * 0.53f;
        float h3 = t + (bottom - t) * 0.75f;
        float h4 = t + (bottom - t) * 0.90f;
        c.drawRect(l, t, r, h4, line);
        c.drawLine(l, h1, r, h1, line);
        c.drawLine(l, h2, r, h2, line);
        c.drawLine(l, h3, r, h3, line);
        c.drawLine(l, h4, r, h4, line);

        for (int i = 1; i < 8; i++) c.drawLine(l + (r-l)*i/8f, t, l + (r-l)*i/8f, h1, line);
        for (int i = 1; i < 4; i++) c.drawLine(l + (r-l)*i/4f, h1, l + (r-l)*i/4f, h2, line);
        c.drawLine((l+r)/2f, h2, (l+r)/2f, h3, line);

        for (int i=0;i<8;i++) figure(c, state.figures[i], l+(r-l)*(i+.5f)/8f, (t+h1)/2f, (r-l)/8f*0.70f, ""+(i+1));
        for (int i=0;i<4;i++) figure(c, state.figures[8+i], l+(r-l)*(i+.5f)/4f, (h1+h2)/2f, (r-l)/4f*0.55f, ""+(9+i));
        figure(c, state.figures[12], l+(r-l)*.25f, (h2+h3)/2f, (r-l)*.20f, "右见证");
        figure(c, state.figures[13], l+(r-l)*.75f, (h2+h3)/2f, (r-l)*.20f, "左见证");
        figure(c, state.figures[14], (l+r)/2f, (h3+h4)/2f, (r-l)*.24f, "法官");

        Path p = new Path();
        p.moveTo(l, h4); p.quadTo((l+r)/2f, bottom+dp(8), r, h4); c.drawPath(p, line);
        figure(c, state.figures[15], (l+r)/2f, (h4+bottom)/2f+dp(4), (r-l)*.24f, "调停者");
    }

    private void drawTraditional(Canvas c) {
        float l=dp(12), r=getWidth()-dp(12), t=dp(18), b=getHeight()-dp(18);
        float cx=(l+r)/2f, cy=(t+b)/2f;
        c.drawRect(l,t,r,b,line);
        c.drawLine(l,t,cx,cy,line); c.drawLine(r,t,cx,cy,line);
        c.drawLine(l,b,cx,cy,line); c.drawLine(r,b,cx,cy,line);
        c.drawLine(cx,t,l,cy,line); c.drawLine(cx,t,r,cy,line);
        c.drawLine(cx,b,l,cy,line); c.drawLine(cx,b,r,cy,line);

        float[][] pos={
                {.24f,.18f},{.50f,.13f},{.76f,.18f},
                {.85f,.36f},{.80f,.64f},{.67f,.82f},
                {.50f,.87f},{.33f,.82f},{.20f,.64f},
                {.15f,.36f},{.37f,.42f},{.63f,.58f}
        };
        for(int i=0;i<12;i++) figure(c,state.figures[i],l+(r-l)*pos[i][0],t+(b-t)*pos[i][1],dp(50),"H"+(i+1));
    }

    private void drawCrowley(Canvas c) {
        float l=dp(12), r=getWidth()-dp(12), t=dp(18), b=getHeight()-dp(18);
        float cx=(l+r)/2f, cy=(t+b)/2f;
        c.drawRect(l,t,r,b,line);
        Path d = new Path();
        d.moveTo(cx,t); d.lineTo(r,cy); d.lineTo(cx,b); d.lineTo(l,cy); d.close(); c.drawPath(d,line);
        c.drawLine(l,t,r,b,line); c.drawLine(r,t,l,b,line);
        float[][] pos={
                {.26f,.12f},{.50f,.22f},{.74f,.12f},
                {.88f,.30f},{.76f,.50f},{.88f,.70f},
                {.74f,.88f},{.50f,.78f},{.26f,.88f},
                {.12f,.70f},{.24f,.50f},{.12f,.30f}
        };
        int[] crowleyOrder={0,4,1,8,5,9,2,6,10,3,7,11};
        for(int i=0;i<12;i++) figure(c,state.figures[crowleyOrder[i]],l+(r-l)*pos[i][0],t+(b-t)*pos[i][1],dp(47),"H"+(i+1));
        figure(c,state.figures[GeomancyEngine.JUDGE],cx,cy,dp(55),"Judge");
    }

    private void figure(Canvas c, int[] f, float cx, float cy, float cellW, String label) {
        float radius = Math.max(dp(3.2f), Math.min(dp(6f), cellW * 0.055f));
        float dy = radius * 2.7f;
        float start = cy - dy * 1.5f;
        for (int i=0;i<4;i++) {
            float y=start+i*dy;
            if (f[i]==1) c.drawCircle(cx,y,radius,dot);
            else { c.drawCircle(cx-radius*1.55f,y,radius,dot); c.drawCircle(cx+radius*1.55f,y,radius,dot); }
        }
        text.setTextSize(dp(9));
        c.drawText(label,cx,cy+dy*2.25f,text);
    }

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}    
}
