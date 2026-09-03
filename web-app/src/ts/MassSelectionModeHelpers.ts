export default {
    /**
     * Update the styling of the passed item so that it's the same used for the "previously-selected" elements
     * @param element the element to update
     */
    updateElementStylingToPrevSelected(element: HTMLElement) {
        element.style.border = "3px dashed var(--text)";
        element.style.setProperty("--border-size", "3px");
    },
    /**
     * Get information about the elements between two DOM nodes
     * @param clickedItem the HTMLElement that was clicked
     * @param imageBtnsAvailable a Map that has as its key the song/image/file button elements
     * @param prevItemSelected the previously-selected items
     * @returns the array, sorted by DOM position, and the position of the previously-selected item and the currently-selected item. Note that the previously-selected item is always before the currently-selected item
     */
    getSelectedRange(clickedItem: HTMLElement, imageBtnsAvailable: Map<HTMLElement, any>, prevItemSelected: HTMLElement) {
        const mapArr = Array.from(imageBtnsAvailable).sort((a, b) => {
            const position = a[0].compareDocumentPosition(b[0]);
            if (position & Node.DOCUMENT_POSITION_FOLLOWING) return -1; 
            if (position & Node.DOCUMENT_POSITION_PRECEDING) return 1; 
            return 0;
        });
        let [currentElementIndex, prevItemIndex] = [mapArr.findIndex(i => i[0] === clickedItem), mapArr.findIndex(i => i[0] === prevItemSelected)];
        if (prevItemIndex > currentElementIndex) {
            let tempCurrent = currentElementIndex;
            currentElementIndex = prevItemIndex;
            prevItemIndex = tempCurrent;
        }
        return {mapArr, currentElementIndex, prevItemIndex}
    }
}