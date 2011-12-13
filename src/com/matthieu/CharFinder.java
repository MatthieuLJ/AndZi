package com.matthieu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

public class CharFinder extends Activity implements TextWatcher {
    protected HanziDB myDB=null;
    protected HanziDB refDB=null;
    private String hanzi;
    private ArrayList<Vocabulary> words;
    private ArrayList<String> previous_guess;
    int show_result;
    boolean first_try=true;

    private final String TAG="CharFinder";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).myDB;
        refDB = ((AndZiApplication) getApplication()).refDB;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        if (sp.getString("show_result", "always").equals("always")) {
            show_result = 1;
        }
        else if (sp.getString("show_result", "always").equals("sometimes")) {
            show_result = 2;
        }
        else {
            show_result = 3;
        }

        hanzi=new String();
        words=new ArrayList<Vocabulary>();
        previous_guess=new ArrayList<String>();

        setContentView(R.layout.char_finder);

        EditText guess = (EditText) findViewById(R.id.char_finder_chinese);
        guess.addTextChangedListener(this);
        guess.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE)
                    handle_ok(textView);
                return true;
            }
        });
       
        set_next_study();
    }

    @Override
    protected void onResume() {
        super.onResume();

        set_next_study();
    }

    public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {
        Log.d(TAG, "got return code "+resultCode);

        if (requestCode != 0)
            return;

        if (resultCode == 1) {
            update_scores(true);
        } else {
            update_scores(false);
        }

    }

    private void set_next_study() {
        hanzi = myDB.getNextFilteredToStudy();
        
        if (hanzi == null) {
            Toast.makeText(this, "Sorry, nothing to test you on", 5).show();
            this.finish();
            return;
        }

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
            chinese.setTextAppearance(CharFinder.this, android.R.style.TextAppearance_Medium_Inverse);
            chinese.setClickable(false);
            chinese.setText(w.chinese.replaceAll(hanzi, "\\?"));
            trlp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            trlp.setMargins(5,5,5,5);
            chinese.setLayoutParams(trlp);
            tr.addView(chinese);

            TextView trans = new TextView(this);
            trans.setTextAppearance(CharFinder.this, android.R.style.TextAppearance_Medium_Inverse);
            trans.setClickable(false);
            String en=new String();
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

    private int check_guess(String guess) {
        int res=0;

        if (guess.equals(hanzi)) {
            res=1;
        }
        else {
            //check if that character could have been good also...
            if (guess.length()==1) {
                res=-1;
                for (Vocabulary w:words) {
                    String tmp_word = new String(w.chinese);
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

    private void show_solution() {
        Intent i = new Intent(this, TestResultActivity.class);
		i.putExtra("Hanzi", hanzi);
        i.putExtra("Mode", 0);
        startActivity(i);
    }

    private void show_solution(String guess, boolean offer_override) {
        Intent i = new Intent(this, TestResultActivity.class);
		i.putExtra("Hanzi", hanzi);
        i.putExtra("Mode", 0);
        i.putExtra("WrongGuess", guess);
        if (offer_override) {
            i.putExtra("FixAnswer", true);
            startActivityForResult(i, 0);
        }
        else {
            startActivity(i);
        }
    }

    protected void update_scores(boolean good) {
        if (good) {
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
                if (good)
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

        int res = check_guess(guess);

        if (res == -1)
            return;

        if (((show_result == 2) || (show_result ==1)) && (res == 0)) {
            if (!guess.equals(""))
                show_solution(guess, true);
            else {
                show_solution(guess, false);
                update_scores(false);
            }
        }
        else {
            if (res == 0)
                update_scores(false);
            else
                update_scores(true);

            if (show_result==1) {
                show_solution();
            }
            else {
                if (res == 0) {
                    toast = Toast.makeText(this, "Sorry, the correct answer was " + hanzi, 2);
                }
                else {
                    toast = Toast.makeText(this, "Well done !", 2);
                }
                toast.show();
                set_next_study();
            }
        }
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
