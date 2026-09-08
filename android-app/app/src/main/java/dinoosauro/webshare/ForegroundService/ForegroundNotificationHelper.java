package dinoosauro.webshare.ForegroundService;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import dinoosauro.webshare.MainActivity;
import dinoosauro.webshare.R;

/**
 * Manage things for the Foreground Service
 */
public class ForegroundNotificationHelper {
    public static final String CHANNEL_ID = "ServerRunning";

    /**
     * Create a Notification channel for the Foreground Service
     * @param context the Android app Context
     */

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Server Running",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    /**
     * Create a custom notification for the Foreground service
     * @param context the Android app Context
     * @return a Notification
     */

    public static Notification createNotification(Context context) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                 .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getResources().getString(R.string.foreground_title))
                .setContentText(context.getResources().getString(R.string.foreground_desc))
                .addAction(R.drawable.key__24px_, context.getResources().getString(R.string.foreground_login_auth), createIntent(context, "loginAuth"))
                .addAction(R.drawable.arrow_exit__24px_, context.getResources().getString(R.string.foreground_stop_server), createIntent(context, "stopServer"))
                .setContentIntent(pendingIntent)
                .setSilent(true)
                .build();
    }
    /**
     * The int used for the request code of the new intent
     */
    private static int requestCode = 0;
    /**
     * Create an Intent that'll trigger an action on MainActivity
     * @param context the Context used to create the intent
     * @param extra a string that'll identify the action to do in the MainActivity
     */
    private static PendingIntent createIntent(Context context, String extra) {
        Intent actionIntent = new Intent(context, MainActivity.class);
        actionIntent.putExtra("foregroundNotificationCmd", extra);
        actionIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        requestCode++; // Since otherwise only the first action registered with the same request code would be triggered.
        return PendingIntent.getActivity(context, requestCode, actionIntent, PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Get if the Foreground Service is enabled or not
     * @param context the Android app Context
     * @return a boolean, true if the Foreground Service is running
     */
    public static boolean isServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (ForegroundService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
