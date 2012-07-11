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
import com.matthieu.widget.ProgressGraph;
import java.util.ArrayList;

public class ProgressDB extends SQLiteOpenHelper {

    static public class ProgressData {
        public long date=0;
        public int num_chars_in_box[]={0,0,0,0,0};
        public int num_chars_expired_in_box[]={0,0,0,0,0};
    }

    private static final String TAG="ProgressDB";
    private static final int DATABASE_VERSION = 2;
    private static final int DEFAULT_MAX=40;
    private SQLiteDatabase db;

    public ProgressDB(Context context, String db_name) {
        super(context, db_name, null, DATABASE_VERSION);
        Log.d(TAG, "new ProgressDB "+db_name);
        Log.d(TAG, Thread.currentThread().getStackTrace().toString());
        db = this.getWritableDatabase();
        db.execSQL("PRAGMA synchronous=OFF");
        db.acquireReference();
        Log.d(TAG, "with db "+db);
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
                "expired_box_5 INTEGER," +
                "zoom INTEGER DEFAULT 0)");
        database.execSQL("CREATE INDEX progress_date ON progress(date)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int i, int i1) {
        if (i<2) {
            database.execSQL("ALTER TABLE progress ADD COLUMN zoom INTEGER DEFAULT 0");
            distribute(database);
        }
    }

    private void distribute_one_level(SQLiteDatabase db, long min_date, long max_date, int max_num, int zoom, int max_zoom) {
        long increment = (max_date - min_date) / max_num;
        ContentValues cv= new ContentValues();
        cv.put("zoom", zoom);

        Cursor check = db.rawQuery("SELECT date FROM tmp_progress WHERE date>=? AND date<=? AND zoom=-1",
                new String[] {Long.toString(min_date), Long.toString(max_date)});
        if (!check.moveToFirst()) {
            check.close();
            return;
        }
        check.close();

        for (int i=0; i<=max_num; i++) {
            long tmp_date = min_date + (i*increment);
            Cursor c = db.rawQuery("SELECT date, ABS(date-?) AS distance FROM tmp_progress WHERE date>=? AND date<=? AND zoom=-1 ORDER BY distance ASC LIMIT 1",
                    new String[] {Long.toString(tmp_date), Long.toString(tmp_date-increment/2),
                            Long.toString(tmp_date+increment/2)});

            if (c.moveToFirst()) {
                int date_column = c.getColumnIndex("date");
                long date = c.getLong(date_column);
                db.update("tmp_progress", cv, "date=?", new String[] {Long.toString(date)});
            }
            c.close();
            if ((i>0) && (zoom < max_zoom))
                distribute_one_level(db, tmp_date - increment, tmp_date, max_num, zoom+1, max_zoom);
        }
    }

    private void distribute(final SQLiteDatabase db) {
        Log.w(TAG, "Distribute!");

        Thread distributer = new Thread() {
            @Override
            public void run() {
                // setup a new table as a copy for the zoom values
                db.execSQL("DROP TABLE IF EXISTS tmp_progress");
                db.execSQL("CREATE TABLE tmp_progress (" +
                        "date INTEGER PRIMARY KEY NOT NULL, " +
                        "total_box_1 INTEGER, " +
                        "total_box_2 INTEGER, " +
                        "total_box_3 INTEGER, " +
                        "total_box_4 INTEGER, " +
                        "total_box_5 INTEGER, " +
                        "expired_box_2 INTEGER, " +
                        "expired_box_3 INTEGER, " +
                        "expired_box_4 INTEGER, " +
                        "expired_box_5 INTEGER," +
                        "zoom INTEGER DEFAULT 0)");
                db.execSQL("INSERT INTO tmp_progress SELECT date,total_box_1, total_box_2, total_box_3," +
                        "total_box_4, total_box_5, expired_box_2, expired_box_3, expired_box_4, expired_box_5, zoom " +
                        "FROM progress");

                // set everything to -1
                ContentValues cv= new ContentValues();
                cv.put("zoom", -1);
                db.update("tmp_progress", cv, "", null);

                // get the maximum zoom level
                Cursor c = db.rawQuery("SELECT MIN(date) AS date_min, MAX(date) AS date_max " +
                        "FROM tmp_progress", null);
                if (!c.moveToFirst()) {// really does not look like there is anything to distribute
                    c.close();
                    return;
                }
                long min_date = c.getLong(c.getColumnIndex("date_min"));
                long max_date = c.getLong(c.getColumnIndex("date_max"));
                c.close();
                if (min_date == max_date)
                    return;
                int max_zoom = ProgressGraph.getMax_zoom(min_date, max_date);
                Log.w(TAG, "Max zoom is "+max_zoom);

                // set the extremeties at the level 0
                cv.put("zoom", 0);
                db.update("tmp_progress", cv, "date=? OR date=?", new String[] {Long.toString(min_date), Long.toString(max_date)});

                // start the recursiveness
                distribute_one_level(db, min_date, max_date, DEFAULT_MAX, 0, max_zoom-1);

                // delete anything that would be left
                db.delete("tmp_progress", "zoom=?", new String[] {"-1"});

                db.execSQL("BEGIN TRANSACTION");
                db.execSQL("DROP TABLE progress");
                db.execSQL("CREATE TABLE progress (" +
                        "date INTEGER PRIMARY KEY NOT NULL, " +
                        "total_box_1 INTEGER, " +
                        "total_box_2 INTEGER, " +
                        "total_box_3 INTEGER, " +
                        "total_box_4 INTEGER, " +
                        "total_box_5 INTEGER, " +
                        "expired_box_2 INTEGER, " +
                        "expired_box_3 INTEGER, " +
                        "expired_box_4 INTEGER, " +
                        "expired_box_5 INTEGER," +
                        "zoom INTEGER DEFAULT 0)");
                db.execSQL("CREATE INDEX progress_date ON progress(date)");
                db.execSQL("INSERT INTO progress SELECT date,total_box_1, total_box_2, total_box_3," +
                        "total_box_4, total_box_5, expired_box_2, expired_box_3, expired_box_4, expired_box_5, zoom " +
                        "FROM tmp_progress");
                db.execSQL("DROP TABLE tmp_progress");
                db.execSQL("COMMIT");
            }
        };

        distributer.start();
    }

