package com.matthieu.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
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

                myDB.create_export_task(export_db.getPath(),include_scores,only_filtered,true, EmailDB.this, new Runnable() {
                    public void run() {
                        Intent sendIntent = new Intent(Intent.ACTION_SEND);
                        sendIntent.setType("application/sqlite");
                        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "AndZi database shared");
                        sendIntent.putExtra(Intent.EXTRA_TEXT, "File attached.");
                        sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + export_db.getPath()));
                        startActivity(Intent.createChooser(sendIntent, "Email:"));
                    }
                });
                return true;
            }
        });
    }
}
