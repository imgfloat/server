let stompClient;
const canvas = document.getElementById('admin-canvas');
const ctx = canvas.getContext('2d');
const overlay = document.getElementById('admin-overlay');
const overlayFrame = overlay?.querySelector('iframe');
let canvasSettings = { width: 1920, height: 1080 };
canvas.width = canvasSettings.width;
canvas.height = canvasSettings.height;
const assets = new Map();
const mediaCache = new Map();
const renderStates = new Map();
const animatedCache = new Map();
let selectedAssetId = null;
let interactionState = null;
let animationFrameId = null;
let lastSizeInputChanged = null;
const HANDLE_SIZE = 10;
const ROTATE_HANDLE_OFFSET = 32;

const controlsPanel = document.getElementById('asset-controls');
const widthInput = document.getElementById('asset-width');
const heightInput = document.getElementById('asset-height');
const aspectLockInput = document.getElementById('maintain-aspect');
const speedInput = document.getElementById('asset-speed');
const muteInput = document.getElementById('asset-muted');
const selectedAssetName = document.getElementById('selected-asset-name');
const selectedAssetMeta = document.getElementById('selected-asset-meta');
const selectedZLabel = document.getElementById('asset-z-level');
const selectedTypeLabel = document.getElementById('asset-type-label');
const selectedVisibilityBadge = document.getElementById('selected-asset-visibility');
const selectedToggleBtn = document.getElementById('selected-asset-toggle');
const selectedDeleteBtn = document.getElementById('selected-asset-delete');
const aspectLockState = new Map();

if (widthInput) widthInput.addEventListener('input', () => handleSizeInputChange('width'));
if (heightInput) heightInput.addEventListener('input', () => handleSizeInputChange('height'));
if (speedInput) speedInput.addEventListener('change', updatePlaybackFromInputs);
if (muteInput) muteInput.addEventListener('change', updateMuteFromInput);
if (selectedToggleBtn) selectedToggleBtn.addEventListener('click', (event) => {
    event.stopPropagation();
    const asset = getSelectedAsset();
    if (asset) {
        updateVisibility(asset, !asset.hidden);
    }
});
if (selectedDeleteBtn) selectedDeleteBtn.addEventListener('click', (event) => {
    event.stopPropagation();
    const asset = getSelectedAsset();
    if (asset) {
        deleteAsset(asset);
    }
});

function connect() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, () => {
        stompClient.subscribe(`/topic/channel/${broadcaster}`, (payload) => {
            const body = JSON.parse(payload.body);
            handleEvent(body);
        });
        fetchAssets();
    });
}

function fetchAssets() {
    fetch(`/api/channels/${broadcaster}/assets`).then((r) => r.json()).then(renderAssets);
}

function fetchCanvasSettings() {
    return fetch(`/api/channels/${broadcaster}/canvas`)
        .then((r) => r.json())
        .then((settings) => {
            canvasSettings = settings;
            resizeCanvas();
        })
        .catch(() => resizeCanvas());
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
    if (overlayFrame) {
        overlayFrame.style.width = `${displayWidth}px`;
        overlayFrame.style.height = `${displayHeight}px`;
        overlayFrame.style.left = `${(bounds.width - displayWidth) / 2}px`;
        overlayFrame.style.top = `${(bounds.height - displayHeight) / 2}px`;
    }
    draw();
}

function renderAssets(list) {
    list.forEach((asset) => assets.set(asset.id, asset));
    drawAndList();
}

function handleEvent(event) {
    if (event.type === 'DELETED') {
        assets.delete(event.assetId);
        clearMedia(event.assetId);
        renderStates.delete(event.assetId);
        if (selectedAssetId === event.assetId) {
            selectedAssetId = null;
        }
    } else if (event.payload) {
        assets.set(event.payload.id, event.payload);
        ensureMedia(event.payload);
    }
    drawAndList();
}

function drawAndList() {
    draw();
    renderAssetList();
}

function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    getZOrderedAssets().forEach((asset) => drawAsset(asset));
}

function getZOrderedAssets() {
    return Array.from(assets.values()).sort(zComparator);
}

