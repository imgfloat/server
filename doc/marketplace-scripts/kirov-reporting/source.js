const COMMAND = "!kirov";
const COOLDOWN_MS = 5000;
const ATTACHMENT_NAME = "kirov_reporting.mp3";

function normalizeMessage(message) {
    return (message || "").trim().toLowerCase();
}

function isCommandMatch(message) {
    const normalized = normalizeMessage(message);
    if (!normalized.startsWith(COMMAND)) {
        return false;
    }
    return normalized.length === COMMAND.length || normalized.startsWith(`${COMMAND} `);
}

function resolveAttachmentId(assets, state) {
    if (state?.attachmentId) {
        return state.attachmentId;
    }
    if (!Array.isArray(assets)) {
        return null;
    }
    const match = assets.find((asset) => asset?.name?.toLowerCase() === ATTACHMENT_NAME);
    if (!match?.id) {
        return null;
    }
    state.attachmentId = match.id;
    return match.id;
}

function tick(context, state) {
    const { chatMessages, assets, now, playAudio } = context;
    if (!Array.isArray(chatMessages) || chatMessages.length === 0) {
        return;
    }
    if (typeof playAudio !== "function") {
        return;
    }
    const lastSeen = state.lastChatTimestamp ?? 0;
    let latestSeen = lastSeen;
    const currentTime = Number.isFinite(now) ? now : 0;
    const lastTriggerAt = state.lastTriggerAt ?? -Infinity;
    let nextTriggerAt = lastTriggerAt;
    let shouldTrigger = false;

    chatMessages.forEach((message) => {
        const timestamp = message?.timestamp ?? 0;
        if (timestamp <= lastSeen) {
            return;
        }
        latestSeen = Math.max(latestSeen, timestamp);
        if (!shouldTrigger && isCommandMatch(message?.message)) {
            if (currentTime - lastTriggerAt >= COOLDOWN_MS) {
                shouldTrigger = true;
            }
        }
    });

    state.lastChatTimestamp = latestSeen;

    if (!shouldTrigger) {
        return;
    }

    const attachmentId = resolveAttachmentId(assets, state);
    if (!attachmentId) {
        return;
    }

    playAudio(attachmentId);
    state.lastTriggerAt = currentTime;
}
