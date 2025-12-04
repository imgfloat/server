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
    });
}

function fetchAssets() {
    fetch(`/api/channels/${broadcaster}/assets`).then(r => r.json()).then(renderAssets);
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
    const fileInput = document.getElementById('asset-file');
    if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
        alert('Please choose an image to upload.');
        return;
    }
    const data = new FormData();
    data.append('file', fileInput.files[0]);
    fetch(`/api/channels/${broadcaster}/assets`, {
        method: 'POST',
        body: data
    }).then(() => {
        fileInput.value = '';
    });
}

window.addEventListener('resize', () => {
    canvas.width = canvas.offsetWidth;
    canvas.height = canvas.offsetHeight;
    draw();
});

connect();
