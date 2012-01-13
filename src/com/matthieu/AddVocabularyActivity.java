package com.matthieu;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.matthieu.widget.EditTextAutomaticSize;
import com.matthieu.widget.ExtendingLinearLayout;
import java.util.ArrayList;

public class AddVocabularyActivity extends Activity
{
    private static final String TAG="AddVocabularyActivity";

    ArrayList<PinyinButton> pinyin_choices;
    ArrayList<VocabularyItem> translations_from_ref;
    ArrayList<VocabularyItem> translations_displayed_from_ref;
    ArrayList<VocabularyItem> translations_in_db;
    protected HanziDB myDB=null;
    protected HanziDB refDB=null;

    private String default_lesson="";
    private String current_chinese_word="";
    private Handler process_handler=null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pinyin_choices = new ArrayList<PinyinButton>();
        translations_from_ref = new ArrayList<VocabularyItem>();
        translations_displayed_from_ref = new ArrayList<VocabularyItem>();
        translations_in_db = new ArrayList<VocabularyItem>();
        process_handler = new Handler();

        setContentView(R.layout.add_vocabulary_top);

        EditTextAutomaticSize chinese_word = (EditTextAutomaticSize) findViewById(R.id.add_vocabulary_chinese);
        ChineseTextWatcher text_watcher = new ChineseTextWatcher(chinese_word);
        chinese_word.addTextChangedListener(text_watcher);
        chinese_word.setMaximumTextSize(32);
        chinese_word.setMinimumTextSize(10);
        View.inflate(AddVocabularyActivity.this, R.layout.add_vocabulary_wrong_entry, (ViewGroup) findViewById(R.id.main_content));

        String hanzi = getIntent().getStringExtra("Hanzi");
        if (hanzi!= null) {
            chinese_word.setText(hanzi);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // in case those changed...
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        refDB = ((AndZiApplication) getApplication()).getRefDB();
    }

    // Handle when the content of the chinese word text field changes
    private class ChineseTextWatcher implements TextWatcher {

        private EditTextAutomaticSize tview;
        private int state; // 0 is empty, 1 is "normal", 2 is "wrong character in the string"
        private boolean need_complete_update=false;

        public ChineseTextWatcher(EditTextAutomaticSize tv) {
            tview = tv;
            state=-1;
        }

        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            // change text size to fit in the box
            tview.adaptTextSize();

            // check if we only have chinese characters (change layout if not)
            checkChineseChars();

            if (state == 1) {
                current_chinese_word = ((EditTextAutomaticSize) findViewById(R.id.add_vocabulary_chinese)).getText().toString();

                // Populate the choices for the pinyin buttons
                setPinyinButtons(charSequence, i, i1, i2);

                // populate the default values for the pinyin buttons
                process_handler.post(new update_default_pinyins());
            }
        }

        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

