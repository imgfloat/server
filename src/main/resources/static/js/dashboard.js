// TODO: Code smell Dashboard script uses broad shared state and imperative DOM updates instead of focused components.
const elements = {
    adminList: document.getElementById("admin-list"),
    suggestionList: document.getElementById("admin-suggestions"),
    adminInput: document.getElementById("new-admin"),
    addAdminButton: document.getElementById("add-admin-btn"),
    canvasWidth: document.getElementById("canvas-width"),
    canvasHeight: document.getElementById("canvas-height"),
    maxVolumeDb: document.getElementById("max-volume-db"),
    maxVolumeLabel: document.getElementById("max-volume-label"),
    canvasStatus: document.getElementById("canvas-status"),
    canvasSaveButton: document.getElementById("save-canvas-btn"),
    allowChannelEmotes: document.getElementById("allow-channel-emotes"),
    allowSevenTvEmotes: document.getElementById("allow-7tv-emotes"),
    allowScriptChat: document.getElementById("allow-script-chat"),
    scriptSettingsStatus: document.getElementById("script-settings-status"),
    scriptSettingsSaveButton: document.getElementById("save-script-settings-btn"),
    deleteAccountButton: document.getElementById("delete-account-btn"),
};

const apiBase = `/api/channels/${encodeURIComponent(broadcaster)}`;

function buildIdentity(admin) {
    const identity = document.createElement("div");
    identity.className = "identity-row";

    const avatar = document.createElement(admin.avatarUrl ? "img" : "div");
    avatar.className = "avatar";
    if (admin.avatarUrl) {
        avatar.src = admin.avatarUrl;
        avatar.alt = `${admin.displayName || admin.login} avatar`;
    } else {
        avatar.classList.add("avatar-fallback");
        avatar.textContent = (admin.displayName || admin.login || "?").charAt(0).toUpperCase();
    }

    const details = document.createElement("div");
    details.className = "identity-text";
    const title = document.createElement("p");
    title.className = "list-title";
    title.textContent = admin.displayName || admin.login;
    const subtitle = document.createElement("p");
    subtitle.className = "muted";
    subtitle.textContent = `@${admin.login}`;

    details.appendChild(title);
    details.appendChild(subtitle);
    identity.appendChild(avatar);
    identity.appendChild(details);
    return identity;
}

function renderAdmins(list) {
    if (!elements.adminList) return;
    elements.adminList.innerHTML = "";
    if (!list || list.length === 0) {
        const empty = document.createElement("li");
        empty.className = "stacked-list-item empty";
        empty.textContent = "No channel admins yet.";
        elements.adminList.appendChild(empty);
        return;
    }

    list.forEach((admin) => {
        const li = document.createElement("li");
        li.className = "stacked-list-item";

        li.appendChild(buildIdentity(admin));

        const actions = document.createElement("div");
        actions.className = "actions";

        const removeBtn = document.createElement("button");
        removeBtn.type = "button";
        removeBtn.className = "secondary";
        removeBtn.textContent = "Remove";
        removeBtn.addEventListener("click", () => removeAdmin(admin.login));

        actions.appendChild(removeBtn);
        li.appendChild(actions);
        elements.adminList.appendChild(li);
    });
}

function renderSuggestedAdmins(list) {
    if (!elements.suggestionList) return;

    elements.suggestionList.innerHTML = "";
    if (!list || list.length === 0) {
        const empty = document.createElement("li");
        empty.className = "stacked-list-item empty";
        empty.textContent = "No moderator suggestions right now.";
        elements.suggestionList.appendChild(empty);
        return;
    }

    list.forEach((admin) => {
        const li = document.createElement("li");
        li.className = "stacked-list-item";

        li.appendChild(buildIdentity(admin));

        const actions = document.createElement("div");
        actions.className = "actions";

        const addBtn = document.createElement("button");
        addBtn.type = "button";
        addBtn.className = "ghost";
        addBtn.textContent = "Add channel admin";
        addBtn.addEventListener("click", () => addAdmin(admin.login));

        actions.appendChild(addBtn);
        li.appendChild(actions);
        elements.suggestionList.appendChild(li);
    });
}

