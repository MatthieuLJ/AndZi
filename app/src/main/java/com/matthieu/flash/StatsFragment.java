/******************************
 * Simple activity, mostly with some stats and the ProgressGraph
 */
package com.matthieu.flash;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.TableRow;
import android.widget.TextView;
import com.matthieu.flash.widget.ProgressGraph;
import com.matthieu.flash.R;

import java.util.*;

public class StatsFragment extends Fragment {
    protected HanziDB myDB=null;
    Timer timer;

    private static final boolean DEBUG = false;
    private final String TAG="Stats";

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        myDB = ((AndZiApplication) activity.getApplication()).getMyDB();
    }

    @Override
    public void onResume() {
        super.onResume();
        myDB = ((AndZiApplication) getActivity().getApplication()).getMyDB();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View res = inflater.inflate(R.layout.db, container, false);

        final ProgressGraph graph = res.findViewById(R.id.graph);
        graph.set_data(((AndZiApplication) getActivity().getApplication()).getProgress());

        Button zoomin= res.findViewById(R.id.graph_in);
        zoomin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                graph.zoom_in();
                update_buttons(getView());
            }
        });
        Button zoomout= res.findViewById(R.id.graph_out);
        zoomout.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                graph.zoom_out();
                update_buttons(getView());
            }
        });

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        update_stats();
                    }
                });
            }
        }, 10*60*1000, 10*60*1000);

        update_stats(res);
        update_time_to_study(res);
        update_buttons(res);

        return res;
    }

    @Override
    public void onDestroyView() {
        timer.cancel();
        timer.purge();
        timer=null;
        super.onDestroyView();
    }

    public void update_stats() {
        if (getView()==null)
            return;
        update_stats(getView());
        update_time_to_study(getView());
    }

    @SuppressLint({"NewApi"})
    public void update_stats(final View v) {
        if (v == null)
            return;

        AsyncTask<Void,Void,Void> stats_updater = new AsyncTask<Void,Void,Void>() {
            int num_chars;
            int num_words;
            int expired;

            @Override
            protected Void doInBackground(Void... voids) {
                if (!myDB.isOpen()) {
                    cancel(true);
                }
                num_chars = myDB.getNumChars();
                num_words = myDB.getNumWords();
                expired = myDB.getNumExpired();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                final ProgressGraph graph = v.findViewById(R.id.graph);
                if (graph == null)
                    return;
                graph.update_graph(true);

                TextView tv;

                tv = v.findViewById(R.id.stats_chars);
                tv.setText(""+num_chars);

                tv = v.findViewById(R.id.stats_words);
                tv.setText(""+num_words);

                tv = v.findViewById(R.id.stats_to_study);
                tv.setText(""+expired);
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            stats_updater.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        else
            stats_updater.execute((Void[])null);
    }

    private void update_buttons(View v) {
        Button zoomin= v.findViewById(R.id.graph_in);
        Button zoomout= v.findViewById(R.id.graph_out);
        ProgressGraph graph = v.findViewById(R.id.graph);
        zoomin.setEnabled(graph.can_zoom_in());
        zoomout.setEnabled(graph.can_zoom_out());
    }

    private void update_time_to_study(final View v) {
        if (!myDB.isOpen()) {
            return;
        }
        int expired = myDB.getNumExpired();
        long delay = myDB.getMostAdvancedExpiringTime();
        if (DEBUG) Log.d(TAG, "delay is " + delay);
        TableRow row = v.findViewById(R.id.db_stats_time_to_study_row);
        if ((expired == 0) && (delay >0)) {
            long update_time;
            StringBuilder time_to_display = new StringBuilder();
            row.setVisibility(View.VISIBLE);
            if (delay / (1000*60*60*24) >= 2) {
                time_to_display.append(delay / (1000*60*60*24)).append(" days");
                update_time = 1000*60*60*24;
            } else if (delay / (1000*60*60*24) >= 1) {
                long hours = (delay / (1000*60*60)) - 24;
                time_to_display.append("1 day ").append(hours).append(" hours");
                update_time = 1000*60*60;
            } else if (delay / (1000*60*60) >= 2) {
                time_to_display.append(delay / (1000*60*60)).append(" hours");
                update_time = 1000*60*60;
            } else if (delay / (1000*60*60) >= 1) {
                long minutes = (delay / (1000*60)) - 60;
                time_to_display.append("1 hour ").append(minutes).append(" minutes");
                update_time = 1000*60;
            } else if (delay / (1000*60) >= 2) {
                time_to_display.append(delay / (1000*60)).append(" minutes");
                update_time = 1000*60;
            } else if (delay / (1000*60) >= 1) {
                long seconds = (delay / 1000) - 60;
                time_to_display.append("1 minute ").append(seconds).append(" seconds");
                update_time = 1000;
            } else {
                time_to_display.append(delay / 1000).append(" seconds");
                update_time = 1000;
            }
            TextView time_field = v.findViewById(R.id.stats_time_to_study);
            time_field.setText(time_to_display.toString());

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    Activity a = getActivity();
                    if (a!=null)
                    a.runOnUiThread(new Runnable() {
                        public void run() {
                            update_time_to_study(v);
                        }
                    });
                }
            };

            if (timer != null)
                timer.schedule(task, update_time);
        } else {
            row.setVisibility(View.GONE);
            update_stats(v);
            if (expired>0) {
                TestActivity a = (TestActivity) getActivity();
                if (a!=null)
                    a.test_done(false);
            }
        }

    }
}
