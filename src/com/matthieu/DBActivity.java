package com.matthieu;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
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
        if (num_in_box1>0) bar.set_expired_label(""+num_in_box1);
        bar.invalidate();

        bar = (StatsBars) findViewById(R.id.stats_box2graph);
        bar.set_expired((num_expired_in_box2*100)/max_to_display);
        if (num_expired_in_box2>0) bar.set_expired_label(""+num_expired_in_box2);
        bar.set_good(((num_in_box2-num_expired_in_box2)*100)/max_to_display);
        if (num_in_box2-num_expired_in_box2>0) bar.set_good_label(""+(num_in_box2-num_expired_in_box2));
        bar.invalidate();

        bar = (StatsBars) findViewById(R.id.stats_box3graph);
        bar.set_expired((num_expired_in_box3*100)/max_to_display);
        if (num_expired_in_box3>0) bar.set_expired_label(""+num_expired_in_box3);
        bar.set_good(((num_in_box3-num_expired_in_box3)*100)/max_to_display);
        if (num_in_box3-num_expired_in_box3>0) bar.set_good_label(""+(num_in_box3-num_expired_in_box3));
        bar.invalidate();

        bar = (StatsBars) findViewById(R.id.stats_box4graph);
        bar.set_expired((num_expired_in_box4*100)/max_to_display);
        if (num_expired_in_box4>0) bar.set_expired_label(""+num_expired_in_box4);
        bar.set_good(((num_in_box4-num_expired_in_box4)*100)/max_to_display);
        if (num_in_box4-num_expired_in_box4>0) bar.set_good_label(""+(num_in_box4-num_expired_in_box4));
        bar.invalidate();

        bar = (StatsBars) findViewById(R.id.stats_box5graph);
        bar.set_expired((num_expired_in_box5*100)/max_to_display);
        if (num_expired_in_box5>0) bar.set_expired_label(""+num_expired_in_box5);
        bar.set_good(((num_in_box5-num_expired_in_box5)*100)/max_to_display);
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
            case R.id.email_db:
                email_database();
                break;
            case R.id.copy_db:
                copy_database();
                break;
            case R.id.clear_db:
                clear_database();
                break;
            case R.id.import_db:
                import_database();
                break;
            case R.id.replace_db:
                replace_database();
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

        export_db.delete();
    }

    private class database_emailer extends AsyncTask<File,Void,Void> {
        File export_db;
        ProgressDialog pd;
        boolean enough_space;

        @Override
        protected void onPostExecute(Void aVoid) {
            pd.dismiss();

            if (enough_space) {
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.setType("application/sqlite");
                sendIntent.putExtra(Intent.EXTRA_SUBJECT, "AndZi database shared");
                sendIntent.putExtra(Intent.EXTRA_TEXT, "File attached.");
                sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + export_db.getPath()));
                startActivity(Intent.createChooser(sendIntent, "Email:"));
            }
            else {
                Toast toast = Toast.makeText(DBActivity.this, "Not enough space on device to email database", 5);
                toast.show();
            }
        }

        @Override
        protected Void doInBackground(File... files) {
            export_db=files[0];
            enough_space = ((AndZiApplication) getApplication()).check_exporting_space(export_db.getPath());
            
            if (enough_space)
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
        boolean enough_space;

        @Override
        protected void onPostExecute(Void aVoid) {
            pd.dismiss();

            if (enough_space) {
                Toast toast = Toast.makeText(DBActivity.this, "Database was copied to "+export_db.getPath(), 5);
                toast.show();
            }
            else {
                Toast toast = Toast.makeText(DBActivity.this, "Not enough space on device to export database", 5);
                toast.show();
            }
        }

        @Override
        protected Void doInBackground(File... files) {
            export_db=files[0];
            enough_space = ((AndZiApplication) getApplication()).check_exporting_space(export_db.getPath());

            if (enough_space)
                myDB.exportDB(export_db.getPath());

            return null;
        }

        @Override
        protected void onPreExecute() {
            pd = ProgressDialog.show(DBActivity.this, "Exporting Database", "Copying...", false);
        }
    }

    private void clear_database() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setMessage("Are you sure you want to clear all your database?");
        alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                myDB.clearDB();
                update_content();
                dialog.dismiss();
            }
        });
        alert.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                dialog.dismiss();
            }
        });
        alert.create();
        alert.show();
    }

    private void import_database() {
        Intent intent = new Intent(this, com.matthieu.widget.file_dialog.FileDialog.class);
        intent.putExtra(com.matthieu.widget.file_dialog.FileDialog.START_PATH, "/sdcard");
        startActivityForResult(intent, 0);
    }

    private void replace_database() {
        Intent intent = new Intent(this, com.matthieu.widget.file_dialog.FileDialog.class);
        intent.putExtra(com.matthieu.widget.file_dialog.FileDialog.START_PATH, "/sdcard");
        startActivityForResult(intent, 1);
    }

    public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {
        // should come here after the activity startedin import_database completes
        if (resultCode == Activity.RESULT_OK) {

            if ((requestCode != 0) && (requestCode != 1)) {
                return;
            }
            String filePath = data.getStringExtra(com.matthieu.widget.file_dialog.FileDialog.RESULT_PATH);

            if (requestCode == 0) {
                merger merge_task = new merger();
                merge_task.execute(filePath);
            }
            else if (requestCode == 1) {
                ((AndZiApplication) getApplication()).replaceDataBase(filePath);
                update_content();
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            return;
        }
    }

    private class merger extends AsyncTask<String, Void, Void> {
        ProgressDialog mProgressDialog;
        boolean enough_space;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog = new ProgressDialog(DBActivity.this);
            mProgressDialog.setMessage("Merging database");
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setMax(100);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.show();
        }

        @Override
        protected Void doInBackground(String... strings) {
            enough_space = ((AndZiApplication) getApplication()).check_merging_space(strings[0]);
            if (enough_space)
                myDB.mergeDB(strings[0], mProgressDialog);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            mProgressDialog.dismiss();
            update_content();
            if (!enough_space) {
                Toast toast = Toast.makeText(DBActivity.this, "Not enough space on device to impoort database", 5);
                toast.show();
            }
        }
    }

    private void reset_database() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setMessage("Are you sure you want to restart the study for everything?");
        alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                ArrayList<String> hanzis = myDB.getAllChar();
                for (String han : hanzis) {
                    myDB.restartStudy(han);
                }
                update_content();
                dialog.dismiss();
            }
        });
        alert.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                dialog.dismiss();
            }
        });
        alert.create();
        alert.show();
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
