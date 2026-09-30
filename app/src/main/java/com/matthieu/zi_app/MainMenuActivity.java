/******************************
 * MainMenu, entry activity into the application
 */
package com.matthieu.zi_app;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.matthieu.zi_app.settings.Settings;
import org.jetbrains.annotations.Nullable;

public class MainMenuActivity extends AppCompatActivity {
    private final String TAG="MainMenuActivity";
    boolean showing_splash = false;
    boolean start_test;
    Fragment splash_fragment=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.mainmenu_theme);
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        start_test=false;

        if (savedInstanceState != null) {
            splash_fragment = getSupportFragmentManager().getFragment(savedInstanceState, "splash");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        start_test=intent.getBooleanExtra("start_test", false);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (splash_fragment != null) {
            try {
                getSupportFragmentManager().putFragment(outState, "splash", splash_fragment);
            } catch (IllegalStateException e) {}
        }
    }

    public void start_it() {
        if (splash_fragment != null) {
            getSupportFragmentManager().beginTransaction().remove(splash_fragment).commitAllowingStateLoss();
            splash_fragment = null;
        }
        if (start_test) {
            handle_test(null);
            start_test=false;
        }

        setContentView(R.layout.main_menu);
        Typeface main_menu_font = Typeface.createFromAsset(getAssets(), "fonts/Dunkin_Sans.ttf");
        ((Button)findViewById(R.id.main_menu_test)).setTypeface(main_menu_font);
        ((Button)findViewById(R.id.main_menu_ref)).setTypeface(main_menu_font);
        ((Button)findViewById(R.id.main_menu_help)).setTypeface(main_menu_font);
        ((Button)findViewById(R.id.main_menu_settings)).setTypeface(main_menu_font);
    }

    public void fail_it() {
        Toast.makeText(this, R.string.main_menu_fail_load_dictionary, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ProgressService.clear_notification(getApplicationContext());

        if (splash_fragment != null)
            return;

        showing_splash = ((AndZiApplication) getApplication()).getShowSplash();
        ((AndZiApplication) getApplication()).showSplash(false);

        if (showing_splash || (((AndZiApplication) getApplication()).getMyDB() == null)) {
            splash_fragment = new SplashFragment();
            getSupportFragmentManager().beginTransaction().add(android.R.id.content, splash_fragment).commit();
        }
        else {
            if (!((AndZiApplication)getApplication()).initialize_ref_db(null))
                fail_it();
            else
                start_it();
        }
    }

    public void handle_prefs(View v) {
        Intent intent = new Intent(this, Settings.class);
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
