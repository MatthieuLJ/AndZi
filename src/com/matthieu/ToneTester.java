package com.matthieu;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

public class ToneTester extends Activity{
    protected HanziDB myDB=null;
    String hanzi;
    Pinyin pinyin;
    String radical;
    ArrayList<Integer> tones;

    private final String TAG="ToneTester";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).myDB;
        tones=new ArrayList<Integer>();


        set_next_study();
    }

    private void set_next_study() {
        hanzi = myDB.getNextToStudy();

        if (hanzi == null) {
            // TODO : handle nothing left to study
        }

        setContentView(R.layout.tone_tester);

        ArrayList<String> pinyins = myDB.getPinyinForChar(hanzi);

        pinyin = new Pinyin(pinyins.get((int)(Math.random()*pinyins.size())));
        radical = pinyin.get_radical();
        tones.clear();

        for (String p : pinyins) {
            Pinyin tmp_p=new Pinyin(p);
            if (tmp_p.get_radical().equals(radical)) {
                tones.add(tmp_p.get_tone());
            }
        }


        TextView tv_hanzi = (TextView) findViewById(R.id.tone_test_chinese);
        TextView tv_pinyin = (TextView) findViewById(R.id.tone_test_pinyin);

        tv_hanzi.setText(hanzi);
        tv_pinyin.setText(radical);

        CheckBox cb;
        cb = (CheckBox) findViewById(R.id.tone_test_first_tone);
        cb.setChecked(false);
        cb = (CheckBox) findViewById(R.id.tone_test_second_tone);
        cb.setChecked(false);
        cb = (CheckBox) findViewById(R.id.tone_test_third_tone);
        cb.setChecked(false);
        cb = (CheckBox) findViewById(R.id.tone_test_fourth_tone);
        cb.setChecked(false);
        cb = (CheckBox) findViewById(R.id.tone_test_fifth_tone);
        cb.setChecked(false);


        Log.d(TAG, "Expects tones : "+tones.toString());
    }

    private void show_solution() {
        setContentView(R.layout.tone_tester_result);
        LinearLayout main_layout = (LinearLayout) findViewById(R.id.tone_tester_results);
        for (int tone : tones) {
            add_one_tone(main_layout, radical+tone);
        }
        TextView tv_hanzi = (TextView) findViewById(R.id.tone_test_chinese);

        tv_hanzi.setText(hanzi);

    }

    private void add_one_tone(LinearLayout linear, String pin) {
        LayoutInflater inflater = getLayoutInflater();
        View section = inflater.inflate(R.layout.tone_tester_result_section, linear, false);

        TextView tv_pinyin = (TextView) section.findViewById(R.id.tone_test_result_pinyin);
        tv_pinyin.setText(PinyinParser.convert_to_pretty_version(pin));

        TextView tv_translations = (TextView) section.findViewById(R.id.tone_test_result_translations);
        ArrayList<Vocabulary> trans = myDB.getWordsWithPinyin(hanzi, pin);
        String str_trans = new String();
        for (int i=0; i<trans.size(); i++) {
            Vocabulary voc = trans.get(i);
            str_trans += voc.chinese + "(" + PinyinParser.convert_to_pretty_version(voc.pinyin) + ") ";
            for (int j=0; j<voc.english.size(); j++) {
                String tr=voc.english.get(j);
                str_trans += tr;
                if (j<voc.english.size()-1)
                    str_trans += ", ";
            }
            if (i<trans.size()-1)
                str_trans += "\n";
        }
        tv_translations.setText(str_trans);

        linear.addView(section, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void handle_cancel(View v) {
        myDB.restartStudy(hanzi);
        set_next_study();
    }

    public void handle_ok(View v) {
        myDB.resetTime(hanzi);
        show_solution();
    }

    public void handle_next(View v) {
        set_next_study();
    }
}
