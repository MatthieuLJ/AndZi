package com.matthieu.flash.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;
import com.matthieu.flash.R;

public class TextViewAutomaticSize extends TextView {
    float maximum_text_size, minimum_text_size;

    public TextViewAutomaticSize(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AdaptiveTextSize);

        maximum_text_size = a.getDimension(R.styleable.AdaptiveTextSize_max_size, 32f);
        minimum_text_size = a.getDimension(R.styleable.AdaptiveTextSize_min_size, 10f);

        a.recycle();
    }

    public TextViewAutomaticSize(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AdaptiveTextSize);

        maximum_text_size = a.getDimension(R.styleable.AdaptiveTextSize_max_size, 32f);
        minimum_text_size = a.getDimension(R.styleable.AdaptiveTextSize_min_size, 10f);

        a.recycle();
    }

    public TextViewAutomaticSize(Context context) {
        super(context);
    }

    public void setMaximumTextSize(int max) {
        maximum_text_size = max;
    }
    public void setMinimumTextSize(int min) {
        minimum_text_size = min;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        adaptTextSize();
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);

        adaptTextSize();
    }

    public void adaptTextSize() {
        int width = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        String text = getText().toString();
        float size=maximum_text_size;

        TextPaint tp = getPaint();
        Rect currentBounds = new Rect ( );

        setTextSize(TypedValue.COMPLEX_UNIT_PX,size);
        tp.getTextBounds(text, 0, text.length(), currentBounds );

        while ((currentBounds.right > width) && (size > minimum_text_size)) {
            size--;
            setTextSize(TypedValue.COMPLEX_UNIT_PX,size);
            tp.getTextBounds(text, 0, text.length(), currentBounds );
        }
    }
}
