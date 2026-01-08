import { MIN_FRAME_TIME } from "./const.js"

let frameScheduled = false;
let lastRenderTime = 0;
let pendingDraw = false;

export function draw(ctx) {
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
            renderFrame(ctx);
        }

        frameScheduled = false;
        if (pendingDraw || !shouldRender) {
            pendingDraw = false;
            setTimeout(draw, Math.max(0, delay));
        }
    });
}

function renderFrame(ctx) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    getRenderOrder().forEach(drawAsset);
}
