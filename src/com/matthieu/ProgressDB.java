/******************************
 * Class holding a database of the progress made during the study
 */
package com.matthieu;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.PriorityQueue;

public class ProgressDB extends SQLiteOpenHelper{

    static public class ProgressData {
        public long date=0;
        public int num_chars_in_box[]={0,0,0,0,0};
        public int num_chars_expired_in_box[]={0,0,0,0,0};
    }

    private static final String TAG="ProgressDB";
    private static final int DATABASE_VERSION = 1;
    private static final int DEFAULT_MAX=200;
    private SQLiteDatabase db;
    private AndZiApplication app;

    public ProgressDB(Context context, AndZiApplication application, String db_name) {
        super(context, db_name, null, DATABASE_VERSION);
        app = application;
        db = this.getWritableDatabase();
        db.execSQL("PRAGMA synchronous=OFF");
        db.acquireReference();
        purge_data(DEFAULT_MAX);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        Log.v(TAG, "onCreate ProgressDB...");

        database.execSQL("DROP TABLE IF EXISTS progress");

        database.execSQL("CREATE TABLE progress (" +
                "date INTEGER PRIMARY KEY NOT NULL, " +
                "total_box_1 INTEGER, " +
                "total_box_2 INTEGER, " +
                "total_box_3 INTEGER, " +
                "total_box_4 INTEGER, " +
                "total_box_5 INTEGER, " +
                "expired_box_2 INTEGER, " +
                "expired_box_3 INTEGER, " +
                "expired_box_4 INTEGER, " +
                "expired_box_5 INTEGER)");
        database.execSQL("CREATE INDEX progress_date ON progress(date)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
    }

    public void add_data() {
        ProgressData new_data = app.getCurrentProgress();
        add_data(new_data);
    }

    public void add_data(ProgressData data) {
        ArrayList<ProgressData> latest = get_data(1);
        if (latest != null) {
            if (latest.get(0).date - data.date < 1000 * 60 * 60 * 24) {
                int latest_total = latest.get(0).num_chars_in_box[0]+latest.get(0).num_chars_in_box[1]+latest.get(0).num_chars_in_box[2]+
                        latest.get(0).num_chars_in_box[3]+latest.get(0).num_chars_in_box[4];
                int current_total = data.num_chars_in_box[0]+data.num_chars_in_box[1]+data.num_chars_in_box[2]+
                        data.num_chars_in_box[3]+data.num_chars_in_box[4];
                if (100 *((float)Math.abs(latest_total-current_total))/(Math.min(latest_total, current_total)) < 5) {
                    int diff = Math.abs(latest.get(0).num_chars_in_box[0]-data.num_chars_in_box[0])+Math.abs(latest.get(0).num_chars_in_box[1]-data.num_chars_in_box[1])+
                            Math.abs(latest.get(0).num_chars_in_box[2]-data.num_chars_in_box[2])+Math.abs(latest.get(0).num_chars_in_box[3]-data.num_chars_in_box[3])+
                            Math.abs(latest.get(0).num_chars_in_box[4]-data.num_chars_in_box[4]);
                    if (100 *((float)diff)/(Math.min(latest_total, current_total)) < 5) {
                        int expired_diff= Math.abs(latest.get(0).num_chars_expired_in_box[1]-data.num_chars_expired_in_box[1])+
                                Math.abs(latest.get(0).num_chars_expired_in_box[2]-data.num_chars_expired_in_box[2])+Math.abs(latest.get(0).num_chars_expired_in_box[3]-data.num_chars_expired_in_box[3])+
                                Math.abs(latest.get(0).num_chars_expired_in_box[4]-data.num_chars_expired_in_box[4]);
                        if (100 *((float)expired_diff)/(Math.min(latest_total, current_total)) < 5) {
                            // not enough changes
                            return;
                        }
                    }
                }
            }
        }
        Log.d(TAG, "Adding values!");
        ContentValues cv = new ContentValues();
        cv.put("date", data.date);
        cv.put("total_box_1", data.num_chars_in_box[0]);
        cv.put("total_box_2", data.num_chars_in_box[1]);
        cv.put("total_box_3", data.num_chars_in_box[2]);
        cv.put("total_box_4", data.num_chars_in_box[3]);
        cv.put("total_box_5", data.num_chars_in_box[4]);
        cv.put("expired_box_2", data.num_chars_expired_in_box[1]);
        cv.put("expired_box_3", data.num_chars_expired_in_box[2]);
        cv.put("expired_box_4", data.num_chars_expired_in_box[3]);
        cv.put("expired_box_5", data.num_chars_expired_in_box[4]);
        db.insert("progress", null, cv);
        purge_data(DEFAULT_MAX);
    }

    public ArrayList<ProgressData> get_data() {
        ArrayList<ProgressData> res=new ArrayList<ProgressData>();
        Cursor cur = db.rawQuery("SELECT * FROM progress ORDER BY date ASC", new String[]{});
        if (cur.moveToFirst()) {
            res.ensureCapacity(cur.getCount());
            do {
                ProgressData data = new ProgressData();
                data.date = cur.getLong(cur.getColumnIndex("date"));
                data.num_chars_in_box[0]=cur.getInt(cur.getColumnIndex("total_box_1"));
                data.num_chars_in_box[1]=cur.getInt(cur.getColumnIndex("total_box_2"));
                data.num_chars_in_box[2]=cur.getInt(cur.getColumnIndex("total_box_3"));
                data.num_chars_in_box[3]=cur.getInt(cur.getColumnIndex("total_box_4"));
                data.num_chars_in_box[4]=cur.getInt(cur.getColumnIndex("total_box_5"));
                data.num_chars_expired_in_box[0]=data.num_chars_in_box[0];
                data.num_chars_expired_in_box[1]=cur.getInt(cur.getColumnIndex("expired_box_2"));
                data.num_chars_expired_in_box[2]=cur.getInt(cur.getColumnIndex("expired_box_3"));
                data.num_chars_expired_in_box[3]=cur.getInt(cur.getColumnIndex("expired_box_4"));
                data.num_chars_expired_in_box[4]=cur.getInt(cur.getColumnIndex("expired_box_5"));

                res.add(data);
            } while (cur.moveToNext());
            Log.d(TAG, "Got "+res.size()+" states for progress");
        }
        cur.close();
        return res;
    }

    public ArrayList<ProgressData> get_data(int num) {
        ArrayList<ProgressData> res=null;
        if (num <=0)
            return null;
        Cursor cur = db.rawQuery("SELECT * FROM progress ORDER BY date ASC LIMIT "+num, new String[]{});
        if (cur.moveToFirst()) {
            res = new ArrayList<ProgressData>();
            res.ensureCapacity(cur.getCount());
            do {
                ProgressData data = new ProgressData();
                data.date = cur.getLong(cur.getColumnIndex("date"));
                data.num_chars_in_box[0]=cur.getInt(cur.getColumnIndex("total_box_1"));
                data.num_chars_in_box[1]=cur.getInt(cur.getColumnIndex("total_box_2"));
                data.num_chars_in_box[2]=cur.getInt(cur.getColumnIndex("total_box_3"));
                data.num_chars_in_box[3]=cur.getInt(cur.getColumnIndex("total_box_4"));
                data.num_chars_in_box[4]=cur.getInt(cur.getColumnIndex("total_box_5"));
                data.num_chars_expired_in_box[0]=data.num_chars_in_box[0];
                data.num_chars_expired_in_box[1]=cur.getInt(cur.getColumnIndex("expired_box_2"));
                data.num_chars_expired_in_box[2]=cur.getInt(cur.getColumnIndex("expired_box_3"));
                data.num_chars_expired_in_box[3]=cur.getInt(cur.getColumnIndex("expired_box_4"));
                data.num_chars_expired_in_box[4]=cur.getInt(cur.getColumnIndex("expired_box_5"));

                res.add(data);
            } while (cur.moveToNext());
        }
        cur.close();
        return res;
    }

    public void delete_data(long max_date) {
       db.delete("progress", "date<=?", new String[] {Long.toString(max_date)});
    }

    public void purge_data(int min_items) {
        ArrayList<ProgressData> data = get_data();
        
        if (data.size() < min_items)
            return;

        long min_date = data.get(0).date;
        long max_date = data.get(data.size()-1).date;
        HashMap<ProgressData, Double> deltas = new HashMap<ProgressData, Double>();
        for (int i=1; i<data.size()-1; i++) {
            double delta = (data.get(i + 1).date - data.get(i - 1).date) * Math.sqrt((double)(data.get(i).date - min_date) / (max_date - min_date));
            deltas.put(data.get(i), delta);
        }

        while (deltas.size() > min_items-2) {
            int min_index=0;
            double min_value=-1;
            for (int i=1; i<data.size()-1; i++) {
                if ((deltas.containsKey(data.get(i))) &&
                        ((min_value == -1) || (deltas.get(data.get(i)) < min_value))) {
                    min_value = deltas.get(data.get(i));
                    min_index = i;
                }
            }
            if (min_index == 0)
                return;
            int res = db.delete("progress", "date=?", new String[] {Long.toString(data.get(min_index).date)});
            Log.d(TAG, "Deleted "+res+" rows (date "+data.get(min_index).date+", delta "+min_value+")");
            deltas.remove(data.get(min_index));
            data.remove(min_index);

            // recomputes neighbors
            double delta;
            if (min_index < data.size()-1) {
                delta = (data.get(min_index + 1).date - data.get(min_index - 1).date) *
                        Math.sqrt((double)(data.get(min_index).date - min_date) / (max_date - min_date));
                deltas.remove(data.get(min_index));
                deltas.put(data.get(min_index), delta);
            }

            if (min_index > 1) {
                delta = (data.get(min_index).date - data.get(min_index - 2).date) *
                        Math.sqrt((double)(data.get(min_index-1).date - min_date) / (max_date - min_date));
                deltas.remove(data.get(min_index-1));
                deltas.put(data.get(min_index-1), delta);
            }
        }
    }
}
