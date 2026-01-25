const scripts = new Map();
const allowedFetchUrls = new Set();
let channelName = "";
let tickIntervalId = null;
let lastTick = 0;
let startTime = 0;
const tickIntervalMs = 1000 / 60;
const errorKeys = new Set();
const allowedImportUrls = new Set();
const nativeImportScripts = typeof self.importScripts === "function" ? self.importScripts.bind(self) : null;
const sharedDependencyUrls = ["/js/vendor/three.min.js", "/js/vendor/GLTFLoader.js", "/js/vendor/OBJLoader.js"];
const nativeFetch = typeof self.fetch === "function" ? self.fetch.bind(self) : null;
let activeScriptId = null;
let chatMessages = [];
let emoteCatalog = [];

function normalizeUrl(url) {
    try {
        return new URL(url, self.location?.href || "http://localhost").toString();
    } catch (_error) {
        return "";
    }
}

function registerAllowedImport(url) {
    const normalized = normalizeUrl(url);
    if (normalized) {
        allowedImportUrls.add(normalized);
    }
}

sharedDependencyUrls.forEach(registerAllowedImport);

function importAllowedScripts(...urls) {
    if (!nativeImportScripts) {
        throw new Error("Network access is disabled in asset scripts.");
    }
    const resolved = urls.map((url) => normalizeUrl(url));
    if (resolved.some((url) => !allowedImportUrls.has(url))) {
        throw new Error("Network access is disabled in asset scripts.");
    }
    return nativeImportScripts(...resolved);
}

function loadSharedDependencies() {
    if (!nativeImportScripts || sharedDependencyUrls.length === 0) {
        return;
    }
    importAllowedScripts(...sharedDependencyUrls);
}

loadSharedDependencies();

function sanitizeAllowedDomains(domains) {
    if (!Array.isArray(domains)) {
        return [];
    }
    const normalized = [];
    domains.forEach((raw) => {
        const candidate = typeof raw === "string" ? raw.trim() : "";
        if (!candidate) {
            return;
        }
        const withScheme = candidate.includes("://") ? candidate : `https://${candidate}`;
        try {
            const url = new URL(withScheme, self.location?.href || "http://localhost");
            if (!url.hostname) {
                return;
            }
            const host = url.hostname.toLowerCase();
            const port = url.port ? `:${url.port}` : "";
            const value = `${host}${port}`;
            if (!normalized.includes(value) && normalized.length < 32) {
                normalized.push(value);
            }
        } catch (_error) {
            // ignore invalid domains from metadata/user input
        }
    });
    return normalized;
}

function extractUrlFromInput(input) {
    if (typeof input === "string") {
        return input;
    }
    if (input && typeof input.url === "string") {
        return input.url;
    }
    return "";
}

function extractDomain(url) {
    try {
        return new URL(url, self.location?.href || "http://localhost").host.toLowerCase();
    } catch (_error) {
        return "";
    }
}

function isSameOrigin(url) {
    if (!self.location?.origin) {
        return false;
    }
    try {
        return new URL(url, self.location.origin).origin === self.location.origin;
    } catch (_error) {
        return false;
    }
}

function domainMatches(domain, allowed) {
    if (!domain || !allowed) {
        return false;
    }
    if (allowed.includes(":")) {
        return domain === allowed;
    }
    return domain === allowed || domain.endsWith(`.${allowed}`);
}

function isFetchAllowedForScript(script, targetUrl) {
    const normalized = normalizeUrl(targetUrl);
    if (!normalized) {
        return { allowed: false, reason: "Invalid or empty URL" };
    }
    if (isSameOrigin(normalized) || allowedFetchUrls.has(normalized)) {
        return { allowed: true, normalized };
    }
    const domain = extractDomain(normalized);
    if (!domain) {
        return { allowed: false, reason: "Invalid URL" };
    }
    const allowedDomains = Array.isArray(script.allowedDomains) ? script.allowedDomains : [];
    const allowed = allowedDomains.some((value) => domainMatches(domain, value));
    return allowed
        ? { allowed: true, normalized }
        : { allowed: false, reason: `Domain ${domain} is not in the allowed list.`, normalized };
}

