import { BroadcastRenderer } from "./broadcast/renderer.js";
import { connectTwitchChat } from "./broadcast/twitchChat.js";
import { setUpElectronWindowFrame, setUpElectronWindowResizeListener } from "./electron.js";

const canvas = document.getElementById("broadcast-canvas");
const scriptLayer = document.getElementById("broadcast-script-layer");
setUpElectronWindowFrame();

const renderer = new BroadcastRenderer({ canvas, scriptLayer, broadcaster, showToast });
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
