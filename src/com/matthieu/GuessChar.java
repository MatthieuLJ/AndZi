package com.matthieu;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.util.ArrayList;

public class GuessChar extends TestActivity implements TextWatcher {
    protected HanziDB myDB=null;
    protected HanziDB refDB=null;
    private String hanzi;
    private ArrayList<Vocabulary> words;
    private ArrayList<String> previous_guess;
    boolean first_try=true;

    private final String TAG="GuessChar";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        refDB = ((AndZiApplication) getApplication()).getRefDB();

        hanzi="";
        words=new ArrayList<Vocabulary>();
        previous_guess=new ArrayList<String>();

        setContentView(R.layout.guess_char);

        EditText guess = (EditText) findViewById(R.id.char_finder_chinese);
        guess.addTextChangedListener(this);
        guess.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE)
                    handle_ok(textView);
                return true;
            }
        });
    }

    protected void set_next_study() {
        super.set_next_study();
        hanzi = myDB.getNextFilteredToStudy();
        
        if (hanzi == null) {
            Toast.makeText(this, "Sorry, nothing to test you on", 5).show();
            this.finish();
            return;
        }
        display_test();
    }

    @Override
    protected void display_test() {
        TextView previous = (TextView) findViewById(R.id.char_finder_previous_guess);
        previous.setText("");
        previous_guess.clear();

        EditText guess = (EditText) findViewById(R.id.char_finder_chinese);
        guess.setText("");

        words = myDB.getWordsWithPinyin(hanzi);

        Log.d(TAG, "Testing for character "+hanzi+" got "+words.size()+" words");

        TableLayout tl = (TableLayout) findViewById(R.id.char_finder_word_grid);
        tl.removeAllViews();
        TableRow.LayoutParams trlp;
        for (Vocabulary w : words) {
            TableRow tr = new TableRow(this);

            TextView chinese = new TextView(this);
            chinese.setTextAppearance(GuessChar.this, android.R.style.TextAppearance_Medium_Inverse);
            chinese.setClickable(false);
            chinese.setText(w.chinese.replaceAll(hanzi, "\\?"));
            trlp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            trlp.setMargins(5,5,5,5);
            chinese.setLayoutParams(trlp);
            tr.addView(chinese);

            TextView trans = new TextView(this);
            trans.setTextAppearance(GuessChar.this, android.R.style.TextAppearance_Medium_Inverse);
            trans.setClickable(false);
            String en="";
            for (int j=0; j<w.english.size(); j++) {
                en += w.english.get(j);
                if (j<w.english.size()-1)
                    en += ", ";
            }
            trans.setText(en);
            trlp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            trlp.setMargins(5,5,5,5);
            trans.setLayoutParams(trlp);
            tr.addView(trans);

            tl.addView(tr);
        }
        tl.invalidate();

        first_try=true;
    }

    protected int check_guess(String guess) {
        int res=0;

        if (guess.equals(hanzi)) {
            res=1;
        }
        else {
            //check if that character could have been good also...
            if (guess.length()==1) {
                res=-1;
                for (Vocabulary w:words) {
                    String tmp_word = w.chinese;
                    tmp_word = tmp_word.replaceAll(hanzi, guess);

                    ArrayList<String> tmp_translations=new ArrayList<String>();
                    for (Vocabulary tmp_v:myDB.getTranslations(tmp_word)) {
                        tmp_translations.addAll(tmp_v.english);
                    }
                    if (refDB != null) {
                        for (Vocabulary tmp_v:refDB.getTranslations(tmp_word)) {
                            tmp_translations.addAll(tmp_v.english);
                        }
                    }
                    boolean found = false;
                    if (tmp_translations.size()>0) {
                        for (String en:w.english) {
                            for (String tmp_en:tmp_translations) {
                                if (HanziDB.compare_translations(en, tmp_en))
                                    found=true;
                            }
                        }
                    }
                    if(!found) {
                        res=0;
                        break;
                    }
                }
            }
        }

        if (res == -1) {
                if (!previous_guess.contains(guess))
                    previous_guess.add(guess);
                TextView previous = (TextView) findViewById(R.id.char_finder_previous_guess);
                EditText edit_guess = (EditText) findViewById(R.id.char_finder_chinese);
                previous.setText("Previous try:\n");
                for (int i=0; i<previous_guess.size(); i++) {
                    previous.setText(previous.getText() + previous_guess.get(i));
                    if (i<previous_guess.size()-1)
                        previous.setText(previous.getText() + ",\n");
                }
                edit_guess.setText("");
                previous.invalidate();
        }

        return res;
    }

    protected void update_scores(int good) {
        if (good==1) {
            myDB.betterBox(hanzi);
        }
        else {
            myDB.restartStudy(hanzi);
        }

        // reset the time for all characters displayed or restart study
        for (Vocabulary v:words) {
            for (int i=0; i<v.chinese.length();) {
                String other_hanzi = v.chinese.substring(i, i+Character.charCount(v.chinese.codePointAt(i)));
                i+=Character.charCount(v.chinese.codePointAt(i));
                if (other_hanzi.equals(hanzi))
                    continue;
                if (good==1)
                    myDB.resetTime(other_hanzi);
                else
                    myDB.restartStudy(other_hanzi);
            }
        }
    }

    public void handle_ok(View v) {
        Toast toast;

        EditText word_guess = (EditText) findViewById(R.id.char_finder_chinese);
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(word_guess.getWindowToken(), 0);

        EditText chinese = (EditText) findViewById(R.id.char_finder_chinese);
        String guess = chinese.getText().toString();

        handle_submit(hanzi, guess, true);
    }

    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
    }

    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
    }

    public void afterTextChanged(Editable editable) {
        String current_guess = editable.toString();
        if (first_try) {
            if ((current_guess.codePointCount(0, current_guess.length()) == 1) && (current_guess.codePointAt(0) == hanzi.codePointAt(0))) {
                handle_ok(null);
            }
            else if (!current_guess.equals(""))
                first_try=false;
        }
    }
}
