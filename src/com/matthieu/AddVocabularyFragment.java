/******************************
 * Activity to add more vocabulary in the database
 *
 * ChineseTextWatcher    checks if the input is Chinese or not and trigger the rest of the processing when there is some change
 * PinyinButtonFiller    gets all the available pinyins for a particular character
 * PinyinButton          holds the current value / default value and other possible values for each pinyin button
 * UpdateDefaultPinyint  tries to find the word in the database to set the default pinyin values for each button
 * UpdateVocabularyList   gets the translations in the local database and in the reference database
 * VocabularyArrayAdapter is the adapter for the list of translations
 */
package com.matthieu;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
//import android.util.Log;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.matthieu.widget.EditTextAutomaticSize;
import com.matthieu.widget.ExtendingLinearLayout;
import com.matthieu.widget.PinyinButtons;

import java.util.ArrayList;
import java.util.HashMap;

public class AddVocabularyFragment extends Fragment
{
    //private static final String TAG="AddVocabularyActivity";

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
        setRetainInstance(true);

        translations_from_ref = new ArrayList<VocabularyItem>();
        translations_displayed_from_ref = new ArrayList<VocabularyItem>();
        translations_in_db = new ArrayList<VocabularyItem>();
        process_handler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Bundle bundle=getArguments();
        if (bundle != null) {
            current_chinese_word = bundle.getString("hanzi");
        }
        final View res = inflater.inflate(R.layout.add_vocabulary_top, container, false);

        EditTextAutomaticSize chinese_word = (EditTextAutomaticSize) res.findViewById(R.id.add_vocabulary_chinese);
        ChineseTextWatcher text_watcher = new ChineseTextWatcher(chinese_word);
        chinese_word.addTextChangedListener(text_watcher);
        chinese_word.setMaximumTextSize(32);
        chinese_word.setMinimumTextSize(10);
        View.inflate(getActivity(), R.layout.add_vocabulary_wrong_entry, (ViewGroup) res.findViewById(R.id.main_content));


