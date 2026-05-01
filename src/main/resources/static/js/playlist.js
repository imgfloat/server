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
        bindCreate();
        await loadPlaylists();
        await loadActivePlaylist();
        refreshAudioAssetOptions();
        window.addEventListener("playlistEvent", (e) => handlePlaylistEvent(e.detail));
        const assetListEl = document.getElementById("asset-list");
        if (assetListEl) {
            new MutationObserver(refreshAudioAssetOptions).observe(assetListEl, { childList: true, subtree: true });
        }
    }

    function refreshAudioAssetOptions() {
        const items = document.querySelectorAll("#asset-list .asset-item[data-asset-type='AUDIO']");
        audioAssets = Array.from(items).map(item => ({
            id: item.dataset.assetId,
            name: item.dataset.assetName || item.dataset.assetId,
        })).filter(a => a.id);
        // Update any open track-select dropdowns
        document.querySelectorAll(".playlist-track-select").forEach(sel => {
            const current = sel.value;
            sel.innerHTML = '<option value="">Add audio asset…</option>';
            audioAssets.forEach(a => {
                const opt = document.createElement("option");
                opt.value = a.id;
                opt.textContent = a.name;
                sel.appendChild(opt);
            });
            if (current) sel.value = current;
        });
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
            const state = await fetch(`${apiBase()}/active`).then(r => r.ok ? r.json() : null).catch(() => null);
            if (!state) return;
            activePlaylistId = state.id ?? null;
            if (state.isPlaying && state.currentTrackId) {
                playbackState = {
                    playing: true,
                    paused: state.isPaused ?? false,
                    trackId: state.currentTrackId,
                };
            }
            renderPlaylistList();
        } catch { /* silently ignore */ }
    }

    // ── Create playlist ───────────────────────────────────────────────────

    function bindCreate() {
        document.getElementById("create-playlist-btn")?.addEventListener("click", createPlaylist);
        document.getElementById("new-playlist-name")?.addEventListener("keydown", e => {
            if (e.key === "Enter") { e.preventDefault(); createPlaylist(); }
        });
    }

    async function createPlaylist() {
        const input = document.getElementById("new-playlist-name");
        const name = input?.value?.trim();
        if (!name) { showToast("Enter a playlist name.", "info"); return; }
        try {
            const view = await apiFetch("", { method: "POST", body: JSON.stringify({ name }) });
            input.value = "";
            // Don't push locally — the PLAYLIST_CREATED STOMP event will add it.
            // Pre-set expanded id so it opens as soon as the event renders it.
            if (view?.id) expandedPlaylistId = view.id;
        } catch {
            showToast("Could not create playlist.", "error");
        }
    }

    // ── Playlist list ─────────────────────────────────────────────────────

    function renderPlaylistList() {
        const list = document.getElementById("playlist-list");
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
            const isExpanded = p.id === expandedPlaylistId;
            const isActive   = p.id === activePlaylistId;

            const li = document.createElement("li");
            li.className = "playlist-list-item"
                + (isExpanded ? " expanded" : "")
                + (isActive   ? " active"   : "");
            li.dataset.id = p.id;

            // ── header row ───────────────────────────────────────────────
            const row = document.createElement("div");
            row.className = "playlist-list-row";
            row.addEventListener("click", () => toggleExpand(p.id));

            const nameSpan = document.createElement("span");
            nameSpan.className = "playlist-list-name";
            nameSpan.textContent = p.name;

            const actions = document.createElement("div");
            actions.className = "playlist-list-actions";

            // select-for-playback button
            const selectBtn = document.createElement("button");
            selectBtn.type = "button";
            selectBtn.className = "ghost small icon-button" + (isActive ? " active" : "");
            selectBtn.title = isActive ? "Active — click to deselect" : "Select for playback";
            selectBtn.innerHTML = isActive
                ? '<i class="fa-solid fa-check" aria-hidden="true"></i>'
                : '<i class="fa-solid fa-circle-play" aria-hidden="true"></i>';
            selectBtn.addEventListener("click", (e) => { e.stopPropagation(); toggleActivePlaylist(p.id); });
            actions.appendChild(selectBtn);

            if (isExpanded) {
                const renameBtn = document.createElement("button");
                renameBtn.type = "button";
                renameBtn.className = "ghost small icon-button";
                renameBtn.title = "Rename";
                renameBtn.innerHTML = '<i class="fa-solid fa-pencil" aria-hidden="true"></i>';
                renameBtn.addEventListener("click", (e) => {
                    e.stopPropagation();
                    const form = li.querySelector(".playlist-rename-form");
                    if (form) {
                        form.classList.toggle("hidden");
                        if (!form.classList.contains("hidden")) {
                            form.querySelector("input")?.focus();
                        }
                    }
                });

                const deleteBtn = document.createElement("button");
                deleteBtn.type = "button";
                deleteBtn.className = "ghost small icon-button danger-icon";
                deleteBtn.title = "Delete";
                deleteBtn.innerHTML = '<i class="fa-solid fa-trash" aria-hidden="true"></i>';
                deleteBtn.addEventListener("click", (e) => { e.stopPropagation(); deletePlaylist(p); });

                actions.appendChild(renameBtn);
                actions.appendChild(deleteBtn);
            }

            row.appendChild(nameSpan);
            row.appendChild(actions);
            li.appendChild(row);

            // ── inline detail (only when expanded) ───────────────────────
            if (isExpanded) {
                li.appendChild(buildInlineDetail(p));
            }

            list.appendChild(li);
        });
    }

    function buildInlineDetail(p) {
        const detail = document.createElement("div");
        detail.className = "playlist-inline-detail";

        // rename form (hidden by default)
        const renameForm = document.createElement("div");
        renameForm.className = "playlist-rename-form hidden";
        const renameInput = document.createElement("input");
        renameInput.type = "text";
        renameInput.maxLength = 100;
        renameInput.autocomplete = "off";
        renameInput.value = p.name;
        const renameSave = document.createElement("button");
        renameSave.type = "button";
        renameSave.className = "ghost small";
        renameSave.textContent = "Save";
        const renameCancel = document.createElement("button");
        renameCancel.type = "button";
        renameCancel.className = "ghost small";
        renameCancel.textContent = "Cancel";

        const doRename = async () => {
            const name = renameInput.value.trim();
            if (!name) return;
            try {
                const updated = await apiFetch(`/${p.id}`, { method: "PUT", body: JSON.stringify({ name }) });
                const idx = playlists.findIndex(x => x.id === p.id);
                if (idx >= 0) playlists[idx] = updated;
                renameForm.classList.add("hidden");
                renderPlaylistList();
            } catch {
                showToast("Could not rename playlist.", "error");
            }
        };
        renameSave.addEventListener("click", doRename);
        renameInput.addEventListener("keydown", e => { if (e.key === "Enter") doRename(); });
        renameCancel.addEventListener("click", () => renameForm.classList.add("hidden"));

        renameForm.appendChild(renameInput);
        renameForm.appendChild(renameSave);
        renameForm.appendChild(renameCancel);
        detail.appendChild(renameForm);

        // playback controls (only when this playlist is active)
        if (p.id === activePlaylistId) {
            const controls = document.createElement("div");
            controls.className = "playlist-controls";

            const nowPlaying = document.createElement("div");
            nowPlaying.className = "playlist-now-playing";
            if (playbackState.playing && playbackState.trackId) {
                const track = p.tracks.find(t => t.id === playbackState.trackId);
                nowPlaying.textContent = track ? `♪ ${track.assetName}` : "Playing…";
            }
            controls.appendChild(nowPlaying);

            const buttons = document.createElement("div");
            buttons.className = "playlist-buttons";

            const prevBtn = document.createElement("button");
            prevBtn.type = "button";
            prevBtn.className = "ghost small icon-button";
            prevBtn.title = "Previous / Restart";
            prevBtn.innerHTML = '<i class="fa-solid fa-backward-step" aria-hidden="true"></i>';
            prevBtn.addEventListener("click", () => commandPrev(p));

            const playPauseBtn = document.createElement("button");
            playPauseBtn.type = "button";
            playPauseBtn.className = "ghost small icon-button";
            playPauseBtn.title = "Play / Pause";
            const ppIcon = playbackState.playing && !playbackState.paused ? "fa-pause" : "fa-play";
            playPauseBtn.innerHTML = `<i class="fa-solid ${ppIcon}" aria-hidden="true"></i>`;
            playPauseBtn.addEventListener("click", () => togglePlayPause(p));

            const nextBtn = document.createElement("button");
            nextBtn.type = "button";
            nextBtn.className = "ghost small icon-button";
            nextBtn.title = "Next track";
            nextBtn.innerHTML = '<i class="fa-solid fa-forward-step" aria-hidden="true"></i>';
            nextBtn.addEventListener("click", () => commandNext(p));

            buttons.appendChild(prevBtn);
            buttons.appendChild(playPauseBtn);
            buttons.appendChild(nextBtn);
            controls.appendChild(buttons);
            detail.appendChild(controls);
        }

        // add-track row
        const addRow = document.createElement("div");
        addRow.className = "playlist-add-track-row";

        const sel = document.createElement("select");
        sel.className = "playlist-track-select";
        sel.innerHTML = '<option value="">Add audio asset…</option>';
        audioAssets.forEach(a => {
            const opt = document.createElement("option");
            opt.value = a.id;
            opt.textContent = a.name;
            sel.appendChild(opt);
        });

        const addBtn = document.createElement("button");
        addBtn.type = "button";
        addBtn.className = "ghost small";
        addBtn.textContent = "Add";
        addBtn.addEventListener("click", () => addTrack(p, sel.value));

        addRow.appendChild(sel);
        addRow.appendChild(addBtn);
        detail.appendChild(addRow);

        // track list
        const trackList = document.createElement("ul");
        trackList.className = "playlist-track-list";

        if (!p.tracks?.length) {
            const empty = document.createElement("li");
            empty.className = "playlist-track-empty";
            empty.textContent = "No tracks yet.";
            trackList.appendChild(empty);
        } else {
            p.tracks.forEach(track => {
                const tli = document.createElement("li");
                tli.className = "playlist-track-item" + (track.id === playbackState.trackId ? " playing" : "");
                tli.dataset.trackId = track.id;
                tli.draggable = true;

                const handle = document.createElement("span");
                handle.className = "playlist-track-handle";
                handle.innerHTML = '<i class="fa-solid fa-grip-vertical" aria-hidden="true"></i>';

                const name = document.createElement("span");
                name.className = "playlist-track-name";
                name.textContent = track.assetName;

                const removeBtn = document.createElement("button");
                removeBtn.type = "button";
                removeBtn.className = "ghost small icon-button danger-icon";
                removeBtn.title = "Remove";
                removeBtn.innerHTML = '<i class="fa-solid fa-xmark" aria-hidden="true"></i>';
                removeBtn.addEventListener("click", () => removeTrack(p.id, track.id));

                tli.appendChild(handle);
                tli.appendChild(name);
                tli.appendChild(removeBtn);
                trackList.appendChild(tli);
            });
            bindDragReorder(trackList, p.id);
        }

        detail.appendChild(trackList);
        return detail;
    }

    // ── Expand / collapse ─────────────────────────────────────────────────

    function toggleExpand(playlistId) {
        expandedPlaylistId = expandedPlaylistId === playlistId ? null : playlistId;
        renderPlaylistList();
    }

    // ── Active playlist ───────────────────────────────────────────────────

    async function toggleActivePlaylist(playlistId) {
        const newId = playlistId === activePlaylistId ? null : playlistId;
        try {
            await apiFetch("/active", { method: "PUT", body: JSON.stringify({ playlistId: newId }) });
            activePlaylistId = newId;
            renderPlaylistList();
        } catch {
            showToast("Could not update active playlist.", "error");
        }
    }

    // ── Track management ──────────────────────────────────────────────────

    async function addTrack(p, audioAssetId) {
        if (!audioAssetId) { showToast("Select an audio asset to add.", "info"); return; }
        try {
            const updated = await apiFetch(`/${p.id}/tracks`, { method: "POST", body: JSON.stringify({ audioAssetId }) });
            const idx = playlists.findIndex(x => x.id === p.id);
            if (idx >= 0) playlists[idx] = updated;
            renderPlaylistList();
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
        } catch {
            showToast("Could not remove track.", "error");
        }
    }

    async function deletePlaylist(p) {
        if (!confirm(`Delete playlist "${p.name}"?`)) return;
        try {
            await apiFetch(`/${p.id}`, { method: "DELETE" });
            playlists = playlists.filter(x => x.id !== p.id);
            if (expandedPlaylistId === p.id) expandedPlaylistId = null;
            if (activePlaylistId === p.id) activePlaylistId = null;
            renderPlaylistList();
        } catch {
            showToast("Could not delete playlist.", "error");
        }
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
                    const items = [...list.querySelectorAll(".playlist-track-item")];
                    const fromIdx = items.indexOf(dragging);
                    const toIdx = items.indexOf(item);
                    if (fromIdx < toIdx) list.insertBefore(dragging, item.nextSibling);
                    else list.insertBefore(dragging, item);
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
                } catch {
                    showToast("Could not reorder tracks.", "error");
                    await loadPlaylists();
                }
            });
        });
    }

    // ── Playback controls ─────────────────────────────────────────────────

    async function togglePlayPause(p) {
        if (playbackState.playing && !playbackState.paused) {
            await commandPause(p);
        } else {
            await commandPlay(p, playbackState.trackId || p.tracks[0]?.id || null);
        }
    }

    async function commandPlay(p, trackId) {
        try {
            await apiFetch(`/${p.id}/play`, { method: "POST", body: JSON.stringify({ trackId }) });
        } catch {
            showToast("Could not start playback.", "error");
        }
    }

    async function commandPause(p) {
        try {
            await apiFetch(`/${p.id}/pause`, { method: "POST" });
        } catch {
            showToast("Could not pause.", "error");
        }
    }

    async function commandNext(p) {
        if (!playbackState.trackId) return;
        try {
            await apiFetch(`/${p.id}/next`, { method: "POST", body: JSON.stringify({ currentTrackId: playbackState.trackId }) });
        } catch {
            showToast("Could not skip to next.", "error");
        }
    }

    async function commandPrev(p) {
        if (!playbackState.trackId) return;
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
                }
                break;
            case "PLAYLIST_DELETED":
                playlists = playlists.filter(p => p.id !== playlistId);
                if (expandedPlaylistId === playlistId) expandedPlaylistId = null;
                if (activePlaylistId === playlistId) activePlaylistId = null;
                renderPlaylistList();
                break;
            case "PLAYLIST_SELECTED":
                activePlaylistId = payload?.id ?? null;
                playbackState = { playing: false, paused: false, trackId: null };
                renderPlaylistList();
                break;
            case "PLAYLIST_PLAY":
                playbackState = { playing: true, paused: false, trackId: trackId ?? null };
                renderPlaylistList();
                updateNowPlayingPill();
                break;
            case "PLAYLIST_PAUSE":
                playbackState = { ...playbackState, paused: true };
                renderPlaylistList();
                updateNowPlayingPill();
                break;
            case "PLAYLIST_NEXT":
            case "PLAYLIST_PREV":
                if (trackId) playbackState = { playing: true, paused: false, trackId };
                renderPlaylistList();
                updateNowPlayingPill();
                break;
            case "PLAYLIST_ENDED":
                playbackState = { playing: false, paused: false, trackId: null };
                renderPlaylistList();
                updateNowPlayingPill();
                break;
        }
    }

    function updateNowPlayingPill() {
        const pill = document.getElementById("admin-now-playing-pill");
        const textEl = document.getElementById("admin-now-playing-text");
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
