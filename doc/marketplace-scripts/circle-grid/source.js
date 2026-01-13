function init() {}

function tick(context, state) {
    const { ctx, width, height, deltaMs } = context;
    if (!ctx) {
        return;
    }
    ctx.clearRect(0, 0, width, height);

    const centerX = width / 2;
    const centerY = height / 2;
    const maxRadius = Math.min(width, height) * 0.45;
    const pulse = 0.02 * (deltaMs || 0);
    state.phase = (state.phase || 0) + pulse;

    ctx.strokeStyle = "#38bdf8";
    ctx.lineWidth = 2;
    for (let radius = maxRadius; radius > 0; radius -= maxRadius / 6) {
        ctx.globalAlpha = 0.2 + (radius / maxRadius) * 0.6;
        ctx.beginPath();
        ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
        ctx.stroke();
    }
    ctx.globalAlpha = 1;

    ctx.strokeStyle = "#22c55e";
    ctx.lineWidth = 3;
    const sweep = (Math.sin(state.phase) + 1) / 2;
    const sweepRadius = maxRadius * (0.3 + sweep * 0.7);
    ctx.beginPath();
    ctx.arc(centerX, centerY, sweepRadius, 0, Math.PI * 2);
    ctx.stroke();

    ctx.strokeStyle = "#f8fafc";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(centerX, 0);
    ctx.lineTo(centerX, height);
    ctx.moveTo(0, centerY);
    ctx.lineTo(width, centerY);
    ctx.stroke();
}
