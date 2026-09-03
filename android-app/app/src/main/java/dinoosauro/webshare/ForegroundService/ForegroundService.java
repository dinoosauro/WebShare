package dinoosauro.webshare.ForegroundService;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import dinoosauro.webshare.Server;
import dinoosauro.webshare.WebShare;

public class ForegroundService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_FOREGROUND_SERVICE".equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
        } else {
            ForegroundNotificationHelper.createNotificationChannel(this);
            Notification notification = ForegroundNotificationHelper.createNotification(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC); else startForeground(1, notification);
        }
        return START_STICKY;
    }
    @Override
    public void onTaskRemoved(Intent rootIntent) { // Called if the user swiped the app away from recents
        Server server = WebShare.getServer();
        if (server != null) server.stopServer();
        super.onTaskRemoved(rootIntent);
    }
    @Override
    public void onDestroy() {
        Server server = WebShare.getServer();
        if (server != null) server.stopServer();
        super.onDestroy();
    }
}