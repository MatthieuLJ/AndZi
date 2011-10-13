package com.matthieu.widget;

import android.content.Context;
import android.graphics.Rect;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.widget.EditText;

public class EditTextAutomaticSize extends EditText {
    int maximum_text_size, minimum_text_size;

    public EditTextAutomaticSize(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public EditTextAutomaticSize(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EditTextAutomaticSize(Context context) {
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

    public void adaptTextSize() {
        int width = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        String text = getText().toString();
        int size=maximum_text_size;

        TextPaint tp = getPaint();
        Rect currentBounds = new Rect ( );

        setTextSize(size);
        tp.getTextBounds((String) text, 0, text.length(), currentBounds );

        while ((currentBounds.right > width) && (size > minimum_text_size)) {
            size--;
            setTextSize(size);
            tp.getTextBounds((String) text, 0, text.length(), currentBounds );
        }
    }
}
