/******************************
 * Activity to guess the tone of a character
 */
package com.matthieu;

import android.gesture.*;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import java.util.ArrayList;

public class GuessTone extends TestFragment implements GestureOverlayView.OnGesturePerformedListener {
    private Pinyin pinyin;
    private ArrayList<Integer> tones=new ArrayList<Integer>();

    private final String TAG="GuessTone";

    @Override
    protected boolean set_hanzi_to_test(String hanzi) {
        super.set_hanzi_to_test(hanzi);
        ArrayList<String> pinyins;

        pinyins = myDB.getPinyinForChar(hanzi);
        if (pinyins.size() != 1)
            hanzi=null;
        else {
            pinyin = new Pinyin(pinyins.get(0));
            if (pinyin.get_tone()==5)
                hanzi=null;
        }

        if (hanzi == null) {
            return false;
        }
        Log.d(TAG, "GuessTone OK");

        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.guess_tone, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        update_test_view(getView());
    }

    @Override
    public void update_test_view(View v) {
        if (v == null)
            return;

        ArrayList<String> pinyins;
        String radical;

        GestureOverlayView overlay = (GestureOverlayView) v.findViewById(R.id.guess_tone_gestureview);
        overlay.addOnGesturePerformedListener(this);

        pinyins = myDB.getPinyinForChar(hanzi);
        radical = new Pinyin(pinyin.get_radical()+"5").get_pretty();
        tones.clear();

        for (String p : pinyins) {
            Pinyin tmp_p=new Pinyin(p);
            if (tmp_p.get_radical().equals(pinyin.get_radical())) {
                tones.add(tmp_p.get_tone());
            }
        }

        TextView tv_hanzi = (TextView) v.findViewById(R.id.guess_tone_chinese);
        TextView tv_pinyin = (TextView) v.findViewById(R.id.guess_tone_pinyin);

        tv_hanzi.setText(hanzi);
        tv_pinyin.setText(radical);

        CheckBox cb;
        cb = (CheckBox) v.findViewById(R.id.guess_tone_first_tone);
        cb.setChecked(false);
        cb = (CheckBox) v.findViewById(R.id.guess_tone_second_tone);
        cb.setChecked(false);
        cb = (CheckBox) v.findViewById(R.id.guess_tone_third_tone);
        cb.setChecked(false);
        cb = (CheckBox) v.findViewById(R.id.guess_tone_fourth_tone);
        cb.setChecked(false);

        Log.d(TAG, "Expects tones : "+tones.toString());
        v.invalidate();
    }

    protected int check_guess(String guess) {
        int res=INCORRECT;

        // check if did not guess anything
        if (guess.length()==0)
            return res;
        // check if any wrong guess
        if (guess.contains("1") && !tones.contains(1))
            return res;
        if (guess.contains("2") && !tones.contains(2))
            return res;
        if (guess.contains("3") && !tones.contains(3))
            return res;
        if (guess.contains("4") && !tones.contains(4))
            return res;

        return CORRECT;
    }