function zComparator(a, b) {
    const aZ = a?.zIndex ?? 0;
    const bZ = b?.zIndex ?? 0;
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
    const ready = isDrawable(media);
    if (ready) {
        ctx.globalAlpha = asset.hidden ? 0.35 : 0.9;
        ctx.drawImage(drawSource, -halfWidth, -halfHeight, renderState.width, renderState.height);
    } else {
        ctx.globalAlpha = asset.hidden ? 0.2 : 0.4;
        ctx.fillStyle = 'rgba(124, 58, 237, 0.35)';
        ctx.fillRect(-halfWidth, -halfHeight, renderState.width, renderState.height);
    }

    if (asset.hidden) {
        ctx.fillStyle = 'rgba(15, 23, 42, 0.35)';
        ctx.fillRect(-halfWidth, -halfHeight, renderState.width, renderState.height);
    }

    ctx.globalAlpha = 1;
    ctx.strokeStyle = asset.id === selectedAssetId ? 'rgba(124, 58, 237, 0.9)' : 'rgba(255, 255, 255, 0.4)';
    ctx.lineWidth = asset.id === selectedAssetId ? 2 : 1;
    ctx.setLineDash(asset.id === selectedAssetId ? [6, 4] : []);
    ctx.strokeRect(-halfWidth, -halfHeight, renderState.width, renderState.height);
    if (asset.id === selectedAssetId) {
        drawSelectionOverlay(renderState);
    }
    ctx.restore();
}

function smoothState(asset) {
    const previous = renderStates.get(asset.id) || { ...asset };
    const factor = interactionState && interactionState.assetId === asset.id ? 0.5 : 0.18;
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
    let delta = ((target - current + 180) % 360) - 180;
    return current + delta * factor;
}

function lerp(a, b, t) {
    return a + (b - a) * t;
}

function drawSelectionOverlay(asset) {
    const halfWidth = asset.width / 2;
    const halfHeight = asset.height / 2;
    ctx.save();
    ctx.setLineDash([6, 4]);
    ctx.strokeStyle = 'rgba(124, 58, 237, 0.9)';
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
    ctx.fillStyle = isRotation ? 'rgba(96, 165, 250, 0.9)' : 'rgba(124, 58, 237, 0.9)';
    ctx.strokeStyle = '#0f172a';
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
        { x: 0, y: 0, type: 'nw' },
        { x: asset.width / 2, y: 0, type: 'n' },
        { x: asset.width, y: 0, type: 'ne' },
        { x: asset.width, y: asset.height / 2, type: 'e' },
        { x: asset.width, y: asset.height, type: 'se' },
        { x: asset.width / 2, y: asset.height, type: 's' },
        { x: 0, y: asset.height, type: 'sw' },
        { x: 0, y: asset.height / 2, type: 'w' }
    ];
}

function rotatePoint(x, y, degrees) {
    const radians = degrees * Math.PI / 180;
    const cos = Math.cos(radians);
    const sin = Math.sin(radians);
    return {
        x: x * cos - y * sin,
        y: x * sin + y * cos
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
        y: rotated.y + asset.height / 2
    };
}

function angleFromCenter(asset, point) {
    const centerX = asset.x + asset.width / 2;
    const centerY = asset.y + asset.height / 2;
    return Math.atan2(point.y - centerY, point.x - centerX) * 180 / Math.PI;
}

