const canvas = document.getElementById('broadcast-canvas');
const ctx = canvas.getContext('2d');
canvas.width = window.innerWidth;
canvas.height = window.innerHeight;
const assets = new Map();

function connect() {
    const socket = new SockJS('/ws');
    const stompClient = Stomp.over(socket);
    stompClient.connect({}, () => {
        stompClient.subscribe(`/topic/channel/${broadcaster}`, (payload) => {
            const body = JSON.parse(payload.body);
            handleEvent(body);
        });
        fetch(`/api/channels/${broadcaster}/assets/visible`).then(r => r.json()).then(renderAssets);
    });
}

function renderAssets(list) {
    list.forEach(asset => assets.set(asset.id, asset));
    draw();
}

function handleEvent(event) {
    if (event.type === 'DELETED') {
        assets.delete(event.assetId);
    } else if (event.payload && !event.payload.hidden) {
        assets.set(event.payload.id, event.payload);
    } else if (event.payload && event.payload.hidden) {
        assets.delete(event.payload.id);
    }
    draw();
}

function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    assets.forEach(asset => {
        ctx.save();
        ctx.globalAlpha = 1;
        ctx.translate(asset.x, asset.y);
        ctx.rotate(asset.rotation * Math.PI / 180);
        const image = new Image();
        image.src = asset.url;
        image.onload = () => {
            ctx.drawImage(image, 0, 0, asset.width, asset.height);
        };
        ctx.restore();
    });
}

window.addEventListener('resize', () => {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
    draw();
});

connect();
