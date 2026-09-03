<script lang="ts">
    // This component is used so that a function (`callback`) can be called before an item is visible. It works by creating a div that is translated 100px above the component position, and every time the user scrolls the page the div's position is checked
    import { onMount } from "svelte";
    const {callback}: {callback: () => void} = $props();
    let main: HTMLDivElement;
    onMount(() => {
        function scrollEvent() {
            if (main.getBoundingClientRect().top < window.innerHeight) callback();
        }
        window.addEventListener("scroll", scrollEvent);
        return () => window.removeEventListener("scroll", scrollEvent);
    })
</script>

<div bind:this={main} style="pointer-events: none; transform: translateY(-100px); height: 100vh; width: 100px;"></div>