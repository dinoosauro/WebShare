import { getIconSrc, type iconType } from "./IconManager";

interface Props {
    icon: iconType,
    type?: string
}

const obj = {
    /**
     * A list of all the HTMLImageElements that have an icon attached to
     */
    elements: new Map<HTMLImageElement, Props>(),
    /**
     * Make the image an icon, and register it so that it can be updated if the theme changes
     * @param element the image where the icon should be applied
     * @param type the icon type and color
     */
    register: (element: HTMLImageElement, type: Props) => {
        obj.elements.set(element, type);
        element.src = getIconSrc(type.icon, type.type);
        return {
            destroy: () => obj.elements.delete(element)
        }
    },
    /**
     * Change the color of the registered icons
     */
    regenerateIcons: () => {
        for (const [element, type] of obj.elements) {
            element.src = getIconSrc(type.icon, type.type);
        }
    }
}

export default obj;