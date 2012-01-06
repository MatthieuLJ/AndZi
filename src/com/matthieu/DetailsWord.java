package com.matthieu;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

public class DetailsWord  {

    static void populate_word_details_view(Activity context, ViewGroup section, String hanzi, boolean include_ref) {
        HanziDB myDB = ((AndZiApplication) context.getApplication()).getMyDB();
        HanziDB refDB = ((AndZiApplication) context.getApplication()).getRefDB();

        ArrayList<String> pinyins = new ArrayList<String>();
        for (Vocabulary voc : myDB.getTranslations(hanzi)) {
            if (!pinyins.contains(voc.pinyin))
                pinyins.add(voc.pinyin);
        }
        if ((include_ref) && (refDB != null)) {
            for (Vocabulary voc : refDB.getTranslations(hanzi)) {
                if (!pinyins.contains(voc.pinyin))
                    pinyins.add(voc.pinyin);
            }
        }

        // first write the word at the top
        LinearLayout top_word = new LinearLayout(context);
        top_word.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        top_word.setOrientation(LinearLayout.HORIZONTAL);
        top_word.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.TOP);
        for (int i=0; i<hanzi.length(); ) {
            TextView character = new TextView(context);
            final String character_label = hanzi.substring(i, i + Character.charCount(hanzi.codePointAt(i)));
            character.setTextAppearance(context, android.R.style.TextAppearance_Large_Inverse);
            character.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
            character.setText(character_label);
            character.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    // send to character details of character_label
                }
            });
            top_word.addView(character);
            i+=Character.charCount(hanzi.codePointAt(i));
        }

        section.addView(top_word);

        for (int i=0; i<pinyins.size(); i++) {
            add_one_word_pinyin(context, section, hanzi, pinyins.get(i), include_ref);
        }
    }

    static void add_one_word_pinyin(Activity context, ViewGroup section, String hanzi, String pinyin, boolean include_ref) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean show_pinyin_color = sp.getBoolean("color_pinyin", true);
        HanziDB myDB = ((AndZiApplication) context.getApplication()).getMyDB();
        HanziDB refDB = ((AndZiApplication) context.getApplication()).getRefDB();

        TextView tv_pinyin = new TextView(context);
        if (show_pinyin_color)
            tv_pinyin.setText(Html.fromHtml(PinyinParser.convert_to_html_version(pinyin)));
        else
            tv_pinyin.setText(PinyinParser.convert_to_pretty_version(pinyin));
        section.addView(tv_pinyin, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tv_translations = new TextView(context);
        ArrayList<Vocabulary> vocabulary =  myDB.getTranslations(hanzi, pinyin);
        ArrayList<String> trans;
        if (vocabulary.size() > 0)
            trans = vocabulary.get(0).english;
        else
            trans = new ArrayList<String>();
        if ((include_ref) && (refDB != null)) {
            ArrayList<Vocabulary> more_vocabulary = refDB.getTranslations(hanzi, pinyin);
            if (more_vocabulary.size() > 0)
                for (String more_trans:more_vocabulary.get(0).english)
                    if (!trans.contains(more_trans)) {
                        trans.add("<i>"+more_trans+"</i>");
                    }
        }
        String str_trans = "";

        for (int j=0; j<trans.size(); j++) {
            String tr=trans.get(j);
            str_trans += tr;
            if (j<trans.size()-1)
                str_trans += ", ";

        }
        tv_translations.setText(Html.fromHtml(str_trans));
        tv_translations.setTextColor(Color.BLACK);
        section.addView(tv_translations, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
