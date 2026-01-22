const COMMAND = "!chess";
const FEED_URL = "https://lichess.org/api/tv/feed";
const HEADER_HEIGHT = 54;
const PADDING = 16;
const BOARD_MARGIN = 12;
const BORDER_RADIUS = 14;
const FOOTER_HEIGHT = 36;
const LIGHT_SQUARE = "#f0d9b5";
const DARK_SQUARE = "#b58863";
const BOARD_OUTLINE = "#111827";
const PANEL_BG = "rgba(15, 23, 42, 0.72)";
const PANEL_BORDER = "rgba(255, 255, 255, 0.1)";
const TEXT_PRIMARY = "#f8fafc";
const TEXT_MUTED = "#cbd5f5";

const PIECE_ASSETS = {
    P: "Chess_plt45.png",
    R: "Chess_rlt45.png",
    N: "Chess_nlt45.png",
    B: "Chess_blt45.png",
    Q: "Chess_qlt45.png",
    K: "Chess_klt45.png",
    p: "Chess_pdt45.png",
    r: "Chess_rdt45.png",
    n: "Chess_ndt45.png",
    b: "Chess_bdt45.png",
    q: "Chess_qdt45.png",
    k: "Chess_kdt45.png",
};

const ACTIVE_STATUSES = new Set(["started", "playing", "created"]);

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

function parseFen(fen) {
    if (!fen || typeof fen !== "string") {
        return null;
    }
    const parts = fen.trim().split(" ");
    if (parts.length < 2) {
        return null;
    }
    const rows = parts[0].split("/");
    if (rows.length !== 8) {
        return null;
    }
    const board = rows.map((row) => {
        const squares = [];
        for (const char of row) {
            if (Number.isInteger(Number(char))) {
                const emptyCount = Number(char);
                for (let i = 0; i < emptyCount; i += 1) {
                    squares.push(null);
                }
            } else {
                squares.push(char);
            }
        }
        return squares.length === 8 ? squares : null;
    });
    if (board.some((row) => !row || row.length !== 8)) {
        return null;
    }
    return {
        board,
        turn: parts[1],
    };
}

function parseUciMove(move) {
    if (!move || typeof move !== "string" || move.length < 4) {
        return null;
    }
    const files = "abcdefgh";
    const fromFile = files.indexOf(move[0]);
    const fromRank = Number(move[1]);
    const toFile = files.indexOf(move[2]);
    const toRank = Number(move[3]);
    if (fromFile < 0 || toFile < 0 || !fromRank || !toRank) {
        return null;
    }
    return {
        from: { row: 8 - fromRank, col: fromFile },
        to: { row: 8 - toRank, col: toFile },
    };
}

function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
}

function ensurePieceCache(state) {
    if (!state.pieceCache) {
        state.pieceCache = new Map();
    }
    return state.pieceCache;
}

function resolvePieceAsset(pieceKey, assets) {
    const assetName = PIECE_ASSETS[pieceKey];
    if (!assetName || !Array.isArray(assets)) {
        return null;
    }
    return assets.find((asset) => asset?.name === assetName) || null;
}

function loadPieceBitmap(pieceKey, assets, state) {
    const cache = ensurePieceCache(state);
    const existing = cache.get(pieceKey);
    if (existing?.bitmap) {
        return existing.bitmap;
    }
    if (existing?.loading) {
        return null;
    }
    const asset = resolvePieceAsset(pieceKey, assets);
    if (!asset?.url && !asset?.blob) {
        return null;
    }
    const entry = { loading: true, bitmap: null };
    cache.set(pieceKey, entry);
    const blobPromise = asset.blob
        ? Promise.resolve(asset.blob)
        : fetch(asset.url)
            .then((response) => {
                if (!response.ok) {
                    throw new Error("Failed to load piece asset");
                }
                return response.blob();
            });
    blobPromise
        .then((blob) => createImageBitmap(blob))
        .then((bitmap) => {
            entry.bitmap = bitmap;
            entry.loading = false;
        })
        .catch(() => {
            cache.delete(pieceKey);
        });
    return null;
}

