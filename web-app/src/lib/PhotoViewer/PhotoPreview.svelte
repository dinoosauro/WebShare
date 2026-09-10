<script lang="ts">
    import { fade, slide } from "svelte/transition";
    import type { FileMetadata, MediaInfo } from "../../ts/Interfaces/API";
    import Card from "../SmallComponents/Card.svelte";
    import leaflet from "leaflet";
    import "leaflet/dist/leaflet.css"
    import { cubicInOut } from "svelte/easing";
    import { getIconSrc } from "../../ts/IconManager";
    import InputRange from "../SmallComponents/InputRange.svelte";
    import { onMount } from "svelte";
    import convertNumberToStr from "../../ts/ConvertNumberToString";
    import StartPath from "../../ts/StartPath";
    import lang from "../../ts/Lang";
    import Settings from "../../ts/Settings";
    import checkIfColorIsDark from "../../ts/CheckIfColorIsDark";
    import icon2x from 'leaflet/dist/images/marker-icon-2x.png';
    import icon from 'leaflet/dist/images/marker-icon.png';
    import shadow from 'leaflet/dist/images/marker-shadow.png';
    import RegenerateIcons from "../../ts/RegenerateIcons";


    const {token, sourceImage, data, callback, backFn, getLocationReload, nextImage, prevImage}: {
        token: string, 
        /**
         * Information obtained by the MediaStore about the opened image
         */
        data: MediaInfo, 
        /**
         * Src of the image
         */
        sourceImage: string, 
        /**
         * Function called when the image has been loaded. This permits to fire a transition.
         * @param image the image loaded
         * @param main the container of the PhotoPreview container
         */
        callback: (image: HTMLImageElement, main: HTMLElement) => Promise<void>, 
        /**
        * Function called when the user closes the PhotoPreview dialog. This permits to fire a transition.
        * @param image the image loaded
        * @param main the container of the PhotoPreview container
        * @param imagesDeleted a list of MediaInfo elements that have been deleted while the PhotoPreview component was visible
        */
        backFn: (image: HTMLImageElement, main: HTMLElement, imagesDeleted: MediaInfo[]) => void,
        /**
         * This function contains the function that can be called to refresh the location map. This is usually used in the Settings, so that the user can change the map style.
         */
        getLocationReload: (fn: (() => void) | undefined) => void,
        /**
         * Go to the next image
         */
        nextImage: () => void,
        /**
         * Go to the previous image
         */
        prevImage: () => void
    } = $props();

    /**
     * Main container of the PhotoPreview component
     */
    let main: HTMLElement;
    /**
     * The image loaded
     */
    let img: HTMLImageElement;
    /**
     * The video loaded
     */
    let video: HTMLVideoElement;
    /**
     * If the image has been loaded and the transition has been fired.
     */
    let imageEventDone = false;
    /**
     * Url used to download the image
     */
    let downloadUrl = $derived(`${StartPath}/api/download?id=${encodeURIComponent(data.id)}&mimetype=${encodeURIComponent(data.mimeType)}&token=${encodeURIComponent(token)}`);
    /**
     * Metadata of the current image
     */
    let metadata: FileMetadata[] | undefined = $state();
    /**
     * Container of the location map
     */
    let leafletMap: HTMLElement;
    /**
     * Leaflet instance attached to the location map container
     */
    let locationMap: leaflet.Map | undefined;
    /**
     * Width and height of the video/image
     */
    let contentSize: [number, number] | undefined = $state();
    /**
     * If the video controls should be shown or not
     */
    let showVideoControls = $state(false);
    /**
     * If the metadata of the video have been loaded (and so playback can start)
     */
    let videoMetadataLoaded = false;


    // VIDEO-SPECIFIC COMPONENTS

    /**
     * Slider to change the progress of video playback
     */
    let videoSlider: HTMLInputElement | undefined;
    let currentTimeSpan: HTMLSpanElement;
    /**
     * If the user is changing mamually the slider position
     */
    let isVideoSliderClickInProgress = false;
    /**
     * If the "change" event of the input slider has been called automatically (from the timechange event)
     */
    let isVideoSliderInputTriggered = false;
    /**
     * The selected action in the video controls: if undefined, all the action buttons should be shown. Otherwise, a slider to edit the playback rate or the volume should be shown instead of the buttons
     */
    let videoControlsSelectedBtn: "volume" | "playbackrate" | undefined = $state();
    /**
     * The Range input used to change either the volume or the playback
     */
    let volumeOrPlaybackSelect: HTMLInputElement | undefined = $state();
    /**
     * The filter used to increase or decrease the volume
     */
    let volumeFilter: GainNode | undefined;
    /**
     * The AudioContext used to create the volume filter
     */
    let context: AudioContext | undefined;
    /**
     * If the video has been paused or not
     */
    let isPaused = $state(false);
    /**
     * The image or video container
     */
    let imageViewer: HTMLElement;
    /**
     * The icon in the video controls that permits to toggle fullscreen mode
     */
    let fullscreenIcon: HTMLImageElement | undefined;
    /**
     * A list of the images that have been deleted from this component
     */
    let deletedImages: MediaInfo[] = [];
    /**
     * If the component is being closed
     */
    let isBeingDestroyed = false;
    /**
     * Close the PhotoPreview component
     */
    function closeWrapper() {
        img.style.display = "block";
        if (video) video.style.display = "none";
        isBeingDestroyed = true;
        setTimeout(() => window.history.back(), 25);
    }

    $effect(() => {
        if (volumeOrPlaybackSelect) { // Permit to trigger events to the input after the transition has ended
            setTimeout(() => ((volumeOrPlaybackSelect?.parentElement?.parentElement as HTMLElement).style.pointerEvents = "unset"), 600);
        }
    })
    /**
     * If it's the first time the State is being changed or not. This is saved so that, if the user goes backwards/forwards, the application will only replace the State with the new image information
     */
    let isFirstState = true;
    $effect(() => { // Change the state
        if (isBeingDestroyed) return;
        window.history[isFirstState ? "pushState" : "replaceState"]({...window.history.state, imageOpenInfo: {id: data.id, mimeType: data.mimeType}}, "");
        if (img) img.style.display = "block";
        if (video) video.style.display = "none";
        isFirstState = false;
    })
    /**
     * A variable that forces realoding the location map
     */
    let locationReload = $state(Date.now());
    onMount(() => {
        leaflet.Icon.Default.mergeOptions({
            iconRetinaUrl: icon2x,
            iconUrl: icon,
            shadowUrl: shadow,
        });
        function fullscreenEvent() {
            if (fullscreenIcon) RegenerateIcons.register(fullscreenIcon, {icon: (document.fullscreenElement ? "fullscreenminimize" : "fullscreenmaximize")});
        }
        
        function popstateEvent() {
            backFn(img, main, deletedImages);
            window.history.replaceState({...window.history.state, imageOpenInfo: undefined}, "");
        }

        function keyboardEvent(e: KeyboardEvent) {
            if (e.code === "ArrowRight") nextImage(); else if (e.code === "ArrowLeft") prevImage(); else if (e.code === "Escape") {
                window.removeEventListener("keydown", keyboardEvent); // Let's remove it immediately, so that the user can't trigger multiple animations by pressing Escape multiple times.
                closeWrapper();
            }
        }
        window.addEventListener("fullscreenchange", fullscreenEvent);
        window.addEventListener("popstate", popstateEvent);
        window.addEventListener("keydown", keyboardEvent);
        getLocationReload(() => (locationReload = Date.now()));
        return () => {
            locationMap?.remove();
            if (volumeFilter) volumeFilter.disconnect();
            if (context) context.close();
            window.removeEventListener("fullscreenchange", fullscreenEvent);
            window.removeEventListener("popstate", popstateEvent);
            window.removeEventListener("keydown", keyboardEvent);
            getLocationReload(undefined);
        }
    })

    /**
     * Create the location map
     */
    function createLocationMap() {
        const latitude = metadata?.find(i => i.description === "GPS Latitude" || i.description === "Latitude");
        const longitude = metadata?.find(i => i.description === "GPS Longitude" || i.description === "Longitude");
        if (typeof latitude?.value !== "undefined" && typeof longitude?.value !== "undefined") {
            // Let's convert latitude and longitude so that we can have a number value
            const regex = /°|'|\"/g; 
            const metadataPosition = [
                regex.test(latitude.value) ? latitude.value.replaceAll(" ", "").replaceAll(",", ".").split(regex).reduce((a, b, i) => a + (+b / (i === 0 ? 1 : i === 1 ? 60 : 3600)), 0).toString() : latitude.value.replace(",", "."),
                regex.test(longitude.value) ? longitude.value.replaceAll(" ", "").replaceAll(",", ".").split(/°|'|\"/g).reduce((a, b, i) => a + (+b / (i === 0 ? 1 : i === 1 ? 60 : 3600)), 0).toString() : longitude.value.replace(",", ".")
            ];
            const interval = setInterval(() => {
                if (!metadataPosition) {
                    clearInterval(interval);
                    return;
                }
                if (leafletMap) {
                    clearInterval(interval);
                    leafletMap.style.height = "30vh";
                    locationMap?.remove();
                    locationMap = leaflet.map(leafletMap).setView([+metadataPosition[0], +metadataPosition[1]], 13);
                    leaflet.tileLayer(Settings.photoViewer.locationMapStyle === "satellite" ? 'https://tiles.stadiamaps.com/tiles/alidade_satellite/{z}/{x}/{y}{r}.{ext}' : `https://tiles.stadiamaps.com/tiles/alidade_smooth${(Settings.photoViewer.locationMapStyle === "default" && checkIfColorIsDark(getComputedStyle(document.body).getPropertyValue("--secondcard"))) || Settings.photoViewer.locationMapStyle === "dark" ? "_dark" : ""}/{z}/{x}/{y}{r}.{ext}`, {
                        minZoom: 0,
                        maxZoom: 20,
                        attribution: Settings.photoViewer.locationMapStyle === "satellite" ? '&copy; CNES, Distribution Airbus DS, © Airbus DS, © PlanetObserver (Contains Copernicus Data) | &copy; <a href="https://www.stadiamaps.com/" target="_blank">Stadia Maps</a> &copy; <a href="https://openmaptiles.org/" target="_blank">OpenMapTiles</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' : '&copy; <a href="https://www.stadiamaps.com/" target="_blank">Stadia Maps</a> &copy; <a href="https://openmaptiles.org/" target="_blank">OpenMapTiles</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
                        // @ts-ignore
                        ext: Settings.photoViewer.locationMapStyle === "satellite" ? "jpg" : "png"
                    }).addTo(locationMap);
                    leaflet.marker([+metadataPosition[0], +metadataPosition[1]]).addTo(locationMap)
                }
            }, 15)
        }
    }

    $effect(() => {
        function useless(_: any) {}
        useless(locationReload);
        if (metadata) createLocationMap();
    })

    $effect(() => { // Load metadata
        fetch(`${StartPath}/api/metadata?id=${encodeURIComponent(data.id)}&isVideo=${data.mimeType.startsWith("video") ? "1" : "0"}`, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        }).then((res) => {
            if (!res.ok) return;
            res.json().then((json) => {
                metadata = json;
            })
        })
    });    
