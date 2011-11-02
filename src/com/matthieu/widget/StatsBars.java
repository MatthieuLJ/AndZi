package com.matthieu.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class StatsBars extends View {
    int good=0;  // those two go from 0 to 100...
    int expired=0;

    private final String TAG="StatsBar";

    public StatsBars(Context context) {
        super(context);
    }

    public StatsBars(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void set_good(int go) {
        good=go;
    }
    public void set_expired(int exp) {
        expired=exp;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //Log.d(TAG, "onMeasure " + MeasureSpec.getSize(widthMeasureSpec) + "," + MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int bound;

        if ((good == 0) && (expired == 0)) { // not ready yet to draw anything
            return;
        }

        ShapeDrawable green_rect = new ShapeDrawable(new RectShape());
        ShapeDrawable red_rect = new ShapeDrawable(new RectShape());
        green_rect.getPaint().setColor(0xFF00FF00);
        red_rect.getPaint().setColor(0xFFFF0000);

        int total_width = getMeasuredWidth();
        int total_height = getMeasuredHeight();

        if (expired == 0) { // only good ones
            bound = (total_width)*good/100;
            if (bound == 0) bound = 1;
            green_rect.setBounds(getPaddingLeft(),getPaddingTop(),getPaddingLeft()+bound, getPaddingTop()+total_height);
            green_rect.draw(canvas);
            return;
        }
        if (good == 0) { // only expired ones
            bound = (total_width)*expired/100;
            if (bound == 0) bound = 1;
            red_rect.setBounds(getPaddingLeft(),getPaddingTop(),getPaddingLeft()+bound, getPaddingTop()+total_height);
            red_rect.draw(canvas);
            return;
        }

        // split...
        bound = (total_width)*good/100;
        if (bound == 0) bound = 1;
        green_rect.setBounds(getPaddingLeft(),getPaddingTop(),getPaddingLeft()+bound, getPaddingTop()+(3*total_height)/8);
        bound = (total_width)*expired/100;
        if (bound == 0) bound = 1;
        red_rect.setBounds(getPaddingLeft(),getPaddingTop()+(5*total_height)/8,getPaddingLeft()+bound, getPaddingTop()+total_height);
        green_rect.draw(canvas);
        red_rect.draw(canvas);
    }
}
