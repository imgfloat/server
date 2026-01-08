import { isAudioAsset } from "../media/audio.js";
import { isGifAsset, isVideoAsset, isVideoElement } from "./assetKinds.js";

export function createMediaManager({ state, audioManager, draw, obsBrowser, supportsAnimatedDecode, canPlayProbe }) {
    const { mediaCache, animatedCache, blobCache, animationFailures, videoPlaybackStates } = state;

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
        audioManager.clearAudio(assetId);
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
            audioManager.ensureAudioController(asset);
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
            const playbackState = getVideoPlaybackState(element);
            element.addEventListener("playing", () => {
                playbackState.playRequested = false;
                if (playbackState.unmuteOnPlay) {
                    element.muted = false;
                    playbackState.unmuteOnPlay = false;
                }
            });
            element.addEventListener("pause", () => {
                playbackState.playRequested = false;
            });
            audioManager.applyMediaVolume(element, asset);
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
            .catch(() => {});
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
        const playbackState = getVideoPlaybackState(element);
        const nextSpeed = asset.speed ?? 1;
        const effectiveSpeed = Math.max(nextSpeed, 0.01);
        if (element.playbackRate !== effectiveSpeed) {
            element.playbackRate = effectiveSpeed;
        }
        const volume = audioManager.applyMediaVolume(element, asset);
        const shouldUnmute = volume > 0;
        element.muted = true;

        if (effectiveSpeed === 0) {
            element.pause();
            playbackState.playRequested = false;
            playbackState.unmuteOnPlay = false;
            return;
        }

        element.play();

        if (shouldUnmute) {
            if (!element.paused && element.readyState >= 2) {
                element.muted = false;
            } else {
                playbackState.unmuteOnPlay = true;
            }
        }

        if (element.paused || element.ended) {
            if (!playbackState.playRequested) {
                playbackState.playRequested = true;
                const playPromise = element.play();
                if (playPromise?.catch) {
                    playPromise.catch(() => {
                        playbackState.playRequested = false;
                    });
                }
            }
        }
    }

    function recordDuration(assetId, seconds) {
        if (!Number.isFinite(seconds) || seconds <= 0) {
            return;
        }
        const asset = state.assets.get(assetId);
        if (!asset) {
            return;
        }
        const nextMs = Math.round(seconds * 1000);
        if (asset.durationMs === nextMs) {
            return;
        }
        asset.durationMs = nextMs;
    }

    function getVideoPlaybackState(element) {
        if (!element) {
            return { playRequested: false, unmuteOnPlay: false };
        }
        let playbackState = videoPlaybackStates.get(element);
        if (!playbackState) {
            playbackState = { playRequested: false, unmuteOnPlay: false };
            videoPlaybackStates.set(element, playbackState);
        }
        return playbackState;
    }

    return {
        clearMedia,
        ensureMedia,
        applyMediaSettings,
        canPlayVideoType,
    };
}
