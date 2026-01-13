function init() {}

function tick(context, state) {
    const { ctx, width, height, deltaMs } = context;
    if (!ctx) {
        return;
    }
    const speed = 0.02;
    const offset = ((state.offset || 0) + (deltaMs || 0) * speed) % 40;
    state.offset = offset;

    const squareSize = 40;
    ctx.clearRect(0, 0, width, height);
    for (let y = -squareSize; y < height + squareSize; y += squareSize) {
        for (let x = -squareSize; x < width + squareSize; x += squareSize) {
            const isDark = ((x + y) / squareSize) % 2 === 0;
            ctx.fillStyle = isDark ? "#101828" : "#e2e8f0";
            ctx.fillRect(x + offset, y + offset, squareSize, squareSize);
        }
    }

    ctx.strokeStyle = "#ef4444";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(width / 2, 0);
    ctx.lineTo(width / 2, height);
    ctx.moveTo(0, height / 2);
    ctx.lineTo(width, height / 2);
    ctx.stroke();
}