    private void check_to_distribute(final SQLiteDatabase db) {
        // check how many at level 0 towards the end
        Cursor c = db.rawQuery("SELECT COUNT(date) AS count FROM progress WHERE date > (SELECT MAX(date) FROM progress WHERE zoom!=0)", new String[] {});
        if (!c.moveToFirst())
            return;
        int count = c.getInt(c.getColumnIndex("count"));
        c.close();
        if (count > 10)
            distribute(db);

    }

    @Override
    public synchronized void close() {
        db.releaseReference();
        db.close();
        super.close();
    }

    public void add_data(ProgressData data) {
        Log.d(TAG, "Adding progress data");
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
        check_to_distribute(db);
    }

    public ArrayList<ProgressData> get_data(int zoom) {
        ArrayList<ProgressData> res=new ArrayList<ProgressData>();
        Cursor cur = db.rawQuery("SELECT * FROM progress WHERE zoom<=? ORDER BY date ASC", new String[]{Integer.toString(zoom)});
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

    public ArrayList<ProgressData> get_data(int zoom, long date_min, long date_max) {
        ArrayList<ProgressData> res=new ArrayList<ProgressData>();

        Cursor cur;
        cur = db.rawQuery("SELECT MAX(date) AS max_date FROM progress WHERE zoom <=? AND date<=?",
                new String[]{Integer.toString(zoom), Long.toString(date_min)});
        if (cur.moveToFirst()) {
            long date_before = cur.getLong(cur.getColumnIndex("max_date"));
            cur.close();
            cur=db.rawQuery("SELECT * FROM progress WHERE date=?", new String[] {Long.toString(date_before)});
            if (cur.moveToFirst()) {
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
            }
            cur.close();
        }

        cur = db.rawQuery("SELECT * FROM progress WHERE zoom<=? AND date>=? AND date<=? ORDER BY date ASC",
                new String[]{Integer.toString(zoom), Long.toString(date_min), Long.toString(date_max)});
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

        cur = db.rawQuery("SELECT MIN(date) AS min_date FROM progress WHERE zoom <=? AND date>=?",
                new String[]{Integer.toString(zoom), Long.toString(date_max)});
        if (cur.moveToFirst()) {
            long date_after = cur.getLong(cur.getColumnIndex("min_date"));
            cur.close();
            cur=db.rawQuery("SELECT * FROM progress WHERE date=?", new String[] {Long.toString(date_after)});
            if (cur.moveToFirst()) {
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
            }
            cur.close();
        }

        return res;
    }

    public int get_max_chars() {
        Cursor cur = db.rawQuery("SELECT total_box_1+total_box_2+total_box_3+total_box_4+total_box_5 AS sum " +
                "FROM progress ORDER BY sum DESC LIMIT 1", new String[] {});
        int res=0;
        if (cur.moveToFirst())
            res = cur.getInt(cur.getColumnIndex("sum"));
        cur.close();
        return res;
    }

    public void delete_data(long to_keep) {
        if (to_keep==-1)
            db.delete("progress", "", new String[] {});
        else
            db.delete("progress", "date<=?", new String[] {Long.toString(System.currentTimeMillis()-to_keep)});
    }
}
