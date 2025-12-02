let stompClient;
const canvas = document.getElementById('admin-canvas');
const ctx = canvas.getContext('2d');
canvas.width = canvas.offsetWidth;
canvas.height = canvas.offsetHeight;
const images = new Map();

function connect() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, () => {
        stompClient.subscribe(`/topic/channel/${broadcaster}`, (payload) => {
            const body = JSON.parse(payload.body);
            handleEvent(body);
        });
        fetchImages();
        fetchAdmins();
    });
}

function fetchImages() {
    fetch(`/api/channels/${broadcaster}/images`).then(r => r.json()).then(renderImages);
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

function renderImages(list) {
    list.forEach(img => images.set(img.id, img));
    draw();
}

function handleEvent(event) {
    if (event.type === 'DELETED') {
        images.delete(event.imageId);
    } else if (event.payload) {
        images.set(event.payload.id, event.payload);
    }
    draw();
}

function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    images.forEach(img => {
        ctx.save();
        ctx.globalAlpha = img.hidden ? 0.35 : 1;
        ctx.translate(img.x, img.y);
        ctx.rotate(img.rotation * Math.PI / 180);
        ctx.fillStyle = 'rgba(124, 58, 237, 0.25)';
        ctx.fillRect(0, 0, img.width, img.height);
        ctx.restore();
    });
}

function uploadImage() {
    const url = document.getElementById('image-url').value;
    const width = parseFloat(document.getElementById('image-width').value);
    const height = parseFloat(document.getElementById('image-height').value);
    fetch(`/api/channels/${broadcaster}/images`, {
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
