/**
 * The pop-up player element
 */
let floatingItem: HTMLElement | undefined;
/**
 * A list of all the divs whose height should be updated to compensate with the floating player
 */
let divsToUpdate = new Set<HTMLElement>();

const observer = new ResizeObserver(() => {
    if (!floatingItem) return;
    for (const div of divsToUpdate) div.style.height = `${floatingItem.getBoundingClientRect().height + 15}px`;
})

/**
 * Register the passed div as an empty space, that can be used so that, if the floating player is visible, it'll still be possible to scroll to the bottom of the webpage
 * @param div the div to register as an empty space
 */
export function createEmptySpace(div: HTMLElement) {
    divsToUpdate.add(div);
    if (floatingItem) div.style.height = `${floatingItem.getBoundingClientRect().height + 15}px`;
    return {
        destroy: () => divsToUpdate.delete(div)
    }
}

/**
 * Register an element as the floating player, so that its height can be compensated.
 * @param component the component to register
 */
export function floatingComponent(component: HTMLElement) {
    floatingItem = component;
    observer.disconnect();
    observer.observe(component);
    return {
        destroy: () => {
            if (component === floatingItem) {
                floatingItem = undefined;
                for (const div of divsToUpdate) div.style.height = `0px`;
            }
        }
    }
}