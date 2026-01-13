function init() {}

function tick(context, state) {
    const { ctx, width, height, deltaMs } = context;
    if (!ctx) {
        return;
    }
    const dt = (deltaMs || 16) * 0.001;
    state.angle = (state.angle || 0) + dt * 0.6;

    ctx.clearRect(0, 0, width, height);

    const centerX = width / 2;
    const centerY = height / 2;
    const scale = Math.min(width, height) * 0.32;

    const points = [];
    const segments = 140;
    const halfWidth = 0.35;

    for (let i = 0; i <= segments; i += 1) {
        const t = (i / segments) * Math.PI * 2;
        const cosT = Math.cos(t);
        const sinT = Math.sin(t);

        for (const side of [-halfWidth, halfWidth]) {
            const cosHalf = Math.cos(t / 2);
            const sinHalf = Math.sin(t / 2);
            const radius = 1 + (side * cosHalf) / 2;
            const x = radius * cosT;
            const y = radius * sinT;
            const z = (side * sinHalf) / 2;

            const y1 = y * Math.cos(state.angle) - z * Math.sin(state.angle);
            const z1 = y * Math.sin(state.angle) + z * Math.cos(state.angle);
            const x2 = x * Math.cos(state.angle * 0.7) - z1 * Math.sin(state.angle * 0.7);
            const z2 = x * Math.sin(state.angle * 0.7) + z1 * Math.cos(state.angle * 0.7);

            const depth = 2.6;
            const perspective = depth / (depth - z2);
            const screenX = centerX + x2 * scale * perspective;
            const screenY = centerY + y1 * scale * perspective;
            points.push({ screenX, screenY, depth: z2 });
        }
    }

    ctx.lineWidth = 2;
    for (let i = 0; i < points.length - 2; i += 2) {
        const a = points[i];
        const b = points[i + 1];
        const brightness = Math.max(0.25, (a.depth + 1.5) / 3);
        ctx.strokeStyle = `rgba(56, 189, 248, ${brightness})`;
        ctx.beginPath();
        ctx.moveTo(a.screenX, a.screenY);
        ctx.lineTo(b.screenX, b.screenY);
        ctx.stroke();
    }

    ctx.strokeStyle = "rgba(248, 250, 252, 0.7)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    for (let i = 0; i < points.length; i += 2) {
        const p = points[i];
        if (i === 0) {
            ctx.moveTo(p.screenX, p.screenY);
        } else {
            ctx.lineTo(p.screenX, p.screenY);
        }
    }
    ctx.closePath();
    ctx.stroke();
}
