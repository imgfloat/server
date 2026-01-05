const canvas = document.getElementById("broadcast-canvas");
const obsBrowser = !!window.obsstudio;
const supportsAnimatedDecode =
    typeof ImageDecoder !== "undefined" && typeof createImageBitmap === "function" && !obsBrowser;
const canPlayProbe = document.createElement("video");
const ctx = canvas.getContext("2d");
let canvasSettings = { width: 1920, height: 1080 };
canvas.width = canvasSettings.width;
canvas.height = canvasSettings.height;
const assets = new Map();
const mediaCache = new Map();
const renderStates = new Map();
const visibilityStates = new Map();
const animatedCache = new Map();
const blobCache = new Map();
const animationFailures = new Map();
const audioControllers = new Map();
const pendingAudioUnlock = new Set();
const TARGET_FPS = 60;
const MIN_FRAME_TIME = 1000 / TARGET_FPS;
const VISIBILITY_THRESHOLD = 0.01;
let lastRenderTime = 0;
let frameScheduled = false;
let pendingDraw = false;
let renderIntervalId = null;
const pendingRemovals = new Set();
const audioUnlockEvents = ["pointerdown", "keydown", "touchstart"];
let layerOrder = [];

audioUnlockEvents.forEach((eventName) => {
    window.addEventListener(eventName, () => {
        if (!pendingAudioUnlock.size) return;
        pendingAudioUnlock.forEach((controller) => safePlay(controller));
        pendingAudioUnlock.clear();
    });
});

function ensureLayerPosition(assetId, placement = "keep") {
    const asset = assets.get(assetId);
    if (asset && isAudioAsset(asset)) {
        return;
    }
    const existingIndex = layerOrder.indexOf(assetId);
    if (existingIndex !== -1 && placement === "keep") {
        return;
    }
    if (existingIndex !== -1) {
        layerOrder.splice(existingIndex, 1);
    }
    if (placement === "append") {
        layerOrder.push(assetId);
    } else {
        layerOrder.unshift(assetId);
    }
    layerOrder = layerOrder.filter((id) => assets.has(id));
}

function getLayerOrder() {
    layerOrder = layerOrder.filter((id) => {
        const asset = assets.get(id);
        return asset && !isAudioAsset(asset);
    });
    assets.forEach((asset, id) => {
        if (isAudioAsset(asset)) {
            return;
        }
        if (!layerOrder.includes(id)) {
            layerOrder.unshift(id);
        }
    });
    return layerOrder;
}

function getRenderOrder() {
    return [...getLayerOrder()]
        .reverse()
        .map((id) => assets.get(id))
        .filter(Boolean);
}

function queueRemoval(assetId) {
    if (assetId) {
        pendingRemovals.add(assetId);
    }
}

function removeAsset(assetId) {
    assets.delete(assetId);
    layerOrder = layerOrder.filter((id) => id !== assetId);
    clearMedia(assetId);
    renderStates.delete(assetId);
    visibilityStates.delete(assetId);
}

function flushPendingRemovals() {
    if (!pendingRemovals.size) return;
    pendingRemovals.forEach((id) => removeAsset(id));
    pendingRemovals.clear();
}

function connect() {
    const socket = new SockJS("/ws");
    const stompClient = Stomp.over(socket);
    stompClient.connect({}, () => {
        stompClient.subscribe(`/topic/channel/${broadcaster}`, (payload) => {
            const body = JSON.parse(payload.body);
            handleEvent(body);
        });
        fetch(`/api/channels/${broadcaster}/assets/visible`)
            .then((r) => {
                if (!r.ok) {
                    throw new Error("Failed to load assets");
                }
                return r.json();
            })
            .then(renderAssets)
            .catch(() => showToast("Unable to load overlay assets. Retrying may help.", "error"));
    });
}

function renderAssets(list) {
    layerOrder = [];
    list.forEach((asset) => storeAsset(asset, "append"));
    draw();
}

function storeAsset(asset, placement = "keep") {
    if (!asset) return;
    const wasExisting = assets.has(asset.id);
    assets.set(asset.id, asset);
    ensureLayerPosition(asset.id, placement);
    if (!wasExisting && !visibilityStates.has(asset.id)) {
        const initialAlpha = 0; // Fade in newly discovered assets
        visibilityStates.set(asset.id, { alpha: initialAlpha, targetHidden: !!asset.hidden });
    }
}

