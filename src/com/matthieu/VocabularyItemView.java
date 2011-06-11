package com.matthieu;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;
import android.os.Handler;
import java.util.ArrayList;


/**
 * User: matthieu
 * Date: 6/10/11
 * Time: 11:16 AM
 */
public class VocabularyItemView extends LinearLayout {
    private static final String TAG="VocabularyItemView";
    protected ArrayList<View> itemsToHide=null;
    private Interpolator interpolator;
    private int state=0; // -1:collapsing, 1:expanding, 0:nothing

    private int current_height, new_height;
    private ViewGroup.LayoutParams savedParams;
    protected Handler handler = new Handler();
    private ResizeRunnable runnable;

    public VocabularyItemView(Context context) {
        super(context);
    }

    public VocabularyItemView(Context context, AttributeSet attrs) {
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
            if (hp.hide_on_collapse) {
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
        Log.d(TAG, "expand !");
        findItemsToHide();
        current_height = getMeasuredHeight();
        state = 1;
        for (View v : itemsToHide) {
            v.setVisibility(VISIBLE);
        }
        invalidate();
    }

    public void collapse() {
        Log.d(TAG, "collapse !");
        findItemsToHide();
        current_height = getMeasuredHeight();
        state = -1;
        for (View v : itemsToHide) {
            v.setVisibility(GONE);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //Log.d(TAG, "onMeasure ! (" + widthMeasureSpec + ", " + heightMeasureSpec +")");
        if (state == -1) {
            super.onMeasure(widthMeasureSpec,heightMeasureSpec);
            new_height = getMeasuredHeight();
            savedParams = getLayoutParams();

            for (View v : itemsToHide) {
                v.setVisibility(GONE);
            }
            //super.onMeasure(widthMeasureSpec,heightMeasureSpec);
            state = -2;
        }
        else if (state == 1) {
            super.onMeasure(widthMeasureSpec,heightMeasureSpec);
            new_height = getMeasuredHeight();
            savedParams = getLayoutParams();

            for (View v : itemsToHide) {
                v.setVisibility(GONE);
            }
            //super.onMeasure(widthMeasureSpec,heightMeasureSpec);
            state = 2;
        }
        else {
            super.onMeasure(widthMeasureSpec,heightMeasureSpec);
            return;
        }
        Log.v(TAG, "state : "+state+", saved width "+savedParams.width+", height "+savedParams.height);
        Log.v(TAG, "from " + current_height + " to " +new_height);
        if (runnable == null) {
            runnable = new ResizeRunnable(current_height, new_height, 5000 );
            handler.post(runnable);
        }
    }

    protected void closeAnimation() {
        Log.d(TAG, "closing...");
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
//            final int newHeight = adding ? (startHeight + (int) (timelinePoint * guesstimatedSize)) : (startHeight - (int) (timelinePoint * guesstimatedSize));

            final int newHeight = startHeight + (int) (timelinePoint * height_diff);

            setLayoutParams(new LinearLayout.LayoutParams(getWidth(), newHeight));
            handler.postDelayed(this, refreshTime);
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

    public static class HideParams extends LayoutParams {
        public boolean hide_on_collapse=false;

        public HideParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.VocabularyItemView);

            hide_on_collapse = a.getBoolean(R.styleable.VocabularyItemView_hide_on_collapse, false);
            
            a.recycle();

        }

        public HideParams(int width, int height, boolean hide) {
            super(width, height);
            hide_on_collapse = hide;
        }

        public HideParams(ViewGroup.LayoutParams p) {
            super(p);
        }
    }
}
