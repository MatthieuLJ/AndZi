package com.matthieu;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;

import java.util.Timer;
import java.util.TimerTask;

public class ProgressService extends Service {
    private final static int NOTIFICATION_ID=1;
    @Override
    public void onCreate() {
        super.onCreate();
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (!((AndZiApplication) getApplication()).isApplicationForeground()) {
                    update_notification(true);
                    ((AndZiApplication) getApplication()).showSplash(true);
                }else {
                    clear_notification(ProgressService.this);
                }
            }
        };

        timer.scheduleAtFixedRate(task, 30*1000, 2*60*60*1000); // every 2 hours
        //timer.scheduleAtFixedRate(task, 0, 120*1000); // every 2 minutes for test...
    }

    public void update_notification(boolean store) {
        ProgressDB db = ((AndZiApplication) getApplication()).getProgress();
        ProgressDB.ProgressData current_data = ((AndZiApplication)getApplication()).getCurrentProgress();
        if (current_data == null)
            return;
        if (current_data.num_chars_in_box[0] + current_data.num_chars_in_box[1] + current_data.num_chars_in_box[2] + current_data.num_chars_in_box[3] + current_data.num_chars_in_box[4] == 0)
            return;

        if (store)
            db.add_data(current_data);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        int expired = current_data.num_chars_expired_in_box[0] + current_data.num_chars_expired_in_box[1] + current_data.num_chars_expired_in_box[2] +
                current_data.num_chars_expired_in_box[3] + current_data.num_chars_expired_in_box[4];

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        boolean notification_enabled = sp.getBoolean("settings_notification", true);
        if ((notification_enabled) && (expired > 25)) {
            Notification notification = new Notification(R.drawable.icon, getResources().getString(R.string.notification_ticker), System.currentTimeMillis());
            notification.flags = Notification.FLAG_AUTO_CANCEL | Notification.FLAG_ONLY_ALERT_ONCE;
            Intent start_test = new Intent(this, MainMenuActivity.class);
            start_test.putExtra("start_test", true);
            start_test.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), start_test, PendingIntent.FLAG_CANCEL_CURRENT);
            notification.setLatestEventInfo(getApplicationContext(), getResources().getString(R.string.notification_title), getResources().getString(R.string.notification_content, expired), pi);
            nm.notify(NOTIFICATION_ID, notification);
        }
        else {
            nm.cancel(NOTIFICATION_ID);
        }
    }

    public static void clear_notification(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ID);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
