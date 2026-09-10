package dinoosauro.webshare;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class CheckUpdates {
    public static String versionNumber = "1.0.4";

    /**
     * Check if updates are available, and, if so, show an alert
     * @param context a valid applicaton context
     */
    public static void checkUpdates(Context context) {
        new Thread(() -> {
            try {
                // Fetch the current version code from GitHub
                HttpURLConnection urlConnection = (HttpURLConnection) new URL("https://raw.githubusercontent.com/Dinoosauro/WebShare/main/updateCode").openConnection();
                BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                StringBuilder versionCode = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    versionCode.append(line);
                }
                reader.close();
                String suggestedVersion = versionCode.toString().replace("\n", "").trim();
                if (!suggestedVersion.equals(versionNumber) && !suggestedVersion.equals(context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).getString("SkipUpdateVersion", null))) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        new MaterialAlertDialogBuilder(context)
                                .setTitle(R.string.new_version_available)
                                .setMessage(context.getResources().getString(R.string.update_version_desc, versionNumber, suggestedVersion))
                                .setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Dinoosauro/WebShare/releases"));
                                    context.startActivity(intent);
                                })
                                .setNeutralButton(R.string.not_now, (dialogInterface, i) -> {})
                                .setNegativeButton(R.string.dont_ask_again, (dialogInterface, i) -> {
                                    context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).edit().putString("SkipUpdateVersion", suggestedVersion).apply();
                                })
                                .show();
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

    }
}
