package dinoosauro.webshare.APIClasses;

public class AudioInfo {
    public Long id;
    public String name;
    public Long dateModified;
    public Long dateAdded;
    public String album;
    public String albumArtist;
    public String artist;
    public Long bitrate;
    public Long cdTrack;
    public String composer;
    public Long discNumber;
    public String genre;
    public Long numTracks;
    public String title;
    public Long track;
    public Long year;
    public Long albumId;
    public Long duration;

    public AudioInfo(Long id, String name, Long dateModified, Long dateAdded, String album, String albumArtist, String artist, Long bitrate, Long cdTrack, String composer, Long discNumber, String genre, Long numTracks, String title, Long track, Long year, Long albumId, Long duration) {
        this.id = id;
        this.name = name;
        this.dateModified = dateModified;
        this.dateAdded = dateAdded;
        this.album = album;
        this.albumArtist = albumArtist;
        this.artist = artist;
        this.bitrate = bitrate;
        this.cdTrack = cdTrack;
        this.composer = composer;
        this.discNumber = discNumber;
        this.genre = genre;
        this.numTracks = numTracks;
        this.title = title;
        this.track = track;
        this.year = year;
        this.albumId = albumId;
        this.duration = duration;
    }
}
