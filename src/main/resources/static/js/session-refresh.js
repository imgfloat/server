(() => {
    const refreshDelayMs = 4 * 60 * 1000;
    let refreshInterval;

    const refresh = () => {
        fetch("/api/session/refresh", { cache: "no-store" }).catch(() => null);
    };

    const start = () => {
        if (refreshInterval) {
            return;
        }
        refresh();
        refreshInterval = globalThis.setInterval(refresh, refreshDelayMs);
    };

    const stop = () => {
        if (refreshInterval) {
            clearInterval(refreshInterval);
            refreshInterval = null;
        }
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start, { once: true });
    } else {
        start();
    }

    globalThis.addEventListener("beforeunload", stop);
    document.addEventListener("visibilitychange", () => {
        if (document.hidden) {
            stop();
        } else {
            start();
        }
    });
})();
