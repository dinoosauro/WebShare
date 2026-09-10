<script lang="ts">
    import { cubicInOut } from "svelte/easing";
    import ApplyTheming from "../ts/ApplyTheming";
    import Settings from "../ts/Settings";
    import Card from "./SmallComponents/Card.svelte";
    import Dialog from "./SmallComponents/Dialog.svelte";
    import ExpandableItems from "./SmallComponents/ExpandableItems.svelte";
    import { slide } from "svelte/transition";
    import OpenSource from "./OpenSource.svelte";
    import lang from "../ts/Lang";
    const {callback, callbacks}: {callback: () => void, callbacks: {
        updatePhotoAlbumNames?: () => void,
        rerenderFilesTab?: () => void,
        changeFileTable?: () => void,
        updateShowIconsForVideos?: () => void,
        divideSongsAgain?: () => void,
        changeAudioPlayerGridMode?: () => void,
        changeAudioPlayerCompactMode?: () => void,
        rerenderAudioPlayerTable?: () => void,
        rerenderLocationMap?: () => void
    }} = $props();

    /**
     * An object that, for each ID, contains the value of the input element with the same ID. It's used in the `changeElement` snippet, since we can't rely on $state there.
     */
    const inputValues: {[key: string]: string} = {};
    /**
     * An object that, for each ID, contains a random string used to refresh the content. It's used in the `changeElement` snippet, since we can't rely on $state there.
     */
    const inputKeys: {[key: string]: string} = $state({});
    /**
     * If the user has enabled custom theming options
     */
    let customTheming = $state(!Settings.theme.fetchFromDevice);
    const themeOptions = [["background", "Background color"], ["text", "Text color"], ["secondtext", "Secondary text color"], ["card", "First card color"], ["secondcard", "Second card color"], ["accent", "Accent color"]];
    for (const [value] of themeOptions) {
        if (typeof Settings.theme.colors[value] === "undefined") Settings.theme.colors[value] = getComputedStyle(document.body).getPropertyValue(`--${value}`);
    }
</script>

