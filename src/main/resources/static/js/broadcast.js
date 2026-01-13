import { BroadcastRenderer } from "./broadcast/renderer.js";
import { connectTwitchChat } from "./broadcast/twitchChat.js";
import { setUpElectronWindowResizeListener } from "./electron.js";

const canvas = document.getElementById("broadcast-canvas");
const scriptLayer = document.getElementById("broadcast-script-layer");
const renderer = new BroadcastRenderer({ canvas, scriptLayer, broadcaster, showToast });
const disconnectChat = connectTwitchChat(broadcaster, ({ channel, displayName, message }) => {
    console.log(`[twitch:${broadcaster}] ${displayName}: ${message}`);
    renderer.receiveChatMessage({
        channel,
        displayName,
        message,
    });
});

setUpElectronWindowResizeListener(canvas);
renderer.start();

window.addEventListener("beforeunload", () => {
    disconnectChat();
});
