package com.matthieu.settings;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Toast;
import com.matthieu.AndZiApplication;
import com.matthieu.HanziDB;
import com.matthieu.R;

import java.io.File;
import java.io.IOException;

public class EmailDB extends PreferenceActivity {
    protected HanziDB myDB=null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        addPreferencesFromResource(R.xml.settings_email_db);

        Preference email = findPreference("do_email");
        email.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(EmailDB.this);
                boolean include_scores = sp.getBoolean("include_scores", false);
                boolean only_filtered = sp.getBoolean("only_filtered", false);
                final File export_db;
                try {
                    export_db = File.createTempFile("AndZi", ".azdb");
                } catch (IOException e) {
                    e.printStackTrace();
                    return true;
                }
                database_emailer emailer = new database_emailer();
                emailer.include_scores(include_scores);
                emailer.filtered_lessons(only_filtered);
                emailer.execute(export_db);
                return true;
            }
        });
    }

    private class database_emailer extends AsyncTask<File,Void,Void> {
        File export_db;
        ProgressDialog pd;
        boolean with_scores, only_filtered_lessons;
        boolean enough_space;

        public void include_scores(boolean include) {
            with_scores=include;
        }
        public void filtered_lessons(boolean filter) {
            only_filtered_lessons=filter;
        }

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
                Toast toast = Toast.makeText(EmailDB.this, "Not enough space on device to email database", 5);
                toast.show();
            }
        }

        @Override
        protected Void doInBackground(File... files) {
            export_db=files[0];
            enough_space = ((AndZiApplication) getApplication()).check_exporting_space(export_db.getPath());

            if (enough_space) {
                if ((!only_filtered_lessons) && (with_scores)) {
                    ((AndZiApplication) getApplication()).copyMyDataBase(export_db.getPath());
                }
                else {
                    if (only_filtered_lessons) {
                        myDB.exportDB(export_db.getPath(), myDB.getLessonsToFilter(), with_scores);
                    }
                    else {
                        myDB.exportDB(export_db.getPath(), with_scores);
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPreExecute() {
            pd = ProgressDialog.show(EmailDB.this, "Emailing Database", "Copying...", false);
        }
    }

}
