/******************************
 * Simple activity, mostly with some stats and the ProgressGraph
 */
package com.matthieu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.*;
import android.widget.Button;
import android.widget.TextView;
import com.matthieu.widget.ProgressGraph;

import java.util.*;

public class DBActivity extends Activity {
    protected HanziDB myDB=null;
    Timer timer;

    private final String TAG="Stats";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        setContentView(R.layout.db);

        TextView tv;
        int num_chars = myDB.getNumChars();
        tv = (TextView) findViewById(R.id.stats_chars);
        tv.setText(""+num_chars);

        int num_words = myDB.getNumWords();
        tv = (TextView) findViewById(R.id.stats_words);
        tv.setText(""+num_words);

        int expired = myDB.getNumCharsExpiredInBoxOrAbove(0);
        tv = (TextView) findViewById(R.id.stats_to_study);
        tv.setText(""+expired);

        final ProgressGraph graph = (ProgressGraph) findViewById(R.id.graph);
        graph.set_data(((AndZiApplication)getApplication()).getProgress());

        Button zoomin= (Button) findViewById(R.id.graph_in);
        zoomin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                graph.zoom_in();
                update_buttons();
            }
        });
        Button zoomout= (Button) findViewById(R.id.graph_out);
        zoomout.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                graph.zoom_out();
                update_buttons();
            }
        });

        update_buttons();

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                update_graph();
                DBActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        graph.invalidate();
                    }
                });
            }
        }, 10*60*1000, 10*60*1000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        update_graph();
    }

    private void update_graph() {
        final ProgressGraph graph = (ProgressGraph) findViewById(R.id.graph);
        graph.update_graph();
    }

    private void update_buttons() {
        Button zoomin= (Button) findViewById(R.id.graph_in);
        Button zoomout= (Button) findViewById(R.id.graph_out);
        ProgressGraph graph = (ProgressGraph) findViewById(R.id.graph);
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