function normalizeUsername(value) {
    return (value || "").trim().replace(/^@+/, "");
}

function setButtonBusy(button, isBusy, busyLabel) {
    if (!button) return;
    if (!button.dataset.defaultLabel) {
        button.dataset.defaultLabel = button.textContent;
    }
    button.disabled = isBusy;
    if (busyLabel) {
        button.textContent = isBusy ? busyLabel : button.dataset.defaultLabel;
    }
}

async function fetchJson(path, options = {}, errorMessage = "Request failed") {
    const response = await fetch(`${apiBase}${path}`, options);
    if (!response.ok) {
        throw new Error(errorMessage);
    }
    return response.json();
}

async function fetchSuggestedAdmins() {
    try {
        const data = await fetchJson("/admins/suggestions", {}, "Failed to load admin suggestions");
        renderSuggestedAdmins(data);
    } catch (error) {
        renderSuggestedAdmins([]);
    }
}

async function fetchAdmins() {
    try {
        const data = await fetchJson("/admins", {}, "Failed to load admins");
        renderAdmins(data);
    } catch (error) {
        renderAdmins([]);
        showToast("Unable to load admins right now. Please try again.", "error");
    }
}

async function removeAdmin(username) {
    if (!username) return;
    try {
        const response = await fetch(
            `${apiBase}/admins/${encodeURIComponent(username)}`,
            { method: "DELETE" },
        );
        if (!response.ok) {
            throw new Error("Remove admin failed");
        }
        await Promise.all([fetchAdmins(), fetchSuggestedAdmins()]);
    } catch (error) {
        showToast("Failed to remove admin. Please retry.", "error");
    }
}

async function addAdmin(usernameFromAction) {
    const username = normalizeUsername(usernameFromAction || elements.adminInput?.value);
    if (!username) {
        showToast("Enter a Twitch username to add as an admin.", "info");
        return;
    }

    setButtonBusy(elements.addAdminButton, true, "Adding...");
    try {
        await fetchJson(
            "/admins",
            {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username }),
            },
            "Add admin failed",
        );
        if (elements.adminInput) {
            elements.adminInput.value = "";
        }
        showToast(`Added @${username} as an admin.`, "success");
        await Promise.all([fetchAdmins(), fetchSuggestedAdmins()]);
    } catch (error) {
        showToast("Unable to add admin right now. Please try again.", "error");
    } finally {
        setButtonBusy(elements.addAdminButton, false, "Adding...");
    }
}

function renderCanvasSettings(settings) {
    if (elements.canvasWidth) elements.canvasWidth.value = Math.round(settings.width);
    if (elements.canvasHeight) elements.canvasHeight.value = Math.round(settings.height);
    if (elements.maxVolumeDb) {
        const volumeDb = Number.isFinite(settings.maxVolumeDb) ? settings.maxVolumeDb : DEFAULT_MAX_VOLUME_DB;
        const sliderValue = dbToSlider(volumeDb);
        elements.maxVolumeDb.value = sliderValue;
        setMaxVolumeLabel(volumeDb);
    }
}

async function fetchCanvasSettings() {
    try {
        const data = await fetchJson("/canvas", {}, "Failed to load canvas settings");
        renderCanvasSettings(data);
    } catch (error) {
        renderCanvasSettings({ width: 1920, height: 1080, maxVolumeDb: 0 });
        showToast("Using default canvas size. Unable to load saved settings.", "warning");
    }
}

