package com.matthieu.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.ScrollView;

public class ShrinkingScrollView extends ScrollView {
    private float original_weight=-1;
    public ShrinkingScrollView(Context context) {
        super(context);
    }

    public ShrinkingScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ShrinkingScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    Runnable to_request_layout = new Runnable() {
        @Override
        public void run() {
            requestLayout();
        }
    };

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) getLayoutParams();
        float previous_weight = params.weight;

        if (original_weight == -1)
            original_weight = params.weight;

        if ((getChildCount()>0) && (getVisibility()!=GONE)) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            int overall_height = getChildAt(0).getMeasuredHeight();
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (getMeasuredHeight() >= overall_height) {
                if (previous_weight != 0) {
                    params.weight=0;
                    params.height = overall_height;
                    setLayoutParams(params);
                    post(to_request_layout);
                }

                setMeasuredDimension(getMeasuredWidth(),overall_height);
            }
            else if (previous_weight == 0) {
                params.weight = original_weight;
                params.height = 0;
                setLayoutParams(params);
                post(to_request_layout);
            }
        }
        else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
