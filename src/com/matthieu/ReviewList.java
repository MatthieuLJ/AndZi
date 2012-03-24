package com.matthieu;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.*;
import android.widget.*;
import com.matthieu.widget.ExtendingLinearLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class ReviewList extends ListActivity {
    protected HanziDB myDB=null;
    protected ArrayList<CharPinyin> to_study;
    protected HashMap<CharPinyin, ArrayList<Vocabulary>> data_to_study;

    private static final String TAG="ReviewList";
    private boolean show_pinyin_color;

    private String lesson=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();

        to_study=new ArrayList<CharPinyin>();
        data_to_study=new HashMap<CharPinyin, ArrayList<Vocabulary>>();

        lesson=getIntent().getStringExtra("lesson");
        if (lesson == null) {
            BaseAdapter adapter = new ReviewListAdapter();
            new table_filler(adapter).execute();
            LayoutInflater inflater = getLayoutInflater();
            TextView header = (TextView) inflater.inflate(R.layout.review_list_header, getListView(), false);

            int expired = myDB.getNumCharsInBox(0) + myDB.getNumCharsExpiredInBox(1) +
                    myDB.getNumCharsExpiredInBox(2) + myDB.getNumCharsExpiredInBox(3) + myDB.getNumCharsExpiredInBox(4);
            header.setText("You have "+ expired +" characters to study");
            getListView().addHeaderView(header, null, false);
            setListAdapter(adapter);
        }
        else {
            BaseAdapter adapter = new ReviewLessonAdapter();
            setListAdapter(adapter);
            
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ReviewList.this);
        show_pinyin_color = sp.getBoolean("color_pinyin", true);
    }

    private class CharPinyin {
        public String character=null;
        public String pinyin=null;
        public boolean expanded=false;
        public boolean equals(CharPinyin other) {
            return ((other.character.equals(character)) && (other.pinyin.equals(pinyin)));
        }
    }

    private class table_filler extends AsyncTask<Void, Integer, Void> {
        BaseAdapter adap;
        ArrayList<CharPinyin> temp_to_study;
        ArrayList<String> chars_to_study;

        public table_filler(BaseAdapter a) {
            adap=a;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            data_to_study.clear();
            to_study.clear();

            chars_to_study = myDB.getAllFilteredToStudy();
        }

        private void fill_tables() {
            temp_to_study=new ArrayList<CharPinyin>();

            for (int i=0; i<chars_to_study.size(); i++) {
                String chinese = chars_to_study.get(i);
                ArrayList<String> pinyins = myDB.getPinyinForChar(chinese);
                for (String pin:pinyins) {
                    Pinyin this_pinyin=new Pinyin(pin);
                    if (this_pinyin.get_tone() == 5) {
                        // no tone, check if there is another one with the same radical
                        int count=0;
                        for (String other_pin : pinyins) {
                            if (other_pin.equals(pin))
                                continue;
                            Pinyin other_pinyin=new Pinyin(other_pin);
                            if (other_pinyin.get_radical().equals(this_pinyin.get_radical()))
                                count++;
                        }
                        if (count == 1)
                            continue;
                    }

                    CharPinyin charpinyin = new CharPinyin();
                    charpinyin.character=chinese;
                    charpinyin.pinyin=pin;

                    temp_to_study.add(charpinyin);
                    ArrayList<Vocabulary> data = myDB.getWordsWithPinyin(chinese, pin);
                    if (this_pinyin.get_tone() != 5) {
                        // check if there was one that needed to be combined
                        int combine=0;
                        for (String other_pin : pinyins) {
                            if (other_pin.equals(pin))
                                continue;
                            Pinyin other_pinyin=new Pinyin(other_pin);
                            if (other_pinyin.get_radical().equals(this_pinyin.get_radical())) {
                                if ((other_pinyin.get_tone() == 5) && (combine == 0))
                                    combine = 1;
                                if (other_pinyin.get_tone() != 5) {
                                    combine = -1;
                                    break;
                                }

                            }
                        }
                        if (combine == 1) {
                            data.addAll(myDB.getWordsWithPinyin(chinese,this_pinyin.get_radical()+"5"));
                            // check there is no duplicate
                            ArrayList<String> chinese_words=new ArrayList<String>();
                            for (int j=data.size()-1; j>=0; j--) {
                                Vocabulary v = data.get(j);
                                if (chinese_words.contains(v.chinese))
                                    data.remove(j);
                                else
                                    chinese_words.add(v.chinese);
                            }
                        }
                    }

                    data_to_study.put(charpinyin, data);
                }
                if (temp_to_study.size() > 20) {
                    to_study.addAll(temp_to_study);
                    publishProgress(i);
                    temp_to_study.clear();
                }
            }
            to_study.addAll(temp_to_study);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
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
            if (to_study.size()==0) {
                AlertDialog.Builder no_words_dialog = new AlertDialog.Builder(ReviewList.this);
                no_words_dialog.setCancelable(false);
                no_words_dialog.setMessage("Nothing to study");
                no_words_dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                        finish();
                    }
                });
                no_words_dialog.create();
                no_words_dialog.show();
            }
            else {
                adap.notifyDataSetChanged();
            }
        }
    }

    private class ReviewListAdapter extends BaseAdapter {

        public ReviewListAdapter() {
            super();
        }

        public int getCount() {
            return to_study.size();
        }

        public Object getItem(int i) {
            return data_to_study.get(to_study.get(i));
        }

        public long getItemId(int i) {
            return 0;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public View getView(int i, View convertView, ViewGroup viewGroup) {
            LayoutInflater li = getLayoutInflater();
            if (convertView == null) {
                convertView = li.inflate(R.layout.review_list_item, viewGroup, false);
            }
            TextView chinese = (TextView) convertView.findViewById(R.id.review_item_character);
            final String hanzi = to_study.get(i).character;
            chinese.setText(hanzi);
            chinese.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    Intent intent=new Intent(ReviewList.this, Details.class);
                    intent.putExtra("hanzi", hanzi);
                    startActivity(intent);
                }
            });
            TextView pinyin = (TextView) convertView.findViewById(R.id.review_item_pinyin);
            if (show_pinyin_color)
                pinyin.setText(Html.fromHtml(PinyinParser.convert_to_html_version(to_study.get(i).pinyin)));
            else
                pinyin.setText(PinyinParser.convert_to_pretty_version(to_study.get(i).pinyin));

            Button arrow = (Button) convertView.findViewById(R.id.review_expand_button);
            ExtendingLinearLayout words = (ExtendingLinearLayout) convertView.findViewById(R.id.review_item_words);
            if (to_study.get(i).expanded) {
                words.expand_immediate();
                arrow.setBackgroundResource(R.drawable.expand_arrow_down);
                arrow.setTag(R.id.review_expanded, "1");
            }
            else {
                words.collapse_immediate();
                arrow.setBackgroundResource(R.drawable.expand_arrow_right);
                arrow.setTag(R.id.review_expanded, "0");
            }
            arrow.setTag(R.id.review_view_to_expand, words);
            arrow.setTag(R.id.review_item, to_study.get(i));

            TableLayout word_table = (TableLayout) convertView.findViewById(R.id.review_item_word_table);
            word_table.removeAllViews();
            for (Vocabulary voc : data_to_study.get(to_study.get(i))) {
                TableRow row = (TableRow) li.inflate(R.layout.review_item_word_row, word_table, false);
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
                        Intent intent=new Intent(ReviewList.this, Details.class);
                        intent.putExtra("hanzi", chinese_label);
                        startActivity(intent);
                    }
                });
                word_table.addView(row);
            }
            
            return convertView;
        }
    }

    public void expand(View v) {
        String tag = (String) v.getTag(R.id.review_expanded);
        CharPinyin charpin = (CharPinyin) v.getTag(R.id.review_item);

        if (tag.equals("0")) {
            v.setBackgroundResource(R.drawable.expand_arrow_down);
            ExtendingLinearLayout el = (ExtendingLinearLayout) v.getTag(R.id.review_view_to_expand);
            if (el!=null)
                el.expand();
            v.setTag(R.id.review_expanded, "1");
            charpin.expanded=true;
        }
        else {
            v.setBackgroundResource(R.drawable.expand_arrow_right);
            ExtendingLinearLayout el = (ExtendingLinearLayout) v.getTag(R.id.review_view_to_expand);
            if (el!=null)
                el.collapse();
            v.setTag(R.id.review_expanded, "0");
            charpin.expanded=false;
        }
    }

    private class ReviewLessonAdapter extends BaseAdapter {
        ArrayList<Vocabulary> lesson_voc;
        public ReviewLessonAdapter() {
            super();
            lesson_voc=myDB.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(lesson)));
        }

        public int getCount() {
            return lesson_voc.size();
        }

        public Object getItem(int i) {
            return null;
        }

        public long getItemId(int i) {
            return 0;
        }

        public View getView(int position, View convertView, ViewGroup viewGroup) {
            LayoutInflater li = getLayoutInflater();
            if (convertView == null) {
                convertView = li.inflate(R.layout.details_list_item, viewGroup, false);
            }
            final Vocabulary voc=lesson_voc.get(position);
            TextView chinese = (TextView) convertView.findViewById(R.id.details_chinese_word);
            chinese.setText(voc.chinese);
            TextView pinyin = (TextView) convertView.findViewById(R.id.details_pinyin_word);
            if (show_pinyin_color)
                pinyin.setText(Html.fromHtml(PinyinParser.convert_to_html_version(voc.pinyin)));
            else
                pinyin.setText(PinyinParser.convert_to_pretty_version(voc.pinyin));
            TextView translation = (TextView) convertView.findViewById(R.id.details_translations);
            String trans="";
            for (int j=0; j<voc.english.size();j++) {
                trans+=voc.english.get(j);
                if (j<voc.english.size()-1)
                    trans+=", ";
            }
            translation.setText(trans);
            convertView.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    Intent intent=new Intent(ReviewList.this, Details.class);
                    intent.putExtra("hanzi", voc.chinese);
                    startActivity(intent);
                }
            });

            return convertView;
        }
    }
}
