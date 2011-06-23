package com.matthieu;

import android.app.Activity;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.matthieu.widget.ExtendingLinearLayout;
import java.util.ArrayList;

public class AddVocabularyActivity extends Activity
{
    private static final String TAG="AddVocabularyActivity";

    ArrayList<PinyinChoices> pinyin_choices;
    public HanziDB myDB=null;
    public HanziDB refDB=null;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).myDB;
        refDB = ((AndZiApplication) getApplication()).refDB;

        pinyin_choices = new ArrayList<PinyinChoices>();

        setContentView(R.layout.add_vocabulary_top);
        
        EditText chinese_word = (EditText) findViewById(R.id.chinese);
        chinese_word.addTextChangedListener(new ChineseTextWatcher(chinese_word));
        View.inflate(getApplicationContext(), R.layout.add_vocabulary_wrong_entry, (ViewGroup) findViewById(R.id.main_content));
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
            Log.d(TAG, "text changed at "+i+", sequence of "+i1+" chars became "+i2+"; new sequence=["+charSequence.toString()+"]");
            Log.d(TAG, "pinyin_choices has "+pinyin_choices.size()+" elements");
            if (i1 > 0) {
                // remove the ones that were changed
                for (int j=0; j<i1; j++) pinyin_choices.remove(i);
            }
            if (i2 > 0) {
                for (int j=0; j<i2; j++) {
                    PinyinChoices tmp = new PinyinChoices(charSequence.toString().substring(i+j, i+j+1),
                            ((AndZiApplication) getApplication()).myDB,
                            ((AndZiApplication) getApplication()).refDB, i+j);
                }
            }
        }
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            adaptTextSize();
            checkChineseChars();
        }

        public void afterTextChanged(Editable editable) {
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
    }

    private void populatePinyinButtons() {
        LinearLayout container = (LinearLayout) findViewById(R.id.pinyin_buttons);
    }

    private class PinyinChoices extends AsyncTask<Void, Void, Void> {
        private HanziDB local_db;
        private HanziDB ref_db;
        private String hanzi;
        private int position;
        private ArrayList<String> local_pinyins;
        private ArrayList<String> ref_pinyins;

        private String new_pinyin;
        private String default_choice="";
        private String current_choice;

        private final static String TAG = "PinyinChoices";
        //private Button button;

        //public PinyinChoices(String h, HanziDB l_db, HanziDB r_db, Button b, int p) {
        //    button = b;
        public PinyinChoices(String h, HanziDB l_db, HanziDB r_db, int p) {
            Log.d(TAG, "new [" + h + "], at "+p);
            local_db = l_db;
            ref_db = r_db;
            new_pinyin = "";
            hanzi = h;
            position = p;
            this.execute((Void[]) null);
        }

        public String get_pinyin(int index) {
            if (index < local_pinyins.size())
                return local_pinyins.get(index);
            if (index < local_pinyins.size() + ref_pinyins.size())
                return ref_pinyins.get(index-local_pinyins.size());
            if (index == local_pinyins.size() + ref_pinyins.size())
                return new_pinyin;
            return "";
        }

        public int get_number_pinyins() {
            int res = local_pinyins.size()+ref_pinyins.size();
            if (new_pinyin.equals(""))
                return res;
            else
                return res+1;
        }

        public void setDefaultChoice(String s) {
            default_choice = s;
        }
        public void setCurrentChoice(String s) {
            current_choice = s;
        }
        public String getCurrentChoice() {
            return current_choice;
        }
        public String getHanzi() {
            return hanzi;
        }
        public int getPosition() {
            return position;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if ((!default_choice.equals("")) && ((local_pinyins.contains(default_choice)) || (ref_pinyins.contains(default_choice)))) {
                current_choice=default_choice;
            }
            else if (local_pinyins.size()!= 0) {
                current_choice = local_pinyins.get(0);
            }
            else if (ref_pinyins.size() != 0) {
                current_choice = ref_pinyins.get(0);
            }
            else {
                current_choice = "?";
            }
            //button.setText(current_choice);
            //button.invalidate();
            Log.d(TAG, "Set button with "+current_choice);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (local_db != null)
                local_pinyins = local_db.getPinyinForChar(hanzi);
            else
                local_pinyins = new ArrayList<String>();

            if (ref_db != null)
                ref_pinyins = ref_db.getPinyinForChar(hanzi);
            else
                ref_pinyins = new ArrayList<String>();

            for (int i=ref_pinyins.size()-1; i>=0; i--) {
                if (local_pinyins.contains(ref_pinyins.get(i))) {
                    ref_pinyins.remove(i);
                }
            }
            return null;
        }
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
}
