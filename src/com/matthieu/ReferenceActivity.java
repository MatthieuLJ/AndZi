package com.matthieu;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.v4.app.*;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.matthieu.widget.ViewPagerParallax;
import com.matthieu.widget.handle_details_request;
import com.viewpagerindicator.PageIndicator;

public class ReferenceActivity extends SherlockFragmentActivity implements handle_details_request, ViewPager.OnPageChangeListener{
    final private static int max_details_fragments=3;
    Fragment fragments[];
    int ids[];
    final private static String TAG = "RerefenceActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock);
        requestWindowFeature(com.actionbarsherlock.view.Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.reference);

        ViewPagerParallax pager = (ViewPagerParallax) findViewById(R.id.reference_pager);

        fragments = new Fragment[max_details_fragments+1];
        ids = new int[max_details_fragments+1];

        if (savedInstanceState != null) {
            for (int i=0; i<=max_details_fragments; i++) {
                fragments[i] = getSupportFragmentManager().getFragment(savedInstanceState, "fragment"+i);
            }

            if (pager == null) {
                ids = savedInstanceState.getIntArray("ids");

                LinearLayout master_linear = (LinearLayout) findViewById(R.id.ref_linear);
                if (master_linear!=null) {
                    for (int i=1; i<=max_details_fragments; i++) {
                        master_linear.getChildAt(i).setId(ids[i]);
                        if (fragments[i]!=null)
                            master_linear.getChildAt(i).setVisibility(View.VISIBLE);
                    }
                }

                TableLayout master_table = (TableLayout) findViewById(R.id.ref_table);
                if (master_table != null) {
                    for (int i=1; i<=max_details_fragments; i++) {
                        ((TableRow)master_table.getChildAt(i/2)).getChildAt(i%2).setId(ids[i]);
                        if (fragments[i]!=null) {
                            ((TableRow)master_table.getChildAt(i/2)).setVisibility(View.VISIBLE);
                            ((TableRow)master_table.getChildAt(i/2)).getChildAt(i%2).setVisibility(View.VISIBLE);
                        }
                    }
                }
            }

        } else {
            TypedArray ar = getResources().obtainTypedArray(R.array.reference_frame_ids);
            for (int i=0; i<=max_details_fragments; i++)
                ids[i] = ar.getResourceId(i, 0);
            ar.recycle();

            fragments[0] = new DictionaryFragment();

            if (pager == null) {
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.add(R.id.ref_fragment0, fragments[0]);
                ft.commit();
            }
        }

        if (pager != null) {
            pager.set_max_pages(max_details_fragments+1);
            pager.setBackgroundAsset(R.raw.treatise);
            pager.setOffscreenPageLimit(max_details_fragments+1);
            pager.setAdapter(new reference_pager(getSupportFragmentManager()));

            PageIndicator mIndicator = (PageIndicator) findViewById(R.id.pager_indicator);
            mIndicator.setViewPager(pager);
            mIndicator.setOnPageChangeListener(this);
            if (savedInstanceState != null)
                pager.setCurrentItem(savedInstanceState.getInt("position"));
        }

