/******************************
 * MainMenu, entry activity into the application
 */
package com.matthieu;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.View;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.matthieu.settings.Settings;
import org.jetbrains.annotations.Nullable;

public class MainMenuActivity extends SherlockFragmentActivity {
    private final String TAG="MainMenuActivity";
    boolean showing_splash = false;
    boolean start_test;
    Fragment splash_fragment=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock);
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        start_test=false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        start_test=intent.getBooleanExtra("start_test", false);
    }

    public void start_it() {
        if (splash_fragment != null) {
            getSupportFragmentManager().beginTransaction().remove(splash_fragment).commit();
            splash_fragment = null;
        }
        if (start_test) {
            handle_test(null);
            start_test=false;
        }
        else if (((AndZiApplication) getApplication()).isOnline() && showing_splash) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainMenuActivity.this);
            boolean offer_to_download_ref = prefs.getBoolean("first_time_ref", true);
            if ((offer_to_download_ref) && (!((AndZiApplication) getApplication()).getRefDBPresent())) {
                AlertDialog.Builder alert = new AlertDialog.Builder(MainMenuActivity.this);
                alert.setMessage(R.string.download_dictionary);
                alert.setNegativeButton(R.string.later, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });
                alert.setNeutralButton(R.string.do_not_ask_again, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        SharedPreferences.Editor edit = prefs.edit();
                        edit.putBoolean("first_time_ref", false);
                        edit.commit();
                        dialog.dismiss();
                    }
                });
                alert.setPositiveButton(R.string.download, new DialogInterface.OnClickListener() {
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

    @Override
    protected void onResume() {
        super.onResume();

        showing_splash = ((AndZiApplication) getApplication()).getShowSplash();
        ((AndZiApplication) getApplication()).showSplash(false);

        if (showing_splash || (((AndZiApplication) getApplication()).getMyDB() == null)) {
            splash_fragment = new SplashFragment();
            getSupportFragmentManager().beginTransaction().add(android.R.id.content, splash_fragment).commit();
        }
        else {
            ((AndZiApplication)getApplication()).initialize_ref_db();
            start_it();
        }
    }

    public void handle_add_vocabulary(View v) {
        Intent intent = new Intent(this, VocabularyEditorActivity.class);
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
    
    public void handle_test(@Nullable View v) {
        Intent intent = new Intent(this, TestActivity.class);
        startActivity(intent);
    }

    public void handle_dictionary(View v) {
        Intent intent = new Intent(this, ReferenceActivity.class);
        startActivity(intent);
    }

    public void handle_help(View v) {
        Intent intent = new Intent(this, Help.class);
        startActivity(intent);
    }
}
