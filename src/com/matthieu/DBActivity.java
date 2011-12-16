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

        int num_chars = myDB.getNumChars();
        tv = (TextView) findViewById(R.id.stats_chars);
        tv.setText(""+num_chars);

        int num_words = myDB.getNumWords();
        tv = (TextView) findViewById(R.id.stats_words);
        tv.setText(""+num_words);

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
            case R.id.download_ref_db:
                download_ref_database();
                break;
            case R.id.delete_ref_db:
                delete_ref_database();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }


    private void download_ref_database() {
        final String params[]= {"http://db.tt/RdRHvoJa" /*"http://172.16.21.2/cedict_db"*/};
        ref_downloader down = new ref_downloader();
        down.execute(params);
    }

    private class ref_downloader extends AsyncTask<String,Integer,Void> {
        ProgressDialog mProgressDialog;
        String PATH, URL;
        boolean enough_space;
        private final String TAG="ref_downloader";

        @Override
        protected Void doInBackground(String... strings) {
            try {
                PATH=File.createTempFile("ref", "db").getPath();
            } catch (IOException e) {
                e.printStackTrace();
                //PATH=DBActivity.getApplicationContext().getFilesDir()+"/cedict_ref/";
            }
            URL=strings[0];



            DownloadFromUrl(URL, PATH);
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            
            mProgressDialog = new ProgressDialog(DBActivity.this);
            mProgressDialog.setMessage("Downloading reference database");
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setMax(100);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.show();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (enough_space) {
                mProgressDialog.setMessage("Installing...");
                ((AndZiApplication) getApplication()).installRefDB(PATH, true);
                Log.d(TAG, "installed");
            }
            mProgressDialog.dismiss();
            if (!enough_space) {
                Toast toast = Toast.makeText(DBActivity.this, "Not enough space on device to download and isntall reference database", 5);
                toast.show();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            mProgressDialog.setProgress(values[0]);
        }

        private void DownloadFromUrl(String download_URL, String fileName) {
            int count;
            try {
                URL url = new URL(download_URL);
                URLConnection connection = url.openConnection();
                connection.connect();
                int lengthOfFile = connection.getContentLength();
                long startTime = System.currentTimeMillis();

                Log.d(TAG, "download beginning");
                Log.d(TAG, "download url:" + url);
                Log.d(TAG, "downloaded file name:" + fileName);
                Log.d(TAG, "file size:" + lengthOfFile);

                enough_space = ((AndZiApplication) getApplication()).check_ref_download_space(PATH, lengthOfFile);
                if (!enough_space) {
                    return;
                }

                // download the file
                InputStream input = new BufferedInputStream(url.openStream());
                OutputStream output = new FileOutputStream(fileName);

                byte data[] = new byte[1024];

                long total = 0;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    // publishing the progress....
                    publishProgress((int)(total*100/lengthOfFile));
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();

                Log.d(TAG, "download ready after "
                                        + ((System.currentTimeMillis() - startTime) / 1000)
                                        + " sec");
            } catch (IOException e) {
                Log.d(TAG, "Error: " + e);
                enough_space = false;
            }

        }
    }

    private void delete_ref_database() {
        ((AndZiApplication) getApplication()).eraseRefDB();
    }
}
