import { BroadcastRenderer } from "./broadcast/renderer.js";
import { connectTwitchChat } from "./broadcast/twitchChat.js";
import { setUpElectronWindowFrame, setUpElectronWindowResizeListener } from "./electron.js";

const canvas = document.getElementById("broadcast-canvas");
const scriptLayer = document.getElementById("broadcast-script-layer");
setUpElectronWindowFrame();

const renderer = new BroadcastRenderer({ canvas, scriptLayer, broadcaster, showToast });
fetch(`/api/twitch/emotes?channel=${encodeURIComponent(broadcaster)}`)
    .then((response) => {
        if (!response.ok) {
            throw new Error("Failed to load Twitch emotes");
        }
        return response.json();
    })
    .then((catalog) => renderer.setEmoteCatalog(catalog))
    .catch((error) => console.warn("Unable to load Twitch emotes", error));
const disconnectChat = connectTwitchChat(
    broadcaster,
    ({ channel, displayName, message, tags, prefix, raw }) => {
        console.log(`[twitch:${broadcaster}] ${displayName}: ${message}`);
        renderer.receiveChatMessage({
            channel,
            displayName,
            message,
            tags,
            prefix,
            raw,
        });
    },
);

setUpElectronWindowResizeListener(canvas);
renderer.start();

window.addEventListener("beforeunload", () => {
    disconnectChat();
});