function drawRoundedRect(ctx, x, y, width, height, radius) {
    const r = clamp(radius, 0, Math.min(width, height) / 2);
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + width, y, x + width, y + height, r);
    ctx.arcTo(x + width, y + height, x, y + height, r);
    ctx.arcTo(x, y + height, x, y, r);
    ctx.arcTo(x, y, x + width, y, r);
    ctx.closePath();
}

function resolvePayload(event) {
    if (!event || typeof event !== "object") {
        return {};
    }
    if (event.d && typeof event.d === "object") {
        return event.d;
    }
    return event;
}

function resolveGameId(payload) {
    return payload?.id || payload?.gameId || payload?.game?.id || payload?.game?.gameId || null;
}

function resolveStatus(payload) {
    return payload?.status || payload?.game?.status || payload?.status?.name || null;
}

function resolvePlayerFromList(players, color) {
    if (!Array.isArray(players)) {
        return null;
    }
    return players.find((player) => player?.color === color) || null;
}

function resolvePlayers(payload) {
    const list = payload?.players || payload?.game?.players;
    const white =
        resolvePlayerFromList(list, "white") ||
        payload?.players?.white ||
        payload?.white ||
        payload?.game?.players?.white ||
        payload?.game?.white;
    const black =
        resolvePlayerFromList(list, "black") ||
        payload?.players?.black ||
        payload?.black ||
        payload?.game?.players?.black ||
        payload?.game?.black;
    const whiteName = white?.name || white?.user?.name || white?.username || "White";
    const blackName = black?.name || black?.user?.name || black?.username || "Black";
    return { white, black, whiteName, blackName };
}

function resolveRating(player) {
    if (!player) {
        return null;
    }
    return player?.rating || player?.ratingDiff || player?.user?.rating || null;
}

function resolveClockValue(payload, color) {
    const fromList = resolvePlayerFromList(payload?.players || payload?.game?.players, color);
    if (Number.isFinite(fromList?.seconds)) {
        return fromList.seconds;
    }
    const shorthandKey = color === "white" ? "wc" : "bc";
    if (Number.isFinite(payload?.[shorthandKey])) {
        return payload[shorthandKey];
    }
    if (Number.isFinite(payload?.game?.[shorthandKey])) {
        return payload.game[shorthandKey];
    }
    const direct = payload?.clocks?.[color];
    if (Number.isFinite(direct)) {
        return direct;
    }
    const nested = payload?.game?.clocks?.[color];
    if (Number.isFinite(nested)) {
        return nested;
    }
    return null;
}

function formatClock(value) {
    if (!Number.isFinite(value)) {
        return "--:--";
    }
    let seconds = Math.floor(value);
    if (value > 100000) {
        seconds = Math.floor(value / 1000);
    } else if (value > 1000) {
        seconds = Math.floor(value / 100);
    }
    const minutes = Math.floor(seconds / 60);
    const remaining = Math.max(seconds % 60, 0);
    return `${minutes}:${String(remaining).padStart(2, "0")}`;
}

function updateFromEvent(event, state) {
    const payload = resolvePayload(event);
    const gameId = resolveGameId(payload);
    if (gameId && !state.gameId) {
        state.gameId = gameId;
    }
    if (gameId && state.gameId && gameId !== state.gameId) {
        state.shouldStop = true;
        return;
    }

    const fen = payload?.fen || payload?.game?.fen || payload?.position?.fen;
    const parsedFen = parseFen(fen);
    if (parsedFen) {
        state.fen = fen;
        state.board = parsedFen.board;
        state.turn = parsedFen.turn;
        state.boardVisible = true;
    }

    const lastMove = payload?.lm || payload?.lastMove || payload?.move || payload?.moves?.split(" ")?.slice(-1)[0];
    if (lastMove) {
        state.lastMove = lastMove;
    }

    const status = resolveStatus(payload);
    if (status) {
        state.status = status;
    }

    const players = resolvePlayers(payload);
    if (players.whiteName || players.blackName) {
        state.whiteName = players.whiteName;
        state.blackName = players.blackName;
    }

    const whiteRating = resolveRating(players.white);
    const blackRating = resolveRating(players.black);
    if (whiteRating) {
        state.whiteRating = whiteRating;
    }
    if (blackRating) {
        state.blackRating = blackRating;
    }

    const whiteClock = resolveClockValue(payload, "white");
    const blackClock = resolveClockValue(payload, "black");
    if (whiteClock !== null) {
        state.whiteClock = whiteClock;
    }
    if (blackClock !== null) {
        state.blackClock = blackClock;
    }

    if (status && !ACTIVE_STATUSES.has(String(status).toLowerCase())) {
        state.gameOver = true;
    }
}

