const { app, BrowserWindow } = require('electron');

function createWindow() {
    const url = process.env.ELECTRON_START_URL || "https://imgfloat.kruhlmann.dev/channels";
    const width = Number.parseInt(process.env.ELECTRON_WINDOW_WIDTH, 10) || 960;
    const height = Number.parseInt(process.env.ELECTRON_WINDOW_HEIGHT, 10) || 640;

    const win = new BrowserWindow({
        width: width,
        height: height,
        transparent: true,
        frame: false,
        backgroundColor: '#00000000',
        alwaysOnTop: false,
        webPreferences: {
            backgroundThrottling: false
        }
    });

    let canvasSizeInterval;

    const clearCanvasSizeInterval = () => {
        if (canvasSizeInterval) {
            clearInterval(canvasSizeInterval);
            canvasSizeInterval = undefined;
        }
    };

    const lockWindowToCanvas = async () => {
        if (win.isDestroyed()) {
            return false;
        }
        try {
            const size = await win.webContents.executeJavaScript(`(() => {
                const canvas = document.getElementById('broadcast-canvas');
                if (!canvas || !canvas.width || !canvas.height) {
                    return null;
                }
                return { width: Math.round(canvas.width), height: Math.round(canvas.height) };
            })();`);

            if (size?.width && size?.height) {
                const [currentWidth, currentHeight] = win.getSize();
                if (currentWidth !== size.width || currentHeight !== size.height) {
                    win.setSize(size.width, size.height, false);
                }
                win.setMinimumSize(size.width, size.height);
                win.setMaximumSize(size.width, size.height);
                win.setResizable(false);
                return true;
            }
        } catch (error) {
            // Best-effort sizing; ignore errors from early navigation states.
        }
        return false;
    };

    const handleNavigation = (navigationUrl) => {
        try {
            const { pathname } = new URL(navigationUrl);
            const isBroadcast = /\/view\/[^/]+\/broadcast\/?$/.test(pathname);

            if (isBroadcast) {
                clearCanvasSizeInterval();
                canvasSizeInterval = setInterval(lockWindowToCanvas, 750);
                lockWindowToCanvas();
            } else {
                clearCanvasSizeInterval();
                win.setResizable(true);
                win.setMinimumSize(320, 240);
                win.setMaximumSize(10000, 10000);
                win.setSize(width, height, false);
            }
        } catch {
            // Ignore malformed URLs while navigating.
        }
    };

    win.loadURL(url);

    win.webContents.on('did-finish-load', () => {
        win.webContents.insertCSS(`
            html, body {
                -webkit-app-region: drag;
                user-select: none;
            }

            body {
                transition: opacity 120ms ease;
            }

            a, button, input, select, textarea, option, [role="button"], [role="textbox"] {
                -webkit-app-region: no-drag;
                user-select: auto;
            }
        `);

        win.webContents.executeJavaScript(`(() => {
            const body = document.body;
            if (!body) {
                return;
            }
            const defaultOpacity = getComputedStyle(body).opacity || '1';
            let timeout;

            window.__imgfloatShowDragOverlay = () => {
                body.style.opacity = '0.9';
                clearTimeout(timeout);
                timeout = setTimeout(() => {
                    body.style.opacity = defaultOpacity;
                }, 150);
            };
        })();`);

        handleNavigation(win.webContents.getURL());
    });

    win.on('move', () => {
        win.webContents.executeJavaScript('window.__imgfloatShowDragOverlay && window.__imgfloatShowDragOverlay();');
    });

    win.webContents.on('did-navigate', (_event, navigationUrl) => handleNavigation(navigationUrl));
    win.webContents.on('did-navigate-in-page', (_event, navigationUrl) => handleNavigation(navigationUrl));
    win.on('closed', clearCanvasSizeInterval);
}

app.whenReady().then(() => {
    createWindow();
});
