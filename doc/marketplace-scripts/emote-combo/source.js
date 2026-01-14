const COMBO_MESSAGE_REQUIREMENT = 3;
const COMBO_IDLE_TIMEOUT_SECONDS = 6;
const COMBO_PERSIST_SECONDS = 4;
const SHAKE_DURATION_MS = 350;
const SHAKE_MAGNITUDE = 6;
const ICON_SIZE = 48;
const PADDING = 16;
const FONT = "24px 'Helvetica Neue', Arial, sans-serif";
const LABEL_COLOR = "#ffffff";

function ensureState(state) {
    if (!state.emoteStats) {
        state.emoteStats = new Map();
    }
    if (!state.emoteCache) {
        state.emoteCache = new Map();
    }
    if (!state.messageIndex) {
        state.messageIndex = 0;
    }
    if (!state.lastProcessedTimestamp) {
        state.lastProcessedTimestamp = 0;
    }
    return state;
}

function buildCatalogMap(emoteCatalog) {
    const map = new Map();
    if (!Array.isArray(emoteCatalog)) {
        return map;
    }
    emoteCatalog.forEach((emote) => {
        const name = emote?.name ? String(emote.name) : "";
        if (!name) {
            return;
        }
        map.set(name, {
            id: emote?.id ? String(emote.id) : name,
            name,
            url: emote?.url || null,
        });
    });
    return map;
}