    public void onGesturePerformed(GestureOverlayView gestureOverlayView, Gesture gesture) {
        int tone=0;

        if (gesture.getStrokesCount() > 1)
            return;

        GestureStroke stroke = gesture.getStrokes().get(0);

        if (stroke.points.length < 4) {
//            Log.v(TAG, "Too short");
            return;
        }

        ArrayList<Point> points = new ArrayList<Point>();

        for (int i=0; i<stroke.points.length; i+=2) {
            if ((i>0) && (stroke.points[i] < stroke.points[i-2])) {
//                Log.v(TAG, "Going right to left");
                return; // cannot go right to left
            }

            points.add(new Point((int)stroke.points[i], (int)stroke.points[i+1]));
        }

        double angle = Math.atan(((float)points.get(points.size()-1).y - points.get(0).y)
                / ((float)points.get(points.size()-1).x - points.get(0).x))*180/Math.PI;

        boolean straight=true;
        // ignore the extremities
        for (int i=2; i<points.size()-1; i++) {
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
            // check the proportions are about right
            float ratio = (gesture.getBoundingBox().right - gesture.getBoundingBox().left) / (gesture.getBoundingBox().bottom - gesture.getBoundingBox().top);
            if ((ratio < 0.3) || (ratio > 2)) {
//                Log.v(TAG, "Bad proportions "+ratio);
                return;
            }

            // find the bottom point
            int bottom=0;
            for (int i=1; i<points.size(); i++) {
                if (points.get(i).y  > points.get(bottom).y) {
                    bottom = i;
                }
            }
            if ((bottom < points.size()/ 5) || (bottom > 4*points.size()/5)) {
//                Log.v(TAG, "Bottom not centered "+bottom+" out of "+points.size());
                return;
            }
            double angle1 = Math.atan(((float)points.get(bottom).y - points.get(0).y)
                    / ((float)points.get(bottom).x - points.get(0).x))*180/Math.PI;
            for (int i=1; i<=bottom; i++) {
                double square_distance = Math.pow((float)points.get(i).y - points.get(i-1).y,2) + Math.pow((float)points.get(i).x - points.get(i-1).x,2);
                if (square_distance <= 100)
                    continue;
                double new_angle = Math.atan(((float)points.get(i).y - points.get(i-1).y)
                        / ((float)points.get(i).x - points.get(i-1).x))*180/Math.PI;
                if (Math.abs(new_angle - angle1) > 50) {
//                    Log.d(TAG, "Too high angle");
                    return;
                }
            }
            double angle2 = Math.atan(((float)points.get(points.size()-1).y - points.get(bottom).y)
                    / ((float)points.get(points.size()-1).x - points.get(bottom).x))*180/Math.PI;
            for (int i=bottom+1; i<points.size(); i++) {
                double square_distance = Math.pow((float)points.get(i).y - points.get(i-1).y,2) + Math.pow((float)points.get(i).x - points.get(i-1).x,2);
                if (square_distance <= 100)
                    continue;
                double new_angle = Math.atan(((float)points.get(i).y - points.get(i-1).y)
                        / ((float)points.get(i).x - points.get(i-1).x))*180/Math.PI;
                if (Math.abs(new_angle - angle2) > 50) {
//                    Log.d(TAG, "Too high angle");
                    return;
                }
            }
            tone = 3;
        }
        
        CheckBox cb=null;
        switch (tone) {
            case 1:
                cb = (CheckBox) getView().findViewById(R.id.guess_tone_first_tone);
                break;
            case 2:
                cb = (CheckBox) getView().findViewById(R.id.guess_tone_second_tone);
                break;
            case 3:
                cb = (CheckBox) getView().findViewById(R.id.guess_tone_third_tone);
                break;
            case 4:
                cb = (CheckBox) getView().findViewById(R.id.guess_tone_fourth_tone);
                break;
        }
        if (cb != null)
            cb.setChecked(true);
    }

    public boolean handle_ok(View v) {
        String tones_checked="";
        CheckBox cb;

        cb = (CheckBox) getView().findViewById(R.id.guess_tone_first_tone);
        if (cb.isChecked()) tones_checked+="1";
        cb = (CheckBox) getView().findViewById(R.id.guess_tone_second_tone);
        if (cb.isChecked()) tones_checked+="2";
        cb = (CheckBox) getView().findViewById(R.id.guess_tone_third_tone);
        if (cb.isChecked()) tones_checked+="3";
        cb = (CheckBox) getView().findViewById(R.id.guess_tone_fourth_tone);
        if (cb.isChecked()) tones_checked+="4";

        int correctness = check_guess(tones_checked);
        GuessToneResultFragment solution_fragment = new GuessToneResultFragment(hanzi, tones_checked, hanzi, correctness);
        ((TestActivity)getActivity()).new_solution_fragment(solution_fragment, correctness);

        return true;
    }

    protected static class GuessToneResultFragment extends SolutionFragment {
        public GuessToneResultFragment(String question, String answer, String hanzi, int good) {
            super(question, answer, hanzi, good);    //To change body of overridden methods use File | Settings | File Templates.
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            final View res = inflater.inflate(R.layout.test_result, container, false);

            DetailsFragment.populate_details_view(GuessToneResultFragment.this, (ViewGroup) res.findViewById(R.id.test_results), hanzi, false, (TestActivity) getActivity());
            if ((good == INCORRECT) && (!answer.equals(""))) {
                LinearLayout bad_guess = (LinearLayout) res.findViewById(R.id.test_result_wrong_guess);
                TextView bad_guess_text = new TextView(getActivity());
                if (answer.length() == 0) {
                    bad_guess_text.setText("You did not guess anything");
                }
                else if (answer.length() == 1) {
                    bad_guess_text.setText("You guessed tone: "+answer);
                }
                else {
                    String label = "You guessed tones: ";
                    for(int i=0; i<answer.length(); i++) {
                        label += answer.substring(i, i+1);
                        if (i<answer.length()-1)
                            label+=", ";
                    }
                    bad_guess_text.setText(label);
                }
                bad_guess_text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                bad_guess_text.setTextColor(0xffdd0000);
                bad_guess_text.setGravity(Gravity.CENTER_HORIZONTAL);
                bad_guess.addView(bad_guess_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                (res.findViewById(R.id.test_result_wrong)).setVisibility(View.VISIBLE);
            }
            return res;
        }

        @Override
        public void onDestroyView() {
            DetailsFragment.kill_detail_view((ViewGroup) getView().findViewById(R.id.test_results));
            super.onDestroyView();
        }

        @Override
        public void update_score() {
            if (good == CORRECT)
                myDB.betterBox(hanzi);
            else
                myDB.worseBox(hanzi);
        }
    }
}
