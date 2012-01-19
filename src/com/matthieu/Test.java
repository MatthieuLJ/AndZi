package com.matthieu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class Test extends Activity {
    private ArrayList<Class> tests = new ArrayList<Class>(Arrays.asList(GuessChar.class, GuessChineseWord.class, GuessContaining.class,
            GuessEnglish.class, GuessPinyin.class, GuessTone.class));
    private ArrayList<Class> tests_with_nothing=new ArrayList<Class>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Collections.shuffle(tests);
        start_test();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == TestActivity.NOTHING_TO_TEST) {
            tests_with_nothing.add(tests.get(requestCode));
            start_test();
        } else if (resultCode == TestActivity.CORRECT) {
            Collections.shuffle(tests);
            start_test();
        } else {
            tests_with_nothing.clear();
            start_test();
        }
    }

    private void start_test() {
        int current_index=0;
        Collections.shuffle(tests);
        Class target = tests.get(current_index);
        while((tests_with_nothing.contains(target)) && (current_index<tests.size())) {
            current_index++;
            target = tests.get(current_index);
        }

        if (current_index == tests.size()) {
            finish();
            return;
        }

        Intent i=new Intent(this, target);
        i.putExtra("SingleTest", true);
        this.startActivityForResult(i, current_index);
    }
}
