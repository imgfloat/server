const { app, BrowserWindow } = require('electron');
const path = require('path');

function createWindow() {
    const url = "https://imgfloat.kruhlmann.dev/channels";
    const initialWindowWidthPx = 960;
    const initialWindowHeightPx = 640;
    const applicationWindow = new BrowserWindow({
        width: initialWindowWidthPx,
        height: initialWindowHeightPx,
        transparent: true,
        frame: true,
        backgroundColor: '#00000000',
        alwaysOnTop: false,
        icon: path.join(__dirname, "../resources/assets/icon/appicon.ico"),
        webPreferences: { backgroundThrottling: false },
    });
    win.setMenu(null);

    let canvasSizeInterval;
    const clearCanvasSizeInterval = () => {
        if (canvasSizeInterval) {
            clearInterval(canvasSizeInterval);
            canvasSizeInterval = undefined;
        }
    };

    const lockWindowToCanvas = async () => {
        if (applicationWindow.isDestroyed()) {
            return false;
        }
        try {
            const size = await applicationWindow.webContents.executeJavaScript(`(() => {
                const canvas = document.getElementById('broadcast-canvas');
                if (!canvas || !canvas.width || !canvas.height) {
                    return null;
                }
                return { width: Math.round(canvas.width), height: Math.round(canvas.height) };
            })();`);

            if (size?.width && size?.height) {
                const [currentWidth, currentHeight] = applicationWindow.getSize();
                if (currentWidth !== size.width || currentHeight !== size.height) {
                    applicationWindow.setSize(size.width, size.height, false);
                }
                applicationWindow.setMinimumSize(size.width, size.height);
                applicationWindow.setMaximumSize(size.width, size.height);
                applicationWindow.setResizable(false);
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
                applicationWindow.setResizable(true);
                applicationWindow.setMinimumSize(320, 240);
                applicationWindow.setMaximumSize(10000, 10000);
                applicationWindow.setSize(initialWindowWidthPx, initialWindowHeightPx, false);
            }
        } catch {
            // Ignore malformed URLs while navigating.
        }
    };

    applicationWindow.loadURL(url);

    applicationWindow.webContents.on('did-finish-load', () => {
        handleNavigation(applicationWindow.webContents.getURL());
    });

    applicationWindow.webContents.on('did-navigate', (_event, navigationUrl) => handleNavigation(navigationUrl));
    applicationWindow.webContents.on('did-navigate-in-page', (_event, navigationUrl) => handleNavigation(navigationUrl));
    applicationWindow.on('closed', clearCanvasSizeInterval);
}

app.whenReady().then(createWindow);
