package com.matthieu;

import android.gesture.*;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;

public class GuessTone extends TestActivity implements GestureOverlayView.OnGesturePerformedListener {
    protected HanziDB myDB=null;
    private String hanzi;
    private Pinyin pinyin;
    private String radical;
    private ArrayList<Integer> tones;

    private final String TAG="GuessTone";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        tones=new ArrayList<Integer>();

        setContentView(R.layout.guess_tone);

        GestureOverlayView overlay = (GestureOverlayView) findViewById(R.id.guess_tone_gestureview);
        overlay.addOnGesturePerformedListener(this);
    }

    protected void set_next_study() {
        super.set_next_study();
        ArrayList<String> all = myDB.getAllFilteredToStudy();
        ArrayList<String> pinyins;
        hanzi = null;

        while((hanzi==null) && (all.size()>0)) {
            hanzi=all.remove(0);
            pinyins = myDB.getPinyinForChar(hanzi);

            pinyin = null;
            while ((pinyin == null) && (pinyins.size() > 0)) {
                int j = (int) (pinyins.size()*Math.random());
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
        display_test();
    }

    @Override
    protected void display_test() {
        ArrayList<String> pinyins;
        pinyins = myDB.getPinyinForChar(hanzi);
        radical = pinyin.get_radical();
        tones.clear();

        for (String p : pinyins) {
            Pinyin tmp_p=new Pinyin(p);
            if (tmp_p.get_radical().equals(radical)) {
                tones.add(tmp_p.get_tone());
            }
        }

        TextView tv_hanzi = (TextView) findViewById(R.id.guess_tone_chinese);
        TextView tv_pinyin = (TextView) findViewById(R.id.guess_tone_pinyin);

        tv_hanzi.setText(hanzi);
        tv_pinyin.setText(radical);

        CheckBox cb;
        cb = (CheckBox) findViewById(R.id.guess_tone_first_tone);
        cb.setChecked(false);
        cb = (CheckBox) findViewById(R.id.guess_tone_second_tone);
        cb.setChecked(false);
        cb = (CheckBox) findViewById(R.id.guess_tone_third_tone);
        cb.setChecked(false);
        cb = (CheckBox) findViewById(R.id.guess_tone_fourth_tone);
        cb.setChecked(false);


        Log.d(TAG, "Expects tones : "+tones.toString());
    }

    protected int check_guess(String guess) {
        int res=0;

        // check if any wrong guess
        if ((guess.contains("1") && !tones.contains(1)) || (tones.contains(1) && !guess.contains("1")))
            return res;
        if ((guess.contains("2") && !tones.contains(2)) || (tones.contains(2) && !guess.contains("2")))
            return res;
        if ((guess.contains("3") && !tones.contains(3)) || (tones.contains(3) && !guess.contains("3")))
            return res;
        if ((guess.contains("4") && !tones.contains(4)) || (tones.contains(4) && !guess.contains("4")))
            return res;
        
        res=1;
        return res;
    }

    @Override
    protected void update_scores(int good) {
        if (good == 1) {
            if (myDB.getBoxForChar(hanzi) == 0) {
                myDB.betterBox(hanzi);
            }
            else {
                myDB.resetTime(hanzi);
            }
        }
        else {
            myDB.restartStudy(hanzi);
        }

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
                cb = (CheckBox) findViewById(R.id.guess_tone_first_tone);
                break;
            case 2:
                cb = (CheckBox) findViewById(R.id.guess_tone_second_tone);
                break;
            case 3:
                cb = (CheckBox) findViewById(R.id.guess_tone_third_tone);
                break;
            case 4:
                cb = (CheckBox) findViewById(R.id.guess_tone_fourth_tone);
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
        String tones_checked="";
        CheckBox cb;

        cb = (CheckBox) findViewById(R.id.guess_tone_first_tone);
        if (cb.isChecked()) tones_checked+="1";
        cb = (CheckBox) findViewById(R.id.guess_tone_second_tone);
        if (cb.isChecked()) tones_checked+="2";
        cb = (CheckBox) findViewById(R.id.guess_tone_third_tone);
        if (cb.isChecked()) tones_checked+="3";
        cb = (CheckBox) findViewById(R.id.guess_tone_fourth_tone);
        if (cb.isChecked()) tones_checked+="4";

        handle_submit(hanzi, new ArrayList<String>(Arrays.asList(pinyin.get_lame())), tones_checked, false);
    }


}
