package com.matthieu;

import android.app.Activity;
import android.os.Bundle;
import android.text.AndroidCharacter;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

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
        GridView gv = (GridView) findViewById(R.id.char_finder_word_grid);
        gv.setAdapter(new VocabularyAdapter());

        setup_test();
    }

    private void setup_test() {
        hanzi = myDB.getNextToStudy();
        words = myDB.getWordsWithPinyin(hanzi);

        Log.d(TAG, "Testing for character"+hanzi);

        for (Vocabulary w : words) {
            w.chinese = w.chinese.replaceAll(hanzi, "\\?");
        }

        GridView gv = (GridView) findViewById(R.id.char_finder_word_grid);
        ((VocabularyAdapter)gv.getAdapter()).notifyDataSetChanged();
    }

    private class VocabularyAdapter extends BaseAdapter {

        public Object getItem(int i) {
            return null;
        }

        public long getItemId(int i) {
            return 0;
        }

        public int getCount() {
            Log.d(TAG, "grid has "+ words.size()*2 +" items");
            return words.size()*2;
        }

        public View getView(int i, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView == null) {
                tv = new TextView(CharFinder.this);
                tv.setTextAppearance(CharFinder.this, android.R.style.TextAppearance_Medium_Inverse);
                tv.setClickable(false);
            }
            else {
                tv= (TextView) convertView;
            }

            Vocabulary word = words.get(i/2);
            if (i%2 == 0) { // chinese part
                Log.d(TAG, "at "+i+": "+word.chinese);
                tv.setText(word.chinese);
            }
            else { // english part
                String en = new String();
                for (int j=0; j<word.english.size(); j++) {
                    String tr=word.english.get(j);
                    en += tr;
                    if (j<word.english.size()-1)
                        en += ", ";
                }
                Log.d(TAG, "at "+i+": "+en);
                tv.setText(en);
            }

            return tv;
        }
    }

    public void handle_ok(View v) {
        setup_test();
    }

    public void handle_give_up(View v) {

    }
}
