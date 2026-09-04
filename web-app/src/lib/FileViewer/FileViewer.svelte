<script lang="ts">
    import { onMount } from "svelte";
    import type { AvailableDirectories, Directory, DirectoryWrapper } from "../../ts/Interfaces/API";
    import Card from "../SmallComponents/Card.svelte";
    import { getIconSrc } from "../../ts/IconManager";
    import getImageToUse from "../../ts/GetFileImage";
    import { slide } from "svelte/transition";
    import { cubicInOut } from "svelte/easing";
    import topBtnContainerTransition from "../../ts/TopButtonsTransition";
    import SelectComponent from "../SmallComponents/SelectComponent.svelte";
    import MassSelectionModeHelpers from "../../ts/MassSelectionModeHelpers";
    import UploadFiles from "../../ts/UploadFiles";
    import SettingsObject from "../../ts/Settings"
    import DropdownMenu from "../SmallComponents/DropdownMenu.svelte";
    import Settings from "../Settings.svelte";
    import ImageIntersectionViewer from "../PhotoViewer/ImageIntersectionViewer.svelte";
    import ReadDroppedFiles from "../../ts/ReadDroppedFiles";
    import StartPath from "../../ts/StartPath";
    import lang from "../../ts/Lang";
    import FileSystemApiHelper from "../../ts/FileSystemApiHelper";
    import RegenerateIcons from "../../ts/RegenerateIcons";

    const {token}: {token: string} = $props();

    /**
     * All the files that are available in the selected directory
     */
    let currentFiles: Directory[] | undefined = $state();
    /**
     * An array, composed of [the selected path (or "" if no folder has been selected), and if the provided path is a Content URI or not]
     */
    let currentPath: [string, boolean] = $state(["", false]);
    /**
     * If no file should be uploaded. This is set to true only if the application doesn't have write permission.
     */
    let disableUpload = $state(false);
    /**
     * List of all the paths or content URIs that the user has selected. 
     * 
     * **Note:** use the `ifSelectedItemsIsContentUri` array to get if the added element is a content uri or a normal path
     */
    let selectedItems: string[] = [];
    /**
     * Array tied with `selectedItems`: for each position `i`, this array tells if `selectedItems[i]` is a content uri or not.
     */
    let ifSelectedItemsIsContentUri: number[] = [];
    /**
     * If the file should be selected instead of downloaded
     */
    let selectModeEnabled = $state(false);
    /**
     * If the settings dialog should be displayed
     */
    let showSettings = $state(false);
    /**
     * If "mass selection mode" is enabled, so if all the items between the previously-clicked item and the clicked item should be selected
     */
    let isMassSelectionEnabled = $state(false);
    /**
     * A map that ties each file button to its directory info
     */
    let directoryMap: Map<HTMLElement, Directory> = new Map();
    /**
     * The file button that has been previously clicked. Stored only if "mass selection mode" is enabled
     */
    let prevSelectedItem: HTMLElement | undefined;
    /**
     * Container of the top-right action buttons
     */
    let topBtnContainer: HTMLElement | undefined;
    /**
     * The form element that is used to send POST requests to download a file
     */
    let downloadForm: HTMLFormElement;
    /**
     * Information about sorting file: [the criteria used (ex: last modified date, name etc.), if the sorting is reversed or not]
     */
    let sortingInfo: [string, boolean] = $state([SettingsObject.fileViewer.sortCriteria, SettingsObject.fileViewer.reverse]);
    /**
     * If a table should be shown instead of a grid
     */
    let showTable = $state(SettingsObject.fileViewer.useTableView); 
    /**
     * Get the parameters that should be compared. This permits to reverse the list directly from the sort function.
     * @param a first element for the sort function
     * @param b second element for the sort function
     * @param invert if `b` should be the source instead of `a`
     */
    function getRealParams(a: Directory, b: Directory, invert: boolean) {
        if (invert) return {source: b, dest: a};
        return {source: a, dest: b}
    } 

    /**
     * Get the mimetype string to display
     * @param source information about the file
     */
    function getMimetypeString(source: Directory) {
        return source.isDirectory ? lang("Folder") : (source.mimeType ?? (source.path.indexOf(".") !== -1 ? `${source.path.substring(source.path.lastIndexOf(".") + 1)} file` : ""));
    }
    $effect(() => { // Effect that runs every time sortingInfo changes
        if (currentFiles) {
            switch(sortingInfo[0]) {
                case "name":
                    currentFiles.sort((a, b) => {
                        const {source, dest} = getRealParams(a, b, sortingInfo[1]);
                        return source.path.localeCompare(dest.path)
                    });
                    break;
                case "size":
                    currentFiles.sort((a, b) => {
                        const {source, dest} = getRealParams(a, b, sortingInfo[1]);
                        if (source.isDirectory && !source.size && !dest.isDirectory) return sortingInfo[1] ? 1 : -1;
                        if (dest.isDirectory && !dest.size && !source.isDirectory) return sortingInfo[1] ? -1 : 1;
                        if (source.isDirectory && dest.isDirectory && !source.size && !dest.size) return source.path.localeCompare(dest.path) * (sortingInfo[1] ? -1 : 1);
                        return dest.size - source.size;
                    });
                    break;
                case "mimetype":
                    currentFiles.sort((a, b) => {
                        const {source, dest} = getRealParams(a, b, sortingInfo[1]);
                        return getMimetypeString(source).localeCompare(getMimetypeString(dest));
                    })
                    break;
                case "lastModified":
                    currentFiles.sort((a, b) => {
                        const {source, dest} = getRealParams(a, b, sortingInfo[1]);
                        return (dest.lastModified ?? 0) - (source.lastModified ?? 0)
                    });
                    break;
            }
        }
    });
    /**
     * Add an element to the `directoryMap`, so the Map that ties the file button to its information
     * @param element the button element
     * @param directory information about the file
     */
    function addToDirectoryMap(element: HTMLElement, directory: Directory) {
        directoryMap.set(element, directory);
        return {
            destroy: () => {
                directoryMap.delete(element);
            }
        }
    }

    /**
     * Remove any reference to the selected items
     */
    function clearSelectedItems() {
        isMassSelectionEnabled = false;
        for (const [element] of directoryMap) element.classList.remove("selected");
        selectedItems = [];
        ifSelectedItemsIsContentUri = [];
    }

    /**
     * Custom value that forces the re-rendering of the file table/grid
     */
    let rerenderData = $state(Date.now());

    /**
     * Get all the files in a directory
     * @param path the path or content uri of the folder to read. If not passed, the application will fetch the available disk drives instead
     * @param isContentUri if the `path` property is a Content URI instaed of a normal path
     */
    async function getDirectory(path?: string, isContentUri?: boolean) {
        document.body.append(spinner)
        if (typeof path === "undefined") { // No path has been provided. Let's get the disk drives connected to the device.
            const sourcesReq = await fetch(`${StartPath}/api/getAvailableDirs`, {
                headers: {
                    Authorization: `Bearer ${token}`
                }
            });
            if (sourcesReq.ok) {
                const sourceJson = await sourcesReq.json() as AvailableDirectories;
                spinner.remove();
                if (sourceJson.directories.length !== 0) return [...sourceJson.directories.map(i => {return {isDirectory: true, path: i.endsWith("/") ? i.substring(0, i.length - 1) : i}}), ...sourceJson.availableContext.map(i => {return {isDirectory: true, path: decodeURIComponent(i.substring(i.indexOf("tree/") + 5)), fileContentUri: i}})] as Directory[];
            }
            spinner.remove();
            return [{isDirectory: true, path: "/storage/emulated/0"}] as Directory[]; // Fallback if no disk drives can be found from the `getAvailableDirs` endpoint
        }
        const req = await fetch(`${StartPath}/api/dir?path=${encodeURIComponent(path)}&isContentUri=${isContentUri ? "1" : "0"}`, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });
        if (req.ok) {
            spinner.remove();
            const json = await req.json() as DirectoryWrapper;
            disableUpload = !json.canWrite;
            return json.files;
        }
        spinner.remove();
        return [];
    }

    /**
     * Convert the number of bytes to a readable string
     * @param num the number of bytes
     */
    function getFileSize(num: number) {
        let size = "bytes";
        while (Math.floor(num) > 1024) {
            num /= 1024;
            size = size === "bytes" ? "kilobytes" : size === "kilobytes" ? "megabytes" : size === "megabytes" ? "gigabytes" : "terabytes";
            if (size === "terabytes") break;
        }
        return `${num.toFixed(2)} ${Math.floor(num) === 1 ? size.substring(0, size.length - 1) : size}`;
    }

    /**
     * Get the string that contains the information about the current file
     * @param dir information about the current file
     */
    function getFileInfoStr(dir: Directory) {
        const output: string[] = [];
        if (SettingsObject.fileViewer.showContent.lastModified && typeof dir.lastModified !== "undefined") output.push(`${lang("Last modified")}: ${new Date(dir.lastModified).toLocaleString()}`);
        if (SettingsObject.fileViewer.showContent.mimetype && typeof dir.mimeType !== "undefined") output.push(`Mimetype: ${dir.mimeType}`);
        if (SettingsObject.fileViewer.showContent.size && typeof dir.size !== "undefined") {
            output.push(`${lang("Size")}: ${getFileSize(dir.size)}`);
        }
        return output;
    }
    /**
     * The spinner element that is displayed when items are being fetched from the user's device
     */
    let spinner = Object.assign(document.createElement("div"), {className: "spinner"});

    $effect(() => { // Automatically fetch the files in the folder when the currentPath proprety is updated
        getDirectory(currentPath[0] === "" ? undefined : currentPath[0], currentPath[1]).then((result) => {
            currentFiles = result;
            loadedItems = 30;
            window.scrollTo({top: 0, behavior: "smooth"});
        })
    })

    let loadedItems = $state(30);

    onMount(() => {
        if (window.history.state?.section === "files" && window.history.state.path) {
            currentPath = window.history.state.path;
        } else window.history.pushState({section: "files", path: ["", false]}, "");
        function popstateEvent(e: PopStateEvent) {
            if (e.state.section !== "files") return;
            currentPath = e.state.path;
        }
        window.addEventListener("popstate", popstateEvent);
        
        function scrollEvent() {
            if (window.scrollY > (document.body.scrollHeight - window.innerHeight - 100)) loadedItems += 30;
        }

        async function refreshFiles() {
            const result = await getDirectory(currentPath[0] === "" ? undefined : currentPath[0], currentPath[1]);
            currentFiles = result;
        }
        UploadFiles.refreshContent.add(refreshFiles);

        async function dropEvent(e: DragEvent) {
            e.preventDefault();
            if (disableUpload || currentPath[0] === "") return;
            if (e.dataTransfer?.items) {
                const files = await ReadDroppedFiles(Array.from(e.dataTransfer.items).map(i => i.webkitGetAsEntry()).filter(i => !!i));
                UploadFiles.uploadSkippingFilePicker({contentUri: currentPath[1] ? currentPath[0] : undefined, path: currentPath[1] ? undefined : currentPath[0], files: files.map(i => i.file), relativePaths: files.map(i => i.path)});
            }
        }
        function dragoverEvent(e: DragEvent) {
            e.preventDefault();
            if (disableUpload || currentPath[0] === "") return;
            if (e.dataTransfer) {
                e.dataTransfer.dropEffect = "copy";
                e.dataTransfer.effectAllowed = "copy";
            }
        }
        window.addEventListener("drop", dropEvent);
        window.addEventListener("dragover", dragoverEvent);
        window.addEventListener("scroll", scrollEvent);
        return () => {
            UploadFiles.refreshContent.delete(refreshFiles);
            window.removeEventListener("popstate", popstateEvent);
            window.removeEventListener("drop", dropEvent);
            window.removeEventListener("dragover", dragoverEvent)
            window.removeEventListener("scroll", scrollEvent);
        }
    })
    
    /**
     * The "click" event of a file button
     * @param e the click event
     * @param file information about the clicked file
     */
    async function fileButtonEvent(e: Event, file: Directory) {
        let element = e.target as HTMLElement;
        if (element.tagName === "TD" || element.parentElement?.tagName === "TD") element = element.closest("tr") as HTMLElement; // Get the table row if the grid is being shown
        if (selectModeEnabled) {                    
            if (isMassSelectionEnabled) {
                if (!prevSelectedItem) {
                    prevSelectedItem = element;
                    MassSelectionModeHelpers.updateElementStylingToPrevSelected(element);
                    return;
                }
                prevSelectedItem.style.border = "";
                element.style.removeProperty("--border-size");
                // The mapArr property is the Object.entries of the directoryMap, sorted so that all the entries are in the order they're displayed in the DOM.
                // Note also that `currentElementIndex` is always after `prevItemIndex`.
                const { mapArr, currentElementIndex, prevItemIndex } = MassSelectionModeHelpers.getSelectedRange(element as HTMLElement, directoryMap, prevSelectedItem);
                if (currentElementIndex !== -1 && prevItemIndex !== -1) {
                    /**
                     * If all the elements between `prevItemIndex` and `currentElementIndex` should be selected. 
                     */
                    const selectAll = !mapArr.slice(prevItemIndex, currentElementIndex).every(i => selectedItems.includes((i[1] as Directory).fileContentUri || `${currentPath[0]}${(i[1] as Directory).path}`));
                    for (let i = prevItemIndex; i <= currentElementIndex; i++) {
                        /**
                         * If the user has already selected this item
                         */
                        const isItemSelected = selectedItems.indexOf((mapArr[i][1] as Directory).fileContentUri || `${currentPath[0]}${(mapArr[i][1] as Directory).path}`);
                        if (selectAll && isItemSelected === -1) { // The item should be added to the list
                            selectedItems.push((mapArr[i][1] as Directory).fileContentUri || `${currentPath[0]}${(mapArr[i][1] as Directory).path}`);
                            ifSelectedItemsIsContentUri.push((mapArr[i][1] as Directory).fileContentUri && (mapArr[i][1] as Directory).isDirectory ? 1 : (mapArr[i][1] as Directory).fileContentUri ? 0 : -1);
                            mapArr[i][0].classList.add("selected");
                        }
                        if (!selectAll && isItemSelected !== -1) { // The item should be removed from the list
                            selectedItems.splice(isItemSelected, 1);
                            ifSelectedItemsIsContentUri.splice(isItemSelected, 1);
                            mapArr[i][0].classList.remove("selected");
                        }
                    }
                }
                prevSelectedItem = undefined;
                return;
            }
            // Mass selection mode isn't enabled: just select or deselect the current element
            const isItemSelected = selectedItems.indexOf(file.fileContentUri || `${currentPath[0]}${file.path}`);
            element.classList[isItemSelected === -1 ? "add" : "remove"]("selected");
            isItemSelected === -1 ? selectedItems.push(file.fileContentUri || `${currentPath[0]}${file.path}`) : selectedItems.splice(isItemSelected, 1);
            isItemSelected === -1 ? ifSelectedItemsIsContentUri.push(file.fileContentUri && file.isDirectory ? 1 : file.fileContentUri ? 0 : -1) : ifSelectedItemsIsContentUri.splice(isItemSelected, 1);
            return;
        }
        if (file.isDirectory) { // Get the elements inside that directory
            currentPath = [file.fileContentUri || `${currentPath[0]}${file.path}${file.path.endsWith("/") ? "" : "/"}`, !!file.fileContentUri];
            window.history.pushState({section: "files", path: JSON.parse(JSON.stringify(currentPath))}, "");
            return;
        }
        window.open(`${StartPath}/api/download?path=${encodeURIComponent(file.fileContentUri || `${currentPath[0]}/${file.path}`)}&token=${encodeURIComponent(token)}&isContentUri=${file.fileContentUri ? "1" : "0"}`, "_blank");
    }

    let table: HTMLTableElement;

    const tableResizeBtn = {
    title: lang("Change table size"),
    icon: "tableresize" as "tableresize",
    id: "tableresize",
    extra: [{
        title: lang("Increase table width"),
        icon: "zoomin" as "zoomin",
        id: "zoomin",
        extra: [],
        keepOpen: true
    }, {
        title: lang("Decrease table width"),
        icon: "zoomout" as "zoomout",
        id: "zoomout",
        extra: [],
        keepOpen: true
    }]
};
</script>

