<script lang="ts">
    import { onMount } from "svelte";
    import { getIconSrc } from "../../ts/IconManager";
    import type { AudioMetadata } from "../../ts/Interfaces/API";
    import PlayAudio from "../../ts/PlayAudio";
    import type { CallbackProperties } from "../../ts/Interfaces/Website";
    import InputRange from "../SmallComponents/InputRange.svelte";
    import convertNumberToStr from "../../ts/ConvertNumberToString";
    import { slide } from "svelte/transition";
    import { cubicInOut } from "svelte/easing";
    import { HandleMultipleTextOverflows, HandleTextOverflow } from "../../ts/HandleTextOverflow";
    import { floatingComponent } from "../../ts/AddEmptySpace";
    import CreateAlbumArt from "../../ts/CreateAlbumArt";
    import lang from "../../ts/Lang";
    import appendToBody from "../../ts/AppendToBody";
    import StartPath from "../../ts/StartPath";

    const {token}: {token: string} = $props();
    /**
     * The metadata of the currently-playing track
     */
    let currentInfo: AudioMetadata | undefined = $state();
    /**
     * The image src of the previous album art. Used so that a fade out transition can be applied
     */
    let prevAlbumArtId: string | undefined = $state();
    /**
     * The play/pause image icon
     */
    let playIcon: HTMLImageElement;
    /**
     * Slider used to change the progress in the music playback
     */
    let audioSlider: HTMLInputElement | undefined;
    /**
     * Text block where the current time is written
     */
    let progressSpan: HTMLElement;
    /**
     * Text block where the song duration is written
     */
    let durationSpan: HTMLElement;

    /**
     * If the "change" event of the input slider has been called automatically (from the timechange event)
     */
    let isAudioSliderInputTriggered = false;
    /**
     * If the user is changing mamually the slider position
     */
    let isAudioSliderClickInProgress = false;
    /**
     * Don't update the `currentInfo` if the playback ID is the same as this value. This is used to keep the pop-up player closed even while music is being played
     */
    let skipPlaybacksOfId = -1;

    let volumeButtonPosition = $state<DOMRectReadOnly | undefined>();

    onMount(() => {
        function event(info: CallbackProperties) {
            if (skipPlaybacksOfId === PlayAudio.currentReproductionId) return;
            if (info.info) {
                if (currentInfo) prevAlbumArtId = `${StartPath}/api/getPreview?id=${encodeURIComponent(currentInfo.id)}&albumId=${encodeURIComponent(currentInfo.albumId ?? "")}&type=audio&token=${encodeURIComponent(token)}`;
                currentInfo = info.info;
                if (typeof info.info.duration !== "undefined") {
                    if (audioSlider) audioSlider.max = info.info.duration.toString();
                    durationSpan.textContent = convertNumberToStr(info.info.duration / 1000);
                }
            }
            if (info.paused) playIcon.src = getIconSrc("play");
            if (info.playing) playIcon.src = getIconSrc("pause");
            if ((typeof info.duration !== "undefined" || typeof info.position !== "undefined") && audioSlider && !isAudioSliderClickInProgress) {
                if (typeof info.position !== "undefined") {
                    audioSlider.value = info.position.toString();
                    progressSpan.textContent = convertNumberToStr(info.position);
                }
                if (typeof info.duration !== "undefined" && !isNaN(info.duration)) {
                    audioSlider.max = info.duration.toString();
                    durationSpan.textContent = convertNumberToStr(info.duration);
                }
                isAudioSliderInputTriggered = true;
                audioSlider.dispatchEvent(new Event("input"));
            }
        }
        PlayAudio.callbacks.add(event);
        return () => PlayAudio.callbacks.delete(event);
    })
    /**
     * Album art image element
     */
    let albumArt: HTMLImageElement;

    /**
     * Add the scroll overflow animation to the passed item
     */
    function addOverflowAnimation(item: HTMLElement) {
        HandleMultipleTextOverflows(item);
    }
