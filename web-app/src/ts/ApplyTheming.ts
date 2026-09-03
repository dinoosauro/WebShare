import checkIfColorIsDark from "./CheckIfColorIsDark";
import { getIconSrc } from "./IconManager";
import Settings from "./Settings";
import StartPath from "./StartPath";

/**
 * Apply the theming that was saved from the Settings
 */
export default async function ApplyTheming() {
    for (const key of document.body.style) {
        if (key.startsWith("--") && document.body.style.getPropertyValue(key).startsWith("#")) document.body.style.removeProperty(key);
    }
    if (Settings.theme.fetchFromDevice) {
        const req = await fetch(`${StartPath}/api/colors`);
        const json = await req.json();
        for (const key in json) document.body.style.setProperty(`--${key}`, json[key]);
    } else {
        for (const property in Settings.theme.colors) {
            document.body.style.setProperty(`--${property}`, Settings.theme.colors[property]);
        }
    }
    if (!checkIfColorIsDark(getComputedStyle(document.body).getPropertyValue("--background"))) document.body.style.setProperty("--transparency-filter", "brightness(150%) blur(8px)");
    const icon = await fetch(getIconSrc("globesync", getComputedStyle(document.body).getPropertyValue("--accent")));
    (document.querySelector("link[rel=icon]") as HTMLLinkElement).href = `data:image/svg+xml;base64,${btoa(await icon.text())}`;
}