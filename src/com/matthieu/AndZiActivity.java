package com.matthieu;

import android.app.Activity;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

public class AndZiActivity extends Activity
{
    private static final String TAG="AndZiActivity";
    //HanziDB hanDB;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_vocabulary);
        //hanDB = new HanziDB(this);
        
        AutoCompleteTextView actv = (AutoCompleteTextView) findViewById(R.id.chinese);
        actv.addTextChangedListener(new ChineseTextWatcher(actv));
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
