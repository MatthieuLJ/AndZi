package com.matthieu;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public abstract class Test {
    protected static HanziDB myDB, refDB;
    protected Activity activity;

    final static public int CORRECT=1;
    final static public int INCORRECT=2;
    final static public int OTHER=3;

    public void attach(Activity a) {
        myDB = ((AndZiApplication) a.getApplicationContext()).getMyDB();
        refDB = ((AndZiApplication) a.getApplicationContext()).getRefDB();
        activity = a;
    }

    abstract boolean set_hanzi_to_test(String s);
    abstract View get_view();
    public void kill_view(ViewGroup v) {};

    public abstract static class SolutionFragment extends Fragment {
        int good;
        String question, answer;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            get_arguments();
            TimerTask updater = new TimerTask() {
                @Override
                public void run() {
                    //Log.d(TAG, "Updating scores !");
                    update_score();
                    /*final TestActivity act = (TestActivity) getActivity();
                    if (act == null)
                        return;
                    act.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            act.update_stats();
                        }
                    });*/
                }
            };
            new Timer().schedule(updater, 500);
        }


        protected void get_arguments() {
            Bundle bundle = getArguments();
            if (bundle != null) {
                good = bundle.getInt("good");
                answer = bundle.getString("answer");
                question = bundle.getString("question");
            }
        }

        abstract public void update_score();

        protected View show_details(LayoutInflater inflater, ViewGroup container, boolean show_accept) {
            final View res = inflater.inflate(R.layout.test_result, container, false);

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    DetailsFragment.populate_details_view(SolutionFragment.this, (ViewGroup) res.findViewById(R.id.test_results), question, false, (TestActivity) getActivity());
                }
            }, 500);

            if ((good == Test.INCORRECT) && (!answer.equals(""))) {
                LinearLayout bad_guess = (LinearLayout) res.findViewById(R.id.test_result_wrong_guess);
                TextView bad_guess_text = new TextView(getActivity());
                bad_guess_text.setText("You guessed: "+answer);
                bad_guess_text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                bad_guess_text.setTextColor(0xffdd0000);
                bad_guess_text.setGravity(Gravity.CENTER_HORIZONTAL);
                bad_guess.addView(bad_guess_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                final FrameLayout bad_guess_details = new FrameLayout(getActivity());
                bad_guess_details.setId(R.id.test_bad_guess);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        DetailsFragment.populate_details_view(SolutionFragment.this, bad_guess_details, answer, false, (TestActivity) getActivity());
                    }
                }, 500);

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
            TimerTask updater = new TimerTask() {
                @Override
                public void run() {
                    restore_score();
                    good = Test.CORRECT;
                    update_score();
                    /*final TestActivity act = (TestActivity) getActivity();
                    if (act == null)
                        return;
                    act.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            act.update_stats();
                        }
                    });*/
                }
            };
            new Timer().schedule(updater, 500);
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
