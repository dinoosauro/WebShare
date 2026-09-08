<script lang="ts">
    import { onMount } from "svelte";
    import { getIconSrc } from "../../ts/IconManager";
    import convertNumberToStr from "../../ts/ConvertNumberToString";
    import RegenerateIcons from "../../ts/RegenerateIcons";
    import lang from "../../ts/Lang";

    let main: HTMLDivElement;
    const {imagePreviewUrl, name, suggestedProportion, useHeightProportion, duration, imageBackgroundColor, customErrorEvent, isFavorite}: {
        /**
         * The URL that'll be loaded if the element is visible
         */
        imagePreviewUrl: string, 
        /**
         * Alt description of the image
         */
        name: string, 
        /**
         * The result of width / height, so that the application knows how much width to keep before loading the image
         */
        suggestedProportion?: number, 
        /**
         * If the proportion should be height / width instead of width / height. This will also limit the width, and not the height, of the image. 
         */
        useHeightProportion?: boolean, 
        /**
         * If the content is a video, its duration in seconds. If no duration is available, put `true` to display only the play icon.
         */
        duration?: number | true, 
        /**
         * Apply a custom background color to the image
         */
        imageBackgroundColor?: string, 
        /**
         * Function to call when the server returns an error while loading the image instead of the standard one (that tries to load it again after 1 second)
         */
        customErrorEvent?: (e: Event) => void,
        /**
         * If the image has been marked as favorite by the user
         */
        isFavorite?: boolean
    } = $props();
    /**
     * If the image should be shown or not
     */
    let showContent = $state(false);
    onMount(() => {
        const rect = main.getBoundingClientRect();
        showContent = rect.top < window.innerHeight && rect.bottom > 0 && rect.left < window.innerWidth && rect.right > 0;
        const observer = new IntersectionObserver((changes) => {
            showContent = changes.some(i => i.isIntersecting);
        });
        observer.observe(main);
        return () => observer.disconnect();
    })
</script>

<div bind:this={main} style={`pointer-events: none; --width-proportion: ${suggestedProportion ?? (useHeightProportion ? "1.333" : "0.75")}; position: relative; overflow: hidden`}>
    {#if showContent}
        <img alt={name} loading="lazy" src={imagePreviewUrl} onload={(e) => {
            const image = e.target as HTMLImageElement;
            if (!main) return;
            main.style.setProperty("--width-proportion", useHeightProportion ? (image.naturalHeight / image.naturalWidth).toString() : (image.naturalWidth / image.naturalHeight).toString());
            image.style[useHeightProportion ? "height" : "width"] = "";
        }} onerror={(e) => {
            if (customErrorEvent) {
                customErrorEvent(e);
                return;
            }
            if ((e.target as HTMLImageElement).src  === "" || (e.target as HTMLImageElement).src === window.location.origin || (e.target as HTMLImageElement).src === `${window.location.origin}/`) return;
            (e.target as HTMLImageElement).src = "";
            setTimeout(() => ((e.target as HTMLImageElement).src = imagePreviewUrl), 1000);
        }} style={`${useHeightProportion ? "width" : "height"}: calc(var(--picture-height) - var(--border-size) - var(--border-size)); object-fit: cover; border-radius: 12px; pointer-events: none; ${useHeightProportion ? "height" : "width"}: calc(var(--picture-height) * var(--width-proportion));${imageBackgroundColor ? ` background-color: ${imageBackgroundColor};` : ""}`}>
        {#if duration}
        <div style="bottom: 5px;" class="flex hcenter gap hoverImg">
            <img use:RegenerateIcons.register={{icon: "play"}} alt={lang("Video")}>
            {#if typeof duration === "number"}
                <span>{convertNumberToStr(duration)}</span>
            {/if}
        </div>
        {/if}
        {#if isFavorite}
        <div style="top: 5px;" class="flex hcenter gap hoverImg">
            <img use:RegenerateIcons.register={{icon: "star"}} alt={lang("Is favorite")}>
        </div>
        {/if}
    {:else}
        <div style={`${useHeightProportion ? "width" : "height"}: var(--picture-height); ${useHeightProportion ? "height" : "width"}: calc(var(--picture-height) * var(--width-proportion))`}></div>
    {/if}
</div>

<style>
    .hoverImg {
        left: 5px; 
        backdrop-filter: var(--transparency-filter); 
        padding: 10px; 
        border-radius: 12px;
        position: absolute;
    }
</style>