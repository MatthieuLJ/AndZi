package com.matthieu;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * User: matthieu
 * Date: 6/7/11
 * Time: 3:41 PM
 */
public class HanziDBleitner extends HanziDBlesson {
    private static final String TAG="HanziDBleitner";

    private static final long two_hours = 2 * 60 * 60 * 1000;
    private static final long two_days = 2 * 24 * 60 * 60 * 1000;
    private static final long two_weeks = 2 * 7 * 24 * 60 * 60 * 1000;
    private static final long two_months = 2 * 30 * 24 * 60 * 60 * 1000;

    public HanziDBleitner(Context context) {
        super(context);
    }

    public HanziDBleitner(Context context, String db_name) {
        super(context, db_name);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS leitner;");

        super.onCreate(db);

        db.execSQL("CREATE TABLE leitner (" +
                "hanzi VARCHAR(2) PRIMARY KEY NOT NULL," +
                "box INTEGER NOT NULL," +
                "mod_time INTEGER NOT NULL);");
    }

    @Override
    public boolean checkDatabase() {
        boolean res = super.checkDatabase();

        Log.v(TAG, "checkDatabase");

        // check all characters in DB are assigned a box
        Cursor c = db.rawQuery("SELECT DISTINCT char_pinyin_id, hanzi FROM char_pinyin WHERE hanzi NOT IN (SELECT hanzi FROM leitner);", null);
        if (c.moveToFirst()) {
            Log.w(TAG, "Character does not have a leitner box");
            res = false;
            do{
                Log.w(TAG, "bad character " + c.getString(c.getColumnIndex("hanzi")));
            }while(c.moveToNext());
        }
        c.close();

        // check all characters in leitner table are in DB
        c = db.rawQuery("SELECT DISTINCT hanzi FROM leitner WHERE hanzi NOT IN (SELECT hanzi FROM char_pinyin);", null);
        if (c.moveToFirst()) {
            Log.w(TAG, "Character in leitner table is not in database");
            res = false;
            do{
                Log.w(TAG, "bad character " + c.getString(c.getColumnIndex("hanzi")));
            }while(c.moveToNext());
        }
        c.close();

        return res;
    }

    ////////////////////////////////////////
    // Manage content
    ////////////////////////////////////////
    @Override
    protected void delCharPinyin(int char_pinyin_id) {
        String hanzi;
        Cursor cur = db.rawQuery("SELECT hanzi  " +
                "FROM char_pinyin " +
                "WHERE char_pinyin_id=?;", new String[] {Integer.toString(char_pinyin_id)});
        if (cur.moveToFirst()) {
            hanzi = cur.getString(cur.getColumnIndex("hanzi"));
        }
        else {
            cur.close();
            return;
        }

        super.delCharPinyin(char_pinyin_id);
        cur = db.rawQuery("SELECT * FROM char_pinyin WHERE hanzi=?;", new String[] {hanzi});
        if (cur.getCount() == 0) {
            db.delete("leitner", "hanzi=?",new String[] {hanzi});
        }
        cur.close();
    }

    @Override
    protected int addCharPinyin(String hanzi, String pinyin) {
        int res = super.addCharPinyin(hanzi,pinyin);

        ContentValues cv = new ContentValues();
        cv.put("hanzi", hanzi);
        cv.put("box", 0);
        cv.put("mod_time", System.currentTimeMillis());
        db.insert("leitner", null, cv);

        return res;
    }

    ////////////////////////////////////////
    // Get information
    ////////////////////////////////////////

    public int getNumCharsInBox(int box) {
        int res;
        Cursor cur = db.rawQuery("SELECT hanzi FROM leitner WHERE box=?;", new String[] {Integer.toString(box)});
        res= cur.getCount();

        cur.close();
        return res;
    }

    public int getNumCharsExpiredInBox(int box) {
        int res;
        long add_time = getExpiryTime(box);
        
        Cursor cur = db.rawQuery("SELECT hanzi FROM leitner WHERE box=? AND mod_time<?;",
                new String[] {Integer.toString(box), Long.toString(System.currentTimeMillis()-add_time)});
        res= cur.getCount();

        cur.close();
        return res;
    }

    public int getBoxForChar(String hanzi) {
        int res;

        Cursor cur = db.rawQuery("SELECT box FROM leitner WHERE hanzi=?;",
                new String[] {hanzi});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id")) + " for word " + chinese);
                res = cur.getInt(cur.getColumnIndex("box"));
            }while(cur.moveToNext());
        }
        else {
            Log.d(TAG, "no box found for char "+hanzi);
            res = -1;
        }

        cur.close();
        return res;
    }

    private long getExpiryTime (int box) {
        switch (box) {
            case 0 :
                return 0;
            case 1:
                return two_hours;
            case 2:
                return two_days;
            case 3:
                return two_weeks;
            case 4:
                return two_months;
            default:
                return 0;
        }
    }

    ////////////////////////////////////////
    // Update data
    ////////////////////////////////////////
    public void resetTime(String hanzi) {
        ContentValues cv = new ContentValues();
        cv.put("mod_time", System.currentTimeMillis());
        db.update("leitner", cv, "hanzi=?", new String[] {hanzi});
    }

    public void betterBox(String hanzi) {
        int current_box = getBoxForChar(hanzi);
        current_box ++;
        if (current_box > 4) current_box = 4;
        ContentValues cv = new ContentValues();
        cv.put("box", current_box);
        cv.put("mod_time", System.currentTimeMillis());
        db.update("leitner", cv, "hanzi=?", new String[] {hanzi});

    }

    public void restartStudy(String hanzi) {
        ContentValues cv = new ContentValues();
        cv.put("box", 0);
        cv.put("mod_time", System.currentTimeMillis());
        db.update("leitner", cv, "hanzi=?", new String[] {hanzi});
    }
}