async function saveCanvasSettings() {
    const width = Number(elements.canvasWidth?.value);
    const height = Number(elements.canvasHeight?.value);
    const maxVolumeDb = sliderToDb(Number(elements.maxVolumeDb?.value));
    if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
        showToast("Please enter a valid width and height.", "info");
        return;
    }
    if (!Number.isInteger(width) || !Number.isInteger(height)) {
        showToast("Please enter whole-number dimensions for the canvas size.", "info");
        return;
    }
    if (!Number.isFinite(maxVolumeDb) || maxVolumeDb > MAX_VOLUME_DB || maxVolumeDb < MIN_VOLUME_DB) {
        showToast(`Max volume must be between ${MIN_VOLUME_DB} and ${MAX_VOLUME_DB} dBFS.`, "info");
        return;
    }
    if (elements.canvasStatus) elements.canvasStatus.textContent = "Saving...";
    setButtonBusy(elements.canvasSaveButton, true, "Saving...");
    try {
        const settings = await fetchJson(
            "/canvas",
            {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ width, height, maxVolumeDb }),
            },
            "Failed to save canvas",
        );
        renderCanvasSettings(settings);
        if (elements.canvasStatus) elements.canvasStatus.textContent = "Saved.";
        showToast("Canvas size saved successfully.", "success");
        setTimeout(() => {
            if (elements.canvasStatus) elements.canvasStatus.textContent = "";
        }, 2000);
    } catch (error) {
        if (elements.canvasStatus) elements.canvasStatus.textContent = "Unable to save right now.";
        showToast("Unable to save canvas size. Please retry.", "error");
    } finally {
        setButtonBusy(elements.canvasSaveButton, false, "Saving...");
    }
}

const MIN_VOLUME_DB = -60;
const MAX_VOLUME_DB = 0;
const DEFAULT_MAX_VOLUME_DB = 0;

function clamp(value, min, max) {
    if (!Number.isFinite(value)) return min;
    return Math.min(max, Math.max(min, value));
}

function sliderToDb(value) {
    const clamped = clamp(value, 0, 100);
    return MIN_VOLUME_DB + (clamped / 100) * (MAX_VOLUME_DB - MIN_VOLUME_DB);
}

function dbToSlider(value) {
    const clamped = clamp(value, MIN_VOLUME_DB, MAX_VOLUME_DB);
    return Math.round(((clamped - MIN_VOLUME_DB) / (MAX_VOLUME_DB - MIN_VOLUME_DB)) * 100);
}

function setMaxVolumeLabel(dbValue) {
    if (!elements.maxVolumeLabel) return;
    const rounded = Math.round(dbValue * 10) / 10;
    elements.maxVolumeLabel.textContent = `${rounded} dBFS`;
}

const demoAudioState = {
    context: null,
    oscillator: null,
    gain: null,
    compressor: null,
    timeoutId: null,
    isPlaying: false,
    previewUnavailable: false,
};

function stopVolumeDemo() {
    if (demoAudioState.timeoutId) {
        clearTimeout(demoAudioState.timeoutId);
        demoAudioState.timeoutId = null;
    }
    if (demoAudioState.oscillator) {
        try {
            demoAudioState.oscillator.stop();
        } catch (_) {}
        demoAudioState.oscillator.disconnect();
        demoAudioState.oscillator = null;
    }
    if (demoAudioState.gain) {
        demoAudioState.gain.disconnect();
        demoAudioState.gain = null;
    }
    if (demoAudioState.compressor) {
        demoAudioState.compressor.disconnect();
        demoAudioState.compressor = null;
    }
    demoAudioState.isPlaying = false;
}

function startVolumeDemo(maxVolumeDb) {
    const AudioContextImpl = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextImpl) {
        if (!demoAudioState.previewUnavailable) {
            showToast("Audio preview is not supported in this browser.", "info");
            demoAudioState.previewUnavailable = true;
        }
        return;
    }
    const context = demoAudioState.context || new AudioContextImpl();
    demoAudioState.context = context;
    if (!demoAudioState.compressor) {
        const compressor = context.createDynamicsCompressor();
        compressor.knee.value = 0;
        compressor.ratio.value = 20;
        compressor.attack.value = 0.003;
        compressor.release.value = 0.25;
        compressor.connect(context.destination);
        demoAudioState.compressor = compressor;
    }
    demoAudioState.compressor.threshold.value = maxVolumeDb;
    if (!demoAudioState.gain) {
        const gain = context.createGain();
        gain.gain.value = 0.8;
        gain.connect(demoAudioState.compressor);
        demoAudioState.gain = gain;
    }
    if (!demoAudioState.oscillator) {
        const oscillator = context.createOscillator();
        oscillator.type = "sine";
        oscillator.frequency.value = 440;
        oscillator.connect(demoAudioState.gain);
        oscillator.start();
        demoAudioState.oscillator = oscillator;
    }
    demoAudioState.isPlaying = true;
    context.resume().catch(() => {});
    if (demoAudioState.timeoutId) {
        clearTimeout(demoAudioState.timeoutId);
    }
    demoAudioState.timeoutId = setTimeout(stopVolumeDemo, 800);
}

