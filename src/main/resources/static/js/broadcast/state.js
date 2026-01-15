export function createBroadcastState() {
    return {
        canvasSettings: { width: 1920, height: 1080 },
        assets: new Map(),
        mediaCache: new Map(),
        renderStates: new Map(),
        visibilityStates: new Map(),
        animatedCache: new Map(),
        blobCache: new Map(),
        animationFailures: new Map(),
        videoPlaybackStates: new WeakMap(),
        layerOrder: [],
        scriptLayerOrder: [],
    };
}
