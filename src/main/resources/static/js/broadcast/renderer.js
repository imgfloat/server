import { AssetKind, MIN_FRAME_TIME, VISIBILITY_THRESHOLD } from "./constants.js";
import { createBroadcastState } from "./state.js";
import { getAssetKind, isCodeAsset, isModelAsset, isVisualAsset, isVideoElement } from "./assetKinds.js";
import { ensureLayerPosition, getLayerOrder, getRenderOrder, getScriptLayerOrder } from "./layers.js";
import { getVisibilityState, smoothState } from "./visibility.js";
import { createAudioManager } from "./audioManager.js";
import { createMediaManager } from "./mediaManager.js";
import { createModelManager } from "../media/modelManager.js";

export class BroadcastRenderer {
    constructor({ canvas, scriptLayer, broadcaster, showToast }) {
        this.canvas = canvas;
        this.ctx = canvas.getContext("2d");
        this.scriptLayer = scriptLayer;
        this.scriptCanvases = new Map();
        this.broadcaster = broadcaster;
        this.showToast = showToast;
        this.state = createBroadcastState();
        this.lastRenderTime = 0;
        this.frameScheduled = false;
        this.pendingDraw = false;
        this.renderIntervalId = null;
        this.scriptWorker = null;
        this.scriptWorkerReady = false;
        this.scriptErrorKeys = new Set();
        this.scriptAttachmentCache = new Map();
        this.scriptAttachmentsByAssetId = new Map();
        this.chatMessages = [];
        this.emoteCatalog = [];
        this.emoteCatalogById = new Map();
        this.globalEmotes = [];
        this.channelEmotes = [];
        this.sevenTvEmotes = [];
        this.sevenTvEmotesByName = new Map();
        this.lastChatPruneAt = 0;
        this.allowChannelEmotesForAssets = true;
        this.allowSevenTvEmotesForAssets = true;
        this.allowScriptChatAccess = true;

        this.obsBrowser = !!globalThis.obsstudio;
        this.supportsAnimatedDecode =
            typeof ImageDecoder !== "undefined" && typeof createImageBitmap === "function" && !this.obsBrowser;
        this.canPlayProbe = document.createElement("video");

        this.audioManager = createAudioManager({ assets: this.state.assets });
        this.mediaManager = createMediaManager({
            state: this.state,
            audioManager: this.audioManager,
            draw: () => this.draw(),
            obsBrowser: this.obsBrowser,
            supportsAnimatedDecode: this.supportsAnimatedDecode,
            canPlayProbe: this.canPlayProbe,
        });
        this.modelManager = createModelManager({ requestDraw: () => this.draw() });

        this.applyCanvasSettings(this.state.canvasSettings);
        globalThis.addEventListener("resize", () => {
            this.resizeCanvas();
        });
    }

    start() {
        this.fetchCanvasSettings().finally(() => {
            this.resizeCanvas();
            this.startRenderLoop();
            this.connect();
        });
    }

    connect() {
        const socket = new SockJS("/ws");
        const stompClient = Stomp.over(socket);
        stompClient.connect({}, () => {
            stompClient.subscribe(`/topic/channel/${this.broadcaster}`, (payload) => {
                const body = JSON.parse(payload.body);
                this.handleEvent(body);
            });
            fetch(`/api/channels/${this.broadcaster}/assets`)
                .then((r) => {
                    if (!r.ok) {
                        throw new Error("Failed to load assets");
                    }
                    return r.json();
                })
                .then((assets) => this.renderAssets(assets))
                .catch(() => this.showToast("Unable to load overlay assets. Retrying may help.", "error"));
        });
    }

    renderAssets(list) {
        this.state.layerOrder = [];
        this.state.scriptLayerOrder = [];
        list.forEach((asset) => {
            this.storeAsset(asset, "append");
            if (isCodeAsset(asset)) {
                this.spawnUserJavaScriptWorker(asset);
            }
        });
        this.draw();
    }

