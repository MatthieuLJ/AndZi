package com.matthieu.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.widget.Toast;
import com.matthieu.AndZiApplication;
import com.matthieu.HanziDB;
import com.matthieu.R;

import java.util.ArrayList;

public class AndZiPreferences extends PreferenceActivity {
    protected HanziDB myDB=null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).myDB;
        addPreferencesFromResource(R.xml.andzipreferences);

        Preference reset_db = findPreference("ResetDB");
        reset_db.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                AlertDialog.Builder alert = new AlertDialog.Builder(AndZiPreferences.this);
                alert.setMessage("Are you sure you want to restart the study for everything?");
                alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, int i) {
                        new AsyncTask<Void,Void,Void>() {
                            ProgressDialog mProgressDialog;
                            @Override
                            protected void onPreExecute() {
                                super.onPreExecute();
                                dialog.dismiss();
                                mProgressDialog = ProgressDialog.show(AndZiPreferences.this, "Reset Database", "", false);
                            }

                            @Override
                            protected void onPostExecute(Void aVoid) {
                                super.onPostExecute(aVoid);
                                mProgressDialog.dismiss();
                            }

                            @Override
                            protected Void doInBackground(Void... voids) {
                                ArrayList<String> hanzis = myDB.getAllChar();
                                for (String han:hanzis){
                                    myDB.restartStudy(han);
                                }

                                return null;
                            }
                        }.execute((Void)null);
                    }
                });
                alert.setNegativeButton("No", new DialogInterface.OnClickListener() {
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
                AlertDialog.Builder alert = new AlertDialog.Builder(AndZiPreferences.this);
                alert.setMessage("Are you sure you want to clear all your database?");
                alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        myDB.clearDB();
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
                return true;
            }
        });

        Preference import_db = findPreference("ImportDB");
        import_db.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(AndZiPreferences.this, com.matthieu.widget.file_dialog.FileDialog.class);
                intent.putExtra(com.matthieu.widget.file_dialog.FileDialog.START_PATH, "/sdcard");
                startActivityForResult(intent, 0);
                return true;
            }
        });
    }
    public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {
        // should come here after the activity startedin import_database completes
        if (resultCode == Activity.RESULT_OK) {

            if ((requestCode != 0) && (requestCode != 1)) {
                return;
            }
            final String filePath = data.getStringExtra(com.matthieu.widget.file_dialog.FileDialog.RESULT_PATH);

            if (requestCode == 0) {
                merger merge_task = new merger();
                merge_task.execute(filePath);
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
            if (myDB.getAllChar().size()==0) {
                mProgressDialog = ProgressDialog.show(AndZiPreferences.this, "Exporting Database", "Copying...", false);
            }
            else {
                mProgressDialog = new ProgressDialog(AndZiPreferences.this);
                mProgressDialog.setMessage("Merging database");
                mProgressDialog.setIndeterminate(false);
                mProgressDialog.setMax(100);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.show();
            }
        }

        @Override
        protected Void doInBackground(String... strings) {
            enough_space = ((AndZiApplication) getApplication()).check_merging_space(strings[0]);
            if (enough_space) {
                if (myDB.getAllChar().size()==0) {
                    // just need to copy
                    ((AndZiApplication) getApplication()).replaceDataBase(strings[0]);
                }
                else {
                    myDB.mergeDB(strings[0], mProgressDialog);    
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            mProgressDialog.dismiss();
            if (!enough_space) {
                Toast toast = Toast.makeText(AndZiPreferences.this, "Not enough space on device to impoort database", 5);
                toast.show();
            }
        }
    }
}
