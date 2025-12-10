const canvas = document.getElementById('broadcast-canvas');
const ctx = canvas.getContext('2d');
let canvasSettings = { width: 1920, height: 1080 };
canvas.width = canvasSettings.width;
canvas.height = canvasSettings.height;
const assets = new Map();
const mediaCache = new Map();
const renderStates = new Map();
const animatedCache = new Map();
const audioControllers = new Map();
const pendingAudioUnlock = new Set();
const TARGET_FPS = 60;
const MIN_FRAME_TIME = 1000 / TARGET_FPS;
let lastRenderTime = 0;
let frameScheduled = false;
let pendingDraw = false;
let sortedAssetsCache = [];
let assetsDirty = true;
let renderIntervalId = null;
const audioPlaceholder = 'data:image/svg+xml;utf8,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="320" height="80"><rect width="100%" height="100%" fill="#0f172a" rx="8"/><g fill="#22d3ee" transform="translate(20 20)"><circle cx="15" cy="20" r="6"/><rect x="28" y="5" width="12" height="30" rx="2"/><rect x="45" y="10" width="140" height="5" fill="#a5f3fc"/><rect x="45" y="23" width="110" height="5" fill="#a5f3fc"/></g><text x="20" y="70" fill="#e5e7eb" font-family="sans-serif" font-size="14">Audio</text></svg>');
const audioUnlockEvents = ['pointerdown', 'keydown', 'touchstart'];

audioUnlockEvents.forEach((eventName) => {
    window.addEventListener(eventName, () => {
        if (!pendingAudioUnlock.size) return;
        pendingAudioUnlock.forEach((controller) => safePlay(controller));
        pendingAudioUnlock.clear();
    });
});

function connect() {
    const socket = new SockJS('/ws');
    const stompClient = Stomp.over(socket);
    stompClient.connect({}, () => {
        stompClient.subscribe(`/topic/channel/${broadcaster}`, (payload) => {
            const body = JSON.parse(payload.body);
            handleEvent(body);
        });
        fetch(`/api/channels/${broadcaster}/assets/visible`)
            .then((r) => {
                if (!r.ok) {
                    throw new Error('Failed to load assets');
                }
                return r.json();
            })
            .then(renderAssets)
            .catch(() => {
                if (typeof showToast === 'function') {
                    showToast('Unable to load overlay assets. Retrying may help.', 'error');
                }
            });
    });
}

function renderAssets(list) {
    list.forEach(asset => {
        asset.zIndex = Math.max(1, asset.zIndex ?? 1);
        assets.set(asset.id, asset);
    });
    assetsDirty = true;
    draw();
}

function fetchCanvasSettings() {
    return fetch(`/api/channels/${broadcaster}/canvas`)
        .then((r) => {
            if (!r.ok) {
                throw new Error('Failed to load canvas');
            }
            return r.json();
        })
        .then((settings) => {
            canvasSettings = settings;
            resizeCanvas();
        })
        .catch(() => {
            resizeCanvas();
            if (typeof showToast === 'function') {
                showToast('Using default canvas size. Unable to load saved settings.', 'warning');
            }
        });
}

function resizeCanvas() {
    const scale = Math.min(window.innerWidth / canvasSettings.width, window.innerHeight / canvasSettings.height);
    const displayWidth = canvasSettings.width * scale;
    const displayHeight = canvasSettings.height * scale;
    canvas.width = canvasSettings.width;
    canvas.height = canvasSettings.height;
    canvas.style.width = `${displayWidth}px`;
    canvas.style.height = `${displayHeight}px`;
    canvas.style.left = `${(window.innerWidth - displayWidth) / 2}px`;
    canvas.style.top = `${(window.innerHeight - displayHeight) / 2}px`;
    draw();
}

function handleEvent(event) {
    if (event.type === 'DELETED') {
        assets.delete(event.assetId);
        clearMedia(event.assetId);
        renderStates.delete(event.assetId);
    } else if (event.payload && !event.payload.hidden) {
        const payload = { ...event.payload, zIndex: Math.max(1, event.payload.zIndex ?? 1) };
        assets.set(payload.id, payload);
        ensureMedia(payload);
        if (isAudioAsset(payload)) {
            playAudioImmediately(payload);
        }
    } else if (event.payload && event.payload.hidden) {
        assets.delete(event.payload.id);
        clearMedia(event.payload.id);
        renderStates.delete(event.payload.id);
    }
    assetsDirty = true;
    draw();
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
    getZOrderedAssets().forEach(drawAsset);
}

function getZOrderedAssets() {
    if (assetsDirty) {
        sortedAssetsCache = Array.from(assets.values()).sort(zComparator);
        assetsDirty = false;
    }
    return sortedAssetsCache;
}

function zComparator(a, b) {
    const aZ = a?.zIndex ?? 1;
    const bZ = b?.zIndex ?? 1;
    if (aZ !== bZ) {
        return aZ - bZ;
    }
    return new Date(a?.createdAt || 0) - new Date(b?.createdAt || 0);
}

function drawAsset(asset) {
    const renderState = smoothState(asset);
    const halfWidth = renderState.width / 2;
    const halfHeight = renderState.height / 2;
    ctx.save();
    ctx.translate(renderState.x + halfWidth, renderState.y + halfHeight);
    ctx.rotate(renderState.rotation * Math.PI / 180);

    const media = ensureMedia(asset);
    const drawSource = media?.isAnimated ? media.bitmap : media;
    const ready = isAudioAsset(asset) || isDrawable(media);
    if (isAudioAsset(asset)) {
        autoStartAudio(asset);
    }
    if (ready && drawSource) {
        ctx.drawImage(drawSource, -halfWidth, -halfHeight, renderState.width, renderState.height);
    }

    if (isAudioAsset(asset)) {
        drawAudioIndicators(asset, halfWidth, halfHeight);
    }

    ctx.restore();
}