    storeAsset(asset, placement = "keep") {
        if (!asset) return;
        console.info(`Storing asset: ${asset.id}`);
        const wasExisting = this.state.assets.has(asset.id);
        this.state.assets.set(asset.id, asset);
        ensureLayerPosition(this.state, asset.id, placement);
        if (isCodeAsset(asset)) {
            this.updateScriptWorkerAttachments(asset);
        }
        if (!wasExisting && !this.state.visibilityStates.has(asset.id)) {
            const initialAlpha = 0; // Fade in newly discovered assets
            this.state.visibilityStates.set(asset.id, {
                alpha: initialAlpha,
                targetHidden: !!asset.hidden,
            });
        }
    }

    removeAsset(assetId) {
        this.state.assets.delete(assetId);
        this.state.layerOrder = this.state.layerOrder.filter((id) => id !== assetId);
        this.state.scriptLayerOrder = this.state.scriptLayerOrder.filter((id) => id !== assetId);
        this.mediaManager.clearMedia(assetId);
        this.modelManager.clearModel(assetId);
        this.stopUserJavaScriptWorker(assetId);
        this.state.renderStates.delete(assetId);
        this.state.visibilityStates.delete(assetId);
    }

    async fetchCanvasSettings() {
        return fetch(`/api/channels/${encodeURIComponent(this.broadcaster)}/canvas`)
            .then((r) => {
                if (!r.ok) {
                    throw new Error("Failed to load canvas");
                }
                return r.json();
            })
            .then((settings) => {
                this.applyCanvasSettings(settings);
            })
            .catch(() => {
                this.resizeCanvas();
                this.showToast("Using default canvas size. Unable to load saved settings.", "warning");
            });
    }

    applyCanvasSettings(settings) {
        if (!settings) {
            return;
        }
        const width = Number.isFinite(settings.width) ? settings.width : this.state.canvasSettings.width;
        const height = Number.isFinite(settings.height) ? settings.height : this.state.canvasSettings.height;
        this.state.canvasSettings = { width, height };
        this.resizeCanvas();
    }

    resizeCanvas() {
        if (Number.isFinite(this.state.canvasSettings.width) && Number.isFinite(this.state.canvasSettings.height)) {
            this.canvas.width = this.state.canvasSettings.width;
            this.canvas.height = this.state.canvasSettings.height;
            this.canvas.style.width = `${this.state.canvasSettings.width}px`;
            this.canvas.style.height = `${this.state.canvasSettings.height}px`;
            if (this.scriptLayer) {
                this.scriptLayer.style.width = `${this.state.canvasSettings.width}px`;
                this.scriptLayer.style.height = `${this.state.canvasSettings.height}px`;
            }
        }
        this.resizeScriptCanvases();
        this.updateScriptWorkerCanvas();
        this.draw();
    }

    handleEvent(event) {
        if (event.type === "CANVAS" && event.payload) {
            this.applyCanvasSettings(event.payload);
            return;
        }
        const assetId = event.assetId || event?.patch?.id || event?.payload?.id;
        if (event.type === "VISIBILITY") {
            this.handleVisibilityEvent(event);
            return;
        }
        if (event.type === "DELETED") {
            this.removeAsset(assetId);
        } else if (event.patch) {
            this.applyPatch(assetId, event.patch);
            if (event.payload) {
                const payload = this.normalizePayload(event.payload);
                if (payload.hidden) {
                    this.hideAssetWithTransition(payload);
                } else if (!this.state.assets.has(payload.id)) {
                    this.upsertVisibleAsset(payload, "append");
                }
            }
        } else if (event.type === "PLAY" && event.payload) {
            const payload = this.normalizePayload(event.payload);
            this.storeAsset(payload);
            if (getAssetKind(payload) === AssetKind.AUDIO) {
                this.audioManager.handleAudioPlay(payload, event.play !== false);
            }
        } else if (event.payload && !event.payload.hidden) {
            const payload = this.normalizePayload(event.payload);
            this.upsertVisibleAsset(payload);
        } else if (event.payload && event.payload.hidden) {
            this.hideAssetWithTransition(event.payload);
        }
        this.draw();
    }

    normalizePayload(payload) {
        return { ...payload };
    }

