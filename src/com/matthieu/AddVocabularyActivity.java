package com.matthieu;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AddVocabularyActivity extends Activity
{
    private static final String TAG="AddVocabularyActivity";

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.add_vocabulary_top);
        
        EditText chinese_word = (EditText) findViewById(R.id.chinese);
        chinese_word.addTextChangedListener(new ChineseTextWatcher(chinese_word));
        View.inflate(getApplicationContext(), R.layout.add_vocabulary_wrong_entry, (ViewGroup) findViewById(R.id.main_content));

    }

    public void expand(View v) {
        String tag = (String) v.getTag();
        if (tag.equals("0")) {
            v.setBackgroundResource(R.drawable.expand_arrow_down);

            // at the end of the animation...
            v.setTag("1");
            ((ExtendingLinearLayout) findViewById(R.id.vocabulary_info)).expand();
            /*
            findViewById(R.id.separation).setVisibility(View.VISIBLE);
            findViewById(R.id.more).setVisibility(View.VISIBLE);
            */
        }
        else {
            v.setBackgroundResource(R.drawable.expand_arrow_right);
            v.setTag("0");
            ((ExtendingLinearLayout) findViewById(R.id.vocabulary_info)).collapse();
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
        private int state; // 0 is empty, 1 is "normal", 2 is "wrong character in the string"

        public ChineseTextWatcher(TextView tv) {
            tview = tv;

        }

        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        public void afterTextChanged(Editable editable) {
            adaptTextSize();
            checkChineseChars();
        }

        private void checkChineseChars() {
            String text = tview.getText().toString();
            int count = text.codePointCount(0, text.length());
            int new_state = state;
            if (count == 0) {
                new_state = 0;
            }
            else {
                new_state=1;
                for (int i=0; i<count; i++) {
                    int entry=text.codePointAt(i);
                    if (((entry < 0x4E00) || (entry > 0x9fff)) // main CJK
                            && ((entry < 0x2e00) || (entry > 0x31ff)) // additional punctuation
                            && ((entry < 0x3400) || (entry > 0x4dbf)) // extension A
                            )
                    {
                        new_state=2;
                        break;
                    }
                }
            }

            LinearLayout main_content = (LinearLayout) findViewById(R.id.main_content);
            if (new_state != state) {
                switch(new_state) {
                    case 0:
                        // empty string
                        if(state == 1) {
                            main_content.removeAllViews();
                            View.inflate(getApplicationContext(), R.layout.add_vocabulary_wrong_entry, main_content);
                        }
                        ((TextView) findViewById(R.id.warning_chinese_word)).setText("Enter a Chinese word");
                        break;
                    case 1:
                        // all chinese characters
                        main_content.removeAllViews();
                        View.inflate(getApplicationContext(), R.layout.add_vocabulary_context, main_content);
                        break;
                    case 2:
                        // some non-chinese characters...
                        if(state == 1) {
                            main_content.removeAllViews();
                            View.inflate(getApplicationContext(), R.layout.add_vocabulary_wrong_entry, main_content);
                        }
                        ((TextView) findViewById(R.id.warning_chinese_word)).setText("Only enter chinese characters");
                        break;
                }
                state = new_state;
            }
        }

        private void adaptTextSize() {
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
