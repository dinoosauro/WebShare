<script lang="ts">
    import { icon } from "leaflet";
    import type { MediaInfo } from "../../ts/Interfaces/API";
    import type { UploadProps } from "../../ts/Interfaces/Website";
    import StartPath from "../../ts/StartPath";
    import UploadFiles from "../../ts/UploadFiles";
    import Card from "../SmallComponents/Card.svelte";
    import InputRange from "../SmallComponents/InputRange.svelte";
    import PhotoPreview from "./PhotoPreview.svelte";
    import { getIconSrc } from "../../ts/IconManager";
    import { fade, slide } from "svelte/transition";
    import { cubicInOut } from "svelte/easing";
    import ImageIntersectionViewer from "./ImageIntersectionViewer.svelte";
    import ExpandableItems from "../SmallComponents/ExpandableItems.svelte";
    import Settings from "../Settings.svelte";
    import SettingsObject from "../../ts/Settings"
    import topBtnContainerTransition from "../../ts/TopButtonsTransition";
    import SelectComponent from "../SmallComponents/SelectComponent.svelte";
    import MassSelectionModeHelpers from "../../ts/MassSelectionModeHelpers";
    import DropdownMenu from "../SmallComponents/DropdownMenu.svelte";
    import { onMount } from "svelte";
    import imageOpenTransition from "../../ts/OpenImageAnimation";
    import ReadDroppedFiles from "../../ts/ReadDroppedFiles";
    import lang from "../../ts/Lang";
    import FileSystemApiHelper from "../../ts/FileSystemApiHelper";
    import PlaceholderToUpdate from "./PlaceholderToUpdate.svelte";
    import RegenerateIcons from "../../ts/RegenerateIcons";

    const {token, albumView}: {
        token: string, 
        /**
         * If albums should be displayed instead of single images
         */
        albumView?: boolean
    } = $props();

      async function refreshContent() {
        const imageReq = await fetch(`${StartPath}/api/getImages`, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });
        if (!imageReq.ok) throw new Error("Failed image fetching");
        const imageJson = await imageReq.json() as MediaInfo[];
        const videoReq = await fetch(`${StartPath}/api/getVideos`, {
        headers: {
        Authorization: `Bearer ${token}`
        }
        });
        if (!videoReq.ok) throw new Error("Failed video fetching");
        const videoJson = await videoReq.json() as MediaInfo[];
        images = [...imageJson, ...videoJson].sort((a, b) => (b.dateTaken || b.dateModified || b.dateAdded) - (a.dateTaken || b.dateModified || b.dateAdded));
    }
    /**
     * Order the images by their timestamp
     * @param images the array with all the images loaded
     * @returns An array with the year name, and a nested array that, for each month and for each day inside of that month, contains a list of image information
     */
    function getImageByTimestamp(images: MediaInfo[]) {
        const output: {[year: string]: MediaInfo[][][]} = {};
        for (const image of images) {
            const date = new Date(image.dateTaken || image.dateModified || image.dateAdded);
            const year = date.getUTCFullYear().toString();
            if (!output[year]) output[year] = [];
            const month = date.getMonth()
            if (!output[year][12 - month]) output[year][12 - month] = [];
            const day = date.getDate();
            if (!output[year][12 - month][31 - day]) output[year][12 - month][31 - day] = [];
            output[year][12 - month][31 - day].push(image);
        }
        const newOutput = Object.entries(output).sort((a, b) => +b[0] - +a[0]);
        return newOutput;
    }
    /**
     * All the loaded images and videos
     */
    let images: MediaInfo[] = $state([]);
    /**
     * Image divided by timestamp
     */
    let imageState = $derived(getImageByTimestamp(images));
    /**
     * If an icon with the video duration should be displayed in the bottom-left corner of videos
     */
    let showIconsForVideos = $state(SettingsObject.photoViewer.showIconForVideos);

    /**
     * Get the relative path of a file
     * @param relPath the path to look
     */
    function getRelativePathName(relPath: string) {
        relPath = relPath.substring(0, relPath.lastIndexOf("/"));
        if (!SettingsObject.albumViewer.mergeFoldersWithDifferentRelativePath) return relPath;
        if (relPath.indexOf("/") !== -1) return relPath.substring(relPath.lastIndexOf("/") + 1);
        return relPath;
    }
    /**
     * Divide images by each folder
     * @param images the images already divided by timestamp
     * @returns an array composed of [the folder name, [the same syntax used for the images divided by timestamp]]
     */
    function divideImagesByFolder(images: typeof imageState) {
        let outputObject: {[albumName: string]: {[year: string]: MediaInfo[][][]}} = {};
        let firstElementOf: {[albumName: string]: MediaInfo} = {};
        /**
         * If the client has sent the `isFavorite` property, the user is using an Android version that supports adding images/videos as favorites. We also need to check that the property is true at least for one image, since otherwise creating a Favorites folder would be useless
         */
        const areFavoritesEnabled = images.flat(4).some(i => typeof i !== "string" && i.isFavorite);
        /**
         * To avoid mixing "real" favorites and the files the user has in a hypotetical folder called "Favorites", we'll generate a random name that'll be used to store all the favorites. The probability the user will have a folder called this should be quite low.
         */
        const favoriteStr = `${Math.random()}-${Math.random()}-Favorites-${Math.random()}-${Math.random()}`;
        if (areFavoritesEnabled) outputObject[favoriteStr] = {}; // Let's add the Favorites folder immediately so that it'll be the first in the list
        for (const [year, yearImages] of images) {
            for (let month = 0; month < yearImages.length; month++) {
                if (typeof yearImages[month] === "undefined") continue;
                for (let day = 0; day < yearImages[month].length; day++) {
                    if (typeof yearImages[month][day] === "undefined") continue;
                    for (const image of yearImages[month][day]) {
                        const relPath = getRelativePathName(image.relativePath ?? lang("No album"));
                        for (const elementToAdd of [...(areFavoritesEnabled && image.isFavorite ? [favoriteStr] : []), relPath]) {
                            if (!firstElementOf[elementToAdd]) firstElementOf[elementToAdd] = image;
                            if (!outputObject[elementToAdd]) outputObject[elementToAdd] = {};
                            if (!outputObject[elementToAdd][year]) outputObject[elementToAdd][year] = [];
                            if (!outputObject[elementToAdd][year][month]) outputObject[elementToAdd][year][month] = [];
                            if (!outputObject[elementToAdd][year][month][day]) outputObject[elementToAdd][year][month][day] = [];
                            outputObject[elementToAdd][year][month][day].push(image);
                        }
                    }
                }
            } 
        }
        const outputEntries: {[albumName: string]: typeof imageState} = {};
        for (const key in outputObject) outputEntries[key] = Object.entries(outputObject[key]).sort((a, b) => +b[0] - +a[0]);
        const entries = Object.entries(outputEntries).map(i => [i[0] === favoriteStr ? lang("Favorites") : i[0], i[1], firstElementOf[i[0]]]);
        return entries as [string, [string, MediaInfo[][][]][], MediaInfo][];
    }
    /**
     * All the available albums, with their metadata divided by year and month
     */
    let availableAlbums = $derived(divideImagesByFolder(imageState));
    /**
     * Information about the selected album: [the album name, [the imageState of the selected number], its position in the `availableAlbums` list]
     */
    let selectedAlbum: [string, [string, MediaInfo[][][]][], number] | undefined = $state();
    /**
     * Get the preview URL for the image
     * @param image the information about the image
     */
    function getPreviewUrl(image: MediaInfo) {
        return `${StartPath}/api/getPreview?id=${encodeURIComponent(image.id)}&token=${encodeURIComponent(token)}&type=${image.mimeType.startsWith("video/") ? "video" : "image"}`
    }
    
    /**
     * [Information about the opened image, the image that was clicked to open the dialog]. Is undefined if no image has been opened.
     */
    let openedImage: [MediaInfo, HTMLImageElement] | undefined = $state();
    /**
     * If the images should be selected instead of opened
     */
    let isSelectModeEnabled = $state(false);
    /**
     * If the settings dialog should be shown or not
     */
    let showSettings = $state(false);
    /**
     * Container of the top-right buttons
     */
    let topBtnContainer: HTMLElement | undefined;
    /**
     * A map that ties to every image/video button its information
     */
    const imageBtnsAvailable: Map<HTMLElement, MediaInfo> = new Map();
    /**
     * Add an image/video button to the `imageBtnsAvailable` map
     */
    function addItemToElementList(node: HTMLElement, info: MediaInfo) {
        imageBtnsAvailable.set(node, info);
        return {
            destroy: () => {
                imageBtnsAvailable.delete(node);
            }
        }
    }
    /**
     * A list of all the selected items
     */
    const itemsSelected: MediaInfo[] = [];
    /**
     * If all the items between the previously-selected item and the currently-selected item should be selected or not
     */
    let isMassSelectionEnabled = $state(false);
    /**
     * The previously clicked image/video button, stored only if `isMassSelectionEnabled` is set to true.
     */
    let prevItemSelected: HTMLElement | undefined;
    /**
     * The form used to download files as a zip
     */
    let downloadForm: HTMLFormElement;

    /**
     * Remove any reference to the selected items
     */
    function clearSelectedItems() {
        isMassSelectionEnabled = false;
        for (const [element] of imageBtnsAvailable) element.classList.remove("selected");
        itemsSelected.splice(0);
    }
    
    /**
     * Make the passeditem previously-selected
     * @param element the image/video button clicked
     */
    function setElementAsPrevSelected(element: HTMLElement) {
        prevItemSelected = element;
        MassSelectionModeHelpers.updateElementStylingToPrevSelected(element);
    }
    /**
     * If it's the first time the component is running. This is done so that the State won't be updated if the user goes forward/back in history
     */
    let isFirstRun = true;
    $effect(() => {
        function registerComponents(..._: any) {}
        registerComponents(selectedAlbum, albumView);
        if (isFirstRun) {
            isFirstRun = false;
            return;
        }
        window.history.pushState({section: albumView ? "albums" : "photos", selectedAlbum: selectedAlbum ? selectedAlbum[0] : undefined}, "");
    })

    $effect(() => {
        if (!albumView) selectedAlbum = undefined; // Added so that the user can switch easily from Photo mode to Album mode
    })
    $effect(() => {
        function registerComponents(_: any) {}
        registerComponents(albumView); // Run this effect when albumView changes
        (() => {loadedItems = 50; loadedAlbums = 30})();
    })

    /**
     * The number of images that can be displayed, including collapsed images
     */
    let loadedItems = $state(50);
    /**
     * A string that contains an identifier of all the ExpandableItems that have been closed at least once. 
     * The website keeps track of this so that it won't ask to load more image if the user closes and then reopens the same div
     */
    let itemsThatHaveBeenClosed = new Set<string>();
    /**
     * A list of all the IDs of the loaded images. It's used to keep track of their position, so that we can apply lazy loading
     */
    let loadedImgsId = $derived((typeof selectedAlbum === "undefined" ? imageState : (selectedAlbum[1])).map(i => i[1]).flat(4).map((a, i) => a.id));
    /**
     * Get the position of the passed picture ID
     * @param id the ID of the image
     */
    function getImageLength(id: number) {
        const index = loadedImgsId.findIndex(i => i === id);
        if (index === -1) {
            loadedImgsId.push(id);
            return loadedImgsId.length - 1;
        }
        return index;
    }
    /**
     * Number of albums currently displayed
     */
    let loadedAlbums = $state(30);

    onMount(() => {
        refreshContent().then(() => {
            if (!albumView) {
                if (window.history.state?.section !== "photos") window.history.pushState({section: "photos"}, "");
                return;
            }
            if (window.history.state?.section === "albums" && window.history.state?.selectedAlbum) {
                const index = availableAlbums.findIndex(i => i[0] === window.history.state?.selectedAlbum);
                if (index !== -1) selectedAlbum = [availableAlbums[index][0], availableAlbums[index][1], index];
            } else window.history.pushState({section: "albums", selectedAlbum: undefined}, "");
        });
        UploadFiles.refreshContent.add(refreshContent);
        function scrollEvent() {
            if (albumView && window.scrollY > (document.body.scrollHeight - window.innerHeight - 100)) loadedAlbums += 30;
        }

        async function popstateEvent(e: PopStateEvent) {
            if (e.state?.imageOpenInfo) { // The user had previously opened an image. We need to find again the image so that we can open it
                for (const [element, info] of imageBtnsAvailable) {
                    if (info.id === e.state?.imageOpenInfo.id && info.mimeType === e.state?.imageOpenInfo.mimeType) { 
                        window.scrollTo({top: window.scrollY + element.getBoundingClientRect().top, behavior: "smooth"}); // We need to scroll up to that element so that the image can be rendered
                        let image: HTMLImageElement | null = null;
                        while (!image) {
                            await new Promise(res => setTimeout(res, 150));
                            image = element.querySelector("img");
                        }
                        openedImage = [info, image];
                        break;
                    }
                }
            }
            if (e.state?.section === "albums") {
                isFirstRun = true;
                if (e.state.selectedAlbum) {
                    const index = availableAlbums.findIndex(i => i[0] === window.history.state?.selectedAlbum);
                    if (index !== -1) selectedAlbum = [availableAlbums[index][0], availableAlbums[index][1], index];
                    return;
                }
                selectedAlbum = undefined;
            }
        }

        async function dropEvent(e: DragEvent) {
            e.preventDefault();
            if (e.dataTransfer?.items) {
                const files = await ReadDroppedFiles(Array.from(e.dataTransfer.items).map(i => i.webkitGetAsEntry()).filter(i => !!i));
                UploadFiles.uploadSkippingFilePicker({isFromImages: true, requestedSubfolder: selectedAlbum ? availableAlbums[selectedAlbum[2]][2].relativePath : undefined, files: files.map(i => i.file), relativePaths: files.map(i => i.path)});
            }
        }
        function dragoverEvent(e: DragEvent) {
            e.preventDefault();
            if (e.dataTransfer) {
                e.dataTransfer.dropEffect = "copy";
                e.dataTransfer.effectAllowed = "copy";
            }
        }
        window.addEventListener("drop", dropEvent);
        window.addEventListener("dragover", dragoverEvent);
        window.addEventListener("popstate", popstateEvent);
        window.addEventListener("scroll", scrollEvent);
        return () => {
            UploadFiles.refreshContent.delete(refreshContent);
            window.removeEventListener("drop", dropEvent);
            window.removeEventListener("dragover", dragoverEvent);
            window.removeEventListener("popstate", popstateEvent);
            window.removeEventListener("scroll", scrollEvent);
        }
    })

    let rerenderLocationMap: (() => void) | undefined;
