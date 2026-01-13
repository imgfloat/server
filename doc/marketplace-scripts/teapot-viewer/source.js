function init(context, state) {
    const asset = Array.isArray(context.assets) ? context.assets[0] : null;
    const THREE = globalThis.THREE;
    if (!asset?.url || !THREE) {
        state.error = "Three.js dependencies are unavailable or no model attachment was found.";
        return;
    }

    const glCanvas = new OffscreenCanvas(context.width || 1, context.height || 1);
    const renderer = new THREE.WebGLRenderer({ canvas: glCanvas, alpha: true, antialias: true });
    renderer.setPixelRatio(1);
    renderer.setClearColor(0x000000, 0);

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(35, 1, 0.1, 100);
    const ambient = new THREE.AmbientLight(0xffffff, 0.85);
    const directional = new THREE.DirectionalLight(0xffffff, 0.65);
    directional.position.set(1, 1, 1);
    scene.add(ambient);
    scene.add(directional);

    const lowerUrl = asset.url.toLowerCase();
    const isObj = asset.mediaType === "model/obj" || lowerUrl.endsWith(".obj");
    const loader = isObj && typeof THREE.OBJLoader === "function" ? new THREE.OBJLoader() : new THREE.GLTFLoader();

    fetch(asset.url)
        .then((response) => {
            if (!response.ok) {
                throw new Error("Unable to fetch model");
            }
            return isObj ? response.text() : response.arrayBuffer();
        })
        .then((data) => {
            if (isObj) {
                return loader.parse(data);
            }
            return new Promise((resolve, reject) => {
                loader.parse(data, "", resolve, reject);
            });
        })
        .then((result) => {
            const model = isObj ? result : result.scene || result.scenes?.[0];
            if (!model) {
                state.error = "Failed to read model data.";
                return;
            }
            const box = new THREE.Box3().setFromObject(model);
            const size = box.getSize(new THREE.Vector3());
            const center = box.getCenter(new THREE.Vector3());
            model.position.sub(center);
            const maxDim = Math.max(size.x, size.y, size.z) || 1;
            const scale = 1.5 / maxDim;
            model.scale.setScalar(scale);
            scene.add(model);
            const distance = maxDim * 2.2;
            camera.position.set(0, 0, distance);
            camera.near = Math.max(0.01, distance / 100);
            camera.far = distance * 100;
            camera.updateProjectionMatrix();
            state.model = model;
        })
        .catch((error) => {
            state.error = error?.message || "Failed to load model.";
        });

    state.glCanvas = glCanvas;
    state.renderer = renderer;
    state.scene = scene;
    state.camera = camera;
    state.rotation = 0;
}

function tick(context, state) {
    const { ctx, width, height, deltaMs } = context;
    if (!ctx || !state.renderer || !state.scene || !state.camera || !state.glCanvas) {
        return;
    }

    const nextWidth = Math.max(1, Math.round(width || 1));
    const nextHeight = Math.max(1, Math.round(height || 1));
    if (state.glCanvas.width !== nextWidth || state.glCanvas.height !== nextHeight) {
        state.glCanvas.width = nextWidth;
        state.glCanvas.height = nextHeight;
        state.renderer.setSize(nextWidth, nextHeight, false);
        state.camera.aspect = nextWidth / nextHeight;
        state.camera.updateProjectionMatrix();
    }

    ctx.clearRect(0, 0, nextWidth, nextHeight);

    if (state.model) {
        state.rotation = (state.rotation + (deltaMs || 0) * 0.0008) % (Math.PI * 2);
        state.model.rotation.y = state.rotation;
    }

    state.renderer.render(state.scene, state.camera);
    ctx.drawImage(state.glCanvas, 0, 0, nextWidth, nextHeight);
}
