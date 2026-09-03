/**
 * Transition done when clicking on an image causes the opening of a fullscreen dialog.
 * @param sourceImage the image where the animation will start
 * @param destionationImage the image where the animation will end
 * @param destinationContainer the main container of the view that'll be set as visible
 * @param isClosing if the animation should be reversed, since it's a closing animation and not an opening animation
 * @param isFromMusicTab put `true` if it's being called from the audio player tab: the border-radius will be forced, and the object fit won't be set to contain
 */
export default async function imageOpenTransition(sourceImage: HTMLImageElement, destionationImage: HTMLImageElement, destinationContainer: HTMLElement, isClosing?: boolean, isFromMusicTab?: boolean) {
    document.body.style.overflow = "hidden";
    const rect = sourceImage.getBoundingClientRect();
    const newImage = sourceImage.cloneNode() as HTMLImageElement;
    await new Promise<void>(res => { // We first need to load the image, otherwise the transition will be buggy. We shoulnd't have a lot of issues, since it's cached.
        newImage.onload = () => res();
        newImage.style.zIndex = "3";
        newImage.style.position = "fixed";
        if (!isFromMusicTab) newImage.style.objectFit = "contain";
        for (const prop of ["top", "left", "width", "height"]) newImage.style[prop as "top"] = `${rect[prop as "top"]}px`;
        document.body.append(newImage);
    })
    destinationContainer.style.opacity = isClosing ? "0" : "1";
    const destinationRect = destionationImage.getBoundingClientRect();
    let outputStyle = {
        top: `${destinationRect.top}px`,
        left: `${destinationRect.left}px`,
        borderRadius: isClosing || isFromMusicTab ? "12px" : "0px",
        width: `${destinationRect.width}px`,
        height: `${destinationRect.height}px`
    }
    await new Promise<void>((res) => newImage.animate([{
        borderRadius: isClosing && !isFromMusicTab ? "0px" : "12px",
        width: `${rect.width}px`,
        height: `${rect.height}px`,
    }, outputStyle], { duration: 300, easing: "ease-in-out" }).addEventListener("finish", () => res()));
    for (const prop in outputStyle) newImage.style[prop as "top"] = outputStyle[prop as "top"];
    if (!isClosing) {
        destionationImage.style.opacity = "1";
    }
    await new Promise((res) => setTimeout(res, 210));
    newImage.remove();
    document.body.style.overflow = "";
}