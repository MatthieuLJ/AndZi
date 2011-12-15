package com.matthieu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;

public class GuessChineseWord extends TestActivity {
    protected HanziDB myDB=null;
    protected HanziDB refDB=null;
    private Vocabulary solution;
    public ArrayList<String> translations, more_translations,previous_guess;
    int show_result;

    private final String TAG="GuessChineseWord";

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

        setContentView(R.layout.guess_chinese_word);
        EditText word_guess = (EditText) findViewById(R.id.guess_chinese_word_guess);
        word_guess.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE)
                    handle_ok(textView);
                return true;
            }
        });

        set_next_study();
    }

    protected void update_scores(int good) {
        Log.d(TAG, "updating scores "+good);

        int min_box = 5;

        if (good == 1) {
            for (int i=0; i<solution.chinese.length();) {
                int new_box = myDB.getBoxForChar(solution.chinese.substring(i, i+Character.charCount(solution.chinese.codePointAt(i))));
                if (new_box < min_box) {
                    min_box = new_box;
                }

                i+=Character.charCount(solution.chinese.codePointAt(i));
            }
            for (int i=0; i<solution.chinese.length();) {
                String hanzi = solution.chinese.substring(i, i+Character.charCount(solution.chinese.codePointAt(i)));
                int box = myDB.getBoxForChar(hanzi);
                if (box == min_box) {
                    myDB.betterBox(hanzi);
                    Log.d(TAG, hanzi +" moves up");
                }
                else {
                    myDB.resetTime(hanzi);
                    Log.d(TAG, hanzi +" will come back later");
                }

                i+=Character.charCount(solution.chinese.codePointAt(i));
            }
        }
        else {
            for (int i=0; i<solution.chinese.length();) {
                String hanzi = solution.chinese.substring(i, i+Character.charCount(solution.chinese.codePointAt(i)));
                myDB.restartStudy(hanzi);
                i+=Character.charCount(solution.chinese.codePointAt(i));
            }
        }
    }

    protected void set_next_study() {
        String candidate;
        ArrayList<String> hanzis = myDB.getAllFilteredToStudy();
        String hanzi;
        solution=null;

        while ((hanzis.size() >0) && (solution == null)) {
            hanzi = hanzis.remove(0);
            Log.d(TAG, "will study "+hanzi);
            ArrayList<Vocabulary> words = myDB.getWordsWithPinyin(hanzi);
            if (words.size() == 0) {
                Log.d(TAG, "we have a problem");
            }

            while ((words.size() > 0) && (solution == null)) {
                candidate = words.remove((int) ((words.size()-1)*Math.random())).chinese;
                ArrayList<Vocabulary> voc = myDB.getTranslations(candidate);
                solution = voc.get((int) ((voc.size()-1)*Math.random()));

                translations = solution.english;
                if (refDB != null) {
                    if (refDB.getTranslations(candidate, solution.pinyin).size() == 0) {
                        more_translations = new ArrayList<String>();
                    }
                    else {
                        more_translations = refDB.getTranslations(candidate, solution.pinyin).get(0).english;
                    }
                }
                else {
                    more_translations = new ArrayList<String>();
                }
            }
        }
        if (solution == null) {
            Toast.makeText(this, "Sorry, nothing to test you on", 5).show();
            this.finish();
            return;
        }
        ArrayList<String> bare_translations = new ArrayList<String>();
        for (String s : translations) {
            bare_translations.add(HanziDB.bare_translation(s));
        }
        for (int j=more_translations.size()-1; j>=0; j--) {
            if (bare_translations.contains(HanziDB.bare_translation(more_translations.get(j))))
                more_translations.remove(j);
        }

        TextView text = (TextView) findViewById(R.id.guess_chinese_word_voc);
        text.setText("");
        for (int i=0; i<translations.size(); i++) {
            text.setText(text.getText()+translations.get(i));
            if (i<translations.size()-1)
                text.setText(text.getText()+"\n");
        }
        TextView more_text = (TextView) findViewById(R.id.guess_chinese_word_more_voc);
        more_text.setText("");

        LinearLayout more = (LinearLayout) findViewById(R.id.guess_chinese_word_more_voc_linear);
        if (more_translations.size() == 0) {
            more.setVisibility(View.GONE);
        }
        else {
            more.setVisibility(View.VISIBLE);
        }
        more.invalidate();
        
        previous_guess = new ArrayList<String>();
        TextView previous = (TextView) findViewById(R.id.guess_chinese_word_previous_guess);
        previous.setText("");

        EditText edit_guess = (EditText) findViewById(R.id.guess_chinese_word_guess);
        edit_guess.setText("");
    }

    protected int check_guess(String guess) {
        if (guess.equals(solution.chinese)) {
            return 1;
        }
        else {
            // check if the word guessed is in the database and relates somewhat...
            ArrayList<Vocabulary> guess_translations = myDB.getTranslations(guess);
            if (refDB != null) {
                guess_translations.addAll(refDB.getTranslations(guess));
            }
            for (String hint:solution.english) {
                for(Vocabulary voc : guess_translations) {
                    for (String en : voc.english) {
                        if (HanziDB.compare_translations(en, hint)) {
                            if (!previous_guess.contains(guess))
                                previous_guess.add(guess);
                            TextView previous = (TextView) findViewById(R.id.guess_chinese_word_previous_guess);
                            previous.setText("Previous try:\n");
                            for (int i=0; i<previous_guess.size(); i++) {
                                previous.setText(previous.getText() + previous_guess.get(i));
                                if (i<previous_guess.size()-1)
                                    previous.setText(previous.getText() + ",\n");
                            }
                            previous.invalidate();
                            return -1;
                        }
                    }
                }
            }
        }

        return 0;
    }

    public void more_voc(View v) {
        LinearLayout more = (LinearLayout) findViewById(R.id.guess_chinese_word_more_voc_linear);
        more.setVisibility(View.GONE);
        TextView more_text = (TextView) findViewById(R.id.guess_chinese_word_more_voc);
        for (int i=0; i<more_translations.size(); i++) {
            more_text.setText(more_text.getText()+more_translations.get(i));
            if (i<more_translations.size()-1)
                more_text.setText(more_text.getText()+"\n");
        }
    }

    public void handle_ok(View v) {
        EditText word_guess = (EditText) findViewById(R.id.guess_chinese_word_guess);
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(word_guess.getWindowToken(), 0);
        
        String guess = word_guess.getText().toString();

        if (handle_submit(solution.chinese, new ArrayList<String>(Arrays.asList(solution.pinyin)), guess, show_result, true) == -1)
            word_guess.setText("");
    }

}
