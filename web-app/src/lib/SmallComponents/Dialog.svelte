<script lang="ts">
    import { cubicInOut } from "svelte/easing";
    import { fade } from "svelte/transition";
    import { getIconSrc } from "../../ts/IconManager";
    import appendToBody from "../../ts/AppendToBody";
    import { onMount } from "svelte";

    const {children, callback}: {children: any, callback: () => void} = $props();

</script>

<div class="dialog" use:appendToBody in:fade={{duration: 200, easing: cubicInOut}} out:fade={{duration: 200, easing: cubicInOut}}>
    <div class="dialogBack">
        <button class="emptyBtn flex hcenter" onclick={() => callback()}>
            <img class="icon" src={getIconSrc("dismiss")} alt="Close dialog">
        </button>
    </div>
    <div class="dialogContent">
        {@render children()}
    </div>
</div>

<style>
    .dialog {
        width: 100vw;
        height: 100vh;
        top: 0;
        left: 0;
        position: fixed;
        z-index: 5;
        backdrop-filter: blur(8px) brightness(50%);
    }
    .dialog > .dialogContent {
        position: fixed;
        top: calc(10vh - 15px);
        padding: 15px;
        background-color: var(--card);
        left: calc(15vw - 15px);
        width: 70vw;
        height: 80vh;
        overflow: auto;
        border-radius: 12px;
    }
    .dialog > .dialogBack {
        position: fixed; 
        top: 10vh; 
        right: calc(15vw);
        width: fit-content; 
        backdrop-filter: blur(8px) brightness(50%); 
        padding: 5px; 
        border-radius: 50%; 
        border: 1px solid var(--text);
        z-index: 2;
    }
</style>