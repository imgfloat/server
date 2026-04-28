/**
 * Copyright infringement report form logic.
 * Handles opening the modal, form validation, and submission.
 */

const modal = document.getElementById("copyright-report-modal");
const form = document.getElementById("copyright-report-form");
const errorEl = document.getElementById("copyright-report-error");
const closeBtn = document.getElementById("copyright-report-close");
const cancelBtn = document.getElementById("copyright-report-cancel");
const reportBtn = document.getElementById("selected-asset-report");

let pendingAssetId = null;

function openReportModal(assetId) {
    pendingAssetId = assetId;
    form.reset();
    hideError();
    modal.classList.remove("hidden");
}

function closeReportModal() {
    modal.classList.add("hidden");
    pendingAssetId = null;
}

function showError(message) {
    errorEl.textContent = message;
    errorEl.classList.remove("hidden");
}

function hideError() {
    errorEl.textContent = "";
    errorEl.classList.add("hidden");
}

async function submitReport(assetId, payload) {
    const csrfToken = document.querySelector("meta[name='_csrf']")?.content ?? "";
    const csrfHeader = document.querySelector("meta[name='_csrf_header']")?.content ?? "X-XSRF-TOKEN";
    const response = await fetch(`/api/assets/${encodeURIComponent(assetId)}/copyright-reports`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            [csrfHeader]: csrfToken,
        },
        body: JSON.stringify(payload),
    });
    if (!response.ok) {
        let msg = "Failed to submit report. Please try again.";
        try {
            const data = await response.json();
            if (data?.message) msg = data.message;
        } catch (_) {}
        throw new Error(msg);
    }
    return response.json();
}

form.addEventListener("submit", async (e) => {
    e.preventDefault();
    hideError();

    const claimantName = document.getElementById("copyright-claimant-name").value.trim();
    const claimantEmail = document.getElementById("copyright-claimant-email").value.trim();
    const originalWork = document.getElementById("copyright-original-work").value.trim();
    const infringing = document.getElementById("copyright-infringing").value.trim();
    const goodFaith = document.getElementById("copyright-good-faith").checked;

    if (!claimantName || !claimantEmail || !originalWork || !infringing) {
        showError("All fields are required.");
        return;
    }
    if (!goodFaith) {
        showError("You must confirm the good faith declaration.");
        return;
    }
    if (!pendingAssetId) {
        showError("No asset selected.");
        return;
    }

    const submitBtn = form.querySelector("button[type=submit]");
    submitBtn.disabled = true;
    try {
        await submitReport(pendingAssetId, {
            claimantName,
            claimantEmail,
            originalWorkDescription: originalWork,
            infringingDescription: infringing,
            goodFaithDeclaration: true,
        });
        closeReportModal();
        if (typeof window.showToast === "function") {
            window.showToast("Copyright report submitted successfully.", "success");
        }
    } catch (err) {
        showError(err.message);
    } finally {
        submitBtn.disabled = false;
    }
});

closeBtn.addEventListener("click", closeReportModal);
cancelBtn.addEventListener("click", closeReportModal);
modal.addEventListener("click", (e) => {
    if (e.target === modal) closeReportModal();
});

if (reportBtn) {
    reportBtn.addEventListener("click", () => {
        const assetIdEl = document.getElementById("selected-asset-id");
        const assetId = assetIdEl?.textContent?.trim();
        if (assetId) {
            openReportModal(assetId);
        }
    });
}

export { openReportModal, closeReportModal };
