package dinoosauro.webshare;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dinoosauro.webshare.APIClasses.AvailableDirectories;
import dinoosauro.webshare.APIClasses.UsedColors;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        // Check permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, R.string.grant_authorization, Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this, R.string.grant_authorization, Toast.LENGTH_LONG).show();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        // Restore default values of the Settings, and set up the events

        SharedPreferences preferences = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        EditText portInput = findViewById(R.id.portValue);
        portInput.setText(String.valueOf(preferences.getInt("ServerPort", 8000)));
        portInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                preferences.edit().putInt("ServerPort", Integer.parseInt(charSequence.toString().isBlank() ? "0" : charSequence.toString())).apply();
            }
        });
        MaterialSwitch calcFolderSize = findViewById(R.id.calcFolderSize);
        calcFolderSize.setChecked(preferences.getBoolean("CalcFolderSize", false));
        calcFolderSize.setOnCheckedChangeListener((compoundButton, b) -> {
            preferences.edit().putBoolean("CalcFolderSize", b).apply();
        });
        MaterialSwitch thirdPartyClients = findViewById(R.id.thirdPartyClients);
        thirdPartyClients.setChecked(preferences.getBoolean("ThirdPartyClients", false));
        thirdPartyClients.setOnCheckedChangeListener((compoundButton, b) -> {
            preferences.edit().putBoolean("ThirdPartyClients", b).apply();
        });

        // Button to start the server
        Button start = findViewById(R.id.button);
        start.setOnClickListener(view -> {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) || (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
                Snackbar.make(view, getResources().getString(R.string.missing_authorization), Snackbar.LENGTH_LONG).show();
                return;
            }
            int port = preferences.getInt("ServerPort", 8000);
            int startFrom = 8000;
            while (!checkIfPortIsAvailable(port)) {
                port = startFrom;
                startFrom++;
            }
            Server server = new Server(port, getApplicationContext(), new UsedColors(start), thirdPartyClients.isChecked());
            WebShare.setServer(server);
            try {
                server.startServer();
                switchToServerRunning(port); // Change the UI
            } catch (Exception e) {
                Snackbar.make(view, getResources().getString(R.string.server_start_fail, e.getMessage()), Snackbar.LENGTH_LONG).show();
            }
        });
        findViewById(R.id.stop).setOnClickListener(view -> {
            WebShare.getServer().stopServer();
            findViewById(R.id.ongoingContainer).setVisibility(View.GONE);
            findViewById(R.id.startContainer).setVisibility(View.VISIBLE);
        });
        findViewById(R.id.authentication).setOnClickListener(view -> { // Get an authorization code to log in
            try {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getResources().getString(R.string.authorization_code))
                        .setMessage(getResources().getString(R.string.authorization_code_dialog_desc, "\n\n" + WebShare.getServer().generateAuthenticationCode()))
                        .setPositiveButton(getResources().getString(R.string.done), (dialogInterface, i) -> {})
                        .setOnDismissListener(dialogInterface -> {
                            WebShare.getServer().removeAuthenticationCode();
                        }).show();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        });

        // Permit to pick a directory so that it can become readable (ex: it's an external device, or it's a SAF device)
        ActivityResultLauncher<Intent> directoryPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                Intent data = result.getData();
                Uri uri = data.getData();
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                List<String> currentUris = new AvailableDirectories(null, getApplicationContext()).availableContext;
                if (!currentUris.contains(uri.toString())) {
                    currentUris.add(uri.toString());
                    createChip(uri.toString());
                }
                preferences.edit().putStringSet("AddedFolders", new HashSet<>(currentUris)).apply();
            }
        });
        findViewById(R.id.externalstorage).setOnClickListener(view -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.addCategory(Intent.CATEGORY_DEFAULT);
            directoryPicker.launch(i);

        });
        Server checkServer = WebShare.getServer();
        if (checkServer != null) {
            switchToServerRunning(checkServer.port);
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        for (String addedExternalDrive: new AvailableDirectories(null, getApplicationContext()).availableContext) createChip(addedExternalDrive);
        CheckUpdates.checkUpdates(this);
        ((TextView) findViewById(R.id.versionNumber)).setText(getResources().getString(R.string.webshare_version, CheckUpdates.versionNumber));
    }

    /**
     * Show the "Server running" interface
     * @param port the port where the server is running
     */
    private void switchToServerRunning(int port) {
        findViewById(R.id.startContainer).setVisibility(View.GONE);
        findViewById(R.id.ongoingContainer).setVisibility(View.VISIBLE);
        TextView serverLink = findViewById(R.id.severLink);
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        serverLink.setText("http://" + Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress()) + ":" + port + "/");
    }

    /**
     * Create the chip used to display the directories the user has manually enabled access to
     * @param path
     */
    private void createChip(String path) {
        Chip chip = new Chip(this);
        String realPath = path;
        if (path.contains("tree/")) path = path.substring(path.indexOf("tree/") + 5);
        try {
            chip.setText(URLDecoder.decode(path, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            chip.setText(path);
        }
        chip.setOnClickListener(view -> {
            List<String> currentUris = new AvailableDirectories(null, getApplicationContext()).availableContext;
            currentUris.remove(realPath);
            getSharedPreferences(getPackageName(), Context.MODE_PRIVATE).edit().putStringSet("AddedFolders", new HashSet<>(currentUris)).apply();
            ((ChipGroup) findViewById(R.id.externalDriveChipContainer)).removeView(chip);
        });
        ((ChipGroup) findViewById(R.id.externalDriveChipContainer)).addView(chip);
        ((ChipGroup) findViewById(R.id.externalDriveChipContainer)).setPadding(0, 10, 0, 0);
    }

    /**
     * Check if the passed port isn't being used by another process
     * @param port the number of the port
     * @return true if the port can be used, false otherwise.
     */
    private boolean checkIfPortIsAvailable(int port) {
        if (port < 1 || port > 65535) return false;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

}