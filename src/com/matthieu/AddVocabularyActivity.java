package com.matthieu;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.matthieu.widget.ExtendingLinearLayout;
import java.util.ArrayList;

public class AddVocabularyActivity extends Activity
{
    private static final String TAG="AddVocabularyActivity";

    ArrayList<PinyinChoices> pinyin_choices;
    ArrayList<VocabularyItem> translations_from_ref;
    ArrayList<VocabularyItem> translations_displayed_from_ref;
    ArrayList<VocabularyItem> translations_in_db;
    protected HanziDB myDB=null;
    protected HanziDB refDB=null;

    private String edited_word="";
    private String default_lesson="";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).myDB;
        refDB = ((AndZiApplication) getApplication()).refDB;

        pinyin_choices = new ArrayList<PinyinChoices>();
        translations_from_ref = new ArrayList<VocabularyItem>();
        translations_displayed_from_ref = new ArrayList<VocabularyItem>();
        translations_in_db = new ArrayList<VocabularyItem>();

        setContentView(R.layout.add_vocabulary_top);

        EditText chinese_word = (EditText) findViewById(R.id.chinese);
        chinese_word.addTextChangedListener(new ChineseTextWatcher(chinese_word));
        View.inflate(AddVocabularyActivity.this, R.layout.add_vocabulary_wrong_entry, (ViewGroup) findViewById(R.id.main_content));
    }

    @Override
    protected void onResume() {
        super.onResume();

        // in case those changed...
        myDB = ((AndZiApplication) getApplication()).myDB;
        refDB = ((AndZiApplication) getApplication()).refDB;
    }

    // Handle when the content of the chinese word text field changes
    private class ChineseTextWatcher implements TextWatcher {
        private static final int maximum_text_size=32;
        private static final int minimum_text_size=10;
        private TextView tview;
        private int state; // 0 is empty, 1 is "normal", 2 is "wrong character in the string"
        private boolean need_complete_update=false;

        public ChineseTextWatcher(TextView tv) {
            tview = tv;
            state=-1;
        }

        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            edited_word="";

            // change text size to fit in the box
            adaptTextSize();

            // check if we only have chinese characters (change layout if not)
            checkChineseChars();

            // Populate the choices for the pinyin buttons
            setPinyinButtons(charSequence, i, i1, i2);

            if (state == 1) {
                // populate the default values for the pinyin buttons
                new update_default_pinyins(tview);
            }
        }

        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

        public void afterTextChanged(Editable editable) {}

        private void adaptTextSize() {
            int width = tview.getWidth() - tview.getCompoundPaddingLeft() - tview.getCompoundPaddingRight();
            String text = tview.getText().toString();
            int size=maximum_text_size;

            TextPaint tp = tview.getPaint();
            Rect currentBounds = new Rect ( );

            tview.setTextSize(size);
            tp.getTextBounds((String) text, 0, text.length(), currentBounds );

            while ((currentBounds.right > width) && (size > minimum_text_size)) {
                size--;
                tview.setTextSize(size);
                tp.getTextBounds((String) text, 0, text.length(), currentBounds );
            }
        }

        private void checkChineseChars() {
            String text = tview.getText().toString();
            int len = text.length();
            int new_state;
            if (len == 0) {
                new_state = 0;
            }
            else {
                new_state=1;
                for (int i=0; i<len;) {
                    int entry=text.codePointAt(i);
                    if (((entry < 0x4E00) || (entry > 0x9fff)) // main CJK
                            && ((entry < 0x2e00) || (entry > 0x31ff)) // additional punctuation
                            && ((entry < 0x3400) || (entry > 0x4dbf)) // extension A
                            )
                    {
                        new_state=2;
                        break;
                    }
                    i+=Character.charCount(entry);
                }
            }
            Log.d(TAG, "context going to state "+new_state);

            need_complete_update=false;

            LinearLayout main_content = (LinearLayout) findViewById(R.id.main_content);
            if (new_state != state) {
                switch(new_state) {
                    case 0:
                        // empty string
                        if(state == 1) {
                            main_content.removeAllViews();
                            View.inflate(AddVocabularyActivity.this, R.layout.add_vocabulary_wrong_entry, main_content);
                        }
                        ((TextView) findViewById(R.id.warning_chinese_word)).setText("Enter a Chinese word");
                        break;
                    case 1:
                        // all chinese characters
                        need_complete_update = true;
                        main_content.removeAllViews();
                        View.inflate(AddVocabularyActivity.this, R.layout.add_vocabulary_context, main_content);
                        ListView lv = (ListView) findViewById(R.id.vocabulary_list);
                        //lv.addFooterView();
                        lv.setAdapter(new VocabularyArrayAdapter());
                        break;
                    case 2:
                        // some non-chinese characters...
                        if(state == 1) {
                            main_content.removeAllViews();
                            View.inflate(AddVocabularyActivity.this, R.layout.add_vocabulary_wrong_entry, main_content);
                        }
                        ((TextView) findViewById(R.id.warning_chinese_word)).setText("Only enter chinese characters");
                        break;
                }
                state = new_state;
            }
        }

        private void setPinyinButtons(CharSequence charSequence, int i, int i1, int i2) {
            LinearLayout pinyin_button_container = (LinearLayout) findViewById(R.id.pinyin_buttons);
            if (pinyin_button_container == null) {
                Log.d(TAG, "pinyin_container is null..");
                return;
            }

            if (!need_complete_update) {
                if (i1 > 0) {
                    // remove the ones that were changed
                    for (int j=0; j<i1; j++) {
                        pinyin_choices.remove(i);
                        pinyin_button_container.removeViewAt(i);
                    }
                }
                if (i2 > 0) {
                    for (int j=0; j<i2; j++) {
                        Button b = new Button(AddVocabularyActivity.this);
                        PinyinChoices tmp = new PinyinChoices(charSequence.toString().substring(i+j, i+j+1),
                                myDB, refDB, b);
                        pinyin_choices.add(i + j, tmp);
                        pinyin_button_container.addView(b, i+j,
                                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    }
                }
            }
            else {
                pinyin_choices.clear();
                for (int j=0; j<charSequence.toString().length(); j++) {
                    Button b = new Button(AddVocabularyActivity.this);
                    PinyinChoices tmp = new PinyinChoices(charSequence.toString().substring(j, j+1),
                            myDB, refDB, b);
                    pinyin_choices.add(j, tmp);
                    pinyin_button_container.addView(b, j,
                            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
            }

        }
    }

    private class PinyinChoices extends AsyncTask<Void, Void, Void> {
        private HanziDB local_db;
        private HanziDB ref_db;
        private String hanzi;
        private ArrayList<String> local_pinyins;
        private ArrayList<String> ref_pinyins;

        private String new_pinyin="";
        private String default_choice="";
        private String current_choice="";

        private final static String TAG = "PinyinChoices";
        private Button button;

        public PinyinChoices(String h, HanziDB l_db, HanziDB r_db, Button b) {
            button = b;
            Log.d(TAG, "new button [" + h + "]");
            local_db = l_db;
            ref_db = r_db;
            hanzi = h;
            this.execute((Void[]) null);
        }

        public String get_pinyin(int index) {
            if (new_pinyin.length()==0) {
                if (index < local_pinyins.size())
                    return local_pinyins.get(index);
                if (index < local_pinyins.size() + ref_pinyins.size())
                    return ref_pinyins.get(index-local_pinyins.size());
            }
            else {
                if (index == 0) return new_pinyin;
                if (index <= local_pinyins.size())
                    return local_pinyins.get(index-1);
                if (index <= local_pinyins.size() + ref_pinyins.size())
                    return ref_pinyins.get(index-local_pinyins.size()-1);
            }
            return "";
        }

        public int get_number_pinyins() {
            int res = local_pinyins.size()+ref_pinyins.size();
            if (new_pinyin.equals("")) {
                return res;
            } else {
                return res+1;
            }
        }

        public void set_default_choice(String s) {
            default_choice = s;
            if ((current_choice.length()==0) || (current_choice.equals("?"))) {
                Log.d(TAG, "did not have any current choice, set it to the default: "+s);
                //current_choice=s;
            }
            update_button();
        }
        public void set_current_choice(String s) {
            Log.d(TAG, "pinyin button set current choice"+s);
            current_choice = s;
            if ((!ref_pinyins.contains(s)) && (!local_pinyins.contains(s)))
                new_pinyin=s;
            update_button();
        }
        public String get_current_choice() {
            String res;

            if (current_choice.length()==0) {
                if ((default_choice.length()!=0) && ((local_pinyins.contains(default_choice)) || (ref_pinyins.contains(default_choice)))) {
                    res=default_choice;
                }
                else if ((local_pinyins != null) && (local_pinyins.size()!= 0)) {
                    res = local_pinyins.get(0);
                }
                else if ((ref_pinyins != null) && (ref_pinyins.size() != 0)) {
                    res = ref_pinyins.get(0);
                }
                else {
                    res = "?";
                }
            }
            else {
                res=current_choice;
            }
            return res;
        }
        public String get_hanzi() {
            return hanzi;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (local_db != null)
                local_pinyins = local_db.getPinyinForChar(hanzi);
            else
                local_pinyins = new ArrayList<String>();

            if (ref_db != null)
                ref_pinyins = ref_db.getPinyinForChar(hanzi);
            else
                ref_pinyins = new ArrayList<String>();

            for (int i=ref_pinyins.size()-1; i>=0; i--) {
                if (local_pinyins.contains(ref_pinyins.get(i))) {
                    ref_pinyins.remove(i);
                }
            }
            return null;
        }

        private void update_button() {
            String to_display=get_current_choice();

            button.setText(new Pinyin(to_display).get_pretty());
            button.invalidate();
            button.setOnClickListener(new PinyinButtonClickListener());
            Log.d(TAG, "Set pinyin button with " + to_display);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            update_button();
        }

        private class PinyinButtonClickListener implements View.OnClickListener {
            public void onClick(View view) {
                Dialog dialog = new Dialog(AddVocabularyActivity.this);
                dialog.setTitle("Pick a pinyin for character "+PinyinChoices.this.get_hanzi());
                fill_dialog(dialog);
                dialog.show();
            }

            private void fill_dialog(final Dialog dial) {
                dial.setContentView(R.layout.add_vocabulary_pinyin_dialog);

                final ListView list = (ListView) dial.findViewById(R.id.pinyin_dialog_choices);
                String choices[] = new String[PinyinChoices.this.get_number_pinyins()];
                for (int i=0; i<PinyinChoices.this.get_number_pinyins(); i++) {
                    choices[i] = new Pinyin(PinyinChoices.this.get_pinyin(i)).get_pretty();
                    //Log.d(TAG, "Add pinyin " + choices[i] +" to the list");
                }

                list.setAdapter(new ArrayAdapter<String>(dial.getContext(), R.layout.add_vocabulary_pinyin_dialog_item, choices));
                list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        change_pinyin(new Pinyin(PinyinChoices.this.get_pinyin(i)).get_lame());
                        dial.dismiss();
                    }
                });

                Button OK_button = (Button) dial.findViewById(R.id.edit_new_pinyin_ok);
                OK_button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        EditText new_pinyin = (EditText) dial.findViewById(R.id.edit_new_pinyin);
                        change_pinyin(new Pinyin(new_pinyin.getText().toString()).get_lame());
                        dial.dismiss();
                    }
                });
            }

            private void change_pinyin(String new_pinyin) {
                PinyinChoices.this.set_current_choice(new_pinyin);
                new update_vocabulary_list((TextView) AddVocabularyActivity.this.findViewById(R.id.chinese));
            }
        }
    }

    private class update_default_pinyins extends AsyncTask<Void, Void, Void> {
        ArrayList<String> default_pinyins;
        TextView tview;

        public update_default_pinyins(TextView tv) {
            tview = tv;
            default_pinyins = new ArrayList<String>();
            this.execute();
        }
        
        @Override
        protected void onPostExecute(Void aVoid) {
            int i;

            super.onPostExecute(aVoid);
            for (i=0; i<default_pinyins.size(); i++) {
                if (i >= pinyin_choices.size()) break;
                pinyin_choices.get(i).set_default_choice(default_pinyins.get(i));
            }

            new update_vocabulary_list(tview);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            int i;
            default_pinyins.clear();
            String text = tview.getText().toString();

            // figure out the default pinyin buttons
            ArrayList<Vocabulary> vocs = myDB.getTranslations(text);
            if (refDB != null) {
                vocs.addAll(refDB.getTranslations(text));
            }
            if (vocs.size() == 0)
                return null;

            int max_transl=-1, best_index=0;
            for (i=0; i<vocs.size(); i++) {
                if (vocs.get(i).english.size() > max_transl) best_index=i;
            }
            Log.d(TAG, "Setting default pinyins to "+vocs.get(best_index).pinyin);
            
            default_pinyins = PinyinParser.parse_string(vocs.get(best_index).pinyin);

            return null;
        }
    }

    private class update_vocabulary_list extends AsyncTask<Void, Void, Void> {
        TextView tview;
        public update_vocabulary_list(TextView tv) {
            tview = tv;
            this.execute();
        }
        
        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            hide_duplicates_in_ref(); // this will also notify of the content change
        }

        @Override
        protected Void doInBackground(Void... voids) {
            String text = tview.getText().toString();

            ArrayList<Vocabulary> tr;

            translations_from_ref.clear();
            translations_in_db.clear();

            // figure out the list of translations based on the current pinyin chosen
            String pinyin="";
            for (PinyinChoices pc : pinyin_choices) {
                pinyin = pinyin.concat(pc.get_current_choice());
            }

            tr = myDB.getTranslations(text, pinyin);
            for (Vocabulary t : tr) {
                for (String e : t.english) {
                    translations_in_db.add(new VocabularyItem(e, myDB.getLessonForWord(t.chinese, t.pinyin, e), 1));
                }
            }

            if (refDB != null) {
                tr = refDB.getTranslations(text, pinyin);
                for (Vocabulary t : tr) {
                    for (String e : t.english) {
                        translations_from_ref.add(new VocabularyItem(e, refDB.getLessonForWord(t.chinese, t.pinyin, e), 2));
                    }
                }
            }

            return null;
        }
    }

    private void hide_duplicates_in_ref() {
        translations_displayed_from_ref.clear();
        ArrayList<String> bare_translations = new ArrayList<String>();
        for (VocabularyItem v : translations_in_db) {
            String bare = HanziDB.bare_translation(v.translation);
            if (!bare.equals(""))
                bare_translations.add(bare);
        }
        for (VocabularyItem v : translations_from_ref) {
            if (!bare_translations.contains(HanziDB.bare_translation(v.translation)))
                translations_displayed_from_ref.add(v);
        }

        ListView lv = (ListView) findViewById(R.id.vocabulary_list);
        if (lv != null) {
            ((VocabularyArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
        }
    }

    private class VocabularyItem {
        public String translation;
        public ArrayList<String> lessons;
        public boolean enabled; // true if included, false if excluded
        public int state; // 1 from local database; 2 in ref DB; 3 if edited; 4 if new
        public boolean visible; // if the view is expanded

        public VocabularyItem(String tr, ArrayList<String > ls, int from) {
            translation=tr;
            lessons=ls;
            state=from;
            if ((state == 1) || (state == 3) || (state == 4))
                enabled=true;
            else
                enabled=false;
            visible=false;
        }
    }

    private class VocabularyArrayAdapter extends ArrayAdapter<Object> {
        private int my=0, ref=0;

        public VocabularyArrayAdapter() {
            super(AddVocabularyActivity.this, R.layout.add_vocabulary_list_item, translations_in_db.toArray());
        }

        @Override
        public int getCount() {
            int res = 0;

            my = translations_in_db.size();
            res = my;
            if (my != 0)
                res ++;

            ref = translations_displayed_from_ref.size();
            res += ref;
            if (ref != 0)
               res ++;

            return res;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row;

            if ((position == 0) || ((my != 0) && (position == my+1))) {
                // it is a title view...
                if (convertView == null) {
                    LayoutInflater inflater = getLayoutInflater();
                    row = inflater.inflate(R.layout.add_vocabulary_list_title, parent, false);
                    row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_title);
                }
                else {
                    if ((Integer)convertView.getTag(R.id.add_list_view_inflated)==R.layout.add_vocabulary_list_title)
                        row = convertView;
                    else  {
                        LayoutInflater inflater = getLayoutInflater();
                        row = inflater.inflate(R.layout.add_vocabulary_list_title, parent, false);
                        row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_title);
                    }
                }

                TextView title = (TextView) row.findViewById(R.id.add_title);
                if ((position==0) && (my != 0))
                    title.setText("Vocabulary from your DB");
                else
                    title.setText("Vocabulary from ref DB");

                return row;
            }

            if (convertView == null) {
                LayoutInflater inflater = getLayoutInflater();
                row = inflater.inflate(R.layout.add_vocabulary_list_item, parent, false);
                row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_item);
            }
            else {
                if ((Integer)convertView.getTag(R.id.add_list_view_inflated)==R.layout.add_vocabulary_list_item)
                    row = convertView;
                else  {
                    LayoutInflater inflater = getLayoutInflater();
                    row = inflater.inflate(R.layout.add_vocabulary_list_item, parent, false);
                    row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_item);
                }
            }

            VocabularyItem voc;

            if ((my != 0) && (position<=my+1)) {

                // part of the local DB

                voc = translations_in_db.get(position-1);

                if (convertView == null) {
                    LayoutInflater inflater = getLayoutInflater();
                    row = inflater.inflate(R.layout.add_vocabulary_list_item, parent, false);
                    row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_item);
                }
                else {
                    if ((Integer)convertView.getTag(R.id.add_list_view_inflated)==R.layout.add_vocabulary_list_item)
                        row = convertView;
                    else  {
                        LayoutInflater inflater = getLayoutInflater();
                        row = inflater.inflate(R.layout.add_vocabulary_list_item, parent, false);
                        row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_item);
                    }
                }
                ExtendingLinearLayout el = (ExtendingLinearLayout) row.findViewById(R.id.add_vocabulary_info);
                Button bt = (Button) row.findViewById(R.id.add_expand_button);
                if (el!=null) {
                    if(voc.visible) {
                        el.expand_immediate();
                        bt.setBackgroundResource(R.drawable.expand_arrow_down);
                        bt.setTag(R.id.add_expanded, "1");
                    }
                    else {
                        el.collapse_immediate();
                        bt.setBackgroundResource(R.drawable.expand_arrow_right);
                        bt.setTag(R.id.add_expanded, "0");
                    }
                }
                bt.setTag(R.id.add_view_to_expand, el);
                bt.setTag(R.id.add_vocabulary_item, voc);

                TextView more = (TextView) row.findViewById(R.id.add_more);
                String lessons = new String();
                if (voc.lessons.size() != 0) {
                    for (String s : voc.lessons) {
                        lessons=lessons.concat(s);
                        lessons=lessons.concat("\n");
                    }
                    lessons=lessons.concat("Edit lessons");
                }
                else {
                    lessons = "Assign to lesson";
                }
                more.setText(lessons);
                more.setTag(R.id.add_vocabulary_item, voc);
                more.setOnClickListener(new LessonEdit());
            }
            else  {

                // in the ref DB

                if (my != 0) {
                    position -= my + 1;
                }
                voc = translations_displayed_from_ref.get(position-1);

                if (convertView == null) {
                    LayoutInflater inflater = getLayoutInflater();
                    row = inflater.inflate(R.layout.add_vocabulary_list_ref_item, parent, false);
                    row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_ref_item);
                }
                else {
                    if ((Integer)convertView.getTag(R.id.add_list_view_inflated)==R.layout.add_vocabulary_list_ref_item)
                        row = convertView;
                    else  {
                        LayoutInflater inflater = getLayoutInflater();
                        row = inflater.inflate(R.layout.add_vocabulary_list_ref_item, parent, false);
                        row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_ref_item);
                    }
                }
            }

            TextView word = (TextView) row.findViewById(R.id.add_word);
            word.setText(voc.translation);
            switch(voc.state) {
                case 1: // local database
                    word.setTypeface(null, Typeface.NORMAL);
                    break;
                case 2: // reference database
                    word.setTypeface(null, Typeface.ITALIC);
                    break;
                case 3: // edited word
                case 4: // new word
                    word.setTypeface(null, Typeface.BOLD);
                    break;

            }
            word.setTag(R.id.add_vocabulary_item, voc);

            CheckBox cb = (CheckBox) row.findViewById(R.id.add_check);
            cb.setTag(R.id.add_vocabulary_item, voc);
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    VocabularyItem voc = (VocabularyItem) compoundButton.getTag(R.id.add_vocabulary_item);
                    if ((voc.state == 2) && (b)) { // we clicked an item from the reference database
                        ArrayList<String> lesson = new ArrayList<String>();
                        if (!(default_lesson.equals(""))) {
                            lesson.add(default_lesson);
                        }
                        VocabularyItem item = new VocabularyItem(voc.translation, lesson, 4);
                        item.enabled=true;
                        translations_in_db.add(item);
                        hide_duplicates_in_ref();
                        return;
                    }
                    voc.enabled=b;
                    Log.d(TAG, "translation "+voc.translation+" is changed");
                }
            });
            cb.setChecked(voc.enabled);
            
            return row;
        }

        private class LessonEdit implements View.OnClickListener {
            VocabularyItem voc;
            ArrayList<EnabledLessons> lesson_list;

            private class EnabledLessons {
                public String lesson;
                public boolean enabled;
            }

            public void onClick(View view) {
                Dialog dialog = new Dialog(AddVocabularyActivity.this);
                voc = (VocabularyItem) view.getTag(R.id.add_vocabulary_item);
                dialog.setTitle("Choose the lessons for word "+voc.translation);
                lesson_list = new ArrayList<EnabledLessons>();
                fill_dialog(dialog);
                dialog.show();
            }

            private void fill_dialog(final Dialog dial) {
                dial.setContentView(R.layout.add_vocabulary_lesson_dialog);

                final ListView list = (ListView) dial.findViewById(R.id.lesson_dialog_choices);

                for (String l : myDB.getLessons()) {
                    EnabledLessons elesson = new EnabledLessons();
                    elesson.lesson=l;
                    elesson.enabled=false;
                    for (String vl : voc.lessons) {
                        if (vl.equals(l)) {
                            elesson.enabled=true;
                            break;
                        }
                    }

                    lesson_list.add(elesson);
                }

                list.setAdapter(new LessonArrayAdapter());
                ((LessonArrayAdapter)list.getAdapter()).notifyDataSetChanged();

                Button OK_button = (Button) dial.findViewById(R.id.lesson_ok);
                OK_button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        voc.lessons.clear();
                        for (EnabledLessons el : lesson_list) {
                            if (el.enabled) {
                                voc.lessons.add(el.lesson);
                            }
                        }
                        dial.dismiss();
                        ListView lv = (ListView) findViewById(R.id.vocabulary_list);
                        ((VocabularyArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
                    }
                });
                Button Cancel_button = (Button) dial.findViewById(R.id.lesson_cancel);
                Cancel_button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        dial.dismiss();
                    }
                });
            }

            private class LessonArrayAdapter extends ArrayAdapter<Object> {

                public LessonArrayAdapter() {
                    super(AddVocabularyActivity.this, R.layout.add_vocabulary_lesson_dialog, lesson_list.toArray());
                }

                @Override
                public int getCount() {
                    return lesson_list.size();
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View row;
                    if (convertView == null) {
                        LayoutInflater inflater = getLayoutInflater();
                        row = inflater.inflate(R.layout.add_vocabulary_lesson_dialog_item, parent, false);
                    }
                    else {
                        row = convertView;
                    }

                    Log.d(TAG, "Checking position "+position);
                    EnabledLessons lesson = lesson_list.get(position);

                    final TextView tv = (TextView) row.findViewById(R.id.lesson_name);
                    tv.setText(lesson.lesson);

                    final CheckBox cb = (CheckBox) row.findViewById(R.id.lesson_check);
                    cb.setTag(R.id.add_vocabulary_lesson, lesson);
                    cb.setChecked(lesson.enabled);
                    cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                            EnabledLessons lesson = (EnabledLessons) compoundButton.getTag(R.id.add_vocabulary_lesson);
                            lesson.enabled = b;
                        }
                    });

                    return row;
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.add_vocabulary_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.add_vocabulary_set_default_lesson:
                set_default_lesson();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void set_default_lesson() {
        Dialog dialog = new Dialog(AddVocabularyActivity.this);
        dialog.setTitle("Choose the default lesson");
        fill_default_lesson_dialog(dialog);
        dialog.show();
    }

    private void fill_default_lesson_dialog(final Dialog dialog) {
        dialog.setContentView(R.layout.add_vocabulary_default_lesson_dialog);
        ArrayList<String> lessons = myDB.getLessons();
        lessons.add(0, "(none)");

        ListView list = (ListView) dialog.findViewById(R.id.default_lesson_list);
        ListAdapter adapter = new DefaultLessonArrayAdapter(lessons);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == 0) {
                    default_lesson = "";
                } else {
                    default_lesson = myDB.getLessons().get(i - 1);
                }
                //((DefaultLessonArrayAdapter) ((ListView) adapterView).getAdapter()).notifyDataSetChanged();
                dialog.dismiss();
            }
        });

        Button cancel_button = (Button) dialog.findViewById(R.id.default_lesson_cancel);
        cancel_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
    }

    private class DefaultLessonArrayAdapter extends ArrayAdapter<String> {
        ArrayList<String> lessons;
        public DefaultLessonArrayAdapter(ArrayList<String> le) {
            super(AddVocabularyActivity.this, android.R.layout.simple_list_item_single_choice, le);
            lessons = le;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CheckedTextView view;
            if (convertView == null) {
                LayoutInflater inflater = getLayoutInflater();
                view = (CheckedTextView) inflater.inflate(R.layout.add_vocabulary_default_lesson_dialog_item, parent, false);
            }
            else {
                view = (CheckedTextView) convertView;
            }
            view.setText(lessons.get(position));

            if ((position == 0) && (default_lesson.equals(""))) {
                view.setChecked(true);
            }
            else if ((position > 0) && (default_lesson.equals(lessons.get(position)))) {
                view.setChecked(true);
            }
            else {
                view.setChecked(false);
            }
            return view;
        }
    }

    public void expand(View v) {
        String tag = (String) v.getTag(R.id.add_expanded);
        VocabularyItem voc = (VocabularyItem) v.getTag(R.id.add_vocabulary_item);
        if (tag.equals("0")) {
            v.setBackgroundResource(R.drawable.expand_arrow_down);
            ExtendingLinearLayout el = (ExtendingLinearLayout) v.getTag(R.id.add_view_to_expand);
            if (el!=null)
                el.expand();
            v.setTag(R.id.add_expanded, "1");
            voc.visible=true;
        }
        else {
            v.setBackgroundResource(R.drawable.expand_arrow_right);
            ExtendingLinearLayout el = (ExtendingLinearLayout) v.getTag(R.id.add_view_to_expand);
            if (el!=null)
                el.collapse();
            v.setTag(R.id.add_expanded, "0");
            voc.visible=false;
        }
    }

    public void handle_word_click(View v) {
        TextView tview = (TextView) v;
        VocabularyItem voc = (VocabularyItem) tview.getTag(R.id.add_vocabulary_item);

        ((EditText) findViewById(R.id.edit_new_vocabulary)).setText(tview.getText());

        if ((voc.state == 1) || (voc.state == 3) || (voc.state == 4)) {
            edited_word=tview.getText().toString();
            ((TextView) findViewById(R.id.new_vocabulary_label)).setText("Edit word:");
        }
        else {
            edited_word = "";
            ((TextView) findViewById(R.id.new_vocabulary_label)).setText("New word:");
        }
        ((EditText) findViewById(R.id.edit_new_vocabulary)).requestFocus();
    }

    public void handle_edit_lesson(View v) {
        VocabularyItem voc = (VocabularyItem) v.getTag(R.id.add_vocabulary_item);
    }

    public void handle_add_vocabulary(View v) {
        String new_word= ((EditText) findViewById(R.id.edit_new_vocabulary)).getText().toString();

        // empty string...
        if (new_word.equals("")) {
            if (edited_word.equals("")) {
                ((TextView) findViewById(R.id.new_vocabulary_label)).setText("New word:");
                return;
            }
        }

        // check it is not already in the current database
        for(int i=translations_in_db.size()-1; i>=0; i-- ) {
            VocabularyItem voc = translations_in_db.get(i);
            // remove anything from the reference database that would be similar...
            if (HanziDB.bare_translation(voc.translation).equals(HanziDB.bare_translation(new_word))) {
                translations_in_db.remove(i);
            } else if (voc.translation.equals(edited_word)) {
                translations_in_db.remove(i);
            }
        }
        if (edited_word.equals("")) {
            ArrayList<String> lesson = new ArrayList<String>();
            if (!(default_lesson.equals(""))) {
                lesson.add(default_lesson);
            }
            translations_in_db.add(0, new VocabularyItem(new_word, lesson, 4));
        }
        else {
            translations_in_db.add(0, new VocabularyItem(new_word, new ArrayList<String>(), 3));
        }

        hide_duplicates_in_ref();
        
        ((EditText) findViewById(R.id.edit_new_vocabulary)).setText("");
        ((TextView) findViewById(R.id.new_vocabulary_label)).setText("New word:");
    }

    public void handle_cancel(View v) {
        EditText chinese_word = (EditText) findViewById(R.id.chinese);
        chinese_word.setText("");

        translations_in_db.clear();
        translations_from_ref.clear();
        translations_displayed_from_ref.clear();
    }

    public void handle_ok(View v) {
        String chinese = ((EditText) findViewById(R.id.chinese)).getText().toString();
        String pinyin = "";
        for (PinyinChoices pc : pinyin_choices) {
            pinyin = pinyin.concat(pc.get_current_choice());
        }
        ArrayList<String> translations=new ArrayList<String>();
        for (VocabularyItem voc: translations_in_db) {
            if (voc.enabled) {
                translations.add(voc.translation);
            }
        }
        myDB.delVocabulary(new Vocabulary(chinese, pinyin));
        myDB.addVocabulary(new Vocabulary(chinese, pinyin, translations));

        for (VocabularyItem voc: translations_in_db) {
            for (String lesson : voc.lessons) {
                myDB.assignWordLesson(lesson, voc.translation,chinese,pinyin);
            }
        }

        EditText chinese_word = (EditText) findViewById(R.id.chinese);
        chinese_word.setText("");

        translations_in_db.clear();
        translations_from_ref.clear();
        translations_displayed_from_ref.clear();
    }
}
