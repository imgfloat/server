function buildIdentity(admin) {
    const identity = document.createElement('div');
    identity.className = 'identity-row';

    const avatar = document.createElement(admin.avatarUrl ? 'img' : 'div');
    avatar.className = 'avatar';
    if (admin.avatarUrl) {
        avatar.src = admin.avatarUrl;
        avatar.alt = `${admin.displayName || admin.login} avatar`;
    } else {
        avatar.classList.add('avatar-fallback');
        avatar.textContent = (admin.displayName || admin.login || '?').charAt(0).toUpperCase();
    }

    const details = document.createElement('div');
    details.className = 'identity-text';
    const title = document.createElement('p');
    title.className = 'list-title';
    title.textContent = admin.displayName || admin.login;
    const subtitle = document.createElement('p');
    subtitle.className = 'muted';
    subtitle.textContent = `@${admin.login}`;

    details.appendChild(title);
    details.appendChild(subtitle);
    identity.appendChild(avatar);
    identity.appendChild(details);
    return identity;
}

function renderAdmins(list) {
    const adminList = document.getElementById('admin-list');
    if (!adminList) return;
    adminList.innerHTML = '';
    if (!list || list.length === 0) {
        const empty = document.createElement('li');
        empty.textContent = 'No channel admins yet';
        adminList.appendChild(empty);
        return;
    }

    list.forEach((admin) => {
        const li = document.createElement('li');
        li.className = 'stacked-list-item';

        li.appendChild(buildIdentity(admin));

        const actions = document.createElement('div');
        actions.className = 'actions';

        const removeBtn = document.createElement('button');
        removeBtn.type = 'button';
        removeBtn.className = 'secondary';
        removeBtn.textContent = 'Remove';
        removeBtn.addEventListener('click', () => removeAdmin(admin.login));

        actions.appendChild(removeBtn);
        li.appendChild(actions);
        adminList.appendChild(li);
    });
}

function renderSuggestedAdmins(list) {
    const suggestionList = document.getElementById('admin-suggestions');
    if (!suggestionList) return;

    suggestionList.innerHTML = '';
    if (!list || list.length === 0) {
        const empty = document.createElement('li');
        empty.className = 'stacked-list-item';
        empty.textContent = 'No moderator suggestions right now';
        suggestionList.appendChild(empty);
        return;
    }

    list.forEach((admin) => {
        const li = document.createElement('li');
        li.className = 'stacked-list-item';

        li.appendChild(buildIdentity(admin));

        const actions = document.createElement('div');
        actions.className = 'actions';

        const addBtn = document.createElement('button');
        addBtn.type = 'button';
        addBtn.className = 'ghost';
        addBtn.textContent = 'Add as admin';
        addBtn.addEventListener('click', () => addAdmin(admin.login));

        actions.appendChild(addBtn);
        li.appendChild(actions);
        suggestionList.appendChild(li);
    });
}

function fetchSuggestedAdmins() {
    fetch(`/api/channels/${broadcaster}/admins/suggestions`)
        .then((r) => {
            if (!r.ok) {
                throw new Error('Failed to load admin suggestions');
            }
            return r.json();
        })
        .then(renderSuggestedAdmins)
        .catch(() => {
            renderSuggestedAdmins([]);
        });
}

function fetchAdmins() {
    fetch(`/api/channels/${broadcaster}/admins`)
        .then((r) => {
            if (!r.ok) {
                throw new Error('Failed to load admins');
            }
            return r.json();
        })
        .then(renderAdmins)
        .catch(() => {
            renderAdmins([]);
            if (typeof showToast === 'function') {
                showToast('Unable to load admins right now. Please try again.', 'error');
            }
        });
}

function removeAdmin(username) {
    if (!username) return;
    fetch(`/api/channels/${broadcaster}/admins/${encodeURIComponent(username)}`, {
        method: 'DELETE'
    }).then((response) => {
        if (!response.ok && typeof showToast === 'function') {
            showToast('Failed to remove admin. Please retry.', 'error');
        }
        fetchAdmins();
        fetchSuggestedAdmins();
    }).catch(() => {
        if (typeof showToast === 'function') {
            showToast('Failed to remove admin. Please retry.', 'error');
        }
    });
}

function addAdmin(usernameFromAction) {
    const input = document.getElementById('new-admin');
    const username = (usernameFromAction || input?.value || '').trim();
    if (!username) {
        if (typeof showToast === 'function') {
            showToast('Enter a Twitch username to add as an admin.', 'info');
        }
        return;
    }

    fetch(`/api/channels/${broadcaster}/admins`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username })
    })
        .then((response) => {
            if (!response.ok) {
                throw new Error('Add admin failed');
            }
            if (input) {
                input.value = '';
            }
            if (typeof showToast === 'function') {
                showToast(`Added @${username} as an admin.`, 'success');
            }
            fetchAdmins();
            fetchSuggestedAdmins();
        })
        .catch(() => {
            if (typeof showToast === 'function') {
                showToast('Unable to add admin right now. Please try again.', 'error');
            }
        });
}

function renderCanvasSettings(settings) {
    const widthInput = document.getElementById('canvas-width');
    const heightInput = document.getElementById('canvas-height');
    if (widthInput) widthInput.value = Math.round(settings.width);
    if (heightInput) heightInput.value = Math.round(settings.height);
}

function fetchCanvasSettings() {
    fetch(`/api/channels/${broadcaster}/canvas`)
        .then((r) => {
            if (!r.ok) {
                throw new Error('Failed to load canvas settings');
            }
            return r.json();
        })
        .then(renderCanvasSettings)
        .catch(() => {
            renderCanvasSettings({ width: 1920, height: 1080 });
            if (typeof showToast === 'function') {
                showToast('Using default canvas size. Unable to load saved settings.', 'warning');
            }
        });
}

function saveCanvasSettings() {
    const widthInput = document.getElementById('canvas-width');
    const heightInput = document.getElementById('canvas-height');
    const status = document.getElementById('canvas-status');
    const width = parseFloat(widthInput?.value) || 0;
    const height = parseFloat(heightInput?.value) || 0;
    if (width <= 0 || height <= 0) {
        if (typeof showToast === 'function') {
            showToast('Please enter a valid width and height.', 'info');
        }
        return;
    }
    if (status) status.textContent = 'Saving...';
    fetch(`/api/channels/${broadcaster}/canvas`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ width, height })
    })
        .then((r) => {
            if (!r.ok) {
                throw new Error('Failed to save canvas');
            }
            return r.json();
        })
        .then((settings) => {
            renderCanvasSettings(settings);
            if (status) status.textContent = 'Saved.';
            if (typeof showToast === 'function') {
                showToast('Canvas size saved successfully.', 'success');
            }
            setTimeout(() => {
                if (status) status.textContent = '';
            }, 2000);
        })
        .catch(() => {
            if (status) status.textContent = 'Unable to save right now.';
            if (typeof showToast === 'function') {
                showToast('Unable to save canvas size. Please retry.', 'error');
            }
        });
}

fetchAdmins();
fetchSuggestedAdmins();
fetchCanvasSettings();
