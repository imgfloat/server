/**
 * Playlist management panel for the admin console.
 *
 * Depends on `broadcaster` being defined in the page scope (set by admin.html inline script).
 * Depends on `showToast` from toast.js.
 * Communicates with the server via REST; live updates arrive via the shared STOMP topic and are
 * forwarded here from admin.js via window.dispatchEvent("playlistEvent").
 */
(function () {
    "use strict";

    const apiBase = () => `/api/channels/${encodeURIComponent(broadcaster)}/playlists`;

    // ── State ─────────────────────────────────────────────────────────────

    let playlists = [];           // PlaylistView[]
    let expandedPlaylistId = null;
    let activePlaylistId = null;  // the channel's persisted active playlist
    let playbackState = {         // mirrors the renderer's playlistState
        playing: false,
        paused: false,
        trackId: null,
    };
    let audioAssets = [];         // [{id, name}] — populated from the DOM asset list

    // ── DOM refs ──────────────────────────────────────────────────────────

    const el = {
        panel:            () => document.getElementById("playlist-panel"),
        toggle:           () => document.getElementById("playlist-panel-toggle"),
        body:             () => document.getElementById("playlist-panel-body"),
        chevron:          () => document.querySelector(".playlist-panel-chevron"),
        newNameInput:     () => document.getElementById("new-playlist-name"),
        createBtn:        () => document.getElementById("create-playlist-btn"),
        list:             () => document.getElementById("playlist-list"),
        detail:           () => document.getElementById("playlist-detail"),
        detailName:       () => document.getElementById("playlist-detail-name"),
        renameBtn:        () => document.getElementById("playlist-rename-btn"),
        deleteBtn:        () => document.getElementById("playlist-delete-btn"),
        renameForm:       () => document.getElementById("playlist-rename-form"),
        renameInput:      () => document.getElementById("playlist-rename-input"),
        renameSave:       () => document.getElementById("playlist-rename-save"),
        renameCancel:     () => document.getElementById("playlist-rename-cancel"),
        controls:         () => document.getElementById("playlist-controls"),
        nowPlayingLabel:  () => document.getElementById("playlist-now-playing-label"),
        playPauseBtn:     () => document.getElementById("playlist-play-pause-btn"),
        prevBtn:          () => document.getElementById("playlist-prev-btn"),
        nextBtn:          () => document.getElementById("playlist-next-btn"),
        trackSelect:      () => document.getElementById("playlist-track-select"),
        addTrackBtn:      () => document.getElementById("playlist-add-track-btn"),
        trackList:        () => document.getElementById("playlist-track-list"),
        nowPlayingPill:   () => document.getElementById("admin-now-playing-pill"),
        nowPlayingText:   () => document.getElementById("admin-now-playing-text"),
    };

    // ── API helpers ───────────────────────────────────────────────────────

    async function apiFetch(path, options = {}) {
        const csrfToken = document.querySelector("meta[name='_csrf']")?.content;
        const csrfHeader = document.querySelector("meta[name='_csrf_header']")?.content;
        const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
        if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
        const response = await fetch(`${apiBase()}${path}`, { ...options, headers });
        if (!response.ok) throw new Error(`Request failed: ${response.status}`);
        const ct = response.headers.get("content-type") || "";
        if (response.status === 204 || !ct.includes("application/json")) return null;
        return response.json();
    }

    // ── Initialisation ────────────────────────────────────────────────────

    async function init() {
        bindToggle();
        bindCreate();
        bindDetailButtons();
        bindPlaybackButtons();
        bindAddTrack();
        await loadPlaylists();
        await loadActivePlaylist();
        refreshAudioAssetOptions();
        // Listen for live updates forwarded from admin.js
        window.addEventListener("playlistEvent", (e) => handlePlaylistEvent(e.detail));
        // Also watch asset list mutations to keep the audio asset picker fresh
        const assetListEl = document.getElementById("asset-list");
        if (assetListEl) {
            new MutationObserver(refreshAudioAssetOptions).observe(assetListEl, { childList: true, subtree: true });
        }
    }

    function refreshAudioAssetOptions() {
        // Collect audio asset IDs and names from the admin asset list items (data attributes set by admin.js)
        const items = document.querySelectorAll("#asset-list .asset-item[data-asset-type='AUDIO']");
        audioAssets = Array.from(items).map(item => ({
            id: item.dataset.assetId,
            name: item.dataset.assetName || item.querySelector(".asset-name")?.textContent?.trim() || item.dataset.assetId,
        })).filter(a => a.id);
        renderTrackSelectOptions();
    }

    function renderTrackSelectOptions() {
        const sel = el.trackSelect();
        if (!sel) return;
        const current = sel.value;
        sel.innerHTML = '<option value="">Add audio asset…</option>';
        audioAssets.forEach(a => {
            const opt = document.createElement("option");
            opt.value = a.id;
            opt.textContent = a.name;
            sel.appendChild(opt);
        });
        if (current) sel.value = current;
    }

    // ── Load ──────────────────────────────────────────────────────────────

    async function loadPlaylists() {
        try {
            playlists = await apiFetch("") || [];
            renderPlaylistList();
        } catch {
            showToast("Unable to load playlists.", "error");
        }
    }

    async function loadActivePlaylist() {
        try {
            const active = await fetch(`${apiBase()}/active`).then(r => r.ok ? r.json() : null).catch(() => null);
            activePlaylistId = active?.id ?? null;
            renderPlaylistList();
            if (expandedPlaylistId) renderDetail();
        } catch { /* silently ignore */ }
    }

    // ── Panel toggle ──────────────────────────────────────────────────────

    function bindToggle() {
        const toggle = el.toggle();
        if (!toggle) return;
        toggle.addEventListener("click", () => {
            const body = el.body();
            const open = !body.classList.contains("hidden");
            body.classList.toggle("hidden", open);
            toggle.setAttribute("aria-expanded", String(!open));
            el.chevron()?.classList.toggle("rotated", !open);
        });
        toggle.addEventListener("keydown", e => {
            if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggle.click(); }
        });
    }

    // ── Create playlist ───────────────────────────────────────────────────

    function bindCreate() {
        el.createBtn()?.addEventListener("click", createPlaylist);
        el.newNameInput()?.addEventListener("keydown", e => { if (e.key === "Enter") { e.preventDefault(); createPlaylist(); } });
    }

    async function createPlaylist() {
        const input = el.newNameInput();
        const name = input?.value?.trim();
        if (!name) { showToast("Enter a playlist name.", "info"); return; }
        try {
            const view = await apiFetch("", { method: "POST", body: JSON.stringify({ name }) });
            input.value = "";
            // Don't push locally — the PLAYLIST_CREATED STOMP event will add it.
            // Just pre-set the expanded id so it opens as soon as the event renders it.
            if (view?.id) expandedPlaylistId = view.id;
        } catch {
            showToast("Could not create playlist.", "error");
        }
    }

    // ── Playlist list ─────────────────────────────────────────────────────

    function renderPlaylistList() {
        const list = el.list();
        if (!list) return;
        list.innerHTML = "";
        if (playlists.length === 0) {
            const empty = document.createElement("li");
            empty.className = "playlist-list-empty";
            empty.textContent = "No playlists yet.";
            list.appendChild(empty);
            return;
        }
        playlists.forEach(p => {
            const li = document.createElement("li");
            li.className = "playlist-list-item" + (p.id === expandedPlaylistId ? " expanded" : "") + (p.id === activePlaylistId ? " active" : "");
            li.dataset.id = p.id;
            li.addEventListener("click", () => toggleExpand(p.id));

            const nameSpan = document.createElement("span");
            nameSpan.className = "playlist-list-name";
            nameSpan.textContent = p.name;

            const actions = document.createElement("div");
            actions.className = "playlist-list-actions";

            const selectBtn = document.createElement("button");
            selectBtn.type = "button";
            selectBtn.className = "ghost small" + (p.id === activePlaylistId ? " active" : "");
            selectBtn.title = p.id === activePlaylistId ? "Active — click to deselect" : "Select for playback";
            selectBtn.innerHTML = p.id === activePlaylistId
                ? '<i class="fa-solid fa-check" aria-hidden="true"></i>'
                : '<i class="fa-solid fa-circle-play" aria-hidden="true"></i>';
            selectBtn.addEventListener("click", (e) => { e.stopPropagation(); toggleActivePlaylist(p.id); });

            actions.appendChild(selectBtn);
            li.appendChild(nameSpan);
            li.appendChild(actions);
            list.appendChild(li);
        });
    }

    async function toggleActivePlaylist(playlistId) {
        const newId = playlistId === activePlaylistId ? null : playlistId;
        try {
            await apiFetch("/active", { method: "PUT", body: JSON.stringify({ playlistId: newId }) });
            activePlaylistId = newId;
            renderPlaylistList();
            renderDetail();
        } catch {
            showToast("Could not update active playlist.", "error");
        }
    }

    function toggleExpand(playlistId) {
        expandedPlaylistId = expandedPlaylistId === playlistId ? null : playlistId;
        renderPlaylistList();
        renderDetail();
    }

    function expandPlaylist(playlistId) {
        expandedPlaylistId = playlistId;
        renderPlaylistList();
        renderDetail();
    }

    // ── Detail panel ──────────────────────────────────────────────────────

    function bindDetailButtons() {
        el.renameBtn()?.addEventListener("click", () => {
            const p = getExpanded();
            if (!p) return;
            el.renameInput().value = p.name;
            el.renameForm()?.classList.remove("hidden");
        });
        el.renameSave()?.addEventListener("click", saveRename);
        el.renameInput()?.addEventListener("keydown", e => { if (e.key === "Enter") saveRename(); });
        el.renameCancel()?.addEventListener("click", () => el.renameForm()?.classList.add("hidden"));
        el.deleteBtn()?.addEventListener("click", deletePlaylist);
    }

    function renderDetail() {
        const detail = el.detail();
        if (!detail) return;
        const p = getExpanded();
        if (!p) { detail.classList.add("hidden"); return; }
        detail.classList.remove("hidden");
        el.detailName().textContent = p.name;
        el.renameForm()?.classList.add("hidden");
        renderControls(p);
        renderTrackList(p);
    }

    function renderControls(p) {
        const controls = el.controls();
        if (!controls) return;
        const isActive = p.id === activePlaylistId;
        controls.classList.toggle("hidden", !isActive);
        if (!isActive) return;

        const playPauseIcon = el.playPauseBtn()?.querySelector("i");
        if (playPauseIcon) {
            playPauseIcon.className = playbackState.playing && !playbackState.paused
                ? "fa-solid fa-pause"
                : "fa-solid fa-play";
        }

        const label = el.nowPlayingLabel();
        if (label) {
            if (playbackState.playing && playbackState.trackId) {
                const track = p.tracks.find(t => t.id === playbackState.trackId);
                label.textContent = track ? `♪ ${track.assetName}` : "Playing…";
            } else {
                label.textContent = "";
            }
        }
    }

    function getExpanded() {
        return playlists.find(p => p.id === expandedPlaylistId) ?? null;
    }

    async function saveRename() {
        const input = el.renameInput();
        const name = input?.value?.trim();
        if (!name) return;
        const p = getExpanded();
        if (!p) return;
        try {
            const updated = await apiFetch(`/${p.id}`, { method: "PUT", body: JSON.stringify({ name }) });
            const idx = playlists.findIndex(x => x.id === p.id);
            if (idx >= 0) playlists[idx] = updated;
            el.renameForm()?.classList.add("hidden");
            renderPlaylistList();
            renderDetail();
        } catch {
            showToast("Could not rename playlist.", "error");
        }
    }

    async function deletePlaylist() {
        const p = getExpanded();
        if (!p) return;
        if (!confirm(`Delete playlist "${p.name}"?`)) return;
        try {
            await apiFetch(`/${p.id}`, { method: "DELETE" });
            playlists = playlists.filter(x => x.id !== p.id);
            if (expandedPlaylistId === p.id) expandedPlaylistId = null;
            if (activePlaylistId === p.id) activePlaylistId = null;
            renderPlaylistList();
            renderDetail();
        } catch {
            showToast("Could not delete playlist.", "error");
        }
    }

    // ── Track list ────────────────────────────────────────────────────────

    function renderTrackList(p) {
        const list = el.trackList();
        if (!list) return;
        list.innerHTML = "";
        if (!p.tracks?.length) {
            const empty = document.createElement("li");
            empty.className = "playlist-track-empty";
            empty.textContent = "No tracks yet.";
            list.appendChild(empty);
            return;
        }
        p.tracks.forEach(track => {
            const li = document.createElement("li");
            li.className = "playlist-track-item" + (track.id === playbackState.trackId ? " playing" : "");
            li.dataset.trackId = track.id;
            li.draggable = true;

            const handle = document.createElement("span");
            handle.className = "playlist-track-handle";
            handle.innerHTML = '<i class="fa-solid fa-grip-vertical" aria-hidden="true"></i>';

            const name = document.createElement("span");
            name.className = "playlist-track-name";
            name.textContent = track.assetName;

            const removeBtn = document.createElement("button");
            removeBtn.type = "button";
            removeBtn.className = "ghost small icon-button danger-icon";
            removeBtn.title = "Remove from playlist";
            removeBtn.innerHTML = '<i class="fa-solid fa-xmark" aria-hidden="true"></i>';
            removeBtn.addEventListener("click", () => removeTrack(p.id, track.id));

            li.appendChild(handle);
            li.appendChild(name);
            li.appendChild(removeBtn);
            list.appendChild(li);
        });
        bindDragReorder(list, p.id);
    }

    function bindDragReorder(list, playlistId) {
        let dragging = null;

        list.querySelectorAll(".playlist-track-item").forEach(item => {
            item.addEventListener("dragstart", (e) => {
                dragging = item;
                item.classList.add("dragging");
                e.dataTransfer.effectAllowed = "move";
            });
            item.addEventListener("dragend", () => {
                item.classList.remove("dragging");
                dragging = null;
                list.querySelectorAll(".playlist-track-item").forEach(el => el.classList.remove("drag-over"));
            });
            item.addEventListener("dragover", (e) => {
                e.preventDefault();
                if (dragging && dragging !== item) {
                    list.querySelectorAll(".playlist-track-item").forEach(el => el.classList.remove("drag-over"));
                    item.classList.add("drag-over");
                    // Reorder in DOM
                    const items = [...list.querySelectorAll(".playlist-track-item")];
                    const fromIdx = items.indexOf(dragging);
                    const toIdx = items.indexOf(item);
                    if (fromIdx < toIdx) {
                        list.insertBefore(dragging, item.nextSibling);
                    } else {
                        list.insertBefore(dragging, item);
                    }
                }
            });
            item.addEventListener("drop", async (e) => {
                e.preventDefault();
                item.classList.remove("drag-over");
                const newOrder = [...list.querySelectorAll(".playlist-track-item")].map(li => li.dataset.trackId);
                try {
                    const updated = await apiFetch(`/${playlistId}/tracks/order`, {
                        method: "PUT",
                        body: JSON.stringify({ trackIds: newOrder }),
                    });
                    const idx = playlists.findIndex(p => p.id === playlistId);
                    if (idx >= 0) playlists[idx] = updated;
                    // No full re-render needed; DOM is already in the right order
                } catch {
                    showToast("Could not reorder tracks.", "error");
                    await loadPlaylists();
                    renderDetail();
                }
            });
        });
    }

    function bindAddTrack() {
        el.addTrackBtn()?.addEventListener("click", addTrack);
    }

    async function addTrack() {
        const p = getExpanded();
        if (!p) return;
        const audioAssetId = el.trackSelect()?.value;
        if (!audioAssetId) { showToast("Select an audio asset to add.", "info"); return; }
        try {
            const updated = await apiFetch(`/${p.id}/tracks`, { method: "POST", body: JSON.stringify({ audioAssetId }) });
            const idx = playlists.findIndex(x => x.id === p.id);
            if (idx >= 0) playlists[idx] = updated;
            el.trackSelect().value = "";
            renderPlaylistList();
            renderDetail();
        } catch {
            showToast("Could not add track.", "error");
        }
    }

    async function removeTrack(playlistId, trackId) {
        try {
            const updated = await apiFetch(`/${playlistId}/tracks/${trackId}`, { method: "DELETE" });
            const idx = playlists.findIndex(p => p.id === playlistId);
            if (idx >= 0) playlists[idx] = updated;
            renderPlaylistList();
            renderDetail();
        } catch {
            showToast("Could not remove track.", "error");
        }
    }

    // ── Playback controls ─────────────────────────────────────────────────

    function bindPlaybackButtons() {
        el.playPauseBtn()?.addEventListener("click", togglePlayPause);
        el.prevBtn()?.addEventListener("click", commandPrev);
        el.nextBtn()?.addEventListener("click", commandNext);
    }

    async function togglePlayPause() {
        const p = getExpanded();
        if (!p || p.id !== activePlaylistId) return;
        if (playbackState.playing && !playbackState.paused) {
            await commandPause();
        } else {
            await commandPlay(playbackState.trackId || p.tracks[0]?.id || null);
        }
    }

    async function commandPlay(trackId) {
        const p = getExpanded();
        if (!p) return;
        try {
            await apiFetch(`/${p.id}/play`, { method: "POST", body: JSON.stringify({ trackId }) });
        } catch {
            showToast("Could not start playback.", "error");
        }
    }

    async function commandPause() {
        const p = getExpanded();
        if (!p) return;
        try {
            await apiFetch(`/${p.id}/pause`, { method: "POST" });
        } catch {
            showToast("Could not pause.", "error");
        }
    }

    async function commandNext() {
        const p = getExpanded();
        if (!p || !playbackState.trackId) return;
        try {
            await apiFetch(`/${p.id}/next`, { method: "POST", body: JSON.stringify({ currentTrackId: playbackState.trackId }) });
        } catch {
            showToast("Could not skip to next.", "error");
        }
    }

    async function commandPrev() {
        const p = getExpanded();
        if (!p || !playbackState.trackId) return;
        try {
            await apiFetch(`/${p.id}/prev`, { method: "POST", body: JSON.stringify({ currentTrackId: playbackState.trackId }) });
        } catch {
            showToast("Could not go back.", "error");
        }
    }

    // ── Live event handler ────────────────────────────────────────────────

    function handlePlaylistEvent(event) {
        if (!event?.type) return;
        const { type, playlistId, trackId, payload } = event;

        switch (type) {
            case "PLAYLIST_CREATED":
                if (payload && !playlists.find(p => p.id === payload.id)) {
                    playlists.push(payload);
                    renderPlaylistList();
                }
                break;
            case "PLAYLIST_UPDATED":
                if (payload) {
                    const idx = playlists.findIndex(p => p.id === payload.id);
                    if (idx >= 0) playlists[idx] = payload;
                    else playlists.push(payload);
                    renderPlaylistList();
                    if (expandedPlaylistId === payload.id) renderDetail();
                }
                break;
            case "PLAYLIST_DELETED":
                playlists = playlists.filter(p => p.id !== playlistId);
                if (expandedPlaylistId === playlistId) expandedPlaylistId = null;
                if (activePlaylistId === playlistId) activePlaylistId = null;
                renderPlaylistList();
                renderDetail();
                break;
            case "PLAYLIST_SELECTED":
                activePlaylistId = payload?.id ?? null;
                playbackState = { playing: false, paused: false, trackId: null };
                renderPlaylistList();
                renderDetail();
                break;
            case "PLAYLIST_PLAY":
                playbackState = { playing: true, paused: false, trackId: trackId ?? null };
                updateNowPlayingPill();
                renderDetail();
                break;
            case "PLAYLIST_PAUSE":
                playbackState = { ...playbackState, paused: true };
                updateNowPlayingPill();
                renderDetail();
                break;
            case "PLAYLIST_NEXT":
            case "PLAYLIST_PREV":
                if (trackId) {
                    playbackState = { playing: true, paused: false, trackId };
                }
                updateNowPlayingPill();
                renderDetail();
                break;
            case "PLAYLIST_ENDED":
                playbackState = { playing: false, paused: false, trackId: null };
                updateNowPlayingPill();
                renderDetail();
                break;
        }
    }

    function updateNowPlayingPill() {
        const pill = el.nowPlayingPill();
        const textEl = el.nowPlayingText();
        if (!pill || !textEl) return;

        if (playbackState.playing && !playbackState.paused && playbackState.trackId) {
            const p = playlists.find(pl => pl.id === activePlaylistId);
            const track = p?.tracks?.find(t => t.id === playbackState.trackId);
            if (track) {
                textEl.textContent = track.assetName;
                pill.classList.remove("hidden");
                return;
            }
        }
        pill.classList.add("hidden");
    }

    // ── Bootstrap ─────────────────────────────────────────────────────────

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
