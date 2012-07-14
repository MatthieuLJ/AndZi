/******************************
 * MainMenu, entry activity into the application
 */
package com.matthieu;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.matthieu.settings.Settings;
import com.matthieu.widget.AndZiActivity;
import com.matthieu.widget.DBCheckProgressHandler;


public class MainMenuActivity extends AndZiActivity {
    private final String TAG="MainMenuActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ProgressService.clear_notification(this);

        final HanziDB myDB = ((AndZiApplication) getApplication()).getMyDB();

        boolean showing_splash = ((AndZiApplication) getApplication()).getShowSplash();

        if (showing_splash) {
            setContentView(R.layout.main_menu_splash);
            TextView tversion = (TextView) findViewById(R.id.splash_version);
            tversion.setText(((AndZiApplication) getApplication()).get_version());

            final ProgressBar progress = (ProgressBar) findViewById(R.id.splash_progress);
            progress.setIndeterminate(false);
            progress.setMax(100);

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    myDB.checkDatabase(true, new DBCheckProgressHandler() {
                        @Override
                        public void progress(int i) {
                            progress.setProgress(i);
                        }

                        @Override
                        public void finish(boolean b) {
                            start_it();
                        }
                    });
                }
            }, 1000);
        }
        else {
            start_it();
        }
    }

    private void start_it() {
        ((AndZiApplication) getApplication()).showSplash(false);
        if (((AndZiApplication) getApplication()).isOnline()) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainMenuActivity.this);
            boolean offer_to_download_ref = prefs.getBoolean("first_time_ref", true);
            if ((offer_to_download_ref) && (!((AndZiApplication) getApplication()).getRefDBPresent())) {
                AlertDialog.Builder alert = new AlertDialog.Builder(MainMenuActivity.this);
                alert.setMessage("Everything would be so much better if you downloaded the dictionary...");
                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });
                alert.setNeutralButton("Do not ask me again", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        SharedPreferences.Editor edit = prefs.edit();
                        edit.putBoolean("first_time_ref", false);
                        edit.commit();
                        dialog.dismiss();
                    }
                });
                alert.setPositiveButton("Download", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        Intent in = new Intent(MainMenuActivity.this, Settings.class);
                        in.putExtra("Settings_action", 0);
                        startActivity(in);
                        dialog.dismiss();
                    }
                });
                alert.create();
                alert.show();
            }
        }

        setContentView(R.layout.main_menu);
    }

    public void handle_add_vocabulary(View v) {
        Intent intent = new Intent(this, AddVocabularyActivity.class);
        startActivity(intent);
    }

    public void handle_edit_lessons(View v) {
        Intent intent = new Intent(this, EditLessonsActivity.class);
        startActivity(intent);
    }

    public void handle_stats(View v) {
        Intent intent = new Intent(this, DBActivity.class);
        startActivity(intent);
    }

    public void handle_prefs(View v) {
        Intent intent = new Intent(this, Settings.class);
        startActivity(intent);
    }

    public void handle_reviewlist(View v) {
        Intent intent = new Intent(this, ReviewList.class);
        startActivity(intent);
    }
    
    public void handle_test(View v) {
        Intent intent = new Intent(this, Test.class);
        startActivity(intent);
    }

    public void handle_dictionary(View v) {
        Intent intent = new Intent(this, Dictionary.class);
        startActivity(intent);
    }

    public void handle_help(View v) {
        Intent intent = new Intent(this, Help.class);
        startActivity(intent);
    }
}
