package com.matthieu;

import android.content.Context;
import android.content.ContextWrapper;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

public class ProgressDBTest extends ActivityInstrumentationTestCase2<EmptyActivity> {
    public ProgressDBTest() {
        super("com.matthieu", EmptyActivity.class);
    }

    private final String TAG="ProgressDBTest";
    protected static final String DbName = "progress_test_db";
    private ProgressDB target_db;
    private Context appContext;

    @Override
    protected void setUp() {
        Log.d(TAG, " setting up");
        appContext = getActivity().getApplicationContext();
        ContextWrapper cw = new ContextWrapper(appContext);
        cw.deleteDatabase(DbName);
        target_db = new ProgressDB(appContext, DbName);
        Log.d(TAG, " is setup");
    }

    @Override
    protected void tearDown() {
        target_db.close();
        getActivity().finish();
        Log.d(TAG, " is destroyed");
    }

    public void testAddData() {
        assertTrue(target_db.get_data(0).size()==0);
        ProgressDB.ProgressData data = new ProgressDB.ProgressData();
        data.num_expired_in_box =new int[] {10,10,10,10,10};
        data.num_in_box =new int[] {20,20,20,20,20};
        data.date=100000L;
        target_db.add_data(data);
        assertTrue(target_db.get_data(0).size()==1);
        assertTrue(target_db.get_data(0).get(0).date==100000L);
        assertTrue(target_db.get_data(0).get(0).num_in_box[1]==20);
        assertTrue(target_db.get_data(0).get(0).num_expired_in_box[1]==10);
    }

    public void testSortedData() {
        assertTrue(target_db.get_data(0).size()==0);
        ProgressDB.ProgressData data = new ProgressDB.ProgressData();
        data.num_expired_in_box =new int[] {10,10,10,10,10};
        data.num_in_box =new int[] {20,20,20,20,20};
        data.date=100000L;
        target_db.add_data(data);
        data.num_expired_in_box =new int[] {10,10,10,10,10};
        data.num_in_box =new int[] {30,30,30,30,30};
        data.date=50000L;
        target_db.add_data(data);
        data.num_expired_in_box =new int[] {20,20,20,20,20};
        data.num_in_box =new int[] {30,30,30,30,30};
        data.date=150000L;
        target_db.add_data(data);
        assertTrue(target_db.get_data(0).size()==3);
        assertTrue(target_db.get_data(0).get(0).date==50000L);
        assertTrue(target_db.get_data(0).get(1).date==100000L);
        assertTrue(target_db.get_data(0).get(2).date==150000L);
    }
}