</script>

<div class="main opacity" bind:this={main}>
    <div class="container">
        <!-- svelte-ignore a11y_no_static_element_interactions -->
        <!-- svelte-ignore a11y_click_events_have_key_events -->
        <!-- svelte-ignore a11y_mouse_events_have_key_events -->
        <div class="flex hcenter imgContainer" style="position: relative;" bind:this={imageViewer} onmouseenter={() => (showVideoControls = videoMetadataLoaded)} onmouseover={() => (showVideoControls = videoMetadataLoaded)} onmouseleave={() => (showVideoControls = false)} onclick={(e) => !(e.target as HTMLElement).closest(".videoControls") && (showVideoControls = !showVideoControls)}>
        <div style="left: 0px;" class="backForwardBtn">
            <button style="width: 100%; height: 100%;" class="emptyBtn" onclick={() => prevImage()}>
                <img use:RegenerateIcons.register={{icon: "arrowcircleleft"}} alt={lang("Previous image")} style="margin-left: 15px; width: 36px; height: 36px">
            </button>
        </div>
        <div style="right: 0px;" class="backForwardBtn">
            <button style="width: 100%; height: 100%" class="emptyBtn" onclick={() => nextImage()}>
                <img use:RegenerateIcons.register={{icon: "arrowcircleright"}} alt={lang("Next image")} style="margin-right: 15px; width: 36px; height: 36px">
            </button>
        </div>
            <img style="opacity: 0;" alt={data.name} src={sourceImage} bind:this={img} onload={(e) => {
                !imageEventDone && callback(img, main).then(() => {
                    if (video) {
                        img.style.display = "none";
                        video.style.display = "block";
                        video.play();
                    }
                });
                if (!isFirstState && video) { // The video has been loaded from the next/previous button. The animation won't be triggered, so we manually need to start the video
                    img.style.display = "none";
                    video.style.display = "block";
                    video.play();
                }
                imageEventDone = true;
                if (img.src.startsWith(downloadUrl)) {
                    contentSize = [img.naturalWidth, img.naturalHeight];
                } else if (data.mimeType.startsWith("image/")) img.src = `${downloadUrl}${data.mimeType === "image/heic" || data.mimeType === "image/heif" ? "&convertTo=webp" : ""}`
            }}>
            {#if data.mimeType.startsWith("video/")}
                <!-- svelte-ignore a11y_media_has_caption -->
                <video onpause={() => (isPaused = true)} onplay={() => (isPaused = false)} crossorigin="anonymous" onloadedmetadata={() => {
                    contentSize = [video.videoWidth, video.videoHeight];
                    if (videoSlider) videoSlider.max = video.duration.toString();
                    videoMetadataLoaded = true;
                }} bind:this={video} ontimeupdate={() => {
                    if (currentTimeSpan) currentTimeSpan.textContent = convertNumberToStr(video?.currentTime ?? 0);
                    if (videoSlider && !isVideoSliderClickInProgress) {
                        videoSlider.value = video.currentTime.toString();
                        isVideoSliderInputTriggered = true;
                        videoSlider.dispatchEvent(new Event("input"));
                    }
                }} style="display: none" src={`${StartPath}/api/download?id=${encodeURIComponent(data.id)}&mimetype=${encodeURIComponent(data.mimeType)}&token=${encodeURIComponent(token)}`}></video>
            {#if showVideoControls}
            <div class="videoControls" in:fade={{duration: 300, easing: cubicInOut}} out:fade={{duration: 300, easing: cubicInOut}}>
                <div style="padding: 15px;">
                    <InputRange max={video?.duration} defaultValue={video?.currentTime} step={0.001} onRendered={(e) => (videoSlider = e)} events={[["input", () => {
                        if (isVideoSliderInputTriggered) {
                            isVideoSliderInputTriggered = false;
                            return;
                        }
                        video.currentTime = +(videoSlider as HTMLInputElement).value;
                    }], ["mousedown", () => (isVideoSliderClickInProgress = true)], ["mouseup", () => (isVideoSliderClickInProgress = false)], ["touchstart", () => (isVideoSliderClickInProgress = true)], ["touchend", () => (isVideoSliderClickInProgress = false)]]}></InputRange>
                    <span style="float: left; margin-top: 10px;" bind:this={currentTimeSpan}>{convertNumberToStr(video?.currentTime ?? 0)}</span>
                    <span style="float: right; margin-top: 10px;">{convertNumberToStr(video?.duration ?? 0)}</span><br><br>
                    <div class="flex wcenter gap hcenter" style="width: 100%;">
                        {#if videoControlsSelectedBtn}
                            <div style="width: 100%; pointer-events: none" in:slide={{duration: 300, easing: cubicInOut, axis: "x", delay: 300}} out:slide={{duration: 300, easing: cubicInOut, axis: "x"}}>
                                <InputRange defaultValue={(videoControlsSelectedBtn === "volume" ? (volumeFilter?.gain.value ?? 1) : video.playbackRate).toString()} onRendered={(range) => (volumeOrPlaybackSelect = range)} step={0.001} max={videoControlsSelectedBtn === "volume" ? 2 : 3} events={[["input", () => {
                                    if (!volumeOrPlaybackSelect) return;
                                    switch(videoControlsSelectedBtn) {
                                        case "playbackrate":
                                            video.playbackRate = +volumeOrPlaybackSelect.value;
                                            break;
                                        case "volume": {
                                            if (!volumeFilter) {
                                                context = new AudioContext();
                                                const audio = context.createMediaElementSource(video);
                                                volumeFilter = context.createGain();
                                                audio.connect(volumeFilter).connect(context.destination);
                                            }
                                            volumeFilter.gain.value = +volumeOrPlaybackSelect.value;
                                            break;
                                        }
                                    }
                                }]]}></InputRange>
                            </div>
                        {/if}
                        {#if !videoControlsSelectedBtn || videoControlsSelectedBtn === "playbackrate"}
                        <button class="emptyBtn flex hcenter backgroundChange" style={videoControlsSelectedBtn === "playbackrate" ? "background-color: var(--accent)" : undefined} in:fade={{duration: 300, easing: cubicInOut, delay: 300}} out:fade={{duration: 300, easing: cubicInOut}} onclick={() => {
                            videoControlsSelectedBtn = videoControlsSelectedBtn === "playbackrate" ? undefined : "playbackrate";
                        }}>
                            <img use:RegenerateIcons.register={{icon: "topspeed"}} class="icon" alt={lang("Change playback rate")}>
                        </button>
                        {/if}
                        {#if !videoControlsSelectedBtn}
                        <button class="emptyBtn flex hcenter backgroundChange" in:fade={{duration: 300, easing: cubicInOut, delay: 300}} out:fade={{duration: 300, easing: cubicInOut}} onclick={() => {
                            document.pictureInPictureElement ? document.exitPictureInPicture() : video.requestPictureInPicture();
                        }}>
                            <img use:RegenerateIcons.register={{icon: "pictureinpicture"}} class="icon" alt={lang("Toggle picture-in-picture for this video")}>
                        </button>
                        <button class="emptyBtn flex hcenter backgroundChange" in:fade={{duration: 300, easing: cubicInOut, delay: 300}} out:fade={{duration: 300, easing: cubicInOut}} onclick={() => {
                            video.paused ? video.play() : video.pause();
                        }}>
                            <img use:RegenerateIcons.register={{icon: isPaused ? "play" : "pause"}} class="icon" alt={lang("Play/pause")}>
                        </button>
                        <button class="emptyBtn flex hcenter backgroundChange" in:fade={{duration: 300, easing: cubicInOut, delay: 300}} out:fade={{duration: 300, easing: cubicInOut}} onclick={() => {
                            if (document.fullscreenElement) document.exitFullscreen();
                            if (typeof imageViewer.requestFullscreen === "function") {
                                imageViewer.requestFullscreen({navigationUI: "hide"});
                                return;
                            }
                            // @ts-ignore
                            if (typeof video.webkitEnterFullscreen === "function") video.webkitEnterFullscreen();
                        }}>
                            <img use:RegenerateIcons.register={{icon: document.fullscreenElement ? "fullscreenminimize" : "fullscreenmaximize"}} bind:this={fullscreenIcon} class="icon" alt={lang("Toggle fullscreen mode")}>
                        </button>
                        {/if}
                        {#if !videoControlsSelectedBtn || videoControlsSelectedBtn === "volume"}
                        <button class="emptyBtn flex hcenter backgroundChange" style={videoControlsSelectedBtn === "volume" ? "background-color: var(--accent)" : undefined} in:fade={{duration: 300, easing: cubicInOut, delay: 300}} out:fade={{duration: 300, easing: cubicInOut}} onclick={() => {
                            videoControlsSelectedBtn = videoControlsSelectedBtn === "volume" ? undefined : "volume";
                        }}>
                            <img use:RegenerateIcons.register={{icon: "speakers"}} class="icon" alt={lang("Change volume")}>
                        </button>
                        {/if}
                    </div>
                </div>
            </div>
            {/if}
            {/if}
        </div>
            <div style="background-color: var(--card);" class="metadataContainer">
            <div style="padding: 10px;">
                <p style="font-size: 20px; margin-right: 60px; overflow-wrap: anywhere;">{data.name}</p><br>
                <p>{lang("Taken on")} {new Date(data.dateTaken || data.dateModified || data.dateAdded).toLocaleString(undefined, {weekday: "long", day: "numeric", month: "long", year: "numeric", hour: "numeric", minute: "numeric"})}</p>
                <Card isSecondCard={true}>
                    <h4>{lang("Actions")}:</h4>
                    <div class="flex" style="gap: 5px; flex-wrap: wrap; align-items: stretch;">
                        <button style="flex: 1 0 150px" onclick={() => {
                            const name = Settings.photoViewer.downloadImageFormat === "default" || data.mimeType.startsWith("video") ? data.name : `${data.name.substring(0, data.name.lastIndexOf("."))}.${Settings.photoViewer.downloadImageFormat === "jpeg" ? "jpg" : Settings.photoViewer.downloadImageFormat}`;
                            const a = Object.assign(document.createElement("a"), {
                                href: `${downloadUrl}&name=${encodeURIComponent(name)}${Settings.photoViewer.downloadImageFormat === "default" || data.mimeType.startsWith("video") ? "" : `&convertTo=${Settings.photoViewer.downloadImageFormat.toLowerCase()}`}`,
                                download: name,
                                target: "_blank",
                            });
                            a.click();
                        }}>
                           {lang("Download")}
                        </button>
                        {#if typeof data.isFavorite !== "undefined"}
                            <button style="flex: 1 0 150px" onclick={async () => {
                                const req = await fetch(`${StartPath}/api/addfavorites?id=${encodeURIComponent(data.id)}&isVideo=${data.mimeType.startsWith("video") ? "1" : "0"}&favorite=${data.isFavorite ? "0" : "1"}`, {headers: {Authorization: `Bearer ${token}`}})
                                if (req.ok) data.isFavorite = !data.isFavorite;
                            }}>{lang(data.isFavorite ? "Remove image from favorites" : "Add image to favorites")}</button>
                        {/if}
                        <button style="flex: 1 0 150px" onclick={async () => {
                            if (confirm(lang("Do you want to delete the selected image? This action can't be undone."))) {
                                const req = await fetch(`${StartPath}/api/delete?id=${encodeURIComponent(data.id)}&type=${data.mimeType.startsWith("video") ? "video" : "image"}`, {
                                    headers: {
                                        Authorization: `Bearer ${token}`
                                    }
                                })
                                if (req.ok) {
                                    deletedImages.push(data);
                                    if (Settings.photoViewer.goToNextImageWhenDeleting) {
                                        nextImage();
                                        return;
                                    }
                                    closeWrapper()
                                }
                            }
                        }}>{lang("Delete image")}</button>
                        <button style="flex: 1 0 150px" onclick={() => closeWrapper()}>{lang("Close preview")}</button>
                    </div>
                </Card>
                {#if metadata}
                <br>
                <div in:slide={{duration: 200, easing: cubicInOut}} out:slide={{duration: 200, easing: cubicInOut}}>
                    <Card isSecondCard={true}>
                        <h4>{lang("Metadata")}:</h4>
                        {#each metadata as entry}
                        {#if entry.description === "Make" || entry.description === "Model" || entry.description === "Aperture Value" || entry.description === "Frame Rate" || entry.description === "Shutter Speed Value"}
                            <div class="flex hcenter gap">
                                <img class="icon" alt={entry.description} use:RegenerateIcons.register={{icon: entry.description === "Make" ? "buildingfactory" : entry.description === "Model" ? "camera" : entry.description === "Aperture Value" ? "lightbulb" : entry.description === "Frame Rate" ? "personrunning" : "timer"}}>
                                <p><u>{entry.description}:</u> {entry.value}</p>
                            </div>
                        {/if}
                        {/each}
                        {#if contentSize}
                            <div class="flex hcenter gap">
                                <img class="icon" alt={lang("Image size")} use:RegenerateIcons.register={{icon: "resize"}}>
                                <p><u>{lang(`${data.mimeType.startsWith("video") ? "Video" : "Image"} size`)}:</u> {contentSize[0]}*{contentSize[1]} pixels</p>
                            </div>
                        {/if}
                        {#key `${locationReload}-${data.id}`}
                        <div style="width: 100%; border-radius: 12px;" bind:this={leafletMap}></div><br>
                        {/key}
                        <Card>
                            <details>
                                <summary>
                                    <strong>{lang("Metadata list")}:</strong>
                                </summary>
                                <ul>
                                    {#each metadata as entry}
                                    <li><u>{entry.description}</u>: {entry.value}</li>
                                    {/each}
                                </ul>
                            </details>
                        </Card>
                    </Card>
                </div>
                {/if}
            </div>
            </div>
    </div>
</div>

<style>
    .container {
        width: 100vw;
        height: 100vh;
        display: flex;
        gap: 10px;
    }

    .imgContainer {
        width: calc(60vw - 5px);
        justify-content: center;
    }

    .metadataContainer {
        width: calc(40vw - 5px); 
        border-top-left-radius: 18px; 
        border-bottom-left-radius: 18px;
        overflow: auto;
    }

    .imgContainer > img:not(.icon), video {
        width: 100%; height: auto; object-fit: contain; max-height: 100vh;
    }

    @media (max-width: 600px) {
        .container {
            overflow: auto;
            display: block;
        }
        .imgContainer {
            width: 100%;
            height: calc(60vh - 5px);
        }        
        .metadataContainer {
            border-bottom-left-radius: unset;
            border-top-right-radius: 12px;
            width: 100%;
            min-height: calc(40vh);
            margin-top: 10px;
            overflow: unset;
        }
        .imgContainer > img:not(.icon), video {
            height: 100%; width: auto; max-width: 100vw; max-height: unset;
        }
    }
    .backForwardBtn {
        width: 15%;
        min-width: 51px;
        position: absolute;
        height: 100%;
        opacity: 0;
        transition: opacity 0.2s ease-in-out;
    }
    .imgContainer:hover .backForwardBtn {
        opacity: 1;
    }
</style>