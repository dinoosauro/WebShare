<script lang="ts">
    import { onMount } from "svelte";
    /**
     * The div that'll be colored with the range progress
     */
    let updateDiv: HTMLDivElement;
    let range: HTMLInputElement;

    const {events = [], max, onRendered, disabled, step, defaultValue}: {
        /**
         * A list of events to add to the input range. Follow the syntax `[event name, callback][]`
         */
        events?: ([string, (e: Event) => void])[], 
        max: number, 
        /**
         * Function to call when the Range is either created or destroyed. 
         * @param range the Range element
         */
        onRendered?: (range: HTMLInputElement | undefined) => void, 
        disabled?: boolean, 
        step?: number, 
        defaultValue?: string | number
    } = $props();
    onMount(() => {
        range.addEventListener("input", () => {
            updateDiv.style.setProperty("--progress", `${(+range.value / +range.max) * 100}%`);
        });
        if (typeof defaultValue !== "undefined") updateDiv.style.setProperty("--progress", `${(+range.value / +range.max) * 100}%`);
        for (const [name, event] of events) range.addEventListener(name, event);
        if (onRendered) onRendered(range);
        return () => {
            if (onRendered) onRendered(undefined);
        }
    })
</script>

<div class="range">
    <div bind:this={updateDiv}></div>
    <input {defaultValue} type="range" {step} {disabled} {max} bind:this={range}>
</div>

<style>
    .range {
        position: relative; 
        height: 18px;
        overflow: hidden;
        border-radius: 16px;
        width: 100%; 
    }
    input, .range {
        border-radius: 16px; 
        padding: 0;
        margin: 0px;
        transition: 0.2s ease-in-out;
    }
    input {
        appearance: none;
        top: 0;
        left: 0;
        height: 15px;
        width: calc(100% - 2px);
    }
    input, .range > div {
        position: absolute;
        border-radius: 16px; 
    }
    .range > div {
        width: var(--progress);
        background-color: var(--accent);
        z-index: 2;
        height: 15px;
        top: 1px;
        left: 1px;
        pointer-events: none;
    }
    input[type=range]::-webkit-slider-thumb {
        opacity: 0;
    }
    input[type=range]::-moz-range-thumb {
        opacity: 0;
    }
</style>

{#if !disabled}
<style>
    .range:hover {
        transform: scaleY(1.5);
    }
</style>
{/if}