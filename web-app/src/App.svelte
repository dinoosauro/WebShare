<script lang="ts">
  let authCode = "";
  let token: string | undefined;
  import {Hash} from "fast-sha256";
    import PhotoViewer from "./lib/PhotoViewer/PictureList.svelte";
    import UploadInfo from "./lib/UploadInfo.svelte";
    import { onMount } from "svelte";
    import Settings from "./ts/Settings";
    import { slide } from "svelte/transition";
    import { cubicInOut } from "svelte/easing";
    import FileViewer from "./lib/FileViewer/FileViewer.svelte";
    import MusicList from "./lib/MusicPlayer/MusicList.svelte";
    import PopupPlayer from "./lib/MusicPlayer/PopupPlayer.svelte";
    import { createEmptySpace } from "./ts/AddEmptySpace";
    import lang from "./ts/Lang";
    import StartPath from "./ts/StartPath";
  let loginBtn: HTMLButtonElement | undefined;

  /**
   * Function used to authenticate to the server
   */
  async function loginFn() {
    if (loginBtn) loginBtn.disabled = true;
   const hash = new Hash();
   hash.update(new TextEncoder().encode(authCode));
   const req = await fetch(`${StartPath}/api/authenticate`, {
      headers: {
        authorization: `Basic ${Array.from(hash.digest()).map(b => b.toString(16).padStart(2, '0')).join('')}`
      }
    });
    if (req.ok) {
      token = await req.text();
      localStorage.setItem("WebShare-Token", token);
      if (typeof selectedSection === "undefined") selectedSection = Settings.defaultSection as "photos";
    } else alert("The authorization code you provided isn't valid");
    if (loginBtn) loginBtn.disabled = false;
  }
  onMount(async () => {
    document.body.style.setProperty("--picture-height", `${Settings.photoViewer.defaultPhotoHeight}px`);
    window.history.scrollRestoration = "manual";
    const possibleToken = localStorage.getItem("WebShare-Token");
    if (possibleToken) {
      const req = await fetch(`${StartPath}/api/check`, {
        headers: {
          Authorization: `Bearer ${possibleToken}`
        }
      });
      if (req.ok) {
        token = possibleToken;
        if (typeof selectedSection === "undefined") selectedSection = (window.history.state?.section || Settings.defaultSection) as "photos";
      } else {localStorage.removeItem("WebShare-Token"); window.history.replaceState({}, "");}
    } else {localStorage.removeItem("WebShare-Token"); window.history.replaceState({}, "");}
    window.addEventListener("popstate", (e) => {
      if (e.state?.section) selectedSection = e.state?.section;
      console.log(e.state?.section, selectedSection);
    })
  })

  let selectedSection: "albums" | "photos" | "files" | "songs" | undefined = $state();
</script>

<h1>WebShare</h1>
{#if typeof selectedSection === "undefined" || !token}
<p>{lang("Welcome to WebShare. To log in and see all the files on your device, please write the code you can obtain from the \"Get authentication code\" button from your phone")}.</p>
<label class="flex hcenter gap">
    <input type="text" autofocus onkeyup={(e) => {
      if (e.key === "Enter") loginFn();
    }} bind:value={authCode}>
    <button bind:this={loginBtn} style="width: fit-content;" onclick={loginFn}>{lang("Login")}</button>
  </label>
{:else}
  <h2 class="flex hcenter gap" in:slide={{duration: 400, easing: cubicInOut, delay: 500}} out:slide={{duration: 400, easing: cubicInOut}}><span style="white-space: nowrap">{lang(`Your${selectedSection === "photos" || selectedSection === "songs" ? " F" : ""}`)}</span> <select bind:value={selectedSection}>
    <option value="photos">{lang("photos and videos")}</option>
    <option value="albums">{lang("albums")}</option>
    <option value="songs">{lang("songs")}</option>
    <option value="files">{lang("files")}</option>
  </select></h2>
{#if selectedSection === "photos" || selectedSection === "albums"}
  <PhotoViewer albumView={selectedSection === "albums"} token={token as string}></PhotoViewer>
{:else if selectedSection === "songs"}
  <MusicList token={token as string}></MusicList>
{:else}
  <FileViewer token={token as string} ></FileViewer>
{/if}
<PopupPlayer token={token as string}></PopupPlayer>
<div use:createEmptySpace></div>
{/if}


<UploadInfo {token}></UploadInfo>