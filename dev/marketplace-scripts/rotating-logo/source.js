async function init(context, state) {
    const asset = Array.isArray(context.assets) ? context.assets[0] : null;
    if (!asset?.blob) {
        return;
    }
    state.rotation = 0;
    state.imageReady = false;
    try {
        state.image = await createImageBitmap(asset.blob);
        state.imageReady = true;
    } catch (error) {
        state.imageError = error;
    }
}

function tick(context, state) {
    const { ctx, width, height, deltaMs } = context;
    if (!ctx) {
        return;
    }
    ctx.clearRect(0, 0, width, height);
    const image = state?.image;
    if (!image || !state.imageReady) {
        return;
    }
    const size = Math.min(width, height) * 0.35;
    state.rotation = (state.rotation + (deltaMs || 0) * 0.002) % (Math.PI * 2);
    ctx.save();
    ctx.translate(width / 2, height / 2);
    ctx.rotate(state.rotation);
    ctx.drawImage(image, -size / 2, -size / 2, size, size);
    ctx.restore();
}
