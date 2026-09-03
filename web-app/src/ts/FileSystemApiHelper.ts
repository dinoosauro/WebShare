import type { DownloadProps } from "./Interfaces/Website";

const obj = {
    /**
     * Update the progress of an ongoing file download operation
     */
    _updateProgress: null as null | ((id: number, progress: number) => void),
    /**
     * Add a new DownloadProps object to the list of items that are being downloaded
     */
    _addProgress: null as null | ((obj: DownloadProps) => void),
    /**
     * Write the body of a fetch request to the user's drive
     * @param path the URL to fetch
     * @param name the name that should be displayed in the top-right corner
     * @param file the FileSystemFileHandle where the response will be written
     */
    pipeContent: async (path: string, name: string, file: FileSystemFileHandle) => {
        const req = await fetch(path);
        let object = {
            id: Math.random(),
            name,
            progress: !req.ok || !req.body ? -1 : 0,
            url: path,
            file
        }
        obj._addProgress && obj._addProgress(object);
        if (req.body && req.ok) {
            const writable = await file.createWritable();
            const contentLength = +(req.headers.get("Content-Length") ?? "0") || 0;
            const reader = req.body.getReader();
            let received = 0;
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                await writable.write(value);
                received += value.length;
                if (contentLength !== 0) obj._updateProgress && obj._updateProgress(object.id, received / contentLength);
            }
            await writable.close();
            obj._updateProgress && obj._updateProgress(object.id, 1);
        }
    }
}

export default obj;