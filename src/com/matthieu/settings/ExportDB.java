package com.matthieu.settings;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Toast;
import com.matthieu.AndZiApplication;
import com.matthieu.HanziDB;
import com.matthieu.R;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

public class ExportDB extends PreferenceActivity {
    protected HanziDB myDB=null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        addPreferencesFromResource(R.xml.settings_export_db);
        String file_path= Environment.getExternalStorageDirectory().getPath()+"/AndZiDB-"+ Calendar.getInstance().get(Calendar.MONTH)+"-"+Calendar.getInstance().get(Calendar.DAY_OF_MONTH)+".azdb";

        EditTextPreference path = (EditTextPreference) findPreference("export_path");
        path.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object o) {
                String current_text = (String) o;
                preference.setSummary("Destination: "+current_text);
                return true;
            }
        });
        path.setText(file_path);
        path.setSummary("Destination: "+file_path);
        Preference export = findPreference("do_export");
        export.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ExportDB.this);
                boolean include_scores = sp.getBoolean("include_scores", false);
                boolean only_filtered = sp.getBoolean("only_filtered", false);
                String destination = sp.getString("export_path", "");

                File export_db = new File(destination);
                try {
                    if (!export_db.createNewFile()) {
                        Toast toast = Toast.makeText(ExportDB.this, "There was an error creating the file", 5);
                        toast.show();
                        return true;
                    }
                } catch (IOException e) {
                    Toast toast = Toast.makeText(ExportDB.this, "There was an error creating the file", 5);
                    toast.show();
                    return true;
                }

                database_exporter exporter = new database_exporter();
                exporter.include_scores(include_scores);
                exporter.filtered_lessons(only_filtered);
                exporter.execute(export_db);

                return true;
            }
        });
    }

    private class database_exporter extends AsyncTask<File,Void,Void> {
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
                Toast toast = Toast.makeText(ExportDB.this, "Database was copied to "+export_db.getPath(), 5);
                toast.show();
            }
            else {
                Toast toast = Toast.makeText(ExportDB.this, "Not enough space on device to export database", 5);
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
            pd = ProgressDialog.show(ExportDB.this, "Exporting Database", "Copying...", false);
        }
    }
}
