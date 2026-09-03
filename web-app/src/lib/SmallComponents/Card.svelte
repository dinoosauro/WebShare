<script lang="ts">
    const {children, isSecondCard, applyMaxHeight}: {children: any, isSecondCard?: boolean, applyMaxHeight?: boolean} = $props();
</script>

<div class={`card`} style={`${isSecondCard ? "background-color: var(--secondcard); " : ""}${applyMaxHeight ? "height: calc(100% - 20px)" : ""}`}>
    {@render children()}
</div>