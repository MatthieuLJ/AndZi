package com.matthieu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

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
        if (resultCode==0) {
            super.onActivityResult(requestCode,resultCode,data);
            finish();
        }
        else if (resultCode == TestActivity.NOTHING_TO_TEST) {
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
        while(tests_with_nothing.contains(target)) {
            current_index++;
            if (current_index >= tests.size())
                break;
            target = tests.get(current_index);
        }

        if (current_index == tests.size()) {
            AlertDialog.Builder nothing_to_study = new AlertDialog.Builder(Test.this);
            nothing_to_study.setCancelable(false);
            nothing_to_study.setMessage("You master everything, do you want to continue ?");
            nothing_to_study.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int i) {
                    dialog.dismiss();
                    HanziDB myDB = ((AndZiApplication) getApplication()).getMyDB();
                    myDB.getMoreStudy(5);
                    tests_with_nothing.clear();
                    start_test();
                }
            });
            nothing_to_study.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int i) {
                    dialog.dismiss();
                    finish();
                }
            });
            nothing_to_study.create();
            nothing_to_study.show();
            return;
        }

        Intent i=new Intent(this, target);
        i.putExtra("SingleTest", true);
        this.startActivityForResult(i, current_index);
    }
}