function smoothState(asset) {
    const previous = renderStates.get(asset.id) || { ...asset };
    const factor = 0.15;
    const next = {
        x: lerp(previous.x, asset.x, factor),
        y: lerp(previous.y, asset.y, factor),
        width: lerp(previous.width, asset.width, factor),
        height: lerp(previous.height, asset.height, factor),
        rotation: smoothAngle(previous.rotation, asset.rotation, factor)
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
    return asset?.mediaType?.startsWith('video/');
}

function isAudioAsset(asset) {
    return asset?.mediaType?.startsWith('audio/');
}

function isVideoElement(element) {
    return element && element.tagName === 'VIDEO';
}

function isGifAsset(asset) {
    return asset?.mediaType?.toLowerCase() === 'image/gif';
}

function drawAudioIndicators(asset, halfWidth, halfHeight) {
    const controller = audioControllers.get(asset.id);
    const isPlaying = controller && !controller.element.paused && !controller.element.ended;
    const hasDelay = !!(controller && controller.delayTimeout);
    if (!isPlaying && !hasDelay) {
        return;
    }
    const indicatorSize = 18;
    const padding = 8;
    let x = -halfWidth + padding + indicatorSize / 2;
    const y = -halfHeight + padding + indicatorSize / 2;

    ctx.save();
    ctx.setLineDash([]);
    if (isPlaying) {
        ctx.fillStyle = 'rgba(34, 197, 94, 0.9)';
        ctx.strokeStyle = '#020617';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.arc(x, y, indicatorSize / 2, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
        ctx.fillStyle = '#020617';
        ctx.beginPath();
        const radius = indicatorSize * 0.22;
        ctx.moveTo(x - radius, y - radius * 1.1);
        ctx.lineTo(x + radius * 1.2, y);
        ctx.lineTo(x - radius, y + radius * 1.1);
        ctx.closePath();
        ctx.fill();
        x += indicatorSize + 4;
    }

    if (hasDelay) {
        ctx.fillStyle = 'rgba(234, 179, 8, 0.9)';
        ctx.strokeStyle = '#020617';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.arc(x, y, indicatorSize / 2, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();

        ctx.strokeStyle = '#020617';
        ctx.beginPath();
        ctx.moveTo(x, y);
        ctx.lineTo(x, y - indicatorSize * 0.22);
        ctx.moveTo(x, y);
        ctx.lineTo(x + indicatorSize * 0.22, y);
        ctx.stroke();
    }
    ctx.restore();
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
    if (typeof ImageBitmap !== 'undefined' && element instanceof ImageBitmap) {
        return true;
    }
    return !!element.complete;
}

function clearMedia(assetId) {
    mediaCache.delete(assetId);
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
    element.preload = 'auto';
    element.controls = false;
    element.addEventListener('loadedmetadata', () => recordDuration(asset.id, element.duration));
    const controller = {
        id: asset.id,
        src: asset.url,
        element,
        delayTimeout: null,
        loopEnabled: false,
        delayMs: 0,
        baseDelayMs: 0
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
    const volume = Math.max(0, Math.min(1, asset.audioVolume ?? 1));
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

function autoStartAudio(asset) {
    if (!isAudioAsset(asset) || asset.hidden) {
        return;
    }
    const controller = ensureAudioController(asset);
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
    if (cached && cached.src === asset.url) {
        applyMediaSettings(cached, asset);
        return cached;
    }

    if (isAudioAsset(asset)) {
        ensureAudioController(asset);
        const placeholder = new Image();
        placeholder.src = audioPlaceholder;
        mediaCache.set(asset.id, placeholder);
        return placeholder;
    }

    if (isGifAsset(asset) && 'ImageDecoder' in window) {
        const animated = ensureAnimatedImage(asset);
        if (animated) {
            mediaCache.set(asset.id, animated);
            return animated;
        }
    }

    const element = isVideoAsset(asset) ? document.createElement('video') : new Image();
    if (isVideoElement(element)) {
        element.loop = true;
        element.muted = asset.muted ?? true;
        element.playsInline = true;
        element.autoplay = true;
        element.onloadeddata = draw;
        element.onloadedmetadata = () => recordDuration(asset.id, element.duration);
        element.src = asset.url;
        const playback = asset.speed ?? 1;
        element.playbackRate = Math.max(playback, 0.01);
        if (playback === 0) {
            element.pause();
        } else {
            element.play().catch(() => {});
        }
    } else {
        element.onload = draw;
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
        isAnimated: true
    };

    fetch(asset.url)
        .then((r) => r.blob())
        .then((blob) => new ImageDecoder({ data: blob, type: blob.type || 'image/gif' }))
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
    controller.decoder.decode().then(({ image, complete }) => {
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
    }).catch(() => {
        // If decoding fails, clear animated cache so static fallback is used next render
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
    const shouldMute = asset.muted ?? true;
    if (element.muted !== shouldMute) {
        element.muted = shouldMute;
    }
    if (nextSpeed === 0) {
        element.pause();
    } else if (element.paused) {
        element.play().catch(() => {});
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

window.addEventListener('resize', () => {
    resizeCanvas();
});

fetchCanvasSettings().finally(() => {
    resizeCanvas();
    startRenderLoop();
    connect();
});
