package com.matthieu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;

/* Class to display results from tests
 * Intent will contain several fields:
 *  - mode (int)                            : 0 for pinyin guess, 1 for chinese word guess
 *  - Hanzi (String)                        : Reference character or chinese string
 *  - Pinyins (ArrayList<String) / optional : Reference pinyin
 *  - WrongGuess (String) / optional        : The wrong guess from the user
 *
 */
public class TestResultActivity extends Activity {
    protected HanziDB myDB=null;
    protected String hanzi, wrong_guess;
    ArrayList<String> pinyins, wrong_pinyins;
    int mode; // 0 for character, 1 for word
    boolean accept_answer;

    private final String TAG="TestResult";

    public final static int MODE_CHARACTER=0;
    public final static int MODE_WORD=1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();

        Log.d(TAG, "Show results");

        hanzi = getIntent().getStringExtra("Hanzi");
        if (hanzi == null) {
            finish();
        }

        mode = getIntent().getIntExtra("Mode", MODE_WORD);

        Log.d(TAG, "Character is "+hanzi+" in mode "+((mode==MODE_WORD)?"word":"character"));
        
        pinyins = getIntent().getStringArrayListExtra("Pinyins");
        if ((pinyins == null) || (pinyins.size() == 0)) {
            if (mode == MODE_CHARACTER)
                pinyins = myDB.getPinyinForChar(hanzi);
            else if (mode == MODE_WORD) {
                pinyins = new ArrayList<String>();
                for (Vocabulary voc : myDB.getTranslations(hanzi)) {
                    pinyins.add(voc.pinyin);
                }
            }
        }

        wrong_guess=getIntent().getStringExtra("WrongGuess");
        if (wrong_guess != null) {
            if (mode == MODE_CHARACTER)
                wrong_pinyins = new ArrayList<String>(Arrays.asList(wrong_guess));
            else if (mode == MODE_WORD) {
                wrong_pinyins = new ArrayList<String>();
                for (Vocabulary voc : myDB.getTranslations(wrong_guess)) {
                    wrong_pinyins.add(voc.pinyin);
                }
            }
        }

        accept_answer =getIntent().getBooleanExtra("FixAnswer", false);

        setContentView(R.layout.test_result);

        LinearLayout main_layout = (LinearLayout) findViewById(R.id.test_results);

        if (mode == MODE_CHARACTER) {
            for (String pin : pinyins) {
                add_one_tone(main_layout, hanzi, pin);
            }
        }
        else if (mode == MODE_WORD) {
            for (String pin : pinyins) {
                add_one_word(main_layout, hanzi, pin);
            }
        }

        if ((wrong_pinyins != null) && (wrong_pinyins.size() != 0)) {
            LinearLayout wrong_layout = (LinearLayout) findViewById(R.id.test_result_wrong_guess);
            wrong_layout.setVisibility(View.VISIBLE);
            TextView wrong_guess_tv = (TextView) findViewById(R.id.test_result_guess);
            wrong_guess_tv.setText("Your guess was : "+wrong_guess);
            wrong_guess_tv.setVisibility(View.VISIBLE);
            if (mode == MODE_CHARACTER) {
                for (String pin : wrong_pinyins) {
                    add_one_tone(wrong_layout, wrong_guess, pin);
                }
            }
            else if (mode == MODE_WORD) {
                for (String pin : wrong_pinyins) {
                    add_one_word(wrong_layout, wrong_guess, pin);
                }
            }
        }

        TextView tv_hanzi = (TextView) findViewById(R.id.test_result_chinese);
        tv_hanzi.setText(hanzi);

        if (accept_answer)
            findViewById(R.id.test_result_accept).setVisibility(View.VISIBLE);
    }

    private void add_one_tone(LinearLayout linear, String han, String pin) {
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout section = (LinearLayout) inflater.inflate(R.layout.test_result_section, linear, false);

        TextView tv_pinyin = (TextView) section.findViewById(R.id.test_result_pinyin);
        tv_pinyin.setText(Html.fromHtml(PinyinParser.convert_to_html_version(pin)));

        LinearLayout result_section = (LinearLayout) section.findViewById(R.id.test_result_translations);

        ArrayList<Vocabulary> trans = myDB.getWordsWithPinyin(han, pin);
        for (Vocabulary tran : trans) {
            String str_trans = "";
            TextView tv_translations = new TextView(this);
            str_trans += tran.chinese + "(" + PinyinParser.convert_to_html_version(tran.pinyin) + ") ";
            for (int j = 0; j < tran.english.size(); j++) {
                String tr = tran.english.get(j);
                str_trans += tr;
                if (j < tran.english.size() - 1)
                    str_trans += ", ";
            }
            Log.d(TAG, "adding word " + str_trans + " to list");
            tv_translations.setText(Html.fromHtml(str_trans));
            tv_translations.setTextAppearance(this, android.R.attr.textAppearanceMedium);
            tv_translations.setTextColor(0xff000000);
            tv_translations.setTag(tran.chinese);
            tv_translations.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    click_result(view);
                }
            });
            result_section.addView(tv_translations, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        linear.addView(section, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void add_one_word(LinearLayout linear, String han, String pin) {
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View section = inflater.inflate(R.layout.test_result_section, linear, false);

        TextView tv_pinyin = (TextView) section.findViewById(R.id.test_result_pinyin);
        tv_pinyin.setText(Html.fromHtml(PinyinParser.convert_to_html_version(pin)));
        LinearLayout result_section = (LinearLayout) section.findViewById(R.id.test_result_translations);
        TextView tv_translations = new TextView(this);
        ArrayList<Vocabulary> trans = myDB.getTranslations(han, pin);
        String str_trans = "";
        for (int i=0; i<trans.size(); i++) {
            Vocabulary voc = trans.get(i);
            for (int j=0; j<voc.english.size(); j++) {
                String tr=voc.english.get(j);
                str_trans += tr;
                if (j<voc.english.size()-1)
                    str_trans += "\n";
            }
            if (i<trans.size()-1)
                str_trans += "\n";
        }
        tv_translations.setText(str_trans);
        tv_translations.setTag(han);
        tv_translations.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                click_result(view);
            }
        });
        result_section.addView(tv_translations, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        linear.addView(section, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void handle_next(View v) {
        finish();
    }

    public void handle_good(View v) {
        setResult(1);
        finish();
    }

    public void click_result(View v) {
        TextView text = (TextView) v;
        String hanzi = (String) text.getTag();
        Intent i = new Intent(this, AddVocabularyActivity.class);
		i.putExtra("Hanzi", hanzi);
        startActivity(i);
        this.finish();
    }

}
