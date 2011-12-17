package com.matthieu;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.matthieu.widget.StatsBars;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

public class DBActivity extends Activity {
    protected HanziDB myDB=null;
    Timer timer;

    private final String TAG="Stats";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        setContentView(R.layout.db);

        if (!myDB.checkDatabase()) {
            Toast toast = Toast.makeText(DBActivity.this, "Database is corrupted... FIXME", 10);
            toast.show();
        }

        update_content();

        Intent intent = getIntent();
        if ((intent.getScheme() != null) && (intent.getScheme().equals("file"))) {
            Log.d(TAG, "Want to include database "+intent.getDataString());
        }

        timer = new Timer();
        long two_minutes_delay = 2*60*1000;
        timer.schedule(new updater(), two_minutes_delay, two_minutes_delay);
    }

    @Override
    protected void onResume() {
        super.onResume();
        update_content();
    }

    private void update_content() {
        TextView tv;
        StatsBars bar;
        int max_num;



        int num_in_box1 = myDB.getNumCharsInBox(0);
        max_num=num_in_box1;
        int num_in_box2 = myDB.getNumCharsInBox(1);
        max_num= num_in_box2>max_num?num_in_box2:max_num;
        int num_expired_in_box2 = myDB.getNumCharsExpiredInBox(1);
        int num_in_box3 = myDB.getNumCharsInBox(2);
        max_num= num_in_box3>max_num?num_in_box3:max_num;
        int num_expired_in_box3 = myDB.getNumCharsExpiredInBox(2);
        int num_in_box4 = myDB.getNumCharsInBox(3);
        max_num= num_in_box4>max_num?num_in_box4:max_num;
        int num_expired_in_box4 = myDB.getNumCharsExpiredInBox(3);
        int num_in_box5 = myDB.getNumCharsInBox(4);
        max_num= num_in_box5>max_num?num_in_box5:max_num;
        int num_expired_in_box5 = myDB.getNumCharsExpiredInBox(4);

        int num_chars = myDB.getNumChars();
        tv = (TextView) findViewById(R.id.stats_chars);
        tv.setText(""+num_chars+"("+(num_in_box1+num_expired_in_box2+num_expired_in_box3+num_expired_in_box4+num_expired_in_box5)+")");

        int num_words = myDB.getNumWords();
        tv = (TextView) findViewById(R.id.stats_words);
        tv.setText(""+num_words);

        int max_to_display = (num_in_box1 + num_in_box2 + num_in_box3 + num_in_box4 + num_in_box5 + max_num)/2;

        if (max_to_display == 0) {
            bar = (StatsBars) findViewById(R.id.stats_box1graph);
            bar.set_expired(0);

            bar = (StatsBars) findViewById(R.id.stats_box2graph);
            bar.set_expired(0);
            bar.set_good(0);

            bar = (StatsBars) findViewById(R.id.stats_box3graph);
            bar.set_expired(0);
            bar.set_good(0);

            bar = (StatsBars) findViewById(R.id.stats_box4graph);
            bar.set_expired(0);
            bar.set_good(0);

            bar = (StatsBars) findViewById(R.id.stats_box5graph);
            bar.set_expired(0);
            bar.set_good(0);
            return;
        }

        //Log.d(TAG, "stats : "+num_in_box1+",("+num_expired_in_box2+","+num_in_box2+"),("+num_expired_in_box3+","+num_in_box3+"),("+
        //        num_expired_in_box4+","+num_in_box4+"),("+num_expired_in_box5+","+num_in_box5+")");

        bar = (StatsBars) findViewById(R.id.stats_box1graph);
        bar.set_max(max_to_display);
        bar.set_expired(num_in_box1);
        if (num_in_box1>0) bar.set_expired_label(""+num_in_box1);
        bar.invalidate();

        bar = (StatsBars) findViewById(R.id.stats_box2graph);
        bar.set_max(max_to_display);
        bar.set_expired(num_expired_in_box2);
        if (num_expired_in_box2>0) bar.set_expired_label(""+num_expired_in_box2);
        bar.set_good(num_in_box2-num_expired_in_box2);
        if (num_in_box2-num_expired_in_box2>0) bar.set_good_label(""+(num_in_box2-num_expired_in_box2));
        bar.invalidate();

        bar = (StatsBars) findViewById(R.id.stats_box3graph);
        bar.set_max(max_to_display);
        bar.set_expired(num_expired_in_box3);
        if (num_expired_in_box3>0) bar.set_expired_label(""+num_expired_in_box3);
        bar.set_good(num_in_box3-num_expired_in_box3);
        if (num_in_box3-num_expired_in_box3>0) bar.set_good_label(""+(num_in_box3-num_expired_in_box3));
        bar.invalidate();

        bar = (StatsBars) findViewById(R.id.stats_box4graph);
        bar.set_max(max_to_display);
        bar.set_expired(num_expired_in_box4);
        if (num_expired_in_box4>0) bar.set_expired_label(""+num_expired_in_box4);
        bar.set_good(num_in_box4-num_expired_in_box4);
        if (num_in_box4-num_expired_in_box4>0) bar.set_good_label(""+(num_in_box4-num_expired_in_box4));
        bar.invalidate();

        bar = (StatsBars) findViewById(R.id.stats_box5graph);
        bar.set_max(max_to_display);
        bar.set_expired(num_expired_in_box5);
        if (num_expired_in_box5>0) bar.set_expired_label(""+num_expired_in_box5);
        bar.set_good(num_in_box5-num_expired_in_box5);
        if (num_in_box5-num_expired_in_box5>0) bar.set_good_label(""+(num_in_box5-num_expired_in_box5));
        bar.invalidate();
    }

    private class updater extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                public void run() {
                    update_content();
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.db_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.add_more_study:
                myDB.getMoreStudy(5);
                update_content();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }


}
