import ApplyTheming from "./ApplyTheming";

let obj = {
    photoViewer: {
        defaultImageUploadPath: "/storage/emulated/0/Pictures/WebShare",
        defaultVideoUploadPath: "/storage/emulated/0/Movies/WebShare",
        askForSubfolders: true,
        defaultPhotoHeight: 200,
        zoomChange: 50,
        showIconForVideos: true,
        locationMapStyle: "default",
        goToNextImageWhenDeleting: true
    },
    albumViewer: {
        mergeFoldersWithDifferentRelativePath: true
    },
    fileViewer: {
        showContent: {
            mimetype: false,
            size: true,
            lastModified: true
        },
        sortCriteria: "name",
        reverse: false,
        useTableView: false,
        showImagePreview: false,
        showAudioPreview: false,
        showPdfPreview: false
    },
    audioViewer: {
        defaultAudioUploadPath: "/storage/emulated/0/Music/WebShare",
        compactMode: false,
        disableGrid: false,
        gridMetadata: {
            album: true,
            albumArtist: false,
            bitrate: false,
            composer: false,
            trackNum: true,
            year: false,
            artist: true,
            duration: true,
            disc: false,
            genre: true
        }
    },
    grouping: {
        divideAuthorsBy: [] as string[],
        divideAlbumAuthorsBy: [] as string[],
    },
    customArtColors: ["#4a7856", "#4a6f78", "#524a78", "#784a6d", "#784a52", "#785f4a", "#78704a"],
    theme: {
        fetchFromDevice: true,
        colors: {} as {[key: string]: string}
    },
    downloads: {
        useFsApi: true,
        replaceFiles: false,
        concurrentDownloads: 3
    },
    uploads: {
        replaceFiles: false
    },
    defaultSection: "photos",
    language: navigator.language.substring(0, 2)
}

const json = JSON.parse(localStorage.getItem("WebShare-Settings") ?? "{}");
obj = UpdateJsonProperties(json, obj);
obj = UpdateStorage(obj, "WebShare-Settings")

/**
 * Copy the properties of an Object from an object to another one
 * @param json the thing that has the value to update
 * @param update the thing that needs to be updated
 * @returns the updated object
 */
function UpdateJsonProperties(json: any, update: any) {
    // @ts-ignore
    for (let key in json) typeof json[key] === "object" && !Array.isArray(json[key]) ? UpdateJsonProperties(json[key], update[key]) : (typeof json[key] === "object" && Array.isArray(json[key])) || (typeof update !== "undefined") ? update[key] = json[key] : {};
    return update;
}


/**
 * Tracks all the changes made to an Object
 * @param obj the object that should be tracked
 * @param key the LocalStorage key where the new file will be saved
 * @param mainObj the "root" of the object. You should NOT put anything here, since it's used only by the UpdateStorage function while iterating nested objects.
 * @returns the Proxy of that object, that should be set as the new value of the Object
 */
function UpdateStorage(obj: any, key: string, mainObj?: any) {
    const proxy = new Proxy(obj, {
        set: (obj, prop, value) => {
            obj[prop] = value;
            localStorage.setItem(key, JSON.stringify(mainObj ?? proxy));
            return true;
        }
    });
    for (const item in proxy) { // If one of the children is an Object, let's set the proxy also to it
        if (typeof proxy[item] === "object" && !(proxy[item] instanceof Uint8Array) && !(proxy[item] instanceof Blob) && !(proxy[item] instanceof File)) proxy[item] = UpdateStorage(proxy[item], key, mainObj ?? proxy)
    }
    return proxy;
}


export default obj;


if (window.location.hash !== "#skipTheme") ApplyTheming();
