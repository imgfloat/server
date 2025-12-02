let stompClient;
const canvas = document.getElementById('admin-canvas');
const ctx = canvas.getContext('2d');
canvas.width = canvas.offsetWidth;
canvas.height = canvas.offsetHeight;
const assets = new Map();

function connect() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, () => {
        stompClient.subscribe(`/topic/channel/${broadcaster}`, (payload) => {
            const body = JSON.parse(payload.body);
            handleEvent(body);
        });
        fetchAssets();
        fetchAdmins();
    });
}

function fetchAssets() {
    fetch(`/api/channels/${broadcaster}/assets`).then(r => r.json()).then(renderAssets);
}

function fetchAdmins() {
    fetch(`/api/channels/${broadcaster}/admins`).then(r => r.json()).then(list => {
        const adminList = document.getElementById('admin-list');
        adminList.innerHTML = '';
        list.forEach(a => {
            const li = document.createElement('li');
            li.textContent = a;
            adminList.appendChild(li);
        });
    }).catch(() => {});
}

function renderAssets(list) {
    list.forEach(asset => assets.set(asset.id, asset));
    draw();
}

function handleEvent(event) {
    if (event.type === 'DELETED') {
        assets.delete(event.assetId);
    } else if (event.payload) {
        assets.set(event.payload.id, event.payload);
    }
    draw();
}

function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    assets.forEach(asset => {
        ctx.save();
        ctx.globalAlpha = asset.hidden ? 0.35 : 1;
        ctx.translate(asset.x, asset.y);
        ctx.rotate(asset.rotation * Math.PI / 180);
        ctx.fillStyle = 'rgba(124, 58, 237, 0.25)';
        ctx.fillRect(0, 0, asset.width, asset.height);
        ctx.restore();
    });
}

function uploadAsset() {
    const url = document.getElementById('asset-url').value;
    const width = parseFloat(document.getElementById('asset-width').value);
    const height = parseFloat(document.getElementById('asset-height').value);
    fetch(`/api/channels/${broadcaster}/assets`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({url, width, height})
    });
}

function addAdmin() {
    const usernameInput = document.getElementById('new-admin');
    const username = usernameInput.value;
    fetch(`/api/channels/${broadcaster}/admins`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({username})
    }).then(() => fetchAdmins());
}

window.addEventListener('resize', () => {
    canvas.width = canvas.offsetWidth;
    canvas.height = canvas.offsetHeight;
    draw();
});

connect();
