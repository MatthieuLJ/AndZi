package com.matthieu;

import android.app.ListActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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

        to_study=new ArrayList<String>();
        data_to_study=new HashMap<String, ArrayList<String>>();

        ArrayAdapter adapter = new ReviewListAdapter();

        new table_filler(adapter).execute();

        setListAdapter(adapter);
    }

    private class table_filler extends AsyncTask<Void, Void, Void> {
        ArrayAdapter adap;
        protected ArrayList<String> temp_to_study;

        public table_filler(ArrayAdapter a) {
            adap=a;
        }

        private void fill_tables() {
            ArrayList<String> chars_to_study = myDB.getAllFilteredToStudy();
            temp_to_study=new ArrayList<String>();
            String pinyin, english;

            for (String chinese: chars_to_study) {
                ArrayList<Vocabulary> voc = myDB.getWordsWithPinyin(chinese);

                for (Vocabulary v:voc) {
                    if ((temp_to_study.contains(v.chinese)) || (to_study.contains(v.chinese)))
                        continue;
                    ArrayList<String> data = new ArrayList<String>();

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
                    data.add(pinyin);
                    data.add(english);

                    temp_to_study.add(v.chinese);
                    data_to_study.put(v.chinese, data);
                    if (temp_to_study.size() > 20) {
                        to_study.addAll(temp_to_study);
                        publishProgress();
                        temp_to_study.clear();
                    }
                }
            }
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
            adap.notifyDataSetChanged();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            fill_tables();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            adap.notifyDataSetChanged();
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
            return data_to_study.get(to_study.get(i));
        }

        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                LayoutInflater li = getLayoutInflater();
                view = li.inflate(R.layout.review_list_item, viewGroup, false);
            }
            TextView chinese = (TextView) view.findViewById(R.id.review_item_chinese);
            chinese.setText(to_study.get(i));

            TextView pinyin = (TextView) view.findViewById(R.id.review_item_pinyin);
            pinyin.setText(PinyinParser.convert_to_pretty_version(data_to_study.get(to_study.get(i)).get(0)));

            TextView english = (TextView) view.findViewById(R.id.review_item_english);
            english.setText(data_to_study.get(to_study.get(i)).get(1));
            
            return view;
        }
    }
}
