/**
 * Copyright notices panel for the broadcaster dashboard.
 *
 * On load: fetches any pending (NOTIFIED) copyright notices and renders them.
 * Dismiss button: acknowledges the notice via API (→ RESOLVED) and removes it.
 * WebSocket: subscribes to the channel topic and refreshes on COPYRIGHT_WARNING.
 */
(function () {
    const panel    = document.getElementById("copyright-notices-panel");
    const list     = document.getElementById("copyright-notices-list");

    if (!panel || !list || typeof broadcaster === "undefined") return;

    // ── Helpers ───────────────────────────────────────────────────────────────
    function csrfHeaders() {
        const token  = document.querySelector("meta[name='_csrf']")?.content ?? "";
        const header = document.querySelector("meta[name='_csrf_header']")?.content ?? "X-XSRF-TOKEN";
        return { [header]: token };
    }

    function escHtml(str) {
        if (!str) return "";
        return String(str)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;")
            .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    function formatDate(iso) {
        if (!iso) return "";
        try { return new Date(iso).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" }); }
        catch { return iso; }
    }

    // ── Render ────────────────────────────────────────────────────────────────
    function renderNotices(notices) {
        if (!notices || notices.length === 0) {
            panel.classList.add("hidden");
            return;
        }
        panel.classList.remove("hidden");
        list.innerHTML = "";
        for (const notice of notices) {
            list.appendChild(buildNoticeItem(notice));
        }
    }

    function buildNoticeItem(notice) {
        const li = document.createElement("li");
        li.className = "copyright-notice-item";
        li.dataset.reportId = notice.id;
        li.innerHTML = `
            <div class="copyright-notice-body">
                <div class="copyright-notice-meta">
                    <span class="eyebrow">Asset ID</span>
                    <code class="copyright-notice-asset">${escHtml(notice.assetId)}</code>
                </div>
                <p class="copyright-notice-message">
                    A copyright infringement claim has been filed against this asset.
                    ${notice.resolutionNotes ? `<span class="muted">Note: ${escHtml(notice.resolutionNotes)}</span>` : ""}
                </p>
                <p class="copyright-notice-date muted">Received ${escHtml(formatDate(notice.updatedAt))}</p>
            </div>
            <div class="copyright-notice-actions">
                <a class="button ghost" href="/view/${encodeURIComponent(broadcaster)}/admin">
                    Review assets
                </a>
                <button class="button secondary" type="button" data-dismiss="${escHtml(notice.id)}">
                    Dismiss
                </button>
            </div>
        `;
        li.querySelector("[data-dismiss]").addEventListener("click", () => dismissNotice(notice.id, li));
        return li;
    }

    // ── API calls ─────────────────────────────────────────────────────────────
    async function loadNotices() {
        try {
            const resp = await fetch(`/api/channels/${encodeURIComponent(broadcaster)}/copyright-notices`);
            if (!resp.ok) return;
            renderNotices(await resp.json());
        } catch (_) { /* non-critical — silently skip */ }
    }

    async function dismissNotice(reportId, li) {
        const btn = li.querySelector("[data-dismiss]");
        if (btn) btn.disabled = true;
        try {
            const resp = await fetch(
                `/api/channels/${encodeURIComponent(broadcaster)}/copyright-notices/${encodeURIComponent(reportId)}/dismiss`,
                { method: "POST", headers: csrfHeaders() }
            );
            if (!resp.ok) {
                if (btn) btn.disabled = false;
                return;
            }
            li.classList.add("copyright-notice-dismissed");
            li.addEventListener("transitionend", () => {
                li.remove();
                if (list.children.length === 0) panel.classList.add("hidden");
            }, { once: true });
            // Fallback in case transition doesn't fire
            setTimeout(() => {
                if (li.parentNode) {
                    li.remove();
                    if (list.children.length === 0) panel.classList.add("hidden");
                }
            }, 400);
        } catch (_) {
            if (btn) btn.disabled = false;
        }
    }

    // ── WebSocket — refresh when a new COPYRIGHT_WARNING arrives ──────────────
    function connectWebSocket() {
        if (typeof SockJS === "undefined" || typeof Stomp === "undefined") return;
        try {
            const socket = new SockJS("/ws");
            const stomp  = Stomp.over(socket);
            stomp.debug   = () => {};
            stomp.connect({}, () => {
                stomp.subscribe(`/topic/channel/${broadcaster}`, (frame) => {
                    try {
                        const msg = JSON.parse(frame.body);
                        if (msg.type === "COPYRIGHT_WARNING") {
                            loadNotices();
                        }
                    } catch (_) {}
                });
            });
        } catch (_) {}
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    loadNotices();
    connectWebSocket();
})();
