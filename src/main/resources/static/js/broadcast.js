import { BroadcastRenderer } from "./broadcast/renderer.js";
import { connectTwitchChat } from "./broadcast/twitchChat.js";
import { setUpElectronWindowFrame, setUpElectronWindowResizeListener } from "./electron.js";

const canvas = document.getElementById("broadcast-canvas");
const scriptLayer = document.getElementById("broadcast-script-layer");
setUpElectronWindowFrame();

const renderer = new BroadcastRenderer({ canvas, scriptLayer, broadcaster, showToast });
const defaultScriptSettings = {
    allowChannelEmotesForAssets: true,
    allowScriptChatAccess: true,
};
let currentScriptSettings = { ...defaultScriptSettings };

const settingsPromise = fetch(`/api/channels/${encodeURIComponent(broadcaster)}/settings`)
    .then((response) => {
        if (!response.ok) {
            throw new Error("Failed to load channel settings");
        }
        return response.json();
    })
    .then((settings) => {
        currentScriptSettings = { ...defaultScriptSettings, ...settings };
        renderer.setScriptSettings(currentScriptSettings);
    })
    .catch((error) => {
        console.warn("Unable to load channel settings", error);
        renderer.setScriptSettings(defaultScriptSettings);
    });

fetch(`/api/twitch/emotes?channel=${encodeURIComponent(broadcaster)}`)
    .then((response) => {
        if (!response.ok) {
            throw new Error("Failed to load Twitch emotes");
        }
        return response.json();
    })
    .then((catalog) => renderer.setEmoteCatalog(catalog))
    .catch((error) => console.warn("Unable to load Twitch emotes", error));
let disconnectChat = () => {};
settingsPromise.finally(() => {
    if (!currentScriptSettings.allowScriptChatAccess) {
        return;
    }
    disconnectChat = connectTwitchChat(
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
});

setUpElectronWindowResizeListener(canvas);
renderer.start();

window.addEventListener("beforeunload", () => {
    disconnectChat();
});