</script>

<DropdownMenu dropdownInfo={[...(!openedImage ? [{
    title: lang("Upload files"),
    icon: "arrowupload" as "arrowupload",
    id: "upload",
    extra: [{
        title: lang("Pick multiple files"),
        icon: "documentadd" as "documentadd",
        id: "uploadfiles",
        extra: []
    }, {
        title: lang("Pick a folder"),
        icon: "folderadd" as "folderadd",
        id: "uploadfolder",
        extra: []
    }],
}, {
    title: lang("Refresh content"),
    id: "refresh",
    extra: [],
    icon: "arrowsync" as "arrowsync"
}, {
    title: lang("Change image height"),
    id: "zoom",
    icon: "autofitheight" as "autofitheight",
    extra: [{
        title: lang("Zoom in"),
        icon: "zoomin" as "zoomin",
        id: "zoomin",
        extra: [],
        keepOpen: true
    }, {
        title: lang("Zoom out"),
        icon: "zoomout" as "zoomout",
        id: "zoomout",
        extra: [],
        keepOpen: true
    }, {
        title: lang("Go back"),
        icon: "dismiss" as "dismiss",
        id: "zoomback",
        extra: []
    }]
}, {
    title: lang("Select files"),
    id: "select",
    extra: [],
    await: true,
    icon: "selectobj" as "selectobj"
}] : []), {
    title: lang("Settings"),
    id: "settings",
    extra: [],
    await: true,
    icon: "settings"
}]} callback={(id) => {
    switch(id) {
        case "uploadfiles":
            UploadFiles.uploadFiles({isFromImages: true, requestedSubfolder: selectedAlbum ? availableAlbums[selectedAlbum[2]][2].relativePath : undefined});
            break;
        case "uploadfolder": 
            UploadFiles.uploadFiles({isFromImages: true, directory: true, requestedSubfolder: selectedAlbum ? availableAlbums[selectedAlbum[2]][2].relativePath : undefined});
            break;
        case "select":
            if (isSelectModeEnabled) clearSelectedItems();
            isSelectModeEnabled = !isSelectModeEnabled;
            break;
        case "refresh":
            refreshContent();
            break;
        case "zoomin": {
            let currentSize = +getComputedStyle(document.body).getPropertyValue("--picture-height").replace("px", "") + SettingsObject.photoViewer.zoomChange;
            document.body.style.setProperty("--picture-height", `${currentSize}px`);
            break;
        }
        case "zoomout": {
            let currentSize = +getComputedStyle(document.body).getPropertyValue("--picture-height").replace("px", "") - SettingsObject.photoViewer.zoomChange;
            if (currentSize <= 0) return;
            document.body.style.setProperty("--picture-height", `${currentSize}px`);
            break;
        }
        case "settings":
            showSettings = true;
            break;
    }
}} getContainer={(container) => (topBtnContainer = container)}></DropdownMenu>


