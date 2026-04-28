/**
 * Sysadmin copyright reports management.
 * Loaded on the /settings page only.
 */

(function () {
    const loadBtn = document.getElementById("copyright-load-btn");
    if (!loadBtn) return;

    const statusFilter = document.getElementById("copyright-status-filter");
    const broadcasterFilter = document.getElementById("copyright-broadcaster-filter");
    const tbody = document.getElementById("copyright-reports-body");
    const table = document.getElementById("copyright-reports-table");
    const placeholder = document.getElementById("copyright-reports-placeholder");
    const pagination = document.getElementById("copyright-pagination");
    const prevPageBtn = document.getElementById("copyright-prev-page");
    const nextPageBtn = document.getElementById("copyright-next-page");
    const pageIndicator = document.getElementById("copyright-page-indicator");

    const reviewModal = document.getElementById("copyright-review-modal");
    const reviewDetail = document.getElementById("copyright-review-detail");
    const reviewForm = document.getElementById("copyright-review-form");
    const reviewNotes = document.getElementById("copyright-review-notes");
    const reviewError = document.getElementById("copyright-review-error");
    const reviewCloseBtn = document.getElementById("copyright-review-close");
    const reviewCancelBtn = document.getElementById("copyright-review-cancel");

    let currentPage = 0;
    let totalPages = 0;
    let pendingReportId = null;

    function csrfHeaders() {
        const token = document.querySelector("meta[name='_csrf']")?.content ?? "";
        const header = document.querySelector("meta[name='_csrf_header']")?.content ?? "X-XSRF-TOKEN";
        return { [header]: token };
    }

    function formatDate(isoStr) {
        if (!isoStr) return "—";
        return new Date(isoStr).toLocaleString();
    }

    function statusBadge(status) {
        const classes = { PENDING: "badge soft", DISMISSED: "badge outline", RESOLVED: "badge" };
        return `<span class="${classes[status] ?? "badge"}">${status}</span>`;
    }

    async function loadReports(page = 0) {
        const status = statusFilter.value;
        const broadcaster = broadcasterFilter.value.trim();
        const params = new URLSearchParams({ page, size: 20 });
        if (status) params.set("status", status);
        if (broadcaster) params.set("broadcaster", broadcaster);

        const resp = await fetch(`/api/copyright-reports?${params}`, {
            headers: { ...csrfHeaders() },
        });
        if (!resp.ok) {
            placeholder.textContent = "Failed to load reports.";
            placeholder.classList.remove("hidden");
            table.classList.add("hidden");
            pagination.classList.add("hidden");
            return;
        }
        const data = await resp.json();
        renderTable(data);
        currentPage = data.page;
        totalPages = data.totalPages;
        updatePagination(data);
    }

    function renderTable(data) {
        tbody.innerHTML = "";
        if (!data.content || data.content.length === 0) {
            placeholder.textContent = "No reports found.";
            placeholder.classList.remove("hidden");
            table.classList.add("hidden");
            pagination.classList.add("hidden");
            return;
        }
        placeholder.classList.add("hidden");
        table.classList.remove("hidden");

        for (const r of data.content) {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>${escHtml(r.broadcaster)}</td>
                <td><code title="${escHtml(r.assetId)}">${escHtml(r.assetId.slice(0, 8))}…</code></td>
                <td>${escHtml(r.claimantName)} &lt;${escHtml(r.claimantEmail)}&gt;</td>
                <td>${statusBadge(r.status)}</td>
                <td>${formatDate(r.createdAt)}</td>
                <td>
                    <button class="button ghost review-btn" data-id="${escHtml(r.id)}" type="button"
                        ${r.status !== "PENDING" ? "disabled title='Already actioned'" : ""}>
                        Review
                    </button>
                </td>
            `;
            tbody.appendChild(tr);
        }

        tbody.querySelectorAll(".review-btn").forEach((btn) => {
            btn.addEventListener("click", () => openReviewModal(btn.dataset.id, data.content.find((r) => r.id === btn.dataset.id)));
        });
    }

    function updatePagination(data) {
        if (data.totalPages <= 1) {
            pagination.classList.add("hidden");
            return;
        }
        pagination.classList.remove("hidden");
        pageIndicator.textContent = `Page ${data.page + 1} of ${data.totalPages}`;
        prevPageBtn.disabled = data.page === 0;
        nextPageBtn.disabled = data.page >= data.totalPages - 1;
    }

    function openReviewModal(reportId, report) {
        pendingReportId = reportId;
        reviewForm.reset();
        hideReviewError();
        reviewDetail.innerHTML = `
            <dl class="detail-list">
                <div><dt>Broadcaster</dt><dd>${escHtml(report.broadcaster)}</dd></div>
                <div><dt>Asset ID</dt><dd><code>${escHtml(report.assetId)}</code></dd></div>
                <div><dt>Claimant</dt><dd>${escHtml(report.claimantName)} &lt;${escHtml(report.claimantEmail)}&gt;</dd></div>
                <div><dt>Original work</dt><dd>${escHtml(report.originalWorkDescription)}</dd></div>
                <div><dt>How it infringes</dt><dd>${escHtml(report.infringingDescription)}</dd></div>
                <div><dt>Submitted</dt><dd>${formatDate(report.createdAt)}</dd></div>
            </dl>
        `;
        reviewModal.classList.remove("hidden");
    }

    function closeReviewModal() {
        reviewModal.classList.add("hidden");
        pendingReportId = null;
    }

    function showReviewError(msg) {
        reviewError.textContent = msg;
        reviewError.classList.remove("hidden");
    }

    function hideReviewError() {
        reviewError.textContent = "";
        reviewError.classList.add("hidden");
    }

    reviewForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        hideReviewError();
        const action = reviewForm.querySelector("input[name='review-action']:checked")?.value;
        if (!action) {
            showReviewError("Please select an action.");
            return;
        }
        const submitBtn = reviewForm.querySelector("button[type=submit]");
        submitBtn.disabled = true;
        try {
            const resp = await fetch(`/api/copyright-reports/${pendingReportId}/review`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    ...csrfHeaders(),
                },
                body: JSON.stringify({ action, resolutionNotes: reviewNotes.value.trim() || null }),
            });
            if (!resp.ok) {
                let msg = "Failed to submit review.";
                try { const d = await resp.json(); if (d?.message) msg = d.message; } catch (_) {}
                showReviewError(msg);
                return;
            }
            closeReviewModal();
            if (typeof window.showToast === "function") {
                window.showToast("Report actioned successfully.", "success");
            }
            loadReports(currentPage);
        } catch (err) {
            showReviewError(err.message);
        } finally {
            submitBtn.disabled = false;
        }
    });

    loadBtn.addEventListener("click", () => loadReports(0));
    prevPageBtn.addEventListener("click", () => loadReports(currentPage - 1));
    nextPageBtn.addEventListener("click", () => loadReports(currentPage + 1));
    reviewCloseBtn.addEventListener("click", closeReviewModal);
    reviewCancelBtn.addEventListener("click", closeReviewModal);
    reviewModal.addEventListener("click", (e) => { if (e.target === reviewModal) closeReviewModal(); });

    function escHtml(str) {
        if (!str) return "";
        return String(str)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }
})();
