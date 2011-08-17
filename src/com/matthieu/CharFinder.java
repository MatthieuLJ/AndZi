package com.matthieu;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.AndroidCharacter;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.ArrayList;

public class CharFinder extends Activity{
    protected HanziDB myDB=null;
    private String hanzi;
    private ArrayList<Vocabulary> words;

    private final String TAG="CharFinder";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).myDB;

        hanzi=new String();
        words=new ArrayList<Vocabulary>();

        setContentView(R.layout.char_finder);
       
        setup_test();
    }

    private void setup_test() {
        hanzi = myDB.getNextToStudy();

        if (hanzi == null) {
            Toast.makeText(this, "Sorry, nothing to test you on", 5).show();
            this.finish();
            return;
        }
        
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

        EditText chinese = (EditText) findViewById(R.id.char_finder_chinese);
        chinese.setText("");
    }

    private void check_guess() {
        Toast toast;
        EditText chinese = (EditText) findViewById(R.id.char_finder_chinese);
        String guess = chinese.getText().toString();
        if (guess.equals(hanzi)) {
            myDB.betterBox(hanzi);
            toast = Toast.makeText(this, "Well done !", 2);
        }
        else {
            myDB.restartStudy(hanzi);
            toast = Toast.makeText(this, "Sorry, the correct answer was " + hanzi, 2);
        }
        toast.show();
    }

    public void handle_ok(View v) {
        check_guess();
        setup_test();
    }

    public void handle_give_up(View v) {

    }
}
