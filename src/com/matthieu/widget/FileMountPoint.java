/******************************
 * Quick class to find the mounting point of a particular folder
 * Useful when looking for available space before doing a copy or a move
 */
package com.matthieu.widget;

import android.util.Log;

import java.io.*;
import java.util.ArrayList;

public class FileMountPoint {
    private static final boolean DEBUG = false;
    private final static String TAG = "FileMountPoint";

    static private ArrayList<String> all_mount_points() {
        ArrayList<String> res = new ArrayList<String>();
        try {
            BufferedReader mounts = new BufferedReader(new FileReader("/proc/mounts"));
            String point;
            String[] elements;
            while ((point = mounts.readLine()) != null) {
                elements = point.split(" ");
                res.add(elements[1]);
            }
        }
        catch (FileNotFoundException e) {
            if (DEBUG) Log.d(TAG, "Cannot find /proc/mounts...");
        }
        catch (IOException e) {
            if (DEBUG) Log.d(TAG, "Ran into problems reading /proc/mounts...");
        }

        return res;
    }

    static public String mount_point(String path) {
        try {
            path = (new File(path)).getCanonicalPath();
        }
        catch (IOException e) {
            if (DEBUG) Log.d(TAG, "Ran into getting canonical path");
        }
        ArrayList<String> list =  all_mount_points();
        String res="/";
        for (String s:list) {
            if ((path.equals(s) || path.startsWith(s+"/")) && s.length()>res.length())
                res = s;
        }

        return res;
    }
}
