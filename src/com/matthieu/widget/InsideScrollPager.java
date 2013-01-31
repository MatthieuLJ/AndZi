package com.matthieu.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.View;

public class InsideScrollPager extends ViewPagerParallax {
    public InsideScrollPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    @SuppressLint({"NewApi"})
    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
        if (v instanceof ProgressGraph) {
            return ((ProgressGraph) v).canScrollHorizontally(-dx);
        } else {
            return super.canScroll(v, checkV, dx, x, y);
        }
    }
}