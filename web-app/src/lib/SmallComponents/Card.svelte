<script lang="ts">
    const {children, isSecondCard, applyMaxHeight}: {children: any, isSecondCard?: boolean, applyMaxHeight?: boolean} = $props();
</script>

<div class={`card${applyMaxHeight ? " maxHeight" : ""}`} style={`${isSecondCard ? "background-color: var(--secondcard); " : ""}`}>
    {@render children()}
</div>