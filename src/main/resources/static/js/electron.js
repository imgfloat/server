export function setUpElectronWindowResizeListener(canvas) {
    if (
        typeof window !== "undefined" &&
        window.store &&
        typeof window.store.setWindowSize === "function"
    ) {
        console.info("Electron environment detected, setting up resize listener.");
    } else {
        console.info("Not running in Electron environment, skipping resize listener setup.");
        return;
    }

    const resize = () => {
        const rect = canvas.getBoundingClientRect();
        window.store.setWindowSize(
            Math.ceil(rect.width),
            Math.ceil(rect.height)
        );
    };

    resize();

    new ResizeObserver(resize).observe(canvas);
}
