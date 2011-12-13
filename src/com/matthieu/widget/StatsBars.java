package com.matthieu.widget;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class StatsBars extends View {
    int good=0;  // those two go from 0 to 100...
    int expired=0;
    int max=0;
    String good_label=null;
    String expired_label=null;

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
    public void set_max(int m) {
        max=m;
    }
    public void set_good_label(String gl) {
        good_label=gl;
    }
    public void set_expired_label(String el) {
        expired_label=el;
    }


    /*
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //Log.d(TAG, "onMeasure " + MeasureSpec.getSize(widthMeasureSpec) + "," + MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }
    */

    @Override
    protected void onDraw(Canvas canvas) {
        int bound;
        Paint p=new Paint();
        Rect r=new Rect();
        int good_width = (good * 100) / max;
        int expired_width = (expired * 100) / max;

        if ((good>0) && (good_width==0)) good_width=1;
        if ((expired>0) && (expired_width==0)) expired_width=1;
        
        p.setTextSize(20);

        if ((good == 0) && (expired == 0)) { // not ready yet to draw anything
            return;
        }

        Log.d(TAG, "good: "+good+", exp: "+expired+", max: "+max);

        ShapeDrawable green_rect = new ShapeDrawable(new RectShape());
        ShapeDrawable red_rect = new ShapeDrawable(new RectShape());
        green_rect.getPaint().setColor(0xFF00FF00);
        red_rect.getPaint().setColor(0xFFFF0000);

        int total_width = getMeasuredWidth();
        int total_height = getMeasuredHeight();

        if (expired == 0) { // only good ones
            bound = (total_width*good_width)/100;
            if (bound == 0) bound = 1;
            green_rect.setBounds(getPaddingLeft(),getPaddingTop(),getPaddingLeft()+bound, getPaddingTop()+total_height);
            green_rect.draw(canvas);
            if (good_label != null) {
                p.getTextBounds(good_label,0,good_label.length(),r);
                canvas.drawText(good_label, (total_width/2)-(r.width()/2), (total_height/2)-(r.height()/2),p);
            }
            return;
        }
        if (good == 0) { // only expired ones
            bound = (total_width*expired_width)/100;
            if (bound == 0) bound = 1;
            red_rect.setBounds(getPaddingLeft(),getPaddingTop(),getPaddingLeft()+bound, getPaddingTop()+total_height);
            red_rect.draw(canvas);
            if (expired_label != null) {
                p.getTextBounds(expired_label,0,expired_label.length(),r);
                canvas.drawText(expired_label, (total_width/2)-(r.width()/2), (total_height/2)-(r.height()/2),p);
            }
            return;
        }

        p.setTextSize(12);
        // split...
        bound = (total_width*good_width)/100;
        if (bound == 0) bound = 1;
        green_rect.setBounds(getPaddingLeft(),getPaddingTop(),getPaddingLeft()+bound, getPaddingTop()+(3*total_height)/8);
        bound = (total_width*expired_width)/100;
        if (bound == 0) bound = 1;
        red_rect.setBounds(getPaddingLeft(),getPaddingTop()+(5*total_height)/8,getPaddingLeft()+bound, getPaddingTop()+total_height);
        green_rect.draw(canvas);
        red_rect.draw(canvas);
        if (good_label != null) {
            p.getTextBounds(good_label,0,good_label.length(),r);
            canvas.drawText(good_label, (total_width/2)-(r.width()/2), (3*total_height/16)-(r.height()/2),p);
        }
        if (expired_label != null) {
            p.getTextBounds(expired_label,0,expired_label.length(),r);
            canvas.drawText(expired_label, (total_width/2)-(r.width()/2), (13*total_height/16)-(r.height()/2),p);
        }
    }
}
