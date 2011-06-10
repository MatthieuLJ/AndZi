package com.matthieu;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.util.ArrayList;

/**
 * User: matthieu
 * Date: 5/31/11
 * Time: 3:19 PM
 */
public class HanziDBlesson extends HanziDB {
    private static final String TAG = "HanziDBlesson";


    public HanziDBlesson(Context context) {
        super(context);
    }

    public HanziDBlesson(Context context, String db_name) {
        super(context, db_name);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS lesson_word;");
        db.execSQL("DROP TABLE IF EXISTS lessons;");

        super.onCreate(db);

        db.execSQL("CREATE TABLE lessons (" +
                "lesson_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "name VARCHAR(64) NOT NULL," +
                "UNIQUE(name));");
        db.execSQL("CREATE TABLE lesson_word (" +
                "eng_id INTEGER NOT NULL," +
                "lesson_id INTEGER NOT NULL," +
                "FOREIGN KEY (eng_id) REFERENCES eword(eng_id)," +
                "FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)," +
                "UNIQUE(eng_id,lesson_id));");
    }

    @Override
    public boolean checkDatabase() {
        boolean res = super.checkDatabase();

        Log.v(TAG, "checkDatabase");

        // check foreign key from lesson_word to lessons
        Cursor c = db.rawQuery("SELECT DISTINCT lesson_id FROM lesson_word WHERE lesson_id NOT IN (SELECT lesson_id FROM lessons);", null);
        if (c.moveToFirst()) {
            Log.w(TAG, "Foreign key lesson_id not respected from lesson_word");
            res = false;
            do{
                Log.w(TAG, "bad lesson_id " + c.getString(c.getColumnIndex("lesson_id")));
            }while(c.moveToNext());
        }
        c.close();

        // check foreign key from lesson_word to eword
        c = db.rawQuery("SELECT DISTINCT eng_id FROM lesson_word WHERE eng_id NOT IN (SELECT eng_id FROM eword);", null);
        if (c.moveToFirst()) {
            Log.w(TAG, "Foreign key eng_id not respected from lesson_word");
            res = false;
            do{
                Log.w(TAG, "bad eng_id " + c.getString(c.getColumnIndex("eng_id")));
            }while(c.moveToNext());
        }
        c.close();

        return res;
    }

    ////////////////////////////////////////
    // Manage Lessons
    ////////////////////////////////////////
    public void addLesson(String name) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        db.insert("lessons", null, cv);
        //db.insertWithOnConflict("lessons", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void delLesson(String name) {
        int lesson_id;

        lesson_id = getLessonId(name);
        if (lesson_id == -1)
            return;

