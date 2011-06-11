package com.matthieu;

import android.app.Activity;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

public class AndZiActivity extends Activity
{
    private static final String TAG="AndZiActivity";

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vocabulary_item);

        /*
        setContentView(R.layout.add_vocabulary);
        
        AutoCompleteTextView actv = (AutoCompleteTextView) findViewById(R.id.chinese);
        actv.addTextChangedListener(new ChineseTextWatcher(actv));
        */
    }

    public void expand(View v) {
        String tag = (String) v.getTag();
        if (tag.equals("0")) {
            v.setBackgroundResource(R.drawable.expand_arrow_down);

            // at the end of the animation...
            v.setTag("1");
            ((VocabularyItemView) findViewById(R.id.vocabulary_info)).expand();
            /*
            findViewById(R.id.separation).setVisibility(View.VISIBLE);
            findViewById(R.id.more).setVisibility(View.VISIBLE);
            */
        }
        else {
            v.setBackgroundResource(R.drawable.expand_arrow_right);
            v.setTag("0");
            ((VocabularyItemView) findViewById(R.id.vocabulary_info)).collapse();
            /*
            findViewById(R.id.separation).setVisibility(View.GONE);
            findViewById(R.id.more).setVisibility(View.GONE);
            */
        }
        //v.invalidate();
    }
    private class ChineseTextWatcher implements TextWatcher {
        private static final int maximum_text_size=32;
        private static final int minimum_text_size=10;
        private TextView tview;

        public ChineseTextWatcher(TextView tv) {
            tview = tv;

        }

        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        public void afterTextChanged(Editable editable) {
            int width = tview.getWidth() - tview.getCompoundPaddingLeft() - tview.getCompoundPaddingRight();
            String text = tview.getText().toString();
            int size=maximum_text_size;

            TextPaint tp = tview.getPaint();
            Rect currentBounds = new Rect ( );

            tview.setTextSize(size);
            tp.getTextBounds((String) text, 0, text.length(), currentBounds );

            while ((currentBounds.right > width) && (size > minimum_text_size)) {
                size--;
                tview.setTextSize(size);
                tp.getTextBounds((String) text, 0, text.length(), currentBounds );
            }
        }

        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }
    }
}
