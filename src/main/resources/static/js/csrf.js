(function () {
    const CSRF_COOKIE_NAME = "XSRF-TOKEN";
    const DEFAULT_HEADER_NAME = "X-XSRF-TOKEN";
    const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);
    const originalFetch = window.fetch;

    function getCookie(name) {
        return document.cookie
            .split(";")
            .map((c) => c.trim())
            .filter((c) => c.startsWith(name + "="))
            .map((c) => c.substring(name.length + 1))[0];
    }

    function isSameOrigin(url) {
        const parsed = new URL(url, window.location.href);
        return parsed.origin === window.location.origin;
    }

    function getMeta(name) {
        const el = document.querySelector(`meta[name=\"${name}\"]`);
        return el ? el.getAttribute("content") : null;
    }

    window.fetch = function patchedFetch(input, init = {}) {
        const request = new Request(input, init);
        const method = (request.method || "GET").toUpperCase();

        if (!SAFE_METHODS.has(method) && isSameOrigin(request.url)) {
            const token = getCookie(CSRF_COOKIE_NAME) || getMeta("_csrf");
            const headerName = getMeta("_csrf_header") || DEFAULT_HEADER_NAME;
            if (token) {
                const headers = new Headers(request.headers || {});
                headers.set(headerName, token);
                return originalFetch(new Request(request, { headers }));
            }
        }

        return originalFetch(request);
    };
})();
