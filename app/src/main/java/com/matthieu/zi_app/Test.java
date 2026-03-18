package com.matthieu.zi_app;

import android.app.Activity;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.matthieu.zi_app.R;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public abstract class Test {
    protected static HanziDB myDB, refDB;
    protected TestActivity activity;

    static private final Timer timer = new Timer();

    final static public int CORRECT=1;
    final static public int INCORRECT=2;
    final static public int OTHER=3;

    public void attach(Activity a) {
        myDB = ((AndZiApplication) a.getApplicationContext()).getMyDB();
        refDB = ((AndZiApplication) a.getApplicationContext()).getRefDB();
        activity = (TestActivity) a;
    }

    abstract boolean set_hanzi_to_test(String s);
    abstract View get_view();
    public void kill_view(View v) {}

    public abstract static class SolutionView {
        int good;
        String question, answer;
        TestActivity parent_activity;
        View result_view;
        Handler handler;

        abstract View get_view(LayoutInflater inflater, ViewGroup container);
        public void kill_view(View v) {}

        public SolutionView(final String question, TestActivity parent) {
            this.question = question;
            this.parent_activity = parent;

            handler = new Handler();
        }

        public View prepare_view(LayoutInflater inflater) {
            result_view = inflater.inflate(R.layout.test_result, null);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    DetailsFragment.populate_details_view(parent_activity, (ViewGroup) result_view.findViewById(R.id.test_results), question, false, parent_activity);
                }
            }, 500);
            return result_view;
        }

        public void set_answer(int good, String answer) {
            this.good = good;
            this.answer = answer;
            TimerTask updater = new TimerTask() {
                @Override
                public void run() {
                    update_score();
                }
            };
            timer.schedule(updater, 500);
        }

        public void update_score() {
            if (parent_activity != null)
                parent_activity.score_changed();
        }

        protected View show_solution(boolean show_accept) {
            if ((good == Test.INCORRECT) && (!answer.equals(""))) {
                LinearLayout bad_guess = result_view.findViewById(R.id.test_result_wrong_guess);
                TextView bad_guess_text = new TextView(parent_activity);
                bad_guess_text.setText("You guessed: "+answer);
                bad_guess_text.setTextSize(TypedValue.COMPLEX_UNIT_PX, parent_activity.getResources().getDimension(R.dimen.large_test_text));
                bad_guess_text.setTextColor(parent_activity.getResources().getColor(R.color.test_failure));
                bad_guess_text.setGravity(Gravity.CENTER_HORIZONTAL);
                bad_guess.addView(bad_guess_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                final FrameLayout bad_guess_details = new FrameLayout(parent_activity);
                bad_guess_details.setId(R.id.test_bad_guess);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        DetailsFragment.populate_details_view(parent_activity, bad_guess_details, answer, false, parent_activity);
                    }
                }, 500);

                bad_guess.addView(bad_guess_details, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                (result_view.findViewById(R.id.test_result_wrong)).setVisibility(View.VISIBLE);
                if (show_accept) {
                    (result_view.findViewById(R.id.test_result_accept)).setVisibility(View.VISIBLE);
                    (result_view.findViewById(R.id.test_result_accept)).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            handle_good(view);
                        }
                    });
                }
            }

            return result_view;
        }

        protected void hide_details(ViewGroup container) {
            ViewGroup test_results = container.findViewById(R.id.test_results);
            if (test_results != null)
                DetailsFragment.kill_detail_view(test_results);
            ViewGroup test_bad_guess = container.findViewById(R.id.test_bad_guess);
            if (test_bad_guess != null)
                DetailsFragment.kill_detail_view(test_bad_guess);
        }

        public void handle_good(View v) {
            v.setEnabled(false);
            TimerTask updater = new TimerTask() {
                @Override
                public void run() {
                    restore_score();
                    good = Test.CORRECT;
                    update_score();
                }
            };
            timer.schedule(updater, 500);
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
