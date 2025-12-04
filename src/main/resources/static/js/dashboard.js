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

fetchAdmins();
