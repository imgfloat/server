import { isAudioAsset } from "../media/audio.js";
import { isCodeAsset, isGifAsset, isModelAsset, isVideoAsset, isVideoElement } from "../broadcast/assetKinds.js";
import { createModelManager } from "../media/modelManager.js";
import {
    ensureLayerPosition as ensureLayerPositionForState,
    getLayerOrder as getLayerOrderForState,
    getRenderOrder as getRenderOrderForState,
    getScriptLayerOrder as getScriptLayerOrderForState,
} from "../broadcast/layers.js";

export function createAdminConsole({
    broadcaster,
    username,
    settings,
    uploadLimitBytes,
    showToast = globalThis.showToast,
    customAssetModal,
}) {
    const SETTINGS = settings;
    const UPLOAD_LIMIT_BYTES = uploadLimitBytes;

    const canvas = document.getElementById("admin-canvas");
    const ctx = canvas.getContext("2d");
    const overlay = document.getElementById("admin-overlay");
    let canvasSettings = { width: 1920, height: 1080 };
    const assets = new Map();
    const mediaCache = new Map();
    const renderStates = new Map();
    const animatedCache = new Map();
    const audioControllers = new Map();
    const pendingAudioUnlock = new Set();
    const loopPlaybackState = new Map();
    const previewCache = new Map();
    const previewImageCache = new Map();
    const pendingTransformSaves = new Map();
    const HANDLE_SIZE = 10;
    const ROTATE_HANDLE_OFFSET = 32;
    const VOLUME_SLIDER_MAX = SETTINGS.maxAssetVolumeFraction * 100;
    const VOLUME_CURVE_STRENGTH = -0.6;
    const KEYBOARD_NUDGE_STEP = 5;
    const KEYBOARD_NUDGE_FAST_STEP = 20;
    const controlsPanel = document.getElementById("asset-controls");
    const widthInput = document.getElementById("asset-width");
    const heightInput = document.getElementById("asset-height");
    const aspectLockInput = document.getElementById("maintain-aspect");
    const speedInput = document.getElementById("asset-speed");
    const speedLabel = document.getElementById("asset-speed-label");
    const volumeInput = document.getElementById("asset-volume");
    const volumeLabel = document.getElementById("asset-volume-label");
    const selectedOrderLabel = document.getElementById("asset-order-position");
    const playbackSection = document.getElementById("playback-section");
    const volumeSection = document.getElementById("volume-section");
    const audioSection = document.getElementById("audio-section");
    const layoutSection = document.getElementById("layout-section");
    const audioLoopInput = document.getElementById("asset-audio-loop");
    const audioDelayInput = document.getElementById("asset-audio-delay");
    const audioSpeedInput = document.getElementById("asset-audio-speed");
    const audioSpeedLabel = document.getElementById("asset-audio-speed-label");
    const audioPitchInput = document.getElementById("asset-audio-pitch");
    const audioDelayLabel = document.getElementById("asset-audio-delay-label");
    const audioPitchLabel = document.getElementById("asset-audio-pitch-label");
    const controlsPlaceholder = document.getElementById("asset-controls-placeholder");
    const fileNameLabel = document.getElementById("asset-file-name");
    const assetInspector = document.getElementById("asset-inspector");
    const selectedAssetName = document.getElementById("selected-asset-name");
    const selectedAssetMeta = document.getElementById("selected-asset-meta");
    const selectedAssetResolution = document.getElementById("selected-asset-resolution");
    const selectedAssetIdLabel = document.getElementById("selected-asset-id");
    const selectedAssetBadges = document.getElementById("selected-asset-badges");
    const selectedEditBtn = document.getElementById("selected-asset-edit");
    const selectedVisibilityBtn = document.getElementById("selected-asset-visibility");
    const selectedDeleteBtn = document.getElementById("selected-asset-delete");
    const assetActionRow = document.getElementById("asset-actions");
    const assetActionButtons = Array.from(assetActionRow?.querySelectorAll("button") ?? []);
    const canvasResolutionLabel = document.getElementById("canvas-resolution");
    const canvasScaleLabel = document.getElementById("canvas-scale");
    const aspectLockState = new Map();
    const commitSizeChange = debounce(() => applyTransformFromInputs(), 180);
    const audioUnlockEvents = ["pointerdown", "keydown", "touchstart"];
    const modelManager = createModelManager({ requestDraw: () => requestDraw() });

    let drawPending = false;
    let layerOrder = [];
    let scriptLayerOrder = [];
    const layerState = {
        assets,
        get layerOrder() {
            return layerOrder;
        },
        set layerOrder(value) {
            layerOrder = value;
        },
        get scriptLayerOrder() {
            return scriptLayerOrder;
        },
        set scriptLayerOrder(value) {
            scriptLayerOrder = value;
        },
    };
    let pendingUploads = [];
    let selectedAssetId = null;
    let interactionState = null;
    let lastSizeInputChanged = null;
    let stompClient;

    function start() {
        applyCanvasSettings(canvasSettings);
        audioUnlockEvents.forEach((eventName) => {
            globalThis.addEventListener(eventName, () => {
                if (!pendingAudioUnlock.size) return;
                pendingAudioUnlock.forEach((controller) => {
                    safePlay(controller);
                });
                pendingAudioUnlock.clear();
            });
        });
        const fileInput = document.getElementById("asset-file");
        if (fileInput) {
            fileInput.addEventListener("change", (event) => {
                handleFileSelection(event.target);
            });
        }
        const assetLauncherButton = document.getElementById("asset-launcher-button");
        if (assetLauncherButton && customAssetModal?.openLauncher) {
            assetLauncherButton.addEventListener("click", () => customAssetModal.openLauncher());
        }
        globalThis.addEventListener("resize", () => {
            resizeCanvas();
        });
        fetchCanvasSettings().finally(() => {
            resizeCanvas();
            connect();
        });
    }

    function handleFileSelection(input) {
        if (!input) return;
        const hasFile = input.files && input.files.length;
        const name = hasFile ? input.files[0].name : "";
        if (fileNameLabel) {
            fileNameLabel.textContent = name || "No file chosen";
        }
        if (hasFile) {
            uploadAsset(input.files[0]);
        }
    }

    function debounce(fn, wait = 150) {
        let timeout;
        return (...args) => {
            clearTimeout(timeout);
            timeout = setTimeout(() => fn(...args), wait);
        };
    }

    function isFormInputElement(element) {
        if (!element) return false;
        if (element.isContentEditable) return true;
        const tag = element.tagName ? element.tagName.toLowerCase() : "";
        return ["input", "textarea", "select", "button", "option"].includes(tag);
    }

    function schedulePersistTransform(asset, silent = false, delay = 200) {
        if (!asset?.id) return;
        cancelPendingTransform(asset.id);
        const timeout = setTimeout(() => {
            pendingTransformSaves.delete(asset.id);
            persistTransform(asset, silent);
        }, delay);
        pendingTransformSaves.set(asset.id, timeout);
    }

    function cancelPendingTransform(assetId) {
        const pending = pendingTransformSaves.get(assetId);
        if (pending) {
            clearTimeout(pending);
            pendingTransformSaves.delete(assetId);
        }
    }

    function ensureLayerPosition(assetId, placement = "keep") {
        ensureLayerPositionForState(layerState, assetId, placement);
    }

    function getLayerOrder() {
        return getLayerOrderForState(layerState);
    }

    function getScriptLayerOrder() {
        return getScriptLayerOrderForState(layerState);
    }

    function getAssetsByLayer() {
        return getLayerOrder()
            .map((id) => assets.get(id))
            .filter(Boolean);
    }

    function getScriptAssetsByLayer() {
        return getScriptLayerOrder()
            .map((id) => assets.get(id))
            .filter(Boolean);
    }

    function getAudioAssets() {
        return Array.from(assets.values())
            .filter((asset) => isAudioAsset(asset))
            .sort((a, b) => (b.createdAtMs || 0) - (a.createdAtMs || 0));
    }

    function getCodeAssets() {
        return getScriptAssetsByLayer();
    }

    function getRenderOrder() {
        return getRenderOrderForState(layerState);
    }

    function getLayerValue(assetId) {
        const asset = assets.get(assetId);
        if (asset && (isAudioAsset(asset) || isCodeAsset(asset))) {
            return 0;
        }
        const order = getLayerOrder();
        const index = order.indexOf(assetId);
        if (index === -1) return 1;
        return order.length - index;
    }

    function getScriptLayerValue(assetId) {
        const asset = assets.get(assetId);
        if (!asset || !isCodeAsset(asset)) {
            return 0;
        }
        const order = getScriptLayerOrder();
        const index = order.indexOf(assetId);
        if (index === -1) return 1;
        return order.length - index;
    }

    function getLayerPosition(assetId) {
        const order = getLayerOrder();
        const index = order.indexOf(assetId);
        if (index === -1) return 1;
        return index + 1;
    }

    function getScriptLayerPosition(assetId) {
        const order = getScriptLayerOrder();
        const index = order.indexOf(assetId);
        if (index === -1) return 1;
        return index + 1;
    }

    function addPendingUpload(name) {
        const pending = {
            id: `pending-${Date.now()}-${Math.round(Math.random() * 100000)}`,
            name,
            status: "uploading",
            createdAtMs: Date.now(),
        };
        pendingUploads.push(pending);
        renderAssetList();
        return pending.id;
    }

    function updatePendingUpload(id, updates = {}) {
        const pending = pendingUploads.find((item) => item.id === id);
        if (!pending) return;
        Object.assign(pending, updates);
        renderAssetList();
    }

    function removePendingUpload(id) {
        const index = pendingUploads.findIndex((item) => item.id === id);
        if (index === -1) return;
        pendingUploads.splice(index, 1);
        renderAssetList();
    }

    function resolvePendingUploadByName(name) {
        if (!name) return;
        const index = pendingUploads.findIndex((item) => item.name === name);
        if (index === -1) return;
        pendingUploads.splice(index, 1);
        renderAssetList();
    }

    function formatDurationLabel(durationMs) {
        const totalSeconds = Math.max(0, Math.round(durationMs / 1000));
        const seconds = totalSeconds % 60;
        const minutes = Math.floor(totalSeconds / 60) % 60;
        const hours = Math.floor(totalSeconds / 3600);
        const parts = [];
        if (hours > 0) {
            parts.push(`${hours}h`);
        }
        if (minutes > 0 || hours > 0) {
            parts.push(`${minutes}m`);
        }
        if (seconds > 0 || parts.length === 0) {
            parts.push(`${seconds}s`);
        }
        return parts.join(" ");
    }

    function recordDuration(assetId, seconds) {
        if (!Number.isFinite(seconds) || seconds <= 0) {
            return;
        }
        const asset = assets.get(assetId);
        if (!asset) {
            return;
        }
        const nextMs = Math.round(seconds * 1000);
        if (asset.durationMs === nextMs) {
            return;
        }
        asset.durationMs = nextMs;
        if (asset.id === selectedAssetId) {
            updateSelectedAssetSummary(asset);
        }
        drawAndList();
    }

    function hasDuration(asset) {
        return (
            asset &&
            Number.isFinite(asset.durationMs) &&
            asset.durationMs > 0 &&
            (isAudioAsset(asset) || isVideoAsset(asset))
        );
    }

    function getDurationBadge(asset) {
        if (!hasDuration(asset)) {
            return null;
        }
        return formatDurationLabel(asset.durationMs);
    }

    function setSpeedLabel(percent) {
        if (!speedLabel) return;
        speedLabel.textContent = `${Math.round(percent)}%`;
    }

    function setAudioSpeedLabel(percentValue) {
        if (!audioSpeedLabel) return;
        const multiplier = Math.max(0, percentValue) / 100;
        const formatted = multiplier >= 10 ? multiplier.toFixed(0) : multiplier.toFixed(2);
        audioSpeedLabel.textContent = `${formatted}x`;
    }

    function formatDelayLabel(ms) {
        const numeric = Math.max(0, Number.parseInt(ms, 10) || 0);
        if (numeric >= 1000) {
            const seconds = numeric / 1000;
            const decimals = Number.isInteger(seconds) ? 0 : 1;
            return `${seconds.toFixed(decimals)}s`;
        }
        return `${numeric}ms`;
    }

    function setAudioDelayLabel(value) {
        if (!audioDelayLabel) return;
        audioDelayLabel.textContent = formatDelayLabel(value);
    }

    function setAudioPitchLabel(percentValue) {
        if (!audioPitchLabel) return;
        const numeric = Math.round(Math.max(0, percentValue));
        audioPitchLabel.textContent = `${numeric}%`;
    }

    function clamp(value, min, max) {
        return Math.min(max, Math.max(min, value));
    }

    function sliderToVolume(sliderValue) {
        const normalized = clamp(sliderValue, 0, VOLUME_SLIDER_MAX) / VOLUME_SLIDER_MAX;
        const curved = normalized + VOLUME_CURVE_STRENGTH * normalized * (1 - normalized) * (1 - 2 * normalized);
        return clamp(
            curved * SETTINGS.maxAssetVolumeFraction,
            SETTINGS.minAssetVolumeFraction,
            SETTINGS.maxAssetVolumeFraction,
        );
    }

    function volumeToSlider(volumeValue) {
        const target =
            clamp(volumeValue ?? 1, SETTINGS.minAssetVolumeFraction, SETTINGS.maxAssetVolumeFraction) /
            SETTINGS.maxAssetVolumeFraction;
        let low = 0;
        let high = VOLUME_SLIDER_MAX;
        for (let i = 0; i < 24; i += 1) {
            const mid = (low + high) / 2;
            const midNormalized = sliderToVolume(mid) / SETTINGS.maxAssetVolumeFraction;
            if (midNormalized < target) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return Math.round(high);
    }

    function setVolumeLabel(sliderValue) {
        if (!volumeLabel) return;
        const volumePercent = Math.round(sliderToVolume(sliderValue) * 100);
        volumeLabel.textContent = `${volumePercent}%`;
    }

    function queueAudioForUnlock(controller) {
        if (!controller) return;
        pendingAudioUnlock.add(controller);
    }

    function safePlay(controller) {
        if (!controller?.element) return;
        const playPromise = controller.element.play();
        if (playPromise?.catch) {
            playPromise.catch(() => queueAudioForUnlock(controller));
        }
    }

    if (widthInput) widthInput.addEventListener("input", () => handleSizeInputChange("width"));
    if (widthInput) widthInput.addEventListener("change", () => commitSizeChange());
    if (heightInput) heightInput.addEventListener("input", () => handleSizeInputChange("height"));
    if (heightInput) heightInput.addEventListener("change", () => commitSizeChange());
    if (speedInput) speedInput.addEventListener("input", updatePlaybackFromInputs);
    if (volumeInput) volumeInput.addEventListener("input", updateVolumeFromInput);
    if (audioLoopInput) audioLoopInput.addEventListener("change", updateAudioSettingsFromInputs);
    if (audioDelayInput)
        audioDelayInput.addEventListener("input", () => {
            setAudioDelayLabel(audioDelayInput.value);
            updateAudioSettingsFromInputs();
        });
    if (audioSpeedInput)
        audioSpeedInput.addEventListener("input", () => {
            setAudioSpeedLabel(audioSpeedInput.value);
            updateAudioSettingsFromInputs();
        });
    if (audioPitchInput)
        audioPitchInput.addEventListener("input", () => {
            setAudioPitchLabel(audioPitchInput.value);
            updateAudioSettingsFromInputs();
        });
    if (selectedDeleteBtn) {
        selectedDeleteBtn.addEventListener("click", () => {
            const asset = getSelectedAsset();
            if (!asset) return;
            deleteAsset(asset);
        });
    }

    globalThis.addEventListener("keydown", (event) => {
        if (isFormInputElement(event.target)) {
            return;
        }

        const asset = getSelectedAsset();

        if ((event.key === "Delete" || event.key === "Backspace") && asset) {
            event.preventDefault();
            deleteAsset(asset);
            return;
        }

        if (!asset || isAudioAsset(asset)) {
            return;
        }

        const step = event.shiftKey ? KEYBOARD_NUDGE_FAST_STEP : KEYBOARD_NUDGE_STEP;
        let moved = false;

        switch (event.key) {
            case "ArrowUp":
                asset.y -= step;
                moved = true;
                break;
            case "ArrowDown":
                asset.y += step;
                moved = true;
                break;
            case "ArrowLeft":
                asset.x -= step;
                moved = true;
                break;
            case "ArrowRight":
                asset.x += step;
                moved = true;
                break;
            default:
                break;
        }

        if (moved) {
            event.preventDefault();
            updateRenderState(asset);
            schedulePersistTransform(asset);
            drawAndList();
        }
    });
    function connect() {
        const socket = new SockJS("/ws");
        stompClient = Stomp.over(socket);
        stompClient.connect(
            {},
            () => {
                stompClient.subscribe(`/topic/channel/${broadcaster}`, (payload) => {
                    const body = JSON.parse(payload.body);
                    handleEvent(body);
                });
                fetchAssets();
            },
            (error) => {
                console.warn("WebSocket connection issue", error);
                fetchAssets();
                setTimeout(
                    () => showToast("Live updates connection interrupted. Retrying may be necessary.", "warning"),
                    1000,
                );
            },
        );
    }

    function fetchAssets() {
        fetch(`/api/channels/${broadcaster}/assets`)
            .then((r) => {
                if (!r.ok) {
                    throw new Error("Failed to load assets");
                }
                return r.json();
            })
            .then(renderAssets)
            .catch(() => showToast("Unable to load assets. Please refresh.", "error"));
    }

    function fetchCanvasSettings() {
        return fetch(`/api/channels/${broadcaster}/canvas`)
            .then((r) => {
                if (!r.ok) {
                    throw new Error("Failed to load canvas");
                }
                return r.json();
            })
            .then((settings) => {
                applyCanvasSettings(settings);
            })
            .catch(() => {
                resizeCanvas();
                showToast("Using default canvas size. Unable to load saved settings.", "warning");
            });
    }

    function applyCanvasSettings(settings) {
        if (!settings) {
            return;
        }
        const width = Number.isFinite(settings.width) ? settings.width : canvasSettings.width;
        const height = Number.isFinite(settings.height) ? settings.height : canvasSettings.height;
        canvasSettings = { width, height };
        resizeCanvas();
    }

    function resizeCanvas() {
        if (!overlay) {
            return;
        }
        const bounds = overlay.getBoundingClientRect();
        const scale = Math.min(bounds.width / canvasSettings.width, bounds.height / canvasSettings.height);
        const displayWidth = canvasSettings.width * scale;
        const displayHeight = canvasSettings.height * scale;
        canvas.width = canvasSettings.width;
        canvas.height = canvasSettings.height;
        canvas.style.width = `${displayWidth}px`;
        canvas.style.height = `${displayHeight}px`;
        canvas.style.left = `${(bounds.width - displayWidth) / 2}px`;
        canvas.style.top = `${(bounds.height - displayHeight) / 2}px`;
        if (canvasResolutionLabel) {
            canvasResolutionLabel.textContent = `${canvasSettings.width} x ${canvasSettings.height}`;
        }
        if (canvasScaleLabel) {
            canvasScaleLabel.textContent = `${Math.round(scale * 100)}%`;
        }
        requestDraw();
    }

    function renderAssets(list) {
        layerOrder = [];
        scriptLayerOrder = [];
        list.forEach((item) => storeAsset(item, { placement: "append" }));
        drawAndList();
    }

    function storeAsset(asset, options = {}) {
        if (!asset) return;
        const placement = options.placement || "keep";
        const existing = assets.get(asset.id);
        const merged = existing ? { ...existing, ...asset } : { ...asset };
        const mediaChanged = existing && existing.url !== merged.url;
        const previewChanged = existing && existing.previewUrl !== merged.previewUrl;
        if (mediaChanged || previewChanged) {
            clearMedia(asset.id);
        }
        const parsedCreatedAt = merged.createdAt ? new Date(merged.createdAt).getTime() : NaN;
        const hasCreatedAtMs = typeof merged.createdAtMs === "number" && Number.isFinite(merged.createdAtMs);
        if (!hasCreatedAtMs) {
            merged.createdAtMs = Number.isFinite(parsedCreatedAt) ? parsedCreatedAt : Date.now();
        }
        assets.set(asset.id, merged);
        ensureLayerPosition(asset.id, existing ? "keep" : placement);
        if (!renderStates.has(asset.id)) {
            renderStates.set(asset.id, { ...merged });
        }
        resolvePendingUploadByName(asset.name);
    }

    function updateRenderState(asset) {
        if (!asset) return;
        const state = renderStates.get(asset.id) || {};
        state.x = asset.x;
        state.y = asset.y;
        state.width = asset.width;
        state.height = asset.height;
        state.rotation = asset.rotation;
        renderStates.set(asset.id, state);
    }

    function handleEvent(event) {
        if (event.type === "CANVAS" && event.payload) {
            applyCanvasSettings(event.payload);
            return;
        }
        const assetId = event.assetId || event?.patch?.id || event?.payload?.id;
        if (event.type === "DELETED") {
            assets.delete(assetId);
            layerOrder = layerOrder.filter((id) => id !== assetId);
            scriptLayerOrder = scriptLayerOrder.filter((id) => id !== assetId);
            clearMedia(assetId);
            renderStates.delete(assetId);
            loopPlaybackState.delete(assetId);
            cancelPendingTransform(assetId);
            if (selectedAssetId === assetId) {
                selectedAssetId = null;
            }
        } else if (event.patch) {
            applyPatch(assetId, event.patch);
        } else if (event.payload) {
            storeAsset(event.payload);
            if (!event.payload.hidden && !isVideoAsset(event.payload)) {
                ensureMedia(event.payload);
                if (isAudioAsset(event.payload) && !loopPlaybackState.has(event.payload.id)) {
                    loopPlaybackState.set(event.payload.id, true);
                }
            } else {
                clearMedia(event.payload.id);
                loopPlaybackState.delete(event.payload.id);
            }
        }
        drawAndList();
    }

    function applyPatch(assetId, patch) {
        if (!assetId || !patch) {
            return;
        }
        const existing = assets.get(assetId);
        if (!existing) {
            return;
        }
        const merged = { ...existing, ...patch };
        const isAudio = isAudioAsset(merged);
        const isScript = isCodeAsset(merged);
        if (patch.hidden) {
            clearMedia(assetId);
            loopPlaybackState.delete(assetId);
        }
        const targetOrder = Number.isFinite(patch.order) ? patch.order : null;
        if (!isAudio && Number.isFinite(targetOrder)) {
            if (isScript) {
                const currentOrder = getScriptLayerOrder().filter((id) => id !== assetId);
                const insertIndex = Math.max(0, currentOrder.length - Math.round(targetOrder));
                currentOrder.splice(insertIndex, 0, assetId);
                scriptLayerOrder = currentOrder;
            } else {
                const currentOrder = getLayerOrder().filter((id) => id !== assetId);
                const insertIndex = Math.max(0, currentOrder.length - Math.round(targetOrder));
                currentOrder.splice(insertIndex, 0, assetId);
                layerOrder = currentOrder;
            }
        }
        storeAsset(merged);
        if (!isAudio) {
            updateRenderState(merged);
        }
    }

    function drawAndList() {
        requestDraw();
        renderAssetList();
    }

    function requestDraw() {
        if (drawPending) {
            return;
        }
        drawPending = true;
        requestAnimationFrame(() => {
            drawPending = false;
            draw();
        });
    }

    function draw() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        getRenderOrder().forEach((asset) => drawAsset(asset));
    }

    function drawAsset(asset) {
        const renderState = smoothState(asset);
        const halfWidth = renderState.width / 2;
        const halfHeight = renderState.height / 2;
        ctx.save();
        ctx.translate(renderState.x + halfWidth, renderState.y + halfHeight);
        ctx.rotate((renderState.rotation * Math.PI) / 180);

        if (isCodeAsset(asset)) {
            ctx.restore();
            return;
        }

        if (isAudioAsset(asset)) {
            autoStartAudio(asset);
            ctx.restore();
            return;
        }

        let drawSource = null;
        let ready = false;
        let showPlayOverlay = false;
        if (isModelAsset(asset)) {
            const model = modelManager.ensureModel(asset);
            drawSource = model?.canvas || null;
            ready = !!model?.ready;
        } else if (isVideoAsset(asset) || isGifAsset(asset)) {
            drawSource = ensureCanvasPreview(asset);
            ready = isDrawable(drawSource);
            showPlayOverlay = true;
        } else {
            const media = ensureMedia(asset);
            drawSource = media?.isAnimated ? media.bitmap : media;
            ready = isDrawable(media);
        }
        if (ready && drawSource) {
            ctx.globalAlpha = asset.hidden ? 0.35 : 0.9;
            ctx.drawImage(drawSource, -halfWidth, -halfHeight, renderState.width, renderState.height);
        } else {
            ctx.globalAlpha = asset.hidden ? 0.2 : 0.4;
            ctx.fillStyle = "rgba(124, 58, 237, 0.35)";
            ctx.fillRect(-halfWidth, -halfHeight, renderState.width, renderState.height);
        }

        if (asset.hidden) {
            ctx.fillStyle = "rgba(15, 23, 42, 0.35)";
            ctx.fillRect(-halfWidth, -halfHeight, renderState.width, renderState.height);
        }

        ctx.globalAlpha = 1;
        ctx.strokeStyle = asset.id === selectedAssetId ? "rgba(124, 58, 237, 0.9)" : "rgba(255, 255, 255, 0.4)";
        ctx.lineWidth = asset.id === selectedAssetId ? 2 : 1;
        ctx.setLineDash(asset.id === selectedAssetId ? [6, 4] : []);
        ctx.strokeRect(-halfWidth, -halfHeight, renderState.width, renderState.height);
        if (showPlayOverlay) {
            drawPlayOverlay(renderState);
        }
        if (asset.id === selectedAssetId) {
            drawSelectionOverlay(renderState);
        }
        ctx.restore();
    }

    function smoothState(asset) {
        const previous = renderStates.get(asset.id) || { ...asset };
        const factor = interactionState && interactionState.assetId === asset.id ? 0.45 : 0.18;
        previous.x = lerp(previous.x, asset.x, factor);
        previous.y = lerp(previous.y, asset.y, factor);
        previous.width = lerp(previous.width, asset.width, factor);
        previous.height = lerp(previous.height, asset.height, factor);
        previous.rotation = smoothAngle(previous.rotation, asset.rotation, factor);
        renderStates.set(asset.id, previous);
        return previous;
    }

    function smoothAngle(current, target, factor) {
        let delta = ((target - current + 180) % 360) - 180;
        return current + delta * factor;
    }

    function lerp(a, b, t) {
        return a + (b - a) * t;
    }

    function drawPlayOverlay(asset) {
        const size = Math.max(24, Math.min(asset.width, asset.height) * 0.2);
        ctx.save();
        ctx.fillStyle = "rgba(15, 23, 42, 0.35)";
        ctx.beginPath();
        ctx.arc(0, 0, size * 0.75, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = "#ffffff";
        ctx.beginPath();
        ctx.moveTo(-size * 0.3, -size * 0.45);
        ctx.lineTo(size * 0.55, 0);
        ctx.lineTo(-size * 0.3, size * 0.45);
        ctx.closePath();
        ctx.fill();
        ctx.restore();
    }

    function drawSelectionOverlay(asset) {
        const halfWidth = asset.width / 2;
        const halfHeight = asset.height / 2;
        ctx.save();
        ctx.setLineDash([6, 4]);
        ctx.strokeStyle = "rgba(124, 58, 237, 0.9)";
        ctx.lineWidth = 1.5;
        ctx.strokeRect(-halfWidth, -halfHeight, asset.width, asset.height);

        const handles = getHandlePositions(asset);
        handles.forEach((handle) => {
            drawHandle(handle.x - halfWidth, handle.y - halfHeight, false);
        });

        drawHandle(0, -halfHeight - ROTATE_HANDLE_OFFSET, true);
        ctx.restore();
    }

    function drawHandle(x, y, isRotation) {
        ctx.save();
        ctx.setLineDash([]);
        ctx.fillStyle = isRotation ? "rgba(96, 165, 250, 0.9)" : "rgba(124, 58, 237, 0.9)";
        ctx.strokeStyle = "#0f172a";
        ctx.lineWidth = 1;
        if (isRotation) {
            ctx.beginPath();
            ctx.arc(x, y, HANDLE_SIZE * 0.65, 0, Math.PI * 2);
            ctx.fill();
            ctx.stroke();
        } else {
            ctx.fillRect(x - HANDLE_SIZE / 2, y - HANDLE_SIZE / 2, HANDLE_SIZE, HANDLE_SIZE);
            ctx.strokeRect(x - HANDLE_SIZE / 2, y - HANDLE_SIZE / 2, HANDLE_SIZE, HANDLE_SIZE);
        }
        ctx.restore();
    }

    function getHandlePositions(asset) {
        return [
            { x: 0, y: 0, type: "nw" },
            { x: asset.width / 2, y: 0, type: "n" },
            { x: asset.width, y: 0, type: "ne" },
            { x: asset.width, y: asset.height / 2, type: "e" },
            { x: asset.width, y: asset.height, type: "se" },
            { x: asset.width / 2, y: asset.height, type: "s" },
            { x: 0, y: asset.height, type: "sw" },
            { x: 0, y: asset.height / 2, type: "w" },
        ];
    }

    function rotatePoint(x, y, degrees) {
        const radians = (degrees * Math.PI) / 180;
        const cos = Math.cos(radians);
        const sin = Math.sin(radians);
        return {
            x: x * cos - y * sin,
            y: x * sin + y * cos,
        };
    }

    function pointerToLocal(asset, point) {
        const centerX = asset.x + asset.width / 2;
        const centerY = asset.y + asset.height / 2;
        const dx = point.x - centerX;
        const dy = point.y - centerY;
        const rotated = rotatePoint(dx, dy, -asset.rotation);
        return {
            x: rotated.x + asset.width / 2,
            y: rotated.y + asset.height / 2,
        };
    }

    function angleFromCenter(asset, point) {
        const centerX = asset.x + asset.width / 2;
        const centerY = asset.y + asset.height / 2;
        return (Math.atan2(point.y - centerY, point.x - centerX) * 180) / Math.PI;
    }

    function hitHandle(asset, point) {
        const local = pointerToLocal(asset, point);
        const tolerance = HANDLE_SIZE * 1.2;
        const rotationDistance = Math.hypot(local.x - asset.width / 2, local.y + ROTATE_HANDLE_OFFSET);
        if (Math.abs(local.y + ROTATE_HANDLE_OFFSET) <= tolerance && rotationDistance <= tolerance * 1.5) {
            return "rotate";
        }
        for (const handle of getHandlePositions(asset)) {
            if (Math.abs(local.x - handle.x) <= tolerance && Math.abs(local.y - handle.y) <= tolerance) {
                return handle.type;
            }
        }
        return null;
    }

    function cursorForHandle(handle) {
        switch (handle) {
            case "nw":
            case "se":
                return "nwse-resize";
            case "ne":
            case "sw":
                return "nesw-resize";
            case "n":
            case "s":
                return "ns-resize";
            case "e":
            case "w":
                return "ew-resize";
            case "rotate":
                return "grab";
            default:
                return "default";
        }
    }

    function resizeFromHandle(state, point) {
        const asset = assets.get(state.assetId);
        if (!asset) return;
        const basis = state.original;
        const local = pointerToLocal(basis, point);
        const handle = state.handle;
        const minSize = 10;

        let nextWidth = basis.width;
        let nextHeight = basis.height;
        let offsetX = 0;
        let offsetY = 0;

        if (handle.includes("e")) {
            nextWidth = basis.width + (local.x - state.startLocal.x);
        }
        if (handle.includes("s")) {
            nextHeight = basis.height + (local.y - state.startLocal.y);
        }
        if (handle.includes("w")) {
            nextWidth = basis.width - (local.x - state.startLocal.x);
        }
        if (handle.includes("n")) {
            nextHeight = basis.height - (local.y - state.startLocal.y);
        }

        const ratio = isAspectLocked(asset.id)
            ? getAssetAspectRatio(asset) || basis.width / Math.max(basis.height, 1)
            : null;
        if (ratio) {
            const widthChanged = handle.includes("e") || handle.includes("w");
            const heightChanged = handle.includes("n") || handle.includes("s");
            if (widthChanged && !heightChanged) {
                nextHeight = nextWidth / ratio;
            } else if (!widthChanged && heightChanged) {
                nextWidth = nextHeight * ratio;
            } else {
                if (Math.abs(nextWidth - basis.width) > Math.abs(nextHeight - basis.height)) {
                    nextHeight = nextWidth / ratio;
                } else {
                    nextWidth = nextHeight * ratio;
                }
            }
        }

        nextWidth = Math.max(minSize, nextWidth);
        nextHeight = Math.max(minSize, nextHeight);

        if (handle.includes("w")) {
            offsetX = basis.width - nextWidth;
        }
        if (handle.includes("n")) {
            offsetY = basis.height - nextHeight;
        }

        const shift = rotatePoint(offsetX, offsetY, basis.rotation);
        asset.x = basis.x + shift.x;
        asset.y = basis.y + shift.y;
        asset.width = nextWidth;
        asset.height = nextHeight;
        updateRenderState(asset);
        requestDraw();
    }

    function updateHoverCursor(point) {
        const asset = getSelectedAsset();
        if (asset) {
            const handle = hitHandle(asset, point);
            if (handle) {
                canvas.style.cursor = cursorForHandle(handle);
                return;
            }
        }
        const hit = findAssetAtPoint(point.x, point.y);
        canvas.style.cursor = hit ? "move" : "default";
    }

    function isJavaScriptFile(file) {
        if (!file) {
            return false;
        }
        const type = (file.type || "").toLowerCase();
        if (type.includes("javascript")) {
            return true;
        }
        const name = (file.name || "").toLowerCase();
        return name.endsWith(".js") || name.endsWith(".mjs");
    }

    function getAssetTypeLabel(asset) {
        const type = asset?.assetType;
        if (type) {
            const lookup = {
                IMAGE: "Image",
                VIDEO: "Video",
                AUDIO: "Audio",
                MODEL: "3D Model",
                SCRIPT: "Script",
                OTHER: "Other",
            };
            return lookup[type] || "Other";
        }
        if (isCodeAsset(asset)) {
            return "Script";
        }
        const raw = asset?.originalMediaType || asset?.mediaType || "";
        if (!raw) {
            return "Other";
        }
        const parts = raw.split("/");
        return parts.length > 1 ? parts[0].toUpperCase() : raw.toUpperCase();
    }

    function getDisplayMediaType(asset) {
        return getAssetTypeLabel(asset);
    }

    function getCachedSource(element) {
        return element?.dataset?.sourceUrl || element?.src || null;
    }

    function isDrawable(element) {
        if (!element) {
            return false;
        }
        if (element.isAnimated) {
            return !!element.bitmap;
        }
        if (isVideoElement(element)) {
            return element.readyState >= 2;
        }
        if (typeof ImageBitmap !== "undefined" && element instanceof ImageBitmap) {
            return true;
        }
        return !!element.complete;
    }

    function clearMedia(assetId) {
        mediaCache.delete(assetId);
        modelManager.clearModel(assetId);
        const cachedPreview = previewCache.get(assetId);
        if (cachedPreview && cachedPreview.startsWith("blob:")) {
            URL.revokeObjectURL(cachedPreview);
        }
        previewCache.delete(assetId);
        previewImageCache.delete(assetId);
        const animated = animatedCache.get(assetId);
        if (animated) {
            animated.cancelled = true;
            clearTimeout(animated.timeout);
            animated.bitmap?.close?.();
            animated.decoder?.close?.();
            animatedCache.delete(assetId);
        }
        const audio = audioControllers.get(assetId);
        if (audio) {
            if (audio.delayTimeout) {
                clearTimeout(audio.delayTimeout);
            }
            audio.element.pause();
            audio.element.currentTime = 0;
            audioControllers.delete(assetId);
        }
    }

    function ensureAudioController(asset) {
        const cached = audioControllers.get(asset.id);
        if (cached && cached.src === asset.url) {
            applyAudioSettings(cached, asset);
            return cached;
        }

        if (cached) {
            clearMedia(asset.id);
        }

        const element = new Audio(asset.url);
        element.autoplay = true;
        element.controls = true;
        element.preload = "auto";
        element.addEventListener("loadedmetadata", () => recordDuration(asset.id, element.duration));
        const controller = {
            id: asset.id,
            src: asset.url,
            element,
            delayTimeout: null,
            loopEnabled: false,
            delayMs: 0,
            baseDelayMs: 0,
        };
        element.onended = () => handleAudioEnded(asset.id);
        audioControllers.set(asset.id, controller);
        applyAudioSettings(controller, asset, true);
        return controller;
    }

    function applyAudioSettings(controller, asset, resetPosition = false) {
        controller.loopEnabled = !!asset.audioLoop;
        controller.baseDelayMs = Math.max(0, asset.audioDelayMillis || 0);
        controller.delayMs = controller.baseDelayMs;
        const speed = Math.max(0.25, asset.audioSpeed || 1);
        const pitch = Math.max(0.5, asset.audioPitch || 1);
        controller.element.playbackRate = speed * pitch;
        const volume = clamp(asset.audioVolume ?? 1, SETTINGS.minAssetVolumeFraction, SETTINGS.maxAssetVolumeFraction);
        controller.element.volume = volume;
        if (resetPosition) {
            controller.element.currentTime = 0;
            controller.element.pause();
        }
    }

    function handleAudioEnded(assetId) {
        const controller = audioControllers.get(assetId);
        if (!controller) return;
        controller.element.currentTime = 0;
        if (controller.delayTimeout) {
            clearTimeout(controller.delayTimeout);
        }
        if (controller.loopEnabled) {
            controller.delayTimeout = setTimeout(() => {
                safePlay(controller);
            }, controller.delayMs);
        } else {
            controller.element.pause();
        }
    }

    function stopAudio(assetId) {
        const controller = audioControllers.get(assetId);
        if (!controller) return;
        if (controller.delayTimeout) {
            clearTimeout(controller.delayTimeout);
        }
        controller.element.pause();
        controller.element.currentTime = 0;
        controller.delayTimeout = null;
        controller.delayMs = controller.baseDelayMs;
    }

    function autoStartAudio(asset) {
        if (!isAudioAsset(asset) || asset.hidden) {
            return;
        }
        ensureAudioController(asset);
    }

    function ensureMedia(asset) {
        const cached = mediaCache.get(asset.id);
        const cachedSource = getCachedSource(cached);
        if (cached && cachedSource !== asset.url) {
            clearMedia(asset.id);
        }
        if (cached && cachedSource === asset.url) {
            applyMediaSettings(cached, asset);
            return cached;
        }

        if (isAudioAsset(asset)) {
            ensureAudioController(asset);
            mediaCache.delete(asset.id);
            return null;
        }

        if (isModelAsset(asset)) {
            return null;
        }

        if (isVideoAsset(asset)) {
            return null;
        }

        if (isGifAsset(asset) && "ImageDecoder" in globalThis) {
            const animated = ensureAnimatedImage(asset);
            if (animated) {
                mediaCache.set(asset.id, animated);
                return animated;
            }
        }

        const element = isVideoAsset(asset) ? document.createElement("video") : new Image();
        element.dataset.sourceUrl = asset.url;
        element.crossOrigin = "anonymous";
        if (isVideoElement(element)) {
            element.loop = true;
            const volume = clamp(
                asset.audioVolume ?? 1,
                SETTINGS.minAssetVolumeFraction,
                SETTINGS.maxAssetVolumeFraction,
            );
            element.muted = volume === 0;
            element.volume = Math.min(volume, 1);
            element.playsInline = true;
            element.autoplay = false;
            element.preload = "metadata";
            element.onloadeddata = requestDraw;
            element.onloadedmetadata = () => recordDuration(asset.id, element.duration);
            element.src = asset.url;
            const playback = asset.speed ?? 1;
            element.playbackRate = Math.max(playback, 0.01);
            element.pause();
        } else {
            element.onload = requestDraw;
            element.src = asset.url;
        }
        mediaCache.set(asset.id, element);
        return element;
    }

    function ensureAnimatedImage(asset) {
        const cached = animatedCache.get(asset.id);
        if (cached && cached.url === asset.url) {
            return cached;
        }

        if (cached) {
            clearMedia(asset.id);
        }

        const controller = {
            id: asset.id,
            url: asset.url,
            src: asset.url,
            decoder: null,
            bitmap: null,
            timeout: null,
            cancelled: false,
            isAnimated: true,
        };

        fetch(asset.url)
            .then((r) => r.blob())
            .then((blob) => new ImageDecoder({ data: blob, type: blob.type || "image/gif" }))
            .then((decoder) => {
                if (controller.cancelled) {
                    decoder.close?.();
                    return null;
                }
                controller.decoder = decoder;
                scheduleNextFrame(controller);
                return controller;
            })
            .catch(() => {
                animatedCache.delete(asset.id);
            });

        animatedCache.set(asset.id, controller);
        return controller;
    }

    function scheduleNextFrame(controller) {
        if (controller.cancelled || !controller.decoder) {
            return;
        }
        controller.decoder
            .decode()
            .then(({ image, complete }) => {
                if (controller.cancelled) {
                    image.close?.();
                    return;
                }
                controller.bitmap?.close?.();
                createImageBitmap(image)
                    .then((bitmap) => {
                        controller.bitmap = bitmap;
                        requestDraw();
                    })
                    .finally(() => image.close?.());

                const durationMicros = image.duration || 0;
                const delay = durationMicros > 0 ? durationMicros / 1000 : 100;
                const hasMore = !complete;
                controller.timeout = setTimeout(() => {
                    if (controller.cancelled) {
                        return;
                    }
                    if (hasMore) {
                        scheduleNextFrame(controller);
                    } else {
                        controller.decoder.reset();
                        scheduleNextFrame(controller);
                    }
                }, delay);
            })
            .catch(() => {
                animatedCache.delete(controller.id);
            });
    }

    function applyMediaSettings(element, asset) {
        if (!isVideoElement(element)) {
            return;
        }
        const nextSpeed = asset.speed ?? 1;
        const effectiveSpeed = Math.max(nextSpeed, 0.01);
        if (element.playbackRate !== effectiveSpeed) {
            element.playbackRate = effectiveSpeed;
        }
        const volume = clamp(asset.audioVolume ?? 1, SETTINGS.minAssetVolumeFraction, SETTINGS.maxAssetVolumeFraction);
        element.muted = volume === 0;
        element.volume = Math.min(volume, 1);
        if (nextSpeed === 0) {
            element.pause();
            return;
        }
        const playPromise = element.play();
        if (playPromise?.catch) {
            playPromise.catch(() => { });
        }
    }

    function getLayerDetail(asset) {
        if (!asset || isAudioAsset(asset)) {
            return null;
        }
        if (isCodeAsset(asset)) {
            return `Order ${getScriptLayerPosition(asset.id)}`;
        }
        return `Order ${getLayerPosition(asset.id)}`;
    }

    function createSectionHeader(title) {
        const li = document.createElement("li");
        li.className = "asset-section";
        li.textContent = title;
        return li;
    }

    function appendAssetListItem(list, asset) {
        const li = document.createElement("li");
        li.className = "asset-item";
        if (asset.id === selectedAssetId) {
            li.classList.add("selected");
        }
        li.classList.toggle("is-hidden", !!asset.hidden);

        const row = document.createElement("div");
        row.className = "asset-row";

        const preview = createPreviewElement(asset);

        const meta = document.createElement("div");
        meta.className = "meta";
        const name = document.createElement("strong");
        name.textContent = asset.name || `Asset ${asset.id.slice(0, 6)}`;
        const details = document.createElement("small");
        const layerDetail = getLayerDetail(asset);
        details.textContent = layerDetail ? `${getAssetTypeLabel(asset)} · ${layerDetail}` : getAssetTypeLabel(asset);
        meta.appendChild(name);
        meta.appendChild(details);

        const actions = document.createElement("div");
        actions.className = "actions";

        if (isCodeAsset(asset)) {
            const editBtn = document.createElement("button");
            editBtn.type = "button";
            editBtn.className = "ghost icon-button";
            editBtn.innerHTML = '<i class="fa-solid fa-code"></i>';
            editBtn.title = "Edit script";
            editBtn.addEventListener("click", (e) => {
                e.stopPropagation();
                customAssetModal?.openEditor?.(asset);
            });
            actions.appendChild(editBtn);
        }

        if (!isAudioAsset(asset)) {
            const ordered = getLayeredAssets(asset);
            const orderIndex = ordered.findIndex((item) => item.id === asset.id);
            const canMoveUp = orderIndex > 0;
            const canMoveDown = orderIndex !== -1 && orderIndex < ordered.length - 1;
            const moveUp = document.createElement("button");
            moveUp.type = "button";
            moveUp.className = "ghost icon-button";
            moveUp.innerHTML = '<i class="fa-solid fa-arrow-up"></i>';
            moveUp.title = isCodeAsset(asset) ? "Move script up" : "Move asset up";
            moveUp.disabled = !canMoveUp;
            moveUp.addEventListener("click", (e) => {
                e.stopPropagation();
                moveLayerItem(asset, "up");
            });
            const moveDown = document.createElement("button");
            moveDown.type = "button";
            moveDown.className = "ghost icon-button";
            moveDown.innerHTML = '<i class="fa-solid fa-arrow-down"></i>';
            moveDown.title = isCodeAsset(asset) ? "Move script down" : "Move asset down";
            moveDown.disabled = !canMoveDown;
            moveDown.addEventListener("click", (e) => {
                e.stopPropagation();
                moveLayerItem(asset, "down");
            });
            actions.appendChild(moveUp);
            actions.appendChild(moveDown);
        }

        if (isAudioAsset(asset)) {
            const playBtn = document.createElement("button");
            playBtn.type = "button";
            playBtn.className = "ghost icon-button";
            const isLooping = !!asset.audioLoop;
            const isPlayingLoop = getLoopPlaybackState(asset);
            updatePlayButtonIcon(playBtn, isLooping, isPlayingLoop);
            playBtn.title = isLooping
                ? isPlayingLoop
                    ? "Pause looping audio"
                    : "Play looping audio"
                : "Play audio";
            playBtn.addEventListener("click", (e) => {
                e.stopPropagation();
                const nextPlay = isLooping ? !(loopPlaybackState.get(asset.id) ?? getLoopPlaybackState(asset)) : true;
                if (isLooping) {
                    loopPlaybackState.set(asset.id, nextPlay);
                    updatePlayButtonIcon(playBtn, true, nextPlay);
                    playBtn.title = nextPlay ? "Pause looping audio" : "Play looping audio";
                }
                triggerAudioPlayback(asset, nextPlay);
            });
            actions.appendChild(playBtn);
        }

        if (!isAudioAsset(asset) && !isCodeAsset(asset)) {
            const toggleBtn = document.createElement("button");
            toggleBtn.type = "button";
            toggleBtn.className = "ghost icon-button";
            toggleBtn.innerHTML = `<i class="fa-solid ${asset.hidden ? "fa-eye" : "fa-eye-slash"}"></i>`;
            toggleBtn.title = asset.hidden ? "Show asset" : "Hide asset";
            toggleBtn.addEventListener("click", (e) => {
                e.stopPropagation();
                selectedAssetId = asset.id;
                updateVisibility(asset, !asset.hidden);
            });
            actions.appendChild(toggleBtn);
        }

        row.appendChild(preview);
        row.appendChild(meta);
        row.appendChild(actions);

        li.addEventListener("click", () => {
            selectedAssetId = asset.id;
            updateRenderState(asset);
            drawAndList();
        });

        li.appendChild(row);
        list.appendChild(li);
    }

    function renderAssetList() {
        const list = document.getElementById("asset-list");
        if (controlsPlaceholder && controlsPanel && controlsPanel.parentElement !== controlsPlaceholder) {
            controlsPlaceholder.appendChild(controlsPanel);
        }
        if (controlsPanel) {
            controlsPanel.classList.add("hidden");
        }
        list.innerHTML = "";

        const hasAssets = assets.size > 0;
        const hasPending = pendingUploads.length > 0;

        if (!hasAssets && !hasPending) {
            selectedAssetId = null;
            if (assetInspector) {
                assetInspector.classList.add("hidden");
            }
            const empty = document.createElement("li");
            empty.textContent = "";
            list.appendChild(empty);
            updateSelectedAssetControls();
            return;
        }

        if (assetInspector) {
            assetInspector.classList.toggle("hidden", !hasAssets);
        }

        const pendingItems = [...pendingUploads].sort((a, b) => (a.createdAtMs || 0) - (b.createdAtMs || 0));
        pendingItems.forEach((pending) => {
            list.appendChild(createPendingListItem(pending));
        });

        const codeAssets = getCodeAssets();
        const audioAssets = getAudioAssets();
        const visualAssets = getAssetsByLayer();

        if (visualAssets.length) {
            list.appendChild(createSectionHeader("Canvas assets"));
            visualAssets.forEach((asset) => appendAssetListItem(list, asset));
        }
        if (audioAssets.length) {
            list.appendChild(createSectionHeader("Audio assets"));
            audioAssets.forEach((asset) => appendAssetListItem(list, asset));
        }
        if (codeAssets.length) {
            list.appendChild(createSectionHeader("Script assets"));
            codeAssets.forEach((asset) => appendAssetListItem(list, asset));
        }

        updateSelectedAssetControls();
    }

    function createPendingListItem(pending) {
        const li = document.createElement("li");
        li.className = "asset-item pending";

        const row = document.createElement("div");
        row.className = "asset-row";

        const preview = document.createElement("div");
        preview.className = "asset-preview pending-preview";
        preview.innerHTML = '<i class="fa-solid fa-cloud-arrow-up" aria-hidden="true"></i>';

        const meta = document.createElement("div");
        meta.className = "meta";
        const name = document.createElement("strong");
        name.textContent = pending?.name || "Uploading asset";
        const details = document.createElement("small");
        details.textContent = pending.status === "processing" ? "Processing upload…" : "Uploading…";
        meta.appendChild(name);
        meta.appendChild(details);

        const progress = document.createElement("div");
        progress.className = "upload-progress";
        const bar = document.createElement("div");
        bar.className = "upload-progress-bar";
        if (pending.status === "processing") {
            bar.classList.add("is-processing");
        }
        progress.appendChild(bar);
        meta.appendChild(progress);

        row.appendChild(preview);
        row.appendChild(meta);
        li.appendChild(row);

        return li;
    }

    function createBadge(label, extraClass = "") {
        const badge = document.createElement("span");
        badge.className = `badge ${extraClass}`.trim();
        badge.textContent = label;
        return badge;
    }

    function getLoopPlaybackState(asset) {
        if (!isAudioAsset(asset) || !asset.audioLoop) {
            return false;
        }
        if (loopPlaybackState.has(asset.id)) {
            return loopPlaybackState.get(asset.id);
        }
        const isVisible = asset.hidden === false || asset.hidden === undefined;
        loopPlaybackState.set(asset.id, isVisible);
        return isVisible;
    }

    function updatePlayButtonIcon(button, isLooping, isPlayingLoop) {
        const icon = isLooping ? (isPlayingLoop ? "fa-pause" : "fa-play") : "fa-play";
        button.innerHTML = `<i class="fa-solid ${icon}"></i>`;
    }

    function createPreviewElement(asset) {
        if (isCodeAsset(asset)) {
            if (asset.logoUrl) {
                const img = document.createElement("img");
                img.className = "asset-preview";
                img.src = asset.logoUrl;
                img.alt = asset.name || "Script logo";
                img.loading = "lazy";
                return img;
            }
            const icon = document.createElement("div");
            icon.className = "asset-preview code-icon";
            icon.innerHTML = '<i class="fa-solid fa-code" aria-hidden="true"></i>';
            return icon;
        }
        if (isAudioAsset(asset)) {
            const icon = document.createElement("div");
            icon.className = "asset-preview audio-icon";
            icon.innerHTML = '<i class="fa-solid fa-music" aria-hidden="true"></i>';
            return icon;
        }
        if (isVideoAsset(asset) || isGifAsset(asset)) {
            const still = document.createElement("div");
            still.className = "asset-preview still";
            still.setAttribute("aria-label", asset.name || "Asset preview");

            const overlay = document.createElement("div");
            overlay.className = "preview-overlay";
            overlay.innerHTML = '<i class="fa-solid fa-play"></i>';
            still.appendChild(overlay);

            loadPreviewFrame(asset, still);
            return still;
        }

        const img = document.createElement("img");
        img.className = "asset-preview";
        img.src = asset.url;
        img.alt = asset.name || "Asset preview";
        img.loading = "lazy";
        return img;
    }

    function fetchPreviewData(asset) {
        if (!asset) return Promise.resolve(null);
        const cached = previewCache.get(asset.id);
        if (cached) {
            return Promise.resolve(cached);
        }

        const fallback = () => {
            const fallbackPromise = isVideoAsset(asset)
                ? captureVideoFrame(asset)
                : isGifAsset(asset)
                    ? captureGifFrame(asset)
                    : Promise.resolve(null);
            return fallbackPromise.then((result) => {
                if (!result) {
                    return null;
                }
                previewCache.set(asset.id, result);
                return result;
            });
        };

        if (!asset.previewUrl) {
            return fallback();
        }

        return new Promise((resolve) => {
            const img = new Image();
            img.onload = () => {
                previewCache.set(asset.id, asset.previewUrl);
                resolve(asset.previewUrl);
            };
            img.onerror = () => fallback().then(resolve);
            img.src = asset.previewUrl;
        }).catch(() => null);
    }

    function loadPreviewFrame(asset, element) {
        if (!asset || !element) return;
        fetchPreviewData(asset)
            .then((dataUrl) => {
                if (!dataUrl) return;
                applyPreviewFrame(element, dataUrl);
            })
            .catch(() => { });
    }

    function applyPreviewFrame(element, dataUrl) {
        if (!element || !dataUrl) return;
        element.style.backgroundImage = `url(${dataUrl})`;
        element.classList.add("has-image");
    }

    function ensureCanvasPreview(asset) {
        const cachedData = previewCache.get(asset.id);
        const cachedImage = previewImageCache.get(asset.id);
        if (cachedData && cachedImage?.src === cachedData) {
            return cachedImage.image;
        }

        if (cachedData) {
            const img = new Image();
            img.crossOrigin = "anonymous";
            img.onload = requestDraw;
            img.src = cachedData;
            previewImageCache.set(asset.id, { src: cachedData, image: img });
            return img;
        }

        fetchPreviewData(asset)
            .then((dataUrl) => {
                if (!dataUrl) return;
                const img = new Image();
                img.crossOrigin = "anonymous";
                img.onload = requestDraw;
                img.src = dataUrl;
                previewImageCache.set(asset.id, { src: dataUrl, image: img });
            })
            .catch(() => { });

        return null;
    }

    function captureVideoFrame(asset) {
        return new Promise((resolve) => {
            const video = document.createElement("video");
            video.crossOrigin = "anonymous";
            video.preload = "auto";
            video.muted = true;
            video.playsInline = true;
            video.src = asset.url;

            video.addEventListener("loadedmetadata", () => recordDuration(asset.id, video.duration), { once: true });

            const cleanup = () => {
                video.pause();
                video.removeAttribute("src");
                video.load();
            };

            video.addEventListener(
                "loadeddata",
                () => {
                    const canvas = document.createElement("canvas");
                    canvas.width = video.videoWidth || asset.width || 0;
                    canvas.height = video.videoHeight || asset.height || 0;
                    if (!canvas.width || !canvas.height) {
                        cleanup();
                        resolve(null);
                        return;
                    }
                    const context = canvas.getContext("2d");
                    context.drawImage(video, 0, 0, canvas.width, canvas.height);
                    try {
                        const dataUrl = canvas.toDataURL("image/png");
                        resolve(dataUrl);
                    } catch (err) {
                        resolve(null);
                    }
                    cleanup();
                },
                { once: true },
            );

            video.addEventListener(
                "error",
                () => {
                    cleanup();
                    resolve(null);
                },
                { once: true },
            );
        });
    }

    function captureGifFrame(asset) {
        if (!("ImageDecoder" in globalThis)) {
            return Promise.resolve(null);
        }
        return fetch(asset.url)
            .then((r) => r.blob())
            .then((blob) => new ImageDecoder({ data: blob, type: blob.type || "image/gif" }))
            .then((decoder) => decoder.decode({ frameIndex: 0 }))
            .then(({ image }) => {
                const canvas = document.createElement("canvas");
                canvas.width = image.displayWidth || asset.width || 0;
                canvas.height = image.displayHeight || asset.height || 0;
                const ctx2d = canvas.getContext("2d");
                ctx2d.drawImage(image, 0, 0, canvas.width, canvas.height);
                image.close?.();
                try {
                    return canvas.toDataURL("image/png");
                } catch (err) {
                    return null;
                }
            })
            .catch(() => null);
    }

    function getSelectedAsset() {
        return selectedAssetId ? assets.get(selectedAssetId) : null;
    }

    function updateSelectedAssetControls(asset = getSelectedAsset()) {
        if (controlsPlaceholder && controlsPanel && controlsPanel.parentElement !== controlsPlaceholder) {
            controlsPlaceholder.appendChild(controlsPanel);
        }

        updateSelectedAssetSummary(asset);

        if (!controlsPanel || !asset) {
            if (controlsPanel) controlsPanel.classList.add("hidden");
            return;
        }

        controlsPanel.classList.remove("hidden");
        lastSizeInputChanged = null;
        if (selectedOrderLabel) {
            selectedOrderLabel.textContent = isCodeAsset(asset)
                ? getScriptLayerPosition(asset.id)
                : getLayerPosition(asset.id);
        }

        if (widthInput) widthInput.value = Math.round(asset.width);
        if (heightInput) heightInput.value = Math.round(asset.height);
        if (aspectLockInput) {
            aspectLockInput.checked = isAspectLocked(asset.id);
            aspectLockInput.onchange = () => setAspectLock(asset.id, aspectLockInput.checked);
        }
        const hideLayout = isAudioAsset(asset) || isCodeAsset(asset);
        if (layoutSection) {
            layoutSection.classList.toggle("hidden", hideLayout);
            const layoutControls = layoutSection.querySelectorAll("input, button");
            layoutControls.forEach((control) => {
                control.disabled = hideLayout;
                control.classList.toggle("disabled", hideLayout);
            });
        }
        if (assetActionButtons.length) {
            assetActionButtons.forEach((button) => {
                const allowForAudio = button.dataset.audioEnabled === "true";
                const allowForCode = button.dataset.codeEnabled === "true";
                const disableButton = hideLayout && !(allowForAudio || allowForCode);
                button.disabled = disableButton;
                button.classList.toggle("disabled", disableButton);
            });
        }
        if (speedInput) {
            const percent = Math.round((asset.speed ?? 1) * 100);
            speedInput.value = Math.min(1000, Math.max(0, percent));
            setSpeedLabel(speedInput.value);
        }
        if (playbackSection) {
            const shouldShowPlayback = isVideoAsset(asset);
            playbackSection.classList.toggle("hidden", !shouldShowPlayback);
            speedInput?.classList?.toggle("disabled", !shouldShowPlayback);
        }
        if (volumeSection) {
            const showVolume = isAudioAsset(asset) || isVideoAsset(asset);
            volumeSection.classList.toggle("hidden", !showVolume);
            const volumeControls = volumeSection.querySelectorAll("input");
            volumeControls.forEach((control) => {
                control.disabled = !showVolume;
                control.classList.toggle("disabled", !showVolume);
            });
            if (showVolume && volumeInput) {
                const sliderValue = volumeToSlider(asset.audioVolume ?? 1);
                volumeInput.value = sliderValue;
                setVolumeLabel(sliderValue);
            }
        }
        if (audioSection) {
            const showAudio = isAudioAsset(asset);
            audioSection.classList.toggle("hidden", !showAudio);
            const audioInputs = [audioLoopInput, audioDelayInput, audioSpeedInput, audioPitchInput];
            audioInputs.forEach((input) => {
                if (!input) return;
                input.disabled = !showAudio;
                input.parentElement?.classList?.toggle("disabled", !showAudio);
            });
            if (showAudio) {
                audioLoopInput.checked = !!asset.audioLoop;
                const delayMs = clamp(Math.max(0, asset.audioDelayMillis ?? 0), 0, 30000);
                audioDelayInput.value = delayMs;
                setAudioDelayLabel(delayMs);
                const audioSpeedPercent = clamp(Math.round(Math.max(0.25, asset.audioSpeed ?? 1) * 100), 25, 400);
                audioSpeedInput.value = audioSpeedPercent;
                setAudioSpeedLabel(audioSpeedPercent);
                const pitchPercent = clamp(Math.round(Math.max(0.5, asset.audioPitch ?? 1) * 100), 50, 200);
                audioPitchInput.value = pitchPercent;
                setAudioPitchLabel(pitchPercent);
            }
        }
    }

    function updateSelectedAssetSummary(asset) {
        if (assetInspector) {
            assetInspector.classList.toggle("hidden", !asset && !assets.size);
        }

        ensureDurationMetadata(asset);

        if (selectedAssetName) {
            selectedAssetName.textContent = asset ? asset.name || `Asset ${asset.id.slice(0, 6)}` : "Choose an asset";
        }
        if (selectedAssetMeta) {
            selectedAssetMeta.textContent = asset
                ? getDisplayMediaType(asset)
                : "Pick an asset in the list to adjust its placement and playback.";
        }
        if (selectedAssetResolution) {
            if (asset) {
                if (isCodeAsset(asset) || isAudioAsset(asset)) {
                    selectedAssetResolution.textContent = "";
                    selectedAssetResolution.classList.add("hidden");
                } else {
                    selectedAssetResolution.textContent = `${Math.round(asset.width)}×${Math.round(asset.height)}`;
                    selectedAssetResolution.classList.remove("hidden");
                }
            } else {
                selectedAssetResolution.textContent = "";
                selectedAssetResolution.classList.add("hidden");
            }
        }
        if (selectedAssetIdLabel) {
            if (asset) {
                selectedAssetIdLabel.textContent = `ID: ${asset.id}`;
                selectedAssetIdLabel.classList.remove("hidden");
            } else {
                selectedAssetIdLabel.classList.add("hidden");
                selectedAssetIdLabel.textContent = "";
            }
        }
        if (selectedAssetBadges) {
            selectedAssetBadges.innerHTML = "";
            if (asset) {
                selectedAssetBadges.appendChild(createBadge(getDisplayMediaType(asset)));
                if (isCodeAsset(asset)) {
                    selectedAssetBadges.appendChild(
                        createBadge(`Script order ${getScriptLayerPosition(asset.id)}`, "subtle"),
                    );
                    selectedAssetBadges.appendChild(createBadge("Above canvas assets", "subtle"));
                } else if (!isAudioAsset(asset)) {
                    selectedAssetBadges.appendChild(createBadge(`Order ${getLayerPosition(asset.id)}`, "subtle"));
                }
                const aspectLabel = !isAudioAsset(asset) && !isCodeAsset(asset) ? formatAspectRatioLabel(asset) : "";
                if (aspectLabel) {
                    selectedAssetBadges.appendChild(createBadge(aspectLabel, "subtle"));
                }
                const durationLabel = getDurationBadge(asset);
                if (durationLabel) {
                    selectedAssetBadges.appendChild(createBadge(durationLabel, "subtle"));
                }
            }
        }
        if (selectedEditBtn) {
            selectedEditBtn.disabled = !asset || !isCodeAsset(asset);
            selectedEditBtn.onclick = null;
            if (asset && isCodeAsset(asset)) {
                selectedEditBtn.onclick = () => customAssetModal?.openEditor?.(asset);
            }
        }
        if (selectedVisibilityBtn) {
            selectedVisibilityBtn.disabled = !asset;
            selectedVisibilityBtn.onclick = null;
            if (asset && isAudioAsset(asset)) {
                const isLooping = !!asset.audioLoop;
                const isPlayingLoop = getLoopPlaybackState(asset);
                updatePlayButtonIcon(selectedVisibilityBtn, isLooping, isPlayingLoop);
                selectedVisibilityBtn.title = isLooping
                    ? isPlayingLoop
                        ? "Pause looping audio"
                        : "Play looping audio"
                    : "Play audio";
                selectedVisibilityBtn.onclick = () => {
                    const nextPlay = isLooping
                        ? !(loopPlaybackState.get(asset.id) ?? getLoopPlaybackState(asset))
                        : true;
                    if (isLooping) {
                        loopPlaybackState.set(asset.id, nextPlay);
                        updatePlayButtonIcon(selectedVisibilityBtn, true, nextPlay);
                        selectedVisibilityBtn.title = nextPlay ? "Pause looping audio" : "Play looping audio";
                    }
                    triggerAudioPlayback(asset, nextPlay);
                };
            } else if (asset && isCodeAsset(asset)) {
                selectedVisibilityBtn.disabled = true;
                selectedVisibilityBtn.title = "Script assets do not render on the canvas";
                selectedVisibilityBtn.innerHTML = '<i class="fa-solid fa-eye-slash"></i>';
            } else if (asset) {
                selectedVisibilityBtn.title = asset.hidden ? "Show asset" : "Hide asset";
                selectedVisibilityBtn.innerHTML = `<i class="fa-solid ${asset.hidden ? "fa-eye" : "fa-eye-slash"}"></i>`;
                selectedVisibilityBtn.onclick = () => updateVisibility(asset, !asset.hidden);
            } else {
                selectedVisibilityBtn.title = "Toggle visibility";
                selectedVisibilityBtn.innerHTML = '<i class="fa-solid fa-eye-slash"></i>';
            }
        }
        if (selectedDeleteBtn) {
            selectedDeleteBtn.disabled = !asset;
            selectedDeleteBtn.title = asset ? "Delete asset" : "Delete asset";
        }
    }

    function ensureDurationMetadata(asset) {
        if (!asset || hasDuration(asset) || (!isVideoAsset(asset) && !isAudioAsset(asset))) {
            return;
        }

        const element = document.createElement(isVideoAsset(asset) ? "video" : "audio");
        element.preload = "metadata";
        element.muted = true;
        element.playsInline = true;
        element.src = asset.url;

        const cleanup = () => {
            element.removeAttribute("src");
            element.load();
        };

        element.addEventListener(
            "loadedmetadata",
            () => {
                recordDuration(asset.id, element.duration);
                cleanup();
            },
            { once: true },
        );

        element.addEventListener("error", cleanup, { once: true });
    }

    function applyTransformFromInputs() {
        const asset = getSelectedAsset();
        if (!asset) return;
        const locked = isAspectLocked(asset.id);
        const ratio = getAssetAspectRatio(asset);
        let nextWidth = parseFloat(widthInput?.value) || asset.width;
        let nextHeight = parseFloat(heightInput?.value) || asset.height;

        if (locked && ratio) {
            if (lastSizeInputChanged === "height") {
                nextWidth = nextHeight * ratio;
                if (widthInput) widthInput.value = Math.round(nextWidth);
            } else {
                nextHeight = nextWidth / ratio;
                if (heightInput) heightInput.value = Math.round(nextHeight);
            }
        }

        asset.width = Math.max(10, nextWidth);
        asset.height = Math.max(10, nextHeight);
        updateRenderState(asset);
        persistTransform(asset);
        drawAndList();
    }

    function updatePlaybackFromInputs() {
        const asset = getSelectedAsset();
        if (!asset || !isVideoAsset(asset)) return;
        const percent = Math.max(0, Math.min(1000, parseFloat(speedInput?.value) || 100));
        setSpeedLabel(percent);
        asset.speed = percent / 100;
        updateRenderState(asset);
        schedulePersistTransform(asset);
        const media = mediaCache.get(asset.id);
        if (media) {
            applyMediaSettings(media, asset);
        }
        drawAndList();
    }

    function updateVolumeFromInput() {
        const asset = getSelectedAsset();
        if (!asset || !(isVideoAsset(asset) || isAudioAsset(asset))) return;
        const sliderValue = Math.max(0, Math.min(VOLUME_SLIDER_MAX, parseFloat(volumeInput?.value) || 100));
        const volumeValue = sliderToVolume(sliderValue);
        setVolumeLabel(sliderValue);
        asset.audioVolume = volumeValue;
        const media = mediaCache.get(asset.id);
        if (media) {
            applyMediaSettings(media, asset);
        }
        if (isAudioAsset(asset)) {
            const controller = ensureAudioController(asset);
            applyAudioSettings(controller, asset);
        }
        schedulePersistTransform(asset);
        drawAndList();
    }

    function updateAudioSettingsFromInputs() {
        const asset = getSelectedAsset();
        if (!asset || !isAudioAsset(asset)) return;
        asset.audioLoop = !!audioLoopInput?.checked;
        const delayMs = clamp(Math.max(0, Number.parseInt(audioDelayInput?.value || "0", 10)), 0, 30000);
        asset.audioDelayMillis = delayMs;
        setAudioDelayLabel(delayMs);
        if (audioDelayInput) audioDelayInput.value = delayMs;
        const nextAudioSpeedPercent = clamp(
            Math.max(25, Number.parseInt(audioSpeedInput?.value || "100", 10)),
            25,
            400,
        );
        setAudioSpeedLabel(nextAudioSpeedPercent);
        if (audioSpeedInput) audioSpeedInput.value = nextAudioSpeedPercent;
        asset.audioSpeed = Math.max(0.25, nextAudioSpeedPercent / 100);
        const nextAudioPitchPercent = clamp(
            Math.max(50, Number.parseInt(audioPitchInput?.value || "100", 10)),
            50,
            200,
        );
        setAudioPitchLabel(nextAudioPitchPercent);
        if (audioPitchInput) audioPitchInput.value = nextAudioPitchPercent;
        asset.audioPitch = Math.max(0.5, nextAudioPitchPercent / 100);
        const controller = ensureAudioController(asset);
        applyAudioSettings(controller, asset);
        schedulePersistTransform(asset);
        drawAndList();
    }

    function nudgeRotation(delta) {
        const asset = getSelectedAsset();
        if (!asset) return;
        const next = (asset.rotation || 0) + delta;
        asset.rotation = next;
        updateRenderState(asset);
        persistTransform(asset);
        drawAndList();
    }

    function recenterSelectedAsset() {
        const asset = getSelectedAsset();
        if (!asset) return;
        const centerX = (canvas.width - asset.width) / 2;
        const centerY = (canvas.height - asset.height) / 2;
        asset.x = centerX;
        asset.y = centerY;
        updateRenderState(asset);
        persistTransform(asset);
        drawAndList();
    }

    function getLayeredAssets(asset) {
        if (!asset) {
            return [];
        }
        return isCodeAsset(asset) ? getScriptAssetsByLayer() : getAssetsByLayer();
    }

    function applyOrderForAsset(asset, ordered) {
        if (!asset) return;
        if (isCodeAsset(asset)) {
            applyScriptLayerOrder(ordered);
        } else {
            applyLayerOrder(ordered);
        }
    }

    function moveLayerItem(asset, direction) {
        if (!asset) return;
        const ordered = getLayeredAssets(asset);
        const index = ordered.findIndex((item) => item.id === asset.id);
        if (index === -1) return;
        const nextIndex = direction === "up" ? index - 1 : index + 1;
        if (nextIndex < 0 || nextIndex >= ordered.length) {
            return;
        }
        [ordered[index], ordered[nextIndex]] = [ordered[nextIndex], ordered[index]];
        applyOrderForAsset(asset, ordered);
    }

    function bringForward() {
        const asset = getSelectedAsset();
        if (!asset || isAudioAsset(asset)) return;
        moveLayerItem(asset, "up");
    }

    function bringBackward() {
        const asset = getSelectedAsset();
        if (!asset || isAudioAsset(asset)) return;
        moveLayerItem(asset, "down");
    }

    function bringToFront() {
        const asset = getSelectedAsset();
        if (!asset || isAudioAsset(asset)) return;
        const ordered = getLayeredAssets(asset).filter((item) => item.id !== asset.id);
        ordered.unshift(asset);
        applyOrderForAsset(asset, ordered);
    }

    function sendToBack() {
        const asset = getSelectedAsset();
        if (!asset || isAudioAsset(asset)) return;
        const ordered = getLayeredAssets(asset).filter((item) => item.id !== asset.id);
        ordered.push(asset);
        applyOrderForAsset(asset, ordered);
    }

    globalThis.handleFileSelection = handleFileSelection;
    globalThis.nudgeRotation = nudgeRotation;
    globalThis.recenterSelectedAsset = recenterSelectedAsset;
    globalThis.bringForward = bringForward;
    globalThis.bringBackward = bringBackward;
    globalThis.bringToFront = bringToFront;
    globalThis.sendToBack = sendToBack;

    function applyLayerOrder(ordered) {
        const newOrder = ordered.map((item) => item.id).filter((id) => assets.has(id));
        layerOrder = newOrder;
        const changed = ordered.map((item) => assets.get(item.id)).filter(Boolean);
        changed.forEach((item) => updateRenderState(item));
        changed.forEach((item) => schedulePersistTransform(item, true));
        drawAndList();
    }

    function applyScriptLayerOrder(ordered) {
        const newOrder = ordered.map((item) => item.id).filter((id) => assets.has(id));
        scriptLayerOrder = newOrder;
        const changed = ordered.map((item) => assets.get(item.id)).filter(Boolean);
        changed.forEach((item) => schedulePersistTransform(item, true));
        drawAndList();
    }

    function getAssetAspectRatio(asset) {
        const media = ensureMedia(asset);
        if (isVideoElement(media) && media?.videoWidth && media?.videoHeight) {
            return media.videoWidth / media.videoHeight;
        }
        if (!isVideoElement(media) && media?.naturalWidth && media?.naturalHeight) {
            return media.naturalWidth / media.naturalHeight;
        }
        if (asset.width && asset.height) {
            return asset.width / asset.height;
        }
        return null;
    }

    function formatAspectRatioLabel(asset) {
        if (isAudioAsset(asset)) {
            return "";
        }
        const ratio = getAssetAspectRatio(asset);
        if (!ratio) {
            return "";
        }
        const normalized = ratio >= 1 ? `${ratio.toFixed(2)}:1` : `1:${(1 / ratio).toFixed(2)}`;
        return `AR ${normalized}`;
    }

    function setAspectLock(assetId, locked) {
        aspectLockState.set(assetId, locked);
    }

    function isAspectLocked(assetId) {
        return aspectLockState.has(assetId) ? aspectLockState.get(assetId) : true;
    }

    function handleSizeInputChange(type) {
        lastSizeInputChanged = type;
        const asset = getSelectedAsset();
        if (!asset) {
            return;
        }
        if (!isAspectLocked(asset.id)) {
            commitSizeChange();
            return;
        }
        const ratio = getAssetAspectRatio(asset);
        if (!ratio) {
            return;
        }
        if (type === "width" && widthInput && heightInput) {
            const width = parseFloat(widthInput.value);
            if (width > 0) {
                heightInput.value = Math.round(width / ratio);
            }
        } else if (type === "height" && widthInput && heightInput) {
            const height = parseFloat(heightInput.value);
            if (height > 0) {
                widthInput.value = Math.round(height * ratio);
            }
        }
        commitSizeChange();
    }

    function updateVisibility(asset, hidden) {
        fetch(`/api/channels/${broadcaster}/assets/${asset.id}/visibility`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ hidden }),
        })
            .then((r) => {
                if (!r.ok) {
                    throw new Error("Failed to update visibility");
                }
                return r.json();
            })
            .then((updated) => {
                storeAsset(updated);
                let visibilityMessage = null;
                if (updated.hidden) {
                    loopPlaybackState.set(updated.id, false);
                    stopAudio(updated.id);
                    showToast("Asset hidden from broadcast.", "info");
                } else if (isAudioAsset(updated)) {
                    playAudioFromCanvas(updated, true);
                    visibilityMessage = "Asset is now visible and active.";
                } else {
                    visibilityMessage = "Asset is now visible.";
                }
                if (visibilityMessage) {
                    showToast(visibilityMessage, "success");
                }
                updateRenderState(updated);
                drawAndList();
            })
            .catch(() => showToast("Unable to change visibility right now.", "error"));
    }

    function triggerAudioPlayback(asset, shouldPlay = true) {
        if (!asset) return Promise.resolve();
        return fetch(`/api/channels/${broadcaster}/assets/${asset.id}/play`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ play: shouldPlay }),
        })
            .then((r) => r.json())
            .then((updated) => {
                storeAsset(updated);
                updateRenderState(updated);
                return updated;
            });
    }

    function deleteAsset(asset) {
        fetch(`/api/channels/${broadcaster}/assets/${asset.id}`, { method: "DELETE" })
            .then((response) => {
                if (!response.ok) {
                    throw new Error("Failed to delete asset");
                }
                clearMedia(asset.id);
                assets.delete(asset.id);
                renderStates.delete(asset.id);
                layerOrder = layerOrder.filter((id) => id !== asset.id);
                scriptLayerOrder = scriptLayerOrder.filter((id) => id !== asset.id);
                cancelPendingTransform(asset.id);
                if (selectedAssetId === asset.id) {
                    selectedAssetId = null;
                }
                drawAndList();
                showToast("Asset deleted.", "info");
            })
            .catch(() => showToast("Unable to delete asset. Please try again.", "error"));
    }

    function uploadAsset(file = null) {
        const fileInput = document.getElementById("asset-file");
        const selectedFile = file || (fileInput?.files && fileInput.files.length ? fileInput.files[0] : null);
        if (!selectedFile) {
            showToast("Choose an image, GIF, video, audio, or JavaScript file to upload.", "info");
            return;
        }
        if (selectedFile.size > UPLOAD_LIMIT_BYTES) {
            showToast(`File is too large. Maximum upload size is ${UPLOAD_LIMIT_BYTES / 1024 / 1024} MB.`, "error");
            return;
        }

        if (isJavaScriptFile(selectedFile)) {
            selectedFile
                .text()
                .then((source) => {
                    if (typeof getUserJavaScriptSourceError === "function") {
                        const error = getUserJavaScriptSourceError(source);
                        if (error) {
                            showToast(`JavaScript error: ${error.title}`, "error");
                            if (fileNameLabel) {
                                fileNameLabel.textContent = "Upload failed";
                            }
                            return;
                        }
                    }
                    beginAssetUpload(selectedFile);
                })
                .catch(() => {
                    showToast("Unable to read the JavaScript file. Please try again.", "error");
                });
            return;
        }

        beginAssetUpload(selectedFile);
    }

    function beginAssetUpload(selectedFile) {
        const pendingId = addPendingUpload(selectedFile.name);
        const data = new FormData();
        data.append("file", selectedFile);
        const fileInput = document.getElementById("asset-file");
        if (fileNameLabel) {
            fileNameLabel.textContent = "Uploading...";
        }
        fetch(`/api/channels/${broadcaster}/assets`, {
            method: "POST",
            body: data,
        })
            .then((response) => {
                if (!response.ok) {
                    throw new Error("Upload failed");
                }
                if (fileInput) {
                    fileInput.value = "";
                    handleFileSelection(fileInput);
                }
                showToast("Upload received. Processing asset...", "success");
                updatePendingUpload(pendingId, { status: "processing" });
            })
            .catch((e) => {
                if (fileNameLabel) {
                    fileNameLabel.textContent = "Upload failed";
                }
                console.error(e);
                removePendingUpload(pendingId);
                showToast("Upload failed. Please try again with a supported file.", "error");
            });
    }

    function getCanvasPoint(event) {
        const rect = canvas.getBoundingClientRect();
        const scaleX = canvas.width / rect.width;
        const scaleY = canvas.height / rect.height;
        return {
            x: (event.clientX - rect.left) * scaleX,
            y: (event.clientY - rect.top) * scaleY,
        };
    }

    function isPointOnAsset(asset, x, y) {
        ctx.save();
        const halfWidth = asset.width / 2;
        const halfHeight = asset.height / 2;
        ctx.translate(asset.x + halfWidth, asset.y + halfHeight);
        ctx.rotate((asset.rotation * Math.PI) / 180);
        const path = new Path2D();
        path.rect(-halfWidth, -halfHeight, asset.width, asset.height);
        const hit = ctx.isPointInPath(path, x, y);
        ctx.restore();
        return hit;
    }

    function findAssetAtPoint(x, y) {
        const ordered = getAssetsByLayer();
        return ordered.find((asset) => !isAudioAsset(asset) && isPointOnAsset(asset, x, y)) || null;
    }

    function persistTransform(asset, silent = false) {
        cancelPendingTransform(asset.id);
        const payload = {
            audioLoop: asset.audioLoop,
            audioDelayMillis: asset.audioDelayMillis,
            audioSpeed: asset.audioSpeed,
            audioPitch: asset.audioPitch,
            audioVolume: asset.audioVolume,
        };
        if (isCodeAsset(asset)) {
            const order = getScriptLayerValue(asset.id);
            payload.order = order;
        } else if (!isAudioAsset(asset)) {
            const order = getLayerValue(asset.id);
            payload.x = asset.x;
            payload.y = asset.y;
            payload.width = asset.width;
            payload.height = asset.height;
            payload.rotation = asset.rotation;
            payload.speed = asset.speed;
            payload.order = order;
            if (isVideoAsset(asset)) {
                payload.muted = asset.muted;
            }
        }
        fetch(`/api/channels/${broadcaster}/assets/${asset.id}/transform`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        })
            .then((r) => {
                if (!r.ok) {
                    throw new Error("Transform failed");
                }
                return r.json();
            })
            .then((updated) => {
                storeAsset(updated);
                updateRenderState(updated);
                if (!silent) {
                    drawAndList();
                }
            })
            .catch(() => {
                if (!silent) {
                    showToast("Unable to save changes. Please retry.", "error");
                }
            });
    }

    canvas.addEventListener("mousedown", (event) => {
        const point = getCanvasPoint(event);
        const current = getSelectedAsset();
        const handle = current ? hitHandle(current, point) : null;
        if (current && handle) {
            interactionState =
                handle === "rotate"
                    ? {
                        mode: "rotate",
                        assetId: current.id,
                        startAngle: angleFromCenter(current, point),
                        startRotation: current.rotation || 0,
                    }
                    : {
                        mode: "resize",
                        assetId: current.id,
                        handle,
                        startLocal: pointerToLocal(current, point),
                        original: { ...current },
                    };
            canvas.style.cursor = cursorForHandle(handle);
            drawAndList();
            return;
        }

        const hit = findAssetAtPoint(point.x, point.y);
        if (hit) {
            selectedAssetId = hit.id;
            updateRenderState(hit);
            interactionState = {
                mode: "move",
                assetId: hit.id,
                offsetX: point.x - hit.x,
                offsetY: point.y - hit.y,
            };
            canvas.style.cursor = "grabbing";
        } else {
            selectedAssetId = null;
            interactionState = null;
            canvas.style.cursor = "default";
        }
        drawAndList();
    });

    canvas.addEventListener("mousemove", (event) => {
        const point = getCanvasPoint(event);
        if (!interactionState) {
            updateHoverCursor(point);
            return;
        }
        const asset = assets.get(interactionState.assetId);
        if (!asset) {
            interactionState = null;
            updateHoverCursor(point);
            return;
        }

        if (interactionState.mode === "move") {
            asset.x = point.x - interactionState.offsetX;
            asset.y = point.y - interactionState.offsetY;
            updateRenderState(asset);
            canvas.style.cursor = "grabbing";
            requestDraw();
        } else if (interactionState.mode === "resize") {
            resizeFromHandle(interactionState, point);
            canvas.style.cursor = cursorForHandle(interactionState.handle);
        } else if (interactionState.mode === "rotate") {
            const angle = angleFromCenter(asset, point);
            asset.rotation = (interactionState.startRotation || 0) + (angle - interactionState.startAngle);
            updateRenderState(asset);
            canvas.style.cursor = "grabbing";
            requestDraw();
        }
    });

    function endInteraction() {
        if (!interactionState) {
            return;
        }
        const asset = assets.get(interactionState.assetId);
        interactionState = null;
        canvas.style.cursor = "default";
        drawAndList();
        if (asset) {
            persistTransform(asset);
        }
    }

    canvas.addEventListener("mouseup", endInteraction);
    canvas.addEventListener("mouseleave", endInteraction);

    return {
        start,
        drawAndList,
        storeAsset,
        updateRenderState,
        updateSelectedAssetControls,
        handleCustomAssetSaved(asset) {
            if (!asset) {
                return;
            }
            storeAsset(asset);
            updateRenderState(asset);
            selectedAssetId = asset.id;
            updateSelectedAssetControls(asset);
            drawAndList();
        },
    };
}
