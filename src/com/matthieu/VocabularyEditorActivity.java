package com.matthieu;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.matthieu.widget.LockableViewPager;
import com.viewpagerindicator.PageIndicator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

public class VocabularyEditorActivity extends SherlockFragmentActivity {
    Fragment fragments[];

    HanziDB refDB, myDB;

    public enum activity_state {
        STATE_INIT,
        STATE_EDIT_WORD,
        STATE_EDIT_WORD_VOC_SELECTED_SINGLE,
        STATE_EDIT_WORD_VOC_SELECTED_MULTIPLE,
        STATE_EDIT_WORD_DIC_SELECTED,
        STATE_EDIT_WORD_VOC_SELECTED_LESSON_CHANGED,
        STATE_LESSON_SELECTED_SINGLE,
        STATE_LESSON_SELECTED_MULTIPLE,
        STATE_LESSON_SELECTION
    }
    private activity_state current_state = activity_state.STATE_INIT;
    public activity_state get_state() {
        return current_state;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vocabulary_editor);

        refDB = ((AndZiApplication) getApplication()).getRefDB();
        myDB = ((AndZiApplication) getApplication()).getMyDB();

        String hanzi = getIntent().getStringExtra("Hanzi");

        fragments = new Fragment[3];

        LockableViewPager pager = (LockableViewPager) findViewById(R.id.voc_editor_pager);

        if (savedInstanceState != null) {
            fragments[0] = getSupportFragmentManager().getFragment(savedInstanceState, "fragment0");
            fragments[1] = getSupportFragmentManager().getFragment(savedInstanceState, "fragment1");
            fragments[2] = getSupportFragmentManager().getFragment(savedInstanceState, "fragment2");
            if (pager != null) {
                pager.setCurrentItem(savedInstanceState.getInt("position"));
            }
        }
        if (fragments[0] == null) {
            fragments[0] = new AddVocabularyFragment();
            if (hanzi != null) {
                Bundle bundle = new Bundle();
                bundle.putString("hanzi", hanzi);
                fragments[0].setArguments(bundle);
            }
        }
        if (fragments[1] == null)
            fragments[1] = new EditLessonsFragment();

        if (pager != null) {
            pager.setOffscreenPageLimit(2);
            pager.setAdapter(new editor_pager(getSupportFragmentManager()));

            PageIndicator mIndicator = (PageIndicator) findViewById(R.id.pager_indicator);
            mIndicator.setViewPager(pager);
        }
        else {
            // TODO When we are on a larger screen without a pager
        }

