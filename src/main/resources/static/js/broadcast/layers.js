import { isCodeAsset, isVisualAsset } from "./assetKinds.js";

function isScriptAsset(asset) {
    return isCodeAsset(asset);
}

function isLayerableVisual(asset) {
    return isVisualAsset(asset) && !isScriptAsset(asset);
}

function getLayerBucket(state, asset) {
    if (isScriptAsset(asset)) {
        if (!Array.isArray(state.scriptLayerOrder)) {
            state.scriptLayerOrder = [];
        }
        return state.scriptLayerOrder;
    }
    if (isLayerableVisual(asset)) {
        return state.layerOrder;
    }
    return null;
}

function normalizeOrder(state, predicate, existing) {
    const filtered = existing.filter((id) => {
        const asset = state.assets.get(id);
        return asset && predicate(asset);
    });
    state.assets.forEach((asset, id) => {
        if (!predicate(asset)) {
            return;
        }
        if (!filtered.includes(id)) {
            filtered.push(id);
        }
    });
    return filtered;
}

export function ensureLayerPosition(state, assetId, placement = "keep") {
    const asset = state.assets.get(assetId);
    if (!asset) {
        return;
    }
    const bucket = getLayerBucket(state, asset);
    if (!bucket) {
        return;
    }
    const existingIndex = bucket.indexOf(assetId);
    if (existingIndex !== -1 && placement === "keep") {
        return;
    }
    if (existingIndex !== -1) {
        bucket.splice(existingIndex, 1);
    }
    if (placement === "append") {
        bucket.push(assetId);
    } else {
        bucket.unshift(assetId);
    }
    if (bucket === state.layerOrder) {
        state.layerOrder = normalizeOrder(state, isLayerableVisual, bucket);
    } else {
        state.scriptLayerOrder = normalizeOrder(state, isScriptAsset, bucket);
    }
}

export function getLayerOrder(state) {
    state.layerOrder = normalizeOrder(state, isLayerableVisual, state.layerOrder);
    return state.layerOrder;
}

export function getScriptLayerOrder(state) {
    if (!Array.isArray(state.scriptLayerOrder)) {
        state.scriptLayerOrder = [];
    }
    state.scriptLayerOrder = normalizeOrder(state, isScriptAsset, state.scriptLayerOrder);
    return state.scriptLayerOrder;
}

export function getRenderOrder(state) {
    return [...getLayerOrder(state)]
        .reverse()
        .map((id) => state.assets.get(id))
        .filter(Boolean);
}