function fetchCanvasSettings() {
    return fetch(`/api/channels/${encodeURIComponent(broadcaster)}/canvas`)
        .then((r) => {
            if (!r.ok) {
                throw new Error("Failed to load canvas");
            }
            return r.json();
        })
        .then((settings) => {
            canvasSettings = settings;
            resizeCanvas();
        })
        .catch(() => {
            resizeCanvas();
            showToast("Using default canvas size. Unable to load saved settings.", "warning");
        });
}

function resizeCanvas() {
    draw();
}

function handleEvent(event) {
    const assetId = event.assetId || event?.patch?.id || event?.payload?.id;
    if (event.type === "VISIBILITY") {
        handleVisibilityEvent(event);
        return;
    }
    if (event.type === "DELETED") {
        removeAsset(assetId);
    } else if (event.patch) {
        applyPatch(assetId, event.patch);
        if (event.payload) {
            const payload = normalizePayload(event.payload);
            if (payload.hidden) {
                hideAssetWithTransition(payload);
            } else if (!assets.has(payload.id)) {
                upsertVisibleAsset(payload, "append");
            }
        }
    } else if (event.type === "PLAY" && event.payload) {
        const payload = normalizePayload(event.payload);
        storeAsset(payload);
        if (isAudioAsset(payload)) {
            handleAudioPlay(payload, event.play !== false);
        }
    } else if (event.payload && !event.payload.hidden) {
        const payload = normalizePayload(event.payload);
        upsertVisibleAsset(payload);
    } else if (event.payload && event.payload.hidden) {
        hideAssetWithTransition(event.payload);
    }
    draw();
}

function normalizePayload(payload) {
    return { ...payload };
}

function hideAssetWithTransition(asset) {
    const payload = asset ? normalizePayload(asset) : null;
    if (!payload?.id) {
        return;
    }
    const existing = assets.get(payload.id);
    if (
        !existing &&
        (!Number.isFinite(payload.x) ||
            !Number.isFinite(payload.y) ||
            !Number.isFinite(payload.width) ||
            !Number.isFinite(payload.height))
    ) {
        return;
    }
    const merged = normalizePayload({ ...(existing || {}), ...payload, hidden: true });
    storeAsset(merged);
    stopAudio(payload.id);
}

function upsertVisibleAsset(asset, placement = "keep") {
    const payload = asset ? normalizePayload(asset) : null;
    if (!payload?.id) {
        return;
    }
    const placementMode = assets.has(payload.id) ? "keep" : placement;
    storeAsset(payload, placementMode);
    ensureMedia(payload);
    if (isAudioAsset(payload)) {
        playAudioImmediately(payload);
    }
}

function handleVisibilityEvent(event) {
    const payload = event.payload ? normalizePayload(event.payload) : null;
    const patch = event.patch;
    const id = payload?.id || patch?.id || event.assetId;

    if (payload?.hidden || patch?.hidden) {
        hideAssetWithTransition({ id, ...payload, ...patch });
        draw();
        return;
    }

    if (payload) {
        const placement = assets.has(payload.id) ? "keep" : "append";
        upsertVisibleAsset(payload, placement);
    }

    if (patch && id) {
        applyPatch(id, patch);
    }

    draw();
}

function applyPatch(assetId, patch) {
    if (!assetId || !patch) {
        return;
    }
    const sanitizedPatch = Object.fromEntries(
        Object.entries(patch).filter(([, value]) => value !== null && value !== undefined),
    );
    const existing = assets.get(assetId);
    if (!existing) {
        return;
    }
    const merged = normalizePayload({ ...existing, ...sanitizedPatch });
    const isAudio = isAudioAsset(merged);
    if (sanitizedPatch.hidden) {
        hideAssetWithTransition(merged);
        return;
    }
    const targetLayer = Number.isFinite(patch.layer)
        ? patch.layer
        : Number.isFinite(patch.zIndex)
            ? patch.zIndex
            : null;
    if (!isAudio && Number.isFinite(targetLayer)) {
        const currentOrder = getLayerOrder().filter((id) => id !== assetId);
        const insertIndex = Math.max(0, currentOrder.length - Math.round(targetLayer));
        currentOrder.splice(insertIndex, 0, assetId);
        layerOrder = currentOrder;
    }
    storeAsset(merged);
    ensureMedia(merged);
}