        if ((hanzi!=null) && (!hanzi.equals(""))){
            // TODO set the right state
        }

        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getAction() == Intent.ACTION_EDIT) {
            String hanzi = intent.getStringExtra("Hanzi");
            if ((hanzi != null) && (fragments[0] != null)) {
                ((AddVocabularyFragment)fragments[0]).set_word_to_edit(hanzi);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LockableViewPager pager = (LockableViewPager) findViewById(R.id.voc_editor_pager);
        if (getIntent().getAction().equals(Intent.ACTION_EDIT)) {
            if (pager != null) {
                pager.setCurrentItem(0);
                pager.setPagingEnabled(true);
            }
        } else if (getIntent().getAction().equals(Intent.ACTION_PICK)) {  // lesson selection mode
            if (pager != null) {
                pager.setCurrentItem(1);
                pager.setPagingEnabled(false);
            }
            set_state(activity_state.STATE_LESSON_SELECTION);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            getSupportFragmentManager().putFragment(outState, "fragment0", fragments[0]);
        } catch (IllegalStateException e) {}
        try {
            getSupportFragmentManager().putFragment(outState, "fragment1", fragments[1]);
        } catch (IllegalStateException e) {}
        if (fragments[2] != null) {
            try {
                getSupportFragmentManager().putFragment(outState, "fragment2", fragments[2]);
            } catch (IllegalStateException e) {}
        }
        LockableViewPager pager = (LockableViewPager) findViewById(R.id.voc_editor_pager);
        if (pager != null) {
            outState.putInt("position", pager.getCurrentItem());
        }
    }

    public void open_lesson_list(ArrayList<String> label) {
        if (fragments[2]!=null)
            close_lesson_list();
        fragments[2] = new LessonListFragment();
        Bundle bundle = new Bundle();
        bundle.putStringArrayList("lesson", label);
        fragments[2].setArguments(bundle);
    }

    public void close_lesson_list() {
        LockableViewPager pager = (LockableViewPager) findViewById(R.id.voc_editor_pager);
        if ((pager != null) && (pager.getCurrentItem()==2)) {
            pager.setCurrentItem(1, true);
        }
        if (fragments[2] == null)
            return;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.remove(fragments[2]);
        ft.commit();
        fragments[2]=null;
        if (pager != null)
            pager.getAdapter().notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        ViewPager pager = (ViewPager) findViewById(R.id.voc_editor_pager);
        if ((pager != null) && (pager.getCurrentItem() != 0))
            pager.setCurrentItem(0, true); // TODO: only do that when we are in "editor" mode, not for lesson picking
        else
            super.onBackPressed();
    }

    public void set_state(activity_state new_state) {
        if (current_state == activity_state.STATE_EDIT_WORD_VOC_SELECTED_LESSON_CHANGED) {
            ((AddVocabularyFragment)fragments[0]).set_new_lesson_assignment(flush_lesson_assignment());
        }
        current_state = new_state;
        invalidateOptionsMenu();
    }

    private class editor_pager extends FragmentPagerAdapter {
        public editor_pager(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            return fragments[i];
        }

        @Override
        public int getCount() {
            return fragments[2]==null?2:3;
        }

        @Override
        public int getItemPosition(Object object) {
            Fragment fragment = (Fragment) object;
            for (int i=0; i<3; i++)
                if (fragment.equals(fragments[i]))
                    return POSITION_UNCHANGED;

            return POSITION_NONE;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        int menu_resource=0;
        switch(current_state) {
            case STATE_INIT:
                menu_resource = R.menu.add_vocabulary_lesson_none;
                break;
            case STATE_EDIT_WORD:
                menu_resource = R.menu.add_vocabulary_menu_ready;
                break;
            case STATE_EDIT_WORD_VOC_SELECTED_SINGLE:
                menu_resource = R.menu.add_vocabulary_menu_edit;
                break;
            case STATE_EDIT_WORD_VOC_SELECTED_MULTIPLE:
                menu_resource = R.menu.add_vocabulary_menu_edit_multiple;
                break;
            case STATE_EDIT_WORD_VOC_SELECTED_LESSON_CHANGED:
                menu_resource = R.menu.add_vocabulary_menu_ready;
                break;
            case STATE_EDIT_WORD_DIC_SELECTED:
                menu_resource = R.menu.add_vocabulary_menu_add;
                break;
            case STATE_LESSON_SELECTED_SINGLE:
                menu_resource = R.menu.add_vocabulary_lesson_single;
                break;
            case STATE_LESSON_SELECTED_MULTIPLE:
                menu_resource = R.menu.add_vocabulary_lesson_multiple;
                break;
            case STATE_LESSON_SELECTION:
                menu_resource = R.menu.add_vocabulary_lesson_selection;
                break;
        }
        menu.clear();
        if (menu_resource != 0) {
            MenuInflater inflater = getSupportMenuInflater();
            inflater.inflate(menu_resource, menu);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ArrayList<String> selection;

        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.add_vocabulary_show_new_translation:
                ((AddVocabularyFragment)fragments[0]).add_new();
                break;
            case R.id.add_vocabulary_add_selection:
                ((AddVocabularyFragment)fragments[0]).add_selection();
                if ((fragments[2] != null) && ((AddVocabularyFragment)fragments[0]).get_default_lesson().equals(
                        ((LessonListFragment)fragments[2]).current_lessons())) {
                    ((LessonListFragment)fragments[2]).update_list();
                }

                return true;
            case R.id.add_vocabulary_delete_selection:
                ((AddVocabularyFragment)fragments[0]).del_selection();
                if (fragments[2] != null)
                    ((LessonListFragment)fragments[2]).update_list();
                return true;
            case R.id.add_vocabulary_edit_translation:
                ((AddVocabularyFragment)fragments[0]).edit_selection();
                if (fragments[2] != null)
                    ((LessonListFragment)fragments[2]).update_list();
                return true;
            case R.id.add_vocabulary_assign_lesson_done:
                flush_lesson_assignment();
                return true;
            case R.id.add_vocabulary_done:
                ((AddVocabularyFragment)fragments[0]).done();
                return true;

            case R.id.add_vocabulary_lesson_select_all:
                ((EditLessonsFragment)fragments[1]).set_select_all();
                break;
            case R.id.add_vocabulary_lesson_select_none:
                ((EditLessonsFragment)fragments[1]).set_select_none();
                break;
            case R.id.add_vocabulary_lesson_select_done:
                Intent resultIntent = getIntent();
                selection = ((EditLessonsFragment)fragments[1]).get_selection();
                Bundle b = new Bundle();
                b.putStringArrayList("lesson_list", selection);
                resultIntent.putExtras(b);
                setResult(RESULT_OK, resultIntent);
                finish();
                break;

            case R.id.add_vocabulary_lesson_show_new_lesson:
                ((EditLessonsFragment)fragments[1]).add_new();
                // TODO: move to lesson list fragment if we have a ViewPager
                break;
            case R.id.add_vocabulary_lesson_clone:
                ((EditLessonsFragment)fragments[1]).clone_selection();
                break;
            case R.id.add_vocabulary_lesson_delete:
                selection = ((EditLessonsFragment)fragments[1]).get_selection();
                if (selection.size() != 1) {
                    ((EditLessonsFragment)fragments[1]).update_state();
                    break;
                }
                close_lesson_list();
                ((EditLessonsFragment)fragments[1]).del_selection();
                break;
            case R.id.add_vocabulary_lesson_rename:
                selection = ((EditLessonsFragment)fragments[1]).get_selection();
                if (selection.size() != 1) {
                    ((EditLessonsFragment)fragments[1]).update_state();
                    break;
                }
                close_lesson_list();
                ((EditLessonsFragment)fragments[1]).rename_selection();
                break;
            case R.id.add_vocabulary_lesson_merge:
                ((EditLessonsFragment)fragments[1]).merge_selection();
                break;
            case R.id.add_vocabulary_lesson_set_default:
                set_default_list();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void set_default_list() {
        ArrayList<String> selection = ((EditLessonsFragment)fragments[1]).get_selection();
        if (selection.size() != 1) {
            ((EditLessonsFragment)fragments[1]).update_state();
            return;
        }
        String lesson = selection.get(0);
        if (lesson != null) {
            ((AddVocabularyFragment)fragments[0]).set_default_lesson(lesson);
        }
        LockableViewPager pager = (LockableViewPager) findViewById(R.id.voc_editor_pager);
        if (pager != null) {
            pager.setCurrentItem(0, true);
        }
    }

    public void edit_lesson_assignment(HashMap<String, LessonAssignment> assignment) {
        ((EditLessonsFragment)fragments[1]).start_assigning_lesson(assignment);
    }

    public void clear_lesson_selection() {
        ((EditLessonsFragment)fragments[1]).clear_selection();
        close_lesson_list();
    }

    public HashMap<String, LessonAssignment> flush_lesson_assignment() {
        return ((EditLessonsFragment)fragments[1]).get_assignment();
    }

    public static class LessonAssignment {
        boolean partiable;
        int state;
    }
}