function hitHandle(asset, point) {
    const local = pointerToLocal(asset, point);
    const tolerance = HANDLE_SIZE * 1.2;
    const rotationDistance = Math.hypot(local.x - asset.width / 2, local.y + ROTATE_HANDLE_OFFSET);
    if (Math.abs(local.y + ROTATE_HANDLE_OFFSET) <= tolerance && rotationDistance <= tolerance * 1.5) {
        return 'rotate';
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
        case 'nw':
        case 'se':
            return 'nwse-resize';
        case 'ne':
        case 'sw':
            return 'nesw-resize';
        case 'n':
        case 's':
            return 'ns-resize';
        case 'e':
        case 'w':
            return 'ew-resize';
        case 'rotate':
            return 'grab';
        default:
            return 'default';
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

    if (handle.includes('e')) {
        nextWidth = basis.width + (local.x - state.startLocal.x);
    }
    if (handle.includes('s')) {
        nextHeight = basis.height + (local.y - state.startLocal.y);
    }
    if (handle.includes('w')) {
        nextWidth = basis.width - (local.x - state.startLocal.x);
    }
    if (handle.includes('n')) {
        nextHeight = basis.height - (local.y - state.startLocal.y);
    }

    const ratio = isAspectLocked(asset.id) ? (getAssetAspectRatio(asset) || basis.width / Math.max(basis.height, 1)) : null;
    if (ratio) {
        const widthChanged = handle.includes('e') || handle.includes('w');
        const heightChanged = handle.includes('n') || handle.includes('s');
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

    if (handle.includes('w')) {
        offsetX = basis.width - nextWidth;
    }
    if (handle.includes('n')) {
        offsetY = basis.height - nextHeight;
    }

    const shift = rotatePoint(offsetX, offsetY, basis.rotation);
    asset.x = basis.x + shift.x;
    asset.y = basis.y + shift.y;
    asset.width = nextWidth;
    asset.height = nextHeight;
    renderStates.set(asset.id, { ...asset });
    draw();
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
    canvas.style.cursor = hit ? 'move' : 'default';
}

function startRenderLoop() {
    if (animationFrameId) {
        return;
    }
    const tick = () => {
        draw();
        animationFrameId = requestAnimationFrame(tick);
    };
    animationFrameId = requestAnimationFrame(tick);
}

function isVideoAsset(asset) {
    return (asset.mediaType && asset.mediaType.startsWith('video/')) || asset.url?.startsWith('data:video/');
}

function isVideoElement(element) {
    return element && element.tagName === 'VIDEO';
}

function getDisplayMediaType(asset) {
    const raw = asset.originalMediaType || asset.mediaType || '';
    if (!raw) {
        return 'Unknown';
    }
    const parts = raw.split('/');
    return parts.length > 1 ? parts[1].toUpperCase() : raw.toUpperCase();
}

function isGifAsset(asset) {
    return (asset.mediaType && asset.mediaType.toLowerCase() === 'image/gif') || asset.url?.startsWith('data:image/gif');
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
}

function ensureMedia(asset) {
    const cached = mediaCache.get(asset.id);
    if (cached && cached.src === asset.url) {
        applyMediaSettings(cached, asset);
        return cached;
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
        element.src = asset.url;
        element.playbackRate = asset.speed && asset.speed > 0 ? asset.speed : 1;
        element.play().catch(() => {});
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
        animatedCache.delete(controller.id);
    });
}

function applyMediaSettings(element, asset) {
    if (!isVideoElement(element)) {
        return;
    }
    const nextSpeed = asset.speed && asset.speed > 0 ? asset.speed : 1;
    if (element.playbackRate !== nextSpeed) {
        element.playbackRate = nextSpeed;
    }
    const shouldMute = asset.muted ?? true;
    if (element.muted !== shouldMute) {
        element.muted = shouldMute;
    }
    if (element.paused) {
        element.play().catch(() => {});
    }
}

function renderAssetList() {
    const list = document.getElementById('asset-list');
    list.innerHTML = '';

    if (!assets.size) {
        const empty = document.createElement('li');
        empty.textContent = 'No assets yet. Upload to get started.';
        list.appendChild(empty);
        updateSelectedAssetControls();
        return;
    }

    const sortedAssets = getZOrderedAssets().reverse();
    sortedAssets.forEach((asset) => {
        const li = document.createElement('li');
        li.className = 'asset-item';
        if (asset.id === selectedAssetId) {
            li.classList.add('selected');
        }
        if (asset.hidden) {
            li.classList.add('hidden');
        }

        const preview = createPreviewElement(asset);

        const meta = document.createElement('div');
        meta.className = 'meta';
        const name = document.createElement('strong');
        name.textContent = asset.name || `Asset ${asset.id.slice(0, 6)}`;
        const details = document.createElement('small');
        details.textContent = `Z ${asset.zIndex ?? 0} · ${Math.round(asset.width)}x${Math.round(asset.height)} · ${getDisplayMediaType(asset)} · ${asset.hidden ? 'Hidden' : 'Visible'}`;
        meta.appendChild(name);
        meta.appendChild(details);

        const actions = document.createElement('div');
        actions.className = 'actions';

        const toggleBtn = document.createElement('button');
        toggleBtn.type = 'button';
        toggleBtn.className = 'ghost icon-button';
        toggleBtn.innerHTML = `<span class="icon" aria-hidden="true">${asset.hidden ? '👁️' : '🙈'}</span><span class="label">${asset.hidden ? 'Show' : 'Hide'}</span>`;
        toggleBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            selectedAssetId = asset.id;
            updateVisibility(asset, !asset.hidden);
        });

        const deleteBtn = document.createElement('button');
        deleteBtn.type = 'button';
        deleteBtn.className = 'ghost danger icon-button';
        deleteBtn.innerHTML = '<span class="icon" aria-hidden="true">🗑️</span><span class="label">Delete</span>';
        deleteBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            deleteAsset(asset);
        });

        actions.appendChild(toggleBtn);
        actions.appendChild(deleteBtn);

        li.addEventListener('click', () => {
            selectedAssetId = asset.id;
            renderStates.set(asset.id, { ...asset });
            drawAndList();
        });

        li.appendChild(preview);
        li.appendChild(meta);
        li.appendChild(actions);
        list.appendChild(li);
    });

    updateSelectedAssetControls();
}