function handleVolumeSliderInput() {
    if (!elements.maxVolumeDb) return;
    const nextDb = sliderToDb(Number(elements.maxVolumeDb.value));
    setMaxVolumeLabel(nextDb);
    startVolumeDemo(nextDb);
}

function renderScriptSettings(settings) {
    if (elements.allowChannelEmotes) {
        elements.allowChannelEmotes.checked = settings.allowChannelEmotesForAssets !== false;
    }
    if (elements.allowSevenTvEmotes) {
        elements.allowSevenTvEmotes.checked = settings.allowSevenTvEmotesForAssets !== false;
    }
    if (elements.allowScriptChat) {
        elements.allowScriptChat.checked = settings.allowScriptChatAccess !== false;
    }
}

async function fetchScriptSettings() {
    try {
        const data = await fetchJson("/settings", {}, "Failed to load script settings");
        renderScriptSettings(data);
    } catch (error) {
        renderScriptSettings({
            allowChannelEmotesForAssets: true,
            allowSevenTvEmotesForAssets: true,
            allowScriptChatAccess: true,
        });
        showToast("Using default script settings. Unable to load saved preferences.", "warning");
    }
}

async function saveScriptSettings() {
    saveCanvasSettings()
    const allowChannelEmotesForAssets = elements.allowChannelEmotes?.checked ?? true;
    const allowSevenTvEmotesForAssets = elements.allowSevenTvEmotes?.checked ?? true;
    const allowScriptChatAccess = elements.allowScriptChat?.checked ?? true;
    if (elements.scriptSettingsStatus) elements.scriptSettingsStatus.textContent = "Saving...";
    setButtonBusy(elements.scriptSettingsSaveButton, true, "Saving...");
    try {
        const settings = await fetchJson(
            "/settings",
            {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    allowChannelEmotesForAssets,
                    allowSevenTvEmotesForAssets,
                    allowScriptChatAccess,
                }),
            },
            "Failed to save script settings",
        );
        renderScriptSettings(settings);
        if (elements.scriptSettingsStatus) elements.scriptSettingsStatus.textContent = "Saved.";
        showToast("Script settings saved successfully.", "success");
        setTimeout(() => {
            if (elements.scriptSettingsStatus) elements.scriptSettingsStatus.textContent = "";
        }, 2000);
    } catch (error) {
        if (elements.scriptSettingsStatus) elements.scriptSettingsStatus.textContent = "Unable to save right now.";
        showToast("Unable to save script settings. Please retry.", "error");
    } finally {
        setButtonBusy(elements.scriptSettingsSaveButton, false, "Saving...");
    }
}

async function deleteAccount() {
    const confirmation = window.prompt(
        "Type DELETE to permanently remove your account, assets, and session.",
    );
    if (confirmation !== "DELETE") {
        if (confirmation !== null) {
            showToast("Account deletion cancelled.", "info");
        }
        return;
    }

    setButtonBusy(elements.deleteAccountButton, true, "Deleting...");
    try {
        const response = await fetch("/api/account", { method: "DELETE" });
        if (!response.ok) {
            throw new Error("Delete account failed");
        }
        showToast("Account deleted. Redirecting...", "success");
        window.location.href = "/";
    } catch (error) {
        showToast("Unable to delete account right now. Please retry.", "error");
        setButtonBusy(elements.deleteAccountButton, false, "Deleting...");
    }
}

if (elements.adminInput) {
    elements.adminInput.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            addAdmin();
        }
    });
}

fetchAdmins();
fetchSuggestedAdmins();
fetchCanvasSettings();
fetchScriptSettings();

if (elements.deleteAccountButton) {
    elements.deleteAccountButton.addEventListener("click", deleteAccount);
}
if (elements.maxVolumeDb) {
    elements.maxVolumeDb.addEventListener("input", handleVolumeSliderInput);
}
