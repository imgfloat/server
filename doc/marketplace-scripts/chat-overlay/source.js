const MAX_LINES = 8;
const PADDING = 16;
const LINE_HEIGHT = 22;
const EMOTE_SIZE = 18;
const FONT = "16px 'Helvetica Neue', Arial, sans-serif";

function ensureEmoteCache(state) {
    if (!state.emoteCache) {
        state.emoteCache = new Map();
    }
    return state.emoteCache;
}

function getEmoteBitmap(url, state) {
    if (!url) {
        return null;
    }
    const cache = ensureEmoteCache(state);
    const existing = cache.get(url);
    if (existing?.bitmap) {
        return existing.bitmap;
    }
    if (existing?.loading) {
        return null;
    }
    const entry = { loading: true, bitmap: null };
    cache.set(url, entry);
    fetch(url)
        .then((response) => {
            if (!response.ok) {
                throw new Error("Failed to load emote");
            }
            return response.blob();
        })
        .then((blob) => createImageBitmap(blob))
        .then((bitmap) => {
            entry.bitmap = bitmap;
            entry.loading = false;
        })
        .catch(() => {
            cache.delete(url);
        });
    return null;
}

function normalizeFragments(message) {
    if (Array.isArray(message?.fragments) && message.fragments.length) {
        return message.fragments;
    }
    const text = message?.message || "";
    return [{ type: "text", text }];
}

function tokenizeFragments(fragments) {
    const tokens = [];
    fragments.forEach((fragment) => {
        if (fragment?.type === "emote" && fragment?.url) {
            tokens.push({
                type: "emote",
                url: fragment.url,
                text: fragment.text || fragment.name || "",
                width: EMOTE_SIZE,
            });
            return;
        }
        const text = fragment?.text || "";
        const words = text.split(" ");
        words.forEach((word, index) => {
            const value = index < words.length - 1 ? `${word} ` : word;
            if (value) {
                tokens.push({ type: "text", text: value });
            }
        });
    });
    return tokens;
}

function formatLines(messages, ctx, width) {
    const maxWidth = Math.max(width - PADDING * 2, 0);
    const lines = [];
    messages.forEach((message) => {
        const prefixText = message.displayName ? `${message.displayName}: ` : "";
        const nameColor = message.tags?.color || "#ffffff";
        const prefixWidth = ctx.measureText(prefixText).width;
        const tokens = tokenizeFragments(normalizeFragments(message));
        if (!tokens.length) {
            lines.push({
                prefixText,
                prefixWidth,
                nameColor,
                fragments: [],
                contentWidth: 0,
            });
            return;
        }

        let isFirstLine = true;
        let availableWidth = Math.max(maxWidth - (prefixText ? prefixWidth : 0), 0);
        let currentFragments = [];
        let currentWidth = 0;

        const flushLine = () => {
            lines.push({
                prefixText: isFirstLine ? prefixText : "",
                prefixWidth: isFirstLine ? prefixWidth : 0,
                nameColor,
                fragments: currentFragments,
                contentWidth: currentWidth,
            });
            currentFragments = [];
            currentWidth = 0;
            isFirstLine = false;
            availableWidth = maxWidth;
        };

        tokens.forEach((token) => {
            const tokenWidth =
                token.type === "emote" ? token.width : ctx.measureText(token.text || "").width;
            if (tokenWidth > availableWidth && currentFragments.length) {
                flushLine();
            }
            currentFragments.push({ ...token, width: tokenWidth });
            currentWidth += tokenWidth;
            availableWidth = Math.max(availableWidth - tokenWidth, 0);
        });

        if (currentFragments.length) {
            flushLine();
        }
    });
    return lines.slice(-MAX_LINES);
}

function tick(context, state) {
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
    const boxWidth = Math.max(...lines.map((line) => line.prefixWidth + line.contentWidth), 120);

    ctx.fillStyle = "rgba(0, 0, 0, 0.55)";
    ctx.fillRect(PADDING, height - boxHeight - PADDING, boxWidth + PADDING * 2, boxHeight);

    lines.forEach((line, index) => {
        const x = PADDING * 2;
        const y = height - boxHeight - PADDING + PADDING + index * LINE_HEIGHT;
        if (line.prefixText) {
            ctx.fillStyle = line.nameColor || "#ffffff";
            ctx.fillText(line.prefixText, x, y);
        }
        let cursorX = x + line.prefixWidth;
        line.fragments.forEach((fragment) => {
            if (fragment.type === "emote" && fragment.url) {
                const bitmap = getEmoteBitmap(fragment.url, state);
                if (bitmap) {
                    const yOffset = y + (LINE_HEIGHT - EMOTE_SIZE) / 2;
                    ctx.drawImage(bitmap, cursorX, yOffset, EMOTE_SIZE, EMOTE_SIZE);
                } else if (fragment.text) {
                    ctx.fillStyle = "#ffffff";
                    ctx.fillText(fragment.text, cursorX, y);
                }
                cursorX += fragment.width;
                return;
            }
            if (fragment.text) {
                ctx.fillStyle = "#ffffff";
                ctx.fillText(fragment.text, cursorX, y);
            }
            cursorX += fragment.width;
        });
    });
}
