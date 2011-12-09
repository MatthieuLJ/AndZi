package com.matthieu;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.gesture.*;
import android.graphics.Point;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class ToneTester extends Activity implements GestureOverlayView.OnGesturePerformedListener {
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

        setContentView(R.layout.tone_tester);

        GestureOverlayView overlay = (GestureOverlayView) findViewById(R.id.tone_tester_gestureview);
        overlay.addOnGesturePerformedListener(this);

        set_next_study();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        set_next_study();
    }

    private void set_next_study() {
        ArrayList<String> all = myDB.getAllFilteredToStudy();
        ArrayList<String> pinyins;
        hanzi = null;

        while((hanzi==null) && (all.size()>0)) {
            hanzi=all.remove(0);
            pinyins = myDB.getPinyinForChar(hanzi);

            pinyin = null;
            while ((pinyin == null) && (pinyins.size() > 0)) {
                int j = (int) ((pinyins.size()-1)*Math.random());
                pinyin = new Pinyin(pinyins.remove(j));
                if (pinyin.get_tone() == 5) {
                    //Log.v(TAG,"Reject study of pinyin "+pinyin.get_pretty());
                    pinyin=null;
                }
            }
            if (pinyin == null) {
                //Log.v(TAG,"Reject study of character "+hanzi);
                hanzi=null;
            }
        }

        if (hanzi == null) {
            Toast.makeText(this, "Sorry, nothing to test you on", 5).show();
            this.finish();
            return;
        }
        
        pinyins = myDB.getPinyinForChar(hanzi);
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


        Log.d(TAG, "Expects tones : "+tones.toString());
    }

    private int check_guesses() {
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
        i.putExtra("Mode", 0);
        startActivity(i);
    }

    public void onGesturePerformed(GestureOverlayView gestureOverlayView, Gesture gesture) {
        int tone=0;

        if (gesture.getStrokesCount() > 1)
            return;

        GestureStroke stroke = gesture.getStrokes().get(0);
        ArrayList<Point> points = new ArrayList<Point>();

        for (int i=0; i<stroke.points.length; i+=2) {
            if ((i>0) && (stroke.points[i] < stroke.points[i-2])) {
                return; // cannot go right to left
            }

            points.add(new Point((int)stroke.points[i], (int)stroke.points[i+1]));
        }

        double angle = Math.atan(((float)points.get(points.size()-1).y - points.get(0).y)
                / ((float)points.get(points.size()-1).x - points.get(0).x))*180/Math.PI;

        boolean straight=true;
        for (int i=1; i<points.size(); i++) {
            double new_angle = Math.atan(((float)points.get(i).y - points.get(i-1).y)
                / ((float)points.get(i).x - points.get(i-1).x))*180/Math.PI;
            if (Math.abs(new_angle - angle) > 30) {
                straight=false;
                break;
            }
        }
        if (straight) {
            if (Math.abs(angle) < 30)
                tone = 1;
            else if (Math.abs(angle) > 60)
                return; // too steep
            else if (angle >=30)
                tone = 4;
            else if (angle <= -30)
                tone = 2;
        } else {
            // find the bottom point
            int bottom=0;
            for (int i=0; i<points.size(); i++) {
                if (points.get(i).y == gesture.getBoundingBox().bottom) {
                    bottom = i;
                    break;
                }
            }
            if ((bottom == 0) || (bottom == points.size()-1)) {
                return;
            }
            double angle1 = Math.atan(((float)points.get(bottom).y - points.get(0).y)
                    / ((float)points.get(bottom).x - points.get(0).x))*180/Math.PI;
            for (int i=1; i<=bottom; i++) {
                double new_angle = Math.atan(((float)points.get(i).y - points.get(i-1).y)
                        / ((float)points.get(i).x - points.get(i-1).x))*180/Math.PI;
                if (Math.abs(new_angle - angle1) > 50) {
                    return;
                }
            }
            double angle2 = Math.atan(((float)points.get(points.size()-1).y - points.get(bottom).y)
                    / ((float)points.get(points.size()-1).x - points.get(bottom).x))*180/Math.PI;
            for (int i=bottom+1; i<points.size(); i++) {
                double new_angle = Math.atan(((float)points.get(i).y - points.get(i-1).y)
                        / ((float)points.get(i).x - points.get(i-1).x))*180/Math.PI;
                if (Math.abs(new_angle - angle2) > 50) {
                    return;
                }
            }
            tone = 3;
        }
        
        CheckBox cb=null;
        switch (tone) {
            case 1:
                cb = (CheckBox) findViewById(R.id.tone_test_first_tone);
                break;
            case 2:
                cb = (CheckBox) findViewById(R.id.tone_test_second_tone);
                break;
            case 3:
                cb = (CheckBox) findViewById(R.id.tone_test_third_tone);
                break;
            case 4:
                cb = (CheckBox) findViewById(R.id.tone_test_fourth_tone);
                break;
        }
        if (cb != null)
            cb.setChecked(true);
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
