/******************************
 * Activity to export the database to a file
 */
package com.matthieu.settings;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
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
    String file_path= Environment.getExternalStorageDirectory().getPath()+"/AndZiDB-"+ (1+Calendar.getInstance().get(Calendar.MONTH))+"-"+Calendar.getInstance().get(Calendar.DAY_OF_MONTH)+".azdb";
        
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        addPreferencesFromResource(R.xml.settings_export_db);
        EditTextPreference path = (EditTextPreference) findPreference("export_path");
        path.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object o) {
                String current_text = (String) o;
                preference.setSummary("Destination: "+current_text);
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ExportDB.this);
                SharedPreferences.Editor edit = sp.edit();
                edit.putString("export_path", current_text);
                edit.commit();
                return true;
            }
        });
        path.setText(file_path);
        path.setSummary("Destination: "+file_path);
        Preference export = findPreference("do_export");
        export.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            private void do_export(final String destination, final boolean include_scores, final boolean only_filtered) {
                File export_db = new File(destination);
                try {
                    if (!export_db.createNewFile()) {
                        Toast toast = Toast.makeText(ExportDB.this, "There was an error creating the file", 5);
                        toast.show();
                        return;
                    }
                } catch (IOException e) {
                    Toast toast = Toast.makeText(ExportDB.this, "There was an error creating the file", 5);
                    toast.show();
                    return;
                }
                if (((AndZiApplication) getApplication()).check_exporting_space(destination))
                    myDB.create_export_task(destination, include_scores, only_filtered, true, ExportDB.this, new Runnable() {
                        public void run() {
                            Toast toast = Toast.makeText(ExportDB.this, "Database was copied to "+destination, 5);
                            toast.show();
                        }
                    });
                else {
                    Toast toast = Toast.makeText(ExportDB.this, "Not enough space on device to export database", 5);
                    toast.show();
                }

                return;
            }
            public boolean onPreferenceClick(Preference preference) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ExportDB.this);
                final boolean include_scores = sp.getBoolean("include_scores", false);
                final boolean only_filtered = sp.getBoolean("only_filtered", false);
                final String destination = sp.getString("export_path", file_path);

                if (new File(destination).exists()) {
                    AlertDialog.Builder alert = new AlertDialog.Builder(ExportDB.this);
                    alert.setMessage("The file ["+destination+"] already exists, do you want to overwrite?");
                    alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            dialog.dismiss();
                        }
                    });
                    alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            new File(destination).delete();
                            do_export(destination, include_scores, only_filtered);
                            dialog.dismiss();
                        }
                    });
                    alert.create();
                    alert.show();
                }
                else {
                    do_export(destination, include_scores, only_filtered);
                }

                return true;
            }
        });
    }
}
