import Settings from "./Settings";

/**
 * A map that ties every fallback album art ID (name-color) as the blob URL to use.
 */
const alreadyCreatedMap = new Map<string, string>([]);

/**
 * Create a SVG image with the first letters of the passed string.
 * @param name the name to use in the album art
 * @returns the image URL of the created SVG image
 */
export default function CreateAlbumArt(name: string) {
    name = name.split(" ").slice(0, 2).map(i => i[0]).join("");
    const backgroundColor = Settings.customArtColors[Math.floor(Math.random() * Settings.customArtColors.length)];
    const id = `${name}-${backgroundColor}`;
    const href = alreadyCreatedMap.get(id);
    if (href) return href;
    // Create a fallback SVG with a random color and `name` written at the center
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    for (const prop of [["width", "400"], ["height", "400"], ["viewBox", "0 0 400 400"], ["xmlns", "http://www.w3.org/2000/svg"]]) svg.setAttribute(prop[0], prop[1]);
    const background = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    for (const prop of [["width", "400"], ["height", "400"], ["fill", backgroundColor]]) background.setAttribute(prop[0], prop[1]);
    const text = document.createElementNS("http://www.w3.org/2000/svg", "text");
    for (const prop of [["x", "50%"], ["y", "50%"], ["dominant-baseline", "middle"], ["text-anchor", "middle"], ["fill", "white"], ["font-size", "200"], ["font-family", "Work Sans, Arial, sans-serif"], ["alignment-baseline", "central"]]) text.setAttribute(prop[0], prop[1]);
    text.textContent = name;
    // Append elements to SVG
    svg.appendChild(background);
    svg.appendChild(text);
    const blobUrl = URL.createObjectURL(new Blob([svg.outerHTML], {type: "image/svg+xml"}));
    alreadyCreatedMap.set(id, blobUrl);
    return blobUrl;
}