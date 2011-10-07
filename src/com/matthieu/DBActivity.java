package com.matthieu;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import com.matthieu.widget.StatsBars;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class DBActivity extends Activity {
    protected HanziDB myDB=null;
    Timer timer;


    private final String TAG="Stats";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).myDB;
        setContentView(R.layout.db);

        update_content();

        Intent intent = getIntent();
        if ((intent.getScheme() != null) && (intent.getScheme().equals("file"))) {
            Log.d(TAG, "Want to include database "+intent.getDataString());
        }

        timer = new Timer();
        long two_minutes_delay = 2*60*1000;
        timer.schedule(new updater(), two_minutes_delay, two_minutes_delay);
    }

    private void update_content() {
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

        Log.d(TAG, "stats : "+num_in_box1+",("+num_expired_in_box2+","+num_in_box2+"),("+num_expired_in_box3+","+num_in_box3+"),("+
                num_expired_in_box4+","+num_in_box4+"),("+num_expired_in_box5+","+num_in_box5+")");

        bar = (StatsBars) findViewById(R.id.stats_box1graph);
        bar.set_expired((num_in_box1*100)/max_to_display);
        bar.invalidate();

        bar = (StatsBars) findViewById(R.id.stats_box2graph);
        bar.set_expired((num_expired_in_box2*100)/max_to_display);
        bar.set_good(((num_in_box2-num_expired_in_box2)*100)/max_to_display);
        bar.invalidate();

        bar = (StatsBars) findViewById(R.id.stats_box3graph);
        bar.set_expired((num_expired_in_box3*100)/max_to_display);
        bar.set_good(((num_in_box3-num_expired_in_box3)*100)/max_to_display);
        bar.invalidate();

        bar = (StatsBars) findViewById(R.id.stats_box4graph);
        bar.set_expired((num_expired_in_box4*100)/max_to_display);
        bar.set_good(((num_in_box4-num_expired_in_box4)*100)/max_to_display);
        bar.invalidate();

        bar = (StatsBars) findViewById(R.id.stats_box5graph);
        bar.set_expired((num_expired_in_box5*100)/max_to_display);
        bar.set_good(((num_in_box5-num_expired_in_box5)*100)/max_to_display);
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
            case R.id.stats_email_db:
                email_database();
                break;
            case R.id.stats_copy_db:
                copy_database();
                break;
            case R.id.clear_db:
                clear_database();
                break;
            case R.id.import_db:
                import_database();
                break;
            case R.id.reset_db:
                reset_database();
                break;
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

    private void email_database() {
        File export_db;
        try {
            export_db = File.createTempFile("AndZi", ".azdb");
        } catch (IOException e) {
            Log.d(TAG, "Could not create file");
            e.printStackTrace();
            return;
        }

        new database_emailer().execute(export_db);
    }

    private class database_emailer extends AsyncTask<File,Void,Void> {
        File export_db;
        ProgressDialog pd;

        @Override
        protected void onPostExecute(Void aVoid) {
            pd.dismiss();
            
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("application/sqlite");
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "AndZi database shared");
            sendIntent.putExtra(Intent.EXTRA_TEXT, "File attached.");
            sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + export_db.getPath()));
            startActivity(Intent.createChooser(sendIntent, "Email:"));
        }

        @Override
        protected Void doInBackground(File... files) {
            export_db=files[0];
            myDB.exportDB(export_db.getPath());

            return null;
        }

        @Override
        protected void onPreExecute() {
            pd = ProgressDialog.show(DBActivity.this, "Emailing Database", "Copying...", false);
        }
    }

    private void copy_database() {
        File export_db;
        try {
            export_db = File.createTempFile("AndZi", ".azdb", Environment.getExternalStorageDirectory());
        } catch (IOException e) {
            Log.d(TAG, "Could not create file");
            e.printStackTrace();
            Toast toast = Toast.makeText(DBActivity.this, "There was an error copying the database", 5);
            toast.show();
            return;
        }

        new database_exporter().execute(export_db);
    }

    private class database_exporter extends AsyncTask<File,Void,Void> {
        File export_db;
        ProgressDialog pd;

        @Override
        protected void onPostExecute(Void aVoid) {
            pd.dismiss();

            Toast toast = Toast.makeText(DBActivity.this, "Database was copied to "+export_db.getPath(), 5);
            toast.show();
        }

        @Override
        protected Void doInBackground(File... files) {
            export_db=files[0];
            myDB.exportDB(export_db.getPath());

            return null;
        }

        @Override
        protected void onPreExecute() {
            pd = ProgressDialog.show(DBActivity.this, "Exporting Database", "Copying...", false);
        }
    }

    private void clear_database() {
        myDB.clearDB();
        update_content();
    }

    private void import_database() {
        Intent intent = new Intent(this, com.matthieu.widget.file_dialog.FileDialog.class);
        intent.putExtra(com.matthieu.widget.file_dialog.FileDialog.START_PATH, "/sdcard");
        startActivityForResult(intent, 0);
    }

    private void reset_database() {
        ArrayList<String> hanzis = myDB.getAllChar();
        for (String han : hanzis) {
            myDB.restartStudy(han);
        }
        update_content();
    }

    private void download_ref_database() {
        final String params[]= {/*"http://db.tt/RdRHvoJa"*/ "http://172.16.21.2/cedict_db"};
        downloader down = new downloader();
        down.execute(params);
    }

    private class downloader extends AsyncTask<String,Integer,Void> {
        ProgressDialog mProgressDialog;
        String PATH, URL;
        private final String TAG="downloader";

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
            mProgressDialog.setMessage("Installing...");
            ((AndZiApplication) getApplication()).installRefDB(PATH);
            Log.d(TAG, "installed");
            mProgressDialog.dismiss();
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
                URLConnection conexion = url.openConnection();
                conexion.connect();
                int lenghtOfFile = conexion.getContentLength();
                long startTime = System.currentTimeMillis();

                Log.d(TAG, "download begining");
                Log.d(TAG, "download url:" + url);
                Log.d(TAG, "downloaded file name:" + fileName);
                Log.d(TAG, "file size:" + lenghtOfFile);

                // downlod the file
                InputStream input = new BufferedInputStream(url.openStream());
                OutputStream output = new FileOutputStream(fileName);

                byte data[] = new byte[1024];

                long total = 0;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    // publishing the progress....
                    publishProgress((int)(total*100/lenghtOfFile));
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();

                Log.d(TAG, "download ready in"
                                        + ((System.currentTimeMillis() - startTime) / 1000)
                                        + " sec");
            } catch (IOException e) {
                Log.d(TAG, "Error: " + e);
            }

        }
    }

    private void delete_ref_database() {
        ((AndZiApplication) getApplication()).eraseRefDB();
    }
    public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {
        if (resultCode == Activity.RESULT_OK) {

            if (requestCode != 0) {
                return;
            }

            String filePath = data.getStringExtra(com.matthieu.widget.file_dialog.FileDialog.RESULT_PATH);
            myDB.mergeDB(filePath);
            update_content();

        } else if (resultCode == Activity.RESULT_CANCELED) {
            return;
        }
    }
}
