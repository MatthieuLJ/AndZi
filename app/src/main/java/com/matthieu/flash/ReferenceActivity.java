package com.matthieu.flash;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.widget.ViewPager2;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.matthieu.flash.widget.handle_details_request;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class ReferenceActivity extends FragmentActivity implements handle_details_request {
    final private static int max_details_fragments=3;
    Fragment[] fragments;
    int[] ids;

    private static final boolean DEBUG = false;
    final private static String TAG = "RerefenceActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO: setTheme(R.style.Theme_Sherlock);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.reference);

        ViewPager2 pager = findViewById(R.id.reference_pager);

        fragments = new Fragment[max_details_fragments+1];
        ids = new int[max_details_fragments+1];

        if (savedInstanceState != null) {
            for (int i=0; i<=max_details_fragments; i++) {
                fragments[i] = getSupportFragmentManager().getFragment(savedInstanceState, "fragment"+i);
            }

            if (pager == null) {
                ids = savedInstanceState.getIntArray("ids");

                LinearLayout master_linear = findViewById(R.id.ref_linear);
                if (master_linear!=null) {
                    for (int i=1; i<=max_details_fragments; i++) {
                        master_linear.getChildAt(i).setId(ids[i]);
                        if (fragments[i]!=null)
                            master_linear.getChildAt(i).setVisibility(View.VISIBLE);
                    }
                }

                LinearLayout master_table = findViewById(R.id.ref_table);
                if (master_table != null) {
                    for (int i=1; i<=max_details_fragments; i++) {
                        ((LinearLayout)master_table.getChildAt(i/2)).getChildAt(i%2).setId(ids[i]);
                        if (fragments[i]!=null) {
                            master_table.getChildAt(i/2).setVisibility(View.VISIBLE);
                            ((LinearLayout)master_table.getChildAt(i/2)).getChildAt(i%2).setVisibility(View.VISIBLE);
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
                androidx.fragment.app.FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.add(R.id.ref_fragment0, fragments[0]);
                ft.commit();
            }
        }

        if (pager != null) {
            pager.setOffscreenPageLimit(max_details_fragments+1);
            pager.setAdapter(new reference_pager(getSupportFragmentManager(), ReferenceActivity.this.getLifecycle()));

            if (savedInstanceState != null)
                pager.setCurrentItem(savedInstanceState.getInt("position"));
        }

        String hanzi = getIntent().getStringExtra("Hanzi");
        boolean use_ref = getIntent().getBooleanExtra("use_ref", false);
        if (hanzi!=null) {
            new_details_request(hanzi, use_ref);
        }


        OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                ViewPager2 pager = findViewById(R.id.reference_pager);
                if ((pager != null) && (pager.getCurrentItem() != 0) && (((DictionaryFragment)fragments[0]).has_search()))
                    pager.setCurrentItem(0, true);
                else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };

        getOnBackPressedDispatcher().addCallback(this, backCallback);

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            getSupportFragmentManager().putFragment(outState, "fragment0", fragments[0]);
        } catch (IllegalStateException e) {
            if (DEBUG) Log.e(TAG, "Got an exception saving fragment 0 " + e);
        }
        for (int i=1; i<=max_details_fragments; i++) {
            if (fragments[i] != null) {
                try {
                    getSupportFragmentManager().putFragment(outState, "fragment"+i, fragments[i]);
                } catch (IllegalStateException e) {
                    if (DEBUG) Log.e(TAG, "Got an exception saving fragment "+i+" "+e);
                }
            }
        }
        ViewPager2 pager = findViewById(R.id.test_pager);
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
                ViewPager2 pager = findViewById(R.id.reference_pager);
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
            androidx.fragment.app.FragmentTransaction ft = fm.beginTransaction();
            ft.remove(fragments[index]);
            ft.commit();

            max = index;
        }

        ViewPager2 pager = findViewById(R.id.reference_pager);

        FragmentManager fm = getSupportFragmentManager();
        androidx.fragment.app.FragmentTransaction ft = fm.beginTransaction();

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

        LinearLayout master_linear = findViewById(R.id.ref_linear);
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

        LinearLayout master_table = findViewById(R.id.ref_table);
        if (master_table != null) {
            int row = max / 2;
            LinearLayout tr = (LinearLayout) master_table.getChildAt(row);
            int id_removed = tr.getChildAt(max % 2).getId();
            tr.removeViewAt(max%2);
            // shift
            for (int r=max/2; r>0; r--) {
                FrameLayout moving_fl = (FrameLayout) ((LinearLayout) master_table.getChildAt(r-1)).getChildAt(1);
                ((LinearLayout) master_table.getChildAt(r-1)).removeViewAt(1);
                ((LinearLayout) master_table.getChildAt(r)).addView(moving_fl, 0);
                master_table.getChildAt(r).setVisibility(View.VISIBLE);
            }
            FrameLayout fl = new FrameLayout(this);
            fl.setId(id_removed);
            fl.setVisibility(View.VISIBLE);
            ((LinearLayout) master_table.getChildAt(0)).addView(fl, 1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
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

    private class pageChangeCallback extends ViewPager2.OnPageChangeCallback {
        @Override
        public void onPageSelected(int position) {
            invalidateOptionsMenu();
        }
        @Override
        public void onPageScrollStateChanged(int state) {}
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
    }

    // use a FragmentPagerAdapter here because we are moving pages around (FragmentStatePagerAdapter does not do that well : http://code.google.com/p/android/issues/detail?id=37990)
    private class reference_pager extends FragmentStateAdapter {
        public reference_pager(FragmentManager fm, Lifecycle lf) {
            super(fm, lf);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return fragments[position];
        }

        @Override
        public long getItemId(int position) {
            return fragments[position].hashCode();
        }

        @Override
        public int getItemCount() {
            int max;
            for (max=1; max<=max_details_fragments; max++) {
                if (fragments[max] == null)
                    break;
            }
            return max;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        ViewPager2 pager = findViewById(R.id.reference_pager);

        menu.clear();
        MenuInflater inflater = getMenuInflater();

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
        ViewPager2 pager = findViewById(R.id.reference_pager);
        int page=0;
        if (pager!= null)
            page = pager.getCurrentItem();

        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.reference_source) {
            ((DictionaryFragment) fragments[0]).change_source();
            invalidateOptionsMenu();
        } else if (itemId == R.id.details_add_edit) {
            ((DetailsFragment) fragments[page]).edit_add_word();
        } else if (itemId == R.id.details_pinned) {
            ((DetailsFragment) fragments[page]).pin_unpin();
            invalidateOptionsMenu();
        } else if (itemId == R.id.details_source) {
            ((DetailsFragment) fragments[page]).change_source();
            invalidateOptionsMenu();
        } else {
            return false;
        }
        return true;
    }
}
