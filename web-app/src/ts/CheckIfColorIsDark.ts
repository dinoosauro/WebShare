/**
 * Check if a color is dark or not
 * @param hex the hex color to check
 * @returns true if the color is dark, false if the color is light
 */
export default function checkIfColorIsDark(hex: string) {
    let [r, g, b] = [parseInt(hex.substring(1, 3), 16), parseInt(hex.substring(3, 5), 16), parseInt(hex.substring(5), 16)];
    return (0.299 * r + 0.587 * g + 0.114 * b) <= 186;
}