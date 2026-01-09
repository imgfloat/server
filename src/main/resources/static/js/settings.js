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
const sysadminListElement = document.getElementById("sysadmin-list");
const sysadminInputElement = document.getElementById("new-sysadmin");
const addSysadminButtonElement = document.getElementById("add-sysadmin-button");

let currentSettings = JSON.parse(serverRenderedSettings);
const initialSysadmin =
    typeof serverRenderedInitialSysadmin === "string" && serverRenderedInitialSysadmin.trim() !== ""
        ? serverRenderedInitialSysadmin.trim().toLowerCase()
        : null;
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

function renderSystemAdministrators(admins) {
    sysadminListElement.innerHTML = "";
    if (!admins || admins.length === 0) {
        const empty = document.createElement("li");
        empty.classList.add("stacked-list-item");
        empty.innerHTML = '<p class="muted">No system administrators found.</p>';
        sysadminListElement.appendChild(empty);
        return;
    }

    admins.forEach((admin) => {
        const listItem = document.createElement("li");
        listItem.classList.add("stacked-list-item");

        const text = document.createElement("div");
        text.innerHTML = `<p class="list-title">${admin}</p><p class="muted">System admin access</p>`;

        const button = document.createElement("button");
        button.classList.add("button", "secondary");
        button.type = "button";
        button.textContent = "Remove";
        button.addEventListener("click", () => removeSystemAdministrator(admin));

        listItem.appendChild(text);
        listItem.appendChild(button);
        sysadminListElement.appendChild(listItem);
    });
}

async function loadSystemAdministrators() {
    return fetch("/api/system-administrators")
        .then((r) => {
            if (!r.ok) {
                throw new Error("Failed to load system admins");
            }
            return r.json();
        })
        .then((admins) => {
            renderSystemAdministrators(admins);
        })
        .catch((error) => {
            console.error(error);
            showToast("Unable to load system admins", "error");
        });
}

function addSystemAdministrator() {
    const username = sysadminInputElement.value.trim();
    if (!username) {
        showToast("Enter a Twitch username", "warning");
        return;
    }
    fetch("/api/system-administrators", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ twitchUsername: username }),
    })
        .then((r) => {
            if (!r.ok) {
                throw new Error("Failed to add system admin");
            }
            return r.json();
        })
        .then((admins) => {
            sysadminInputElement.value = "";
            renderSystemAdministrators(admins);
            showToast("System admin added", "success");
        })
        .catch((error) => {
            console.error(error);
            showToast("Unable to add system admin", "error");
        });
}

function removeSystemAdministrator(username) {
    fetch(`/api/system-administrators/${encodeURIComponent(username)}`, { method: "DELETE" })
        .then((r) => {
            if (!r.ok) {
                return r.text().then((text) => {
                    throw new Error(text || "Failed to remove system admin");
                });
            }
            return r.json();
        })
        .then((admins) => {
            renderSystemAdministrators(admins);
            showToast("System admin removed", "success");
        })
        .catch((error) => {
            console.error(error);
            showToast("Unable to remove system admin", "error");
        });
}

formElement.querySelectorAll("input").forEach((input) => {
    input.addEventListener("input", () => {
        loadUserSettingsFromDom();
        updateSubmitButtonDisabledState();
    });
});

addSysadminButtonElement.addEventListener("click", () => addSystemAdministrator());
sysadminInputElement.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
        event.preventDefault();
        addSystemAdministrator();
    }
});

formElement.addEventListener("submit", (event) => {
    event.preventDefault();
    submitSettingsForm();
});

setFormSettings(currentSettings);
updateStatCards(currentSettings);
updateSubmitButtonDisabledState();
loadSystemAdministrators();

if (initialSysadmin) {
    document.querySelectorAll("[data-sysadmin-remove]").forEach((button) => {
        const username = button.getAttribute("data-sysadmin-username");
        if (username && username.trim().toLowerCase() === initialSysadmin) {
            button.disabled = true;
            button.title = "The initial system administrator cannot be removed.";
        }
    });
}