        db.delete("lesson_word", "lesson_id=?", new String[] {Integer.toString(lesson_id)});
        db.delete("lessons", "lesson_id=?", new String[] {Integer.toString(lesson_id)});
    }

    public ArrayList<String> getLessons() {
        ArrayList<String> res = new ArrayList<String>();

        Cursor cur = db.rawQuery("SELECT name FROM lessons", null);
        if (cur.moveToFirst()) {
            do{
                res.add(cur.getString(cur.getColumnIndex("name")));
            }while(cur.moveToNext());
        }
        cur.close();
        return res;
    }

    protected int getLessonId(String name) {
        int res = -1;
        Cursor cur = db.rawQuery("SELECT * FROM lessons WHERE name=?;", new String[] {name});
        if (cur.moveToFirst()) {
            res = cur.getInt(cur.getColumnIndex("lesson_id"));
        }
        cur.close();
        return res;

    }

    ////////////////////////////////////////
    // Manage Words in lessons
    ////////////////////////////////////////
    public void assignWordLesson(String lesson, String english, String chinese, String pinyin) {
        int eng_id=-1;
        int lesson_id;

        lesson_id = getLessonId(lesson);
        if (lesson_id == -1) {
            Log.w(TAG, "Unknown lesson " + lesson);
            return;
        }

        Cursor cur = db.rawQuery("SELECT eng_id " +
                                 "FROM eword " +
                                 "  JOIN cword ON cword.cword_id=eword.cword_id " +
                                 "WHERE eword.translation=? AND cword.chinese=? AND cword.pinyin=?",
                new String[] {english,chinese,pinyin});
        if (cur.moveToFirst()) {
            eng_id = cur.getInt(cur.getColumnIndex("eng_id"));
        }
        cur.close();
        if (eng_id == -1) {
            Log.w(TAG, "Cannot find word ("+english+","+chinese+","+pinyin);
            return;
        }

        ContentValues cv = new ContentValues();
        cv.put("lesson_id", lesson_id);
        cv.put("eng_id", Integer.toString(eng_id));
        db.insert("lesson_word", null, cv);
        //db.insertWithOnConflict("lesson_word", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void removeWordLesson(String lesson, String english, String chinese, String pinyin) {
        int eng_id=-1;
        int lesson_id;

        lesson_id = getLessonId(lesson);
        if (lesson_id == -1) {
            Log.w(TAG, "Unknown lesson " + lesson);
            return;
        }

        Cursor cur = db.rawQuery("SELECT eng_id " +
                                 "FROM eword " +
                                 "  JOIN cword ON cword.cword_id=eword.cword_id " +
                                 "WHERE eword.translation=? AND cword.chinese=? AND cword.pinyin=?",
                new String[] {english,chinese,pinyin});
        if (cur.moveToFirst()) {
            eng_id = cur.getInt(cur.getColumnIndex("eng_id"));
        }
        cur.close();
        if (eng_id == -1) {
            Log.w(TAG, "Cannot find word ("+english+","+chinese+","+pinyin);
            return;
        }

        db.delete("lesson_word", "eng_id=? AND lesson_id=?", new String[] {Integer.toString(eng_id), Integer.toString(lesson_id)});
    }

    public ArrayList<Vocabulary> getVocabularyInLessons(ArrayList<String> lessons) {
        ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
        Vocabulary tmp=null;
        String whereQuery = buildWhereQuery(lessons);

        Cursor cur = db.rawQuery("SELECT e.translation AS translation, " +
                                 "c.chinese AS chinese, " +
                                 "c.pinyin AS pinyin " +
                                 "FROM (SELECT * FROM lesson_word " + whereQuery + ") AS lesson_word" +
                                 " JOIN eword AS e ON lesson_word.eng_id=e.eng_id " +
                                 " JOIN cword AS c ON c.cword_id=e.cword_id "
                                 ,null);
        
        if (cur.moveToFirst()) {
            do{
                String chinese = cur.getString(cur.getColumnIndex("chinese"));
                String pinyin = cur.getString(cur.getColumnIndex("pinyin"));
                if (tmp == null)
                    tmp = new Vocabulary(chinese, pinyin,
                            cur.getString(cur.getColumnIndex("translation")));
                else {
                    if (tmp.chinese.equals(chinese) && tmp.pinyin.equals(pinyin))
                        tmp.english.add(cur.getString(cur.getColumnIndex("translation")));
                    else {
                        res.add(tmp);
                        tmp = new Vocabulary(chinese, pinyin,
                                cur.getString(cur.getColumnIndex("translation")));
                    }
                }
            }while(cur.moveToNext());
            res.add(tmp);
        }

        cur.close();
        return res;
    }

    protected String buildWhereQuery(ArrayList<String> lessons) {
        String whereQuery = "WHERE lesson_word.lesson_id IN (";
        int size = lessons.size();
        if (size == 0)
            return "";

        for (String lesson : lessons) {
            int id = getLessonId(lesson);
            if (id == -1) {
                size --;
                continue;
            }
            if (--size == 0)
                whereQuery += "'" + Integer.toString(id) + "'";
            else
                whereQuery += "'" + Integer.toString(id) + "',";
        }
        whereQuery += ")";
        return whereQuery;
    }

    ////////////////////////////////////////
    // Manage content
    ////////////////////////////////////////
    @Override
    protected void delEword(int eng_id) {
        super.delEword(eng_id);
        db.delete("lesson_word", "eng_id=?", new String[] {Integer.toString(eng_id)});
    }

    ////////////////////////////////////////
    // Manage Filter
    ////////////////////////////////////////
    protected boolean filter_enabled=false;
    protected ArrayList<String> lesson_filter=new ArrayList<String>();
    protected String where_filter; // TODO implement and use

    public void enableFilter(boolean enable) {
        filter_enabled = enable;
    }

    public void setLessonsToFilter(ArrayList<String> lessons) {
        lesson_filter = lessons;
        where_filter = buildWhereQuery(lessons);
    }
    
    ////////////////////////////////////////
    // Use filter
    ////////////////////////////////////////

    public ArrayList<Vocabulary> getFilteredTranslations(String chinese) {
        if ((lesson_filter.size() > 0) && (filter_enabled)) {
            ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
            ArrayList<Integer> cword_ids = getFilteredWordId(chinese);
            for (int id : cword_ids) {
                res.addAll(getFilteredTranslations(id));
            }
            return res;
        }
        else
            return getTranslations(chinese);
    }

    public ArrayList<Vocabulary> getFilteredTranslations(String chinese, String pinyin) {
        if ((lesson_filter.size() > 0) && (filter_enabled)) {
            ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
            ArrayList<Integer> cword_ids = getFilteredWordId(chinese, pinyin);
            for (int id : cword_ids) {
                res.addAll(getFilteredTranslations(id));
            }
            return res;
        }
        else
            return getTranslations(chinese, pinyin);
    }

    public ArrayList<Vocabulary> getFilteredCTranslations(String english) {
        if ((lesson_filter.size() > 0) && (filter_enabled)) {
            ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
        Cursor cur = db.rawQuery("SELECT cword.chinese AS chinese,cword.pinyin AS pinyin " +
                "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                "JOIN (SELECT * FROM eword WHERE translation=?) AS eword ON eword.eng_id=lesson_word.eng_id " +
                "JOIN cword ON cword.cword_id=eword.cword_id "
                , new String[] {english});
        if (cur.moveToFirst()) {
            do{
                res.add(new Vocabulary
                        (cur.getString(cur.getColumnIndex("chinese")), cur.getString(cur.getColumnIndex("pinyin")),english));
            }while(cur.moveToNext());
        }
        else {
            Log.d(TAG, "no record found for word "+english);
        }

        cur.close();
        return res;
        }
        else
            return getCTranslations(english);
    }

    protected ArrayList<Integer> getFilteredWordId(String chinese) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT cword.cword_id AS cword_id " +
                "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                "JOIN eword ON eword.eng_id=lesson_word.eng_id " +
                "JOIN cword ON cword.cword_id=eword.cword_id " +
                "WHERE cword.chinese=?;", new String[] {chinese});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id")) + " for word " + chinese);
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
        else {
            Log.d(TAG, "no record found for word "+chinese+" in filtered db");
        }
        cur.close();
        return res;
    }

    protected ArrayList<Integer> getFilteredWordId(String chinese, String pinyin) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT * " +
                "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                "JOIN eword ON eword.eng_id=lesson_word.eng_id " +
                "JOIN cword ON cword.cword_id=eword.cword_id " +
                "WHERE cword.chinese=? AND cword.pinyin=?;", new String[] {chinese, pinyin});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id")) + " for word " + chinese);
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
        else {
            Log.d(TAG, "no record found for word "+chinese+" in filtered db");
        }
        cur.close();
        return res;
    }

    protected ArrayList<Vocabulary> getFilteredTranslations(int cword_id) {
        ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
        Vocabulary tmp=null;

        Cursor cur = db.rawQuery("SELECT * " +
                "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                "JOIN eword ON eword.eng_id=lesson_word.eng_id " +
                "JOIN cword ON eword.cword_id=cword.cword_id " +
                "WHERE cword.cword_id=? ORDER BY cword.cword_id ASC;", new String[] {Integer.toString(cword_id)});

        if (cur.moveToFirst()) {
            do{
                String chinese = cur.getString(cur.getColumnIndex("chinese"));
                String pinyin =  cur.getString(cur.getColumnIndex("pinyin"));
                if (tmp == null)
                    tmp = new Vocabulary(
                            chinese, pinyin,
                            cur.getString(cur.getColumnIndex("translation")));
                else {
                    if (tmp.chinese.equals(chinese) && tmp.pinyin.equals(pinyin))
                        tmp.english.add(cur.getString(cur.getColumnIndex("translation")));
                    else {
                        res.add(tmp);
                        tmp = new Vocabulary(chinese, pinyin,
                                cur.getString(cur.getColumnIndex("translation")));
                    }
                }
            }while(cur.moveToNext());
            res.add(tmp);
        }

        cur.close();
        return res;
    }
}
