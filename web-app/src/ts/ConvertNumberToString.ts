/**
 * Get a HH:MM:SS string from a number
 * @param num the seconds to convert
 * @returns the formatted string
 */
export default function convertNumberToStr(num: number) {
    const hours = Math.floor(num / 3600);
    num -= hours * 3600;
    const minutes = Math.floor(num / 60);
    num -= minutes * 60;
    function formatNum(num: number) {
        if (num < 10) return `0${num}`;
        return num.toString();
    }
    return `${hours === 0 ? "" : `${formatNum(hours)}:`}${formatNum(minutes)}:${formatNum(Math.floor(num))}`;
}
