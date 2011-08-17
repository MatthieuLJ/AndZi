package com.matthieu;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import com.matthieu.widget.StatsBars;

public class StatsActivity extends Activity{
    protected HanziDB myDB=null;

    private final String TAG="Stats";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).myDB;
        setContentView(R.layout.stats);

        TextView tv;
        StatsBars bar;

        int num_chars = myDB.getNumChars();
        tv = (TextView) findViewById(R.id.stats_chars);
        tv.setText(""+num_chars);

        int num_words = myDB.getNumWords();
        tv = (TextView) findViewById(R.id.stats_words);
        tv.setText(""+num_words);

        int num_in_box1 = myDB.getNumCharsInBox(0);
        int num_in_box2 = myDB.getNumCharsInBox(1);
        int num_expired_in_box2 = myDB.getNumCharsExpiredInBox(1);
        int num_in_box3 = myDB.getNumCharsInBox(2);
        int num_expired_in_box3 = myDB.getNumCharsExpiredInBox(2);
        int num_in_box4 = myDB.getNumCharsInBox(3);
        int num_expired_in_box4 = myDB.getNumCharsExpiredInBox(3);
        int num_in_box5 = myDB.getNumCharsInBox(4);
        int num_expired_in_box5 = myDB.getNumCharsExpiredInBox(4);

        /*
        int max_to_display=Math.max(num_in_box1,
                Math.max(num_in_box2 - num_expired_in_box2,
                Math.max(num_expired_in_box2,
                Math.max(num_in_box3 - num_expired_in_box3,
                Math.max(num_expired_in_box3,
                Math.max(num_in_box4 - num_expired_in_box4,
                Math.max(num_expired_in_box4,
                Math.max(num_in_box5 - num_expired_in_box5,
                num_expired_in_box5))))))));
                */
        int max_to_display = num_in_box1 + num_in_box2 + num_in_box3 + num_in_box4 + num_in_box5;


        bar = (StatsBars) findViewById(R.id.stats_box1graph);
        bar.set_expired((num_in_box1*100)/max_to_display);

        bar = (StatsBars) findViewById(R.id.stats_box2graph);
        bar.set_expired((num_expired_in_box2*100)/max_to_display);
        bar.set_good(((num_in_box2-num_expired_in_box2)*100)/max_to_display);

        bar = (StatsBars) findViewById(R.id.stats_box3graph);
        bar.set_expired((num_expired_in_box3*100)/max_to_display);
        bar.set_good(((num_in_box3-num_expired_in_box3)*100)/max_to_display);

        bar = (StatsBars) findViewById(R.id.stats_box4graph);
        bar.set_expired((num_expired_in_box4*100)/max_to_display);
        bar.set_good(((num_in_box4-num_expired_in_box4)*100)/max_to_display);

        bar = (StatsBars) findViewById(R.id.stats_box5graph);
        bar.set_expired((num_expired_in_box5*100)/max_to_display);
        bar.set_good(((num_in_box5-num_expired_in_box5)*100)/max_to_display);
    }
}
