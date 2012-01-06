package com.matthieu;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

public class DetailsCharacter {
    static public void populate_character_details_view(Activity context, ViewGroup section, String hanzi, boolean include_ref) {
        populate_character_details_view(context, section, hanzi, null, include_ref);
    }

    static public void populate_character_details_view(Activity context, ViewGroup section, String hanzi, ArrayList<String> pinyin, boolean include_ref) {
        // first write the character at the top
        TextView character = new TextView(context);
        character.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final String character_label = hanzi;
        character.setTextAppearance(context, android.R.style.TextAppearance_Large_Inverse);
        character.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        character.setGravity(Gravity.CENTER_HORIZONTAL);
        character.setText(character_label);
        section.addView(character);

        if (pinyin == null) {
            HanziDB myDB = ((AndZiApplication) context.getApplication()).getMyDB();
            HanziDB refDB = ((AndZiApplication) context.getApplication()).getRefDB();

            // get all possible pinyins
            pinyin=myDB.getPinyinForChar(hanzi);
            if ((include_ref) && (refDB != null)) {
                for (String p:refDB.getPinyinForChar(hanzi))
                    if (!pinyin.contains(p))
                        pinyin.add(p);
            }
        }

        for (String pin:pinyin)
            add_one_character_pinyin(context, section, hanzi, pin, include_ref);

    }

    static void add_one_character_pinyin(Activity context, ViewGroup section, String hanzi, String pinyin, boolean include_ref) {
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

        ArrayList<Vocabulary> trans = myDB.getWordsWithPinyin(hanzi, pinyin);
        if ((include_ref) && (refDB != null))
            trans = HanziDB.merge_vocabulary(trans, refDB.getWordsWithPinyin(hanzi, pinyin));

        for (Vocabulary tran : trans) {
            String str_trans = "";
            TextView tv_translations = new TextView(context);
            str_trans += tran.chinese + " (" + PinyinParser.convert_to_html_version(tran.pinyin) + "): ";
            for (int j = 0; j < tran.english.size(); j++) {
                str_trans += tran.english.get(j);
                if (j < tran.english.size() - 1)
                    str_trans += ", ";
            }
            tv_translations.setText(Html.fromHtml(str_trans));
            tv_translations.setTextAppearance(context, android.R.attr.textAppearanceMedium);
            tv_translations.setTextColor(0xff000000);
            tv_translations.setTag(tran.chinese);
            tv_translations.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {

                }
            });
            section.addView(tv_translations, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }
}
