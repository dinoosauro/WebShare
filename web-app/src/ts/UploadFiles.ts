import type { UploadProps } from "./Interfaces/Website";
import Settings from "./Settings";

interface UploadFilesProps {
    directory?: boolean,
    isFromImages?: boolean,
    isFromAudio?: boolean,
    path?: string,
    requestedSubfolder?: string,
    contentUri?: string
}

interface SendFileProps extends UploadFilesProps {
    files: FileList | File[],
    relativePaths?: string[]
}
const obj = {
    /**
     * Start file upload. An input file is automatically created to pick some files
     */
    uploadFiles({directory, isFromImages, path, requestedSubfolder, contentUri, isFromAudio}: UploadFilesProps) {
        if (typeof requestedSubfolder === "string" && !requestedSubfolder.startsWith("/")) requestedSubfolder = `/storage/emulated/0/${requestedSubfolder}`;
           const input = Object.assign(document.createElement("input"), {
               type: "file",
               multiple: true,
               accept: isFromAudio ? "audio/*" : isFromImages ? "image/*,video/*" : undefined,
               directory,
               webkitdirectory: directory,
               onchange: () => {
                   if (input.files) obj.uploadSkippingFilePicker({directory, isFromImages, path, requestedSubfolder, contentUri, isFromAudio, files: input.files});
            }
        });
        input.click();
    },
    /**
     * Start file upload. Files must be manually passed, along with their relative path. If files should be picked, use the `uploadFiles` function.
     */
    uploadSkippingFilePicker({directory, isFromImages, path, requestedSubfolder, contentUri, isFromAudio, files, relativePaths = []}: SendFileProps) {        
        const subfolder = typeof requestedSubfolder !== "string" && (isFromImages || isFromAudio) && Settings.photoViewer.askForSubfolders ? prompt("If you want to upload these images and videos in a subfolder, write its name below. Otherwise, leave this field blank.") : null;
        if (obj._startUpload) obj._startUpload(Array.from(files).map((file, i) => {
             const startPathForPhotoViewer = file.type.startsWith("image") ? Settings.photoViewer.defaultImageUploadPath : file.type.startsWith("audio") ? Settings.audioViewer.defaultAudioUploadPath : Settings.photoViewer.defaultVideoUploadPath;
             return {file, path: (isFromImages || isFromAudio) ? 
                 (path ?? `${(requestedSubfolder || startPathForPhotoViewer)}${(requestedSubfolder || startPathForPhotoViewer).endsWith("/") ? "" : "/"}${subfolder ? `${subfolder}/` : ""}${file.webkitRelativePath || file.name}`)
                 : `${path || ""}${(path || "/").endsWith("/") ? "" : "/"}${relativePaths[i] || file.webkitRelativePath || file.name}`, 
                 id: Math.random(), contentUri}
         }));
    },
    /**
     * Function added by the `UploadInfo` component, since it's the one that handles file upload
     */
    _startUpload: null as null | ((files: UploadProps[], retry?: boolean) => void),
    /**
     * List of functions to call to refresh the available files on the user's drive after a file has been uploaded.
     */
    refreshContent: new Set<() => void>()
}
export default obj;