function createScriptFetch(script) {
    return async function scriptFetch(input, init) {
        const targetUrl = extractUrlFromInput(input);
        const decision = isFetchAllowedForScript(script, targetUrl);
        console.info(
            `Script ${script.id} fetch attempt`,
            JSON.stringify({
                url: targetUrl || "",
                normalized: decision.normalized || "",
                allowedDomains: script.allowedDomains,
                allowed: decision.allowed,
                reason: decision.reason,
            })
        );
        if (!decision.allowed) {
            const message = `Fetch blocked for script ${script.id}: ${decision.reason}`;
            const error = new Error(message);
            console.error(message);
            reportScriptError(script.id, "fetch", error);
            return Promise.reject(error);
        }
        if (!nativeFetch) {
            const error = new Error("Fetch is unavailable in this environment.");
            reportScriptError(script.id, "fetch", error);
            return Promise.reject(error);
        }
        try {
            return await nativeFetch(input, init);
        } catch (error) {
            console.error(`Fetch failed for script ${script.id} (${targetUrl || "<unknown>"})`, error);
            reportScriptError(script.id, "fetch", error);
            throw error;
        }
    };
}

function fetchForActiveScript(input, init) {
    const script = activeScriptId ? scripts.get(activeScriptId) : null;
    if (!script) {
        const error = new Error("Fetch is only available inside a running script context.");
        console.error(error.message);
        return Promise.reject(error);
    }
    if (!script.context.fetch) {
        script.context.fetch = createScriptFetch(script);
    }
    return script.context.fetch(input, init);
}

if (nativeFetch) {
    self.fetch = function sandboxedFetch(input, init) {
        return fetchForActiveScript(input, init);
    };
}

function refreshAllowedFetchUrls() {
    allowedFetchUrls.clear();
    scripts.forEach((script) => {
        const assets = script?.context?.assets;
        if (!Array.isArray(assets)) {
            return;
        }
        assets.forEach((asset) => {
            if (asset?.url) {
                const normalized = normalizeUrl(asset.url);
                if (normalized) {
                    allowedFetchUrls.add(normalized);
                }
            }
        });
    });
    if (Array.isArray(emoteCatalog)) {
        emoteCatalog.forEach((emote) => {
            if (emote?.url) {
                const normalized = normalizeUrl(emote.url);
                if (normalized) {
                    allowedFetchUrls.add(normalized);
                }
            }
        });
    }
    if (Array.isArray(chatMessages)) {
        chatMessages.forEach((message) => {
            const fragments = message?.fragments;
            if (!Array.isArray(fragments)) {
                return;
            }
            fragments.forEach((fragment) => {
                if (fragment?.url) {
                    const normalized = normalizeUrl(fragment.url);
                    if (normalized) {
                        allowedFetchUrls.add(normalized);
                    }
                }
            });
        });
    }
}

function reportScriptError(id, stage, error) {
    if (!id) {
        return;
    }
    const key = `${id}:${stage}:${error?.message ?? error}`;
    if (errorKeys.has(key)) {
        return;
    }
    errorKeys.add(key);
    self.postMessage({
        type: "scriptError",
        payload: {
            id,
            stage,
            message: error?.message ?? String(error),
            stack: error?.stack || "",
        },
    });
}

function updateScriptContexts() {
    scripts.forEach((script) => {
        if (!script.context) {
            return;
        }
        script.context.canvas = script.canvas;
        script.context.ctx = script.ctx;
        script.context.channelName = channelName;
        script.context.width = script.canvas?.width ?? 0;
        script.context.height = script.canvas?.height ?? 0;
        script.context.chatMessages = chatMessages;
        script.context.emoteCatalog = emoteCatalog;
        script.context.allowedDomains = script.allowedDomains;
    });
}

function ensureTickLoop() {
    if (tickIntervalId) {
        return;
    }
    startTime = performance.now();
    lastTick = startTime;
    tickIntervalId = setInterval(() => {
        if (scripts.size === 0) {
            return;
        }
        const now = performance.now();
        const deltaMs = now - lastTick;
        const elapsedMs = now - startTime;
        lastTick = now;

        scripts.forEach((script) => {
            if (!script.tick || !script.ctx) {
                return;
            }
            script.context.now = now;
            script.context.deltaMs = deltaMs;
            script.context.elapsedMs = elapsedMs;
            try {
                activeScriptId = script.id;
                script.tick(script.context, script.state);
            } catch (error) {
                console.error(`Script ${script.id} tick failed`, error);
            } finally {
                activeScriptId = null;
            }
        });
    }, tickIntervalMs);
}

function stopTickLoopIfIdle() {
    if (scripts.size === 0 && tickIntervalId) {
        clearInterval(tickIntervalId);
        tickIntervalId = null;
    }
}

