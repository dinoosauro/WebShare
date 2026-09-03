/**
 * List all the dropped files by keeping their relative path
 * @param list the array of FileSystemEntries got from the drop event
 * @param path the starting path of the current folder
 * @returns a list of files, with their File object and their relative path
 */
export default async function ReadDroppedFiles(list: FileSystemEntry[], path = "") {
    const output: {file: File, path: string}[] = [];
    for (const entry of list) {
        if (entry.isDirectory) {
            await new Promise<void>((res) => {
                (entry as FileSystemDirectoryEntry).createReader().readEntries(async (entries) => {
                    output.push(...await ReadDroppedFiles(entries, `${path}${entry.name}/`));
                    res();
                }, (err) => {
                    console.warn(err);
                    res();
                })
            })
        } else if (entry.isFile) {
            await new Promise<void>(res => {
                (entry as FileSystemFileEntry).file((file) => {
                    output.push({file, path: `${path}${file.name}`});
                    res();
                }, (err) => {
                    console.warn(err);
                    res();
                })
            })
        }
    }
    return output;
}