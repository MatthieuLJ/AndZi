package com.matthieu;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import com.matthieu.widget.StatsBars;

import java.io.File;
import java.io.IOException;

public class DBActivity extends Activity{
    protected HanziDB myDB=null;

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
        myDB.exportDB(export_db.getPath());

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("application/sqlite");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "AndZi database shared");
        sendIntent.putExtra(Intent.EXTRA_TEXT, "File attached.");
        sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + export_db.getPath()));
        startActivity(Intent.createChooser(sendIntent, "Email:"));
    }

    private void copy_database() {
        File export_db;
        try {
            export_db = File.createTempFile("AndZi", ".azdb", Environment.getExternalStorageDirectory());
        } catch (IOException e) {
            Log.d(TAG, "Could not create file");
            e.printStackTrace();
            return;
        }
        myDB.exportDB(export_db.getPath());

        Toast toast = Toast.makeText(this, "Database was copied to "+export_db.getPath(), 5);
        toast.show();
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