<DropdownMenu dropdownInfo={[...(currentPath[0] === "" ? [tableResizeBtn] : [{
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
    title: lang("Create folder"),
    icon: "folderadd" as "folderadd",
    id: "createfolder",
    extra: []
}, {
    title: lang("Sort files"),
    icon: "arrowsort" as "arrowsort",
    id: "sortFiles",
    extra: [{
        title: lang("By last modified date"),
        id: "sortLastModified",
        icon: "calendardate" as "calendardate",
        extra: []
    }, {
        title: lang("By size"),
        id: "sortBySize",
        icon: "harddrive" as "harddrive",
        extra: []
    }, {
        title: lang("By name"),
        id: "sortByName",
        icon: "textsortascending" as "textsortascending",
        extra: []
    }, {
        title: lang("Reverse order"),
        id: "sortReverse",
        icon: "arrowsort" as "arrowsort",
        customImgStyling: "transform: scaleY(-1)",
        extra: []
    }]
}, ...(showTable ? [tableResizeBtn] : []), {
    title: lang("Select files"),
    id: "select",
    extra: [],
    await: true,
    icon: "selectobj" as "selectobj"
}]), {
    title: lang("Settings"),
    id: "settings",
    extra: [],
    await: true,
    icon: "settings"
}]} callback={(id) => {
    switch(id) {
        case "uploadfiles":
            UploadFiles.uploadFiles({contentUri: currentPath[1] ? currentPath[0] : undefined, path: currentPath[1] ? undefined : currentPath[0]});
            break;
        case "uploadfolder": 
            UploadFiles.uploadFiles({directory: true, contentUri: currentPath[1] ? currentPath[0] : undefined, path: currentPath[1] ? undefined : currentPath[0] });
            break;
        case "select":
            selectModeEnabled = !selectModeEnabled;
            break;
        case "sortLastModified":
            sortingInfo[0] = "lastModified"
            break;
        case "sortBySize":
            sortingInfo[0] = "size";
            break;
        case "sortByName":
            sortingInfo[0] = "name";
            break;
        case "sortByMimetype":
            sortingInfo[0] = "mimetype";
            break;
        case "sortReverse":
            sortingInfo[1] = !sortingInfo[1]
            break;
        case "settings":
            showSettings = true;
            break;
        case "zoomin": 
            table.style.width = `${table.scrollWidth + SettingsObject.photoViewer.zoomChange}px`;
            break;
        case "zoomout":
            table.style.width = `${table.scrollWidth - SettingsObject.photoViewer.zoomChange}px`;
            break;
        case "createfolder": {
            const name = prompt(lang("How do you want to name the new folder?"))
            if (name != null) {
                fetch(`${StartPath}/api/newfolder?path=${encodeURIComponent(currentPath[1] ? name : `${currentPath[0]}${currentPath[0].endsWith("/") ? "" : "/"}${name}`)}${currentPath[1] ? `&sourceContentUri=${encodeURIComponent(currentPath[0])}` : ""}`, {headers: {Authorization: `Bearer ${token}`}}).then(() => {
                    for (const fn of UploadFiles.refreshContent) fn();
                })
            }
            break;
        }


    }
}} getContainer={(container) => (topBtnContainer = container)}></DropdownMenu>