{#snippet changeElement(array: string[], inputType = "text", isSecondCard = false, callback?: () => void, id = Math.random().toString())}
    <label class="flex hcenter gap">
        <input type={inputType} bind:value={inputValues[id]}>
        <button onclick={() => {
            array.push(inputValues[id]);
            inputKeys[id] = Math.random().toString();
            callback && callback();
        }}>Add</button>
    </label><br>
    <Card isSecondCard={!isSecondCard}>
        <u>{lang("Already added entries (click to remove)")}:</u><br><br>
        <div class="flex hcenter gap" style="overflow: auto;">
            {#key inputKeys[id] || id}
            {#each array as entry, i (`${entry}-${i}`)}
                <button style={`background-color: var(--${isSecondCard ? "second" : ""}card); color: var(--text);`} onclick={(e) => {
                    array.splice(i, 1);
                    inputKeys[id] = Math.random().toString();
                    callback && callback();
                }}>
                    <div class="flex hcenter gap">
                        {#if inputType === "color"}
                        <div style={`border-radius: 50%; height: 16px; width: 16px; background-color: ${entry};`}></div>
                        {/if}
                        <span>{entry}</span>
                    </div>
                </button>
            {/each}
            {/key}
        </div>
    </Card>
{/snippet}

<Dialog {callback}>
    <h2>{lang("Settings")}:</h2>
    <Card isSecondCard={true}>
        <ExpandableItems title="Language:" size="h3" isOpened={true}>
            <label class="flex hcenter gap">
                Application language: <select onchange={() => window.location.reload()} bind:value={Settings.language}>
                    <option value="en">English</option>
                    <option value="it">Italiano</option>
                </select>
            </label>
        </ExpandableItems>
    </Card><br>
    <Card isSecondCard={true}>
        <ExpandableItems title={lang("File upload:")} size="h3" isOpened={true}>
            <Card>
                <label class="flex hcenter gap">
                    <input type="checkbox">{lang("If a file already exists, replace it")}
                </label>
            </Card>
        </ExpandableItems>
    </Card><br>
    <Card isSecondCard={true}>
        <ExpandableItems title={lang("Photo viewer:")} size="h3" isOpened={true}>
            <Card>
                <label class="flex hcenter gap">
                    {lang("Default path for image upload")}: <input style="background-color: var(--secondcard);" type="text" bind:value={Settings.photoViewer.defaultImageUploadPath}>
                </label><br>
                <label class="flex hcenter gap">
                    {lang("Default path for video upload")}: <input style="background-color: var(--secondcard);" type="text" bind:value={Settings.photoViewer.defaultVideoUploadPath}>
                </label><br>
                <label class="flex hcenter gap">
                    {lang("When downloading an image")}, <select style="background-color: var(--secondcard);" bind:value={Settings.photoViewer.downloadImageFormat}>
                        <option value="default">download the original file</option>
                        <option value="jpg">convert it to JPEG</option>
                        <option value="png">convert it to PNG</option>
                        <option value="webp">convert it to WebP</option>
                    </select>
                </label><br>
                <label class="flex hcenter gap">
                    <input type="checkbox" bind:checked={Settings.photoViewer.askForSubfolders}>{lang("Ask every time if the files should be uploaded in a subfolder")}
                </label><br>
                <label class="flex hcenter gap">
                    {lang("Default photo height (in pixels)")}: <input style="background-color: var(--secondcard);" type="number" min="50" bind:value={Settings.photoViewer.defaultPhotoHeight}>
                </label><br>
                <label class="flex hcenter gap">
                    {lang("When zooming, increase/decrease the picture of (in pixels)")}: <input type="number" style="background-color: var(--secondcard);" bind:value={Settings.photoViewer.zoomChange} min="1">
                </label><br>
                <label class="flex hcenter gap">
                    <input type="checkbox" onchange={() => (callbacks.updateShowIconsForVideos && callbacks.updateShowIconsForVideos())} bind:checked={Settings.photoViewer.showIconForVideos}>{lang("Show the video duration in the bottom-left corner of the photo viewer")}
                </label><br>
                <label class="flex hcenter gap">
                    <input type="checkbox" bind:checked={Settings.photoViewer.goToNextImageWhenDeleting}>{lang("Go to the next image when an image has been deleted in the fullscreen viewer mode. If disabled, the website will close the fullscreen photo viewer")}
                </label><br>
                <label class="flex hcenter gap">
                    {lang("Location map style")}: <select onchange={() => callbacks.rerenderLocationMap && callbacks.rerenderLocationMap()} bind:value={Settings.photoViewer.locationMapStyle} style="background-color: var(--secondcard);">
                        <option value="default">{lang("Default")}</option>
                        <option value="light">{lang("Light")}</option>
                        <option value="dark">{lang("Dark")}</option>
                        <option value="satellite">{lang("Satellite")}</option>
                    </select>
                </label>
            </Card>
        </ExpandableItems>
    </Card><br>
    <Card isSecondCard={true}>
        <ExpandableItems title={lang("Album viewer:")} size="h3" isOpened={true}>
           <Card>
            <label class="flex hcenter gap">
                <input type="checkbox" bind:checked={Settings.albumViewer.mergeFoldersWithDifferentRelativePath} onchange={() => callbacks.updatePhotoAlbumNames && callbacks.updatePhotoAlbumNames()}>{lang("If a folder has the same name, but it's stored in a different path (ex: Downloads/A and Pictures/A), put them in two different albums")}
            </label>
           </Card>
        </ExpandableItems>
    </Card><br>
    <Card isSecondCard={true}>
        <ExpandableItems title={lang("File viewer:")} size="h3" isOpened={true}>
            <Card>
                <h4>{lang("File information:")}</h4>
                <label class="flex hcenter gap">
                    <input type="checkbox" onchange={() => callbacks.rerenderFilesTab && callbacks.rerenderFilesTab()} bind:checked={Settings.fileViewer.showContent.lastModified}>{lang("Show the last modified date below each file name")}
                </label><br>
                <label class="flex hcenter gap">
                    <input type="checkbox" onchange={() => callbacks.rerenderFilesTab && callbacks.rerenderFilesTab()} bind:checked={Settings.fileViewer.showContent.mimetype}>{lang("Show the file mimetype below each file name")}
                </label><br>
                <label class="flex hcenter gap">
                    <input type="checkbox" onchange={() => callbacks.rerenderFilesTab && callbacks.rerenderFilesTab()} bind:checked={Settings.fileViewer.showContent.size}>{lang("Show the file size below each file name")}
                </label>
            </Card><br>
            <Card>
                <h4>{lang("Sorting options")}:</h4>
                <label class="flex hcenter gap">
                    {lang("Default sorting option")}: <select style="background-color: var(--secondcard);" bind:value={Settings.fileViewer.sortCriteria}>
                        <option value="name">{lang("by file name")}</option>
                        <option value="lastModified">{lang("by last modified date")}</option>
                        <option value="size">{lang("by file size")}</option>
                    </select>
                </label><br>
                <label class="flex hcenter gap">
                    <input type="checkbox" bind:checked={Settings.fileViewer.reverse}>{lang("Automatically reverse the list")}
                </label>
            </Card><br>
            <Card>
                <h4>{lang("Grid options")}:</h4>
                <label class="flex hcenter gap">
                    <input type="checkbox" bind:checked={Settings.fileViewer.useTableView} onchange={() => callbacks.changeFileTable && callbacks.changeFileTable()} >{lang("Show files in a table instead of a grid")}
                </label><br>
                <label class="flex hcenter gap">
                    <input type="checkbox" bind:checked={Settings.fileViewer.showImagePreview} onchange={() => callbacks.rerenderFilesTab && callbacks.rerenderFilesTab()}>{lang("Show a preview for image and video files")}
                </label><br>
                <label class="flex hcenter gap">
                    <input type="checkbox" bind:checked={Settings.fileViewer.showAudioPreview} onchange={() => callbacks.rerenderFilesTab && callbacks.rerenderFilesTab()}>{lang("Show a preview for audio files")}
                </label><br>
                <label class="flex hcenter gap">
                    <input type="checkbox" bind:checked={Settings.fileViewer.showPdfPreview} onchange={() => callbacks.rerenderFilesTab && callbacks.rerenderFilesTab()}>{lang("Show a preview for PDF files")}
                </label>
            </Card>
        </ExpandableItems>
    </Card><br>
    <Card isSecondCard={true}>
        <ExpandableItems title={lang("Songs viewer:")} size="h3" isOpened={true}>
           <Card>
           <h4>{lang("General settings")}:</h4>
            <label class="flex hcenter gap">
                {lang("Default path for audio upload")}: <input style="background-color: var(--secondcard);" type="text" bind:value={Settings.audioViewer.defaultAudioUploadPath}>
            </label><br>
            <label class="flex hcenter gap">
                <input type="checkbox" bind:checked={Settings.audioViewer.compactMode} onchange={() => callbacks.changeAudioPlayerCompactMode && callbacks.changeAudioPlayerCompactMode()}>{lang("Enable compact mode")}
            </label><br>
            <label class="flex hcenter gap">
                <input type="checkbox" bind:checked={Settings.audioViewer.disableGrid} onchange={() => callbacks.changeAudioPlayerGridMode && callbacks.changeAudioPlayerGridMode()}>{lang("Show songs in a table instead of a grid")}
            </label>
           </Card><br>
           <Card>
            <h4>{lang("Fallback album art colors")}:</h4>
            {@render changeElement(Settings.customArtColors, "color")}
           </Card><br>
           <Card>
            <h4>{lang("Artist division (experimental)")}:</h4>
            <p>{lang("Sometimes, you might have some artists split by some characters (for example, a comma). Here you can put these symbols so that you'll be able to treat `Artist A, Artist B` as two separate artists")}</p>
            <Card isSecondCard={true}>
                <div style="height: 10px;"></div>
                <strong>{lang("For artists")}:</strong><br><br>
                {@render changeElement(Settings.grouping.divideAuthorsBy, undefined, true, callbacks.divideSongsAgain)}
            </Card><br>
            <Card isSecondCard={true}>
                <div style="height: 10px;"></div>
                <strong>{lang("For album artists")}:</strong><br><br>
                {@render changeElement(Settings.grouping.divideAlbumAuthorsBy, undefined, true, callbacks.divideSongsAgain)}
            </Card><br>
                        <Card isSecondCard={true}>
                <h4>{lang("Metadata to show in the table")}:</h4>
                {#each [["album", "Album"], ["artist", "Artists"], ["albumArtist", "Album artists"], ["composer", "Composer"], ["trackNum", "Track number"], ["genre", "Genre"], ["disc", "Disc number"], ["year", "Year"], ["duration", "Duration"], ["bitrate", "Bitrate"]] as [key, description]}
                <label class="flex hcenter gap">
                    <input type="checkbox" class="cardColor" onchange={() => callbacks.rerenderAudioPlayerTable && callbacks.rerenderAudioPlayerTable()} bind:checked={Settings.audioViewer.gridMetadata[key as "album"]}>
                    {lang(description)}
                </label><br>
                {/each}
            </Card>
           </Card>
        </ExpandableItems>
    </Card><br>
    <Card isSecondCard={true}>
        <ExpandableItems title={lang("Download multiple items:")} size="h3" isOpened={true}>
            <Card>
                <label class="flex hcenter gap">
                <input type="checkbox" bind:checked={Settings.downloads.useFsApi}>{lang("Download multiple files directly on the device if possible. If disabled or not supported by the browser, a zip file will be downloaded.")}
                </label><br>
                <label class="flex hcenter gap">
                    <input type="checkbox" bind:checked={Settings.downloads.replaceFiles}>{lang("Replace the files saved on this device")}
                </label><br>
                <label class="flex hcenter gap">
                    {lang("Maximum concurrent downloads")}: <input type="number" bind:value={Settings.downloads.concurrentDownloads} min="1">
                </label>
            </Card>
        </ExpandableItems>
    </Card><br>
    <Card isSecondCard={true}>
        <ExpandableItems title={lang("Application theme:")} size="h3" isOpened={true}>
            <Card>
                <label class="flex hcenter gap">
                    <input type="checkbox" bind:checked={Settings.theme.fetchFromDevice} onchange={() => {ApplyTheming(); customTheming = !Settings.theme.fetchFromDevice}}> {lang("Get colors from the smartphone's theme")}
                </label>
                {#if customTheming}
                <div in:slide={{duration: 200, easing: cubicInOut}} out:slide={{duration: 200, easing: cubicInOut}}>
                    <br>
                    <Card>
                        <strong style="display: block; margin: 10px 0px">{lang("Custom colors")}:</strong>
                        <div class="flex hcenter gap" style="flex-wrap: wrap;">
                            {#each themeOptions as [value, description]}
                                <Card isSecondCard={true}>
                                    <div class="flex hcenter gap">
                                        <input type="color" style="width: 75px; padding: 5px" bind:value={Settings.theme.colors[value]} onchange={() => ApplyTheming()}>
                                        <span>{description}</span>
                                    </div>
                                </Card>
                            {/each}
                        </div>
                    </Card>
                </div>
                {/if}
            </Card>
        </ExpandableItems>
    </Card><br>
    <Card isSecondCard={true}>
        <ExpandableItems title={lang("Application behavior:")} size="h3" isOpened={true}>
            <Card>
                <label class="flex hcenter gap">
                    {lang("Default application section")}: <select style="background-color: var(--secondcard);" bind:value={Settings.defaultSection}>
                        <option value="photos">{lang("photos and videos")}</option>
                        <option value="albums">{lang("albums")}</option>
                        <option value="songs">{lang("songs")}</option>
                        <option value="file">{lang("files")}</option>
                    </select>
                </label>
            </Card>
        </ExpandableItems>
    </Card><br>
    <OpenSource></OpenSource>
</Dialog>