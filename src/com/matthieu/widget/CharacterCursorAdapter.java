package com.matthieu.widget;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.matthieu.*;
import java.util.ArrayList;

public class CharacterCursorAdapter  extends CursorAdapter {
    boolean show_pinyin_color;
    HanziDB db;
    boolean filtered;

    ArrayList<CharPinyin> expanded_map=new ArrayList<CharPinyin>();

    private class CharPinyin {
        public String hanzi=null;
        public String pinyin=null;
        public CharPinyin(String han, String pin) {this.hanzi=han; this.pinyin=pin; }
        public boolean equals(CharPinyin other) {
            return ((other.hanzi.equals(hanzi)) && (other.pinyin.equals(pinyin)));
        }
    }

    public CharacterCursorAdapter(final Context context, final Cursor cursor, final HanziDB db, final boolean filtered) {
        super(context, cursor, true);
        if (cursor != null) {
            chinese_column_index = cursor.getColumnIndex("chinese");
            pinyin_column_index = cursor.getColumnIndex("pinyin");
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        show_pinyin_color = sp.getBoolean("color_pinyin", true);

        this.db = db;
        this.filtered = filtered;
    }

    private int chinese_column_index;
    private int pinyin_column_index;

    @Override
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor);
        if (cursor != null) {
            chinese_column_index = cursor.getColumnIndex("chinese");
            pinyin_column_index = cursor.getColumnIndex("pinyin");
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View newView = li.inflate(R.layout.review_list_item, viewGroup, false);

        bindView(newView, context, cursor);

        return newView;
    }

    @Override
    public void bindView(final View view, final Context context, final Cursor cursor) {
        TextView chinese = (TextView) view.findViewById(R.id.review_item_character);
        final String hanzi = cursor.getString(chinese_column_index);
        chinese.setText(hanzi);
        chinese.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent intent=new Intent(context, Details.class);
                intent.putExtra("hanzi", hanzi);
                context.startActivity(intent);
            }
        });
        final String pinyin = cursor.getString(pinyin_column_index);
        TextView pinyin_text = (TextView) view.findViewById(R.id.review_item_pinyin);
        if (show_pinyin_color)
            pinyin_text.setText(PinyinParser.convert_to_spannable_version(pinyin));
        else
            pinyin_text.setText(PinyinParser.convert_to_pretty_version(pinyin));

        Button arrow = (Button) view.findViewById(R.id.review_expand_button);
        arrow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                expand(view);
            }
        });
        TableLayout word_table = (TableLayout) view.findViewById(R.id.review_item_word_table);
        word_table.removeAllViews();
        ExtendingLinearLayout words = (ExtendingLinearLayout) view.findViewById(R.id.review_item_words);
        CharPinyin char_pinyin = new CharPinyin(hanzi, pinyin);
        boolean expanded=expanded_map.contains(char_pinyin);
        if (expanded) {
            fill_word_table(context, (ViewGroup) view, char_pinyin);
            words.expand_immediate();
            arrow.setBackgroundResource(R.drawable.expand_arrow_down);
        }
        else {
            words.collapse_immediate();
            arrow.setBackgroundResource(R.drawable.expand_arrow_right);
        }
        arrow.setTag(R.id.review_view_to_expand, words);
        arrow.setTag(R.id.review_item, char_pinyin);
    }

    private void fill_word_table(final Context context, final ViewGroup container, final CharPinyin char_pinyin) {
        LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ArrayList<Vocabulary> words;
        if (filtered)
            words = db.getFilteredWordsWithPinyin(char_pinyin.hanzi, char_pinyin.pinyin);
        else
            words = db.getWordsWithPinyin(char_pinyin.hanzi, char_pinyin.pinyin);
        TableLayout word_table = (TableLayout) container.findViewById(R.id.review_item_word_table);

        for (Vocabulary voc : words) {
            TableRow row = (TableRow) li.inflate(R.layout.review_item_word_row, container, false);
            TextView chinese_word = (TextView) row.findViewById(R.id.review_word_chinese);
            final String chinese_label = voc.chinese;
            chinese_word.setText(chinese_label);
            TextView english_word = (TextView) row.findViewById(R.id.review_word_english);
            String english_translations="";
            for (int j=0; j<voc.english.size(); j++) {
                english_translations += voc.english.get(j);
                if (j < voc.english.size()-1)
                    english_translations += ", ";
            }
            english_word.setText(english_translations);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    Intent intent=new Intent(context, Details.class);
                    intent.putExtra("hanzi", chinese_label);
                    context.startActivity(intent);
                }
            });
            word_table.addView(row);
        }
    }

    public void expand(View v) {
        CharPinyin char_pinyin = (CharPinyin) v.getTag(R.id.review_item);
        boolean expanded=expanded_map.contains(char_pinyin);
        ExtendingLinearLayout el = (ExtendingLinearLayout) v.getTag(R.id.review_view_to_expand);
        if (el == null)
            return;

        if (!expanded) {
            fill_word_table(v.getContext(), el, char_pinyin);
            v.setBackgroundResource(R.drawable.expand_arrow_down);
            el.expand();
            expanded_map.add(char_pinyin);
        }
        else {
            v.setBackgroundResource(R.drawable.expand_arrow_right);
            el.collapse();
            expanded_map.remove(char_pinyin);
        }
    }
}