{#if typeof currentFiles === "undefined"}
<p>{lang("Loading files, please wait")}.</p>
{:else}
<Card>
    <div class="flex hcenter gap" in:slide={{duration: 400, easing: cubicInOut}} out:slide={{duration: 400, easing: cubicInOut}}>
        {#if currentPath[0] !== ""}
        <button class="flex hcenter" style="padding: 5px; background-color: var(--secondcard); width: fit-content; border-radius: 50%" onclick={() => {
            window.history.back();
        }}>
            <img use:RegenerateIcons.register={{icon: "arrowleft"}} alt={lang("Go back")} class="icon">
        </button>
        {/if}  
        <h3 style="overflow-wrap: anywhere;">{currentPath[1] ? (() => { // Edit the content uri string so that it's more readable
            let str = decodeURIComponent(currentPath[0].substring(currentPath[0].indexOf("tree/") + 5));
            if (str.indexOf("/data/data/") !== -1) return str.substring(str.lastIndexOf("/data/data"));
            return str;
        })(): currentPath[0] === "" ? "Pick a directory" : currentPath[0].substring(0, currentPath[0].length - 1)}</h3>
</div>
{#key rerenderData}

{#if disableUpload}
<Card isSecondCard={true}>
    <h4>{lang("The application can't write in this folder")}.</h4>
    <p>{lang(`You won't be able to upload files to this folder until you don't select it from the "Access to external drives" section of the mobile application`)}.</p>
</Card><br>
{/if}
{#if showTable}
    <div style="overflow: auto; max-width: 100%;">
        <table bind:this={table}>
            <thead>
                <tr>
                    <th style="resize: horizontal; overflow: auto" class="hover"  onclick={() => {
                        if (sortingInfo[0] !== "name") sortingInfo[0] = "name"; else sortingInfo[1] = !sortingInfo[1];
                    }}>
                    <div class="flex hcenter gap">
                        {#if sortingInfo[0] === "name"}
                        <img class="icon" use:RegenerateIcons.register={{icon: sortingInfo[1] ? "arrowdown" : "arrowup", type: getComputedStyle(document.body).getPropertyValue("--accenttext")}} alt={lang("Sorted by this column")}>
                        {/if}
                        {lang("File name")}:
                    </div>
                    </th>
                    {#if SettingsObject.fileViewer.showContent.lastModified}
                    <th style="resize: horizontal; overflow: auto" class="hover"  onclick={() => {
                        if (sortingInfo[0] !== "lastModified") sortingInfo[0] = "lastModified"; else sortingInfo[1] = !sortingInfo[1];
                    }}>
                        <div class="flex hcenter gap">
                            {#if sortingInfo[0] === "lastModified"}
                            <img class="icon" use:RegenerateIcons.register={{icon:  sortingInfo[1] ? "arrowup" : "arrowdown", type: getComputedStyle(document.body).getPropertyValue("--accenttext")}} alt={lang("Sorted by this column")}>
                            {/if}
                            {lang("Last modified date")}:
                        </div>
                    </th>
                    {/if}
                    {#if SettingsObject.fileViewer.showContent.size}
                    <th style="resize: horizontal; overflow: auto" class="hover" onclick={() => {
                        if (sortingInfo[0] !== "size") sortingInfo[0] = "size"; else sortingInfo[1] = !sortingInfo[1];
                    }}>
                        <div class="flex hcenter gap">
                            {#if sortingInfo[0] === "size"}
                            <img class="icon" use:RegenerateIcons.register={{icon:  sortingInfo[1] ? "arrowup" : "arrowdown", type: getComputedStyle(document.body).getPropertyValue("--accenttext")}} alt={lang("Sorted by this column")}>
                            {/if}
                            {lang("File size")}:
                        </div>
                    </th>
                    {/if}
                    {#if SettingsObject.fileViewer.showContent.mimetype}
                    <th style="resize: horizontal; overflow: auto" class="hover" onclick={() => {
                        if (sortingInfo[0] !== "mimetype") sortingInfo[0] = "mimetype"; else sortingInfo[1] = !sortingInfo[1];
                    }}>
                    <div class="flex hcenter gap">
                        {#if sortingInfo[0] === "mimetype"}
                        <img class="icon" use:RegenerateIcons.register={{icon:  sortingInfo[1] ? "arrowup" : "arrowdown", type: getComputedStyle(document.body).getPropertyValue("--accenttext")}} alt={lang("Sorted by this column")}>
                        {/if}
                        {lang("File mimetype")}:
                    </div>
                    </th>
                    {/if}
                </tr>
            </thead>
            <tbody>
                {#each currentFiles as file, i (file.fileContentUri || file.path)}
                    {#if i < loadedItems}
                    <tr in:slide={{duration: 300, easing: cubicInOut}} out:slide={{duration: 300, easing: cubicInOut}} role="button" class="hover" style="transition: filter 0.2s ease-in-out;" use:addToDirectoryMap={file} onclick={(e) => fileButtonEvent(e, file)}>
                        <td style="overflow-wrap: anywhere;">
                            <div style={`padding: 10px; ${(SettingsObject.fileViewer.showImagePreview && (file.mimeType?.startsWith("image") || file.mimeType?.startsWith("video"))) || (SettingsObject.fileViewer.showAudioPreview && file.mimeType?.startsWith("audio")) || (SettingsObject.fileViewer.showPdfPreview && file.mimeType === "application/pdf") ? "flex-direction: column" : ""}`} class="flex hcenter gap">
                                {#if (SettingsObject.fileViewer.showImagePreview && (file.mimeType?.startsWith("image") || file.mimeType?.startsWith("video"))) || (SettingsObject.fileViewer.showAudioPreview && file.mimeType?.startsWith("audio")) || (SettingsObject.fileViewer.showPdfPreview && file.mimeType === "application/pdf")}
                                    <ImageIntersectionViewer useHeightProportion={true} imageBackgroundColor={file.mimeType === "application/pdf" ? "white" : undefined} name={file.path} imagePreviewUrl={`${StartPath}/api/getPreview?path=${encodeURIComponent(file.fileContentUri || `${currentPath[0]}${file.path}`)}&isContentUri=${file.fileContentUri ? "1" : "0"}&type=${encodeURIComponent(file.mimeType)}&token=${encodeURIComponent(token)}`}></ImageIntersectionViewer>
                                {:else}
                                    <img class="icon" style="pointer-events: none;" use:RegenerateIcons.register={{icon: getImageToUse(file) as "document"}} alt={"File icon"}>
                                {/if}
                                <span style="pointer-events: none;">{file.path}</span>
                            </div>
                            
                        </td>
                        {#if SettingsObject.fileViewer.showContent.lastModified}
                            <td style="overflow-wrap: anywhere;">{new Date(file.lastModified || Date.now()).toLocaleString()}</td>
                        {/if}
                        {#if SettingsObject.fileViewer.showContent.size}
                            <td style="overflow-wrap: anywhere;">{file.isDirectory && typeof file.size === "undefined" ? lang("Not calculated") : getFileSize(file.size || 0)}</td>
                        {/if}
                        {#if SettingsObject.fileViewer.showContent.mimetype}
                            <td style="overflow-wrap: anywhere;">{getMimetypeString(file)}</td>
                        {/if}
                    </tr>
                    {/if}
                {/each}
            </tbody>
        </table>
    </div>
{:else}
<div class="flex hcenter gap" style="flex-wrap: wrap; align-items: stretch">
    {#each currentFiles as file, i (file.fileContentUri || file.path)}
        {#if i < loadedItems}
        <button use:addToDirectoryMap={file} in:slide={{duration: 300, easing: cubicInOut}} out:slide={{duration: 300, easing: cubicInOut}} class="flex hcenter gap" style="background-color: var(--secondcard); flex: 1 0 calc(var(--picture-height) + 150px);" onclick={async (e) => {
            fileButtonEvent(e, file);
        }}>
            {#if SettingsObject.fileViewer.showImagePreview && (file.mimeType?.startsWith("image") || file.mimeType?.startsWith("video"))}
                <ImageIntersectionViewer useHeightProportion={true} name={file.path} imagePreviewUrl={`${StartPath}/api/getPreview?path=${encodeURIComponent(file.fileContentUri || `${currentPath[0]}${file.path}`)}&isContentUri=${file.fileContentUri ? "1" : "0"}&type=${encodeURIComponent(file.mimeType)}&token=${encodeURIComponent(token)}`}></ImageIntersectionViewer>
            {:else}
                <img class="icon" use:RegenerateIcons.register={{icon: getImageToUse(file) as "document"}} alt={lang("File icon")}>
            {/if}
            <p style="overflow-wrap: anywhere; pointer-events: none; width: 100%; flex: 1">
                <span style="color: var(--text); overflow-wrap: anywhere">{file.path}</span>
                {#if getFileInfoStr(file).length !== 0}
                <span style="margin-top: 10px; display: flex; color: var(--secondtext); flex-direction: column; overflow-wrap: anywhere">
                    {#each getFileInfoStr(file) as str}
                        <span style="width: 100%;">{str}</span>
                    {/each}
                </span>
                {/if}
            </p>
        </button>
        {/if}
    {/each}
</div>
{/if}
{#if loadedItems < currentFiles.length}
<button onclick={() => (loadedItems += 30)}>{lang("Load more items")}</button>
{/if}
{/key}
</Card>

<form style="display: none;" target="_blank" method="POST" action={`${StartPath}/api/zip?token=${encodeURIComponent(token)}`} bind:this={downloadForm}>
    <input name="type" value="files">
    <input name="paths">
    <input name="isContentUri">
    <input name="source">
</form>


{#if selectModeEnabled}
<SelectComponent changeMassSelectionCallback={(e) => {isMassSelectionEnabled = e;}} {isMassSelectionEnabled} downloadCallback={async () => {
    const formInfo = {
        paths: selectedItems,
        isContentUri: ifSelectedItemsIsContentUri,
        source: checkSource()
    };
    /**
     * Check which part of the file path we can remove since all the files are in that directory (for example, if all files are in the "Downloads" directory, we can remove "/storage/emulated/0/Downloads" from the zip file structure). To do this, we'll split each file path according to their folder, and we'll check if the folder at the position `i` is the same for every file
     */
    function checkSource() {
        let pathSplit = selectedItems.map((i, p) => i.split(ifSelectedItemsIsContentUri[p] !== -1 ? "%2F" : "/"));
        let subfoldersWithSameName = 0;
        pathSplit[0].pop();
        while (subfoldersWithSameName < pathSplit[0].length) {
            if (pathSplit.every(i => i[subfoldersWithSameName] === pathSplit[0][subfoldersWithSameName])) {
                subfoldersWithSameName++;
            } else break;
        }
        return pathSplit[0].slice(0, subfoldersWithSameName).join(ifSelectedItemsIsContentUri.every(i => !!i) ? "%2F" : "/")
    }
    if (typeof window.showDirectoryPicker === "function") { // Save the files using the File System API instead of downloading a zip file
        try {
            if (!SettingsObject.downloads.useFsApi) throw new Error("The user doesn't want to use the File System API");
            const dir = await window.showDirectoryPicker({id: "FileDownload", mode: "readwrite", startIn: "downloads"});
            let index = -1;
            async function nextItem() { // As a separate function instead of a for loop so that multiple files can be downloaded at the same time
                index++;
                const i = index;
                if (i >= formInfo.paths.length) return;
                    const checkDir = await fetch(`${StartPath}/api/dir?path=${encodeURIComponent(formInfo.paths[i])}&isContentUri=${formInfo.isContentUri[i] === -1 ? "0" : "1"}`, {headers: {Authorization: `Bearer ${token}`}});
                    if (checkDir.ok) {
                        const data = await checkDir.json() as Directory[];
                        formInfo.paths.push(...data.map(a => a.fileContentUri || `${formInfo.paths[i]}/${a.path}`));
                        formInfo.isContentUri.push(...data.map(a => a.fileContentUri && a.isDirectory ? 1 : a.fileContentUri ? 0 : -1));
                        nextItem();
                        return;
                    }
                let name = formInfo.isContentUri[i] !== -1 ? decodeURIComponent(formInfo.paths[i]) : formInfo.paths[i]; 
                name = name.substring(decodeURIComponent(formInfo.source).length);
                if (name.startsWith("/")) name = name.substring(1);
                const splitDir = name.split("/");
                let realName = splitDir.pop() ?? name;
                let outputHandle = dir;
                for (const paths of splitDir) outputHandle = await outputHandle.getDirectoryHandle(paths, {create: true});
                if (!SettingsObject.downloads.replaceFiles) { // Check if a file in the same directory exists
                    const availableFiles: string[] = [] 
                    for await (const file of outputHandle.values()) availableFiles.push(file.name);
                    let tempFileName = realName;
                    let j = 1;
                    while (availableFiles.indexOf(realName) !== -1) {
                        realName = `${tempFileName.substring(0, tempFileName.lastIndexOf("."))} (${j})${tempFileName.substring(tempFileName.lastIndexOf("."))}`;
                        j++;
                    }
                }
                const fileHandle = await outputHandle.getFileHandle(realName, {create: true});
                await FileSystemApiHelper.pipeContent(`${StartPath}/api/download?path=${encodeURIComponent(formInfo.paths[i])}&token=${encodeURIComponent(token)}&isContentUri=${formInfo.isContentUri[i] === -1 ? 0 : 1}`, name, fileHandle);
                nextItem();
            }
            for (let i = 0; i < SettingsObject.downloads.concurrentDownloads; i++) nextItem();
            return;
        } catch(ex) {
            console.warn(ex);
        }
    }
    // Populate the form entries to download a zip file
    (downloadForm.querySelector("[name=paths]") as HTMLInputElement).value = JSON.stringify(formInfo.paths);
    (downloadForm.querySelector("[name=isContentUri]") as HTMLInputElement).value = JSON.stringify(formInfo.isContentUri);
    (downloadForm.querySelector("[name=source]") as HTMLInputElement).value = formInfo.source;
    downloadForm.submit();
}} deleteCallback={async (e) => {
    if (confirm(lang("Do you want to delete all the selected files? This action can't be undone."))) {
        ((e.target as HTMLImageElement).parentElement as HTMLButtonElement).disabled = true;
        for (let i = 0; i < selectedItems.length; i++) {
            const req = await fetch(`${StartPath}/api/delete?path=${encodeURIComponent(selectedItems[i])}&isContentUri=${ifSelectedItemsIsContentUri[i] !== -1 ? "1" : "0"}`, {
                headers: {
                    Authorization: `Bearer ${token}`
                }
            });
            if (req.ok) {
                const index = (currentFiles ?? []).findIndex(j => (j.fileContentUri || `${currentPath[0]}${j.path}`) === selectedItems[i]);
                if (index !== -1) currentFiles?.splice(index, 1);
                selectedItems.splice(i, 1);
                ifSelectedItemsIsContentUri.splice(i, 1);
                i--;
            }
        }
        ((e.target as HTMLImageElement).parentElement as HTMLButtonElement).disabled = false;
    }
}} closeCallback={async () => {
    clearSelectedItems();
    if (topBtnContainer) topBtnContainerTransition(topBtnContainer, true).then(() => topBtnContainer && topBtnContainerTransition(topBtnContainer))
    selectModeEnabled = false;
}}></SelectComponent>
{/if}
{/if}

{#if showSettings}
<Settings callback={() => (showSettings = false)} callbacks={{rerenderFilesTab: () => (rerenderData = Date.now()), changeFileTable: () => (showTable = SettingsObject.fileViewer.useTableView)}}></Settings>
{/if}