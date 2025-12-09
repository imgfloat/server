function renderAdmins(list) {
    const adminList = document.getElementById('admin-list');
    adminList.innerHTML = '';
    if (!list || list.length === 0) {
        const empty = document.createElement('li');
        empty.textContent = 'No channel admins yet';
        adminList.appendChild(empty);
        return;
    }

    list.forEach((admin) => {
        const li = document.createElement('li');
        li.textContent = admin;
        adminList.appendChild(li);
    });
}

function fetchAdmins() {
    fetch(`/api/channels/${broadcaster}/admins`)
        .then((r) => r.json())
        .then(renderAdmins)
        .catch(() => renderAdmins([]));
}

function addAdmin() {
    const input = document.getElementById('new-admin');
    const username = input.value.trim();
    if (!username) {
        alert('Enter a Twitch username to add as an admin.');
        return;
    }

    fetch(`/api/channels/${broadcaster}/admins`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username })
    })
        .then(() => {
            input.value = '';
            fetchAdmins();
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
        .then((r) => r.json())
        .then(renderCanvasSettings)
        .catch(() => renderCanvasSettings({ width: 1920, height: 1080 }));
}

function saveCanvasSettings() {
    const widthInput = document.getElementById('canvas-width');
    const heightInput = document.getElementById('canvas-height');
    const status = document.getElementById('canvas-status');
    const width = parseFloat(widthInput?.value) || 0;
    const height = parseFloat(heightInput?.value) || 0;
    if (width <= 0 || height <= 0) {
        alert('Please enter a valid width and height.');
        return;
    }
    if (status) status.textContent = 'Saving...';
    fetch(`/api/channels/${broadcaster}/canvas`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ width, height })
    })
        .then((r) => r.json())
        .then((settings) => {
            renderCanvasSettings(settings);
            if (status) status.textContent = 'Saved.';
            setTimeout(() => {
                if (status) status.textContent = '';
            }, 2000);
        })
        .catch(() => {
            if (status) status.textContent = 'Unable to save right now.';
        });
}

fetchAdmins();
fetchCanvasSettings();
