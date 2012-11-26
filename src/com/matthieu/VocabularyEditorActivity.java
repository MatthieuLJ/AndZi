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

import java.util.ArrayList;
import java.util.HashMap;

public class VocabularyEditorActivity extends SherlockFragmentActivity implements ViewPager.OnPageChangeListener {
    Fragment fragments[];

    HanziDB refDB, myDB;

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

            mIndicator.setOnPageChangeListener(this);

        }
        else {
            // TODO When we are on a larger screen without a pager
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
            set_menu(menu_types.LESSON_SELECTION);
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

    public void open_lesson_list(String label) {
        if (fragments[2]!=null)
            close_lesson_list();
        fragments[2] = new LessonListFragment();
        Bundle bundle = new Bundle();
        bundle.putString("lesson", label);
        fragments[2].setArguments(bundle);
        LockableViewPager pager = (LockableViewPager) findViewById(R.id.voc_editor_pager);
        if (pager != null) {
            pager.getAdapter().notifyDataSetChanged();
            pager.setCurrentItem(2, true);
        }
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
    public void onPageScrolled(int i, float v, int i1) {
    }

    @Override
    public void onPageSelected(int i) {
        switch(i) {
            case 0:
                ((AddVocabularyFragment)fragments[0]).change_menu();
                break;
            case 1:
                ((EditLessonsFragment)fragments[1]).change_menu();
                break;
            case 2:
                ((LessonListFragment)fragments[2]).change_menu();
                break;
        }
    }

    @Override
    public void onPageScrollStateChanged(int i) {
    }

    @Override
    public void onBackPressed() {
        ViewPager pager = (ViewPager) findViewById(R.id.voc_editor_pager);
        if ((pager != null) && (pager.getCurrentItem() != 0))
            pager.setCurrentItem(0, true);
        else
            super.onBackPressed();
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

    public enum menu_types {
        INITIAL,
        VOCABULARY_NEW_SELECTED,
        VOCABULARY_CURRENT_SELECTED,
        VOCABULARY_CURRENT_MULTIPLE_SELECTED,
        VOCABULARY_ASSIGN_LESSON,
        VOCABULARY_READY,
        LESSON_NONE_SELECTED,
        LESSON_ONE_SELECTED,
        LESSON_MULTI_SELECTED,
        LESSON_SELECTION
    }
    private menu_types current_type=menu_types.INITIAL;

    public void set_menu(menu_types new_type) {
        current_type = new_type;
        invalidateOptionsMenu();
    }

    public menu_types get_menu() {
        return current_type;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.add_vocabulary_init, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        int menu_resource;
        switch(current_type) {
            case VOCABULARY_NEW_SELECTED:
                menu_resource = R.menu.add_vocabulary_menu_add;
                break;
            case VOCABULARY_CURRENT_SELECTED:
                menu_resource = R.menu.add_vocabulary_menu_edit;
                break;
            case VOCABULARY_CURRENT_MULTIPLE_SELECTED:
                menu_resource = R.menu.add_vocabulary_menu_edit_multiple;
                break;
            case VOCABULARY_READY:
                menu_resource = R.menu.add_vocabulary_menu_ready;
                break;
            case VOCABULARY_ASSIGN_LESSON:
                menu_resource = R.menu.add_vocabulary_assign_lesson;
                break;
            case LESSON_NONE_SELECTED:
                menu_resource = R.menu.add_vocabulary_lesson_none;
                break;
            case LESSON_ONE_SELECTED:
                menu_resource = R.menu.add_vocabulary_lesson_single;
                break;
            case LESSON_MULTI_SELECTED:
                menu_resource = R.menu.add_vocabulary_lesson_multiple;
                break;
            case LESSON_SELECTION:
                menu_resource = R.menu.add_vocabulary_lesson_selection;

                break;
            case INITIAL:
            default:
                menu_resource = R.menu.add_vocabulary_init;
        }
        menu.clear();
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(menu_resource, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String single_lesson;
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
                        ((LessonListFragment)fragments[2]).current_lesson())) {
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
            case R.id.add_vocabulary_assign_lesson:
                edit_lesson_assignment();
                return true;
            case R.id.add_vocabulary_assign_lesson_done:
                done_lesson_assignment();
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
                break;
            case R.id.add_vocabulary_lesson_clone:
                ((EditLessonsFragment)fragments[1]).clone_selection();
                break;
            case R.id.add_vocabulary_lesson_delete:
                selection = ((EditLessonsFragment)fragments[1]).get_selection();
                if (selection.size() != 1) {
                    ((EditLessonsFragment)fragments[1]).change_menu();
                    break;
                }
                single_lesson = selection.get(0);
                if ((fragments[2] != null) && (single_lesson != null) && (single_lesson.equals(((LessonListFragment)fragments[2]).current_lesson())))
                    close_lesson_list();
                ((EditLessonsFragment)fragments[1]).del_selection();
                break;
            case R.id.add_vocabulary_lesson_rename:
                selection = ((EditLessonsFragment)fragments[1]).get_selection();
                if (selection.size() != 1) {
                    ((EditLessonsFragment)fragments[1]).change_menu();
                    break;
                }
                single_lesson = selection.get(0);
                if ((fragments[2] != null) && (single_lesson != null) && (single_lesson.equals(((LessonListFragment)fragments[2]).current_lesson())))
                    close_lesson_list();
                ((EditLessonsFragment)fragments[1]).rename_selection();
                break;
            case R.id.add_vocabulary_lesson_merge:
                ((EditLessonsFragment)fragments[1]).merge_selection();
                break;
            case R.id.add_vocabulary_lesson_list:
                ((EditLessonsFragment)fragments[1]).list_selection();
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
            ((EditLessonsFragment)fragments[1]).change_menu();
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

    private void edit_lesson_assignment() {
        HashMap<String, LessonAssignment> assignment = ((AddVocabularyFragment)fragments[0]).get_current_lesson_assignment();
        ((EditLessonsFragment)fragments[1]).start_assigning_lesson(assignment);
        set_menu(menu_types.VOCABULARY_ASSIGN_LESSON);
        LockableViewPager pager = (LockableViewPager) findViewById(R.id.voc_editor_pager);
        if (pager != null) {
            pager.setCurrentItem(1, true);
            pager.setPagingEnabled(false);
        }
    }

    private void done_lesson_assignment() {
        HashMap<String, LessonAssignment> assignment = ((EditLessonsFragment)fragments[1]).get_assignment();
        ((AddVocabularyFragment)fragments[0]).set_new_lesson_assignment(assignment);
        LockableViewPager pager = (LockableViewPager) findViewById(R.id.voc_editor_pager);
        if (pager != null) {
            pager.setCurrentItem(0, true);
            pager.setPagingEnabled(true);
        }
        ((EditLessonsFragment)fragments[1]).redraw_list();
        if ((fragments[2] != null) && (assignment.containsKey(((LessonListFragment)fragments[2]).current_lesson()))) {
            ((LessonListFragment)fragments[2]).update_list();
        }
    }

    public static class LessonAssignment {
        boolean partiable;
        int state;
    }
}
