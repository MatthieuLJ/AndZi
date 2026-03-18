package com.matthieu.zi_app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;

import java.util.Timer;
import java.util.TimerTask;

public class ProgressService extends Service {
    private final static int NOTIFICATION_ID=1;
    private final static String TAG="ProgressService";
    @Override
    public void onCreate() {
        super.onCreate();
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (!((AndZiApplication) getApplication()).isApplicationForeground()) {
                    update_notification();
                    update_progress_data();
                    ((AndZiApplication) getApplication()).showSplash(true);
                }else {
                    clear_notification(ProgressService.this);
                    if (!((AndZiApplication) getApplication()).is_screen_on())
                        update_progress_data();
                }
            }
        };

        timer.scheduleAtFixedRate(task, 30*1000, 2*60*60*1000); // every 2 hours
        //timer.scheduleAtFixedRate(task, 0, 120*1000); // every 2 minutes for test...
    }

    public void update_progress_data() {
        ProgressDB db = ((AndZiApplication) getApplication()).getProgress();

        ProgressDB.ProgressData current_data = ((AndZiApplication)getApplication()).getCurrentProgress();
        if (current_data == null)
            return;

        if (current_data.num_in_box[0] + current_data.num_in_box[1] + current_data.num_in_box[2] + current_data.num_in_box[3] + current_data.num_in_box[4] == 0)
            return;

        db.add_data(current_data);
    }

    public void update_notification() {
        HanziDB myDB = ((AndZiApplication) getApplication()).getMyDB();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        int expired = myDB.getNumExpired();

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        boolean notification_enabled = sp.getBoolean("notification_enabled", true);
        if ((notification_enabled) && (expired > 25)) {
            Notification notification;
            Intent start_test = new Intent(this, MainMenuActivity.class);
            start_test.putExtra("start_test", true);
            PendingIntent pi = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), start_test, PendingIntent.FLAG_CANCEL_CURRENT);

            notification = new NotificationCompat.Builder(this).setContentTitle(getResources().getString(R.string.notification_title))
                    .setContentText(getResources().getString(R.string.notification_content, expired))
                    .setSmallIcon(R.drawable.zi_icon_notification).setContentIntent(pi)
                    .setAutoCancel(true).setOnlyAlertOnce(true).build();

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
