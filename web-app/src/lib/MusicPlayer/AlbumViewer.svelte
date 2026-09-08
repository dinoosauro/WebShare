<script lang="ts">
    import { onMount } from "svelte";
    import convertNumberToStr from "../../ts/ConvertNumberToString";
    import type { AudioMetadata } from "../../ts/Interfaces/API";
    import PlayAudio from "../../ts/PlayAudio";
    import { getIconSrc } from "../../ts/IconManager";
    import lang from "../../ts/Lang";
    import checkIfColorIsDark from "../../ts/CheckIfColorIsDark";
    import RegenerateIcons from "../../ts/RegenerateIcons";

    const {name, songs, token, viewCallback, type, src}: {
        /**
         * The title of the album view
         */
        name: string, 
        /**
         * Information about the songs that should be displayed
         */
        songs: AudioMetadata[], 
        token: string, 
        /**
         * Function called when the album art has been loaded, so that an animation can be done from the MusicList component
         * @param img the album art image element
         * @param main the container of the AlbumViewer component
         */
        viewCallback: (img: HTMLImageElement, main: HTMLElement) => void, 
        /**
         * If the selected resource was the result of a division by album, by artist or by album artist
         */
        type: "album" | "artist" | "albumartist", 
        /**
         * The album art source
         */
        src: string
    } = $props();

    let main: HTMLElement;
    let img: HTMLImageElement;
    let isTransitionDone = false;

    onMount(() => {
        if (!window.history.state || window.history.state.openedContent !== name || window.history.state.contentType !== type) window.history.pushState({section: "songs", openedContent: name, contentType: type}, "");
        function keyboardEvent(e: KeyboardEvent) { // Permit to close the album viewer by pressing Escape
            if (e.code === "Escape") {
                window.removeEventListener("keydown", keyboardEvent);
                window.history.back();
            }
        }
        window.addEventListener("keydown", keyboardEvent);
        return () => {
            window.removeEventListener("keydown", keyboardEvent);
        }
    })
</script>



<div class="main opacity applyFilter" style="overflow: auto; opacity: 0" bind:this={main}>
    <img style="width: 100vw; height: 100vh; position: fixed; z-index: -1; filter: blur(32px); object-fit: cover" {src} alt={lang("Background album art")}>
    <button class="flex hcenter emptyBtn" style="padding: 5px; backdrop-filter: var(--transparency-filter); position: fixed; top: 15px; left: 15px; width: fit-content; border-radius: 50%; border: 1px solid var(--text)" onclick={() => {
        window.history.back();
    }}>
        <img use:RegenerateIcons.register={{icon: "arrowleft"}} alt={lang("Go back")} class="icon">
    </button>
    <div style={`overflow: auto; padding: 25px; ${!checkIfColorIsDark(getComputedStyle(document.body).getPropertyValue("--background")) ? "--filter: invert(1) hue-rotate(180deg)" : ""}`}>
        <div class="flex hcenter wcenter" style="margin-top: 10px;">
            <div>
                <img bind:this={img} style="max-width: 40vw; max-height: 40vh; border-radius: 12px;" alt="Album art" onload={async () => {
                    if (!isTransitionDone) {
                        // Avoid scrolling while the transition is being done
                        main.style.overflow = "hidden";
                        (main.querySelector("div") as HTMLDivElement).style.overflow = "hidden";
                        isTransitionDone = true;
                        img.style.opacity = "0";
                        await viewCallback(img, main);
                        // Restore scrolling functionality
                        main.style.overflow = "auto";
                        (main.querySelector("div") as HTMLDivElement).style.overflow = "auto";
                        // Make the image visibile and add a box shadow
                        img.style.opacity = "1";
                        const boxShadow = `-3px 2px 15px 9px #ffffff22`;
                        img.animate([{
                            boxShadow: ""
                        }, {
                            boxShadow
                        }], {duration: 800}).onfinish = () => (img.style.boxShadow = boxShadow);
                    }
                }} {src}>
            </div>
        </div>
        <h2 style="text-align: center;">{name}</h2>
        {#if type === "album"}
        <p style="color: var(--secondtext); text-align: center;">{songs[0].albumArtist ? songs[0].albumArtist : ""}{songs[0].albumArtist && songs[0].genre ? ` – ` : ""}{songs[0].genre ? songs[0].genre : ""}</p>
        {/if}
        <br>
        <div class="flex gap" style="flex-direction: column;">
            {#each songs as song (song.id)}
            <button class="emptyBtn" onclick={() => {
                PlayAudio.playAudio({info: song, token, queue: songs});
            }}>
                <div class="flex hcenter gap">
                    <p style="width: fit-content; width: 35px; text-align: left; color: var(--secondtext)">
                        {song.cdTrack}.
                    </p>
                    <div style="width: 100%; text-align: left;">
                        <p style="margin: 0;">{song.title || song.name}</p>
                        <p style="color: var(--secondtext); margin: 0">{song.artist}</p>
                    </div>
                    <p style="width: fit-content; margin-left: 15px; color: var(--secondtext)">
                        {convertNumberToStr((song.duration ?? 0) / 1000)}
                    </p>
                </div>
            </button>
            {/each}
        </div>
    </div>
</div>

<style>
    .applyFilter h2, .applyFilter h3, .applyFilter span, .applyFilter p {
        filter: var(--filter);
    }
</style>