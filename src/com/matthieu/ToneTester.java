package com.matthieu;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class ToneTester extends Activity{
    protected HanziDB myDB=null;
    private String hanzi;
    private Pinyin pinyin;
    private String radical;
    private ArrayList<Integer> tones;
    int show_result;

    private final String TAG="ToneTester";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        myDB = ((AndZiApplication) getApplication()).myDB;
        tones=new ArrayList<Integer>();

        Log.d(TAG, "Going to show results: "+sp.getString("show_result", "always"));
        if (sp.getString("show_result", "always").equals("always")) {
            show_result = 1;
        }
        else if (sp.getString("show_result", "always").equals("sometimes")) {
            show_result = 2;
        }
        else {
            show_result = 3;
        }

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

    private int check_guesses() {
        ArrayList<Integer> guesses=new ArrayList<Integer>();
        CheckBox cb;
        int res=0;

        // check if any wrong guess

        cb = (CheckBox) findViewById(R.id.tone_test_first_tone);
        if (cb.isChecked() && !tones.contains(1)) { myDB.restartStudy(hanzi); return res; }
        if (tones.contains(1) && !cb.isChecked()) { myDB.restartStudy(hanzi); return res; }
        cb = (CheckBox) findViewById(R.id.tone_test_second_tone);
        if (cb.isChecked() && !tones.contains(2)) { myDB.restartStudy(hanzi); return res; }
        if (tones.contains(2) && !cb.isChecked()) { myDB.restartStudy(hanzi); return res; }
        cb = (CheckBox) findViewById(R.id.tone_test_third_tone);
        if (cb.isChecked() && !tones.contains(3)) { myDB.restartStudy(hanzi); return res; }
        if (tones.contains(3) && !cb.isChecked()) { myDB.restartStudy(hanzi); return res; }
        cb = (CheckBox) findViewById(R.id.tone_test_fourth_tone);
        if (cb.isChecked() && !tones.contains(4)) { myDB.restartStudy(hanzi); return res; }
        if (tones.contains(4) && !cb.isChecked()) { myDB.restartStudy(hanzi); return res; }
        cb = (CheckBox) findViewById(R.id.tone_test_fifth_tone);
        if (cb.isChecked() && !tones.contains(5)) { myDB.restartStudy(hanzi); return res; }
        // no need to check if 5th tone is missing...

        // if we got here, we're good... :)
        if (myDB.getBoxForChar(hanzi) == 0) {
            myDB.betterBox(hanzi);
        }
        else {
            myDB.resetTime(hanzi);
        }
        res=1;
        return res;
    }

    private void show_solution() {
        ArrayList<String> pinyins = new ArrayList<String>();

        for (int tone : tones) {
            pinyins.add(radical + tone);
        }
        
        Intent i = new Intent(this, TestResultActivity.class);
		i.putExtra("Hanzi", hanzi);
		i.putExtra("Pinyins", pinyins);
        startActivity(i);
    }


    public void handle_cancel(View v) {
        myDB.restartStudy(hanzi);
        set_next_study();
    }

    public void handle_ok(View v) {
        Toast toast;
        int res = check_guesses();
        if ((show_result==1) || ((show_result == 2) && (res == 0))) {
            show_solution();
        }
        else {
            if (res == 0) {
                toast = Toast.makeText(this, "Sorry, this is wrong", 2);
            }
            else {
                toast = Toast.makeText(this, "Well done !", 2);
            }
            toast.show();
            set_next_study();
        }
    }
}
