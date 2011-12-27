package com.matthieu;

import android.app.Application;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.util.Log;
import com.matthieu.widget.FileMountPoint;

import java.io.*;
import java.util.ArrayList;

public class AndZiApplication extends Application {
    private HanziDB myDB=null;
    private HanziDB refDB=null;
    private boolean ref_enabled=true;
    private boolean ref_present=false;

    private final static String TAG = "AndZiApplication";
    private final static String REF_DB_NAME = "cedict_db";
    private static final String DEFAULT_DATABASE_NAME = "HANZI_DB";

    public void onCreate() {
        initialize_my_db();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        refDB = new HanziDB(getApplicationContext(), this,REF_DB_NAME);
        if (refDB.getNumChars()==0) {
            refDB.close();
            ContextWrapper cw = new ContextWrapper(getApplicationContext());
            cw.deleteDatabase(REF_DB_NAME);
            refDB=null;
            ref_present=false;
        }
        else {
            ref_present=true;
            ref_enabled=prefs.getBoolean("ref_enabled", true);
        }
    }

    public HanziDB getMyDB() { return myDB; }
    public HanziDB getRefDB() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        ref_enabled=prefs.getBoolean("ref_enabled", true);
        Log.d(TAG, "Reference db is "+(ref_enabled?"enabled":"disabled"));
        return (ref_enabled&&ref_present)?refDB:null;
    }
    public boolean getRefDBPresent() { return ref_present; }

    private void initialize_my_db() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        myDB = new HanziDB(getApplicationContext(), this, DEFAULT_DATABASE_NAME);
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
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

    public void installRefDB(String path, boolean erase_source) {
        eraseRefDB();
        String dest = getApplicationContext().getDatabasePath(REF_DB_NAME).getPath();
        if (!copyDataBase(path, dest)) {
            new File(dest).delete();
            return;
        }
        if (erase_source) {
            File to_delete= new File(path);
            to_delete.delete();
        }

        refDB = new HanziDB(getApplicationContext(), this, REF_DB_NAME);
        ref_present=true;
        Log.d(TAG, "Done installing reference db " + dest);
    }

    public void eraseRefDB() {

        if (refDB != null) {
            refDB.close();
            refDB = null;
        }
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        cw.deleteDatabase(REF_DB_NAME);
        ref_present=false;
    }

    public boolean check_exporting_space(String path) {
        // exporting is a several steps process:
        // create another temporary database with the data to export
        // copy the temporary file to the destination
        // delete the temporary file
        String destination_mp = FileMountPoint.mount_point(path);
        String localDB_mp = FileMountPoint.mount_point(getApplicationContext().getDatabasePath(DEFAULT_DATABASE_NAME).getPath());

        if (destination_mp.equals(localDB_mp)) {
            StatFs stats = new StatFs(destination_mp);
            int available_space = stats.getAvailableBlocks()*stats.getBlockSize();

            File f = getApplicationContext().getDatabasePath(DEFAULT_DATABASE_NAME);
            long dbSize = f.length();

            if (available_space < 2*dbSize)
                return false;
        }
        else {
            File f = getApplicationContext().getDatabasePath(DEFAULT_DATABASE_NAME);
            long dbSize = f.length();

            StatFs stats = new StatFs(destination_mp);
            int available_space = stats.getAvailableBlocks()*stats.getBlockSize();

            if (available_space < dbSize)
                return false;

            stats = new StatFs(localDB_mp);
            available_space = stats.getAvailableBlocks()*stats.getBlockSize();

            if (available_space < dbSize)
                return false;
        }

        return true;
    }

    public boolean check_merging_space(String path) {
        // merging is a several steps process:
        // copy the database into DB path
        // create a backup
        // merge it
        // delete the copy
        // delete the backup

        StatFs stats = new StatFs(FileMountPoint.mount_point(getApplicationContext().getDatabasePath(DEFAULT_DATABASE_NAME).getPath()));
        int available_space = stats.getAvailableBlocks()*stats.getBlockSize();

        File f = new File(path);
        long dbSize = f.length();

        return available_space >= 3 * dbSize;
    }

    public boolean check_ref_download_space(String path, int size) {
        // Downloading and installing the reference database means:
        // download the file to the path
        // delete the existing ref db
        // copy the new one in place
        // delete the temporary file (?)

        String destination_mp = FileMountPoint.mount_point(path);
        String localDB_mp = FileMountPoint.mount_point(getApplicationContext().getDatabasePath(REF_DB_NAME).getPath());
        long installed_db_size = getApplicationContext().getDatabasePath(REF_DB_NAME).length();

        // first there is enough space to download the file
        StatFs stats = new StatFs(destination_mp);
        int available_space = stats.getAvailableBlocks()*stats.getBlockSize();

        if (available_space < size)
            return false;

        if (destination_mp.equals(localDB_mp)) {
            if (available_space + installed_db_size < 2*size)
                return false;
        }
        else {
            stats = new StatFs(localDB_mp);
            available_space = stats.getAvailableBlocks()*stats.getBlockSize();
            if (available_space + installed_db_size < size)
                return false;

        }

        return true;
    }

    public boolean copyMyDataBase(String destination) {
        return copyDataBase(getApplicationContext().getDatabasePath(DEFAULT_DATABASE_NAME).getPath(), destination);
    }

    public boolean copyDataBase(String source, String destination) {
        InputStream input;
        try {
            input = new FileInputStream(source);
        }
        catch (IOException e) {
            Log.d(TAG, "Could not open source");
            return false;
        }
        if (input == null) {
            Log.d(TAG, "Could not open file at "+source);
            return false;
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
            return false;
        }

        return true;
    }

    public void replaceDataBase(String source) {
        myDB.close();
        myDB=null;
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        cw.deleteDatabase(DEFAULT_DATABASE_NAME);
        if (!copyDataBase(source, getApplicationContext().getDatabasePath(DEFAULT_DATABASE_NAME).getPath())) {

        }
        initialize_my_db();
    }
}
