package dinoosauro.webshare.APIClasses;

public class FileList {
    public String path;
    public boolean isDirectory;
    public String mimeType;
    public Long lastModified;
    public boolean canWrite;
    public Long size;
    public String fileContentUri;

    public FileList(String path, boolean isDirectory, String mimeType, Long lastModified, boolean canWrite, Long size, String fileContentUri) {
        this.path = path;
        this.isDirectory = isDirectory;
        this.mimeType = mimeType;
        this.lastModified = lastModified;
        this.canWrite = canWrite;
        this.size = size;
        this.fileContentUri = fileContentUri;
    }
}
