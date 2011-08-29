package com.matthieu;

import android.app.Application;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.*;
import java.util.ArrayList;

public class AndZiApplication extends Application {
    public HanziDB myDB=null;
    public HanziDB refDB=null;

    private final static String TAG = "AndZiApplication";
    private final static String PREFERENCES_NAME="AndziPrefs";
    private final static String REF_DB_NAME = "cedict_db";

    public void onCreate() {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, 0);
        String db_name = prefs.getString("LocalDB", "");
        if ((!db_name.equals("")) && (checkFileExists(db_name))) {
            myDB = new HanziDB(getApplicationContext(), this, db_name);
            Log.d(TAG, "opened local DB "+db_name);
        }
        else
        {
            myDB = new HanziDB(getApplicationContext(), this);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString("LocalDB", myDB.getDatabasePath());
            edit.commit();
            Log.d(TAG, "Created new local DB "+myDB.getDatabasePath());
        }

        db_name = prefs.getString("ReferenceDB", "");
        if ((!db_name.equals("")) && (checkFileExists(db_name))) {
            refDB = new HanziDB(getApplicationContext(), this,db_name);
            Log.d(TAG, "opened reference DB " + db_name);
        }

        boolean filter_on = prefs.getBoolean("Lesson_Filter_On", false);
        ArrayList<String> lesson=new ArrayList<String>();
        try {
            lesson = (ArrayList<String>) ObjectSerializer.deserialize(prefs.getString("Lesson_Filter", (String) ObjectSerializer.serialize(new ArrayList<String>())));
        } catch (IOException e) {
            e.printStackTrace();
            filter_on = false;
        }
        myDB.enableFilter(filter_on);
        myDB.setLessonsToFilter(lesson);
    }

    public void save_database_filter(boolean on, ArrayList<String> list) {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, 0);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean("Lesson_Filter_On", on);
        try {
            edit.putString("Lesson_Filter", (String) ObjectSerializer.serialize(list));
        } catch (IOException e) {
            edit.putBoolean("Lesson_Filter_On", false);
            e.printStackTrace();
        }
        edit.commit();
    }

    public boolean checkFileExists(String path) {
        return new File(path).exists();
    }

    public void installRefDB(String path) {
        String dest = getApplicationContext().getDatabasePath(REF_DB_NAME).getPath();
        copyDataBase(path, dest);

        refDB = new HanziDB(getApplicationContext(), this, dest);
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, 0);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString("ReferenceDB", dest);
        edit.commit();

        Log.d(TAG, "Done installing reference db " + dest);
    }

    public void eraseRefDB() {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, 0);
        String db_name = prefs.getString("ReferenceDB", "");
        if (refDB != null) {
            refDB.close();
            refDB = null;
        }
        if (!db_name.equals("")) {
            ContextWrapper cw = new ContextWrapper(getApplicationContext());
            cw.deleteDatabase(db_name);
        }
    }

    public void copyDataBase(String source, String destination) {
        InputStream input;
        try {
            input = new FileInputStream(source);
        }
        catch (IOException e) {
            Log.d(TAG, "no ref DB in assets");
            return;
        }
        if (input == null) {
            Log.d(TAG, "could not open file at "+source);
            return;
        }

        OutputStream output=null;
        try {
            output = new FileOutputStream(destination);

            //transfer bytes
            byte[] buffer = new byte[1024];
            int length;
            while ((length = input.read(buffer))>0){
                output.write(buffer, 0, length);
            }

            //Close the streams
            output.flush();
            output.close();
            input.close();
        }
        catch (IOException e) {
            Log.d(TAG, "Failed to copy database");
            return;
        }

    }
}
