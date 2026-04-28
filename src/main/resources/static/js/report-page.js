/**
 * Report copyright infringement page logic.
 * Three-step flow: search broadcaster → pick asset → submit DMCA form.
 */
(function () {
    // ── Elements ──────────────────────────────────────────────────────────────
    const stepBroadcaster = document.getElementById("step-broadcaster");
    const stepAsset       = document.getElementById("step-asset");
    const stepForm        = document.getElementById("step-form");
    const stepDone        = document.getElementById("step-done");

    const broadcasterInput = document.getElementById("broadcaster-search-input");
    const broadcasterBtn   = document.getElementById("broadcaster-search-btn");
    const broadcasterError = document.getElementById("broadcaster-error");

    const assetGrid  = document.getElementById("asset-grid");
    const assetError = document.getElementById("asset-error");

    const selectedAssetPreview = document.getElementById("selected-asset-preview");
    const dmcaForm             = document.getElementById("dmca-form");
    const formError            = document.getElementById("form-error");
    const submitBtn            = document.getElementById("submit-btn");
    const backToAssetBtn       = document.getElementById("back-to-asset-btn");

    let selectedAssetId   = null;
    let currentBroadcaster = null;

    // ── Helpers ───────────────────────────────────────────────────────────────
    function show(el)  { el.classList.remove("hidden"); }
    function hide(el)  { el.classList.add("hidden"); }

    function showError(el, msg) {
        el.textContent = msg;
        show(el);
    }

    function hideError(el) {
        el.textContent = "";
        hide(el);
    }

    function csrfHeaders() {
        const token  = document.querySelector("meta[name='_csrf']")?.content ?? "";
        const header = document.querySelector("meta[name='_csrf_header']")?.content ?? "X-XSRF-TOKEN";
        return { [header]: token };
    }

    function escHtml(str) {
        if (!str) return "";
        return String(str)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function assetTypeName(type) {
        const names = { IMAGE: "Image", VIDEO: "Video", AUDIO: "Audio", SCRIPT: "Script", MODEL: "3D model", OTHER: "File" };
        return names[type] ?? type ?? "Asset";
    }

    // ── Step 1 – broadcaster search ───────────────────────────────────────────
    async function searchBroadcaster() {
        const query = broadcasterInput.value.trim();
        if (!query) {
            showError(broadcasterError, "Please enter a broadcaster username.");
            return;
        }
        hideError(broadcasterError);
        broadcasterBtn.disabled = true;
        broadcasterBtn.textContent = "Searching…";
        try {
            const resp = await fetch(`/api/channels?q=${encodeURIComponent(query)}`);
            if (!resp.ok) throw new Error("Search failed");
            const channels = await resp.json();
            // Find an exact match first, then fall back to first result
            const exact = channels.find(c => c.toLowerCase() === query.toLowerCase());
            const broadcaster = exact ?? (channels.length > 0 ? channels[0] : null);
            if (!broadcaster) {
                showError(broadcasterError, `No broadcaster found matching "${escHtml(query)}". Check the spelling and try again.`);
                return;
            }
            await loadAssets(broadcaster);
        } catch (err) {
            showError(broadcasterError, "Could not search for broadcaster. Please try again.");
        } finally {
            broadcasterBtn.disabled = false;
            broadcasterBtn.textContent = "Search";
        }
    }

    broadcasterBtn.addEventListener("click", searchBroadcaster);
    broadcasterInput.addEventListener("keydown", (e) => {
        if (e.key === "Enter") { e.preventDefault(); searchBroadcaster(); }
    });

    // ── Step 2 – asset picker ─────────────────────────────────────────────────
    async function loadAssets(broadcaster) {
        hideError(assetError);
        assetGrid.innerHTML = '<p class="muted tiny">Loading assets…</p>';
        currentBroadcaster = broadcaster;

        try {
            const resp = await fetch(`/api/channels/${encodeURIComponent(broadcaster)}/assets/visible`);
            if (!resp.ok) throw new Error("Failed to load assets");
            const assets = await resp.json();

            if (!assets || assets.length === 0) {
                assetGrid.innerHTML = '<p class="muted tiny">This broadcaster has no public assets.</p>';
                show(stepAsset);
                return;
            }

            assetGrid.innerHTML = "";
            for (const asset of assets) {
                const card = buildAssetCard(asset, broadcaster);
                assetGrid.appendChild(card);
            }

            hide(stepBroadcaster);
            show(stepAsset);
            stepAsset.scrollIntoView({ behavior: "smooth", block: "start" });
        } catch (err) {
            showError(assetError, "Could not load assets for this broadcaster. Please try again.");
        }
    }

    function buildAssetCard(asset, broadcaster) {
        const card = document.createElement("button");
        card.type = "button";
        card.className = "report-asset-card";
        card.dataset.assetId = asset.id;

        const imgSrc = asset.previewUrl || (asset.assetType === "IMAGE" ? asset.url : null);

        let mediaHtml = "";
        if (imgSrc) {
            mediaHtml = `<img class="asset-card-thumb" src="${escHtml(imgSrc)}" alt="${escHtml(asset.name)}" loading="lazy" />`;
        } else if (asset.assetType === "VIDEO") {
            mediaHtml = `<div class="asset-card-thumb asset-card-icon"><i class="fa-solid fa-film"></i></div>`;
        } else if (asset.assetType === "AUDIO") {
            mediaHtml = `<div class="asset-card-thumb asset-card-icon"><i class="fa-solid fa-music"></i></div>`;
        } else if (asset.assetType === "SCRIPT") {
            mediaHtml = `<div class="asset-card-thumb asset-card-icon"><i class="fa-solid fa-code"></i></div>`;
        } else {
            mediaHtml = `<div class="asset-card-thumb asset-card-icon"><i class="fa-solid fa-file"></i></div>`;
        }

        card.innerHTML = `
            ${mediaHtml}
            <span class="asset-card-name">${escHtml(asset.name || assetTypeName(asset.assetType))}</span>
            <span class="asset-card-type">${escHtml(assetTypeName(asset.assetType))}</span>
        `;

        card.addEventListener("click", () => selectAsset(asset, broadcaster));
        return card;
    }

    function selectAsset(asset, broadcaster) {
        selectedAssetId = asset.id;

        const imgSrc = asset.previewUrl || (asset.assetType === "IMAGE" ? asset.url : null);
        const thumb = imgSrc
            ? `<img class="asset-card-thumb" src="${escHtml(imgSrc)}" alt="${escHtml(asset.name)}" />`
            : `<div class="asset-card-thumb asset-card-icon"><i class="fa-solid fa-file"></i></div>`;

        selectedAssetPreview.innerHTML = `
            <div class="selected-asset-summary">
                ${thumb}
                <div>
                    <p class="asset-summary-name">${escHtml(asset.name || assetTypeName(asset.assetType))}</p>
                    <p class="muted tiny">Broadcaster: <strong>${escHtml(broadcaster)}</strong> &mdash; Type: ${escHtml(assetTypeName(asset.assetType))}</p>
                </div>
            </div>
        `;

        hide(stepAsset);
        show(stepForm);
        stepForm.scrollIntoView({ behavior: "smooth", block: "start" });
    }

    backToAssetBtn.addEventListener("click", () => {
        selectedAssetId = null;
        hide(stepForm);
        show(stepAsset);
        stepAsset.scrollIntoView({ behavior: "smooth", block: "start" });
    });

    // ── Step 3 – DMCA form submit ─────────────────────────────────────────────
    dmcaForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        hideError(formError);

        const claimantName  = document.getElementById("claimant-name").value.trim();
        const claimantEmail = document.getElementById("claimant-email").value.trim();
        const originalWork  = document.getElementById("original-work").value.trim();
        const infringing    = document.getElementById("infringing-description").value.trim();
        const goodFaith     = document.getElementById("good-faith").checked;

        if (!claimantName || !claimantEmail || !originalWork || !infringing) {
            showError(formError, "All fields are required.");
            return;
        }
        if (!goodFaith) {
            showError(formError, "You must confirm the good faith declaration.");
            return;
        }
        if (!selectedAssetId) {
            showError(formError, "No asset selected. Please go back and select an asset.");
            return;
        }

        submitBtn.disabled = true;
        submitBtn.textContent = "Submitting…";

        try {
            const resp = await fetch(`/api/assets/${encodeURIComponent(selectedAssetId)}/copyright-reports`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    ...csrfHeaders(),
                },
                body: JSON.stringify({
                    claimantName,
                    claimantEmail,
                    originalWorkDescription: originalWork,
                    infringingDescription: infringing,
                    goodFaithDeclaration: true,
                }),
            });

            if (!resp.ok) {
                let msg = "Failed to submit your report. Please try again.";
                try {
                    const data = await resp.json();
                    if (data?.message) msg = data.message;
                } catch (_) {}
                showError(formError, msg);
                return;
            }

            hide(stepForm);
            show(stepDone);
            stepDone.scrollIntoView({ behavior: "smooth", block: "start" });
        } catch (err) {
            showError(formError, "A network error occurred. Please check your connection and try again.");
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = "Submit report";
        }
    });
})();
