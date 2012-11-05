/******************************
 * Parent class for all tests
 */
package com.matthieu;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.matthieu.widget.handle_details_request;

import java.util.HashMap;

public abstract class TestFragment extends Fragment {
    protected String hanzi;

    protected static HanziDB myDB=null;
    protected static HanziDB refDB=null;

    private static final String TAG="TestFragment";

    final static public int CORRECT=1;
    final static public int INCORRECT=2;
    final static public int OTHER=3;

    public void set_activity(Activity activity) {
        myDB = ((AndZiApplication) activity.getApplication()).getMyDB();
        refDB = ((AndZiApplication) activity.getApplication()).getRefDB();
    }

    protected boolean set_hanzi_to_test(String hanzi) {
        this.hanzi = hanzi;
        return true;
    }

    abstract public boolean handle_ok(View v);
    abstract public void update_test_view(View v);

    protected abstract static class SolutionFragment extends Fragment {
        int good;
        String question, answer, hanzi;

        public SolutionFragment(String question, String answer, String hanzi, int good) {
            this.good = good;
            this.answer = answer;
            this.question = question;
            this.hanzi = hanzi;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            Thread updater = new Thread() {
                @Override
                public void run() {
                    Log.d(TAG, "Updating scores !");
                    update_score();
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ((TestActivity)getActivity()).update_stats();
                        }
                    });
                }
            };
            updater.start();
        }

        abstract public void update_score();

        protected View show_details(LayoutInflater inflater, ViewGroup container, boolean show_accept) {
            final View res = inflater.inflate(R.layout.test_result, container, false);

            DetailsFragment.populate_details_view(this, (ViewGroup) res.findViewById(R.id.test_results), hanzi, false, (TestActivity) getActivity());
            if ((good == INCORRECT) && (!answer.equals(""))) {
                LinearLayout bad_guess = (LinearLayout) res.findViewById(R.id.test_result_wrong_guess);
                TextView bad_guess_text = new TextView(getActivity());
                bad_guess_text.setText("You guessed: "+answer);
                bad_guess_text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                bad_guess_text.setTextColor(0xffdd0000);
                bad_guess_text.setGravity(Gravity.CENTER_HORIZONTAL);
                bad_guess.addView(bad_guess_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                FrameLayout bad_guess_details = new FrameLayout(getActivity());
                bad_guess_details.setId(R.id.test_bad_guess);
                DetailsFragment.populate_details_view(this, bad_guess_details, answer, false, (TestActivity) getActivity());
                bad_guess.addView(bad_guess_details, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                (res.findViewById(R.id.test_result_wrong)).setVisibility(View.VISIBLE);
                if (show_accept) {
                    (res.findViewById(R.id.test_result_accept)).setVisibility(View.VISIBLE);
                    (res.findViewById(R.id.test_result_accept)).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            handle_good(view);
                        }
                    });
                }
            }

            return res;
        }

        protected void hide_details(ViewGroup container) {
            ViewGroup test_results = (ViewGroup) container.findViewById(R.id.test_results);
            if (test_results != null)
                DetailsFragment.kill_detail_view(test_results);
            ViewGroup test_bad_guess = (ViewGroup) container.findViewById(R.id.test_bad_guess);
            if (test_bad_guess != null)
                DetailsFragment.kill_detail_view(test_bad_guess);
        }

        public void handle_good(View v) {
            v.setEnabled(false);
            Thread updater = new Thread() {
                @Override
                public void run() {
                    restore_score();
                    good = CORRECT;
                    update_score();
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ((TestActivity)getActivity()).update_stats();
                        }
                    });
                }
            };
            updater.start();
        }

        protected class saved_score {
            int box;
            long time;
            public saved_score(int b, long t) {
                this.box = b;
                this.time = t;
            }
        }
        HashMap<String, saved_score> old_scores;

        protected void restore_score() {
            if (old_scores == null)
                return;
            for (String c : old_scores.keySet()) {
                myDB.setBoxForChar(c, old_scores.get(c).box);
                myDB.setExpirationTimeForChar(c, old_scores.get(c).time);
            }
        }

    }
}
