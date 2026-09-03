package dinoosauro.webshare;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.webkit.MimeTypeMap;

import androidx.documentfile.provider.DocumentFile;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.google.gson.Gson;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dinoosauro.webshare.APIClasses.AudioInfo;
import dinoosauro.webshare.APIClasses.AvailableDirectories;
import dinoosauro.webshare.APIClasses.ExifData;
import dinoosauro.webshare.APIClasses.FileList;
import dinoosauro.webshare.APIClasses.FileListWrapper;
import dinoosauro.webshare.APIClasses.ImageInfo;
import dinoosauro.webshare.APIClasses.UsedColors;
import dinoosauro.webshare.ForegroundService.ForegroundNotificationHelper;
import dinoosauro.webshare.ForegroundService.ForegroundService;
import fi.iki.elonen.NanoHTTPD;

public class Server extends NanoHTTPD {
    /**
     * The application context used to start the server
     */
    Context context;
    /**
     * The SHA256 of the authentication code that permits to log in. This value might be nullish, in this case no new logins should be permitted.
     */
    String authenticationCode;
    /**
     * The Material Design 3 colors applied to the application
     */
    UsedColors usedColors;
    /**
     * A list of all the valid tokens
     */
    ArrayList<String> tokens = new ArrayList<>();
    /**
     * The WakeLock used so that the device doesn't sleep
     */
    private PowerManager.WakeLock wakeLock;
    /**
     * The WifiLock used so that Wi-Fi performances are always at its best
     */
    private WifiManager.WifiLock wifiLock;
    /**
     * Port number of this server
     */
    int port;
    /**
     * If CORS should be enabled for this server
     */
    private boolean enableCors;
    public Server(int port, Context context, UsedColors usedColors, boolean enableCors) {
        super(port);
        this.port = port;
        this.context = context;
        this.usedColors = usedColors;
        this.enableCors = enableCors;
    }

