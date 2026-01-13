function init() {}

function tick(context) {
    const { ctx, width, height } = context;
    if (!ctx) {
        return;
    }
    ctx.clearRect(0, 0, width, height);
    const topHeight = height * 0.7;
    const barWidth = width / 7;
    const colors = [
        "#ffffff",
        "#ffff00",
        "#00ffff",
        "#00ff00",
        "#ff00ff",
        "#ff0000",
        "#0000ff",
    ];
    colors.forEach((color, index) => {
        ctx.fillStyle = color;
        ctx.fillRect(index * barWidth, 0, barWidth, topHeight);
    });

    const middleHeight = height * 0.15;
    const middleColors = ["#0000ff", "#000000", "#ff00ff", "#000000", "#00ffff", "#000000", "#ffffff"];
    middleColors.forEach((color, index) => {
        ctx.fillStyle = color;
        ctx.fillRect(index * barWidth, topHeight, barWidth, middleHeight);
    });

    const bottomHeight = height - topHeight - middleHeight;
    const bottomColors = ["#2b2b2b", "#ffffff", "#2b2b2b", "#00ffff", "#2b2b2b", "#ff00ff", "#2b2b2b"];
    bottomColors.forEach((color, index) => {
        ctx.fillStyle = color;
        ctx.fillRect(index * barWidth, topHeight + middleHeight, barWidth, bottomHeight);
    });
}
