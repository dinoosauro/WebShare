export interface MediaInfo {
    id: number,
    name: string,
    dateModified: number,
    size: number,
    mimeType: string,
    dateTaken: number,
    dateAdded: number,
    width: number,
    height: number,
    relativePath: string,
    duration?: number
}

export interface FileMetadata {
    description: string,
    value: string
}

export interface Directory {
    path: string,
    isDirectory?: boolean,
    mimeType?: string,
    fileContentUri?: string,
    lastModified: number,
    canWrite: boolean,
    size: number
}

export interface DirectoryWrapper {
    files: Directory[],
    canWrite: boolean
}

export interface AvailableDirectories {
    directories: string[],
    availableContext: string[]
}

export interface AudioMetadata {
    id: number,
    name: string,
    dateModified: number,
    dateAdded: number,
    album?: string
    albumArtist?: string,
    bitrate?: number,
    composer?: string,
    cdTrack?: number,
    discNumber?: number,
    genre?: string,
    numTracks?: number,
    title?: string,
    track?: number,
    year?: number,
    albumId?: string,
    artist?: string,
    duration?: number
}

declare global {
    interface Window {
        showDirectoryPicker?: ({id, mode, startIn}: {id?: string, mode?: "read" | "readwrite", startIn?: "desktop" | "documents" | "downloads" | "music" | "pictures" | "videos"}) => Promise<FileSystemDirectoryHandle>
    }
}