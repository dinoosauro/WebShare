<script lang="ts">
    import { onMount } from "svelte";
    import type { AudioMetadata } from "../../ts/Interfaces/API";
    import ImageIntersectionViewer from "../PhotoViewer/ImageIntersectionViewer.svelte";
    import Card from "../SmallComponents/Card.svelte";
    import AlbumViewer from "./AlbumViewer.svelte";
    import imageOpenTransition from "../../ts/OpenImageAnimation";
    import GetGroupingRegex from "../../ts/GetGroupingRegex";
    import CreateAlbumArt from "../../ts/CreateAlbumArt";
    import PlayAudio from "../../ts/PlayAudio";
    import DropdownMenu from "../SmallComponents/DropdownMenu.svelte";
    import UploadFiles from "../../ts/UploadFiles";
    import StartPath from "../../ts/StartPath";
    import SettingsObject from "../../ts/Settings"
    import SelectComponent from "../SmallComponents/SelectComponent.svelte";
    import topBtnContainerTransition from "../../ts/TopButtonsTransition";
    import MassSelectionModeHelpers from "../../ts/MassSelectionModeHelpers";
    import Settings from "../Settings.svelte";
    import ReadDroppedFiles from "../../ts/ReadDroppedFiles";
    import lang from "../../ts/Lang";
    import convertNumberToStr from "../../ts/ConvertNumberToString";
    import { getIconSrc } from "../../ts/IconManager";
    import FileSystemApiHelper from "../../ts/FileSystemApiHelper";
    import RegenerateIcons from "../../ts/RegenerateIcons";

    const {token}: {token: string} = $props();

    /**
     * A list of all the audio files stored on the user's device
     */
    let availableAudioFiles: AudioMetadata[] = $state([]);
    /**
     * Information about the dividing criteria used ["album" | "albumartist" | "artist" | "track", if the list has been reversed or not]
     */
    let sortingInfo = $state(["album", false]);
    /**
     * Audio files divided by the selected criteria and sorted.
     */
    let sortedAudioFiles = $derived(sortAudioFiles(availableAudioFiles));
    async function refreshContent() {
        const req = await fetch(`${StartPath}/api/getAudio`, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });
        if (req.ok) {
            availableAudioFiles = await req.json();
        }
    }
    /**
     * If the application has already fetched some songs. This is used so that the application won't change the History State if the user has just went back from history 
     */
    let firstSortingDone = false;
    function sortAudioFiles(list: AudioMetadata[]) {
        if (firstSortingDone) {
            window.history.pushState({section: "songs", sortingInfo: JSON.parse(JSON.stringify(sortingInfo))}, "");
        } else firstSortingDone = true;
        const output: {[key: string]: AudioMetadata[]} = {};
        switch(sortingInfo[0]) {
            case "album": {
                for (const file of list) {
                    const key = file.album || "Unknown";
                    if (!output[key]) output[key] = [];
                    output[key].push(file);
                }
                for (const key in output) {
                    output[key].sort((a, b) => (a.cdTrack ?? -1) - (b.cdTrack ?? -1));
                }
                break;
            }
            case "artist":
            case "albumartist": {
                for (const file of list) {
                    const keys = ((sortingInfo[0] === "albumartist" ? file.albumArtist : file.artist) || "Unknown").split(GetGroupingRegex(sortingInfo[0] === "albumartist")).map(i => i.trim());
                    for (const key of keys) {
                        if (!output[key]) output[key] = [];
                        output[key].push(file);
                    }
                }
                for (const key in output) {
                    output[key].sort((a, b) => (a.title || a.name).localeCompare(b.title || b.name));
                }
                break;
            }
            case "track": {
                let sorted = [...list].sort((a, b) => (a.title || a.name).localeCompare(b.title || b.name));
                for (let i = 0; i < sorted.length; i++) {
                    output[i.toString()] = [sorted[i]];
                }
                break;
            }
        }
        const entries = Object.entries(output);
        if (sortingInfo[0] !== "album" && sortingInfo[0] !== "track") entries.sort((a, b) => a[0].localeCompare(b[0]));
        if (sortingInfo[1]) entries.reverse();
        return entries;
    }

    /**
     * A map that ties each music ID to [the music button, information about the songs it contains]. See also `albumElementsMap`
     */
    let albumMap = new Map<string, [HTMLElement, AudioMetadata[]]>();
    /**
     * A map that ties each music button to the information about the songs it contains. See also `albumMap`
     */
    let albumElementsMap = new Map<HTMLElement, AudioMetadata[]>([]);
    /**
     * Add an element both to `albumMap` and `albumElementsMap`
     */
    function addToAlbumMap(item: HTMLElement, {name, metadata}: {name: string, metadata: AudioMetadata[]}) {
        albumMap.set(name, [item, metadata]);
        albumElementsMap.set(item, metadata);
        return {
            destroy: () => {
                albumMap.delete(name);
                albumElementsMap.delete(item);
            }
        }
    }

    /**
     * The album art and container of the `AlbumViewer` component, if it has been opened by the user.
     */
    let albumHtmlElements: [HTMLImageElement, HTMLElement] | undefined;

    let loadedItems = $state(30);

    onMount(() => {
        refreshContent();
        if (window.history.state?.section === "songs" && window.history.state?.sortingInfo) { // Restore previous state properties
            sortingInfo = window.history.state?.sortingInfo;
        } else window.history.pushState({section: "songs", sortingInfo: JSON.parse(JSON.stringify(sortingInfo))}, "");
        UploadFiles.refreshContent.add(refreshContent);

        function scrollEvent() {
            if (window.scrollY > (document.body.scrollHeight - window.innerHeight - 100)) loadedItems += 30;
        }

        async function popstateEvent(e: PopStateEvent) {
            if (e.state.section !== "songs") return;
            if (e.state.openedContent && e.state.contentType) { // An item has been opened, so we'll open it again
                const elements = albumMap.get(e.state.openedContent);
                if (elements) { 
                    // Scroll to the element so that the image can be displayed (without the image it isn't be possible to open the content)
                    window.scrollTo({top: window.scrollY + elements[0].getBoundingClientRect().top - 10, behavior: "smooth"});
                    while (!elements[0].querySelector("img")) await new Promise(res => setTimeout(res, 150));
                    clickedImage = [[e.state.openedContent, elements[1], elements[0].querySelector("img")?.src as string], elements[0].querySelector("img") as HTMLImageElement];
                }
            } else {
                if (typeof clickedImage !== "undefined" && typeof albumHtmlElements !== "undefined") { // Clear information about the opened item
                    albumHtmlElements[0].style.boxShadow = "";
                    await imageOpenTransition(albumHtmlElements[0], clickedImage[1], albumHtmlElements[1], true, true);
                }
                clickedImage = undefined;
            }
        }

        async function dropEvent(e: DragEvent) {
            e.preventDefault();
            if (e.dataTransfer?.items) {
                const files = await ReadDroppedFiles(Array.from(e.dataTransfer.items).map(i => i.webkitGetAsEntry()).filter(i => !!i));
                UploadFiles.uploadSkippingFilePicker({isFromAudio: true, files: files.map(i => i.file), relativePaths: files.map(i => i.path)});
            }
        }
        function dragoverEvent(e: DragEvent) {
            e.preventDefault();
            if (e.dataTransfer) {
                e.dataTransfer.dropEffect = "copy";
                e.dataTransfer.effectAllowed = "copy";
            }
        }
        window.addEventListener("popstate", popstateEvent);
        window.addEventListener("drop", dropEvent);
        window.addEventListener("dragover", dragoverEvent);
        window.addEventListener("scroll", scrollEvent);
        return () => {
            window.removeEventListener("popstate", popstateEvent);
            window.removeEventListener("drop", dropEvent);
            window.removeEventListener("dragover", dragoverEvent);
            window.removeEventListener("scroll", scrollEvent);
            UploadFiles.refreshContent.delete(refreshContent);
        }
    })

    /**
     * Information about the clicked image: [[album/artist name, selected tracks, album art URL], album art image element]
     */
    let clickedImage: [[string, AudioMetadata[], string], HTMLImageElement] | undefined = $state();

    /**
     * Get the URL for the album art
     * @param albumName name of the album/artist
     * @param albumContent music metadata of that album/artist
     */
    function getAlbumArtLink(albumName: string, albumContent: AudioMetadata[]) {
        return sortingInfo[0] === "album" || sortingInfo[0] === "track" ? `${StartPath}/api/getPreview?id=${encodeURIComponent(albumContent[0].id)}&albumId=${encodeURIComponent(albumContent[0].albumId ?? "")}&type=audio&token=${encodeURIComponent(token)}` : CreateAlbumArt(sortingInfo[0] === "track" ? (albumContent[0].title || albumContent[0].name) : albumName);
    }

    /**
     * Container of the buttons displayed in the top-right corner
     */
    let topBtnContainer: HTMLElement | undefined;

    /**
     * A list of all the selected music IDs
     */
    let selectedItems = new Set<number>([]);

    /**
     * If the music items should be selected instead of played
     */
    let isSelectModeEnabled = $state(false);
    /**
     * If all the music items between the previously-clicked item and the currently-clicked item should be selected
     */
    let isMassSelectionEnabled = $state(false);
    /**
     * The item that has been previously clicked
     */
    let prevItemSelected: HTMLElement | undefined;
    /**
     * The form element used to download files as a zip file
     */
    let downloadForm: HTMLFormElement;
    /**
     * If the settings are being displayed
     */
    let areSettingsOpened = $state(false);

    /**
     * Remove any reference of the selected items
     */
    function clearSelectedItems() {
        isMassSelectionEnabled = false;
        for (const [element] of albumElementsMap) element.classList.remove("selected");
        selectedItems.clear();
    }

    /**
     * Update the styling of all the music buttons by checking if they're selected or not
     */
    function checkSelectedItems() {
        for (const [element, items] of albumElementsMap) {
            element.classList[items.every(i => selectedItems.has(i.id)) ? "add" : "remove"]("selected");
        }
    }

    /**
     * Reduce the height of the buttons (the image is displayed at the left, information about the content at the right)
     */
    let enableCompactMode = $state(SettingsObject.audioViewer.compactMode);
    /**
     * If table mode should be displayed instead of grid mode
     */
    let disableGrid = $state(SettingsObject.audioViewer.disableGrid);    

    /**
     * Event called when the user clicks on a track/album/artist etc
     */
    function clickEvent(e: Event, albumName: string, albumContent: AudioMetadata[]) {
        let element = e.target as HTMLElement;
        if (element.tagName !== "BUTTON") element = element.closest("tr") as HTMLTableRowElement;
        if (isSelectModeEnabled) {
            if (isMassSelectionEnabled) {
                if (!prevItemSelected) {
                    prevItemSelected = element;
                    MassSelectionModeHelpers.updateElementStylingToPrevSelected(element);
                    return;
                }
                prevItemSelected.style.border = "";
                element.style.removeProperty("--border-size");
                // The mapArr property is the Object.entries of the directoryMap, sorted so that all the entries are in the order they're displayed in the DOM.
                // Note also that `currentElementIndex` is always after `prevItemIndex`.
                const {mapArr, currentElementIndex, prevItemIndex} = MassSelectionModeHelpers.getSelectedRange(element, albumElementsMap, prevItemSelected)
                if (prevItemIndex !== -1 && currentElementIndex !== -1) {
                    /**
                    * If all the elements between `prevItemIndex` and `currentElementIndex` should be selected. 
                    */
                    const selectAll = mapArr.slice(prevItemIndex, currentElementIndex + 1).some(i => (i[1] as AudioMetadata[]).some(i => !selectedItems.has(i.id)));
                    for (let i = prevItemIndex; i < currentElementIndex + 1; i++) {
                        mapArr[i][0].classList[selectAll ? "add" : "remove"]("selected");
                        for (const element of mapArr[i][1]) {
                            /**
                            * If the user has already selected this item
                            */
                            const isAdded = selectedItems.has(element.id);
                            if (selectAll && !isAdded) selectedItems.add(element.id);
                            if (!selectAll && isAdded) selectedItems.delete(element.id); 
                        }
                    }
                }
                prevItemSelected = undefined;
                if (sortingInfo[0] === "albumartist" || sortingInfo[0] === "artist") checkSelectedItems(); // Since we might have selected or deselected all the songs of other artists, we need to check again for all the elements in the DOM
                return;
            }
            // Mass selection mode isn't enabled: just select or deselect the current element
            const shouldElementBeAdded = albumContent.some(i => !selectedItems.has(i.id));
            element.classList[shouldElementBeAdded ? "add" : "remove"]("selected");
            for (const element of albumContent) selectedItems[shouldElementBeAdded ? "add" : "delete"](element.id);
            if (sortingInfo[0] === "albumartist" || sortingInfo[0] === "artist") checkSelectedItems(); // Same as before
            return;
        }
        if (sortingInfo[0] === "track") {
            const entries = sortedAudioFiles.map(i => i[1]).flat();
            entries.push(...entries.splice(0, entries.findIndex(i => i.id === albumContent[0].id)));
            PlayAudio.playAudio({info: albumContent[0], token, queue: entries});
            return;
        }
        const img = element.querySelector("img");
        if (img) clickedImage = [[albumName, albumContent, img.src], img];
    }

    /**
     * Value used only when grid mode is disabled (so, table mode is enabled). It contains [the AudioMetadata key used to sort the items in the array, if the list is reversed or not]
     */
    let tableSelection = $derived([sortingInfo[0] === "track" ? "cdTrack" : sortingInfo[0] === "albumartist" ? "albumArtist" : sortingInfo[0], sortingInfo[1]]) as [string, boolean];
    /**
     * A list of all the keys of the AudioMetadata object that are a number instead of a string.
     */
    const numProperties = ["id", "dateModified", "dateAdded", "bitrate", "cdTrack", "discNumber", "numTracks", "track", "year", "duration"];
    /**
     * Update this value to force re-render the table
     */
    let forceTableRerender = $state(Date.now());
    $effect(() => { // Effect that runs every time the table must be sorted
        function registerComponents(..._: any) {};
        registerComponents(tableSelection);
        if (tableSelection[0] === "" || !disableGrid) return;
        sortedAudioFiles.sort((a, b) => {
            if (tableSelection[1]) [a, b] = [b, a];
            if (tableSelection[0] === "duration") {
                return a[1].reduce((a, b) => a + (b.duration ?? 0), 0) - b[1].reduce((a, b) => a + (b.duration ?? 0), 0);
            }
            if (numProperties.indexOf(tableSelection[0]) !== -1) {
                return (a[1][0][tableSelection[0] as "track"] ?? 0) - (b[1][0][tableSelection[0] as "track"] ?? 0);
            }
            return (a[1][0][tableSelection[0] as "album"] ?? (tableSelection[0] === "track" ? a[1][0].name : null) ?? lang("Unknown")).localeCompare(b[1][0][tableSelection[0] as "album"] ?? (tableSelection[0] === "track" ? a[1][0].name : null) ?? lang("Unknown"));
        })
        forceTableRerender = Date.now();
    });

    /**
     * The table where the song metadata is shown
     */
    let table: HTMLTableElement;

