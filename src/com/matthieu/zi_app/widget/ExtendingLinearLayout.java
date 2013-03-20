/******************************
 * A linear layout that can extends and collapse with some animation
 */
package com.matthieu.zi_app.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;
import android.os.Handler;

import java.util.ArrayList;


public class ExtendingLinearLayout extends LinearLayout {
    protected ArrayList<View> itemsToHide=null;
    private Interpolator interpolator;
    private int state=0; // -1:collapsing, 1:expanding, 0:nothing

    private int current_height, current_width;
    int saved_height;

    private LinearLayout.LayoutParams savedParams;
    protected Handler handler = new Handler();
    private ResizeRunnable runnable;

    public ExtendingLinearLayout(Context context) {
        super(context);
    }

    public ExtendingLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void findItemsToHide() {
        final int count = getChildCount();

        if (itemsToHide == null) {
            itemsToHide = new ArrayList<View>();
        }
        else {
            itemsToHide.clear();
        }

        for (int i=0; i<count; i++) {
            final View child = getChildAt(i);

            if (child == null) continue;

            final HideParams hp = (HideParams) child.getLayoutParams();
            if (hp.hidable) {
                itemsToHide.add(child);
            }
        }
    }

    public Interpolator getInterpolator()
    {
        return interpolator;
    }

    public void setInterpolator(Interpolator inter)
    {
        interpolator = inter;
    }

    public void expand() {
        //Log.d(TAG, "expand !");
        if (state > 0)
            return;
        if (state < 0) {
            runnable.reverse();
            state = 2;
            return;
        }
        findItemsToHide();
        current_height = getHeight();
        current_width = getWidth();
        savedParams = (LinearLayout.LayoutParams) this.getLayoutParams();
        state = 1;

        requestLayout();
    }

    public void collapse() {
        //Log.d(TAG, "collapse !");
        if (state < 0)
            return;
        if (state > 0) {
            runnable.reverse();
            state = -2;
            return;
        }
        findItemsToHide();
        current_height = getHeight();
        current_width = getWidth();
        savedParams = (LinearLayout.LayoutParams) this.getLayoutParams();
        state = -1;

        requestLayout();
    }

    public void expand_immediate() {
        findItemsToHide();
        for (View v : itemsToHide) {
            v.setVisibility(VISIBLE);
        }
        invalidate();
    }

    public void collapse_immediate() {
        findItemsToHide();
        for (View v : itemsToHide) {
            v.setVisibility(GONE);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //Log.d(TAG, "onMeasure..." + MeasureSpec.toString(widthMeasureSpec) +", "+MeasureSpec.toString(heightMeasureSpec));
        int new_height;

        if (state == -1) {
            //Log.d(TAG, "initiate collapse...");
            for (View v : itemsToHide) {
                v.setVisibility(VISIBLE);
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(current_width, MeasureSpec.EXACTLY),heightMeasureSpec);
            saved_height = getMeasuredHeight();

            for (View v : itemsToHide) {
                v.setVisibility(GONE);
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(current_width, MeasureSpec.EXACTLY),heightMeasureSpec);
            new_height = getMeasuredHeight();
        }
        else if (state == 1) {
            //Log.d(TAG, "initiate expand...");
            for (View v : itemsToHide) {
                v.setVisibility(GONE);
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(current_width, MeasureSpec.EXACTLY),heightMeasureSpec);
            saved_height = getMeasuredHeight();

            for (View v : itemsToHide) {
                v.setVisibility(VISIBLE);
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(current_width, MeasureSpec.EXACTLY),heightMeasureSpec);
            new_height = getMeasuredHeight();

            for (View v : itemsToHide) {
                v.setVisibility(GONE);
            }
        }
        else {
            super.onMeasure(widthMeasureSpec,heightMeasureSpec);
            //Log.d(TAG, "usual ("+state+")");
            return;
        }

        //Log.v(TAG, "setMeasure " + current_width + ", " + saved_height);
        setMeasuredDimension(current_width,saved_height);

        //Log.v(TAG, "state : "+state+", saved width "+savedParams.width+", height "+savedParams.height);
        //Log.v(TAG, "from " + current_height + " to " +new_height);
        if (runnable == null) {
            runnable = new ResizeRunnable(current_height, new_height, 500 );
            handler.post(runnable);
        }
    }

    protected void closeAnimation() {
        //Log.d(TAG, "closing...");
        setLayoutParams(savedParams);
        if (state > 0) {
            for (View v : itemsToHide) {
                v.setVisibility(VISIBLE);
            }
            invalidate();
        }
        else if (state < 0) {
            for (View v : itemsToHide) {
                v.setVisibility(GONE);
            }
            invalidate();
        }

        state = 0;
        runnable = null;
    }

    protected class ResizeRunnable implements Runnable
    {
        private long startTime;

        private long refreshTime = 50;

        private int startHeight;
        private int endHeight;
        private int duration;


        protected ResizeRunnable(int sHeight, int eHeight, int dur)
        {
            startTime = System.currentTimeMillis();
            startHeight=sHeight;
            endHeight=eHeight;
            duration=dur;
        }

        public void change_target_height(int eHeight) {
            endHeight=eHeight;
        }

        public void reverse() {
            long remaining = duration - (System.currentTimeMillis() - startTime);
            if(remaining <= 0)
            {
                remaining = 0;
            }
            int tmp = endHeight;
            endHeight = startHeight;
            startHeight = tmp;
            startTime = System.currentTimeMillis() - remaining;
        }

        public void run()
        {
            final long elapsed = System.currentTimeMillis() - startTime;
            if(elapsed >= duration)
            {
                closeAnimation();
                return;//Done
            }

            float timelinePoint = (float) elapsed / (float) duration;
            if(interpolator != null)
                timelinePoint = interpolator.getInterpolation(timelinePoint);

            int height_diff = endHeight - startHeight;

            final int newHeight = startHeight + (int) (timelinePoint * height_diff);

            LinearLayout.LayoutParams new_params = new LinearLayout.LayoutParams(savedParams);
            new_params.height = newHeight;
            new_params.width = getWidth();
            //Log.v(TAG, "set params "+getWidth()+", "+newHeight);
            setLayoutParams(new_params);
            handler.postDelayed(this, refreshTime);

            if(state == -1) state=-2;
            if(state == 1)  state=2;
        }
    }

    @Override
    public HideParams generateLayoutParams(AttributeSet attrs) {
        return new HideParams(getContext(), attrs);
    }

    @Override
    public HideParams generateDefaultLayoutParams() {
        LayoutParams tmp = super.generateDefaultLayoutParams();
        return new HideParams(tmp.width,tmp.height, false);
    }

    @Override
    public HideParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new HideParams(p);
    }

    @Override
    public boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof HideParams;
    }
}