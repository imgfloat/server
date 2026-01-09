const formElement = document.getElementById("settings-form");
const submitButtonElement = document.getElementById("settings-submit-button");
const canvasFpsElement = document.getElementById("canvas-fps");
const canvasSizeElement = document.getElementById("canvas-size");
const minPlaybackSpeedElement = document.getElementById("min-playback-speed");
const maxPlaybackSpeedElement = document.getElementById("max-playback-speed");
const minPitchElement = document.getElementById("min-audio-pitch");
const maxPitchElement = document.getElementById("max-audio-pitch");
const minVolumeElement = document.getElementById("min-volume");
const maxVolumeElement = document.getElementById("max-volume");
const statusElement = document.getElementById("settings-status");
const statCanvasFpsElement = document.getElementById("stat-canvas-fps");
const statCanvasSizeElement = document.getElementById("stat-canvas-size");
const statPlaybackRangeElement = document.getElementById("stat-playback-range");
const statAudioRangeElement = document.getElementById("stat-audio-range");
const statVolumeRangeElement = document.getElementById("stat-volume-range");

const currentSettings = JSON.parse(serverRenderedSettings);
let userSettings = { ...currentSettings };

function jsonEquals(a, b) {
    if (a === b) return true;

    if (typeof a !== "object" || typeof b !== "object" || a === null || b === null) {
        return false;
    }

    const keysA = Object.keys(a);
    const keysB = Object.keys(b);

    if (keysA.length !== keysB.length) return false;

    for (const key of keysA) {
        if (!keysB.includes(key)) return false;
        if (!jsonEquals(a[key], b[key])) return false;
    }

    return true;
}

function setFormSettings(s) {
    canvasFpsElement.value = s.canvasFramesPerSecond;
    canvasSizeElement.value = s.maxCanvasSideLengthPixels;

    minPlaybackSpeedElement.value = s.minAssetPlaybackSpeedFraction;
    maxPlaybackSpeedElement.value = s.maxAssetPlaybackSpeedFraction;
    minPitchElement.value = s.minAssetAudioPitchFraction;
    maxPitchElement.value = s.maxAssetAudioPitchFraction;
    minVolumeElement.value = s.minAssetVolumeFraction;
    maxVolumeElement.value = s.maxAssetVolumeFraction;
}

function updateStatCards(settings) {
    if (!settings) return;
    statCanvasFpsElement.textContent = `${settings.canvasFramesPerSecond ?? "--"} fps`;
    statCanvasSizeElement.textContent = `${settings.maxCanvasSideLengthPixels ?? "--"} px`;
    statPlaybackRangeElement.textContent = `${settings.minAssetPlaybackSpeedFraction ?? "--"} – ${settings.maxAssetPlaybackSpeedFraction ?? "--"}x`;
    statAudioRangeElement.textContent = `${settings.minAssetAudioPitchFraction ?? "--"} – ${settings.maxAssetAudioPitchFraction ?? "--"}x`;
    statVolumeRangeElement.textContent = `${settings.minAssetVolumeFraction ?? "--"} – ${settings.maxAssetVolumeFraction ?? "--"}x`;
}

function readInt(input) {
    return input.checkValidity() ? Number(input.value) : null;
}

function readFloat(input) {
    return input.checkValidity() ? Number(input.value) : null;
}

function loadUserSettingsFromDom() {
    userSettings.canvasFramesPerSecond = readInt(canvasFpsElement);
    userSettings.maxCanvasSideLengthPixels = readInt(canvasSizeElement);
    userSettings.minAssetPlaybackSpeedFraction = readFloat(minPlaybackSpeedElement);
    userSettings.maxAssetPlaybackSpeedFraction = readFloat(maxPlaybackSpeedElement);
    userSettings.minAssetAudioPitchFraction = readFloat(minPitchElement);
    userSettings.maxAssetAudioPitchFraction = readFloat(maxPitchElement);
    userSettings.minAssetVolumeFraction = readFloat(minVolumeElement);
    userSettings.maxAssetVolumeFraction = readFloat(maxVolumeElement);
}

function updateSubmitButtonDisabledState() {
    if (jsonEquals(currentSettings, userSettings)) {
        submitButtonElement.disabled = "disabled";
        statusElement.textContent = "No changes yet.";
        statusElement.classList.remove("status-success", "status-warning");
        return;
    }
    if (!formElement.checkValidity()) {
        submitButtonElement.disabled = "disabled";
        statusElement.textContent = "Fix highlighted fields.";
        statusElement.classList.add("status-warning");
        statusElement.classList.remove("status-success");
        return;
    }
    submitButtonElement.disabled = null;
    statusElement.textContent = "Ready to save.";
    statusElement.classList.remove("status-warning");
}

function submitSettingsForm() {
    if (submitButtonElement.getAttribute("disabled") != null) {
        console.warn("Attempted to submit invalid form");
        showToast("Settings not valid", "warning");
        return;
    }
    statusElement.textContent = "Saving…";
    statusElement.classList.remove("status-success", "status-warning");
    fetch("/api/settings/set", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(userSettings),
    })
        .then((r) => {
            if (!r.ok) {
                throw new Error("Failed to load canvas");
            }
            return r.json();
        })
        .then((newSettings) => {
            currentSettings = { ...newSettings };
            userSettings = { ...newSettings };
            updateStatCards(newSettings);
            showToast("Settings saved", "success");
            statusElement.textContent = "Saved.";
            statusElement.classList.add("status-success");
            updateSubmitButtonDisabledState();
        })
        .catch((error) => {
            showToast("Unable to save settings", "error");
            console.error(error);
            statusElement.textContent = "Save failed. Try again.";
            statusElement.classList.add("status-warning");
        });
}

formElement.querySelectorAll("input").forEach((input) => {
    input.addEventListener("input", () => {
        loadUserSettingsFromDom();
        updateSubmitButtonDisabledState();
    });
});

formElement.addEventListener("submit", (event) => {
    event.preventDefault();
    submitSettingsForm();
});

setFormSettings(currentSettings);
updateStatCards(currentSettings);
updateSubmitButtonDisabledState();
