package com.matthieu;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.ArrayList;

public class GuessChar extends TestActivity {
    protected HanziDB myDB=null;
    protected HanziDB refDB=null;
    private String hanzi;
    private ArrayList<Vocabulary> words;
    private ArrayList<String> previous_guess;
    private String last_guess="";
    private int last_good=0;

    private final String TAG="GuessChar";
    private int current_view=0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        refDB = ((AndZiApplication) getApplication()).getRefDB();

        hanzi="";
        words=new ArrayList<Vocabulary>();
        previous_guess=new ArrayList<String>();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (current_view==R.layout.guess_char) {
            EditText word_guess = (EditText) findViewById(R.id.char_finder_chinese);
            if (word_guess==null)
                return;
            String guess = word_guess.getText().toString();
            super.onConfigurationChanged(newConfig);
            word_guess = (EditText) findViewById(R.id.char_finder_chinese);
            word_guess.setText(guess);
        } else if (current_view == R.layout.test_result) {
            super.onConfigurationChanged(newConfig);
            show_solution(last_guess, last_good);
        } else {
            super.onConfigurationChanged(newConfig);
        }
    }

    protected void set_next_study() {
        super.set_next_study();
        String candidate, candidate_word;
        ArrayList<String> hanzis = myDB.getAllFilteredToStudy();
        ArrayList<Vocabulary> words;
        hanzi=null;

        while ((hanzis.size() >0) && (hanzi == null)) {
            candidate = hanzis.remove((int)(hanzis.size()*Math.pow(Math.random(),2)));
            Log.d(TAG, "will study " + candidate);
            words = myDB.getWordsWithPinyin(candidate);
            if (words.size() == 0) {
                Log.d(TAG, "we have a problem");
            }
            while ((words.size() > 0) && (hanzi == null)) {
                candidate_word=words.remove(0).chinese;
                String without_hanzi=candidate_word.replace(candidate, "");
                if (without_hanzi.codePointCount(0, without_hanzi.length())>0) {
                    hanzi=candidate;
                }
            }
        }

        if (hanzi == null) {
            nothing_to_test();
            return;
        }
        display_test();
    }

    @Override
    protected void display_test() {
        setContentView(R.layout.guess_char);
        current_view = R.layout.guess_char;

        EditText guess = (EditText) findViewById(R.id.char_finder_chinese);
        guess.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE)
                    handle_ok(textView);
                return true;
            }
        });
        guess.setText("");
        guess.requestFocus();

        TextView previous = (TextView) findViewById(R.id.char_finder_previous_guess);
        previous.setText("");
        previous_guess.clear();

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
    }

    protected int check_guess(String guess) {
        int res=INCORRECT;

        if (guess.equals(hanzi)) {
            res=CORRECT;
        }
        else {
            //check if that character could have been good also...
            if (guess.length()==1) {
                res=OTHER;
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
                        res=INCORRECT;
                        break;
                    }
                }
            }
        }

        if (res == OTHER) {
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
        Log.d(TAG, "updating scores "+good);
        
        if (good==CORRECT) {
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
                if (good==CORRECT) {
                    myDB.resetTime(other_hanzi);
                }
                else
                    myDB.restartStudy(other_hanzi);
            }
        }
    }

    public void handle_ok(View v) {
        EditText word_guess = (EditText) findViewById(R.id.char_finder_chinese);
        if (word_guess==null)
            return;
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(word_guess.getWindowToken(), 0);

        String guess = word_guess.getText().toString();

        handle_submit(guess);
    }

    @Override
    protected void show_solution(String guess, int good) {
        setContentView(R.layout.test_result);
        current_view=R.layout.test_result;
        last_guess=guess;
        last_good=good;
        Details.populate_details_view(GuessChar.this, (ViewGroup) findViewById(R.id.test_results), hanzi, false);
        if ((good == INCORRECT) && (!guess.equals(""))) {
            LinearLayout bad_guess = (LinearLayout) findViewById(R.id.test_result_wrong_guess);
            TextView bad_guess_text = new TextView(GuessChar.this);
            bad_guess_text.setText("You guessed: "+guess);
            bad_guess_text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            bad_guess_text.setTextColor(0xffdd0000);
            bad_guess_text.setGravity(Gravity.CENTER_HORIZONTAL);
            bad_guess.addView(bad_guess_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            (findViewById(R.id.test_result_wrong)).setVisibility(View.VISIBLE);
        }
    }
}