    hideAssetWithTransition(asset) {
        const payload = asset ? this.normalizePayload(asset) : null;
        if (!payload?.id) {
            return;
        }
        const existing = this.state.assets.get(payload.id);
        if (
            !existing &&
            (!Number.isFinite(payload.x) ||
                !Number.isFinite(payload.y) ||
                !Number.isFinite(payload.width) ||
                !Number.isFinite(payload.height))
        ) {
            return;
        }
        const merged = this.normalizePayload({ ...(existing || {}), ...payload, hidden: true });
        this.storeAsset(merged);
        this.stopUserJavaScriptWorker(merged.id);
        this.audioManager.stopAudio(payload.id);
    }

    upsertVisibleAsset(asset, placement = "keep") {
        const payload = asset ? this.normalizePayload(asset) : null;
        if (!payload?.id) {
            return;
        }
        const placementMode = this.state.assets.has(payload.id) ? "keep" : placement;
        this.storeAsset(payload, placementMode);
        this.mediaManager.ensureMedia(payload);
        const kind = getAssetKind(payload);
        if (kind === AssetKind.AUDIO) {
            this.audioManager.playAudioImmediately(payload);
        } else if (kind === AssetKind.CODE) {
            this.spawnUserJavaScriptWorker(payload);
        }
    }

    handleVisibilityEvent(event) {
        const payload = event.payload ? this.normalizePayload(event.payload) : null;
        const patch = event.patch;
        const id = payload?.id || patch?.id || event.assetId;

        if (payload?.hidden || patch?.hidden) {
            this.hideAssetWithTransition({ id, ...payload, ...patch });
            this.draw();
            return;
        }

        if (payload) {
            const placement = this.state.assets.has(payload.id) ? "keep" : "append";
            this.upsertVisibleAsset(payload, placement);
        }

        if (patch && id) {
            this.applyPatch(id, patch);
        }

        this.draw();
    }

    applyPatch(assetId, patch) {
        if (!assetId || !patch) {
            return;
        }
        const sanitizedPatch = Object.fromEntries(
            Object.entries(patch).filter(([, value]) => value !== null && value !== undefined),
        );
        const existing = this.state.assets.get(assetId);
        if (!existing) {
            return;
        }
        const merged = this.normalizePayload({ ...existing, ...sanitizedPatch });
        console.log(merged);
        const isVisual = isVisualAsset(merged);
        const isScript = isCodeAsset(merged);
        if (sanitizedPatch.hidden) {
            this.hideAssetWithTransition(merged);
            return;
        }
        const targetOrder = Number.isFinite(sanitizedPatch.order) ? sanitizedPatch.order : null;
        if (Number.isFinite(targetOrder)) {
            if (isScript) {
                const currentOrder = getScriptLayerOrder(this.state).filter((id) => id !== assetId);
                const totalCount = currentOrder.length + 1;
                const insertIndex = Math.max(0, Math.min(currentOrder.length, totalCount - Math.round(targetOrder)));
                currentOrder.splice(insertIndex, 0, assetId);
                this.state.scriptLayerOrder = currentOrder;
                this.applyScriptCanvasOrder();
            } else if (isVisual) {
                const currentOrder = getLayerOrder(this.state).filter((id) => id !== assetId);
                const totalCount = currentOrder.length + 1;
                const insertIndex = Math.max(0, Math.min(currentOrder.length, totalCount - Math.round(targetOrder)));
                currentOrder.splice(insertIndex, 0, assetId);
                this.state.layerOrder = currentOrder;
            }
        }
        this.storeAsset(merged);
        this.mediaManager.ensureMedia(merged);
        if (isCodeAsset(merged)) {
            console.info(`Spawning JS worker for patched asset: ${merged.id}`);
            this.spawnUserJavaScriptWorker(merged);
        }
    }

    draw() {
        if (this.frameScheduled) {
            this.pendingDraw = true;
            return;
        }
        this.frameScheduled = true;
        requestAnimationFrame((timestamp) => {
            const elapsed = timestamp - this.lastRenderTime;
            const delay = MIN_FRAME_TIME - elapsed;
            const shouldRender = elapsed >= MIN_FRAME_TIME;

            if (shouldRender) {
                this.lastRenderTime = timestamp;
                this.renderFrame();
            }

            this.frameScheduled = false;
            if (this.pendingDraw || !shouldRender) {
                this.pendingDraw = false;
                setTimeout(() => this.draw(), Math.max(0, delay));
            }
        });
    }

