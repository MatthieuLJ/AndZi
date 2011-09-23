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
        //Log.d(TAG, "onDraw " + canvas.getWidth() + "," + canvas.getHeight());
        //Log.d(TAG, "onDraw StatsBars: "+good+","+expired);

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
            //Log.d(TAG,"green :"+getPaddingLeft()+ "," +getPaddingTop()+ "," +(getPaddingLeft()+(total_width)*good/100)+ "," + (getPaddingTop()+total_height));
            green_rect.setBounds(getPaddingLeft(),getPaddingTop(),getPaddingLeft()+(total_width)*good/100, getPaddingTop()+total_height);
            green_rect.draw(canvas);
            return;
        }
        if (good == 0) { // only expired ones
            //Log.d(TAG,"red :"+getPaddingLeft()+ "," +getPaddingTop()+ "," +(getPaddingLeft()+(total_width)*expired/100)+ "," + (getPaddingTop()+total_height));
            red_rect.setBounds(getPaddingLeft(),getPaddingTop(),getPaddingLeft()+(total_width)*expired/100, getPaddingTop()+total_height);
            red_rect.draw(canvas);
            return;
        }

        // split...
        //Log.d(TAG,"green :"+getPaddingLeft()+ "," +getPaddingTop()+ "," +(getPaddingLeft()+(total_width)*good/100)+ "," + (getPaddingTop()+total_height/3));
        green_rect.setBounds(getPaddingLeft(),getPaddingTop(),getPaddingLeft()+(total_width)*good/100, getPaddingTop()+(3*total_height)/8);
        //Log.d(TAG,"red :"+getPaddingLeft()+ "," +(getPaddingTop()+(total_height*2)/3)+ "," +(getPaddingLeft()+(total_width)*expired/100)+ "," + (getPaddingTop()+total_height/3));
        red_rect.setBounds(getPaddingLeft(),getPaddingTop()+(5*total_height)/8,getPaddingLeft()+(total_width)*expired/100, getPaddingTop()+total_height);
        green_rect.draw(canvas);
        red_rect.draw(canvas);
    }
}
