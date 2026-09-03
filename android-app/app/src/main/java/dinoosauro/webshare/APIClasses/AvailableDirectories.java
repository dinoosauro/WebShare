package dinoosauro.webshare.APIClasses;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AvailableDirectories {
    public List<String> directories;
    public List<String> availableContext;

    public AvailableDirectories(List<String> directories, Context context) {
        this.directories = directories;

        // Get the Content URIs the user has authorized the application access to
        Set<String> currentUris = new HashSet<>(context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).getStringSet("AddedFolders", new HashSet<>()));
        List<String> uriArray = new ArrayList<>(currentUris);
        boolean hasBeenEdited = false; // If the list has been edited, and so should be saved in the SharedPreferences
        for (int i = 0; i < uriArray.size(); i++) {
            try {
                DocumentFile test = DocumentFile.fromTreeUri(context, Uri.parse(uriArray.get(i)));
                if (test == null || !test.isDirectory() || !test.canWrite()) throw new Exception("");
            } catch(Exception ex) {
                uriArray.remove(i);
                i--;
                hasBeenEdited = true;
            }
        }
        if (hasBeenEdited) context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).edit().putStringSet("AddedFolders", new HashSet<>(uriArray)).apply();
        this.availableContext = uriArray;
    }
}