function createPreviewElement(asset) {
    if (isVideoAsset(asset)) {
        const video = document.createElement('video');
        video.className = 'asset-preview';
        video.src = asset.url;
        video.loop = true;
        video.muted = true;
        video.playsInline = true;
        video.autoplay = true;
        video.play().catch(() => {});
        return video;
    }

    const img = document.createElement('img');
    img.className = 'asset-preview';
    img.src = asset.url;
    img.alt = asset.name || 'Asset preview';
    return img;
}

function getSelectedAsset() {
    return selectedAssetId ? assets.get(selectedAssetId) : null;
}

function updateSelectedAssetControls() {
    if (!controlsPanel) {
        return;
    }
    const asset = getSelectedAsset();
    if (!asset) {
        controlsPanel.classList.add('hidden');
        return;
    }

    controlsPanel.classList.remove('hidden');
    lastSizeInputChanged = null;
    selectedAssetName.textContent = asset.name || `Asset ${asset.id.slice(0, 6)}`;
    selectedAssetMeta.textContent = `Z ${asset.zIndex ?? 0} · ${Math.round(asset.width)}x${Math.round(asset.height)} · ${getDisplayMediaType(asset)} · ${asset.hidden ? 'Hidden' : 'Visible'}`;
    if (selectedZLabel) {
        selectedZLabel.textContent = asset.zIndex ?? 0;
    }
    if (selectedTypeLabel) {
        selectedTypeLabel.textContent = getDisplayMediaType(asset);
    }
    if (selectedVisibilityBadge) {
        selectedVisibilityBadge.textContent = asset.hidden ? 'Hidden' : 'Visible';
        selectedVisibilityBadge.classList.toggle('danger', !!asset.hidden);
    }
    if (selectedToggleBtn) {
        selectedToggleBtn.querySelector('.label').textContent = asset.hidden ? 'Show' : 'Hide';
        selectedToggleBtn.querySelector('.icon').textContent = asset.hidden ? '👁️' : '🙈';
    }

    if (widthInput) widthInput.value = Math.round(asset.width);
    if (heightInput) heightInput.value = Math.round(asset.height);
    if (aspectLockInput) {
        aspectLockInput.checked = isAspectLocked(asset.id);
        aspectLockInput.onchange = () => setAspectLock(asset.id, aspectLockInput.checked);
    }
    if (speedInput) {
        speedInput.value = Math.round((asset.speed && asset.speed > 0 ? asset.speed : 1) * 100);
    }
    if (muteInput) {
        muteInput.checked = !!asset.muted;
        muteInput.disabled = !isVideoAsset(asset);
        muteInput.parentElement?.classList.toggle('disabled', !isVideoAsset(asset));
    }
}

function applyTransformFromInputs() {
    const asset = getSelectedAsset();
    if (!asset) return;
    const locked = isAspectLocked(asset.id);
    const ratio = getAssetAspectRatio(asset);
    let nextWidth = parseFloat(widthInput?.value) || asset.width;
    let nextHeight = parseFloat(heightInput?.value) || asset.height;

    if (locked && ratio) {
        if (lastSizeInputChanged === 'height') {
            nextWidth = nextHeight * ratio;
            if (widthInput) widthInput.value = Math.round(nextWidth);
        } else {
            nextHeight = nextWidth / ratio;
            if (heightInput) heightInput.value = Math.round(nextHeight);
        }
    }

    asset.width = Math.max(10, nextWidth);
    asset.height = Math.max(10, nextHeight);
    renderStates.set(asset.id, { ...asset });
    persistTransform(asset);
    drawAndList();
}

