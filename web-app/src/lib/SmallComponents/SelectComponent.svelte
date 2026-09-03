<script lang="ts">
    import { cubicInOut } from "svelte/easing";
    import { fade } from "svelte/transition";
    import { getIconSrc } from "../../ts/IconManager";
    import lang from "../../ts/Lang";
    import { onMount } from "svelte";
    let {isMassSelectionEnabled, changeMassSelectionCallback, downloadCallback, deleteCallback, closeCallback}: {
        isMassSelectionEnabled: boolean,
        changeMassSelectionCallback: (e: boolean) => void
        downloadCallback: (e: Event) => void,
        deleteCallback: (e: Event) => void,
        closeCallback: (e: Event) => void
    } = $props();
    onMount(() => {
        function keydownEvent(e: KeyboardEvent) {
            if (e.key === "Shift") changeMassSelectionCallback(true);
        }
        function keyupEvent(e: KeyboardEvent) {
            if (e.key === "Shift") changeMassSelectionCallback(false);
        }
        window.addEventListener("keydown", keydownEvent);
        window.addEventListener("keyup", keyupEvent);
        return () => {
            window.removeEventListener("keydown", keydownEvent);
            window.removeEventListener("keyup", keyupEvent);
        }
    })
</script>

<div class="bottomDialog" in:fade={{duration: 200, easing: cubicInOut}} out:fade={{duration: 200, easing: cubicInOut}}>
    <div class="flex hcenter gap">
        <p style="width: 100%;"><strong>{lang(`${isMassSelectionEnabled ? "Mass select" : "Select"} mode enabled`)}:</strong> {isMassSelectionEnabled ? lang("click two items, and all the elements between them will be selected") : lang("click on an item to select it")}.</p>
        <button class="emptyBtn flex hcenter" onclick={() => changeMassSelectionCallback(!isMassSelectionEnabled)}>
            <img class="icon" src={getIconSrc(isMassSelectionEnabled ? "selectalloff" : "selectallon")} alt={lang("Toggle mass selection mode")}>
        </button>
        <button class="emptyBtn flex hcenter" onclick={downloadCallback}>
            <img class="icon" src={getIconSrc("arrowdownload")} alt={lang("Download files")}>
        </button>
        <button class="emptyBtn flex hcenter" onclick={deleteCallback}>
            <img class="icon" src={getIconSrc("delete")} alt={lang("Delete files")}>
        </button>
        <button class="emptyBtn flex hcenter" onclick={closeCallback}>
            <img class="icon" src={getIconSrc("dismiss")} alt={lang("Disable select mode")}>
        </button>
    </div>
</div>
