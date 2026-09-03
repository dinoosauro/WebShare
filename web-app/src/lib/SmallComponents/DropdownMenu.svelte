<script lang="ts">
    import { onMount } from "svelte";
    import { getIconSrc } from "../../ts/IconManager";
    import type { DropdownMenu } from "../../ts/Interfaces/Website";
    import topBtnContainerTransition from "../../ts/TopButtonsTransition";

    const {dropdownInfo, callback, getContainer}: {
        /**
         * All the elements to add in the dropdown menu
         */
        dropdownInfo: DropdownMenu[], 
        /**
         * Function called when the user clicks on a dropdown entry
         * @param id the id of the clicked button
         */
        callback: (id: string) => void, 
        /**
         * A function that returns the container of the top-right buttons
         * @param container
         */
        getContainer?: (container?: HTMLElement) => void
    } = $props();

    let topBtnContainer: HTMLElement;

    let selectedElement: DropdownMenu[] | undefined = $state();

    async function btnCallback(dropdownItem: DropdownMenu) {
        if (dropdownItem.keepOpen) {
            callback(dropdownItem.id);
            return;
        }
        const manualTriggerTransition = typeof selectedElement === "undefined" && dropdownItem.extra.length === 0;
        if (dropdownItem.await) {
            await topBtnContainerTransition(topBtnContainer, true); 
            selectedElement = dropdownItem.extra.length !== 0 ? dropdownItem.extra : undefined;
            if (manualTriggerTransition) topBtnContainerTransition(topBtnContainer);
        } else {
            topBtnContainerTransition(topBtnContainer, true).then(() => {
                selectedElement = dropdownItem.extra.length !== 0 ? dropdownItem.extra : undefined;
                if (manualTriggerTransition) topBtnContainerTransition(topBtnContainer);
            });
        }
        callback(dropdownItem.id);
    }

    $effect(() => {
        function uselessWrapper(_?: any) { // So that the effect is run every time selectedElement changes
            topBtnContainerTransition(topBtnContainer);
        }
        uselessWrapper(selectedElement);
    })

    onMount(() => {
        getContainer && getContainer(topBtnContainer);
        return () => {
            getContainer && getContainer(undefined);
        }
    })
</script>

<div class="topBtnContainer" bind:this={topBtnContainer}>
    {#if typeof selectedElement === "undefined"}
    <div class="flex hcenter" style="gap: 5px">
        {#each dropdownInfo as dropdownItem}
            <button class="emptyBtn flex hcenter" title={dropdownItem.title} onclick={() => btnCallback(dropdownItem)}>
            {#if dropdownItem.icon}
                <img style={dropdownItem.customImgStyling} class="icon" src={getIconSrc(dropdownItem.icon)} alt={dropdownItem.title}>
            {/if}
            </button>

        {/each}
    </div>
    {:else}
        {#each selectedElement as element}
        <button class="emptyBtn flex hcenter gap verticalBtn" title={element.title} onclick={() => btnCallback(element)}>
            {#if element.icon}
                <img style={element.customImgStyling} class="icon" src={getIconSrc(element.icon)} alt={element.title}>
            {/if}
            <p>{element.title}</p>
        </button>
        {/each}
    {/if}
</div>

{#if selectedElement}
<button title="Close dialog" style="z-index: 4; width: 100vw; height: 100vh; position: fixed; left: 0; top: 0" class="emptyBtn" onclick={async () => {
    await topBtnContainerTransition(topBtnContainer, true);
    selectedElement = undefined;
}}></button>
{/if}

<style>
    .verticalBtn {
        padding: 0px 10px; 
        margin: 10px; 
        border-radius: 12px;
        position: relative;
        transition: background-color 0.2s ease-in-out;
        width: 100%;
        width: -webkit-fill-available;
        width: -moz-available;
    }
    .verticalBtn:hover {
        background-color: rgb(from var(--secondcard) r g b / 50%)
    }
</style>