function updatePlaybackFromInputs() {
    const asset = getSelectedAsset();
    if (!asset) return;
    const percent = Math.max(10, Math.min(400, parseFloat(speedInput?.value) || 100));
    asset.speed = percent / 100;
    renderStates.set(asset.id, { ...asset });
    persistTransform(asset);
    drawAndList();
}

function updateMuteFromInput() {
    const asset = getSelectedAsset();
    if (!asset || !isVideoAsset(asset)) return;
    asset.muted = !!muteInput?.checked;
    renderStates.set(asset.id, { ...asset });
    persistTransform(asset);
    const media = mediaCache.get(asset.id);
    if (media) {
        applyMediaSettings(media, asset);
    }
    drawAndList();
}

function nudgeRotation(delta) {
    const asset = getSelectedAsset();
    if (!asset) return;
    const next = (asset.rotation || 0) + delta;
    asset.rotation = next;
    renderStates.set(asset.id, { ...asset });
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
    renderStates.set(asset.id, { ...asset });
    persistTransform(asset);
    drawAndList();
}

function bringForward() {
    const asset = getSelectedAsset();
    if (!asset) return;
    const ordered = getZOrderedAssets();
    const index = ordered.findIndex((item) => item.id === asset.id);
    if (index === -1 || index === ordered.length - 1) return;
    [ordered[index], ordered[index + 1]] = [ordered[index + 1], ordered[index]];
    applyZOrder(ordered);
}

function bringBackward() {
    const asset = getSelectedAsset();
    if (!asset) return;
    const ordered = getZOrderedAssets();
    const index = ordered.findIndex((item) => item.id === asset.id);
    if (index <= 0) return;
    [ordered[index], ordered[index - 1]] = [ordered[index - 1], ordered[index]];
    applyZOrder(ordered);
}

function bringToFront() {
    const asset = getSelectedAsset();
    if (!asset) return;
    const ordered = getZOrderedAssets().filter((item) => item.id !== asset.id);
    ordered.push(asset);
    applyZOrder(ordered);
}

function sendToBack() {
    const asset = getSelectedAsset();
    if (!asset) return;
    const ordered = getZOrderedAssets().filter((item) => item.id !== asset.id);
    ordered.unshift(asset);
    applyZOrder(ordered);
}

function applyZOrder(ordered) {
    const changed = [];
    ordered.forEach((item, index) => {
        if ((item.zIndex ?? 0) !== index) {
            item.zIndex = index;
            changed.push(item);
        }
        assets.set(item.id, item);
        renderStates.set(item.id, { ...item });
    });
    changed.forEach((item) => persistTransform(item, true));
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

function setAspectLock(assetId, locked) {
    aspectLockState.set(assetId, locked);
}

function isAspectLocked(assetId) {
    return aspectLockState.has(assetId) ? aspectLockState.get(assetId) : true;
}

function handleSizeInputChange(type) {
    lastSizeInputChanged = type;
    const asset = getSelectedAsset();
    if (!asset || !isAspectLocked(asset.id)) {
        return;
    }
    const ratio = getAssetAspectRatio(asset);
    if (!ratio) {
        return;
    }
    if (type === 'width' && widthInput && heightInput) {
        const width = parseFloat(widthInput.value);
        if (width > 0) {
            heightInput.value = Math.round(width / ratio);
        }
    } else if (type === 'height' && widthInput && heightInput) {
        const height = parseFloat(heightInput.value);
        if (height > 0) {
            widthInput.value = Math.round(height * ratio);
        }
    }
}

function updateVisibility(asset, hidden) {
    fetch(`/api/channels/${broadcaster}/assets/${asset.id}/visibility`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ hidden })
    }).then((r) => r.json()).then((updated) => {
        assets.set(updated.id, updated);
        drawAndList();
    });
}

function deleteAsset(asset) {
    fetch(`/api/channels/${broadcaster}/assets/${asset.id}`, { method: 'DELETE' }).then(() => {
        assets.delete(asset.id);
        mediaCache.delete(asset.id);
        renderStates.delete(asset.id);
        if (selectedAssetId === asset.id) {
            selectedAssetId = null;
        }
        drawAndList();
    });
}

