const MAX_LINES = 8;
const PADDING = 16;
const LINE_HEIGHT = 22;
const FONT = "16px 'Helvetica Neue', Arial, sans-serif";

function wrapLine(ctx, text, maxWidth) {
    if (!text) {
        return [""];
    }
    const words = text.split(" ");
    const lines = [];
    let current = "";
    words.forEach((word) => {
        const test = current ? `${current} ${word}` : word;
        if (ctx.measureText(test).width > maxWidth && current) {
            lines.push(current);
            current = word;
        } else {
            current = test;
        }
    });
    if (current) {
        lines.push(current);
    }
    return lines;
}

function formatLines(messages, ctx, width) {
    const maxWidth = Math.max(width - PADDING * 2, 0);
    const lines = [];
    messages.forEach((message) => {
        const prefix = message.displayName ? `${message.displayName}: ` : "";
        const raw = `${prefix}${message.message || ""}`;
        wrapLine(ctx, raw, maxWidth).forEach((line) => lines.push(line));
    });
    return lines.slice(-MAX_LINES);
}

function tick(context) {
    const { ctx, width, height, chatMessages } = context;
    if (!ctx) {
        return;
    }
    ctx.clearRect(0, 0, width, height);
    ctx.font = FONT;
    ctx.textBaseline = "top";

    const messages = Array.isArray(chatMessages) ? chatMessages : [];
    if (messages.length === 0) {
        return;
    }

    const lines = formatLines(messages, ctx, width);
    const boxHeight = lines.length * LINE_HEIGHT + PADDING * 2;
    const boxWidth = Math.max(
        ...lines.map((line) => ctx.measureText(line).width),
        120,
    );

    ctx.fillStyle = "rgba(0, 0, 0, 0.55)";
    ctx.fillRect(PADDING, height - boxHeight - PADDING, boxWidth + PADDING * 2, boxHeight);

    ctx.fillStyle = "#ffffff";
    lines.forEach((line, index) => {
        ctx.fillText(line, PADDING * 2, height - boxHeight - PADDING + PADDING + index * LINE_HEIGHT);
    });
}
