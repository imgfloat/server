function canManageElectronWindow() {
    return (
        typeof window !== "undefined" &&
        window.store &&
        typeof window.store.setWindowSize === "function"
    );
}

function getWindowFrameHeight() {
    const height = getComputedStyle(document.documentElement)
        .getPropertyValue("--window-frame-height")
        .trim();
    const parsed = Number.parseInt(height.replace("px", ""), 10);
    return Number.isNaN(parsed) ? 0 : parsed;
}

export function setUpElectronWindowFrame() {
    if (
        typeof window === "undefined" ||
        !window.store ||
        typeof window.store.minimizeWindow !== "function"
    ) {
        return false;
    }

    document.body.classList.add("has-window-frame");

    const frame = document.createElement("div");
    frame.className = "window-frame";
    frame.setAttribute("role", "presentation");
    frame.innerHTML = `
        <div class="window-frame-title">Imgfloat</div>
        <div class="window-frame-controls">
            <button class="window-control" type="button" data-window-action="minimize" aria-label="Minimize">
                &minus;
            </button>
            <button class="window-control window-control-close" type="button" data-window-action="close" aria-label="Close">
                &times;
            </button>
        </div>
    `;
    document.body.appendChild(frame);

    frame.querySelectorAll("[data-window-action]").forEach((button) => {
        button.addEventListener("click", () => {
            const action = button.dataset.windowAction;
            if (action === "minimize") {
                window.store.minimizeWindow();
            }
            if (action === "close") {
                window.store.closeWindow();
            }
        });
    });

    return true;
}

export function setUpElectronWindowResizeListener(canvas) {
    if (canManageElectronWindow()) {
        console.info("Electron environment detected, setting up resize listener.");
    } else {
        console.info("Not running in Electron environment, skipping resize listener setup.");
        return;
    }

    const resize = () => {
        const rect = canvas.getBoundingClientRect();
        const frameHeight = document.body.classList.contains("has-window-frame")
            ? getWindowFrameHeight()
            : 0;
        window.store.setWindowSize(
            Math.ceil(rect.width),
            Math.ceil(rect.height + frameHeight)
        );
    };

    resize();

    new ResizeObserver(resize).observe(canvas);
}