function draw() {
    if (frameScheduled) {
        pendingDraw = true;
        return;
    }
    frameScheduled = true;
    requestAnimationFrame((timestamp) => {
        const elapsed = timestamp - lastRenderTime;
        const delay = MIN_FRAME_TIME - elapsed;
        const shouldRender = elapsed >= MIN_FRAME_TIME;

        if (shouldRender) {
            lastRenderTime = timestamp;
            renderFrame();
        }

        frameScheduled = false;
        if (pendingDraw || !shouldRender) {
            pendingDraw = false;
            setTimeout(draw, Math.max(0, delay));
        }
    });
}

function renderFrame() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    getRenderOrder().forEach(drawAsset);
    flushPendingRemovals();
}

function drawAsset(asset) {
    const visibility = getVisibilityState(asset);
    if (visibility.alpha <= VISIBILITY_THRESHOLD && asset.hidden) {
        queueRemoval(asset.id);
        return;
    }
    const renderState = smoothState(asset);
    const halfWidth = renderState.width / 2;
    const halfHeight = renderState.height / 2;
    ctx.save();
    ctx.globalAlpha = Math.max(0, Math.min(1, visibility.alpha));
    ctx.translate(renderState.x + halfWidth, renderState.y + halfHeight);
    ctx.rotate((renderState.rotation * Math.PI) / 180);

    if (isAudioAsset(asset)) {
        if (!asset.hidden) {
            autoStartAudio(asset);
        }
        ctx.restore();
        return;
    }

    const media = ensureMedia(asset);
    const drawSource = media?.isAnimated ? media.bitmap : media;
    const ready = isDrawable(media);
    if (ready && drawSource) {
        ctx.drawImage(drawSource, -halfWidth, -halfHeight, renderState.width, renderState.height);
    }

    ctx.restore();
}

function getVisibilityState(asset) {
    const current = visibilityStates.get(asset.id) || {};
    const targetAlpha = asset.hidden ? 0 : 1;
    const startingAlpha = Number.isFinite(current.alpha) ? current.alpha : 0;
    const factor = asset.hidden ? 0.18 : 0.2;
    const nextAlpha = lerp(startingAlpha, targetAlpha, factor);
    const state = { alpha: nextAlpha, targetHidden: !!asset.hidden };
    visibilityStates.set(asset.id, state);
    return state;
}

function smoothState(asset) {
    const previous = renderStates.get(asset.id) || { ...asset };
    const factor = 0.15;
    const next = {
        x: lerp(previous.x, asset.x, factor),
        y: lerp(previous.y, asset.y, factor),
        width: lerp(previous.width, asset.width, factor),
        height: lerp(previous.height, asset.height, factor),
        rotation: smoothAngle(previous.rotation, asset.rotation, factor),
    };
    renderStates.set(asset.id, next);
    return next;
}

function smoothAngle(current, target, factor) {
    const delta = ((target - current + 180) % 360) - 180;
    return current + delta * factor;
}

function lerp(a, b, t) {
    return a + (b - a) * t;
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
}

function isVideoAsset(asset) {
    return asset?.mediaType?.startsWith("video/");
}

function isAudioAsset(asset) {
    return asset?.mediaType?.startsWith("audio/");
}

function isVideoElement(element) {
    return element && element.tagName === "VIDEO";
}