function getEmoteBitmap(url, state) {
    if (!url) {
        return null;
    }
    const cache = state.emoteCache;
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

function addEmoteOccurrence(found, emoteKey, name, url, count = 1) {
    if (!emoteKey) {
        return;
    }
    const existing = found.get(emoteKey) || { key: emoteKey, name, url, count: 0 };
    existing.name = name || existing.name;
    existing.url = url || existing.url;
    existing.count += count;
    found.set(emoteKey, existing);
}

function extractEmotes(message, catalogMap) {
    const found = new Map();
    const fragments = Array.isArray(message?.fragments) ? message.fragments : [];
    let sawEmoteFragment = false;

    fragments.forEach((fragment) => {
        if (fragment?.type === "emote") {
            sawEmoteFragment = true;
            const key = String(fragment?.id || fragment?.name || fragment?.text || fragment?.url || "");
            addEmoteOccurrence(
                found,
                key,
                fragment?.name || fragment?.text || key,
                fragment?.url || null,
                1,
            );
        }
    });

    if (!sawEmoteFragment) {
        const text = message?.message || fragments.map((fragment) => fragment?.text || "").join("");
        if (text && catalogMap.size) {
            text.split(/\s+/).forEach((token) => {
                if (!token) {
                    return;
                }
                const catalogEntry = catalogMap.get(token);
                if (!catalogEntry) {
                    return;
                }
                addEmoteOccurrence(found, catalogEntry.id, catalogEntry.name, catalogEntry.url, 1);
            });
        }
    }

    return found;
}

function updateComboForMessage(message, state, catalogMap) {
    const emotesInMessage = extractEmotes(message, catalogMap);
    state.messageIndex += 1;
    const now = message?.timestamp || Date.now();

    if (!emotesInMessage.size) {
        if (state.previousMessageKeys) {
            state.previousMessageKeys.forEach((key) => {
                const stats = state.emoteStats.get(key);
                if (stats && stats.lastMessageIndex === state.messageIndex - 1) {
                    stats.streak = 0;
                    state.emoteStats.set(key, stats);
                }
            });
        }
        state.previousMessageKeys = new Set();
        return;
    }
    const messageKeys = new Set(emotesInMessage.keys());

    emotesInMessage.forEach((emote) => {
        const key = emote.key;
        const stats = state.emoteStats.get(key) || {
            key,
            name: emote.name,
            url: emote.url,
            streak: 0,
            lastMessageIndex: 0,
            comboCount: 0,
            lastSeenTime: 0,
        };
        stats.name = emote.name || stats.name;
        stats.url = emote.url || stats.url;

        if (stats.lastMessageIndex === state.messageIndex - 1) {
            stats.streak += 1;
        } else {
            stats.streak = 1;
            stats.comboCount = 0;
        }

        stats.lastMessageIndex = state.messageIndex;
        stats.lastSeenTime = now;

        if (stats.streak >= COMBO_MESSAGE_REQUIREMENT) {
            if (!state.activeComboKey || state.activeComboKey === key) {
                if (state.activeComboKey !== key) {
                    stats.comboCount = stats.streak;
                    state.activeComboKey = key;
                } else {
                    stats.comboCount += 1;
                }
                state.activeComboLastSeen = now;
                state.lastCombo = {
                    key,
                    name: stats.name,
                    url: stats.url,
                    count: stats.comboCount,
                };
                state.shakeUntil = now + SHAKE_DURATION_MS;
            }
        }

        state.emoteStats.set(key, stats);
    });

    if (state.previousMessageKeys) {
        state.previousMessageKeys.forEach((key) => {
            if (messageKeys.has(key)) {
                return;
            }
            const stats = state.emoteStats.get(key);
            if (stats && stats.lastMessageIndex === state.messageIndex - 1) {
                stats.streak = 0;
                state.emoteStats.set(key, stats);
            }
        });
    }

    state.previousMessageKeys = messageKeys;
}

function processNewMessages(messages, state, catalogMap) {
    const freshMessages = messages.filter((message) => {
        const timestamp = message?.timestamp || 0;
        return timestamp > state.lastProcessedTimestamp;
    });

    freshMessages.forEach((message) => {
        updateComboForMessage(message, state, catalogMap);
        state.lastProcessedTimestamp = Math.max(state.lastProcessedTimestamp, message.timestamp || 0);
    });
}

function resolveDisplayedCombo(state, now) {
    if (state.activeComboKey) {
        const stats = state.emoteStats.get(state.activeComboKey);
        if (stats && now - stats.lastSeenTime <= COMBO_IDLE_TIMEOUT_SECONDS * 1000) {
            return {
                name: stats.name,
                url: stats.url,
                count: stats.comboCount,
                active: true,
            };
        }
        state.activeComboKey = null;
        state.comboEndedAt = now;
        if (state.lastCombo) {
            state.lastCombo = { ...state.lastCombo };
        }
    }

    if (state.comboEndedAt && now - state.comboEndedAt <= COMBO_PERSIST_SECONDS * 1000) {
        return state.lastCombo ? { ...state.lastCombo, active: false } : null;
    }

    return null;
}

function drawCombo(context, state, combo, now) {
    const { ctx, width, height } = context;
    if (!ctx || !combo) {
        return;
    }

    ctx.clearRect(0, 0, width, height);
    ctx.font = FONT;
    ctx.textBaseline = "middle";

    let shakeX = 0;
    let shakeY = 0;
    if (state.shakeUntil && now < state.shakeUntil) {
        shakeX = (Math.random() * 2 - 1) * SHAKE_MAGNITUDE;
        shakeY = (Math.random() * 2 - 1) * SHAKE_MAGNITUDE;
    }

    const iconX = PADDING + shakeX;
    const iconY = height - PADDING - ICON_SIZE + shakeY;
    const labelX = iconX + ICON_SIZE + PADDING;
    const labelY = iconY + ICON_SIZE / 2;

    const bitmap = getEmoteBitmap(combo.url, state);
    if (bitmap) {
        ctx.drawImage(bitmap, iconX, iconY, ICON_SIZE, ICON_SIZE);
    } else if (combo.name) {
        ctx.fillStyle = LABEL_COLOR;
        ctx.fillText(combo.name, iconX, labelY);
    }

    ctx.fillStyle = LABEL_COLOR;
    ctx.fillText(`${combo.count} x Combo`, labelX, labelY);
}

function tick(context, state) {
    const { chatMessages, emoteCatalog } = context;
    if (!context?.ctx) {
        return;
    }
    ensureState(state);
    const messages = Array.isArray(chatMessages) ? chatMessages : [];
    const catalogMap = buildCatalogMap(emoteCatalog);
    processNewMessages(messages, state, catalogMap);

    const now = Date.now();
    const combo = resolveDisplayedCombo(state, now);
    if (!combo) {
        context.ctx.clearRect(0, 0, context.width, context.height);
        return;
    }
    drawCombo(context, state, combo, now);
}
