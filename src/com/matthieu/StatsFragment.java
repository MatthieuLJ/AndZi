/******************************
 * Simple activity, mostly with some stats and the ProgressGraph
 */
package com.matthieu;

import android.app.Activity;
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

        TextView tv;
        int num_chars = myDB.getNumChars();
        tv = (TextView) res.findViewById(R.id.stats_chars);
        tv.setText(""+num_chars);

        int num_words = myDB.getNumWords();
        tv = (TextView) res.findViewById(R.id.stats_words);
        tv.setText(""+num_words);

        int expired = myDB.getNumCharsExpiredInBoxOrAbove(0);
        tv = (TextView) res.findViewById(R.id.stats_to_study);
        tv.setText(""+expired);

        int expired_words = myDB.getNumWordsExpiredInBoxOrAbove(0);
        tv = (TextView) res.findViewById(R.id.stats_to_study_words);
        tv.setText(""+expired_words);

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

        update_buttons(res);

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                update_graph();
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        graph.invalidate();
                    }
                });
            }
        }, 10*60*1000, 10*60*1000);

        return res;
    }

    public void update_graph() {
        final ProgressGraph graph = (ProgressGraph) getView().findViewById(R.id.graph);
        graph.update_graph();
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
