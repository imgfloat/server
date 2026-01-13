const DEFAULT_WIDTH = 640;
const DEFAULT_HEIGHT = 360;
const MAX_PIXEL_RATIO = 2;

function getThree() {
    return globalThis.THREE || null;
}

function clampSize(value, fallback) {
    if (!Number.isFinite(value) || value <= 0) {
        return fallback;
    }
    return Math.max(1, Math.round(value));
}

function pickLoader(asset, three) {
    const url = (asset?.url || "").toLowerCase();
    if ((asset?.mediaType === "model/obj" || url.endsWith(".obj")) && typeof three.OBJLoader === "function") {
        return { loader: new three.OBJLoader(), kind: "obj" };
    }
    if (typeof three.GLTFLoader === "function") {
        return { loader: new three.GLTFLoader(), kind: "gltf" };
    }
    return null;
}

function centerAndScale(model, three) {
    const box = new three.Box3().setFromObject(model);
    const size = box.getSize(new three.Vector3());
    const center = box.getCenter(new three.Vector3());
    model.position.sub(center);
    const maxDim = Math.max(size.x, size.y, size.z) || 1;
    const scale = 1.5 / maxDim;
    model.scale.setScalar(scale);
    return { size, maxDim };
}

function disposeModel(model) {
    if (!model?.traverse) {
        return;
    }
    model.traverse((child) => {
        if (child.geometry?.dispose) {
            child.geometry.dispose();
        }
        if (child.material) {
            if (Array.isArray(child.material)) {
                child.material.forEach((material) => material?.dispose?.());
            } else {
                child.material.dispose?.();
            }
        }
    });
}

export function createModelManager({ requestDraw } = {}) {
    const controllers = new Map();

    function clearModel(assetId) {
        const controller = controllers.get(assetId);
        if (!controller) {
            return;
        }
        if (controller.model) {
            disposeModel(controller.model);
        }
        controller.renderer?.dispose?.();
        controllers.delete(assetId);
    }

    function ensureModel(asset) {
        const three = getThree();
        if (!three || !asset?.id || !asset?.url) {
            return null;
        }

        let controller = controllers.get(asset.id);
        if (controller && controller.url !== asset.url) {
            clearModel(asset.id);
            controller = null;
        }

        if (!controller) {
            const canvas = document.createElement("canvas");
            const renderer = new three.WebGLRenderer({ canvas, alpha: true, antialias: true });
            renderer.setPixelRatio(Math.min(globalThis.devicePixelRatio || 1, MAX_PIXEL_RATIO));
            renderer.setClearColor(0x000000, 0);

            const scene = new three.Scene();
            const camera = new three.PerspectiveCamera(35, 1, 0.1, 100);
            const ambient = new three.AmbientLight(0xffffff, 0.85);
            const directional = new three.DirectionalLight(0xffffff, 0.65);
            directional.position.set(1, 1, 1);
            scene.add(ambient);
            scene.add(directional);

            controller = {
                id: asset.id,
                url: asset.url,
                canvas,
                renderer,
                scene,
                camera,
                model: null,
                ready: false,
                startTime: performance.now(),
                width: 0,
                height: 0,
            };

            const loaderChoice = pickLoader(asset, three);
            if (loaderChoice) {
                if (loaderChoice.kind === "obj") {
                    loaderChoice.loader.load(asset.url, (obj) => {
                        const { maxDim } = centerAndScale(obj, three);
                        controller.model = obj;
                        controller.scene.add(obj);
                        const distance = maxDim * 2.2;
                        controller.camera.position.set(0, 0, distance);
                        controller.camera.near = Math.max(0.01, distance / 100);
                        controller.camera.far = distance * 100;
                        controller.camera.updateProjectionMatrix();
                        controller.ready = true;
                        requestDraw?.();
                    });
                } else {
                    loaderChoice.loader.load(asset.url, (gltf) => {
                        const model = gltf.scene || gltf.scenes?.[0];
                        if (!model) {
                            return;
                        }
                        const { maxDim } = centerAndScale(model, three);
                        controller.model = model;
                        controller.scene.add(model);
                        const distance = maxDim * 2.2;
                        controller.camera.position.set(0, 0, distance);
                        controller.camera.near = Math.max(0.01, distance / 100);
                        controller.camera.far = distance * 100;
                        controller.camera.updateProjectionMatrix();
                        controller.ready = true;
                        requestDraw?.();
                    });
                }
            }

            controllers.set(asset.id, controller);
        }

        const width = clampSize(asset.width, DEFAULT_WIDTH);
        const height = clampSize(asset.height, DEFAULT_HEIGHT);
        if (controller.width !== width || controller.height !== height) {
            controller.width = width;
            controller.height = height;
            controller.renderer.setSize(width, height, false);
            controller.camera.aspect = width / height;
            controller.camera.updateProjectionMatrix();
        }

        if (controller.ready && controller.model) {
            const now = performance.now();
            controller.model.rotation.y = (now - controller.startTime) * 0.0004;
            controller.renderer.render(controller.scene, controller.camera);
        }

        return { canvas: controller.canvas, ready: controller.ready };
    }

    return { ensureModel, clearModel };
}

