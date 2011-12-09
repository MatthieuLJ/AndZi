package com.matthieu;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class ReviewList extends ListActivity {
    protected HanziDB myDB=null;
    protected ArrayList<String> to_study;
    protected HashMap<String, ArrayList<String>> data_to_study;

    private static final String TAG="ReviewList";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).myDB;
        fill_tables();

        ListAdapter adapter = new ReviewListAdapter();

        setListAdapter(adapter);
    }

    private void fill_tables() {
        to_study = myDB.getAllFilteredToStudy();
        data_to_study = new HashMap<String, ArrayList<String>>();
        String pinyin, english;

        for (String chinese: to_study) {
            ArrayList<String> data = new ArrayList<String>();
            ArrayList<Vocabulary> voc = myDB.getWordsWithPinyin(chinese);

            for (Vocabulary v:voc) {
                pinyin = new String();
                english = new String();
                if (!pinyin.equals("")) {
                    pinyin += " / ";
                    english += " / ";
                }
                pinyin+=v.pinyin;
                for (int i=0; i<v.english.size(); i++) {
                    if (i!=0) english+=", ";
                    english+=v.english.get(i);
                }
                data.add(v.chinese);
                data.add(pinyin);
                data.add(english);

                data_to_study.put(chinese, data);
            }
        }

    }

    private class ReviewListAdapter extends ArrayAdapter<Object> {

        public ReviewListAdapter() {
            super(ReviewList.this, R.layout.review_list_item, to_study.toArray());
        }

        public int getCount() {
            return to_study.size();
        }

        public Object getItem(int i) {
            return to_study.get(i);
        }

        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                LayoutInflater li = getLayoutInflater();
                view = li.inflate(R.layout.review_list_item, viewGroup, false);
            }
            TextView chinese = (TextView) view.findViewById(R.id.review_item_chinese);
            chinese.setText(data_to_study.get(to_study.get(i)).get(0));

            TextView pinyin = (TextView) view.findViewById(R.id.review_item_pinyin);
            pinyin.setText(PinyinParser.convert_to_pretty_version(data_to_study.get(to_study.get(i)).get(1)));

            TextView english = (TextView) view.findViewById(R.id.review_item_english);
            english.setText(data_to_study.get(to_study.get(i)).get(2));
            
            return view;
        }
    }
}