</script>

{#snippet tableHeaderCell(title: string, propToChange: keyof AudioMetadata)}
<th style="resize: horizontal; overflow: auto" class="hover" onclick={() => {
    tableSelection = [propToChange, tableSelection[0] === propToChange ? !tableSelection[1] : tableSelection[1]];
}}>
    <div class="flex hcenter gap">
        {#if tableSelection[0] === propToChange}
            <img class="icon" use:RegenerateIcons.register={{icon: tableSelection[1] ? "arrowup" : "arrowdown", type: getComputedStyle(document.body).getPropertyValue("--accenttext")}} alt={lang("Sorted by this column")}>
        {/if}
        {title}
    </div>
</th>
{/snippet}

<DropdownMenu dropdownInfo={[{
    title: lang("Upload files"),
    icon: "arrowupload",
    id: "upload",
    extra: [{
        title: lang("Pick multiple files"),
        icon: "documentadd",
        id: "uploadfiles",
        extra: []
    }, {
        title: lang("Pick a folder"),
        icon: "folderadd",
        id: "uploadfolder",
        extra: []
    }],
}, {
    title: lang("Refresh content"),
    id: "refresh",
    extra: [],
    icon: "arrowsync"
}, {
    title: lang("Reverse list"),
    id: "reverse",
    extra: [],
    icon: "arrowsort" as "arrowsort",
    customImgStyling: "transform: scaleY(-1)",
    await: true
}, {
    title: lang("Change buttons height"),
    id: "zoom",
    icon: "autofitheight" as "autofitheight",
    extra: [...(enableCompactMode ? [] : [{
        title: lang("Change album art size"),
        icon: "imageresize" as "imageresize",
        id: "albumArtSize",
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
    }],
}]), ...(disableGrid ? [{
    title: lang("Change table size"),
    icon: "tableresize" as "tableresize",
    id: "tableSize",
    extra: [{
        title: lang("Increase table width"),
        icon: "zoomin" as "zoomin",
        id: "zoomintable",
        extra: [],
        keepOpen: true
    }, {
        title: lang("Decrease table width"),
        icon: "zoomout" as "zoomout",
        id: "zoomouttable",
        extra: [],
        keepOpen: true
    }, {
        title: lang("Go back"),
        icon: "dismiss" as "dismiss",
        id: "zoomback",
        extra: []
    }]
}] : [])]
    
}, {
    title: lang("Select files"),
    id: "select",
    extra: [],
    await: true,
    icon: "selectobj"
}, {
    title: lang("Settings"),
    id: "settings",
    extra: [],
    await: true,
    icon: "settings"
}]} callback={(id) => {
    switch(id) {
        case "uploadfiles":
            UploadFiles.uploadFiles({isFromAudio: true});
            break;
        case "uploadfolder": 
            UploadFiles.uploadFiles({isFromAudio: true, directory: true});
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
        case "zoomintable": 
            table.style.width = `${table.scrollWidth + SettingsObject.photoViewer.zoomChange}px`;
            break;
        case "zoomouttable":
            table.style.width = `${table.scrollWidth - SettingsObject.photoViewer.zoomChange}px`;
            break;
        case "settings":
            areSettingsOpened = true;
            break;
        case "reverse":
            sortingInfo[1] = !sortingInfo[1];
            break;
    }
}} getContainer={(container) => (topBtnContainer = container)}></DropdownMenu>