    renderFrame() {
        const now = Date.now();
        if (now - this.lastChatPruneAt > 1000) {
            this.lastChatPruneAt = now;
            this.pruneChatMessages(now);
        }
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        getRenderOrder(this.state).forEach((asset) => this.drawAsset(asset));
    }

    drawAsset(asset) {
        const visibility = getVisibilityState(this.state, asset);
        if (visibility.alpha <= VISIBILITY_THRESHOLD && asset.hidden) {
            return;
        }
        const renderState = smoothState(this.state, asset);
        const halfWidth = renderState.width / 2;
        const halfHeight = renderState.height / 2;
        this.ctx.save();
        this.ctx.globalAlpha = Math.max(0, Math.min(1, visibility.alpha));
        this.ctx.translate(renderState.x + halfWidth, renderState.y + halfHeight);
        this.ctx.rotate((renderState.rotation * Math.PI) / 180);

        const kind = getAssetKind(asset);
        if (kind === AssetKind.CODE) {
            this.ctx.restore();
            return;
        }

        if (kind === AssetKind.AUDIO) {
            if (!asset.hidden) {
                this.audioManager.autoStartAudio(asset);
            }
            this.ctx.restore();
            return;
        }

        let drawSource = null;
        let ready = false;
        if (isModelAsset(asset)) {
            const model = this.modelManager.ensureModel(asset);
            drawSource = model?.canvas || null;
            ready = !!model?.ready;
        } else {
            const media = this.mediaManager.ensureMedia(asset);
            drawSource = media?.isAnimated ? media.bitmap : media;
            ready = this.isDrawable(media);
        }
        if (ready && drawSource) {
            this.ctx.drawImage(drawSource, -halfWidth, -halfHeight, renderState.width, renderState.height);
        }

        this.ctx.restore();
    }

