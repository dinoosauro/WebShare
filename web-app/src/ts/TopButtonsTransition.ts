/**
 * Create the animation used to show the buttons in the top-right corner of the application
 * @param topBtnContainer the container of the top-right buttons
 * @param invert if the animation should be inverted or not
 */
export default function topBtnContainerTransition(topBtnContainer: HTMLElement, invert?: boolean) {
    const obj = [{
        transform: `scale(0)`,
        transformOrigin: "top right",
        overflow: "hidden"
    }, {
        overflow: "hidden",
        transformOrigin: "top right",
        transform: "scale(1.15)"
    }, {
        transform: "scale(1)",
        transformOrigin: "top right"
    }];
    return new Promise(res => {
        topBtnContainer.animate(invert ? obj.reverse() : obj, { duration: 450, easing: "ease-in-out" }).addEventListener("finish", res);
    })
}
