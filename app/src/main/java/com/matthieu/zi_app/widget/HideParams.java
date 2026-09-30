package com.matthieu.zi_app.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.matthieu.zi_app.R;

public class HideParams extends LinearLayout.LayoutParams {
    public boolean hidable =false;
    public boolean scroll = false;

    public HideParams(Context c, AttributeSet attrs) {
        super(c, attrs);
        TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.ModifiableLinearLayout);

        hidable = a.getBoolean(R.styleable.ModifiableLinearLayout_hidable, false);

        a.recycle();
    }

    public void setHidable(boolean h) {
        hidable=h;
    }

    public HideParams(int width, int height, boolean hide) {
        super(width, height);
        hidable = hide;
    }

    public HideParams(ViewGroup.LayoutParams p) {
        super(p);
    }

    public HideParams(HideParams p) {
        super(p);
        this.hidable=p.hidable;
        this.scroll=p.scroll;
    }
}
