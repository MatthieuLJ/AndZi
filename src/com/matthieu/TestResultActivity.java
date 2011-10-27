package com.matthieu;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

public class TestResultActivity extends Activity {
    protected HanziDB myDB=null;
    protected String hanzi;
    ArrayList<String> pinyins;
    int mode; // 0 for character, 1 for word

    private final String TAG="TestResult";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).myDB;

        Log.d(TAG, "Show results");

        hanzi = getIntent().getStringExtra("Hanzi");
        if (hanzi == null) {
            finish();
        }

        mode = getIntent().getIntExtra("Mode", 1);

        Log.d(TAG, "Character is "+hanzi+" in mode "+((mode==1)?"word":"character"));
        
        pinyins = getIntent().getStringArrayListExtra("Pinyins");
        if ((pinyins == null) || (pinyins.size() == 0)) {
            if (mode == 0)
                pinyins = myDB.getPinyinForChar(hanzi);
            else if (mode == 1) {
                pinyins = new ArrayList<String>();
                for (Vocabulary voc : myDB.getTranslations(hanzi)) {
                    pinyins.add(voc.pinyin);
                }
            }
        }

        setContentView(R.layout.test_result);

        LinearLayout main_layout = (LinearLayout) findViewById(R.id.test_results);

        if (mode == 0) {
            for (String pin : pinyins) {
                add_one_tone(main_layout, pin);
            }
        }
        else if (mode == 1) {
            for (String pin : pinyins) {
                add_one_word(main_layout, pin);
            }
        }

        TextView tv_hanzi = (TextView) findViewById(R.id.test_result_chinese);
        tv_hanzi.setText(hanzi);
    }

    @Override
    public boolean onCreateThumbnail(Bitmap outBitmap, Canvas canvas) {
        return super.onCreateThumbnail(outBitmap, canvas);
    }

    private void add_one_tone(LinearLayout linear, String pin) {
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout section = (LinearLayout) inflater.inflate(R.layout.test_result_section, linear, false);

        TextView tv_pinyin = (TextView) section.findViewById(R.id.test_result_pinyin);
        tv_pinyin.setText(Html.fromHtml(PinyinParser.convert_to_html_version(pin)));

        LinearLayout result_section = (LinearLayout) section.findViewById(R.id.test_result_translations);

        ArrayList<Vocabulary> trans = myDB.getWordsWithPinyin(hanzi, pin);
        for (int i=0; i<trans.size(); i++) {
            String str_trans = new String();
            TextView tv_translations = new TextView(this);
            Vocabulary voc = trans.get(i);
            str_trans += voc.chinese + "(" + PinyinParser.convert_to_html_version(voc.pinyin) + ") ";
            for (int j=0; j<voc.english.size(); j++) {
                String tr=voc.english.get(j);
                str_trans += tr;
                if (j<voc.english.size()-1)
                    str_trans += ", ";
            }
            Log.d(TAG, "adding word "+str_trans+" to list");
            tv_translations.setText(Html.fromHtml(str_trans));
            tv_translations.setTextAppearance(this, android.R.attr.textAppearanceMedium);
            tv_translations.setTextColor(0xff000000);
            tv_translations.setTag(voc.chinese);
            tv_translations.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    click_result(view);
                }
            });
            result_section.addView(tv_translations, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        linear.addView(section, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void add_one_word(LinearLayout linear, String pin) {
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View section = inflater.inflate(R.layout.test_result_section, linear, false);

        TextView tv_pinyin = (TextView) section.findViewById(R.id.test_result_pinyin);
        tv_pinyin.setText(Html.fromHtml(PinyinParser.convert_to_html_version(pin)));
        LinearLayout result_section = (LinearLayout) section.findViewById(R.id.test_result_translations);
        TextView tv_translations = new TextView(this);
        ArrayList<Vocabulary> trans = myDB.getTranslations(hanzi, pin);
        String str_trans = new String();
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
        tv_translations.setTag(hanzi);
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

    public void click_result(View v) {
        TextView text = (TextView) v;
        String hanzi = (String) text.getTag();
        Intent i = new Intent(this, AddVocabularyActivity.class);
		i.putExtra("Hanzi", hanzi);
        startActivity(i);
        this.finish();
    }

}