function isGifAsset(asset) {
    return asset?.mediaType?.toLowerCase() === "image/gif";
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
    const element = mediaCache.get(assetId);
    if (isVideoElement(element)) {
        element.src = "";
        element.remove();
    }
    mediaCache.delete(assetId);
    const animated = animatedCache.get(assetId);
    if (animated) {
        animated.cancelled = true;
        clearTimeout(animated.timeout);
        animated.bitmap?.close?.();
        animated.decoder?.close?.();
        animatedCache.delete(assetId);
    }
    animationFailures.delete(assetId);
    const cachedBlob = blobCache.get(assetId);
    if (cachedBlob?.objectUrl) {
        URL.revokeObjectURL(cachedBlob.objectUrl);
    }
    blobCache.delete(assetId);
    const audio = audioControllers.get(assetId);
    if (audio) {
        if (audio.delayTimeout) {
            clearTimeout(audio.delayTimeout);
        }
        audio.element.pause();
        audio.element.currentTime = 0;
        audio.element.src = "";
        audio.element.remove();
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
    element.preload = "auto";
    element.controls = false;
    element.addEventListener("loadedmetadata", () => recordDuration(asset.id, element.duration));
    const controller = {
        id: asset.id,
        src: asset.url,
        element,
        delayTimeout: null,
        loopEnabled: false,
        loopActive: true,
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
    controller.loopActive = controller.loopEnabled && controller.loopActive !== false;
    controller.baseDelayMs = Math.max(0, asset.audioDelayMillis || 0);
    controller.delayMs = controller.baseDelayMs;
    applyAudioElementSettings(controller.element, asset);
    if (resetPosition) {
        controller.element.currentTime = 0;
        controller.element.pause();
    }
}

function applyAudioElementSettings(element, asset) {
    const speed = Math.max(0.25, asset.audioSpeed || 1);
    const pitch = Math.max(0.5, asset.audioPitch || 1);
    element.playbackRate = speed * pitch;
    const volume = Math.max(0, Math.min(2, asset.audioVolume ?? 1));
    element.volume = Math.min(volume, 1);
}

function getAssetVolume(asset) {
    return Math.max(0, Math.min(2, asset?.audioVolume ?? 1));
}

function applyMediaVolume(element, asset) {
    if (!element) return 1;
    const volume = getAssetVolume(asset);
    element.volume = Math.min(volume, 1);
    return volume;
}

function handleAudioEnded(assetId) {
    const controller = audioControllers.get(assetId);
    if (!controller) return;
    controller.element.currentTime = 0;
    if (controller.delayTimeout) {
        clearTimeout(controller.delayTimeout);
    }
    if (controller.loopEnabled && controller.loopActive) {
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
    controller.loopActive = false;
}

function playAudioImmediately(asset) {
    const controller = ensureAudioController(asset);
    if (controller.delayTimeout) {
        clearTimeout(controller.delayTimeout);
        controller.delayTimeout = null;
    }
    controller.element.currentTime = 0;
    const originalDelay = controller.delayMs;
    controller.delayMs = 0;
    safePlay(controller);
    controller.delayMs = controller.baseDelayMs ?? originalDelay ?? 0;
}

function playOverlappingAudio(asset) {
    const temp = new Audio(asset.url);
    temp.autoplay = true;
    temp.preload = "auto";
    temp.controls = false;
    applyAudioElementSettings(temp, asset);
    const controller = { element: temp };
    temp.onended = () => {
        temp.remove();
    };
    safePlay(controller);
}

function handleAudioPlay(asset, shouldPlay) {
    const controller = ensureAudioController(asset);
    controller.loopActive = !!shouldPlay;
    if (!shouldPlay) {
        stopAudio(asset.id);
        return;
    }
    if (asset.audioLoop) {
        controller.delayMs = controller.baseDelayMs;
        safePlay(controller);
    } else {
        playOverlappingAudio(asset);
    }
}

function autoStartAudio(asset) {
    if (!isAudioAsset(asset) || asset.hidden) {
        return;
    }
    const controller = ensureAudioController(asset);
    if (!controller.loopEnabled || !controller.loopActive) {
        return;
    }
    if (!controller.element.paused && !controller.element.ended) {
        return;
    }
    if (controller.delayTimeout) {
        return;
    }
    controller.delayTimeout = setTimeout(() => {
        safePlay(controller);
    }, controller.delayMs);
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

    if (isGifAsset(asset) && supportsAnimatedDecode) {
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
        if (!canPlayVideoType(asset.mediaType)) {
            return null;
        }
        element.loop = true;
        element.playsInline = true;
        element.autoplay = true;
        element.controls = false;
        element.onloadeddata = draw;
        element.onloadedmetadata = () => recordDuration(asset.id, element.duration);
        element.preload = "auto";
        element.addEventListener("error", () => clearMedia(asset.id));
        applyMediaVolume(element, asset);
        element.muted = true;
        setVideoSource(element, asset);
    } else {
        element.onload = draw;
        element.src = asset.url;
    }
    mediaCache.set(asset.id, element);
    return element;
}

function ensureAnimatedImage(asset) {
    const failedAt = animationFailures.get(asset.id);
    if (failedAt && Date.now() - failedAt < 15000) {
        return null;
    }
    const cached = animatedCache.get(asset.id);
    if (cached && cached.url === asset.url) {
        return cached;
    }

    animationFailures.delete(asset.id);

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

    fetchAssetBlob(asset)
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
            animationFailures.set(asset.id, Date.now());
        });

    animatedCache.set(asset.id, controller);
    return controller;
}

function fetchAssetBlob(asset) {
    const cached = blobCache.get(asset.id);
    if (cached && cached.url === asset.url && cached.blob) {
        return Promise.resolve(cached.blob);
    }
    if (cached && cached.url === asset.url && cached.pending) {
        return cached.pending;
    }

    const pending = fetch(asset.url)
        .then((r) => r.blob())
        .then((blob) => {
            const previous = blobCache.get(asset.id);
            const existingUrl = previous?.url === asset.url ? previous.objectUrl : null;
            const objectUrl = existingUrl || URL.createObjectURL(blob);
            blobCache.set(asset.id, { url: asset.url, blob, objectUrl });
            return blob;
        });
    blobCache.set(asset.id, { url: asset.url, pending });
    return pending;
}

function setVideoSource(element, asset) {
    if (!shouldUseBlobUrl(asset)) {
        applyVideoSource(element, asset.url, asset);
        return;
    }

    const cached = blobCache.get(asset.id);
    if (cached?.url === asset.url && cached.objectUrl) {
        applyVideoSource(element, cached.objectUrl, asset);
        return;
    }

    fetchAssetBlob(asset)
        .then(() => {
            const next = blobCache.get(asset.id);
            if (next?.url !== asset.url || !next.objectUrl) {
                return;
            }
            applyVideoSource(element, next.objectUrl, asset);
        })
        .catch(() => { });
}

function applyVideoSource(element, objectUrl, asset) {
    element.src = objectUrl;
    startVideoPlayback(element, asset);
}

function shouldUseBlobUrl(asset) {
    return !obsBrowser && asset?.mediaType && canPlayVideoType(asset.mediaType);
}

function canPlayVideoType(mediaType) {
    if (!mediaType) {
        return true;
    }
    const support = canPlayProbe.canPlayType(mediaType);
    return support === "probably" || support === "maybe";
}

function getCachedSource(element) {
    return element?.dataset?.sourceUrl || element?.src;
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
                    draw();
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
            // If decoding fails, clear animated cache so static fallback is used next render
            animatedCache.delete(controller.id);
            animationFailures.set(controller.id, Date.now());
        });
}

function applyMediaSettings(element, asset) {
    if (!isVideoElement(element)) {
        return;
    }
    startVideoPlayback(element, asset);
}

function startVideoPlayback(element, asset) {
    const nextSpeed = asset.speed ?? 1;
    const effectiveSpeed = Math.max(nextSpeed, 0.01);
    if (element.playbackRate !== effectiveSpeed) {
        element.playbackRate = effectiveSpeed;
    }
    const volume = applyMediaVolume(element, asset);
    const shouldUnmute = volume > 0;
    element.muted = true;

    if (effectiveSpeed === 0) {
        element.pause();
        return;
    }

    element.play();

    if (shouldUnmute) {
        if (!element.paused && element.readyState >= 2) {
            element.muted = false;
        } else {
            element.addEventListener(
                "playing",
                () => {
                    element.muted = false;
                },
                { once: true },
            );
        }
    }
}

function startRenderLoop() {
    if (renderIntervalId) {
        return;
    }
    renderIntervalId = setInterval(() => {
        draw();
    }, MIN_FRAME_TIME);
}

window.addEventListener("resize", () => {
    resizeCanvas();
});

fetchCanvasSettings().finally(() => {
    resizeCanvas();
    startRenderLoop();
    connect();
});