</script>

{#if currentInfo}
    {#if prevAlbumArtId}
        <img use:appendToBody onload={(e) => {
            (e.target as HTMLElement).classList.add("opacityFade");
            (e.target as HTMLElement).style.opacity = "1";
            const id = prevAlbumArtId;
            const interval = setInterval(() => { // Set an interval that automatically updates the div position, so that, if the user resizes the window, the position will be fixed
                if (!e.target) {
                    clearInterval(interval);
                    return;
                }
                (e.target as HTMLElement).style.bottom = `${window.innerHeight - albumArt.getBoundingClientRect().bottom}px`;
                (e.target as HTMLElement).style.left = `${albumArt.getBoundingClientRect().left}px`;
            }, 15);
            setTimeout(() => {
                if (id === prevAlbumArtId) prevAlbumArtId = undefined;
                clearInterval(interval);
            }, 1000);
        }} style={`bottom: ${window.innerHeight - albumArt.getBoundingClientRect().bottom}px; left: ${albumArt.getBoundingClientRect().left}px; opacity: 0; pointer-events: none; border-radius: 12px; max-width: 16vw; max-height: 16vh;`} src={`${StartPath}/api/getPreview?id=${encodeURIComponent(currentInfo.id)}&albumId=${encodeURIComponent(currentInfo.albumId ?? "")}&type=audio&token=${encodeURIComponent(token)}`} alt={lang("Next album art")}>
    {/if}
<div use:floatingComponent class="bottomContainer" in:slide={{duration: 300, easing: cubicInOut}} out:slide={{duration: 300, easing: cubicInOut}}>
    <button class="emptyBtn" style="position: absolute; right: 15px; top: 15px" onclick={() => {
        skipPlaybacksOfId = PlayAudio.currentReproductionId;
        currentInfo = undefined;
    }}>
        <img src={getIconSrc("dismiss")} class="icon" style="width: 16px; height: 16px" alt={lang("Close dialog")}>
    </button>
    <button class="emptyBtn" style="position: absolute; right: 35px; top: 15px" onclick={(e) => {
        volumeButtonPosition = (e.target as HTMLElement).getBoundingClientRect();
    }}>
        <img src={getIconSrc("speakers")} class="icon" style="width: 16px; height: 16px" alt={lang("Change volume")}>
    </button>
    <div class="flex hcenter gap" style="gap: 25px;">
        <div >
            <img style="border-radius: 12px; max-width: 16vw; max-height: 16vh;" bind:this={albumArt} onerror={(e) => {
                (e.target as HTMLImageElement).src = CreateAlbumArt(currentInfo?.title || currentInfo?.name || "");
            }} src={prevAlbumArtId || `${StartPath}/api/getPreview?id=${encodeURIComponent(currentInfo.id)}&albumId=${encodeURIComponent(currentInfo.albumId ?? "")}&type=audio&token=${encodeURIComponent(token)}`} alt="Album art">
        </div>
        <div style="width: 100%; overflow: hidden">
            <div style="height: 20px;"></div>
            <div class="overflow" style="width: 100%;">
                <strong style="display: block" onerror={(e) => {
                    (e.target as HTMLImageElement).src = CreateAlbumArt(currentInfo?.title || currentInfo?.name || "");
                }} use:addOverflowAnimation>{currentInfo.title || currentInfo.name}</strong>
            </div>
            <div style="height: 5px;"></div>
            <div class="overflow" style="width: 100%;">
                <p use:addOverflowAnimation>{currentInfo.album ?? lang("Unknown album")} – {currentInfo.artist ?? lang("Unknown artist")}</p>
            </div>
            <div style="height: 15px;"></div>
            <InputRange max={PlayAudio.audio.duration} defaultValue={PlayAudio.audio.currentTime} step={0.001} onRendered={(e) => (audioSlider = e)} events={[["input", (e) => {
                if (isAudioSliderInputTriggered) {
                    isAudioSliderInputTriggered = false;
                    return;
                }
                PlayAudio.audio.currentTime = +(audioSlider as HTMLInputElement).value;
                progressSpan.textContent = convertNumberToStr(+(audioSlider as HTMLInputElement).value);
            }], ["mousedown", () => (isAudioSliderClickInProgress = true)], ["mouseup", () => (isAudioSliderClickInProgress = false)], ["touchstart", () => (isAudioSliderClickInProgress = true)], ["touchend", () => (isAudioSliderClickInProgress = false)]]}></InputRange>
            <div style="height: 5px;"></div>
            <div>
                <span style="float: left" bind:this={progressSpan}></span>
                <span style="float: right;" bind:this={durationSpan}></span>
            </div>
            <div class="flex hcenter wcenter gap" style="width: 100%;">
                <button class="emptyBtn" onclick={(e: Event) => {
                    PlayAudio.shuffle = !PlayAudio.shuffle;
                    (e.target as HTMLImageElement).src = getIconSrc(PlayAudio.shuffle ? "shuffle" : "shuffleoff");
                }}>
                    <img src={getIconSrc(PlayAudio.shuffle ? "shuffle" : "shuffleoff")} class="icon" alt={lang("Enable/disable shuffle")}>
                </button>
                <button class="emptyBtn" onclick={() => {
                    PlayAudio.prev();
                }}>
                    <img src={getIconSrc("previous")} class="icon" alt={lang("Previous track")}>
                </button>
                <button class="emptyBtn" onclick={() => {
                    PlayAudio.audio.paused ? PlayAudio.audio.play() : PlayAudio.audio.pause();
                }}>
                    <img bind:this={playIcon} src={getIconSrc("pause")} class="icon" alt={lang("Play/pause")}>
                </button>
                <button class="emptyBtn" onclick={() => {
                    PlayAudio.next();
                }}>
                    <img src={getIconSrc("next")} class="icon" alt={lang("Next track")}>
                </button>
                <button class="emptyBtn" onclick={(e: Event) => {
                    PlayAudio.repeat = PlayAudio.repeat === "no" ? "yes" : PlayAudio.repeat === "yes" ? "single" : "no";
                    (e.target as HTMLImageElement).src = getIconSrc(PlayAudio.repeat === "no" ? "repeatalloff" : PlayAudio.repeat === "yes" ? "repeatall" : "repeat1");
                }}>
                    <img src={getIconSrc(PlayAudio.repeat === "no" ? "repeatalloff" : PlayAudio.repeat === "yes" ? "repeatall" : "repeat1")} class="icon" alt={lang("Toggle between repeat options")}>
                </button>
            </div>
        </div>
    </div>
</div>
{#if volumeButtonPosition}
<div class="bottomContainer" in:slide={{duration: 300, easing: cubicInOut}} out:slide={{duration: 300, easing: cubicInOut}} style={`left: 15vw; bottom: calc(100vh - ${volumeButtonPosition.top}px); width: calc(70vw - 80px); height: fit-content;`}>
    <div class="flex hcenter gap">
        <div style="width: 100%;">
            <span>{lang("Change the volume level")}:</span>
            <div style="height: 10px;"></div>
            <InputRange events={[["input", (e) => {
                PlayAudio.volumeFilter.gain.value = +(e.target as HTMLInputElement).value;
            }]]} max={2} step={0.01} defaultValue={1}></InputRange>
        </div>
        <button class="emptyBtn" onclick={() => (volumeButtonPosition = undefined)}>
            <img class="icon" src={getIconSrc("dismiss")} alt={lang("Close dialog")}>
        </button>
    </div>
</div>
{/if}
{/if}

<style>
    .bottomContainer {
        bottom: 15px;
        border: 1px solid var(--text);
        width: calc(70vw - 15px);
        padding: 15px;
        border-radius: 16px;
        backdrop-filter: blur(8px) brightness(50%);
        position: fixed;
        left: 15vw;
        z-index: 10;
    }

</style>