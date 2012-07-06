/******************************
 * Main settings activity
 */
package com.matthieu.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Toast;
import com.matthieu.AndZiApplication;
import com.matthieu.HanziDB;
import com.matthieu.R;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;

public class Settings extends PreferenceActivity {
    protected HanziDB myDB=null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        addPreferencesFromResource(R.xml.settings_main);

        Preference reset_db = findPreference("ResetDB");
        reset_db.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                AlertDialog.Builder alert = new AlertDialog.Builder(Settings.this);
                alert.setMessage("Are you sure you want to restart the study for everything?");
                alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, int i) {
                        new AsyncTask<Void,Void,Void>() {
                            ProgressDialog mProgressDialog;
                            @Override
                            protected void onPreExecute() {
                                super.onPreExecute();
                                dialog.dismiss();
                                mProgressDialog = ProgressDialog.show(Settings.this, "Reset Database", "", false);
                            }

                            @Override
                            protected void onPostExecute(Void aVoid) {
                                super.onPostExecute(aVoid);
                                mProgressDialog.dismiss();
                            }

                            @Override
                            protected Void doInBackground(Void... voids) {
                                myDB.resetAllScores();
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
                AlertDialog.Builder alert = new AlertDialog.Builder(Settings.this);
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
                Intent intent = new Intent(Settings.this, com.matthieu.widget.file_dialog.FileDialog.class);
                intent.putExtra(com.matthieu.widget.file_dialog.FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());
                startActivityForResult(intent, 0);
                return true;
            }
        });

        final Preference enable_ref = findPreference("ref_enabled");
        final Preference del_ref = findPreference("ClearRef");

        enable_ref.setEnabled(((AndZiApplication)getApplication()).getRefDBPresent());
        del_ref.setEnabled(((AndZiApplication)getApplication()).getRefDBPresent());

        Preference download_ref = findPreference("DownloadRef");
        download_ref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                final String params[]= {"http://db.tt/RdRHvoJa"};
                ref_downloader down = new ref_downloader();
                down.execute(params);
                return true;
            }
        });

        del_ref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                AlertDialog.Builder alert = new AlertDialog.Builder(Settings.this);
                alert.setMessage("Are you sure you want to delete the reference database?");
                alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        ((AndZiApplication) getApplication()).eraseRefDB();
                        enable_ref.setEnabled(((AndZiApplication) getApplication()).getRefDBPresent());
                        del_ref.setEnabled(((AndZiApplication) getApplication()).getRefDBPresent());
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

        Preference export_progress = findPreference("ExportProgress");
        export_progress.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            private void do_export_progress(final String destination) {
                File export_db = new File(destination);
                try {
                    if (!export_db.createNewFile()) {
                        Toast toast = Toast.makeText(Settings.this, "There was an error creating the file", 5);
                        toast.show();
                        return;
                    }
                } catch (IOException e) {
                    Toast toast = Toast.makeText(Settings.this, "There was an error creating the file", 5);
                    toast.show();
                    return;
                }
                if (((AndZiApplication) getApplication()).check_exporting_progress_space(destination)) {
                    ((AndZiApplication) getApplication()).copyProgressDataBase(destination);
                    Toast toast = Toast.makeText(Settings.this, "Successfully copied progress to\n"+destination, 5);
                                        toast.show();
                }
                else {
                    Toast toast = Toast.makeText(Settings.this, "Not enough space on device to export database", 5);
                    toast.show();
                }

                return;
            }
            public boolean onPreferenceClick(Preference preference) {
                String dest = Environment.getExternalStorageDirectory().getPath()+"/progress-"+ (1+ Calendar.getInstance().get(Calendar.MONTH))+"-"+Calendar.getInstance().get(Calendar.DAY_OF_MONTH)+".azdb";
                do_export_progress(dest);
                return true;
            }
        });

        Preference import_progress = findPreference("ImportProgress");
        import_progress.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            private void do_import_progress(final String destination) {
                File export_db = new File(destination);
                try {
                    if (!export_db.createNewFile()) {
                        Toast toast = Toast.makeText(Settings.this, "There was an error creating the file", 5);
                        toast.show();
                        return;
                    }
                } catch (IOException e) {
                    Toast toast = Toast.makeText(Settings.this, "There was an error creating the file", 5);
                    toast.show();
                    return;
                }
                if (((AndZiApplication) getApplication()).check_exporting_progress_space(destination)) {
                    ((AndZiApplication) getApplication()).copyProgressDataBase(destination);
                    Toast toast = Toast.makeText(Settings.this, "Successfully copied progress to\n"+destination, 5);
                    toast.show();
                }
                else {
                    Toast toast = Toast.makeText(Settings.this, "Not enough space on device to export database", 5);
                    toast.show();
                }

                return;
            }
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(Settings.this, com.matthieu.widget.file_dialog.FileDialog.class);
                intent.putExtra(com.matthieu.widget.file_dialog.FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());
                startActivityForResult(intent, 1);
                return true;
            }
        });

        int intent_action=getIntent().getIntExtra("Settings_action", -1);
        switch(intent_action) {
            case 0: // download reference
                final String params[]= {"http://db.tt/RdRHvoJa"};
                ref_downloader down = new ref_downloader();
                down.execute(params);
                break;
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
                if (((AndZiApplication)getApplication()).check_merging_space(filePath))
                    myDB.create_merge_task(filePath, true, this, null);
                else {
                    Toast.makeText(Settings.this, "Not enough space on device to import database", Toast.LENGTH_SHORT).show();
                }
            }
            else if (requestCode == 1) {
                if (((AndZiApplication)getApplication()).check_replace_progress(filePath))
                    ((AndZiApplication)getApplication()).replaceProgressDataBase(filePath);
                else {
                    Toast toast = Toast.makeText(Settings.this, "Not enough space on device to import progress", 5);
                    toast.show();
                }
            }

        } else if (resultCode == Activity.RESULT_CANCELED) {
            return;
        }
    }

    private class ref_downloader extends AsyncTask<String,Integer,Void> {
        ProgressDialog mProgressDialog;
        String PATH, URL;
        boolean enough_space;
        private final String TAG="ref_downloader";

        @Override
        protected Void doInBackground(String... strings) {
            try {
                PATH= File.createTempFile("ref", "db").getPath();
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

            mProgressDialog = new ProgressDialog(Settings.this);
            mProgressDialog.setMessage("Downloading reference database");
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setMax(100);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialogInterface) {
                    cancel(false);
                }
            });
            mProgressDialog.setCancelable(true);
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
                Toast.makeText(Settings.this, "Not enough space on device to download and install reference database", Toast.LENGTH_SHORT).show();
            }

            final Preference enable_ref = findPreference("ref_enabled");
            final Preference del_ref = findPreference("ClearRef");

            if (enable_ref != null) enable_ref.setEnabled(((AndZiApplication)getApplication()).getRefDBPresent());
            if (del_ref != null) del_ref.setEnabled(((AndZiApplication)getApplication()).getRefDBPresent());
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
                    if (isCancelled()) {
                        output.flush();
                        output.close();
                        input.close();
                        new File(fileName).delete();
                        return;
                    }
                    total += count;
                    // publishing the progress....
                    publishProgress((int) (total * 100 / lengthOfFile));
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
}