{#if typeof selectedAlbum !== "undefined"}
    <div class="flex hcenter gap" in:slide={{duration: 400, easing: cubicInOut, delay: 500}} out:slide={{duration: 400, easing: cubicInOut}}>
        <button class="flex hcenter" style="padding: 5px; background-color: var(--card); width: fit-content; border-radius: 50%" onclick={() => {
            window.history.back();
            }}>
            <img use:RegenerateIcons.register={{icon: "arrowleft"}} alt="Go back" class="icon">
        </button>
        <h2>{selectedAlbum[0]}</h2>
    </div>
{/if}

{#if !albumView || typeof selectedAlbum !== "undefined"}

<div in:fade={{duration: 400, easing: cubicInOut, delay: 450}} out:fade={{duration: 400, easing: cubicInOut}}>
{#each (typeof selectedAlbum === "undefined" ? imageState : (selectedAlbum[1])) as [year, yearInfo], p}
    <Card>
        <ExpandableItems title={year} size={"32px"} isOpened={true} callback={(opened) => {
            if (!opened && !itemsThatHaveBeenClosed.has(year)) { // Load all the images, including this year
                itemsThatHaveBeenClosed.add(year);
                loadedItems = (typeof selectedAlbum === "undefined" ? imageState : (selectedAlbum[1])).slice(0, p + 1).map(i => i[1]).flat(4).length + 50;
            } else if (opened && !isSelectModeEnabled) { // Load up to the first 50 images of this year
                loadedItems = (typeof selectedAlbum === "undefined" ? imageState : (selectedAlbum[1])).slice(0, p).map(i => i[1]).flat(4).length + 50;
            }
        }}>
        {#each yearInfo as month, j}
        {#if month !== undefined}
            <Card isSecondCard={true}>
                <ExpandableItems title={(() => {
                    const date = new Date(); 
                    date.setMonth(12 - j); 
                    return date.toLocaleDateString(undefined, {month: "long"});
                })()} size={"28px"} isOpened={true} callback={(opened) => {
                    if (!opened && !itemsThatHaveBeenClosed.has(`${year}-${j}`)) {
                        itemsThatHaveBeenClosed.add(`${year}-${j}`);
                        loadedItems += month.flat(2).length
                    }
                }}>
                {#each month as day, k}
                    {#if day !== undefined}
                    <Card>
                        <ExpandableItems title={(() => {const date = new Date(day[0].dateTaken || day[0].dateModified || day[0].dateAdded); return date.toLocaleDateString(undefined, {weekday: "long", day: "numeric"})})()} size="24px" isOpened={true} callback={(opened) => {
                            if (!opened && !itemsThatHaveBeenClosed.has(`${year}-${j}-${k}`)) {
                                itemsThatHaveBeenClosed.add(`${year}-${j}-${k}`);
                                loadedItems += day.flat().length;
                            }
                        }}>
                            <div class="flex gap wrap">
                                {#each day as image (image.id)}
                                {#if getImageLength(image.id) < loadedItems}
                                <button title={image.name} class={`emptyBtn imageContainer${itemsSelected.find(i => i.id === image.id) ? " selected" : ""}`} use:addItemToElementList={image} onclick={(e) => {
                                    if (isSelectModeEnabled) {
                                        if (isMassSelectionEnabled) {
                                            if (!prevItemSelected) {
                                                setElementAsPrevSelected(e.target as HTMLElement);
                                                return;
                                            }
                                            prevItemSelected.style.border = "";
                                            (e.target as HTMLElement).style.removeProperty("--border-size");
                                            // The mapArr property is the Object.entries of the directoryMap, sorted so that all the entries are in the order they're displayed in the DOM.
                                            // Note also that `currentElementIndex` is always after `prevItemIndex`.
                                            const {mapArr, currentElementIndex, prevItemIndex} = MassSelectionModeHelpers.getSelectedRange(e.target as HTMLElement, imageBtnsAvailable, prevItemSelected)
                                            if (prevItemIndex !== -1 && currentElementIndex !== -1) {
                                                /**
                                                * If all the elements between `prevItemIndex` and `currentElementIndex` should be selected. 
                                                */
                                                const selectAll = mapArr.slice(prevItemIndex, currentElementIndex + 1).some(i => !itemsSelected.find(a => a.id === i[1].id));
                                                for (let i = prevItemIndex; i < currentElementIndex + 1; i++) {
                                                    mapArr[i][0].classList[selectAll ? "add" : "remove"]("selected");
                                                    const currentIndex = itemsSelected.findIndex(a => a.id === mapArr[i][1].id);
                                                    if (selectAll && currentIndex === -1) itemsSelected.push(mapArr[i][1]);;
                                                    if (!selectAll && currentIndex !== -1) itemsSelected.splice(currentIndex, 1);
                                                }
                                            }
                                            prevItemSelected = undefined;
                                            return;
                                        }
                                        // Mass selection mode isn't enabled: just select or deselect the current element
                                        const index = itemsSelected.findIndex(i => i.id === image.id);
                                        if (index === -1) itemsSelected.push(image); else itemsSelected.splice(index, 1);
                                        (e.target as HTMLElement).classList[index === -1 ? "add" : "remove"]("selected");
                                        return;
                                    }
                                    openedImage = [image, (e.target as HTMLElement).firstChild?.firstChild as HTMLImageElement]
                                }}>
                                    <ImageIntersectionViewer isFavorite={image.isFavorite} imagePreviewUrl={getPreviewUrl(image)} name={image.name} duration={showIconsForVideos && image.mimeType.startsWith("video") && typeof image.duration !== "undefined" ? image.duration / 1000 : image.mimeType.startsWith("video") ? true : undefined} suggestedProportion={typeof image.width === "number" && typeof image.height === "number" ? image.width / image.height : undefined}></ImageIntersectionViewer>
                                </button>
                                {:else if getImageLength(image.id) === loadedItems}
                                    <PlaceholderToUpdate callback={() => (loadedItems += 50)}></PlaceholderToUpdate>
                                {/if}
                                {/each}
                            </div>
                        </ExpandableItems>
                    </Card><br>
                    {/if}
                {/each}
                </ExpandableItems>
            </Card><br>
            {/if}
        {/each}
            </ExpandableItems>
        </Card><br>
        {/each}
    </div>
{:else}
<div in:fade={{duration: 400, easing: cubicInOut, delay: 450}} out:fade={{duration: 400, easing: cubicInOut}}>
    <div class="flex gap wrap">
        {#each availableAlbums as album, i}
        {#if i < loadedAlbums}
        <div style="flex: 1 0;">
            <Card>
                <button class={`emptyBtn${album[1][0][1].flat(3).every(a => itemsSelected.find(i => i.id === a.id)) ? " selected" : ""}`} use:addItemToElementList={album[2]} style="width: 100%; border-radius: 12px;" onclick={(e) => {
                    if (isSelectModeEnabled) {
                        if (isMassSelectionEnabled) {
                            if (!prevItemSelected) {
                                setElementAsPrevSelected(e.target as HTMLElement);
                                return;
                            }
                            prevItemSelected.style.border = "";
                            (e.target as HTMLElement).style.removeProperty("--border-size");
                            // The mapArr property is the Object.entries of the directoryMap, sorted so that all the entries are in the order they're displayed in the DOM.
                            // Note also that `currentElementIndex` is always after `prevItemIndex`.
                            const {mapArr, currentElementIndex, prevItemIndex} = MassSelectionModeHelpers.getSelectedRange(e.target as HTMLElement, imageBtnsAvailable, prevItemSelected);
                            if (prevItemIndex !== -1 && currentElementIndex !== -1) {
                                /**
                                * If all the elements between `prevItemIndex` and `currentElementIndex` should be selected. 
                                */
                                const selectAll = mapArr.slice(prevItemIndex, currentElementIndex + 1).some(i => !itemsSelected.find(a => a.id === i[1].id));
                                for (let i = prevItemIndex; i < currentElementIndex + 1; i++) {
                                    const albumInfo = availableAlbums.find(a => a[2].id === mapArr[i][1].id);
                                    if (!albumInfo) continue;
                                    mapArr[i][0].classList[selectAll ? "add" : "remove"]("selected");
                                    const currentIndex = itemsSelected.findIndex(a => a.id === mapArr[i][1].id);
                                    if (selectAll && currentIndex === -1) {
                                        for (const item of albumInfo[1][0][1].flat(3)) {
                                            if (typeof item === "string") continue;
                                            itemsSelected.push(item);
                                        }
                                    }
                                    if (!selectAll && currentIndex !== -1) {
                                        for (const item of albumInfo[1][0][1].flat(3)) {
                                            if (typeof item === "string") continue;
                                            const index = itemsSelected.findIndex(i => i.id === item.id);
                                            if (index !== -1) itemsSelected.splice(index, 1);
                                        }
                                    }
                                }
                            }
                            prevItemSelected = undefined;
                            return;
                        }
                        // Mass selection mode isn't enabled: just select or deselect the current element
                        const flat = album[1][0][1].flat(3); // We need to add all the elements inside of that album, so we'll just flat the array
                        const addItems = flat.some(i => !itemsSelected.find(a => a.id === i.id));
                        (e.target as HTMLElement).classList[addItems ? "add" : "remove"]("selected");
                        if (addItems) {
                            for (const item of flat) {
                                if (!itemsSelected.find(i => item.id === i.id)) itemsSelected.push(item);
                            }
                        } else {
                            for (const item of flat) {
                                const index = itemsSelected.findIndex(i => i.id === item.id);
                                if (index !== -1) itemsSelected.splice(index, 1);
                            }
                        }
                        return;
                    }
                    selectedAlbum = [album[0], album[1], availableAlbums.findIndex(i => i[0] === album[0])];
                }}>
                    <div style="width: 100%; pointer-events: none" class="flex wcenter">
                        <div class="imageContainer">
                            <ImageIntersectionViewer suggestedProportion={typeof album[2].width === "number" && typeof album[2].height === "number" ? album[2].width / album[2].height : undefined} name={album[0]} imagePreviewUrl={getPreviewUrl(album[2])}></ImageIntersectionViewer>
                        </div>
                    </div>
                    <p style="pointer-events: none;">{album[0]}</p>
                </button>
            </Card>
        </div>
        {/if}
        {/each}
    </div>
    {#if loadedAlbums < availableAlbums.length} 
    <button onclick={() => (loadedAlbums += 30)}>{lang("Load more items")}</button>
    {/if}
</div>
{/if}

{#if openedImage}
<PhotoPreview nextImage={async () => {
    if (!openedImage) return;
    const entries = Array.from(imageBtnsAvailable);
    const currentImg = entries.findIndex(i => (openedImage as [MediaInfo, HTMLImageElement])[0].id === i[1].id);
    if (currentImg === -1) return;
    const nextElement = entries.length - 1 === currentImg ? entries[0] : entries[currentImg + 1];
    openedImage[0] = nextElement[1];
}} prevImage={async () => {
    if (!openedImage) return;
    const entries = Array.from(imageBtnsAvailable);
    const currentImg = entries.findIndex(i => (openedImage as [MediaInfo, HTMLImageElement])[0].id === i[1].id);
    if (currentImg === -1) return;
    const prevElement = currentImg === 0 ? entries[entries.length - 1] : entries[currentImg - 1];
    openedImage[0] = prevElement[1];
}} getLocationReload={(fn) => (rerenderLocationMap = fn)}
backFn={async (image, main, deletedElements) => {
    if (!openedImage) return;
    let img = openedImage[1];
    // Since the user might have changed the image from the PhotoPreview component, we'll try fetching the current image again
    const entries = Array.from(imageBtnsAvailable);
    const currentImg = entries.find(i => (openedImage as [MediaInfo, HTMLImageElement])[0].id === i[1].id);
    if (currentImg) {
        if (!currentImg[0].querySelector("img")) window.scrollTo({top: window.scrollY + currentImg[0].getBoundingClientRect().top - 15, behavior: "instant"}); // Scroll so that the image starts being visible
        while (!currentImg[0].querySelector("img")) await new Promise<void>(res => setTimeout(res, 10));
        img = currentImg[0].querySelector("img") as HTMLImageElement;
        while (!img.complete) await new Promise<void>(res => setTimeout(res, 50));
        await new Promise(res => setTimeout(res, 50));
    }
    await imageOpenTransition(image, img, main, true);
    // Now let's remove from the DOM the images that have been deleted
    const availableImages = Array.from(imageBtnsAvailable);
    for (const deletedImage of deletedElements) {
        const index = images.indexOf(deletedImage);
        const buttonElement = availableImages.find(i => i[1] === deletedImage);
        if (buttonElement) { // Let's apply an opacity animation, and then let's delete the files
            new Promise(res => buttonElement[0].animate([{opacity: 1}, {opacity: 0}], {duration: 200, easing: "ease-in-out"}).addEventListener("finish", res)).then(() => {
                if (index !== -1) images.splice(index, 1);
            });
        } else if (index !== -1) images.splice(index, 1);
    }
    openedImage = undefined;
}} {token} data={openedImage[0]} sourceImage={getPreviewUrl(openedImage[0])} callback={async (image, main) => {
    if (!openedImage) return;
    imageOpenTransition(openedImage[1], image, main);
}}></PhotoPreview>
{/if}

<form style="display: none;" target="_blank" method="POST" action={`${StartPath}/api/zip?token=${encodeURIComponent(token)}`} bind:this={downloadForm}>
    <input name="type" value="media">
    <input name="ids">
    <input name="contentType">
    <input name="names">
    <input name="convertTo">
</form>


{#if isSelectModeEnabled}
<SelectComponent changeMassSelectionCallback={(e) => (isMassSelectionEnabled = e)} {isMassSelectionEnabled} downloadCallback={async () => {
    const formData = {
        ids: itemsSelected.map(i => i.id),
        contentType: itemsSelected.map(i => i.mimeType.startsWith("video") ? "1" : "0"),
        names: itemsSelected.map(i => `${!albumView || typeof selectedAlbum !== "undefined" ? "" : `${getRelativePathName(i.relativePath ?? lang("No album"))}/`}${i.name}`),
        convertTo: itemsSelected.map(i => i.mimeType.startsWith("video") ? "default" : SettingsObject.photoViewer.downloadImageFormat)
    };
    if (typeof window.showDirectoryPicker !== "undefined") { // Save the files using the File System API instead of downloading a zip file
        try {
            if (!SettingsObject.downloads.useFsApi) throw new Error("The user doesn't want to use the File System API");
            const picker = await window.showDirectoryPicker({id: "PictureMode", mode: "readwrite", startIn: "pictures"});
            let index = -1;
            async function nextItem() { // As a separate function instead of a for loop so that multiple files can be downloaded at the same time
                index++;
                const i = index;
                if (formData.ids.length <= i) return;
                const nameDir = formData.names[i].split("/");
                let fileName = nameDir.pop() ?? formData.names[i];
                if (formData.convertTo[i] !== "default") fileName = `${fileName.substring(0, fileName.lastIndexOf("."))}.${formData.convertTo[i] === "jpeg" ? "jpg" : formData.convertTo[i]}`
                let outputHandle = picker;
                for (const dir of nameDir) outputHandle = await picker.getDirectoryHandle(dir, {create: true});
                if (!SettingsObject.downloads.replaceFiles) { // Check if a file in the same directory exists
                    const availableFiles: string[] = [] 
                    for await (const file of outputHandle.values()) availableFiles.push(file.name);
                    let tempFileName = fileName;
                    let j = 1;
                    while (availableFiles.indexOf(fileName) !== -1) {
                        fileName = `${tempFileName.substring(0, tempFileName.lastIndexOf("."))} (${j})${tempFileName.substring(tempFileName.lastIndexOf("."))}`;
                        j++;
                    }
                }
                let outputFileHandle = await picker.getFileHandle(fileName, {create: true});
                await FileSystemApiHelper.pipeContent(`${StartPath}/api/download?id=${encodeURIComponent(formData.ids[i])}&mimetype=${encodeURIComponent(formData.contentType[i] === "1" ? "video/mp4" : "image/jpeg")}&token=${encodeURIComponent(token)}${formData.convertTo[i] !== "default" ? `&convertTo=${encodeURIComponent(formData.convertTo[i])}` : ""}`, fileName, outputFileHandle);
                nextItem();
            }
            for (let i = 0; i < SettingsObject.downloads.concurrentDownloads; i++) nextItem();
            return;
        } catch(ex) {
            console.warn(ex);
        }
    }
    // Populate the zip download form
    (downloadForm.querySelector("[name=ids]") as HTMLInputElement).value = JSON.stringify(formData.ids);
    (downloadForm.querySelector("[name=contentType]") as HTMLInputElement).value = JSON.stringify(formData.contentType);
    (downloadForm.querySelector("[name=names]") as HTMLInputElement).value = JSON.stringify(formData.names);
    (downloadForm.querySelector("[name=convertTo]") as HTMLInputElement).value = JSON.stringify(formData.convertTo);
    downloadForm.submit();
}} deleteCallback={async (e: Event) => {
    if (confirm(lang("Are you sure you want to delete the selected files? This action can't be undone."))) {
        ((e.target as HTMLImageElement).parentElement as HTMLButtonElement).disabled = true;
        for (let i = 0; i < itemsSelected.length; i++) {
            const req = await fetch(`${StartPath}/api/delete?id=${encodeURIComponent(itemsSelected[i].id)}&type=${itemsSelected[i].mimeType.startsWith("video") ? "video" : "image"}`, {
                headers: {
                    Authorization: `Bearer ${token}`
                }
            });
            if (req.ok) {
                const index = images.findIndex(j => j.id == itemsSelected[i].id);
                if (index !== -1) images.splice(index, 1);
                itemsSelected.splice(i, 1);
                i--;
            }
        }
        ((e.target as HTMLImageElement).parentElement as HTMLButtonElement).disabled = false;
    }
}} closeCallback={async () => {
    clearSelectedItems();
    isSelectModeEnabled = false;
    if (topBtnContainer) topBtnContainerTransition(topBtnContainer, true).then(() => topBtnContainer && topBtnContainerTransition(topBtnContainer))
}} favoriteCallback={images.some(i => typeof i.isFavorite !== "undefined") ? async () => {
    const markAsFavorite = itemsSelected.some(i => !i.isFavorite);
    for (const element of itemsSelected) {
        if (element.isFavorite === markAsFavorite) continue;
        const req = await fetch(`${StartPath}/api/addfavorites?id=${encodeURIComponent(element.id)}&isVideo=${element.mimeType.startsWith("video") ? "1" : "0"}&favorite=${markAsFavorite ? "1" : "0"}`, {headers: {Authorization: `Bearer ${token}`}});
        if (req.ok) element.isFavorite = markAsFavorite;
    }
} : undefined}></SelectComponent>
{/if}

{#if showSettings}
<Settings callbacks={{updatePhotoAlbumNames: () => (availableAlbums = divideImagesByFolder(imageState)), updateShowIconsForVideos: () => (showIconsForVideos = SettingsObject.photoViewer.showIconForVideos), rerenderLocationMap}} callback={() => (showSettings = false)}></Settings>
{/if}

<style>
    .wrap {
        flex-wrap: wrap;
    }

    .imageContainer {
        height: var(--picture-height); 
        border-radius: 12px; 
        border: 1px solid var(--text);
    }
</style>

