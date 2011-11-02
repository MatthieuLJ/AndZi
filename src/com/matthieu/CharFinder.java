package com.matthieu;

import android.app.Activity;
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
import android.widget.*;

import java.util.ArrayList;

public class CharFinder extends Activity implements TextWatcher {
    protected HanziDB myDB=null;
    private String hanzi;
    private ArrayList<Vocabulary> words;
    int show_result;
    boolean first_try=true;

    private final String TAG="CharFinder";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).myDB;
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

    private void set_next_study() {
        hanzi = myDB.getNextFilteredToStudy();

        if (hanzi == null) {
            Toast.makeText(this, "Sorry, nothing to test you on", 5).show();
            this.finish();
            return;
        }

        EditText guess = (EditText) findViewById(R.id.char_finder_chinese);
        guess.setText("");

        words = myDB.getWordsWithPinyin(hanzi);

        Log.d(TAG, "Testing for character"+hanzi);

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

    private int check_guess() {
        int res=0;
        EditText chinese = (EditText) findViewById(R.id.char_finder_chinese);
        String guess = chinese.getText().toString();
        if (guess.equals(hanzi)) {
            myDB.betterBox(hanzi);
            res=1;
        }
        else {
            myDB.restartStudy(hanzi);
            res=0;
        }
        return res;
    }

    private void show_solution() {
        Intent i = new Intent(this, TestResultActivity.class);
		i.putExtra("Hanzi", hanzi);
        i.putExtra("Mode", 0);
        startActivity(i);
    }

    public void handle_ok(View v) {
        Toast toast;
        int res = check_guess();

        if ((show_result==1) || ((show_result == 2) && (res == 0))) {
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