        public void afterTextChanged(Editable editable) {}

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
                        b.setBackgroundColor(0x00000000);
                        b.setTextColor(0xff000000);
                        b.setBackgroundResource(0);
                        PinyinButton tmp = new PinyinButton(charSequence.toString().substring(i+j, i+j+1), b);
                        process_handler.post(new PinyinButtonFiller(tmp));
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
                    b.setBackgroundColor(0x00000000);
                    b.setTextColor(0xff000000);
                    b.setBackgroundResource(0);
                    PinyinButton tmp = new PinyinButton(charSequence.toString().substring(j, j+1), b);
                    process_handler.post(new PinyinButtonFiller(tmp));
                    pinyin_choices.add(j, tmp);
                    pinyin_button_container.addView(b, j,
                            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
            }
        }
    }

    private class PinyinButtonFiller implements Runnable {
        PinyinButton button;
        public PinyinButtonFiller(PinyinButton b) {
            button=b;
        }

        public void run() {
            String hanzi=button.get_hanzi();
            ArrayList<String> res=new ArrayList<String>();
            if (myDB != null)
                res.addAll(myDB.getPinyinForChar(hanzi));

            if (refDB != null) {
                for (String pin : refDB.getPinyinForChar(hanzi)) {
                    if (!res.contains(pin))
                        res.add(pin);
                }
            }
            button.set_db_pinyins(res);
        }
    }

    private class PinyinButton {
        private String hanzi;
        private ArrayList<String> db_pinyins;

        private String new_pinyin="";
        private String default_choice="";
        private String current_choice="";

        private final static String TAG = "PinyinButton";
        private Button button;

        public PinyinButton(String h, Button b) {
            button = b;
            hanzi = h;
            db_pinyins=new ArrayList<String>();
        }

        public String get_pinyin(int index) {
            if (new_pinyin.length()==0) {
                if (index < db_pinyins.size())
                    return db_pinyins.get(index);
            }
            else {
                if (index == 0) return new_pinyin;
                if (index <= db_pinyins.size())
                    return db_pinyins.get(index-1);
            }
            return "";
        }

        public int get_number_pinyins() {
            int res = db_pinyins.size();
            if (new_pinyin.equals("")) {
                return res;
            } else {
                return res+1;
            }
        }

        public void set_default_choice(String s) {
            default_choice = s;
            update_button();
        }

        public void set_current_choice(String s) {
            Log.d(TAG, "pinyin button set current choice"+s);
            current_choice = s;
            if (!db_pinyins.contains(s))
                new_pinyin=s;
            update_button();
        }

        public String get_current_choice() {
            String res;

            if (current_choice.length()==0) {
                if ((!default_choice.equals("")) && (db_pinyins.contains(default_choice))) {
                    res=default_choice;
                }
                else if (db_pinyins.size()!= 0) {
                    res = db_pinyins.get(0);
                }
                else {
                    res = "";
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

        public void set_db_pinyins(ArrayList<String> pinyins) {
            db_pinyins=pinyins;
            update_button();
        }

        private void update_button() {
            String to_display=get_current_choice();
            Pinyin pinyin = new Pinyin(to_display);
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(AddVocabularyActivity.this);
            boolean show_color = sp.getBoolean("color_pinyin", true);

            if (to_display.equals("")) {
                button.setText("-?-");
            }
            else {
                if (show_color) {
                    button.setText(Html.fromHtml(pinyin.get_color()));
                }
                else {
                    button.setText(pinyin.get_pretty());
                }
            }
            button.invalidate();
            button.setOnClickListener(new PinyinButtonClickListener());

            process_handler.post(new update_vocabulary_list());
        }


        private class PinyinButtonClickListener implements View.OnClickListener {
            public void onClick(View view) {
                Dialog dialog = new Dialog(AddVocabularyActivity.this);
                dialog.setTitle("Pick a pinyin for character "+PinyinButton.this.get_hanzi());
                fill_dialog(dialog);
                dialog.show();
            }

            private void fill_dialog(final Dialog dial) {
                dial.setContentView(R.layout.add_vocabulary_pinyin_dialog);

                final ListView list = (ListView) dial.findViewById(R.id.pinyin_dialog_choices);
                String choices[] = new String[PinyinButton.this.get_number_pinyins()];
                for (int i=0; i<PinyinButton.this.get_number_pinyins(); i++) {
                    choices[i] = new Pinyin(PinyinButton.this.get_pinyin(i)).get_pretty();
                    //Log.d(TAG, "Add pinyin " + choices[i] +" to the list");
                }

                list.setAdapter(new ArrayAdapter<String>(dial.getContext(), R.layout.add_vocabulary_pinyin_dialog_item, choices));
                list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        change_pinyin(new Pinyin(PinyinButton.this.get_pinyin(i)).get_lame());
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
                PinyinButton.this.set_current_choice(new_pinyin);
            }
        }
    }

    private class update_default_pinyins implements Runnable {
        public void run() {
            int i;
            ArrayList<String>  default_pinyins;

            // figure out the default pinyin buttons
            ArrayList<Vocabulary> vocs = myDB.getTranslations(current_chinese_word);
            if ((refDB != null) && (vocs.size() == 0)){
                vocs= refDB.getTranslations(current_chinese_word);
            }
            if (vocs.size() == 0)
                return;

            int max_transl=0, best_index=0;
            for (i=0; i<vocs.size(); i++) {
                if (vocs.get(i).english.size() > max_transl) {
                    best_index=i;
                    max_transl = vocs.get(i).english.size();
                }
            }
            Log.d(TAG, "Setting default pinyins to "+vocs.get(best_index).pinyin);
            
            default_pinyins = PinyinParser.parse_string(vocs.get(best_index).pinyin);

            for (i=0; i<default_pinyins.size(); i++) {
                if (i >= pinyin_choices.size()) break;
                pinyin_choices.get(i).set_default_choice(default_pinyins.get(i));
            }
        }
    }

    private String get_current_pinyin() {
        String pinyin="";
        for (PinyinButton pc : pinyin_choices) {
            if (pc.get_current_choice().equals(""))
                return "";
            pinyin = pinyin.concat(pc.get_current_choice());
        }
        return pinyin;
    }

    private class update_vocabulary_list implements Runnable {
        ArrayList<VocabularyItem> tmp_translations_in_db=new ArrayList<VocabularyItem>();
        ArrayList<VocabularyItem> tmp_translations_from_ref=new ArrayList<VocabularyItem>();
        ArrayList<VocabularyItem> tmp_translations_displayed_from_ref=new ArrayList<VocabularyItem>();

        public void run() {
            String current_pinyin_word;

            Vocabulary tr;

            current_pinyin_word=get_current_pinyin();
            if (current_pinyin_word.equals(""))
                return;

            // figure out the list of translations based on the current pinyin chosen

            tr = myDB.getTranslations(current_chinese_word, current_pinyin_word);

            if (tr != null)
                for (String e : tr.english) {
                    tmp_translations_in_db.add(new VocabularyItem(e, myDB.getLessonForWord(tr.chinese, tr.pinyin, e), VocabularyItem.FROM_LOCAL_DB));
                }

            if (refDB != null) {
                tr = refDB.getTranslations(current_chinese_word, current_pinyin_word);

                if (tr != null)
                    for (String e : tr.english) {
                        tmp_translations_from_ref.add(new VocabularyItem(e, refDB.getLessonForWord(tr.chinese, tr.pinyin, e), VocabularyItem.FROM_REF_DB));
                    }
            }

            ArrayList<String> bare_translations = new ArrayList<String>();
            for (VocabularyItem v : tmp_translations_in_db) {
                String bare = HanziDB.bare_translation(v.translation);
                if (!bare.equals(""))
                    bare_translations.add(bare);
            }
            for (VocabularyItem v : tmp_translations_from_ref) {
                if (!bare_translations.contains(HanziDB.bare_translation(v.translation)))
                    tmp_translations_displayed_from_ref.add(v);
            }

            translations_in_db=tmp_translations_in_db;
            translations_from_ref=tmp_translations_from_ref;
            translations_displayed_from_ref=tmp_translations_displayed_from_ref;

            ListView lv = (ListView) findViewById(R.id.vocabulary_list);
            if (lv != null) {
                ((VocabularyArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
            }
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
        public int state; // 1 from local database; 2 in ref DB; 3 if edited; 4 if new
        public boolean expanded; // if the view is expanded

        public static final int FROM_LOCAL_DB=1;
        public static final int FROM_REF_DB=2;
        public static final int NEW=4;

        public VocabularyItem(String tr, ArrayList<String > ls, int from) {
            translation=tr;
            lessons=ls;
            state=from;
            expanded =false;
        }
    }

    private class VocabularyArrayAdapter extends ArrayAdapter<Object> {
        private int my=0, ref=0;

        public VocabularyArrayAdapter() {
            super(AddVocabularyActivity.this, R.layout.add_vocabulary_list_item, translations_in_db.toArray());
        }

        @Override
        public int getCount() {
            int res;

            my = translations_in_db.size();
            res = my;
            if (my != 0)
                res ++; // for the section title

            ref = translations_displayed_from_ref.size();
            res += ref;
            if (ref != 0)
               res ++; // for the section title

            res++; // for the edit box
            
            return res;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row;

            if ((position == 1) || ((my != 0) && (position == my+2))) {
                // it is a title view...
                if ((convertView != null) && ((Integer)convertView.getTag(R.id.add_list_view_inflated)==R.layout.add_vocabulary_list_title)) {
                    row = convertView;
                }
                else {
                    LayoutInflater inflater = getLayoutInflater();
                    row = inflater.inflate(R.layout.add_vocabulary_list_title, parent, false);
                    row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_title);
                }

                TextView title = (TextView) row.findViewById(R.id.add_title);
                if ((position==1) && (my != 0))
                    title.setText("Vocabulary from your DB");
                else
                    title.setText("Vocabulary from ref DB");

                return row;
            }

            if (position == 0) {
                // it is the edit box for new words
                if ((convertView != null) && ((Integer)convertView.getTag(R.id.add_list_view_inflated)==R.layout.add_vocabulary_list_new_item)) {
                    row = convertView;
                }
                else {
                    LayoutInflater inflater = getLayoutInflater();
                    row = inflater.inflate(R.layout.add_vocabulary_list_new_item, parent, false);
                    row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_new_item);
                }

                Button add_new_word = (Button) row.findViewById(R.id.add_new_translation_ok);
                EditText word_field = (EditText) row.findViewById(R.id.add_new_translation);
                add_new_word.setTag(R.id.add_vocabulary_item, word_field);
                add_new_word.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        EditText word_field = (EditText) view.getTag(R.id.add_vocabulary_item);
                        final String translation = word_field.getText().toString();
                        word_field.setText("");
                        if (translation.equals(""))
                            return;
                        // check if it is not a duplicate
                        for (VocabularyItem v:translations_in_db) {
                            if (v.translation.equals(translation))
                                return;
                        }

                        process_handler.post(new Runnable() {
                            public void run() {
                                Vocabulary voc = new Vocabulary(current_chinese_word, get_current_pinyin(), translation);
                                myDB.addVocabulary(voc);
                                ArrayList<String> word_lesson = new ArrayList<String>();
                                if (!default_lesson.equals("")) {
                                    myDB.assignWordLesson(default_lesson, voc);
                                    word_lesson.add(default_lesson);
                                }
                                translations_in_db.add(new VocabularyItem(translation, word_lesson, VocabularyItem.NEW));
                                hide_duplicates_in_ref();

                            }
                        });
                    }
                });

                return row;
            }


            if ((position >1) && (position<my+2)) {
                // part of the local DB
                final VocabularyItem voc = translations_in_db.get(position-2);

                if ((convertView != null) && ((Integer)convertView.getTag(R.id.add_list_view_inflated)==R.layout.add_vocabulary_list_item)) {
                    row = convertView;
                }
                else {
                    LayoutInflater inflater = getLayoutInflater();
                    row = inflater.inflate(R.layout.add_vocabulary_list_item, parent, false);
                    row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_item);
                }
                
                ExtendingLinearLayout el = (ExtendingLinearLayout) row.findViewById(R.id.add_vocabulary_info);
                Button bt = (Button) row.findViewById(R.id.add_expand_button);
                if (el!=null) {
                    if(voc.expanded) {
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
                String lessons = "";
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
                more.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        AlertDialog.Builder dialog = new AlertDialog.Builder(AddVocabularyActivity.this);
                        dialog.setTitle("Choose the lessons for word \""+voc.translation+"\"");
                        final ArrayList<String> lessons = myDB.getLessons();
                        final boolean[] selected = new boolean[lessons.size()];
                        final CharSequence[] labels = new CharSequence[lessons.size()];
                        for (int i=0; i<lessons.size(); i++) {
                            labels[i] = lessons.get(i);
                            selected[i] = voc.lessons.contains(lessons.get(i));
                        }
                        dialog.setMultiChoiceItems(labels, selected, new DialogInterface.OnMultiChoiceClickListener() {
                            public void onClick(DialogInterface dialog, final int i, final boolean b) {
                                Log.d(TAG, "Changing assignement for lesson "+lessons.get(i));
                                process_handler.post(new Runnable() {
                                    public void run() {
                                        if (b) {
                                            voc.lessons.add(lessons.get(i));
                                            myDB.assignWordLesson(lessons.get(i), voc.translation, current_chinese_word, get_current_pinyin());
                                        }
                                        else {
                                            voc.lessons.remove(lessons.get(i));
                                            myDB.removeWordLesson(lessons.get(i), voc.translation, current_chinese_word, get_current_pinyin());
                                        }
                                    }
                                });

                            }
                        });
                        dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int i) {
                                dialog.dismiss();
                                ListView lv = (ListView) findViewById(R.id.vocabulary_list);
                                if (lv != null) {
                                    ((VocabularyArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
                                }
                            }
                        });
                        dialog.show();
                    }
                });

                TextView word = (TextView) row.findViewById(R.id.add_word);
                word.setText(voc.translation);
                switch(voc.state) {
                    case VocabularyItem.FROM_LOCAL_DB: // local database
                        word.setTypeface(null, Typeface.NORMAL);
                        break;
                    case VocabularyItem.FROM_REF_DB: // reference database
                    case VocabularyItem.NEW: // new word
                        word.setTypeface(null, Typeface.BOLD);
                        break;

                }
                word.setTag(R.id.add_vocabulary_item, voc);

                CheckBox cb = (CheckBox) row.findViewById(R.id.add_check);
                cb.setTag(R.id.add_vocabulary_item, voc);
                cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(final CompoundButton compoundButton, final boolean b) {
                        if (b)
                            return;
                        process_handler.post(new Runnable() {
                            public void run() {
                                VocabularyItem voc = (VocabularyItem) compoundButton.getTag(R.id.add_vocabulary_item);
                                Vocabulary v = new Vocabulary(current_chinese_word, get_current_pinyin(), voc.translation);
                                myDB.delVocabulary(v);
                                translations_in_db.remove(voc);
                                hide_duplicates_in_ref();
                                Log.d(TAG, "translation "+voc.translation+" is changed");
                            }
                        });

                    }
                });
                cb.setChecked(true);

                return row;
            }
            else  {

                // in the ref DB

                if (my != 0) {
                    position -= my + 1;
                }

                final VocabularyItem voc = translations_displayed_from_ref.get(position-2);

                if ((convertView != null) && ((Integer)convertView.getTag(R.id.add_list_view_inflated)==R.layout.add_vocabulary_list_ref_item)) {
                    row = convertView;
                }
                else {
                    LayoutInflater inflater = getLayoutInflater();
                    row = inflater.inflate(R.layout.add_vocabulary_list_ref_item, parent, false);
                    row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_ref_item);
                }

                TextView word = (TextView) row.findViewById(R.id.add_word);
                word.setText(voc.translation);
                word.setTypeface(null, Typeface.ITALIC);
                word.setTag(R.id.add_vocabulary_item, voc);

                CheckBox cb = (CheckBox) row.findViewById(R.id.add_check);
                cb.setTag(R.id.add_vocabulary_item, voc);
                cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(final CompoundButton compoundButton, boolean b) {
                        if (!b)
                            return;
                        process_handler.post(new Runnable() {
                            public void run() {
                                VocabularyItem voc = (VocabularyItem) compoundButton.getTag(R.id.add_vocabulary_item);
                                Vocabulary v = new Vocabulary(current_chinese_word, get_current_pinyin(), voc.translation);
                                myDB.addVocabulary(v);
                                if (!default_lesson.equals("")) {
                                    myDB.assignWordLesson(default_lesson, v);
                                    voc.lessons.add(default_lesson);
                                }
                                translations_in_db.add(voc);
                                translations_displayed_from_ref.remove(voc);
                                ListView lv = (ListView) findViewById(R.id.vocabulary_list);
                                if (lv != null) {
                                    ((VocabularyArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
                                }
                                Log.d(TAG, "translation "+voc.translation+" is changed");
                            }
                        });
                    }
                });
                cb.setChecked(false);

                return row;
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
        AlertDialog.Builder dialog = new AlertDialog.Builder(AddVocabularyActivity.this);
        dialog.setTitle("Choose the default lesson");
        dialog.setCancelable(false);

        final String current_default_lesson=default_lesson;
        final ArrayList<String> lessons = myDB.getLessons();
        lessons.add(0, "(none)");
        int selected_index = default_lesson.equals("")?0:lessons.indexOf(default_lesson);

        final ArrayAdapter<String> aa=new ArrayAdapter<String>(AddVocabularyActivity.this, R.layout.simple_list_item_single_choice, lessons);
        dialog.setSingleChoiceItems(aa, selected_index,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        default_lesson=lessons.get(i);
                        Log.d(TAG, "Set default lesson to "+default_lesson);
                        if (default_lesson.equals("(none)"))
                            default_lesson="";
                        dialog.dismiss();
                    }
                });
        dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                default_lesson = current_default_lesson;
                dialog.dismiss();
            }
        });
        dialog.show();
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
            voc.expanded =true;
        }
        else {
            v.setBackgroundResource(R.drawable.expand_arrow_right);
            ExtendingLinearLayout el = (ExtendingLinearLayout) v.getTag(R.id.add_view_to_expand);
            if (el!=null)
                el.collapse();
            v.setTag(R.id.add_expanded, "0");
            voc.expanded =false;
        }
    }

    public void handle_word_click(View v) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        final TextView view = (TextView) v;
        final VocabularyItem voc = (VocabularyItem) v.getTag(R.id.add_vocabulary_item);

        alert.setTitle("Edit translation for "+current_chinese_word);
        alert.setMessage("Please enter the new word:");

        final EditText input = new EditText(this);
        input.setText(view.getText());
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                final String value = input.getText().toString();
                if ((!value.equals("")) && (!value.equals(view.getText()))) {
                    for (VocabularyItem v:translations_in_db) {
                        if (v.translation.equals(value)) {
                            dialog.dismiss();
                            return;
                        }
                    }
                    process_handler.post(new Runnable() {
                        public void run() {
                            if (translations_in_db.contains(voc)) {
                                Vocabulary modified_voc = new Vocabulary(current_chinese_word, get_current_pinyin(), voc.translation);
                                myDB.delVocabulary(modified_voc);
                                voc.translation=value;
                                modified_voc.english.remove(0);
                                modified_voc.english.add(value);
                                myDB.addVocabulary(modified_voc);
                                for (String le:voc.lessons)
                                    myDB.assignWordLesson(le, modified_voc);
                                ListView lv = (ListView) findViewById(R.id.vocabulary_list);
                                if (lv != null) {
                                    ((VocabularyArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
                                }
                            }
                            else {
                                Vocabulary modified_voc = new Vocabulary(current_chinese_word, get_current_pinyin(), value);
                                VocabularyItem voc_item=new VocabularyItem(value, new ArrayList<String>(), VocabularyItem.NEW);
                                myDB.addVocabulary(modified_voc);
                                if (!default_lesson.equals("")) {
                                    myDB.assignWordLesson(default_lesson, modified_voc);
                                    voc_item.lessons.add(default_lesson);
                                }
                                translations_in_db.add(voc_item);
                                hide_duplicates_in_ref();
                            }
                        }
                    });
                }
                dialog.dismiss();
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });

        alert.show();
    }

    public void handle_add_another(View v) {
        EditText chinese_word = (EditText) findViewById(R.id.add_vocabulary_chinese);
        chinese_word.setText("");
    }
}