        return res;
    }

    @Override
    public void onResume() {
        super.onResume();

        // in case those changed...
        myDB = ((AndZiApplication) getActivity().getApplication()).getMyDB();
        refDB = ((AndZiApplication) getActivity().getApplication()).getRefDB();

        EditTextAutomaticSize chinese_word = (EditTextAutomaticSize) getView().findViewById(R.id.add_vocabulary_chinese);
        if (current_chinese_word!= null) {
            chinese_word.setText(current_chinese_word);
        }
    }

    // Handle when the content of the chinese word text field changes
    private class ChineseTextWatcher implements TextWatcher {

        private EditTextAutomaticSize tview;
        private int state; // 0 is empty, 1 is "normal", 2 is "wrong character in the string"

        public ChineseTextWatcher(EditTextAutomaticSize tv) {
            tview = tv;
            state=-1;
        }

        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            if (getView() == null)
                return;

            // change text size to fit in the box
            tview.adaptTextSize();

            // check if we only have chinese characters (change layout if not)
            checkChineseChars();

            if (state == 1) {
                current_chinese_word = ((EditTextAutomaticSize) getView().findViewById(R.id.add_vocabulary_chinese)).getText().toString();

                // Populate the choices for the pinyin buttons
                PinyinButtons buttons = (PinyinButtons) getView().findViewById(R.id.pinyin_buttons);
                buttons.setPinyinButtons(charSequence.toString(), i, i1, i2, current_chinese_word);
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
            //Log.d(TAG, "context going to state "+new_state);

            LinearLayout main_content = (LinearLayout) getView().findViewById(R.id.main_content);
            if (new_state != state) {
                switch(new_state) {
                    case 0:
                        // empty string
                        if(state == 1) {
                            main_content.removeAllViews();
                            View.inflate(getActivity(), R.layout.add_vocabulary_wrong_entry, main_content);
                        }
                        ((TextView) getView().findViewById(R.id.warning_chinese_word)).setText(R.string.add_voc_enter_chinese);
                        ((VocabularyEditorActivity)getActivity()).set_menu(VocabularyEditorActivity.menu_types.INITIAL);
                        break;
                    case 1:
                        // all chinese characters
                        main_content.removeAllViews();
                        View.inflate(getActivity(), R.layout.add_vocabulary_context, main_content);
                        PinyinButtons buttons = (PinyinButtons) getView().findViewById(R.id.pinyin_buttons);
                        buttons.set_containing_fragment(AddVocabularyFragment.this);
                        buttons.clearPinyinButtons(); //setPinyinButtons(current_chinese_word);
                        ListView lv = (ListView) getView().findViewById(R.id.vocabulary_list);
                        //lv.addFooterView();
                        lv.setAdapter(new VocabularyArrayAdapter());

                        ((VocabularyEditorActivity)getActivity()).set_menu(VocabularyEditorActivity.menu_types.VOCABULARY_READY);
                        break;
                    case 2:
                        // some non-chinese characters...
                        if(state == 1) {
                            main_content.removeAllViews();
                            View.inflate(getActivity(), R.layout.add_vocabulary_wrong_entry, main_content);
                        }
                        ((TextView) getView().findViewById(R.id.warning_chinese_word)).setText(R.string.add_voc_only_chinese);
                        ((VocabularyEditorActivity)getActivity()).set_menu(VocabularyEditorActivity.menu_types.INITIAL);
                        break;
                }
                state = new_state;
            }
        }
    }

    public String get_current_pinyin() {
        PinyinButtons buttons = (PinyinButtons) getView().findViewById(R.id.pinyin_buttons);
        return buttons.get_current_pinyin();
    }

    public void update_vocabulary() {
        process_handler.post(new UpdateVocabularyList());
    }

    private class UpdateVocabularyList implements Runnable {
        ArrayList<VocabularyItem> tmp_translations_in_db=new ArrayList<VocabularyItem>();
        ArrayList<VocabularyItem> tmp_translations_from_ref=new ArrayList<VocabularyItem>();
        ArrayList<VocabularyItem> tmp_translations_displayed_from_ref=new ArrayList<VocabularyItem>();

        public void run() {
            String current_pinyin_word;

            Vocabulary tr;

            current_pinyin_word=get_current_pinyin();
            if (! current_pinyin_word.equals("")) {
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
            }

            translations_in_db=tmp_translations_in_db;
            translations_from_ref=tmp_translations_from_ref;
            translations_displayed_from_ref=tmp_translations_displayed_from_ref;

            ListView lv = (ListView) getView().findViewById(R.id.vocabulary_list);
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

        ListView lv = (ListView) getView().findViewById(R.id.vocabulary_list);
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

    private class VocabularyArrayAdapter extends BaseAdapter {
        private int my=0, ref=0;
        boolean new_collapsed = true;
        ExtendingLinearLayout first_row = null;

        public void expand_new_field(boolean expand) {
            if (first_row != null && expand && new_collapsed) {
                new_collapsed = !expand;
                first_row.expand();
                EditText field = (EditText) first_row.findViewById(R.id.add_new_translation);
                field.requestFocus();
            } else if (first_row == null && expand && new_collapsed) {
                new_collapsed = !expand;
            }
        }

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

        public Object getItem(int position) {
            if (position == 0)
                return "";
            else if (position == 1)
                return getResources().getString(R.string.add_voc_ref_db);
            else if ((my != 0) && (position == my+2))
                return getResources().getString(R.string.add_voc_local_db);
            else if ((position >1) && (position<my+2))
                return translations_in_db.get(position-2);
            else
                return translations_displayed_from_ref.get(position-2);
        }

        public long getItemId(int position) {
            return getItem(position).hashCode();
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0)
                return 0;
            else if (position == 1)
                return 1;
            else if ((my != 0) && (position == my+2))
                return 1;
            else if ((position >1) && (position<my+2))
                return 2;
            else
                return 3;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View row;

            if ((position == 1) || ((my != 0) && (position == my+2))) {
                // it is a title view...
                if ((convertView != null) && ((Integer)convertView.getTag(R.id.add_list_view_inflated)==R.layout.add_vocabulary_list_title)) {
                    row = convertView;
                }
                else {
                    LayoutInflater inflater = getLayoutInflater(null);
                    row = inflater.inflate(R.layout.add_vocabulary_list_title, parent, false);
                    row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_title);
                }

                TextView title = (TextView) row.findViewById(R.id.add_title);
                title.setGravity(Gravity.RIGHT);
                if ((position==1) && (my != 0))
                    title.setText(R.string.add_voc_local_db);
                else
                    title.setText(R.string.add_voc_ref_db);

                row.setClickable(false);
                return row;
            }

            if (position == 0) {
                // it is the edit box for new words
                if ((convertView != null) && ((Integer)convertView.getTag(R.id.add_list_view_inflated)==R.layout.add_vocabulary_list_new_item)) {
                    row = convertView;
                }
                else {
                    LayoutInflater inflater = getLayoutInflater(null);
                    row = inflater.inflate(R.layout.add_vocabulary_list_new_item, parent, false);
                    row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_new_item);
                }
                ExtendingLinearLayout elv = (ExtendingLinearLayout) row.findViewById(R.id.add_voc_new_container);
                if (new_collapsed)
                    elv.collapse_immediate();
                else
                    elv.expand_immediate();
                first_row = elv;

                Button add_new_word = (Button) row.findViewById(R.id.add_new_translation_ok);
                EditText word_field = (EditText) row.findViewById(R.id.add_new_translation);
                add_new_word.setTag(R.id.vocabulary_item, word_field);
                add_new_word.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        EditText word_field = (EditText) view.getTag(R.id.vocabulary_item);
                        final String translation = word_field.getText().toString();
                        word_field.setText("");
                        if (translation.equals(""))
                            return;
                        // check if it is not a duplicate
                        for (VocabularyItem v:translations_in_db) {
                            if (v.translation.equals(translation))
                                return;
                        }

                        ArrayList<String> word_lesson = new ArrayList<String>();
                        if (!default_lesson.equals("")) {
                            word_lesson.add(default_lesson);
                        }
                        translations_in_db.add(new VocabularyItem(translation, word_lesson, VocabularyItem.NEW));
                        new_collapsed = true;
                        InputMethodManager imm = (InputMethodManager)AddVocabularyFragment.this.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(word_field.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

                        hide_duplicates_in_ref();

                        new Thread(new Runnable() {
                            public void run() {
                                Vocabulary voc = new Vocabulary(current_chinese_word, get_current_pinyin(), translation);
                                myDB.addVocabulary(voc);

                                if (!default_lesson.equals("")) {
                                    myDB.assignWordLesson(default_lesson, voc);
                                }
                            }
                        }).start();
                    }
                });

                row.setClickable(false);
                return row;
            }

            if ((position >1) && (position<my+2)) {
                // part of the local DB
                final VocabularyItem voc = translations_in_db.get(position-2);
                boolean has_lessons= !(myDB.getLessons().size() == 0);
                int desired_layout;
                if (has_lessons)
                    desired_layout = R.layout.add_vocabulary_list_item;
                else
                    desired_layout = R.layout.add_vocabulary_list_ref_item;

                if ((convertView != null) && ((Integer)convertView.getTag(R.id.add_list_view_inflated)==desired_layout)) {
                    row = convertView;
                }
                else {
                    LayoutInflater inflater = getLayoutInflater(null);
                    row = inflater.inflate(desired_layout, parent, false);
                    row.setTag(R.id.add_list_view_inflated, desired_layout);
                }

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
                word.setTag(R.id.vocabulary_item, voc);

                row.setTag(R.id.vocabulary_item, voc);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        handle_word_click(view);
                    }
                });
                row.setClickable(true);

                if (current_word_selection.contains(voc)) {
                    row.setBackgroundColor(0xFF777777);
                } else {
                    row.setBackgroundColor(0x00000000);
                }

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
                    LayoutInflater inflater = getLayoutInflater(null);
                    row = inflater.inflate(R.layout.add_vocabulary_list_ref_item, parent, false);
                    row.setTag(R.id.add_list_view_inflated, R.layout.add_vocabulary_list_ref_item);
                }

                TextView word = (TextView) row.findViewById(R.id.add_word);
                word.setText(voc.translation);
                word.setTypeface(null, Typeface.ITALIC);
                word.setTag(R.id.vocabulary_item, voc);

                row.setTag(R.id.vocabulary_item, voc);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        AddVocabularyFragment.this.handle_word_click(view);
                    }
                });
                row.setClickable(true);

                if (current_word_selection.contains(voc)) {
                    row.setBackgroundColor(0xFF777777);
                } else {
                    row.setBackgroundColor(0x00000000);
                }

                return row;
            }
        }
    }

    public void set_default_lesson(String lesson) {
        default_lesson=lesson;
    }

    public String get_default_lesson() {
        return default_lesson;
    }

    private ArrayList<VocabularyItem> current_word_selection=new ArrayList<VocabularyItem>();

    protected void change_menu() {
        VocabularyEditorActivity.menu_types type;
        if (current_chinese_word.equals(""))
            type = VocabularyEditorActivity.menu_types.INITIAL;
        else if (current_word_selection.size() == 0)
            type = VocabularyEditorActivity.menu_types.VOCABULARY_READY;
        else if (translations_in_db.contains(current_word_selection.get(0))) {
            if (current_word_selection.size() == 1)
                type = VocabularyEditorActivity.menu_types.VOCABULARY_CURRENT_SELECTED;
            else
                type = VocabularyEditorActivity.menu_types.VOCABULARY_CURRENT_MULTIPLE_SELECTED;
        } else
            type = VocabularyEditorActivity.menu_types.VOCABULARY_NEW_SELECTED;

        ((VocabularyEditorActivity)getActivity()).set_menu(type);
    }

    public void handle_word_click(View v) {
        final VocabularyItem voc = (VocabularyItem) v.getTag(R.id.vocabulary_item);
        VocabularyEditorActivity.menu_types type = ((VocabularyEditorActivity)getActivity()).get_menu();
        if (voc == null)
            return;

        if (current_word_selection.contains(voc)) {
            current_word_selection.remove(voc);
            v.setBackgroundColor(0x00000000);
        } else if (translations_in_db.contains(voc) &&
                (type == VocabularyEditorActivity.menu_types.VOCABULARY_CURRENT_SELECTED || type == VocabularyEditorActivity.menu_types.VOCABULARY_READY)) {
            current_word_selection.add(voc);
            v.setBackgroundColor(0xFF777777);
        } else if (translations_from_ref.contains(voc) && !translations_in_db.contains(voc) &&
                (type == VocabularyEditorActivity.menu_types.VOCABULARY_NEW_SELECTED || type == VocabularyEditorActivity.menu_types.VOCABULARY_READY)) {
            current_word_selection.add(voc);
            v.setBackgroundColor(0xFF777777);
        } else {
            return;
        }
        v.invalidate();
        change_menu();
    }

    public void add_new() {
        ListView lv = (ListView) getView().findViewById(R.id.vocabulary_list);
        if (lv == null)
            return;
        lv.smoothScrollToPosition(0);
        ((VocabularyArrayAdapter)lv.getAdapter()).expand_new_field(true);
    }

    public void add_selection() {
        if (current_word_selection.size()==0) {
            change_menu();
            return;
        }
        final ArrayList<Vocabulary> to_add = new ArrayList<Vocabulary>();

        for (VocabularyItem v : current_word_selection) {
            if (!default_lesson.equals("")) {
                v.lessons.add(default_lesson);
            }
            translations_in_db.add(v);
            translations_displayed_from_ref.remove(v);

            to_add.add(new Vocabulary(current_chinese_word, get_current_pinyin(), v.translation));
        }

        new Thread(new Runnable() {
            public void run() {
                myDB.addVocabulary(to_add);
                if (!default_lesson.equals("")) {
                    for (Vocabulary v : to_add)
                        myDB.assignWordLesson(default_lesson, v);
                }
            }
        }).start();

        current_word_selection.clear();
        ListView lv = (ListView) getView().findViewById(R.id.vocabulary_list);
        if (lv != null) {
            ((VocabularyArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
        }
        change_menu();
    }

    public void del_selection() {
        if (current_word_selection.size()==0) {
            change_menu();
            return;
        }
        final ArrayList<Vocabulary> to_del = new ArrayList<Vocabulary>();

        for (VocabularyItem v : current_word_selection) {
            v.lessons.clear();
            translations_in_db.remove(v);
            to_del.add(new Vocabulary(current_chinese_word, get_current_pinyin(), v.translation));
        }
        hide_duplicates_in_ref();

        new Thread(new Runnable() {
            public void run() {
                for (Vocabulary v : to_del)
                    myDB.delVocabulary(v);
            }
        }).start();

        current_word_selection.clear();
        ListView lv = (ListView) getView().findViewById(R.id.vocabulary_list);
        if (lv != null) {
            ((VocabularyArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
        }
        change_menu();
    }

    public void done() {
        EditText chinese_word = (EditText) getView().findViewById(R.id.add_vocabulary_chinese);
        if (chinese_word != null)
            chinese_word.setText("");
        change_menu();
    }

    public void edit_selection() {
        if (current_word_selection.size()!=1) {
            change_menu();
            return;
        }
        AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
        final VocabularyItem v = current_word_selection.get(0);
        alert.setTitle(getResources().getString(R.string.add_voc_edit_translation)+current_chinese_word);
        alert.setMessage(R.string.add_voc_edit_new_word);

        final EditText input = new EditText(getActivity());
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setText(v.translation);
        alert.setView(input);

        alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                final String value = input.getText().toString();
                if ((!value.equals("")) && (!value.equals(v.translation))) {
                    for (VocabularyItem v:translations_in_db) {
                        if (v.translation.equals(value)) {
                            dialog.dismiss();
                            return;
                        }
                    }
                    if (!translations_in_db.contains(v))
                        return;

                    final Vocabulary modified_voc = new Vocabulary(current_chinese_word, get_current_pinyin(), v.translation);
                    myDB.delVocabulary(modified_voc);
                    v.translation=value;
                    modified_voc.english.remove(0);
                    modified_voc.english.add(value);
                    current_word_selection.clear();

                    ListView lv = (ListView) getView().findViewById(R.id.vocabulary_list);
                    if (lv != null) {
                        ((VocabularyArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
                    }
                    change_menu();
                    new Thread(new Runnable() {
                        public void run() {
                            myDB.addVocabulary(modified_voc);
                            for (String le:v.lessons)
                                myDB.assignWordLesson(le, modified_voc);
                        }
                    }).start();

                }
                dialog.dismiss();
            }
        });

        alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });

        alert.show();
    }

    public HashMap<String, VocabularyEditorActivity.LessonAssignment> get_current_lesson_assignment() {
        if (current_word_selection.size()==0) {
            change_menu();
            return null;
        }
        HashMap<String, VocabularyEditorActivity.LessonAssignment> res = new HashMap<String, VocabularyEditorActivity.LessonAssignment>();
        for (int i=0; i<current_word_selection.size(); i++ ) {
            VocabularyItem v = current_word_selection.get(i);
            if (i==0) {
                for (String l:v.lessons) {
                    VocabularyEditorActivity.LessonAssignment new_assign = new VocabularyEditorActivity.LessonAssignment();
                    new_assign.state=1;
                    new_assign.partiable=false;
                    res.put(l, new_assign);
                }
            } else {
                for (String l:v.lessons) {
                    if (!res.containsKey(l)) {
                        VocabularyEditorActivity.LessonAssignment new_assign = new VocabularyEditorActivity.LessonAssignment();
                        new_assign.state=2;
                        new_assign.partiable=true;
                        res.put(l, new_assign);
                    }
                }
                for (String l:res.keySet()) {
                    if (!v.lessons.contains(l)) {
                        VocabularyEditorActivity.LessonAssignment assign = res.get(l);
                        assign.state=2;
                        assign.partiable=true;
                    }
                }
            }
        }
        return res;
    }

    public void set_new_lesson_assignment(HashMap<String, VocabularyEditorActivity.LessonAssignment> assignment) {
        if (current_word_selection.size()==0) {
            change_menu();
            return;
        }
        for (final String l : assignment.keySet()) {
            VocabularyEditorActivity.LessonAssignment this_lesson = assignment.get(l);
            if (this_lesson.state == 2)
                continue;
            for (final VocabularyItem v : current_word_selection) {
                switch (this_lesson.state) {
                    case 0:
                        v.lessons.remove(l);
                        new Thread(new Runnable() {
                            public void run() {
                                myDB.removeWordLesson(l, v.translation, current_chinese_word, get_current_pinyin());
                            }
                        }).start();
                        break;
                    case 1:
                        v.lessons.add(l);
                        new Thread(new Runnable() {
                            public void run() {
                                myDB.assignWordLesson(l, v.translation, current_chinese_word, get_current_pinyin());
                            }
                        }).start();
                        break;
                }
            }
        }
    }
}
