package com.matthieu;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import com.matthieu.settings.Settings;

public class MainMenuActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
}