    /**
     * Create a NanoHTTP Response with fixed length
     */
    public Response corsFixedLengthResponse(Response.IStatus status, String mimetype, String content) {
        Response resp = newFixedLengthResponse(status, mimetype, content);
        if (enableCors) resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.addHeader("Access-Control-Max-Age", "5");
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        resp.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Content-Length");
        return resp;
    }
    /**
     * Create a NanoHTTP Response with fixed length
     */
    public Response corsFixedLengthResponse(Response.IStatus status, String mimetype, InputStream content, long length) {
        Response resp = newFixedLengthResponse(status, mimetype, content, length);
        if (enableCors) resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.addHeader("Access-Control-Max-Age", "3628800");
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        resp.addHeader("Access-Control-Allow-Headers", "Authorization");
        return resp;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (session.getMethod() == Method.OPTIONS) return corsFixedLengthResponse(Response.Status.OK, "text/plain", "");
        String webPath = session.getUri().toString();
        if (webPath.startsWith("/api/")) {
            if (!webPath.equals("/api/authenticate") && !webPath.equals("/api/colors")) { // These two endpoints don't require a valid token
                String authorization = session.getHeaders().get("authorization");
                if (authorization == null) {
                    String tempAuth = getParameter(session.getParameters(), "token");
                    if (tempAuth != null) authorization = "Bearer " + tempAuth;
                }
                if (authorization != null && authorization.startsWith("Bearer ")) {
                    String token = authorization.substring(7);
                    if (!tokens.contains(token)) return corsFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "This endpoint requires a valid token.");
                } else return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "A token must be sent in the Authorization header");
            }
            String lastPath = webPath.substring(webPath.lastIndexOf("/"));
            switch (lastPath) {
                case "/colors": { // Endpoint to get the Material You color palette
                    return corsFixedLengthResponse(Response.Status.OK, "application/json", new Gson().toJson(usedColors));
                }
                case "/check": { // Simple endpoint to check if a token is valid or not
                    return corsFixedLengthResponse(Response.Status.OK, "text/plain", "");
                }
                case "/authenticate": { // Get a valid token. A request with the `Authorization: basic *sha256*` header must be done
                    String authentication = session.getHeaders().get("authorization");
                    if (authentication == null) return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing Authorization header");
                    if (authentication.startsWith("Basic ") && authentication.substring(6).equals(authenticationCode)) {
                        String token = generateRandomString(64, false);
                        tokens.add(token);
                        return corsFixedLengthResponse(Response.Status.OK, "text/plain", token);
                    } else return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid authentication key");
                }
                case "/getImages": case "/getVideos": { // Get either all the images or all the videos on the user' sdevice
                    try (Cursor cursor = context.getContentResolver().query(
                            getMediaStoreUri(lastPath.equals("/getVideos") ? AvailableMediaStoreUriTypes.VIDEO : AvailableMediaStoreUriTypes.IMAGE),
                            new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? MediaStore.Images.Media.RELATIVE_PATH : MediaStore.Images.Media.DATA, MediaStore.Video.Media.DURATION},
                            null,
                            null,
                            MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
                    )) {
                        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                        int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                        int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
                        int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
                        int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
                        int takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);
                        int mimetypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE);
                        int widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH);
                        int heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT);
                        int relativePathColumn = cursor.getColumnIndex(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? MediaStore.Images.Media.RELATIVE_PATH : MediaStore.Images.Media.DATA);
                        int durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
                        ArrayList<ImageInfo> imageInfoList = new ArrayList<>();
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(idColumn);
                            imageInfoList.add(new ImageInfo(id, cursor.getString(nameColumn), cursor.getLong(dateColumn), cursor.getLong(sizeColumn), cursor.getLong(takenColumn), cursor.getString(mimetypeColumn), cursor.getLong(dateAddedColumn), cursor.getLong(widthColumn),cursor.getLong(heightColumn), relativePathColumn != -1 ? cursor.getString(relativePathColumn) : null, cursor.getLong(durationColumn)));
                        }
                        return corsFixedLengthResponse(Response.Status.OK, "application/json", new Gson().toJson(imageInfoList));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    return corsFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed fetching images");
                }

                case "/getAudio": { // Get all the audio files, with the available metadata provided by Android
                    Uri collection = getMediaStoreUri(AvailableMediaStoreUriTypes.AUDIO);
                    ArrayList<String> queryStr = new ArrayList<>(Arrays.asList(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATE_MODIFIED, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ARTIST, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.COMPOSER, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION));
                    // MediaStore's metadata varies version by version, so we can query some columns only if we're above a certain version.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) queryStr.add(MediaStore.Audio.Media.GENRE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        queryStr.add(MediaStore.Audio.Media.BITRATE);
                        queryStr.add(MediaStore.Audio.Media.CD_TRACK_NUMBER);
                        queryStr.add(MediaStore.Audio.Media.DISC_NUMBER);
                        queryStr.add(MediaStore.Audio.Media.NUM_TRACKS);
                    }
                    try (Cursor cursor = context.getContentResolver().query(
                            collection,
                            queryStr.toArray(new String[0]),
                            null,
                            null,
                            MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
                    )) {
                        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                        int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                        int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED);
                        int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
                        int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                        int albumArtistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST);
                        int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                        int bitrateColumn = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE);
                        int cdTrackColumn = cursor.getColumnIndex(MediaStore.Audio.Media.CD_TRACK_NUMBER);
                        int composerColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.COMPOSER);
                        int discNumberColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER);
                        Integer genreColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE) : null;
                        int numTracksColumn = cursor.getColumnIndex(MediaStore.Audio.Media.NUM_TRACKS);
                        int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                        int trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK);
                        int yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR);
                        int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                        int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                        ArrayList<AudioInfo> audioInfoList = new ArrayList<>();
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(idColumn);
                            audioInfoList.add(new AudioInfo(id, cursor.getString(nameColumn), cursor.getLong(dateColumn), cursor.getLong(dateAddedColumn), cursor.getString(albumColumn), cursor.getString(albumArtistColumn), cursor.getString(artistColumn), bitrateColumn != -1 ? cursor.getLong(bitrateColumn) : null, cdTrackColumn != -1 ? cursor.getLong(cdTrackColumn) : null, cursor.getString(composerColumn), discNumberColumn == -1 ? null : cursor.getLong(discNumberColumn), genreColumn == null ? null : cursor.getString(genreColumn), numTracksColumn == -1 ? null : cursor.getLong(numTracksColumn), cursor.getString(titleColumn), cursor.getLong(trackColumn), cursor.getLong(yearColumn), cursor.getLong(albumIdColumn), cursor.getLong(durationColumn)));
                        }
                        return corsFixedLengthResponse(Response.Status.OK, "application/json", new Gson().toJson(audioInfoList));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    return corsFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed fetching audios");
                }
                case "/getPreview": { // Generate a preview of an image/video/audio/PDF file
                    Map<String, List<String>> params = session.getParameters();
                    String id = getParameter(params, "id");
                    String type = getParameter(params, "type");
                    String albumId = getParameter(params, "albumId");

                    String path = getParameter(params, "path");
                    if (path != null && type != null) { // From "File" mode
                        DocumentFile documentFile = Objects.equals(getParameter(params, "isContentUri"), "1") ? DocumentFile.fromSingleUri(context, Uri.parse(path)) : DocumentFile.fromFile(new File(path));
                        if (documentFile.exists()) {
                            if (type.equals("application/pdf")) {
                                try { // Render the first image of the PDF, and convert it to a WebP
                                    ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(documentFile.getUri(), "r");
                                    if (descriptor == null) throw new Exception("Failed opening file description");
                                    PdfRenderer renderer = new PdfRenderer(descriptor);
                                    PdfRenderer.Page page = renderer.openPage(0);
                                    Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                                    return newChunkedResponse(Response.Status.OK, "image/webp", convertImage(bitmap, 60, "webp"));
                                } catch (Exception e) {
                                    return corsFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No file could be found from the provided path.");
                                }
                            } else if (type.startsWith("audio")) { // Try fetching the album art of the current element
                                try {
                                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                                    retriever.setDataSource(context, documentFile.getUri());
                                    byte[] bytes = retriever.getEmbeddedPicture();
                                    if (bytes != null) return newChunkedResponse(Response.Status.OK, "image/webp", convertImage(BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null), 60, "webp"));
                                    throw new Exception("Bytes cannot be null");
                                } catch (Exception e) {
                                    return corsFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No file could be found from the provided path.");
                                }
                            } else if (type.startsWith("video")) { // Fetch the first frame of the video
                                try {
                                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                                    retriever.setDataSource(context, documentFile.getUri());
                                    Bitmap result;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                        result = retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST, 512, 512);
                                    } else result = retriever.getFrameAtTime(-1);
                                    if (result == null) throw new IOException("Failed fetching data");
                                    retriever.release();
                                    return newChunkedResponse(Response.Status.OK, "image/webp", convertImage(result, 60, "webp"));
                                } catch (IOException e) {
                                    return corsFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No file could be found from the provided path.");
                                }
                            } else {
                                try { // Since we don't have an ID to fetch the thumbnail automatically generated by Android, we'll re-encode the image by resizing it to a smaller version
                                    BitmapFactory.Options options = new BitmapFactory.Options();
                                    options.inJustDecodeBounds = true;
                                    try (InputStream is = context.getContentResolver().openInputStream(documentFile.getUri())) {
                                        BitmapFactory.decodeStream(is, null, options);
                                    }
                                    int targetSize = 512;
                                    int sampleSize = 1;
                                    int w = options.outWidth, h = options.outHeight;
                                    while (w / sampleSize / 2 >= targetSize || h / sampleSize / 2 >= targetSize) {
                                        sampleSize *= 2;
                                    }
                                    options.inJustDecodeBounds = false;
                                    options.inSampleSize = sampleSize;
                                    Bitmap bitmap;
                                    try (InputStream is = context.getContentResolver().openInputStream(documentFile.getUri())) {
                                        bitmap = BitmapFactory.decodeStream(is, null, options);
                                    }
                                    return newChunkedResponse(Response.Status.OK, "image/webp", convertImage(bitmap, 60, "webp"));
                                } catch (IOException e) {
                                    return corsFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No file could be found from the provided path.");
                                }
                            }
                        }
                    }
                    if (id != null && type != null) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && type.equals("audio")) {
                            return downloadFileFromMediaStore(session, "albumart/mp3", null, albumId, null);
                        }
                        Bitmap bitmap = getThumbnail(context, Long.parseLong(id), type.equals("video") ? AvailableMediaStoreUriTypes.VIDEO : type.equals("audio") ? AvailableMediaStoreUriTypes.AUDIO : AvailableMediaStoreUriTypes.IMAGE);
                        if (bitmap != null) {
                            try {
                                return newChunkedResponse(Response.Status.OK, "image/webp", convertImage(bitmap, 60, "webp"));
                            } catch (IOException e) {
                                return corsFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed image encoding");
                            }
                        } else
                            return corsFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "The ID couldn't be found");
                    } else
                        return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing required parameter: ID");
                }
                case "/download": { // Download a file
                    Map<String, List<String>> params = session.getParameters();
                    String id = getParameter(params, "id");
                    String mimeType = getParameter(params, "mimetype");
                    String convertTo = getParameter(params, "convertTo"); // Parameter that permits to convert an image to another format (ex: heic -> webp)
                    String path = getParameter(params, "path");
                    if (id != null && mimeType != null) {
                        return downloadFileFromMediaStore(session, mimeType, convertTo, id, getParameter(params, "name"));
                    } else if (path != null) {
                        try {
                            DocumentFile file = Objects.equals(getParameter(params, "isContentUri"), "1") ? DocumentFile.fromSingleUri(context, Uri.parse(path)) : DocumentFile.fromFile(new File(path));
                            InputStream stream = context.getContentResolver().openInputStream(file.getUri());
                            Response response = corsFixedLengthResponse(Response.Status.OK, "application/octet-stream", stream, file.length());
                            response.addHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20"));
                            return response;
                        } catch (FileNotFoundException e) {
                            return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "No file has been found at the provided path");
                        }
                    }
                    return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing parameters");
                }
                case "/metadata": { // Get the metadata of a video or image
                    Map<String, List<String>> params = session.getParameters();
                    String id = getParameter(params, "id");
                    boolean isVideo = Objects.equals(getParameter(params, "isVideo"), "1");
                    if (id != null) {
                        Uri imageUri = ContentUris.withAppendedId(getMediaStoreUri(isVideo ? AvailableMediaStoreUriTypes.VIDEO : AvailableMediaStoreUriTypes.IMAGE), Long.parseLong(id));
                        ContentResolver resolver = context.getContentResolver();
                        try {
                            AssetFileDescriptor afd = resolver.openAssetFileDescriptor(imageUri, "r");
                            if (afd != null) {
                                Metadata metadata = ImageMetadataReader.readMetadata(afd.createInputStream());
                                ArrayList<ExifData> tags = new ArrayList<>();
                                for (Directory directory: metadata.getDirectories()) {
                                    for (Tag tag: directory.getTags()) {
                                        tags.add(new ExifData(tag.getTagName(), tag.getDescription()));
                                    }
                                }
                                afd.close();
                                return corsFixedLengthResponse(Response.Status.OK, "application/json", new Gson().toJson(tags));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            return corsFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed metadata extraction");
                        }
                    } else return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing ID parameter");
                    break;
                }
                case "/getAvailableDirs": { // Get the main folder paths, so /storage/emulated/0/, USB devices connected to the phone, and also Content URIs the user has manually permitted access to.
                    StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                    ArrayList<String> paths = new ArrayList<>();
                    for (StorageVolume volume : storageManager.getStorageVolumes()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            File dir = volume.getDirectory();
                            if (dir != null) paths.add(dir.getPath());
                        } else {
                            try { // Undocumented method, that seems to work
                                java.lang.reflect.Method getPathMethod = StorageVolume.class.getMethod("getPath");
                                paths.add((String) getPathMethod.invoke(volume));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    return corsFixedLengthResponse(Response.Status.OK, "application/json", new Gson().toJson(new AvailableDirectories(paths, context)));
                }
                case "/dir": { // List all the files and directory
                    Map<String, List<String>> params = session.getParameters();
                    String path = getParameter(params, "path");
                    boolean fromContentUri = Objects.equals(getParameter(params, "isContentUri"), "1");
                    if (path != null) {
                        DocumentFile dir = fromContentUri ? DocumentFile.fromTreeUri(context, Uri.parse(path)) : DocumentFile.fromFile(new File(path));
                        if (dir != null && dir.isDirectory()) {
                            ArrayList<FileList> fileListList = new ArrayList<>();
                            for (DocumentFile file : dir.listFiles()) {
                                String name = file.getName();
                                fileListList.add(new FileList(name, file.isDirectory(), file.isFile() ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(name.lastIndexOf(".") + 1)) : null, file.lastModified(), file.canWrite(), file.isDirectory() ? (context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).getBoolean("CalcFolderSize", false) ? getFolderSize(file) : null) : Long.valueOf(file.length()), fromContentUri ? file.getUri().toString() : null));
                            }
                            return corsFixedLengthResponse(Response.Status.OK, "application/json", new Gson().toJson(new FileListWrapper(fileListList, dir.canWrite())));
                        } else
                            return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "The provided path is not a directory.");
                    } else
                        return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "A path hasn't been provided");
                }
                case "/newfolder": { // Create a new folder
                    Map<String, List<String>> params = session.getParameters();
                    String path = getParameter(params, "path");
                    if (path != null) {
                        String contentUri = getParameter(params, "sourceContentUri");
                        if (contentUri != null) {
                            DocumentFile documentFile = DocumentFile.fromTreeUri(context, Uri.parse(contentUri));
                            if (documentFile != null && documentFile.createDirectory(path) != null) return corsFixedLengthResponse(Response.Status.OK, "text/plain", "");
                            return corsFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "");
                        }
                        File file = new File(path);
                        if (file.isDirectory()) return corsFixedLengthResponse(Response.Status.OK, "text/plain", "");
                        return corsFixedLengthResponse(file.mkdirs() ? Response.Status.OK : Response.Status.INTERNAL_ERROR, "text/plain", "");
                    } else return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "A path hasn't been provided");
                }
                case "/zip": { // Create a zip file. This endpoint is called by a POST form
                    if (session.getMethod() != Method.POST) return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "This endpoint accepts only POST requests");
                    try {
                        Map<String, String> params = new HashMap<>();
                        session.parseBody(params);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "The body must be a valid form.");
                    }
                    Map<String, List<String>> params = session.getParameters();
                    boolean isFromMedia = Objects.equals(getParameter(params, "type"), "media");
                    /**
                     * A list of all the AssetFileDescriptor that should be used to get the file content
                     */
                    ArrayList<AssetFileDescriptor> fileDescriptors = new ArrayList<>();
                    /**
                     * A list of the paths of the files added in the `fileDescriptors` variable
                     */
                    ArrayList<String> fileDescriptorNames = new ArrayList<>();
                    /**
                     * A list of all the DocumentFiles fetched
                     */
                    ArrayList<DocumentFile> files = new ArrayList<>();
                    if (isFromMedia) { // Get from MediaStore API
                        String idsString = getParameter(params, "ids");
                        String contentTypeStr = getParameter(params, "contentType");
                        String nameStr = getParameter(params, "names");
                        if (idsString != null && contentTypeStr != null && nameStr != null) {
                            String[] ids = new Gson().fromJson(idsString, String[].class); // All the MediaStores IDs
                            int[] contentType = new Gson().fromJson(contentTypeStr, int[].class); // 0 = image; 1 = video; 2 = audio
                            String[] names = new Gson().fromJson(nameStr, String[].class); // relative paths
                            for (int i = 0; i < ids.length; i++) {
                                Uri imageUri = ContentUris.withAppendedId(getMediaStoreUri(contentType[i] == 2 ? AvailableMediaStoreUriTypes.AUDIO : contentType[i] == 1 ? AvailableMediaStoreUriTypes.VIDEO : AvailableMediaStoreUriTypes.IMAGE), Long.parseLong(ids[i]));
                                ContentResolver resolver = context.getContentResolver();
                                try {
                                    fileDescriptors.add(resolver.openAssetFileDescriptor(imageUri, "r"));
                                    fileDescriptorNames.add(names[i]);
                                } catch (Exception ignored) {}
                            }
                        } else return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing required fields in body form");
                    } else { // The user has provided the path
                        String pathsStr = getParameter(params, "paths");
                        String isContentUriStr = getParameter(params, "isContentUri"); // 1 = Content Uri + Directory; 0 = only content uri; -1 = either a standard file or a standard directory
                        if (pathsStr != null) {
                            String[] paths = new Gson().fromJson(pathsStr, String[].class);
                            int[] isContentUri = new Gson().fromJson(isContentUriStr, int[].class);
                            for (int i = 0; i < paths.length; i++) { // Add also the children of a directory if it has been selected
                                files.addAll(readFileTree(isContentUri[i] == 1 ? DocumentFile.fromTreeUri(context, Uri.parse(paths[i])) : isContentUri[i] == 0 ? DocumentFile.fromSingleUri(context, Uri.parse(paths[i])) : DocumentFile.fromFile(new File(paths[i]))));
                            }
                        } else return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing required fields in body form");
                    }

                    PipedInputStream pipedInputStream = new PipedInputStream(64 * 1024);
                    try {
                        PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);
                        ExecutorService service = Executors.newSingleThreadExecutor();
                        service.submit(() -> {
                            try (ZipOutputStream stream = new ZipOutputStream(pipedOutputStream)) {
                                // Let's remove from the zip file name the part of the path all files have in common (ex: /storage/emulated/0). This part is passed by the web client as the "source" parameter
                                String sourcePath = getParameter(params, "source");
                                int sourcePathLength = sourcePath == null ? 0 : sourcePath.length() + (sourcePath.endsWith("/") ? 0 : 1);
                                ArrayList<String> addedFiles = new ArrayList<>();
                                for (DocumentFile file: files) {
                                    Uri fileUri = file.getUri();
                                    stream.putNextEntry(new ZipEntry(getSuggestedName(fileUri.toString().substring(sourcePathLength + (fileUri.toString().startsWith("file://") ? 7 : 0)), addedFiles)));
                                    try (InputStream fis = context.getContentResolver().openInputStream(fileUri)) {
                                        fis.transferTo(stream);
                                    }
                                    stream.closeEntry();
                                }
                                for (int i = 0; i < fileDescriptors.size(); i++) {
                                    stream.putNextEntry(new ZipEntry(getSuggestedName(fileDescriptorNames.get(i), addedFiles)));
                                    try (FileInputStream fis = fileDescriptors.get(i).createInputStream()) {
                                        fis.transferTo(stream);
                                    }
                                    stream.closeEntry();
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            } finally {
                                service.shutdown();
                            }
                        });
                        Response response = newChunkedResponse(Response.Status.OK, "application/zip", pipedInputStream);
                        response.addHeader("Content-Disposition", "attachment; filename=\"WebShare-" + System.currentTimeMillis() + ".zip\"");
                        return response;
                    } catch (IOException e) {
                        e.printStackTrace();
                        return corsFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed file creation");
                    }
                }
                case "/delete": { // Delete a selected file
                    Map<String, List<String>> params = session.getParameters();
                    String path = getParameter(params, "path");
                    String id = getParameter(params, "id");
                    String fileType = getParameter(params, "type");
                    if (path != null) {
                        DocumentFile file = Objects.equals(getParameter(params, "isContentUri"), "1") ? DocumentFile.fromTreeUri(context, Uri.parse(path)) : DocumentFile.fromFile(new File(path));
                        if (file != null && (file.isFile() || file.isDirectory())) return corsFixedLengthResponse(file.delete() ? Response.Status.OK : Response.Status.INTERNAL_ERROR, "text/plain", "");
                        return corsFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "The file couldn't be found");
                    } else if (id != null) {
                        Uri imageUri = ContentUris.withAppendedId(getMediaStoreUri(Objects.equals(fileType, "video") ? AvailableMediaStoreUriTypes.VIDEO : Objects.equals(fileType, "audio") ? AvailableMediaStoreUriTypes.AUDIO : AvailableMediaStoreUriTypes.IMAGE), Long.parseLong(id));
                        ContentResolver resolver = context.getContentResolver();
                        int deletedColumns = resolver.delete(imageUri, null, null);
                        return corsFixedLengthResponse(deletedColumns > 0 ? Response.Status.OK : Response.Status.INTERNAL_ERROR, "text/plain", "");
                    }   else return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "A path hasn't been provided");
                }
                case "/upload": { // Save a file to the user's device
                    Map<String, List<String>> params = session.getParameters();
                    String path = getParameter(params, "path");
                    String replaceContent = getParameter(params, "replace");
                    String lastModified = getParameter(params, "lastModified");
                    File tempFile = null;
                    if (session.getMethod() != Method.POST)
                        return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Only POST event is accepted.");
                    if (path != null) {
                        String contentUri = getParameter(params, "contentUri");
                        DocumentFile file;
                        if (contentUri != null) {
                            file = DocumentFile.fromTreeUri(context, Uri.parse(contentUri));
                            String[] pathSplit = path.split("/");
                            for (int i = 0; i < (pathSplit.length - 1); i++) file = file.createDirectory(pathSplit[i]);
                            String originalLastPath = pathSplit[pathSplit.length - 1];
                            int tryAgain = 1;
                            while (file.findFile(pathSplit[pathSplit.length - 1]) != null) {
                                pathSplit[pathSplit.length - 1] = originalLastPath.substring(0, originalLastPath.lastIndexOf(".")) + "(" + tryAgain + ")" + originalLastPath.substring(originalLastPath.lastIndexOf("."));
                                tryAgain++;
                            }
                            file = file.createFile("application/octet-stream", pathSplit[pathSplit.length - 1]);
                        } else {
                            File folder = new File(path.substring(0, path.lastIndexOf("/")));
                            folder.mkdirs();
                            tempFile = new File(path);
                            int tryAgain = 1;
                            while (tempFile.exists() && !Objects.equals(replaceContent, "1")) {
                                tempFile = new File(path.substring(0, path.lastIndexOf(".")) + " (" + tryAgain + ")" + path.substring(path.lastIndexOf(".")));
                                tryAgain++;
                            }
                            file = DocumentFile.fromFile(tempFile);
                        }
                        try {
                            String contentLengthHeader = session.getHeaders().get("content-length");
                            if (contentLengthHeader == null) return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing Content-Length header");
                            Long contentLength = Long.parseLong(contentLengthHeader);
                            OutputStream outputStream = context.getContentResolver().openOutputStream(file.getUri());
                            InputStream body = session.getInputStream();
                            byte[] buffer = new byte[8192];
                            long totalRead = 0;
                            while (totalRead < contentLength) {
                                int toRead = (int) Math.min(buffer.length, contentLength - totalRead);
                                int read = body.read(buffer, 0, toRead);
                                if (read == -1) break; // Client disconnected early
                                outputStream.write(buffer, 0, read);
                                totalRead += read;
                            }
                            String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(path.substring(path.lastIndexOf(".") + 1));
                            if (lastModified != null && tempFile != null) tempFile.setLastModified(Long.parseLong(lastModified));
                            if (type != null && (type.startsWith("audio/") || type.startsWith("video/") || type.startsWith("image/")) && tempFile != null) // Scan item so that it's indexable by the MediaSource
                                MediaScannerConnection.scanFile(context, new String[]{tempFile.getPath()}, new String[]{type}, (a, b) -> {
                                });
                            return corsFixedLengthResponse(Response.Status.OK, "text/plain", "");
                        } catch (IOException e) {
                            e.printStackTrace();
                            return corsFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed file creation");
                        }
                    } else
                        return corsFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "A path hasn't been provided");
                }
            }
        }
        // Check if a font should be delivered
        if (webPath.startsWith("/ws-")) return newChunkedResponse(Response.Status.OK, "font/woff2", context.getResources().openRawResource(webPath.contains("semibold") ? R.raw.ws_semibold : webPath.contains("bold") ? R.raw.ws_bold : webPath.contains("medium") ? R.raw.ws_medium : R.raw.ws_regular));
        // Otherwise, just return the HTML webpage
        return newChunkedResponse(Response.Status.OK, "text/html", context.getResources().openRawResource(R.raw.index));
    }

    /**
     * Get a single parameter from the list provided by NanoHTTP
     * @param params the list of all parameters
     * @param strToGet the name of the parameter to get
     * @return the string with that parameter, or null if not found
     */
    private String getParameter(Map<String, List<String>> params, String strToGet) {
        List<String> getData = params.get(strToGet);
        if (getData == null) return null;
        String source = getData.get(0);
        if (source == null) return null;
        return source;
    }


    public void startServer() throws IOException {
        start(120000, false);
        if (!ForegroundNotificationHelper.isServiceRunning(context)) { // Start a foreground service so that it can run in background
            Intent serviceIntent = new Intent(context, ForegroundService.class);
            context.startService(serviceIntent);
        }
        // Start WakeLock and WifiLock to ensure the server is always online
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebShare::ServerWakeLock");
            wakeLock.setReferenceCounted(false);
        }
        if (!wakeLock.isHeld()) wakeLock.acquire();
        if (wifiLock == null) {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WebShare::ServerWifiLock");
            wifiLock.setReferenceCounted(false);
        }
        if (!wifiLock.isHeld()) wifiLock.acquire();

    }

    private enum AvailableMediaStoreUriTypes {
        VIDEO, AUDIO, IMAGE, ALBUMART

    }

    /**
     * Get the MediaStore URI for a specific file type
     * @param type the file type
     * @return the URI where the MediaStore contains the files of that type
     */
    private Uri getMediaStoreUri(AvailableMediaStoreUriTypes type) {
        switch(type) {
            case VIDEO:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            case AUDIO:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            case ALBUMART:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) : Uri.parse("content://media/external/audio/albumart");
            default:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
    }

    /**
     * Calculate the size of a folder
     * @param directory the directory whose file should be calculated
     * @return the bytes of that folder
     */
    private long getFolderSize(DocumentFile directory) {
        long result = 0;
        for (DocumentFile file: directory.listFiles()) {
            if (file.isDirectory()) result += getFolderSize(file);
            if (file.isFile()) result += file.length();
        }
        return result;
    }

    /**
     * Get the suggested name for a file, so that it does not overwrite an already-existing file
     * @param tempName the name of the file
     * @param addedFiles the list of files already added
     * @return the suggested name of the file
     */
    private String getSuggestedName(String tempName, ArrayList<String> addedFiles) {
        String name = tempName;
        int i = 1;
        while (addedFiles.contains(name)) {
            int extensionIndex = tempName.lastIndexOf(".");
            if (extensionIndex == -1) name = tempName + " (" + i + ")"; else name = tempName.substring(0, extensionIndex) + " (" + i + ")" + tempName.substring(extensionIndex);
        }
        addedFiles.add(name);
        return name;
    }

    /**
     * Get a NanoHTTP Response by fetching a file from the MediaStore
     * @param session information about the current request
     * @param mimeType the passed mimetype (`video/*`, `audio/*`, `image/*`, `albumart/*`)
     * @param convertTo if not null, the image will be converted to this format (`webp`, `png`, `jpeg`)
     * @param id the ID of the resource to fetch
     * @param outputName the name to add in the Content-Disposition header
     * @return a NanoHTTP response with the file to download
     */
    private Response downloadFileFromMediaStore(IHTTPSession session, String mimeType, String convertTo, String id, String outputName) {
        Uri imageUri = ContentUris.withAppendedId(getMediaStoreUri(mimeType.startsWith("albumart") ? AvailableMediaStoreUriTypes.ALBUMART : mimeType.startsWith("audio") ? AvailableMediaStoreUriTypes.AUDIO : mimeType.startsWith("video") ? AvailableMediaStoreUriTypes.VIDEO : AvailableMediaStoreUriTypes.IMAGE), Long.parseLong(id));
        if (mimeType.startsWith("albumart")) mimeType = "image/jpeg";
        ContentResolver resolver = context.getContentResolver();
        try {
            if (convertTo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // Convert the image
                return newChunkedResponse(Response.Status.OK, "image/" + convertTo, convertImage(ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, imageUri), (decoder, info, src) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE); // Required since Bitmap.compress() can't use a hardware bitmap
                    decoder.setMutableRequired(false);
                }), 60, convertTo));
            }
        } catch (IOException e) {
            return corsFixedLengthResponse(Response.Status.INTERNAL_ERROR, mimeType, "Failed converting image");
        }
        try {
            AssetFileDescriptor afd = resolver.openAssetFileDescriptor(imageUri, "r");
            if (afd == null) {
                return corsFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "We couldn't find an image with a matching ID");
            }
            long fileLength = afd.getLength();
            String rangeHeader = session.getHeaders().get("range");
            InputStream inputStream = new BufferedInputStream(afd.createInputStream(), 65536);;
            if (rangeHeader == null) { // Return the entire file
                Response response = corsFixedLengthResponse(Response.Status.OK, mimeType, inputStream, fileLength);
                response.addHeader("Accept-Ranges", "bytes");
                if (outputName != null) response.addHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(outputName, StandardCharsets.UTF_8).replace("+", "%20"));
                return response;
            }
            // We instead need to read the file range
            long start = 0;
            long end = fileLength - 1;
            String[] splitRange = rangeHeader.replace("bytes=", "").split("-");
            if (splitRange.length == 2) {
                start = Long.parseLong(splitRange[0]);
                end = Long.parseLong(splitRange[1]);
            }
            if (start > end || end >= fileLength) {
                Response response = corsFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "");
                response.addHeader("Content-Range", "bytes */" + fileLength);
                inputStream.close();
                return response;
            }
            inputStream.skip(start);
            Response response = corsFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, inputStream, end - start + 1);
            response.addHeader("Accept-Ranges", "bytes");
            response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            return response;
        } catch (IOException e) {
            e.printStackTrace();
            return corsFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "We couldn't find an image with a matching ID");
        }
    }

    /**
     * Convert an image to a different format
     * @param source the source bitmap to convert
     * @param quality the quality of the output image
     * @param type the type of the output image (`webp`, `png`, `jpeg`)
     * @return an InputStream with the binary data of the new image
     */
    private InputStream convertImage(Bitmap source, int quality, String type) throws IOException {
        PipedInputStream in = new PipedInputStream(64 * 1024);
        PipedOutputStream out = new PipedOutputStream(in);
        new Thread(() -> {
            try (OutputStream os = out) {
                source.compress(Objects.equals(type, "webp") ? Bitmap.CompressFormat.WEBP : Objects.equals(type, "png") ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, quality, os);
            } catch (IOException e) {
                Log.e("ImgConversion", "Failed compression", e);
            } finally {
                source.recycle();
            }
        }, "convert").start();

        return in;
    }

    public void stopServer() {
        stop();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        if (ForegroundNotificationHelper.isServiceRunning(context)) {
            Intent stopIntent = new Intent(context, ForegroundService.class);
            stopIntent.setAction("STOP_FOREGROUND_SERVICE");
            context.startService(stopIntent);
        }
    }

    /**
     * Get all the files in a directory, including its subdirectories
     * @param source the directory to read
     * @return all the DocumentFiles contained in that directory and subdirectory
     */
    private ArrayList<DocumentFile> readFileTree(DocumentFile source) {
        ArrayList<DocumentFile> obj = new ArrayList<>();
        if (!source.exists()) return obj;
        if (source.isDirectory()) {
            for (DocumentFile file : source.listFiles()) {
                obj.addAll(readFileTree(file));
            }
        }
        if (source.isFile()) obj.add(source);
        return obj;
    }

    /**
     * Get a random authentication code the user can use to log in
     * @return the authentication code
     */
    public String generateAuthenticationCode() throws NoSuchAlgorithmException {
        String str = generateRandomString(8, true);
        // We'll save the SHA-256 of the authorization code so that it isn't sent in clear
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(str.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0'); // pad with leading zero if needed
            }
            hexString.append(hex);
        }
        authenticationCode = hexString.toString();
        return str;
    }

    /**
     * Create a random string
     * @param length number of characters of the random string
     * @param skipConfusingLetters if confusing letters (ex: upper i or lower L) shouldn't be added
     * @return the random string
     */
    public String generateRandomString(int length, boolean skipConfusingLetters) {
        String chars = skipConfusingLetters ? "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz123456789" : "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    /**
     * Remove the authentication code from memory. This will forbid further token granting until the user generates a new authentication code
     */
    public void removeAuthenticationCode() {
        authenticationCode = null;
    }

    /**
     * Get the Thumbnail from the MediaStore. Note that this endpoint must not be used for fetching the album art IF the user is on a lower version than Android Q, but instead the `/download` endpoint with the album ID
     * @param context the Context to use
     * @param imageId the ID of the content to fetch
     * @param type the resource type to fetch
     * @return
     */
    private Bitmap getThumbnail(Context context, long imageId, AvailableMediaStoreUriTypes type) {
        ContentResolver resolver = context.getContentResolver();
        Uri imageUri = ContentUris.withAppendedId(getMediaStoreUri(type), imageId);
        Bitmap bitmap = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bitmap = resolver.loadThumbnail(imageUri, new Size(512, 512), null);
            } else if (type == AvailableMediaStoreUriTypes.AUDIO) {
                throw new Exception("Use the download endpoint with the album ID to fetch the album art.");
            } else {
                bitmap = type == AvailableMediaStoreUriTypes.VIDEO ? MediaStore.Video.Thumbnails.getThumbnail(resolver, imageId, MediaStore.Video.Thumbnails.MINI_KIND, null) : MediaStore.Images.Thumbnails.getThumbnail(resolver, imageId, MediaStore.Images.Thumbnails.MINI_KIND, null);
            }
        } catch (Exception e) {
            Log.e("MediaStore", "Failed to load thumbnail for id " + imageId, e);
        }
        return bitmap;
    }

}
