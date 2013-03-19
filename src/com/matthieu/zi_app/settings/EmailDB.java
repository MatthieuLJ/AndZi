/******************************
 * Activity to Email the database
 */
package com.matthieu.zi_app.settings;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import com.matthieu.zi_app.AndZiApplication;
import com.matthieu.zi_app.HanziDB;
import com.matthieu.zi_app.R;
import com.matthieu.zi_app.VocabularyEditorActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class EmailDB extends PreferenceActivity {
    ArrayList<String> lesson_selection;
    HanziDB.export_task running_task;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(EmailDB.this);
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("selected_lessons", false);
        editor.commit();

        addPreferencesFromResource(R.xml.settings_email_db);

        Preference select_lesson = findPreference("selected_lessons");
        select_lesson.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                boolean select = sp.getBoolean("selected_lessons", false);
                if (!select) { // this is called before it is actually called, let's go select some lessons
                    Intent i = new Intent(EmailDB.this, VocabularyEditorActivity.class);
                    i.setAction(Intent.ACTION_PICK);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivityForResult(i, 0);
                }
                return true;
            }
        });

        Preference email = findPreference("do_email");
        email.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                boolean include_scores = sp.getBoolean("include_scores", false);
                boolean selected_lessons = sp.getBoolean("selected_lessons", false);
                final File export_db;
                try {
                    export_db = File.createTempFile("AndZi", ".azdb");
                } catch (IOException e) {
                    e.printStackTrace();
                    return true;
                }

                ProgressDialog dialog = new ProgressDialog(EmailDB.this);
                running_task = ((AndZiApplication) getApplication()).getMyDB().create_export_task(export_db.getPath(),
                        include_scores,selected_lessons?lesson_selection:null,dialog, EmailDB.this, new Runnable() {
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

        Object saved = getLastNonConfigurationInstance();
        if (saved instanceof HanziDB.export_task) {
            running_task = (HanziDB.export_task) saved;
            ProgressDialog dialog = new ProgressDialog(this);
            running_task.change_context(this);
            running_task.change_dialog(dialog);
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        if ((running_task != null) && (running_task.getStatus()!= AsyncTask.Status.FINISHED) && (!running_task.isCancelled()))
            return running_task;
        else
            return super.onRetainNonConfigurationInstance();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode==0) {
            if (resultCode == RESULT_OK) {
                lesson_selection = data.getExtras().getStringArrayList("lesson_list");
            } else {
                final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(EmailDB.this);
                SharedPreferences.Editor editor = sp.edit();
                editor.putBoolean("selected_lessons", false);
                editor.commit();

                CheckBoxPreference select_lesson = (CheckBoxPreference) findPreference("selected_lessons");
                select_lesson.setChecked(false);
            }
        }
    }
}
