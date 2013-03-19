package com.matthieu.zi_app.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;
import com.matthieu.zi_app.AndZiApplication;
import com.matthieu.zi_app.PinyinParser;
import com.matthieu.zi_app.R;
import org.jetbrains.annotations.Nullable;

public class VocabularyCursorAdapter extends CursorAdapter {
    boolean show_pinyin_color;
    VocabularyCursorClickHandle click_handle;

    public VocabularyCursorAdapter(final Context context, final Cursor cursor, final VocabularyCursorClickHandle clicker) {
        super(context, cursor, true);
        if (cursor != null) {
            chinese_column_index = cursor.getColumnIndex("chinese");
            pinyin_column_index = cursor.getColumnIndex("pinyin");
            english_column_index = cursor.getColumnIndex("english");
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        show_pinyin_color = sp.getBoolean("color_pinyin", true);
        click_handle = clicker;
    }

    private int chinese_column_index;
    private int pinyin_column_index;
    private int english_column_index;

    @Override
    public void changeCursor(@Nullable Cursor cursor) {
        super.changeCursor(cursor);
        if (cursor != null) {
            chinese_column_index = cursor.getColumnIndex("chinese");
            pinyin_column_index = cursor.getColumnIndex("pinyin");
            english_column_index = cursor.getColumnIndex("english");
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View newView = li.inflate(R.layout.details_list_item, viewGroup, false);

        bindView(newView, context, cursor);

        return newView;
    }

    @Override
    public void bindView(final View view, final Context context, final Cursor cursor) {
        final TextView details_chinese = (TextView) view.findViewById(R.id.details_chinese_word);
        details_chinese.setText(cursor.getString(chinese_column_index));
        details_chinese.setTypeface(((AndZiApplication) context.getApplicationContext()).get_chinese_font());
        final TextView details_pinyin = (TextView) view.findViewById(R.id.details_pinyin_word);
        if (show_pinyin_color)
            details_pinyin.setText(PinyinParser.convert_to_spannable_version(cursor.getString(pinyin_column_index), context));
        else
            details_pinyin.setText(PinyinParser.convert_to_pretty_version(cursor.getString(pinyin_column_index)));
        TextView details_translations = (TextView) view.findViewById(R.id.details_translations);
        details_translations.setText(cursor.getString(english_column_index));
        final String chinese = cursor.getString(chinese_column_index);
        if (click_handle != null) {
            view.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    click_handle.click(chinese);
                }
            });
        }
    }
}