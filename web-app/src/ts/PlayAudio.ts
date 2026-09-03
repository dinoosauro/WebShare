import type { AudioMetadata } from "./Interfaces/API"
import type { CallbackProperties } from "./Interfaces/Website";
    import StartPath from "./StartPath";

interface PlayAudioProps {
    info: AudioMetadata,
    token: string,
    queue?: AudioMetadata[],
    play?: boolean
}

/**
 * The token that was previously used for audio playback
 */
let tokenStorage = "";

let audio = new Audio();
audio.crossOrigin = "anonymous";

const obj = {
    /**
     * The Audio element used for playback
     */
    audio,
    /**
     * A list of functions that'll be called when something in the Audio changes (ex: time updates, play/pause updates, end updates etc.)
     */
    callbacks: new Set<((info: CallbackProperties) => void)>(),
    /**
     * List of all the songs added in the queue
     */
    queue: [] as AudioMetadata[],
    prevElements: [] as number[],
    repeat: "no" as "no" | "yes" | "single",
    /**
     * Identificator of the current audio playback action. A new ID is generated every time the user manually starts a new music playback.
     */
    currentReproductionId: Math.random(),
    shuffle: false,
    /**
     * Playing position in the queue
     */
    queuePosition: 0,
    /**
     * The GainNode that permits to change the volume of the played track
     */
    volumeFilter: (() => {
        const context = new AudioContext();
        const source = context.createMediaElementSource(audio);
        const gain = context.createGain();
        source.connect(gain).connect(context.destination);
        return gain;
    })(),
    /**
     * Play an audio file
     */
    playAudio: function({info, token, queue, play = true}: PlayAudioProps) {
        tokenStorage = token;
        obj.audio.src = `${StartPath}/api/download?id=${info.id}&mimetype=${encodeURIComponent("audio/mpeg")}&name=${encodeURIComponent(info.name)}&token=${encodeURIComponent(token)}`;
        if (play) obj.audio.play();
        if (queue) {
            obj.queue = [];
            obj.prevElements = [];
            const index = queue.findIndex(i => i.id === info.id);
            if (index !== -1) obj.queue = [...queue.slice(index), ...queue.slice(0, index)];
            obj.currentReproductionId = Math.random();
        }
        for (const callback of obj.callbacks) try {callback({info});} catch(ex) {console.warn(ex)};
    },
    /**
     * Skip to the next song
     */
    next: function() {
        obj.prevElements.push(obj.queuePosition);
        if (obj.repeat === "single") {
            obj.audio.currentTime = 0;
            obj.audio.play();
            return;
        } 
        obj.queuePosition = obj.shuffle ? Math.floor(Math.random() * obj.queue.length) : obj.queuePosition + 1;
        const restartQueue = obj.queuePosition >= obj.queue.length;
        if (restartQueue) obj.queuePosition = 0;
        obj.playAudio({info: obj.queue[obj.queuePosition], token: tokenStorage, play: !(restartQueue && obj.repeat === "no")});
    },
    /**
     * Go back to the previous song
     */
    prev: function() {
        if (obj.repeat === "single" || obj.audio.currentTime > 5) {
            obj.audio.currentTime = 0;
            return;
        }
        const prevItem = obj.prevElements.pop();
        if (typeof prevItem !== "undefined") {
            obj.queuePosition = prevItem;
        } else if (obj.shuffle) {
            obj.audio.currentTime = 0;
            return;
        } else obj.queuePosition--;
        if (obj.queuePosition < 0) obj.queuePosition = obj.repeat === "no" ? 0 : obj.queue.length - 1;
        obj.playAudio({info: obj.queue[obj.queuePosition], token: tokenStorage});
    }
}
obj.audio.addEventListener("play", () => {
    for (const callback of obj.callbacks) callback({playing: true});
})
obj.audio.addEventListener("timeupdate",  () => {
    for (const callback of obj.callbacks) callback({position: obj.audio.currentTime, duration: obj.audio.duration});
})
obj.audio.addEventListener("pause", () => {
    for (const callback of obj.callbacks) callback({paused: true});
})
obj.audio.addEventListener("ended", () => {
    obj.next();
})

export default obj;