function createScriptHandlers(source, context, state, sourceLabel = "") {
    const contextPrelude =
        "const { canvas, ctx, channelName, width, height, now, deltaMs, elapsedMs, assets, chatMessages, emoteCatalog, playAudio, fetch, allowedDomains } = context;";
    const sourceUrl = sourceLabel ? `\n//# sourceURL=${sourceLabel}` : "";
    const factory = new Function(
        "context",
        "state",
        "module",
        "exports",
        `${contextPrelude}\n${source}${sourceUrl}\nconst resolved = (module && module.exports) || exports || {};\nreturn {\n  init: typeof resolved.init === "function" ? resolved.init : typeof init === "function" ? init : null,\n  tick: typeof resolved.tick === "function" ? resolved.tick : typeof tick === "function" ? tick : null,\n};`,
    );
    const module = { exports: {} };
    const exports = module.exports;
    return factory(context, state, module, exports);
}

self.addEventListener("message", (event) => {
    const { type, payload } = event.data || {};
    if (type === "init") {
        channelName = payload.channelName || "";
        updateScriptContexts();
        return;
    }

    if (type === "resize") {
        scripts.forEach((script) => {
            if (!script.canvas) {
                return;
            }
            script.canvas.width = payload.width || script.canvas.width;
            script.canvas.height = payload.height || script.canvas.height;
        });
        updateScriptContexts();
        return;
    }

    if (type === "channel") {
        channelName = payload.channelName || channelName;
        updateScriptContexts();
        return;
    }

    if (type === "addScript") {
        if (!payload?.id || !payload?.source || !payload?.canvas) {
            return;
        }
        const allowedDomains = sanitizeAllowedDomains(payload.allowedDomains);
        const canvas = payload.canvas;
        canvas.width = payload.width || canvas.width;
        canvas.height = payload.height || canvas.height;
        const ctx = canvas.getContext("2d");
        const state = {};
        const context = {
            canvas,
            ctx,
            channelName,
            width: canvas.width ?? 0,
            height: canvas.height ?? 0,
            now: 0,
            deltaMs: 0,
            elapsedMs: 0,
            assets: Array.isArray(payload.attachments) ? payload.attachments : [],
            chatMessages,
            emoteCatalog,
            allowedDomains,
            playAudio: (attachment) => {
                const attachmentId = typeof attachment === "string" ? attachment : attachment?.id;
                if (!attachmentId) {
                    return;
                }
                self.postMessage({
                    type: "scriptAudio",
                    payload: {
                        scriptId: payload.id,
                        attachmentId,
                    },
                });
            },
        };
        const script = {
            id: payload.id,
            allowedDomains,
            canvas,
            ctx,
            context,
            state,
            init: null,
            tick: null,
        };
        context.fetch = createScriptFetch(script);
        let handlers = {};
        try {
            activeScriptId = payload.id;
            handlers = createScriptHandlers(payload.source, context, state, `user-script-${payload.id}.js`);
        } catch (error) {
            console.error(`Script ${payload.id} failed to initialize`, error);
            reportScriptError(payload.id, "initialize", error);
            return;
        } finally {
            activeScriptId = null;
        }
        script.init = handlers.init;
        script.tick = handlers.tick;
        scripts.set(payload.id, script);
        refreshAllowedFetchUrls();
        if (script.init) {
            try {
                activeScriptId = script.id;
                script.init(script.context, script.state);
            } catch (error) {
                console.error(`Script ${payload.id} init failed`, error);
                reportScriptError(payload.id, "init", error);
            } finally {
                activeScriptId = null;
            }
        }
        ensureTickLoop();
        return;
    }

    if (type === "removeScript") {
        if (!payload?.id) {
            return;
        }
        scripts.delete(payload.id);
        refreshAllowedFetchUrls();
        stopTickLoopIfIdle();
    }

    if (type === "updateAttachments") {
        if (!payload?.id) {
            return;
        }
        const script = scripts.get(payload.id);
        if (!script) {
            return;
        }
        script.context.assets = Array.isArray(payload.attachments) ? payload.attachments : [];
        script.allowedDomains = sanitizeAllowedDomains(payload.allowedDomains);
        script.context.allowedDomains = script.allowedDomains;
        refreshAllowedFetchUrls();
    }

    if (type === "chatMessages") {
        chatMessages = Array.isArray(payload?.messages) ? payload.messages : [];
        refreshAllowedFetchUrls();
        updateScriptContexts();
    }

    if (type === "emoteCatalog") {
        emoteCatalog = Array.isArray(payload?.emotes) ? payload.emotes : [];
        refreshAllowedFetchUrls();
        updateScriptContexts();
    }
});