function uploadAsset() {
    const fileInput = document.getElementById('asset-file');
    if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
        alert('Please choose an image, GIF, or video to upload.');
        return;
    }
    const data = new FormData();
    data.append('file', fileInput.files[0]);
    fetch(`/api/channels/${broadcaster}/assets`, {
        method: 'POST',
        body: data
    }).then(() => {
        fileInput.value = '';
    });
}

function getCanvasPoint(event) {
    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    return {
        x: (event.clientX - rect.left) * scaleX,
        y: (event.clientY - rect.top) * scaleY
    };
}

function isPointOnAsset(asset, x, y) {
    ctx.save();
    const halfWidth = asset.width / 2;
    const halfHeight = asset.height / 2;
    ctx.translate(asset.x + halfWidth, asset.y + halfHeight);
    ctx.rotate(asset.rotation * Math.PI / 180);
    const path = new Path2D();
    path.rect(-halfWidth, -halfHeight, asset.width, asset.height);
    const hit = ctx.isPointInPath(path, x, y);
    ctx.restore();
    return hit;
}

function findAssetAtPoint(x, y) {
    const ordered = getZOrderedAssets().reverse();
    return ordered.find((asset) => isPointOnAsset(asset, x, y)) || null;
}

function persistTransform(asset, silent = false) {
    fetch(`/api/channels/${broadcaster}/assets/${asset.id}/transform`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            x: asset.x,
            y: asset.y,
            width: asset.width,
            height: asset.height,
            rotation: asset.rotation,
            speed: asset.speed,
            muted: asset.muted,
            zIndex: asset.zIndex
        })
    }).then((r) => r.json()).then((updated) => {
        assets.set(updated.id, updated);
        if (!silent) {
            drawAndList();
        }
    });
}

canvas.addEventListener('mousedown', (event) => {
    const point = getCanvasPoint(event);
    const current = getSelectedAsset();
    const handle = current ? hitHandle(current, point) : null;
    if (current && handle) {
        interactionState = handle === 'rotate'
            ? {
                mode: 'rotate',
                assetId: current.id,
                startAngle: angleFromCenter(current, point),
                startRotation: current.rotation || 0
            }
            : {
                mode: 'resize',
                assetId: current.id,
                handle,
                startLocal: pointerToLocal(current, point),
                original: { ...current }
            };
        canvas.style.cursor = cursorForHandle(handle);
        drawAndList();
        return;
    }

    const hit = findAssetAtPoint(point.x, point.y);
    if (hit) {
        selectedAssetId = hit.id;
        renderStates.set(hit.id, { ...hit });
        interactionState = {
            mode: 'move',
            assetId: hit.id,
            offsetX: point.x - hit.x,
            offsetY: point.y - hit.y
        };
        canvas.style.cursor = 'grabbing';
    } else {
        selectedAssetId = null;
        interactionState = null;
        canvas.style.cursor = 'default';
    }
    drawAndList();
});

canvas.addEventListener('mousemove', (event) => {
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

    if (interactionState.mode === 'move') {
        asset.x = point.x - interactionState.offsetX;
        asset.y = point.y - interactionState.offsetY;
        renderStates.set(asset.id, { ...asset });
        canvas.style.cursor = 'grabbing';
        draw();
    } else if (interactionState.mode === 'resize') {
        resizeFromHandle(interactionState, point);
        canvas.style.cursor = cursorForHandle(interactionState.handle);
    } else if (interactionState.mode === 'rotate') {
        const angle = angleFromCenter(asset, point);
        asset.rotation = (interactionState.startRotation || 0) + (angle - interactionState.startAngle);
        renderStates.set(asset.id, { ...asset });
        canvas.style.cursor = 'grabbing';
        draw();
    }
});

function endInteraction() {
    if (!interactionState) {
        return;
    }
    const asset = assets.get(interactionState.assetId);
    interactionState = null;
    canvas.style.cursor = 'default';
    drawAndList();
    if (asset) {
        persistTransform(asset);
    }
}

canvas.addEventListener('mouseup', endInteraction);
canvas.addEventListener('mouseleave', endInteraction);

window.addEventListener('resize', () => {
    resizeCanvas();
});

fetchCanvasSettings().finally(() => {
    resizeCanvas();
    startRenderLoop();
    connect();
});
