package com.matthieu;

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
import com.viewpagerindicator.PageIndicator;

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

        ViewPager pager = (ViewPager) findViewById(R.id.voc_editor_pager);

        if (savedInstanceState != null) {
            fragments[0] = getSupportFragmentManager().getFragment(savedInstanceState, "fragment0");
            fragments[1] = getSupportFragmentManager().getFragment(savedInstanceState, "fragment1");
            fragments[2] = getSupportFragmentManager().getFragment(savedInstanceState, "fragment2");
            if (pager != null) {
                pager.setCurrentItem(savedInstanceState.getInt("position"));
            }
        } else {
            fragments[0] = new AddVocabularyFragment();
            if (hanzi != null) {
                Bundle bundle = new Bundle();
                bundle.putString("hanzi", hanzi);
                fragments[0].setArguments(bundle);
            }
            fragments[1] = new EditLessonsFragment();
            fragments[2] = null;
        }


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
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        getSupportFragmentManager().putFragment(outState, "fragment0", fragments[0]);
        getSupportFragmentManager().putFragment(outState, "fragment1", fragments[1]);
        if (fragments[2] != null) {
            getSupportFragmentManager().putFragment(outState, "fragment2", fragments[2]);
        }
        ViewPager pager = (ViewPager) findViewById(R.id.voc_editor_pager);
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
        ViewPager pager = (ViewPager) findViewById(R.id.voc_editor_pager);
        if (pager != null) {
            pager.getAdapter().notifyDataSetChanged();
            pager.setCurrentItem(2, true);
        }
    }

    public void close_lesson_list() {
        ViewPager pager = (ViewPager) findViewById(R.id.voc_editor_pager);
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
        LESSON_FILTER
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
            //case VOCABULARY_ASSIGN_LESSON:
                //menu_resource = R.menu. ;
                //break;
            case LESSON_NONE_SELECTED:
                menu_resource = R.menu.add_vocabulary_lesson_none;
                break;
            case LESSON_ONE_SELECTED:
                menu_resource = R.menu.add_vocabulary_lesson_single;
                break;
            case LESSON_MULTI_SELECTED:
                menu_resource = R.menu.add_vocabulary_lesson_multiple;
                break;
            case LESSON_FILTER:
                menu_resource = R.menu.add_vocabulary_lesson_filter;
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
        switch (item.getItemId()) {
            case R.id.add_vocabulary_ready_add:
                ((AddVocabularyFragment)fragments[0]).add_new();
                break;
            case R.id.add_vocabulary_add_add:
                ((AddVocabularyFragment)fragments[0]).add_selection();
                return true;
            case R.id.add_vocabulary_edit_delete:
                ((AddVocabularyFragment)fragments[0]).del_selection();
                return true;
            case R.id.add_vocabulary_edit_edit:
                ((AddVocabularyFragment)fragments[0]).edit_selection();
                return true;
            case R.id.add_vocabulary_done:
                ((AddVocabularyFragment)fragments[0]).done();
                return true;

            case R.id.add_vocabulary_lesson_none_filter:
                ((EditLessonsFragment)fragments[1]).set_filter_start();
                break;
            case R.id.add_vocabulary_lesson_filter_all:
                ((EditLessonsFragment)fragments[1]).set_filter_all();
                break;
            case R.id.add_vocabulary_lesson_filter_none:
                ((EditLessonsFragment)fragments[1]).set_filter_none();
                break;
            case R.id.add_vocabulary_lesson_filter_done:
                ((EditLessonsFragment)fragments[1]).set_filter_done();
                break;
            case R.id.add_vocabulary_lesson_filter_enable:
                ((EditLessonsFragment)fragments[1]).set_filter_enable();
                break;

            case R.id.add_vocabulary_lesson_none_add:
                ((EditLessonsFragment)fragments[1]).add_new();
                break;
            case R.id.add_vocabulary_lesson_single_clone:
                ((EditLessonsFragment)fragments[1]).clone_selection();
                break;
            case R.id.add_vocabulary_lesson_delete:
                ((EditLessonsFragment)fragments[1]).del_selection();
                break;
            case R.id.add_vocabulary_lesson_single_rename:
                ((EditLessonsFragment)fragments[1]).rename_selection();
                break;
            case R.id.add_vocabulary_lesson_multiple_merge:
                ((EditLessonsFragment)fragments[1]).merge_selection();
                break;
            case R.id.add_vocabulary_lesson_list:
                ((EditLessonsFragment)fragments[1]).list_selection();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
