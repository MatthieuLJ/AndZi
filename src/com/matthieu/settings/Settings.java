/******************************
 * Main settings activity
 */
package com.matthieu.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import com.matthieu.AndZiApplication;
import com.matthieu.HanziDB;
import com.matthieu.R;

import java.io.*;
import java.net.*;
import java.util.Calendar;

public class Settings extends PreferenceActivity {

    private static final String dictionaryURL="http://bit.ly/AndZi-Cedict";

    int current_task_type=0; //0=nothing
                             //1=ref download
                             //2=merge
                             //3=clear
                             //4=reset (scores)

    AsyncTask current_task=null;

    @Override
    @SuppressLint({"NewApi"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (true)
            addPreferencesFromResource(R.xml.settings_main);
        else
            addPreferencesFromResource(R.xml.settings_main_extra);

        Preference reset_db = findPreference("ResetDB");
        reset_db.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                AlertDialog.Builder alert = new AlertDialog.Builder(Settings.this);
                alert.setMessage(R.string.settings_confirm_reset_scores);
                alert.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, int i) {
                        dialog.dismiss();
                        ProgressDialog p_dialog = new ProgressDialog(Settings.this);
                        current_task = new reset_score(p_dialog);
                        current_task_type=4;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                            current_task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Object[])null);
                        else
                            current_task.execute((Object[])null);
                    }
                });
                alert.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });
                alert.create();
                alert.show();
                return true;
            }
        });

        Preference clear_db = findPreference("ClearDB");
        clear_db.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                AlertDialog.Builder alert = new AlertDialog.Builder(Settings.this);
                alert.setMessage(R.string.settings_confirm_clear_db);
                alert.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        ProgressDialog p_dialog = new ProgressDialog(Settings.this);
                        current_task = ((AndZiApplication) getApplication()).getMyDB().create_clear_task(p_dialog, null);
                        current_task_type = 3;
                        dialog.dismiss();
                    }
                });
                alert.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });
                alert.create();
                alert.show();
                return true;
            }
        });

        Preference import_db = findPreference("ImportDB");
        import_db.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(Settings.this, com.matthieu.widget.file_dialog.FileDialog.class);
                intent.putExtra(com.matthieu.widget.file_dialog.FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());
                startActivityForResult(intent, 0);
                return true;
            }
        });

        Preference export_progress = findPreference("ExportProgress");
        if (export_progress != null) {
            export_progress.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    String dest = Environment.getExternalStorageDirectory().getPath()+"/progress-"+ (1+ Calendar.getInstance().get(Calendar.MONTH))+"-"+Calendar.getInstance().get(Calendar.DAY_OF_MONTH)+".azdb";
                    File export_db = new File(dest);
                    try {
                        if (!export_db.createNewFile()) {
                            Toast.makeText(Settings.this, R.string.settings_error_create_file, Toast.LENGTH_SHORT).show();
                            return true;
                        }
                    } catch (IOException e) {
                        Toast.makeText(Settings.this, R.string.settings_error_create_file, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    if (((AndZiApplication) getApplication()).check_exporting_progress_space(dest)) {
                        ((AndZiApplication) getApplication()).copyProgressDataBase(dest);
                        Toast.makeText(Settings.this, getString(R.string.settings_export_progress_success,dest), Toast.LENGTH_SHORT).show();

                    }
                    else {
                        Toast.makeText(Settings.this, R.string.settings_error_not_enough_space_export, Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
            });
        }

        Preference import_progress = findPreference("ImportProgress");
        if (import_progress != null) {
            import_progress.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(Settings.this, com.matthieu.widget.file_dialog.FileDialog.class);
                    intent.putExtra(com.matthieu.widget.file_dialog.FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());
                    startActivityForResult(intent, 1);
                    return true;
                }
            });
        }

        Object saved = getLastNonConfigurationInstance();
        if (saved instanceof to_save) {
            current_task = ((to_save) saved).current_task;
            current_task_type = ((to_save) saved).current_task_type;
            ProgressDialog dialog = new ProgressDialog(this);
            switch (current_task_type) {
                case 2:
                    ((HanziDB.merge_task) current_task).change_context(this);
                    ((HanziDB.merge_task) current_task).change_progress_dialog(dialog);
                    break;
                case 3:
                    ((HanziDB.clear_task) current_task).change_dialog(dialog);
                    break;
                case 4:
                    ((reset_score) current_task).change_dialog(dialog);
                    break;
            }
        }
    }

    private class to_save {
        int current_task_type;
        AsyncTask current_task;
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        if ((current_task_type!=0) && (current_task!=null) && (current_task.getStatus()!= AsyncTask.Status.FINISHED)
                && (!current_task.isCancelled())) {
            to_save saving = new to_save();
            saving.current_task = current_task;
            saving.current_task_type = current_task_type;
            return saving;
        }
        else {
            return super.onRetainNonConfigurationInstance();
        }
    }

    public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {
        // should come here after the activity startedin import_database completes
        if (resultCode == Activity.RESULT_OK) {

            if ((requestCode < 0) || (requestCode > 1)) {
                return;
            }
            final String filePath = data.getStringExtra(com.matthieu.widget.file_dialog.FileDialog.RESULT_PATH);

            if (requestCode == 0) {
                if (((AndZiApplication)getApplication()).check_merging_space(filePath)) {
                    ProgressDialog dialog = new ProgressDialog(this);
                    current_task = ((AndZiApplication) getApplication()).getMyDB().create_merge_task(filePath, dialog, this,
                            new Runnable() {
                                @Override
                                public void run() {
                                    ((AndZiApplication)getApplication()).initialize_my_db();
                                }
                            });
                    current_task_type =2;
                } else {
                    Toast.makeText(Settings.this, R.string.settings_error_not_enough_space_import, Toast.LENGTH_SHORT).show();
                }
            }
            else if (requestCode == 1) {
                if (((AndZiApplication)getApplication()).check_replace_progress(filePath))
                    ((AndZiApplication)getApplication()).replaceProgressDataBase(filePath);
                else {
                    Toast.makeText(Settings.this, R.string.settings_error_not_enough_space_import, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private class reset_score extends AsyncTask<Void,Void,Void> {
        ProgressDialog mProgressDialog;

        public reset_score(ProgressDialog dialog) {
            mProgressDialog = dialog;
        }

        @Override
        protected void onPreExecute() {
            mProgressDialog.setTitle(getResources().getString(R.string.settings_reset_db_progress));
            mProgressDialog.setMessage("");
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.show();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mProgressDialog.dismiss();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            ((AndZiApplication) getApplication()).getMyDB().resetAllScores();
            return null;
        }

        public void change_dialog(ProgressDialog dialog) {
            mProgressDialog = dialog;
            mProgressDialog.setTitle(getResources().getString(R.string.settings_reset_db_progress));
            mProgressDialog.setMessage("");
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.show();
        }
    }
}
