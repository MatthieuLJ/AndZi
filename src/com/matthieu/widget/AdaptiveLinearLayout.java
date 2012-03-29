package com.matthieu.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.matthieu.R;

import java.util.ArrayList;

public class AdaptiveLinearLayout extends LinearLayout {
    protected ArrayList<View> itemsToHide=null;
    protected ArrayList<ScrollView> scrollsToResize=null;
    protected ArrayList<HideParams> scrollsParams=null;

    public AdaptiveLinearLayout(Context context) {
        super(context);
        findItemsToHide();
        findScrolls();
    }

    public AdaptiveLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        findItemsToHide();
        findScrolls();
    }

    public void refreshContent() {
        findItemsToHide();
        findScrolls();
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

    protected void findScrolls() {
        final int count = getChildCount();

        if ((scrollsToResize != null) && (scrollsToResize.size() > 0)) {
            for (int i=0; i<scrollsToResize.size(); i++) {
                ScrollView scrollView=scrollsToResize.get(i);
                scrollView.setLayoutParams(scrollsParams.get(i));
            }
        }

        if (scrollsToResize == null) {
            scrollsToResize = new ArrayList<ScrollView>();
        }
        else {
            scrollsToResize.clear();
        }
        if (scrollsParams == null) {
            scrollsParams = new ArrayList<HideParams>();
        }
        else {
            scrollsParams.clear();
        }

        for (int i=0; i<count; i++) {
            final View child = getChildAt(i);

            if (child == null) continue;

            final HideParams hp = (HideParams) child.getLayoutParams();
            if (hp.scroll) {
                scrollsToResize.add((ScrollView) child);
                scrollsParams.add((HideParams) child.getLayoutParams());
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        findItemsToHide();
        findScrolls();

        for (View v:itemsToHide) {
            v.setVisibility(VISIBLE);
        }
        if (scrollsToResize.size() > 0) {
            for (int i=0; i<scrollsToResize.size(); i++) {
                ScrollView scrollView=scrollsToResize.get(i);
                scrollView.setLayoutParams(scrollsParams.get(i));
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (scrollsToResize.size() > 0) {
            for (ScrollView scrollView : scrollsToResize) {
                if (scrollView.getChildCount() > 0) {
                    final View child = scrollView.getChildAt(0);
                    int height = scrollView.getMeasuredHeight();
                    if (child.getMeasuredHeight() < height) {
                        HideParams hp = new HideParams((HideParams) scrollView.getLayoutParams());
                        hp.height=child.getMeasuredHeight();
                        hp.weight=0;
                        scrollView.setLayoutParams(hp);
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    }
                }
            }
        }

        View spacer = findViewById(R.id.spacer);
        if (spacer==null)
            return;

        int height = spacer.getMeasuredHeight();
        if (height < 20) {
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
