import type { iconType } from "../IconManager";
import type { AudioMetadata } from "./API";

export interface UploadProps {
    file: File, 
    path: string, 
    progress?: HTMLInputElement, 
    isError?: boolean, 
    id: number, 
    contentUri?: string
}

export interface DownloadProps  {
    id: number, 
    name: string, 
    isError?: boolean, 
    element?: HTMLInputElement, 
    url: string, 
    file: FileSystemFileHandle
}

export interface DropdownMenu {
    title: string,
    id: string,
    icon?: iconType,
    await?: boolean,
    extra: DropdownMenu[],
    keepOpen?: boolean,
    customImgStyling?: string
}

export interface CallbackProperties {
    info?: AudioMetadata,
    playing?: boolean,
    paused?: boolean,
    position?: number,
    duration?: number
}