{#if availableAudioFiles.length === 0}
    <p>{lang("Fetching audio files")}...</p>
{:else}
    <Card>
        <h3 class="flex hcenter gap"><span style="white-space: nowrap;">{lang(`Your${sortingInfo[0] === "track" ? " F" : ""}`)}</span> <select style="background-color: var(--secondcard);" bind:value={sortingInfo[0]}>
            <option value="album">{lang("albums")}</option>
            <option value="artist">{lang("artists")}</option>
            <option value="albumartist">{lang("album artists")}</option>
            <option value="track">{lang("tracks")}</option>
        </select></h3>
        {#if disableGrid}
            {#key forceTableRerender}
            <div style="overflow: auto; max-width: 100%;">
            <table bind:this={table}>
                <thead>
                    <tr>
                        <th style="overflow: auto" class="hover">{lang("Image")}</th>
                        {@render tableHeaderCell("Title", sortingInfo[0] === "track" ? "title" : sortingInfo[0] === "albumartist" ? "albumArtist" : (sortingInfo[0] as "album"))}
                        {#if sortingInfo[0] === "album" || sortingInfo[0] === "track"}
                            {#if SettingsObject.audioViewer.gridMetadata.album && sortingInfo[0] !== "album"}
                            {@render tableHeaderCell(lang("Album"), "album")}
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.artist && sortingInfo[0] !== "album"}
                            {@render tableHeaderCell(lang("Artists"), "artist")}
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.albumArtist}
                            {@render tableHeaderCell(lang("Album artists"), "albumArtist")}
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.composer && sortingInfo[0] !== "album"}
                            {@render tableHeaderCell(lang("Composer"), "composer")}
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.trackNum && sortingInfo[0] !== "album"}
                            {@render tableHeaderCell(lang("Track"), "cdTrack")}
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.genre}
                            {@render tableHeaderCell(lang("Genre"), "genre")}
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.disc && sortingInfo[0] !== "album"}
                            {@render tableHeaderCell(lang("Disc"), "discNumber")}
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.year}
                            {@render tableHeaderCell(lang("Year"), "year")}
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.duration}
                            {@render tableHeaderCell(lang("Duration"), "duration")}
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.bitrate && sortingInfo[0] !== "album"}
                            {@render tableHeaderCell(lang("Bitrate"), "bitrate")}
                            {/if}
                        {/if}
                    </tr>
                </thead>
                <tbody>
                    {#each sortedAudioFiles as [albumName, albumContent], i (albumName)}
                    {#if i < loadedItems}
                    <tr use:addToAlbumMap={{name: albumName, metadata: albumContent}} onclick={(e) => clickEvent(e, albumName, albumContent)} class="hover">
                        <td style="overflow-wrap: anywhere;">
                            <ImageIntersectionViewer customErrorEvent={(e) => {
                                (e.target as HTMLImageElement).src = CreateAlbumArt(sortingInfo[0] === "track" ? (albumContent[0].title || albumContent[0].name) : albumName) ;
                                }} name={`${lang("Thumbnail of")} ${albumName}`} suggestedProportion={1} imagePreviewUrl={getAlbumArtLink(albumName, albumContent)}></ImageIntersectionViewer>
                        </td>
                        <td style="overflow-wrap: anywhere;">{sortingInfo[0] === "track" ? (albumContent[0].title || albumContent[0].name) : albumName}</td>
                        {#if sortingInfo[0] === "album" || sortingInfo[0] === "track"}
                            {#if SettingsObject.audioViewer.gridMetadata.album && sortingInfo[0] !== "album"}
                            <td style="overflow-wrap: anywhere;">{albumContent[0].album ?? lang("Unknown")}</td>
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.artist && sortingInfo[0] !== "album"}
                            <td style="overflow-wrap: anywhere;">{albumContent[0].artist ?? lang("Unknown")}</td>
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.albumArtist}
                            <td style="overflow-wrap: anywhere;">{albumContent[0].albumArtist ?? lang("Unknown")}</td>
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.composer && sortingInfo[0] !== "album"}
                            <td style="overflow-wrap: anywhere;">{albumContent[0].composer ?? lang("Unknown")}</td>
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.trackNum && sortingInfo[0] !== "album"}
                            <td style="overflow-wrap: anywhere;">{albumContent[0].cdTrack ?? 1}</td>
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.genre}
                            <td style="overflow-wrap: anywhere;">{albumContent[0].genre ?? lang("Unknown")}</td>
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.disc && sortingInfo[0] !== "album"}
                            <td style="overflow-wrap: anywhere;">{albumContent[0].discNumber ?? lang("Unknown")}</td>
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.year}
                            <td style="overflow-wrap: anywhere;">{albumContent[0].year ?? lang("Unknown")}</td>
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.duration}
                            <td style="overflow-wrap: anywhere;">{convertNumberToStr(albumContent.reduce((a, b) => a + (b.duration ?? 0), 0) / 1000)}</td>
                            {/if}
                            {#if SettingsObject.audioViewer.gridMetadata.bitrate && sortingInfo[0] !== "album"}
                            <td style="overflow-wrap: anywhere;">{((albumContent[0].bitrate ?? 0) / 1024).toFixed(2)} kbit/s</td>
                            {/if}
                        {/if}
                    </tr>
                    {/if}
                    {/each}
                </tbody>
            </table>
            </div>
            {/key}
        {:else}
        <div class={`flex hcenter gap wrap`}>
            {#each sortedAudioFiles as [albumName, albumContent], i (albumName)}
            {#if i < loadedItems}
            <button class={`emptyBtn ${albumContent.some(i => !selectedItems.has(i.id)) ? "" : " selected"}`} use:addToAlbumMap={{name: albumName, metadata: albumContent}} onclick={(e) => clickEvent(e, albumName, albumContent)}>
                <div style="pointer-events: none; height: 100%">
                    <Card isSecondCard={true} applyMaxHeight={true}>
                        <div class={enableCompactMode ? "flex hcenter gap" : ""}>
                            <div style={`margin-top: 10px;${enableCompactMode ? " --picture-height: 100px;" : ""}`}>
                                <ImageIntersectionViewer customErrorEvent={(e) => {
                                    (e.target as HTMLImageElement).src = CreateAlbumArt(sortingInfo[0] === "track" ? (albumContent[0].title || albumContent[0].name) : albumName) ;
                                }} name={`${lang("Thumbnail of")} ${albumName}`} suggestedProportion={1} imagePreviewUrl={getAlbumArtLink(albumName, albumContent)}></ImageIntersectionViewer>
                            </div>
                            <p style="margin-bottom: 10px; pointer-events: none; width: 100%">
                                <span style="overflow-wrap: anywhere">{sortingInfo[0] === "track" ? (albumContent[0].title || albumContent[0].name) : albumName}</span>
                                {#if sortingInfo[0] === "album"}
                                    <br>
                                    <span style="color: var(--secondtext); overflow-wrap: anywhere">{(albumContent[0].albumArtist ?? lang("Unknown"))}</span>
                                {/if}
                            </p>
                        </div>
                    </Card>
                </div>
            </button>
            {/if}
            {/each}
        </div>
        {/if}
        {#if loadedItems < sortedAudioFiles.length}
        <button onclick={() => (loadedItems += 30)}>{lang("Load more items")}</button>
        {/if}
    </Card>
{/if}

{#if clickedImage}
    <AlbumViewer type={sortingInfo[0] as "album"} src={clickedImage[0][2]} {token} songs={clickedImage[0][1]} name={clickedImage[0][0]} viewCallback={async (image, main) => {
        albumHtmlElements = [image, main];
        if (!clickedImage) return;
        await imageOpenTransition(clickedImage[1], image, main, undefined, true);
    }}></AlbumViewer>
{/if}

<form style="display: none;" target="_blank" method="POST" action={`${StartPath}/api/zip?token=${encodeURIComponent(token)}`} bind:this={downloadForm}>
    <input name="type" value="media">
    <input name="ids">
    <input name="contentType">
    <input name="names">
</form>


{#if isSelectModeEnabled}
<SelectComponent changeMassSelectionCallback={(e) => {isMassSelectionEnabled = e;}} {isMassSelectionEnabled} downloadCallback={async () => {
    // Let's get all the names of the selected files
    const names: string[] = [];
    const items = Array.from(selectedItems);
    for (const element of availableAudioFiles) {
        const index = items.findIndex(i => i === element.id);
        if (index !== -1) names[index] = element.name;
    }
    if (typeof window.showDirectoryPicker === "function") { // Save the files using the File System API instead of downloading a zip file
        try {
            if (!SettingsObject.downloads.useFsApi) throw new Error("The user doesn't want to use the File System API");
            const picker = await window.showDirectoryPicker({id: "Music", mode: "readwrite", startIn: "music"});
            let index = -1;
            const writtenFileNames = [] as string[];
            async function nextItem() { // As a separate function instead of a for loop so that multiple files can be downloaded at the same time
                index++;
                const i = index;
                if (index >= items.length) return;
                let outputName = names[i];
                if (!SettingsObject.downloads.replaceFiles) { // Check if a file in the same directory exists
                    const availableFiles: string[] = [] 
                    for await (const file of picker.values()) availableFiles.push(file.name);
                    let tempFileName = outputName;
                    let j = 1;
                    while (availableFiles.indexOf(outputName) !== -1) {
                        outputName = `${tempFileName.substring(0, tempFileName.lastIndexOf("."))} (${j})${tempFileName.substring(tempFileName.lastIndexOf("."))}`;
                        j++;
                    }
                } else { // We need to make sure multiple tracks don't have the same name
                    let j = 1;
                    while (writtenFileNames.indexOf(outputName) !== -1) {
                        outputName = `${names[i].substring(0, names[i].lastIndexOf("."))} (${j})${names[i].substring(names[i].lastIndexOf("."))}`;
                        j++;
                    }
                }
                writtenFileNames.push(outputName);
                const file = await picker.getFileHandle(outputName, {create: true});
                await FileSystemApiHelper.pipeContent(`${StartPath}/api/download?id=${items[i]}&mimetype=${encodeURIComponent("audio/mpeg")}&name=${encodeURIComponent(names[i])}&token=${encodeURIComponent(token)}`, names[i], file);
                nextItem();
            }
            for (let i = 0; i < SettingsObject.downloads.concurrentDownloads; i++) nextItem();
            return;
        } catch(ex) {
            console.warn(ex)
        }
    }
    // And now let's populate the form
    (downloadForm.querySelector("[name=ids]") as HTMLInputElement).value = JSON.stringify(Array.from(selectedItems));
    (downloadForm.querySelector("[name=contentType]") as HTMLInputElement).value = JSON.stringify(names.map(i => 2));
    (downloadForm.querySelector("[name=names]") as HTMLInputElement).value = JSON.stringify(names);
    downloadForm.submit();
}} deleteCallback={async (e) => {
    if (confirm(lang("Do you want to delete all the selected files? This action can't be undone."))) {
        ((e.target as HTMLImageElement).parentElement as HTMLButtonElement).disabled = true;
        let items = Array.from(selectedItems);
        for (let i = 0; i < items.length; i++) {
            const req = await fetch(`${StartPath}/api/delete?id=${encodeURIComponent(items[i])}&type=audio`, {
                headers: {
                    Authorization: `Bearer ${token}`
                }
            });
            if (req.ok) {
                selectedItems.delete(items[i]);
                const index = availableAudioFiles.findIndex(j => (j.id === items[i]));
                if (index !== -1) availableAudioFiles.splice(index, 1);
            }
        }
        ((e.target as HTMLImageElement).parentElement as HTMLButtonElement).disabled = false;
    }
}} closeCallback={async () => {
    clearSelectedItems();
    if (topBtnContainer) topBtnContainerTransition(topBtnContainer, true).then(() => topBtnContainer && topBtnContainerTransition(topBtnContainer))
    isSelectModeEnabled = false;
}}></SelectComponent>
{/if}

{#if areSettingsOpened}
    <Settings callback={() => (areSettingsOpened = false)} callbacks={{
        divideSongsAgain: () => (sortedAudioFiles = sortAudioFiles(availableAudioFiles)),
        changeAudioPlayerCompactMode: () => (enableCompactMode = SettingsObject.audioViewer.compactMode),
        changeAudioPlayerGridMode: () => (disableGrid = SettingsObject.audioViewer.disableGrid),
        rerenderAudioPlayerTable: () => (forceTableRerender = Date.now())
    }}></Settings>
{/if}

<style>
    .wrap {
        flex-wrap: wrap;
        align-items: stretch;
    }
    .wrap > * {
        flex: 1 0 calc(var(--picture-height) + 30px);
    }
    .column {
        flex-direction: column;
    }
    .column > * {
        width: 100%;
    }
</style>
