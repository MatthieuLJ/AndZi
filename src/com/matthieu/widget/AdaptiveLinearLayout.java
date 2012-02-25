package com.matthieu.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.matthieu.R;

import java.util.ArrayList;

public class AdaptiveLinearLayout extends LinearLayout {
    protected ArrayList<View> itemsToHide=null;
    HideParams saved_params=null;

    public AdaptiveLinearLayout(Context context) {
        super(context);
    }

    public AdaptiveLinearLayout(Context context, AttributeSet attrs) {
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

    protected View findScroll() {
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
            if (hp.scroll) {
                return child;
            }
        }
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        findItemsToHide();
        final ScrollView scrollView = (ScrollView) findScroll();

        for (View v:itemsToHide) {
            v.setVisibility(VISIBLE);
        }
        if (saved_params != null) {
            scrollView.setLayoutParams(saved_params);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (scrollView != null) {
            if (scrollView.getChildCount() > 0) {
                final View child = scrollView.getChildAt(0);
                int height = scrollView.getMeasuredHeight();
                if (child.getMeasuredHeight() < height) {
                    saved_params = (HideParams) scrollView.getLayoutParams();
                    HideParams hp = new HideParams((HideParams) scrollView.getLayoutParams());
                    hp.height=child.getMeasuredHeight();
                    hp.weight=0;
                    scrollView.setLayoutParams(hp);
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            }
        }

        View spacer = findViewById(R.id.spacer);
        if (spacer==null)
            return;

        int height = spacer.getMeasuredHeight();
        if (height < 10) {
            findItemsToHide();
            for (View v:itemsToHide) {
                v.setVisibility(GONE);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
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
