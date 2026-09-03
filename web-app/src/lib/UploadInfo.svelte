<script lang="ts">
    import { onMount } from "svelte";
    import { type DownloadProps, type UploadProps } from "../ts/Interfaces/Website";
    import UploadFiles from "../ts/UploadFiles";
    import InputRange from "./SmallComponents/InputRange.svelte";
    import { slide } from "svelte/transition";
    import { cubicInOut } from "svelte/easing";
    import lang from "../ts/Lang";
    import StartPath from "../ts/StartPath";
    import { getIconSrc } from "../ts/IconManager";
    import FileSystemApiHelper from "../ts/FileSystemApiHelper";
    import SettingsObject from "../ts/Settings"
    const fileToUpload = $state<UploadProps[]>([]);
    const fileToDownload = $state<DownloadProps[]>([]);
    const {token}: {token: string | undefined} = $props();
    onMount(() => {
        UploadFiles._startUpload = async (files, retry) => {
            !retry && fileToUpload.push(...files);
            for (const file of files) {
                const req = new XMLHttpRequest();
                req.open("POST", `${StartPath}/api/upload?path=${encodeURIComponent(file.path)}${file.contentUri ? `&contentUri=${encodeURIComponent(file.contentUri)}` : ""}&lastModified=${file.file.lastModified}&replace=${SettingsObject.uploads.replaceFiles ? "1" : "0"}`);
                req.upload.onprogress = (e) => {
                    const data = fileToUpload.find(i => i.id === file.id);
                    if (data?.progress) {
                        data.progress.value = e.loaded.toString();
                        data.progress.dispatchEvent(new Event("input"));
                    }
                }
                req.setRequestHeader("Authorization", `Bearer ${token}`)
                await new Promise<void>(res => {
                    req.upload.onerror = (e) => {
                        console.warn(e);
                        file.isError = true;
                        res();
                    }
                    req.onerror = (e) => {
                        console.warn(e);
                        file.isError = true;
                        res();
                    }
                    req.onloadend = () => {
                        file.isError = !req.status.toString().startsWith("2");
                        res()
                    };
                    req.send(file.file);
                });
                const index = fileToUpload.findIndex(i => i.id === file.id);
                if (index !== -1) fileToUpload[index].isError = file.isError;
                if (index !== -1 && !file.isError) { fileToUpload.splice(index, 1);}
            }
            for (const callback of UploadFiles.refreshContent) callback();
        }
        FileSystemApiHelper._addProgress = (obj) => {
            fileToDownload.push(obj);
        }
        FileSystemApiHelper._updateProgress = (id, progress) => {
            const dataIndex = fileToDownload.findIndex(i => i.id === id);
            if (dataIndex === -1) return;
            fileToDownload[dataIndex].isError = progress === -1;
            if (fileToDownload[dataIndex].element) {
                fileToDownload[dataIndex].element.value = progress.toString();
                fileToDownload[dataIndex].element.dispatchEvent(new Event("input"));
            }
            if (progress === 1 && !fileToDownload[dataIndex].isError) fileToDownload.splice(dataIndex, 1);
        }
        
        return () => {
            UploadFiles._startUpload = null;
            FileSystemApiHelper._addProgress = null;
            FileSystemApiHelper._updateProgress = null;
        }
    })
</script>

{#if fileToUpload.length !== 0 || fileToDownload.length !== 0}
<div class="uploadDialog" in:slide={{duration: 400, easing: cubicInOut}} out:slide={{duration: 400, easing: cubicInOut}}>
{#if fileToUpload.length !== 0}
<h4>{lang("Uploading files")}:</h4>
    {#each fileToUpload as file (file.id)}
    <div class={file.isError ? "flex hcenter gap" : ""}>
        {#if !file.isError}
            <InputRange max={file.file.size} onRendered={(range) => (file.progress = range)} disabled={true}></InputRange>
        {/if}
        <p style="width: 100%;">
            {#if file.isError}
            <strong>{lang("Failed upload of")}:</strong>
            {/if}
            {file.file.webkitRelativePath || file.file.name}
        </p>
        {#if file.isError}
        <button class="emptyBtn" onclick={() => {
            UploadFiles._startUpload && UploadFiles._startUpload([file], true);
        }}>
            <img class="icon" src={getIconSrc("arrowclockwise")} alt={lang("Try again")}>
        </button>
        <button class="emptyBtn" onclick={() => {
            const index = fileToUpload.findIndex(i => i.id === file.id);
            if (index !== -1) fileToUpload.splice(index, 1);
        }}>
            <img class="icon" src={getIconSrc("dismiss")} alt={lang("Dismiss")}>
        </button>
        {/if}
    </div>
    {/each}
{/if}
{#if fileToDownload.length !== 0}
    <h4>{lang("Downloading files")}:</h4>
    {#each fileToDownload as file (file.id)}
    <div class={file.isError ? "flex hcenter gap" : ""}>
        {#if !file.isError}
            <InputRange max={1} step={0.001} onRendered={(range) => (file.element = range)} disabled={true}></InputRange>
        {/if}
        <p style="width: 100%;">
            {#if file.isError}
            <strong>{lang("Failed upload of")}:</strong>
            {/if}
            {file.name}
        </p>
        {#if file.isError}
        <button class="emptyBtn" onclick={() => {
            FileSystemApiHelper.pipeContent(file.url, file.name, file.file);
        }}>
            <img class="icon" src={getIconSrc("arrowclockwise")} alt={lang("Try again")}>
        </button>
        <button class="emptyBtn" onclick={() => {
            const index = fileToUpload.findIndex(i => i.id === file.id);
            if (index !== -1) fileToUpload.splice(index, 1);
        }}>
            <img class="icon" src={getIconSrc("dismiss")} alt={lang("Dismiss")}>
        </button>
        {/if}
    </div>
    {/each}

{/if}
</div>
{/if}

<style>
    .uploadDialog {
        top: 55px; 
        right: 15px; 
        width: 40vw; 
        position: fixed;
        max-height: 45vh; 
        overflow: auto; 
        padding: 15px; 
        backdrop-filter: blur(8px) brightness(40%);
        border-radius: 12px;
        border: 1px solid var(--text);
    }
</style>
