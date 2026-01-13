const scripts = new Map();
const allowedFetchUrls = new Set();
let canvas = null;
let ctx = null;
let channelName = "";
let tickIntervalId = null;
let lastTick = 0;
let startTime = 0;
const tickIntervalMs = 1000 / 60;
const errorKeys = new Set();
const allowedImportUrls = new Set();
const nativeImportScripts = typeof self.importScripts === "function" ? self.importScripts.bind(self) : null;
const sharedDependencyUrls = ["/js/vendor/three.min.js", "/js/vendor/GLTFLoader.js", "/js/vendor/OBJLoader.js"];

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

function disableNetworkApis() {
    const nativeFetch = typeof self.fetch === "function" ? self.fetch.bind(self) : null;
    const blockedApis = {
        fetch: (...args) => {
            if (!nativeFetch) {
                throw new Error("Network access is disabled in asset scripts.");
            }
            const request = new Request(...args);
            const url = normalizeUrl(request.url);
            if (!allowedFetchUrls.has(url)) {
                throw new Error("Network access is disabled in asset scripts.");
            }
            return nativeFetch(request);
        },
        XMLHttpRequest: undefined,
        WebSocket: undefined,
        EventSource: undefined,
        importScripts: (...urls) => importAllowedScripts(...urls),
    };

    Object.entries(blockedApis).forEach(([key, value]) => {
        if (!(key in self)) {
            return;
        }
        try {
            Object.defineProperty(self, key, {
                value,
                writable: false,
                configurable: false,
            });
        } catch (error) {
            try {
                self[key] = value;
            } catch (_error) {
                // ignore if the API cannot be overridden in this environment
            }
        }
    });
}

disableNetworkApis();

function loadSharedDependencies() {
    if (!nativeImportScripts || sharedDependencyUrls.length === 0) {
        return;
    }
    importAllowedScripts(...sharedDependencyUrls);
}

loadSharedDependencies();

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
        script.context.canvas = canvas;
        script.context.ctx = ctx;
        script.context.channelName = channelName;
        script.context.width = canvas?.width ?? 0;
        script.context.height = canvas?.height ?? 0;
    });
}

function ensureTickLoop() {
    if (tickIntervalId) {
        return;
    }
    startTime = performance.now();
    lastTick = startTime;
    tickIntervalId = setInterval(() => {
        if (!ctx || scripts.size === 0) {
            return;
        }
        const now = performance.now();
        const deltaMs = now - lastTick;
        const elapsedMs = now - startTime;
        lastTick = now;

        scripts.forEach((script) => {
            if (!script.tick) {
                return;
            }
            script.context.now = now;
            script.context.deltaMs = deltaMs;
            script.context.elapsedMs = elapsedMs;
            try {
                script.tick(script.context, script.state);
            } catch (error) {
                console.error(`Script ${script.id} tick failed`, error);
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
        "const { canvas, ctx, channelName, width, height, now, deltaMs, elapsedMs, assets } = context;";
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
        canvas = payload.canvas;
        channelName = payload.channelName || "";
        if (canvas) {
            canvas.width = payload.width || canvas.width;
            canvas.height = payload.height || canvas.height;
            ctx = canvas.getContext("2d");
        }
        updateScriptContexts();
        return;
    }

    if (type === "resize") {
        if (canvas) {
            canvas.width = payload.width || canvas.width;
            canvas.height = payload.height || canvas.height;
        }
        updateScriptContexts();
        return;
    }

    if (type === "channel") {
        channelName = payload.channelName || channelName;
        updateScriptContexts();
        return;
    }

    if (type === "addScript") {
        if (!payload?.id || !payload?.source) {
            return;
        }
        const state = {};
        const context = {
            canvas,
            ctx,
            channelName,
            width: canvas?.width ?? 0,
            height: canvas?.height ?? 0,
            now: 0,
            deltaMs: 0,
            elapsedMs: 0,
            assets: Array.isArray(payload.attachments) ? payload.attachments : [],
        };
        let handlers = {};
        try {
            handlers = createScriptHandlers(payload.source, context, state, `user-script-${payload.id}.js`);
        } catch (error) {
            console.error(`Script ${payload.id} failed to initialize`, error);
            reportScriptError(payload.id, "initialize", error);
            return;
        }
        const script = {
            id: payload.id,
            context,
            state,
            init: handlers.init,
            tick: handlers.tick,
        };
        scripts.set(payload.id, script);
        refreshAllowedFetchUrls();
        if (script.init) {
            try {
                script.init(script.context, script.state);
            } catch (error) {
                console.error(`Script ${payload.id} init failed`, error);
                reportScriptError(payload.id, "init", error);
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
        refreshAllowedFetchUrls();
    }
});
