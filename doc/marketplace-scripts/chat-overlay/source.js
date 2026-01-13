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
        const prefixText = message.displayName ? `${message.displayName}: ` : "";
        const bodyText = message.message || "";
        const nameColor = message.tags?.color || "#ffffff";
        if (!prefixText) {
            wrapLine(ctx, bodyText, maxWidth).forEach((line) =>
                lines.push({
                    prefixText: "",
                    prefixWidth: 0,
                    nameColor,
                    text: line,
                }),
            );
            return;
        }

        const prefixWidth = ctx.measureText(prefixText).width;
        const words = bodyText.split(" ");
        let current = "";
        let isFirstLine = true;
        let availableWidth = Math.max(maxWidth - prefixWidth, 0);

        const flushLine = () => {
            lines.push({
                prefixText: isFirstLine ? prefixText : "",
                prefixWidth: isFirstLine ? prefixWidth : 0,
                nameColor,
                text: current,
            });
            current = "";
            isFirstLine = false;
            availableWidth = maxWidth;
        };

        if (!words.length || !bodyText.trim()) {
            lines.push({
                prefixText,
                prefixWidth,
                nameColor,
                text: "",
            });
            return;
        }

        words.forEach((word) => {
            const test = current ? `${current} ${word}` : word;
            if (ctx.measureText(test).width > availableWidth && current) {
                flushLine();
                current = word;
            } else {
                current = test;
            }
        });

        if (current) {
            flushLine();
        }
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
        ...lines.map((line) => line.prefixWidth + ctx.measureText(line.text).width),
        120,
    );

    ctx.fillStyle = "rgba(0, 0, 0, 0.55)";
    ctx.fillRect(PADDING, height - boxHeight - PADDING, boxWidth + PADDING * 2, boxHeight);

    lines.forEach((line, index) => {
        const x = PADDING * 2;
        const y = height - boxHeight - PADDING + PADDING + index * LINE_HEIGHT;
        if (line.prefixText) {
            ctx.fillStyle = line.nameColor || "#ffffff";
            ctx.fillText(line.prefixText, x, y);
        }
        ctx.fillStyle = "#ffffff";
        ctx.fillText(line.text, x + line.prefixWidth, y);
    });
}