        String hanzi = getIntent().getStringExtra("Hanzi");
        boolean use_ref = getIntent().getBooleanExtra("use_ref", false);
        if (hanzi!=null) {
            new_details_request(hanzi, use_ref);
        }

        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            getSupportFragmentManager().putFragment(outState, "fragment0", fragments[0]);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Got an exception saving fragment 0 " + e);
        }
        for (int i=1; i<=max_details_fragments; i++) {
            if (fragments[i] != null) {
                try {
                    getSupportFragmentManager().putFragment(outState, "fragment"+i, fragments[i]);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Got an exception saving fragment "+i+" "+e);
                }
            }
        }
        ViewPager pager = (ViewPager) findViewById(R.id.test_pager);
        if (pager != null) {
            outState.putInt("position", pager.getCurrentItem());
        }
        outState.putIntArray("ids", ids);
    }

    String new_query_to_open = null;
    boolean new_query_use_ref = false;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String hanzi = intent.getStringExtra("Hanzi");
        boolean use_ref = intent.getBooleanExtra("use_ref", false);
        if (hanzi!=null) {
            new_query_to_open = hanzi;
            new_query_use_ref = use_ref;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (new_query_to_open != null) {
            new_details_request(new_query_to_open, new_query_use_ref);
            new_query_to_open=null;
        }
    }

    @Override
    public void new_details_request(String new_hanzi, boolean use_ref) {
        // Check if that request does not exist already
        for (int i=1; i<=max_details_fragments; i++) {
            if (fragments[i] == null)
                break;
            if ((((DetailsFragment)fragments[i]).is_dictionary_search() == use_ref) &&
                ((DetailsFragment)fragments[i]).get_hanzi().equals(new_hanzi)) {
                ViewPager pager = (ViewPager) findViewById(R.id.reference_pager);
                if (pager != null) {
                    pager.setCurrentItem(i, true);
                }
                return;
            }
        }

        // Insert the new fragment
        int max;
        if (fragments[max_details_fragments] == null) {
            // we still have space, just have to shift everything else
            int index;
            for (index = max_details_fragments; index>0; index--)
                if (fragments[index]!=null)
                    break;
            max = index+1;
        } else {
            // find the rightmost fragment not pinned
            int index;
            for (index = max_details_fragments; index>0; index--)
                if (!((DetailsFragment)fragments[index]).isPinned())
                    break;
            if (index == 0) {
                AlertDialog.Builder too_many_pinned = new AlertDialog.Builder(ReferenceActivity.this);
                too_many_pinned.setCancelable(false);
                too_many_pinned.setMessage(getResources().getString(R.string.reference_too_many_pinned));
                too_many_pinned.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });
                too_many_pinned.create();
                too_many_pinned.show();
                return;
            }

            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            ft.remove(fragments[index]);
            ft.commit();

            max = index;
        }

        ViewPager pager = (ViewPager) findViewById(R.id.reference_pager);

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        // shift
        for (int i=max; i>1; i--) {
            fragments[i] = fragments[i-1];
            ids[i] = ids[i-1];
        }

        // insert the new one
        fragments[1] = new DetailsFragment();
        Bundle bundle = new Bundle();
        bundle.putString("hanzi", new_hanzi);
        bundle.putString("pinyin", "");
        bundle.putBoolean("use_ref", use_ref);
        fragments[1].setArguments(bundle);

        LinearLayout master_linear = (LinearLayout) findViewById(R.id.ref_linear);
        if (master_linear != null) {
            int id_removed = master_linear.getChildAt(max).getId();
            master_linear.removeViewAt(max);
            FrameLayout fl = new FrameLayout(this);
            fl.setId(id_removed);
            fl.setVisibility(View.VISIBLE);
            master_linear.addView(fl, 1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            ft.add(id_removed, fragments[1]);
            ids[1] = id_removed;
        }

        TableLayout master_table = (TableLayout) findViewById(R.id.ref_table);
        if (master_table != null) {
            int row = max / 2;
            TableRow tr = (TableRow) master_table.getChildAt(row);
            int id_removed = tr.getChildAt(max % 2).getId();
            tr.removeViewAt(max%2);
            // shift
            for (int r=max/2; r>0; r--) {
                FrameLayout moving_fl = (FrameLayout) ((TableRow) master_table.getChildAt(r-1)).getChildAt(1);
                ((TableRow) master_table.getChildAt(r-1)).removeViewAt(1);
                ((TableRow) master_table.getChildAt(r)).addView(moving_fl, 0);
                ((TableRow) master_table.getChildAt(r)).setVisibility(View.VISIBLE);
            }
            FrameLayout fl = new FrameLayout(this);
            fl.setId(id_removed);
            fl.setVisibility(View.VISIBLE);
            ((TableRow) master_table.getChildAt(0)).addView(fl, 1, new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            ft.add(id_removed, fragments[1]);
            ids[1] = id_removed;
        }

        ft.commit();

        // refresh

        if (pager != null) {
            pager.getAdapter().notifyDataSetChanged();
            pager.setCurrentItem(1, true);
        }
    }

    @Override
    public void onPageScrolled(int i, float v, int i1) {}

    @Override
    public void onPageSelected(int i) {
        invalidateOptionsMenu();
    }

    @Override
    public void onPageScrollStateChanged(int i) {}

    @Override
    public void onBackPressed() {
        ViewPager pager = (ViewPager) findViewById(R.id.reference_pager);
        if ((pager != null) && (pager.getCurrentItem() != 0) && (((DictionaryFragment)fragments[0]).has_search()))
            pager.setCurrentItem(0, true);
        else
            super.onBackPressed();
    }

    // use a FragmentPagerAdapter here because we are moving pages around (FragmentStatePagerAdapter does not do that well : http://code.google.com/p/android/issues/detail?id=37990)
    private class reference_pager extends FragmentPagerAdapter {
        public reference_pager(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            return fragments[i];
        }

        @Override
        public float getPageWidth(int position) {
            if (((getResources().getConfiguration().screenLayout &
                    Configuration.SCREENLAYOUT_SIZE_MASK) ==
                    Configuration.SCREENLAYOUT_SIZE_LARGE) && (getCount() > 1)) {
                return 0.5f;
            } else {
                return 1.0f;
            }
        }

        @Override
        public long getItemId(int position) {
            return fragments[position].hashCode();
        }

        @Override
        public int getCount() {
            int max;
            for (max=1; max<=max_details_fragments; max++) {
                if (fragments[max] == null)
                    break;
            }
            return max;
        }

        @Override
        public int getItemPosition(Object object) {
            int res = POSITION_NONE;
            Fragment fragment = (Fragment) object;
            for (int i=0; i<max_details_fragments+1; i++) {
                if (fragment.equals(fragments[i])) {
                    res = i;
                    break;
                }
            }

            return res;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        ViewPager pager = (ViewPager) findViewById(R.id.reference_pager);

        menu.clear();
        MenuInflater inflater = getSupportMenuInflater();

        if (pager != null) {
            int page = pager.getCurrentItem();
            if (page == 0) {
                inflater.inflate(R.menu.reference_dict, menu);

                MenuItem source = menu.findItem(R.id.reference_source);
                if (((DictionaryFragment)fragments[0]).is_dictionary_search())
                    source.setTitle(R.string.details_dictionary);
                else
                    source.setTitle(R.string.details_voc_list);

            } else {
                inflater.inflate(R.menu.reference_details, menu);
                MenuItem source = menu.findItem(R.id.details_source);
                source.setEnabled(true);
                if (((DetailsFragment)fragments[page]).is_dictionary_search()) {
                    source.setTitle(R.string.details_dictionary);
                    if (!((DetailsFragment)fragments[page]).exists_in_db())
                        source.setEnabled(false);
                } else
                    source.setTitle(R.string.details_voc_list);

                MenuItem pinned = menu.findItem(R.id.details_pinned);
                if (((DetailsFragment)fragments[page]).isPinned())
                    pinned.setTitle(R.string.details_unpin);
                else
                    pinned.setTitle(R.string.details_pin);

                MenuItem add_edit = menu.findItem(R.id.details_add_edit);
                if (((DetailsFragment)fragments[page]).exists_in_db())
                    add_edit.setTitle(R.string.details_edit);
                else
                    add_edit.setTitle(R.string.details_add);

            }

            return true;
        }

        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ViewPager pager = (ViewPager) findViewById(R.id.reference_pager);
        int page=0;
        if (pager!= null)
            page = pager.getCurrentItem();

        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.reference_source:
                ((DictionaryFragment)fragments[0]).change_source();
                invalidateOptionsMenu();
                break;
            case R.id.details_add_edit:
                ((DetailsFragment)fragments[page]).edit_add_word();
                break;
            case R.id.details_pinned:
                ((DetailsFragment)fragments[page]).pin_unpin();
                invalidateOptionsMenu();
                break;
            case R.id.details_source:
                ((DetailsFragment)fragments[page]).change_source();
                invalidateOptionsMenu();
                break;
            default:
                return false;
        }
        return true;
    }
}
