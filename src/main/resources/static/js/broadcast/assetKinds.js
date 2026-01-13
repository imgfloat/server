import { isAudioAsset } from "../media/audio.js";
import { AssetKind } from "./constants.js";

export function isCodeAsset(asset) {
    if (asset?.assetType) {
        return asset.assetType === "SCRIPT";
    }
    const type = (asset?.mediaType || asset?.originalMediaType || "").toLowerCase();
    return type.startsWith("application/javascript") || type.startsWith("text/javascript");
}

export function isVideoAsset(asset) {
    if (asset?.assetType) {
        return asset.assetType === "VIDEO";
    }
    return asset?.mediaType?.startsWith("video/");
}

export function isModelAsset(asset) {
    if (asset?.assetType) {
        return asset.assetType === "MODEL";
    }
    return asset?.mediaType?.startsWith("model/");
}

export function isVideoElement(element) {
    return element?.tagName === "VIDEO";
}

export function isGifAsset(asset) {
    return asset?.mediaType?.toLowerCase() === "image/gif";
}

export function getAssetKind(asset) {
    if (isAudioAsset(asset)) {
        return AssetKind.AUDIO;
    }
    if (isCodeAsset(asset)) {
        return AssetKind.CODE;
    }
    return AssetKind.VISUAL;
}

export function isVisualAsset(asset) {
    return getAssetKind(asset) === AssetKind.VISUAL;
}
