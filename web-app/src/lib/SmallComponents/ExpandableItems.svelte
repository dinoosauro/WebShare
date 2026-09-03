<script lang="ts">
    import { cubicInOut } from "svelte/easing";
    import { slide } from "svelte/transition";

    let {title, children, isOpened, size, callback}: {title: string, size: string, children: any, isOpened: boolean, callback?: (opened: boolean) => void} = $props();
</script>

<div>
    <button class="emptyBtn flex hcenter" style="width: 100%" onclick={() => {
        isOpened = !isOpened;
        callback && callback(isOpened);
    }}>
        {#if size === "h3"}
        <h3 style="width: 100%;">{title}</h3>
        {:else}
        <p style={`width: 100%; font-size: ${size}`}>{title}</p>
        {/if}
        <span style={`font-family: system-ui; transition: transform 0.2s ease-in-out; font-size: ${size}; ${isOpened ? "transform: rotate(-90deg)" : ""}`}>◀</span>
    </button>
    {#if isOpened}
    <div in:slide={{duration: 200, easing: cubicInOut}} out:slide={{duration: 200, easing: cubicInOut}}>
        {@render children()}
    </div>
    {/if}
</div>

<style>
    h3, p {
        text-align: left;
    }
</style>