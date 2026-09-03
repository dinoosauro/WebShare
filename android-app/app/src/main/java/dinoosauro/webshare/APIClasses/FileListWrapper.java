package dinoosauro.webshare.APIClasses;

import java.util.ArrayList;

public class FileListWrapper {
    public ArrayList<FileList> files;
    public boolean canWrite;
    public FileListWrapper(ArrayList<FileList> files, boolean canWrite) {
        this.files = files;
        this.canWrite = canWrite;
    }
}
