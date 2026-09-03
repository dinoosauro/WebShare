package dinoosauro.webshare;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class WebShare extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }

    private static Server server;

    public static Server getServer() {
        return server;
    }
    public static void setServer(Server serverToSet) {
        server = serverToSet;
    }
}
