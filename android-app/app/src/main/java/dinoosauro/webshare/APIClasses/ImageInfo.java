package dinoosauro.webshare.APIClasses;

public class ImageInfo {
    public Long id;
    public String name;
    public Long dateModified;
    public Long size;
    public String mimeType;
    public Long dateTaken;
    public Long dateAdded;
    public Long width;
    public Long height;
    public String relativePath;
    public Long duration;

    public ImageInfo(Long id, String name, Long dateModified, Long size, Long dateTaken, String mimeType, Long dateAdded, Long width, Long height, String relativePath, Long duration) {
        this.id = id;
        this.name = name;
        this.dateTaken = dateTaken;
        this.size = size;
        if (dateModified != null) this.dateModified = dateModified * 1000;
        this.mimeType = mimeType;
        if (dateAdded != null) this.dateAdded = dateAdded * 1000;
        this.width = width;
        this.height = height;
        this.relativePath = relativePath;
        this.duration = duration;
    }

}