    isDrawable(element) {
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

    startRenderLoop() {
        if (this.renderIntervalId) {
            return;
        }
        this.renderIntervalId = setInterval(() => {
            this.draw();
        }, MIN_FRAME_TIME);
    }

    ensureScriptWorker() {
        if (this.scriptWorker) {
            return;
        }
        this.scriptWorker = new Worker("/js/broadcast/script-worker.js");
        this.scriptWorker.addEventListener("message", (event) => this.handleScriptWorkerMessage(event));
        this.scriptWorker.postMessage(
            {
                type: "init",
                payload: {
                    channelName: this.broadcaster,
                },
            },
        );
        this.scriptWorkerReady = true;
        this.updateScriptWorkerChatMessages();
        this.updateScriptWorkerEmoteCatalog();
    }

    setScriptSettings(settings) {
        this.allowChannelEmotesForAssets = settings?.allowChannelEmotesForAssets !== false;
        this.allowSevenTvEmotesForAssets = settings?.allowSevenTvEmotesForAssets !== false;
        this.allowScriptChatAccess = settings?.allowScriptChatAccess !== false;
        if (!this.allowScriptChatAccess) {
            this.chatMessages = [];
        }
        this.refreshEmoteCatalog();
        this.updateScriptWorkerChatMessages();
        this.updateScriptWorkerEmoteCatalog();
    }

    updateScriptWorkerCanvas() {
        if (!this.scriptWorker || !this.scriptWorkerReady) {
            return;
        }
        this.scriptWorker.postMessage({
            type: "resize",
            payload: {
                width: this.state.canvasSettings.width,
                height: this.state.canvasSettings.height,
            },
        });
    }

    updateScriptWorkerChatMessages() {
        if (!this.scriptWorker || !this.scriptWorkerReady) {
            return;
        }
        this.scriptWorker.postMessage({
            type: "chatMessages",
            payload: {
                messages: this.allowScriptChatAccess ? this.chatMessages : [],
            },
        });
    }

    updateScriptWorkerEmoteCatalog() {
        if (!this.scriptWorker || !this.scriptWorkerReady) {
            return;
        }
        this.scriptWorker.postMessage({
            type: "emoteCatalog",
            payload: {
                emotes: this.emoteCatalog,
            },
        });
    }

    setEmoteCatalog(catalog) {
        this.globalEmotes = Array.isArray(catalog?.global) ? catalog.global : [];
        this.channelEmotes = Array.isArray(catalog?.channel) ? catalog.channel : [];
        this.sevenTvEmotes = Array.isArray(catalog?.sevenTv) ? catalog.sevenTv : [];
        this.refreshEmoteCatalog();
    }

    refreshEmoteCatalog() {
        const allowedChannelEmotes = this.allowChannelEmotesForAssets ? this.channelEmotes : [];
        const allowedSevenTvEmotes = this.allowSevenTvEmotesForAssets ? this.sevenTvEmotes : [];
        this.emoteCatalog = [...this.globalEmotes, ...allowedChannelEmotes, ...allowedSevenTvEmotes];
        this.emoteCatalogById = new Map(
            this.emoteCatalog.map((entry) => [String(entry?.id || ""), entry]).filter(([key]) => key),
        );
        this.sevenTvEmotesByName = new Map(
            allowedSevenTvEmotes
                .map((entry) => [String(entry?.name || ""), entry])
                .filter(([key]) => key),
        );
        if (this.chatMessages.length) {
            this.chatMessages = this.chatMessages.map((message) => {
                const fragments = this.buildMessageFragments(message.message || "", message.tags);
                return { ...message, fragments };
            });
            this.updateScriptWorkerChatMessages();
        }
        this.updateScriptWorkerEmoteCatalog();
    }

    pruneChatMessages(now = Date.now()) {
        const cutoff = now - 120_000;
        const pruned = this.chatMessages.filter((item) => item.timestamp >= cutoff);
        if (pruned.length !== this.chatMessages.length) {
            this.chatMessages = pruned;
            this.updateScriptWorkerChatMessages();
        }
    }

    parseEmoteOffsets(rawEmotes) {
        if (!rawEmotes) {
            return [];
        }
        return rawEmotes
            .split("/")
            .flatMap((emoteEntry) => {
                if (!emoteEntry) {
                    return [];
                }
                const [id, positions] = emoteEntry.split(":");
                if (!id || !positions) {
                    return [];
                }
                return positions.split(",").map((range) => {
                    const [start, end] = range.split("-").map((value) => Number.parseInt(value, 10));
                    return Number.isFinite(start) && Number.isFinite(end) ? { id, start, end } : null;
                });
            })
            .filter(Boolean);
    }

    buildMessageFragments(message, tags) {
        if (!message) {
            return [];
        }
        const emotes = this.parseEmoteOffsets(tags?.emotes);
        if (!emotes.length) {
            return this.applySevenTvEmotes([{ type: "text", text: message }]);
        }
        const sorted = emotes.sort((a, b) => a.start - b.start);
        const fragments = [];
        let cursor = 0;
        sorted.forEach((emote) => {
            if (emote.start > cursor) {
                fragments.push({ type: "text", text: message.slice(cursor, emote.start) });
            }
            const emoteText = message.slice(emote.start, emote.end + 1);
            const emoteInfo = this.emoteCatalogById.get(String(emote.id));
            if (emoteInfo) {
                fragments.push({
                    type: "emote",
                    id: emote.id,
                    text: emoteText,
                    name: emoteInfo?.name || emoteText,
                    url: emoteInfo?.url || null,
                });
            } else {
                fragments.push({ type: "text", text: emoteText });
            }
            cursor = emote.end + 1;
        });
        if (cursor < message.length) {
            fragments.push({ type: "text", text: message.slice(cursor) });
        }
        return this.applySevenTvEmotes(fragments);
    }

    applySevenTvEmotes(fragments) {
        if (!this.sevenTvEmotesByName.size) {
            return fragments;
        }
        const enhanced = [];
        fragments.forEach((fragment) => {
            if (fragment?.type !== "text" || !fragment.text) {
                enhanced.push(fragment);
                return;
            }
            const parts = fragment.text.split(/(\s+)/);
            parts.forEach((part) => {
                if (!part) {
                    return;
                }
                if (/^\s+$/.test(part)) {
                    enhanced.push({ type: "text", text: part });
                    return;
                }
                const emote = this.sevenTvEmotesByName.get(part);
                if (emote) {
                    enhanced.push({
                        type: "emote",
                        id: emote.id,
                        text: part,
                        name: emote.name || part,
                        url: emote.url || null,
                    });
                } else {
                    enhanced.push({ type: "text", text: part });
                }
            });
        });
        return enhanced;
    }

    receiveChatMessage(message) {
        if (!this.allowScriptChatAccess) {
            return;
        }
        if (!message) {
            return;
        }
        const now = Date.now();
        const fragments = this.buildMessageFragments(message.message || "", message.tags);
        const entry = { ...message, fragments, timestamp: now };
        this.chatMessages = [...this.chatMessages, entry];
        this.pruneChatMessages(now);
        this.updateScriptWorkerChatMessages();
    }

    extractScriptErrorLocation(stack, scriptId) {
        if (!stack || !scriptId) {
            return "";
        }
        const label = `user-script-${scriptId}.js`;
        const lines = stack.split("\n");
        const matchingLine = lines.find((line) => line.includes(label));
        if (!matchingLine) {
            return "";
        }
        const match = matchingLine.match(/user-script-[^:]+\.js:(\d+)(?::(\d+))?/);
        if (!match) {
            return "";
        }
        const line = match[1];
        const column = match[2];
        return column ? `line ${line}, col ${column}` : `line ${line}`;
    }

    handleScriptWorkerMessage(event) {
        const { type, payload } = event.data || {};
        if (type === "scriptAudio") {
            this.playScriptAudio(payload);
            return;
        }
        if (type !== "scriptError" || !payload?.id) {
            return;
        }
        const key = `${payload.id}:${payload.stage || "unknown"}`;
        if (this.scriptErrorKeys.has(key)) {
            return;
        }
        this.scriptErrorKeys.add(key);
        const location = this.extractScriptErrorLocation(payload.stack, payload.id);
        const details = payload.message || "Unknown error";
        const detailMessage = location ? `${details} (${location})` : details;
        if (this.showToast) {
            this.showToast(`Script ${payload.id} ${payload.stage || "error"}: ${detailMessage}`, "error");
            if (payload.stack) {
                console.error(`Script ${payload.id} ${payload.stage || "error"}`, payload.stack);
            }
        } else {
            console.error(`Script ${payload.id} ${payload.stage || "error"}`, payload);
        }
    }

    async spawnUserJavaScriptWorker(asset) {
        if (!asset?.id || !asset?.url) {
            return;
        }
        this.ensureScriptWorker();
        if (!this.scriptWorkerReady) {
            return;
        }
        const scriptCanvas = this.ensureScriptCanvas(asset.id);
        if (!scriptCanvas) {
            return;
        }
        if (typeof scriptCanvas.transferControlToOffscreen !== "function") {
            console.warn("OffscreenCanvas is not supported in this environment.");
            return;
        }
        const offscreen = scriptCanvas.transferControlToOffscreen();
        let assetSource;
        try {
            const response = await fetch(asset.url);
            if (!response.ok) {
                throw new Error(`Failed to load script asset ${asset.id}`);
            }
            assetSource = await response.text();
        } catch (error) {
            console.error(`Unable to fetch asset ${asset.id} from ${asset.url}`, error);
            return;
        }
        const attachments = await this.resolveScriptAttachments(asset.scriptAttachments);
        this.scriptAttachmentsByAssetId.set(asset.id, attachments);
        this.scriptWorker.postMessage({
            type: "addScript",
            payload: {
                id: asset.id,
                source: assetSource,
                canvas: offscreen,
                width: scriptCanvas.width,
                height: scriptCanvas.height,
                attachments,
                allowedDomains: Array.isArray(asset.allowedDomains) ? asset.allowedDomains : [],
            },
        }, [offscreen]);
    }

    async updateScriptWorkerAttachments(asset) {
        if (!this.scriptWorker || !this.scriptWorkerReady || !asset?.id) {
            return;
        }
        const attachments = await this.resolveScriptAttachments(asset.scriptAttachments);
        this.scriptAttachmentsByAssetId.set(asset.id, attachments);
        this.scriptWorker.postMessage({
            type: "updateAttachments",
            payload: {
                id: asset.id,
                attachments,
                allowedDomains: Array.isArray(asset.allowedDomains) ? asset.allowedDomains : [],
            },
        });
    }

    stopUserJavaScriptWorker(assetId) {
        if (!this.scriptWorker || !assetId) {
            return;
        }
        this.scriptWorker.postMessage({
            type: "removeScript",
            payload: { id: assetId },
        });
        this.scriptAttachmentsByAssetId.delete(assetId);
        this.removeScriptCanvas(assetId);
    }

    playScriptAudio(payload) {
        if (!payload?.scriptId || !payload?.attachmentId) {
            return;
        }
        const attachments = this.scriptAttachmentsByAssetId.get(payload.scriptId);
        if (!Array.isArray(attachments)) {
            return;
        }
        const attachment = attachments.find((item) => item?.id === payload.attachmentId);
        if (!attachment?.url || !attachment?.mediaType?.startsWith("audio/")) {
            return;
        }
        const audioAsset = {
            id: `script-${payload.scriptId}-${attachment.id}`,
            url: attachment.url,
        };
        this.audioManager.playAudioImmediately(audioAsset);
    }

    ensureScriptCanvas(assetId) {
        if (!assetId || !this.scriptLayer) {
            return null;
        }
        const existing = this.scriptCanvases.get(assetId);
        if (existing) {
            this.applyScriptCanvasOrder();
            return existing;
        }
        const canvas = document.createElement("canvas");
        canvas.className = "broadcast-script-canvas";
        canvas.dataset.scriptId = assetId;
        this.applyScriptCanvasSize(canvas);
        this.scriptLayer.appendChild(canvas);
        this.scriptCanvases.set(assetId, canvas);
        this.applyScriptCanvasOrder();
        return canvas;
    }

    removeScriptCanvas(assetId) {
        const canvas = this.scriptCanvases.get(assetId);
        if (!canvas) {
            return;
        }
        canvas.remove();
        this.scriptCanvases.delete(assetId);
        this.applyScriptCanvasOrder();
    }

    resizeScriptCanvases() {
        this.scriptCanvases.forEach((canvas) => {
            this.applyScriptCanvasSize(canvas);
        });
        this.applyScriptCanvasOrder();
    }

    applyScriptCanvasSize(canvas) {
        if (!canvas) {
            return;
        }
        if (Number.isFinite(this.state.canvasSettings.width)) {
            canvas.width = this.state.canvasSettings.width;
            canvas.style.width = `${this.state.canvasSettings.width}px`;
        }
        if (Number.isFinite(this.state.canvasSettings.height)) {
            canvas.height = this.state.canvasSettings.height;
            canvas.style.height = `${this.state.canvasSettings.height}px`;
        }
    }

    applyScriptCanvasOrder() {
        if (!this.scriptLayer) {
            return;
        }
        const ordered = getScriptLayerOrder(this.state);
        ordered
            .slice()
            .reverse()
            .forEach((id) => {
            const canvas = this.scriptCanvases.get(id);
            if (!canvas) {
                return;
            }
            this.scriptLayer.appendChild(canvas);
        });
    }

    async resolveScriptAttachments(attachments) {
        if (!Array.isArray(attachments) || attachments.length === 0) {
            return [];
        }
        const resolved = await Promise.all(
            attachments.map(async (attachment) => {
                if (!attachment?.url || !attachment.mediaType?.startsWith("image/")) {
                    return attachment;
                }
                const cacheKey = attachment.id || attachment.url;
                const cached = this.scriptAttachmentCache.get(cacheKey);
                if (cached?.blob) {
                    return { ...attachment, blob: cached.blob };
                }
                try {
                    const response = await fetch(attachment.url);
                    if (!response.ok) {
                        throw new Error("Failed to fetch script attachment");
                    }
                    const blob = await response.blob();
                    this.scriptAttachmentCache.set(cacheKey, { blob });
                    return { ...attachment, blob };
                } catch (error) {
                    console.error("Unable to load script attachment", error);
                    return attachment;
                }
            }),
        );
        return resolved;
    }
}
