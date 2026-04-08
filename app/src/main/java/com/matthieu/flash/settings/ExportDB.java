/******************************
 * Activity to export the database to a file
 */
package com.matthieu.flash.settings;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.*;
import android.widget.Toast;

import com.matthieu.flash.AddVocabularyActivity;
import com.matthieu.flash.AndZiApplication;
import com.matthieu.flash.HanziDB;
import com.matthieu.flash.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;

public class ExportDB extends PreferenceActivity {
    String file_path= Environment.getExternalStorageDirectory().getPath()+"/AndZiDB-"+ (1+Calendar.getInstance().get(Calendar.MONTH))+"-"+Calendar.getInstance().get(Calendar.DAY_OF_MONTH)+".azdb";
    ArrayList<String> lesson_selection;
    HanziDB.export_task running_task;
        
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ExportDB.this);
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("selected_lessons", false);
        editor.commit();

        addPreferencesFromResource(R.xml.settings_export_db);
        EditTextPreference path = (EditTextPreference) findPreference("export_path");
        path.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object o) {
                String current_text = (String) o;
                preference.setSummary(getString(R.string.settings_export_destination,current_text));
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ExportDB.this);
                SharedPreferences.Editor edit = sp.edit();
                edit.putString("export_path", current_text);
                edit.commit();
                return true;
            }
        });
        path.setText(file_path);
        path.setSummary(getString(R.string.settings_export_destination,file_path));

        Preference select_lesson = findPreference("selected_lessons");
        select_lesson.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                boolean select = sp.getBoolean("selected_lessons", false);
                if (!select) { // this is called before it is actually called, let's go select some lessons
                    Intent i = new Intent(ExportDB.this, AddVocabularyActivity.class);
                    i.setAction(Intent.ACTION_PICK);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivityForResult(i, 0);
                }
                return true;
            }
        });

        Preference export = findPreference("do_export");
        export.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            private void do_export(final String destination, final boolean include_scores, final boolean selected_lessons) {
                File export_db = new File(destination);
                try {
                    if (!export_db.createNewFile()) {
                        Toast.makeText(ExportDB.this, R.string.settings_error_create_file, Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (IOException e) {
                    Toast.makeText(ExportDB.this, R.string.settings_error_create_file, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (((AndZiApplication) getApplication()).check_exporting_space(destination)) {
                    ProgressDialog dialog = new ProgressDialog(ExportDB.this);
                    running_task = ((AndZiApplication) getApplication()).getMyDB().create_export_task(destination, include_scores, selected_lessons?lesson_selection:null, dialog, ExportDB.this, new Runnable() {
                        public void run() {
                            Toast.makeText(ExportDB.this, getString(R.string.settings_export_success, destination), Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    Toast.makeText(ExportDB.this, R.string.settings_error_not_enough_space_export, Toast.LENGTH_SHORT).show();
                }
            }

            public boolean onPreferenceClick(Preference preference) {
                final boolean include_scores = sp.getBoolean("include_scores", false);
                final boolean selected_lessons = sp.getBoolean("selected_lessons", false);
                final String destination = sp.getString("export_path", file_path);

                if (new File(destination).exists()) {
                    AlertDialog.Builder alert = new AlertDialog.Builder(ExportDB.this);
                    alert.setMessage(getString(R.string.settings_export_check_overwrite, destination));
                    alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            dialog.dismiss();
                        }
                    });
                    alert.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            new File(destination).delete();
                            do_export(destination, include_scores, selected_lessons);
                            dialog.dismiss();
                        }
                    });
                    alert.create();
                    alert.show();
                }
                else {
                    do_export(destination, include_scores, selected_lessons);
                }

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
                final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ExportDB.this);
                SharedPreferences.Editor editor = sp.edit();
                editor.putBoolean("selected_lessons", false);
                editor.commit();

                CheckBoxPreference select_lesson = (CheckBoxPreference) findPreference("selected_lessons");
                select_lesson.setChecked(false);
            }
        }
    }
}
