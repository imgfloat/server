const canvas = document.getElementById('broadcast-canvas');
const ctx = canvas.getContext('2d');
canvas.width = window.innerWidth;
canvas.height = window.innerHeight;
const images = new Map();

function connect() {
    const socket = new SockJS('/ws');
    const stompClient = Stomp.over(socket);
    stompClient.connect({}, () => {
        stompClient.subscribe(`/topic/channel/${broadcaster}`, (payload) => {
            const body = JSON.parse(payload.body);
            handleEvent(body);
        });
        fetch(`/api/channels/${broadcaster}/images/visible`).then(r => r.json()).then(renderImages);
    });
}

function renderImages(list) {
    list.forEach(img => images.set(img.id, img));
    draw();
}

function handleEvent(event) {
    if (event.type === 'DELETED') {
        images.delete(event.imageId);
    } else if (event.payload && !event.payload.hidden) {
        images.set(event.payload.id, event.payload);
    } else if (event.payload && event.payload.hidden) {
        images.delete(event.payload.id);
    }
    draw();
}

function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    images.forEach(img => {
        ctx.save();
        ctx.globalAlpha = 1;
        ctx.translate(img.x, img.y);
        ctx.rotate(img.rotation * Math.PI / 180);
        const image = new Image();
        image.src = img.url;
        image.onload = () => {
            ctx.drawImage(image, 0, 0, img.width, img.height);
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
