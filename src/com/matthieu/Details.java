/******************************
 * Details activity to show everything we know of a word or character
 * Also offers static function to populate a ViewGroup with those details
 */

package com.matthieu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Debug;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import com.matthieu.widget.VocabularyCursorAdapter;

import java.util.ArrayList;

public class Details extends Activity {
    private static final String TAG="Details";
    private HanziDB db;

    private String current_hanzi;
    private String old_hanzi="";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        current_hanzi=getIntent().getStringExtra("hanzi");
        if ((current_hanzi==null) || (current_hanzi.equals("")))
            finish();

        boolean use_ref = getIntent().getBooleanExtra("use_ref", true);
        if (use_ref)
            db = ((AndZiApplication) getApplication()).getRefDB();
        else
            db = ((AndZiApplication) getApplication()).getMyDB();


        FrameLayout frame = new FrameLayout(this);
        populate_details_view(this, frame, current_hanzi, db);
        setContentView(frame);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String hanzi=intent.getStringExtra("hanzi");
        if ((hanzi==null) || (hanzi.equals("")))
            finish();

        boolean use_ref = intent.getBooleanExtra("use_ref", true);
        if (use_ref)
            db = ((AndZiApplication) getApplication()).getRefDB();
        else
            db = ((AndZiApplication) getApplication()).getMyDB();

        old_hanzi=current_hanzi;

        current_hanzi=hanzi;

        Log.d(TAG, "Starting new intent for " + hanzi);

        FrameLayout frame = new FrameLayout(this);
        populate_details_view(this, frame, hanzi, db);
        setContentView(frame);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (((keyCode == KeyEvent.KEYCODE_BACK)) && (!old_hanzi.equals(""))) {
            current_hanzi=old_hanzi;
            old_hanzi="";
            FrameLayout frame = new FrameLayout(this);
            populate_details_view(this, frame, current_hanzi, db);
            setContentView(frame);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    static VocabularyCursorAdapter populate_details_view(final Activity context, ViewGroup section, final String hanzi, HanziDB db) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean show_pinyin_color = sp.getBoolean("color_pinyin", true);

        View new_view = View.inflate(context, R.layout.details, section);

        if (hanzi.codePointCount(0, hanzi.length()) == 1) {
            TextView info = (TextView) new_view.findViewById(R.id.details_info);
            ArrayList<String> pinyins = db.getPinyinForChar(hanzi);
            String info_pinyins="(";
            for (int i=0; i<pinyins.size(); i++) {
                String pin=pinyins.get(i);

                if (show_pinyin_color)
                    info_pinyins+=PinyinParser.convert_to_html_version(pin);
                else
                    info_pinyins+=PinyinParser.convert_to_pretty_version(pin);
                if (i < pinyins.size()-1)
                    info_pinyins+=", ";
            }
            info_pinyins+=")";

            info.setText(Html.fromHtml(info_pinyins));
        }

        // first write the word at the top
        LinearLayout top_word = (LinearLayout) new_view.findViewById(R.id.details_title);
        for (int i=0; i<hanzi.length(); ) {
            TextView character = new TextView(context);
            final String character_label = hanzi.substring(i, i + Character.charCount(hanzi.codePointAt(i)));
            character.setTextAppearance(context, android.R.style.TextAppearance_Large_Inverse);
            character.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
            character.setText(character_label);
            if (hanzi.codePointCount(0, hanzi.length())!= 1)
                character.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        Intent intent=new Intent(context, Details.class);
                        intent.putExtra("hanzi", character_label);
                        context.startActivity(intent);
                    }
                });
            top_word.addView(character);
            i+=Character.charCount(hanzi.codePointAt(i));
        }

        ListView list= (ListView) new_view.findViewById(R.id.details_list);
        ArrayList<HanziDB.StringElement> elements = new ArrayList<HanziDB.StringElement>();
        for (int i=0; i<hanzi.length(); ) {
            elements.add(new HanziDB.StringElement(HanziDB.StringElement.CHINESE,
                    hanzi.substring(i, i+Character.charCount(hanzi.codePointAt(i)))));
            i+=Character.charCount(hanzi.codePointAt(i));
        }
        Cursor cursor = db.getWordsWithSequence(elements, true, true);
        VocabularyCursorAdapter res = new VocabularyCursorAdapter(context, cursor);
        if (list != null)
            list.setAdapter(res);

        return res;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.details_menu, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem add_word = menu.findItem(R.id.details_add_word);
        HanziDB myDB = ((AndZiApplication) getApplication()).getMyDB();
        if (myDB.getTranslations(current_hanzi).size()>0) {
            add_word.setTitle("Edit word in vocabulary list");
        }
        else {
            add_word.setTitle("Add word to vocabulary list");
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.details_add_word:
                Intent i=new Intent(this, AddVocabularyActivity.class);
                i.putExtra("Hanzi", current_hanzi);
                startActivity(i);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }
}
