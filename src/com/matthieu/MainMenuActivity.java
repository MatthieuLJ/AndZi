package com.matthieu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import com.matthieu.settings.Settings;

public class MainMenuActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final HanziDB myDB = ((AndZiApplication) getApplication()).getMyDB();

        if (!myDB.checkDatabase()) {
            AlertDialog.Builder alert = new AlertDialog.Builder(MainMenuActivity.this);
            alert.setMessage("Found some problems in the database, would you like to fix them?");
            alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int i) {
                    myDB.checkDatabase(true);
                    dialog.dismiss();
                }
            });
            alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int i) {
                    dialog.dismiss();
                }
            });
            alert.create();
            alert.show();
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean offer_to_download_ref=prefs.getBoolean("first_time_ref", true);
        if ((offer_to_download_ref) && (!((AndZiApplication) getApplication()).getRefDBPresent())) {
            AlertDialog.Builder alert = new AlertDialog.Builder(MainMenuActivity.this);
            alert.setMessage("Everything would be so much better if you downloaded the reference data...");
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
                    Intent in=new Intent(MainMenuActivity.this, Settings.class);
                    in.putExtra("Settings_action", 0);
                    startActivity(in);
                    dialog.dismiss();
                }
            });
            alert.create();
            alert.show();
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

    public void handle_tone_tester(View v) {
        Intent intent = new Intent(this, GuessTone.class);
        startActivity(intent);
    }

    public void handle_stats(View v) {
        Intent intent = new Intent(this, DBActivity.class);
        startActivity(intent);
    }

    public void handle_char_finder(View v) {
        Intent intent = new Intent(this, GuessChar.class);
        startActivity(intent);
    }

    public void handle_prefs(View v) {
        Intent intent = new Intent(this, Settings.class);
        startActivity(intent);
    }

    public void handle_guesschinese(View v) {
        Intent intent = new Intent(this, GuessChineseWord.class);
        startActivity(intent);
    }

    public void handle_reviewlist(View v) {
        Intent intent = new Intent(this, ReviewList.class);
        startActivity(intent);
    }

    public void handle_guesspinyin(View v) {
        Intent intent = new Intent(this, GuessPinyin.class);
        startActivity(intent);
    }

    public void handle_guessenglish(View v) {
        Intent intent = new Intent(this, GuessEnglish.class);
        startActivity(intent);
    }
    
    public void handle_guesscontaining(View v) {
        Intent intent = new Intent(this, GuessContaining.class);
        startActivity(intent);
    }
}
