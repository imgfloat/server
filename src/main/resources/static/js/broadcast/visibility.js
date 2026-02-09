export function getVisibilityState(state, asset) {
    const current = state.visibilityStates.get(asset.id) || {};
    const targetAlpha = asset.hidden ? 0 : 1;
    const startingAlpha = Number.isFinite(current.alpha) ? current.alpha : 0;
    const factor = asset.hidden ? 0.18 : 0.2;
    const nextAlpha = lerp(startingAlpha, targetAlpha, factor);
    const nextState = { alpha: nextAlpha, targetHidden: !!asset.hidden };
    state.visibilityStates.set(asset.id, nextState);
    return nextState;
}

export function smoothState(state, asset) {
    const previous = state.renderStates.get(asset.id) || {};
    const next = {
        x: Number.isFinite(asset.x) ? asset.x : previous.x ?? 0,
        y: Number.isFinite(asset.y) ? asset.y : previous.y ?? 0,
        width: Number.isFinite(asset.width) ? asset.width : previous.width ?? 0,
        height: Number.isFinite(asset.height) ? asset.height : previous.height ?? 0,
        rotation: Number.isFinite(asset.rotation) ? asset.rotation : previous.rotation ?? 0,
    };
    state.renderStates.set(asset.id, next);
    return next;
}

function lerp(a, b, t) {
    return a + (b - a) * t;
}
