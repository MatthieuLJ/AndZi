/******************************
 * Entry class to pick tests randomly
 */
package com.matthieu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class Test extends Activity {
    private ArrayList<Class> tests = new ArrayList<Class>(Arrays.asList(GuessChar.class, GuessChineseWord.class, GuessContaining.class,
            GuessEnglish.class, GuessPinyin.class, GuessTone.class));
    private HanziDB myDB;
    private int current_index=0;
    private boolean continue_with_more_chars=false;
    private String current_hanzi;

    private static final String TAG="Test_Master";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Collections.shuffle(tests);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        start_test();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode==0) {
            super.onActivityResult(requestCode,resultCode,data);
            finish();
        }
        else if (resultCode == TestActivity.NOTHING_TO_TEST) {
            current_index++;
            start_test();
        } else {
            start_test();
        }
    }

    private void set_next_char_to_study() {
        ArrayList<String> hanzis=myDB.getAllFilteredToStudy();
        if (continue_with_more_chars)
            hanzis.addAll(myDB.getMostAdvancedExpiring(5));

        if (hanzis.size()==0) {
            if (!continue_with_more_chars) {
                AlertDialog.Builder nothing_to_study = new AlertDialog.Builder(Test.this);
                nothing_to_study.setCancelable(false);
                if (myDB.isFilterEnabled()) {
                    nothing_to_study.setMessage(getResources().getText(R.string.test_mastering_disable_filter));
                    nothing_to_study.setPositiveButton(getResources().getText(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            dialog.dismiss();
                            myDB.enableFilter(false);
                            start_test();
                        }
                    });
                } else {
                    nothing_to_study.setMessage(getResources().getText(R.string.test_mastering));
                    nothing_to_study.setPositiveButton(getResources().getText(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            dialog.dismiss();
                            continue_with_more_chars=true;
                            start_test();
                        }
                    });
                }
                nothing_to_study.setNegativeButton(getResources().getText(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                        finish();
                    }
                });
                nothing_to_study.create();
                nothing_to_study.show();
            } else {
                finish();
            }
            current_hanzi=null;
        }
        else {
            current_hanzi = hanzis.remove((int)(hanzis.size()*Math.pow(Math.random(),2)));
            Log.d(TAG, "Will study" + current_hanzi);
        }
    }

    private void launch_test() {
        Class target = tests.get(current_index);
        Intent i=new Intent(this, target);
        i.putExtra("hanzi", current_hanzi);
        this.startActivityForResult(i, current_index);
    }

    private void start_test() {
        Collections.shuffle(tests);
        set_next_char_to_study();
        current_index=0;
        if (current_hanzi!=null)
            launch_test();
    }


}
