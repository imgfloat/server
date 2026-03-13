const audioUnlockEvents = ["pointerdown", "keydown", "touchstart"];

export function createAudioManager({ assets, globalScope = globalThis, maxVolumeDb = 0 }) {
    const audioControllers = new Map();
    const pendingAudioUnlock = new Set();
    const limiter = createAudioLimiter({ globalScope, maxVolumeDb });

    audioUnlockEvents.forEach((eventName) => {
        globalScope.addEventListener(eventName, () => {
            limiter.resume();
            if (!pendingAudioUnlock.size) return;
            pendingAudioUnlock.forEach((controller) => safePlay(controller, pendingAudioUnlock, limiter.resume));
            pendingAudioUnlock.clear();
        });
    });

    function ensureAudioController(asset) {
        const cached = audioControllers.get(asset.id);
        if (cached && cached.src === asset.url) {
            applyAudioSettings(cached, asset);
            return cached;
        }

        if (cached) {
            clearAudio(asset.id);
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
        limiter.connectElement(element);
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
        limiter.connectElement(element);
        limiter.resume();
    }

    function getAssetVolume(asset) {
        return Math.max(0, Math.min(2, asset?.audioVolume ?? 1));
    }

    function applyMediaVolume(element, asset) {
        if (!element) return 1;
        const volume = getAssetVolume(asset);
        element.volume = Math.min(volume, 1);
        limiter.connectElement(element);
        limiter.resume();
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
                safePlay(controller, pendingAudioUnlock);
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
        safePlay(controller, pendingAudioUnlock, limiter.resume);
        controller.delayMs = controller.baseDelayMs ?? originalDelay ?? 0;
    }

    function playOverlappingAudio(asset) {
        const temp = new Audio(asset.url);
        temp.autoplay = true;
        temp.preload = "auto";
        temp.controls = false;
        applyAudioElementSettings(temp, asset);
        limiter.connectElement(temp);
        const controller = { element: temp };
        temp.onended = () => {
            limiter.disconnectElement(temp);
            temp.remove();
        };
        safePlay(controller, pendingAudioUnlock, limiter.resume);
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
            safePlay(controller, pendingAudioUnlock, limiter.resume);
        } else {
            playOverlappingAudio(asset);
        }
    }

    function autoStartAudio(asset) {
        if (asset.hidden) {
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
            safePlay(controller, pendingAudioUnlock, limiter.resume);
        }, controller.delayMs);
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

    function clearAudio(assetId) {
        const audio = audioControllers.get(assetId);
        if (!audio) {
            return;
        }
        if (audio.delayTimeout) {
            clearTimeout(audio.delayTimeout);
        }
        limiter.disconnectElement(audio.element);
        audio.element.pause();
        audio.element.currentTime = 0;
        audio.element.src = "";
        audio.element.remove();
        audioControllers.delete(assetId);
    }

    return {
        ensureAudioController,
        applyMediaVolume,
        handleAudioPlay,
        stopAudio,
        playAudioImmediately,
        autoStartAudio,
        clearAudio,
        releaseMediaElement: limiter.disconnectElement,
        setMaxVolumeDb: limiter.setMaxVolumeDb,
    };
}

function safePlay(controller, pendingUnlock, resumeAudio) {
    if (!controller?.element) return;
    if (resumeAudio) {
        resumeAudio();
    }
    const playPromise = controller.element.play();
    if (playPromise?.catch) {
        playPromise.catch(() => {
            pendingUnlock.add(controller);
        });
    }
}

function createAudioLimiter({ globalScope, maxVolumeDb }) {
    const AudioContextImpl = globalScope.AudioContext || globalScope.webkitAudioContext;
    if (!AudioContextImpl) {
        return {
            connectElement: () => {},
            disconnectElement: () => {},
            setMaxVolumeDb: () => {},
            resume: () => {},
        };
    }

    let context = null;
    let limiterNode = null;
    let pendingMaxVolumeDb = maxVolumeDb;

    const sourceNodes = new WeakMap();
    const pendingElements = new Set();

    function ensureContext() {
        if (context) {
            return context;
        }
        context = new AudioContextImpl();
        limiterNode = context.createDynamicsCompressor();
        limiterNode.knee.value = 0;
        limiterNode.ratio.value = 20;
        limiterNode.attack.value = 0.003;
        limiterNode.release.value = 0.25;
        limiterNode.connect(context.destination);
        applyMaxVolumeDb(pendingMaxVolumeDb);
        return context;
    }

    function applyMaxVolumeDb(value) {
        const next = Number.isFinite(value) ? value : 0;
        pendingMaxVolumeDb = next;
        if (limiterNode) {
            limiterNode.threshold.value = next;
        }
    }

    function connectElement(element) {
        if (!element) return;
        if (sourceNodes.has(element)) {
            return;
        }
        if (!context || context.state !== "running") {
            pendingElements.add(element);
            return;
        }
        try {
            const source = context.createMediaElementSource(element);
            source.connect(limiterNode);
            sourceNodes.set(element, source);
        } catch (error) {
            // Ignore elements that cannot be connected to the audio graph.
        }
    }

    function flushPending() {
        if (!pendingElements.size) {
            return;
        }
        const elements = Array.from(pendingElements);
        pendingElements.clear();
        elements.forEach(connectElement);
    }

    function disconnectElement(element) {
        pendingElements.delete(element);
        const source = sourceNodes.get(element);
        if (source) {
            source.disconnect();
            sourceNodes.delete(element);
        }
    }

    function setMaxVolumeDb(value) {
        applyMaxVolumeDb(value);
    }

    function resume() {
        const ctx = ensureContext();
        if (ctx.state === "running") {
            flushPending();
            return;
        }
        ctx.resume()
            .then(() => {
                flushPending();
            })
            .catch(() => {});
    }

    return {
        connectElement,
        disconnectElement,
        setMaxVolumeDb,
        resume,
    };
}
