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

        final ProgressGraph graph = (ProgressGraph) findViewById(R.id.graph);
        ArrayList<ProgressDB.ProgressData> to_display = ((AndZiApplication) getApplication()).getProgress().get_data();
        to_display.add(((AndZiApplication) getApplication()).getCurrentProgress());
        graph.set_data(to_display);

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

        if (!myDB.checkDatabase()) {
            AlertDialog.Builder alert = new AlertDialog.Builder(DBActivity.this);
            alert.setMessage("Found some problems in the database, would you like to fix them?");
            alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int i) {
                    myDB.checkDatabase(true);
                    dialog.dismiss();
                }
            });
            alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int i) {
                    dialog.dismiss();
                }
            });
            alert.create();
            alert.show();
        }

        update_buttons();
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