async function streamFeed(state) {
    state.feedActive = true;
    state.error = null;
    state.shouldStop = false;
    state.gameOver = false;
    state.gameId = null;
    state.status = "connecting";
    state.lastMove = null;
    state.boardVisible = false;
    state.fen = null;
    state.whiteClock = null;
    state.blackClock = null;

    const controller = new AbortController();
    state.abortController = controller;

    let streamEnded = false;
    try {
        const response = await fetch(FEED_URL, {
            method: "GET",
            headers: { Accept: "application/x-ndjson" },
            signal: controller.signal,
        });
        if (!response.ok || !response.body) {
            throw new Error("Unable to load Lichess TV feed");
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";

        while (true) {
            if (state.shouldStop) {
                break;
            }
            const { value, done } = await reader.read();
            if (done) {
                streamEnded = true;
                break;
            }
            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split("\n");
            buffer = lines.pop() || "";
            lines.forEach((line) => {
                if (!line.trim()) {
                    return;
                }
                try {
                    const event = JSON.parse(line);
                    updateFromEvent(event, state);
                } catch (_error) {
                    // Ignore malformed events.
                }
            });

            if (state.gameOver) {
                state.shouldStop = true;
            }
        }
    } catch (error) {
        if (error?.name !== "AbortError") {
            state.error = error?.message || String(error);
        }
    } finally {
        if (state.abortController === controller) {
            state.abortController = null;
        }
        state.feedActive = false;
        if (streamEnded && !state.shouldStop) {
            state.shouldStop = true;
        }
        if (state.shouldStop) {
            state.boardVisible = false;
            state.board = null;
            state.fen = null;
            state.status = null;
            state.gameId = null;
            state.lastMove = null;
            state.turn = null;
            state.gameOver = false;
            state.whiteClock = null;
            state.blackClock = null;
            state.needsClear = true;
        }
    }
}

function stopFeed(state) {
    state.shouldStop = true;
    if (state.abortController) {
        state.abortController.abort();
    }
    state.needsClear = true;
}

function processChatCommands(chatMessages, state) {
    if (!Array.isArray(chatMessages) || chatMessages.length === 0) {
        return;
    }
    const lastSeen = state.lastChatTimestamp ?? 0;
    let latestSeen = lastSeen;
    let shouldStart = false;
    let fallbackTimestamp = lastSeen;

    chatMessages.forEach((message) => {
        let timestamp = message?.timestamp;
        if (!Number.isFinite(timestamp)) {
            fallbackTimestamp += 1;
            timestamp = fallbackTimestamp;
        }
        if (timestamp <= lastSeen) {
            return;
        }
        latestSeen = Math.max(latestSeen, timestamp);
        if (!shouldStart && isCommandMatch(message?.message)) {
            console.log("Lichess TV: Detected command to start stream feed.");
            shouldStart = true;
        }
    });

    state.lastChatTimestamp = latestSeen;

    console.log(`Lichess TV: shouldStart=${shouldStart}, feedActive=${state.feedActive}, boardVisible=${state.boardVisible}`);
    if (shouldStart && !state.feedActive && !state.boardVisible) {
        streamFeed(state);
    }
}

function drawBoard(context, state) {
    const { ctx, width, height, assets } = context;
    const board = state.board;
    if (!ctx || !board) {
        return;
    }

    ctx.clearRect(0, 0, width, height);
    const maxBoardSize = Math.min(width, height) * 0.55;
    const boardSize = clamp(maxBoardSize, 180, Math.min(width, height));
    if (boardSize <= 0) {
        return;
    }

    const panelWidth = boardSize + BOARD_MARGIN * 2;
    const panelHeight = boardSize + HEADER_HEIGHT + FOOTER_HEIGHT + BOARD_MARGIN;
    const panelX = clamp(width - panelWidth - PADDING, PADDING, width - panelWidth);
    const panelY = clamp(height - panelHeight - PADDING, PADDING, height - panelHeight);
    ctx.fillStyle = PANEL_BG;
    drawRoundedRect(ctx, panelX, panelY, panelWidth, panelHeight, BORDER_RADIUS);
    ctx.fill();
    ctx.strokeStyle = PANEL_BORDER;
    ctx.lineWidth = 2;
    ctx.stroke();

    const headerX = panelX + BOARD_MARGIN;
    const headerY = panelY + BOARD_MARGIN * 0.6;

    ctx.fillStyle = TEXT_PRIMARY;
    ctx.font = "600 20px 'Inter', 'Segoe UI', sans-serif";
    ctx.textAlign = "left";
    ctx.textBaseline = "top";

    const metaLabel = `White (${state.whiteRating || "?"}) [${formatClock(state.whiteClock)}] versus Black (${state.blackRating || "?"}) [${formatClock(state.blackClock)}]`;

    ctx.fillText(metaLabel, headerX, headerY);

    const statusValue = state.status ? String(state.status).toUpperCase() : "";
    if (statusValue && statusValue !== "CONNECTING") {
        ctx.fillStyle = TEXT_MUTED;
        ctx.font = "600 13px 'Inter', 'Segoe UI', sans-serif";
        ctx.fillText(statusValue, headerX, headerY + 26);
    }

    const boardX = panelX + BOARD_MARGIN;
    const boardY = panelY + HEADER_HEIGHT;
    const squareSize = boardSize / 8;

    ctx.strokeStyle = BOARD_OUTLINE;
    ctx.lineWidth = 3;
    ctx.strokeRect(boardX - 1.5, boardY - 1.5, boardSize + 3, boardSize + 3);

    const lastMove = parseUciMove(state.lastMove);

    for (let row = 0; row < 8; row += 1) {
        for (let col = 0; col < 8; col += 1) {
            const isLight = (row + col) % 2 === 0;
            ctx.fillStyle = isLight ? LIGHT_SQUARE : DARK_SQUARE;
            ctx.fillRect(boardX + col * squareSize, boardY + row * squareSize, squareSize, squareSize);

            if (
                lastMove &&
                ((lastMove.from.row === row && lastMove.from.col === col) ||
                    (lastMove.to.row === row && lastMove.to.col === col))
            ) {
                ctx.fillStyle = "rgba(59, 130, 246, 0.35)";
                ctx.fillRect(boardX + col * squareSize, boardY + row * squareSize, squareSize, squareSize);
            }
        }
    }

    for (let row = 0; row < 8; row += 1) {
        for (let col = 0; col < 8; col += 1) {
            const piece = board[row]?.[col];
            if (!piece) {
                continue;
            }
            const bitmap = loadPieceBitmap(piece, assets, state);
            if (!bitmap) {
                continue;
            }
            const size = squareSize * 0.85;
            const offset = (squareSize - size) / 2;
            ctx.drawImage(
                bitmap,
                boardX + col * squareSize + offset,
                boardY + row * squareSize + offset,
                size,
                size
            );
        }
    }

    const turnIndicator = state.turn === "b" ? "Black to move" : "White to move";
    ctx.fillStyle = TEXT_PRIMARY;
    ctx.textAlign = "left";
    ctx.fillStyle = TEXT_MUTED;
    ctx.textBaseline = "top";
    ctx.font = "500 12px 'Inter', 'Segoe UI', sans-serif";
    ctx.fillText(turnIndicator, panelX + BOARD_MARGIN, panelY + panelHeight - FOOTER_HEIGHT + 12);
}

function init() { }

function tick(context, state) {
    const { ctx, width, height, chatMessages } = context;
    if (!ctx) {
        return;
    }

    processChatCommands(chatMessages, state);

    if (state.needsClear) {
        ctx.clearRect(0, 0, width, height);
        state.needsClear = false;
    }

    if (!state.boardVisible || !state.board) {
        return;
    }

    drawBoard(context, state);

    if (state.shouldStop) {
        stopFeed(state);
    }
}
