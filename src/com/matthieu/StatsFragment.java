/******************************
 * Simple activity, mostly with some stats and the ProgressGraph
 */
package com.matthieu;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.*;
import android.widget.Button;
import android.widget.TextView;
import com.matthieu.widget.ProgressGraph;

import java.util.*;

public class StatsFragment extends Fragment {
    protected HanziDB myDB=null;
    Timer timer;

    private final String TAG="Stats";

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        myDB = ((AndZiApplication) activity.getApplication()).getMyDB();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View res = inflater.inflate(R.layout.db, container, false);

        final ProgressGraph graph = (ProgressGraph) res.findViewById(R.id.graph);
        graph.set_data(((AndZiApplication) getActivity().getApplication()).getProgress());

        Button zoomin= (Button) res.findViewById(R.id.graph_in);
        zoomin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                graph.zoom_in();
                update_buttons(getView());
            }
        });
        Button zoomout= (Button) res.findViewById(R.id.graph_out);
        zoomout.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                graph.zoom_out();
                update_buttons(getView());
            }
        });

        update_stats(res);
        update_buttons(res);

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

        return res;
    }

    @Override
    public void onDestroyView() {
        timer.cancel();
        super.onDestroyView();
    }

    public void update_stats() {
        update_stats(getView());
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
                num_chars = myDB.getNumChars();
                num_words = myDB.getNumWords();
                expired = myDB.getNumExpired();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                final ProgressGraph graph = (ProgressGraph) v.findViewById(R.id.graph);
                if (graph == null)
                    return;
                graph.update_graph(true);

                TextView tv;

                tv = (TextView) v.findViewById(R.id.stats_chars);
                tv.setText(""+num_chars);

                tv = (TextView) v.findViewById(R.id.stats_words);
                tv.setText(""+num_words);

                tv = (TextView) v.findViewById(R.id.stats_to_study);
                tv.setText(""+expired);
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            stats_updater.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        else
            stats_updater.execute((Void[])null);
    }

    private void update_buttons(View v) {
        Button zoomin= (Button) v.findViewById(R.id.graph_in);
        Button zoomout= (Button) v.findViewById(R.id.graph_out);
        ProgressGraph graph = (ProgressGraph) v.findViewById(R.id.graph);
        if (graph.can_zoom_in()) {
            zoomin.setEnabled(true);
        }
        else {
            zoomin.setEnabled(false);
        }
        if (graph.can_zoom_out()) {
            zoomout.setEnabled(true);
        }
        else {
            zoomout.setEnabled(false);
        }
    }
}
