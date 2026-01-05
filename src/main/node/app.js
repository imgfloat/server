const { app, BrowserWindow } = require("electron");
const path = require("path");
let broadcastRect = { width: 0, height: 0 };

function createWindow() {
    const url = process.env["IMGFLOAT_CHANNELS_URL"] || "https://imgfloat.kruhlmann.dev/channels";
    const initialWindowWidthPx = 960;
    const initialWindowHeightPx = 640;
    const applicationWindow = new BrowserWindow({
        width: initialWindowWidthPx,
        height: initialWindowHeightPx,
        transparent: true,
        frame: true,
        backgroundColor: "#00000000",
        alwaysOnTop: false,
        icon: path.join(__dirname, "../resources/assets/icon/appicon.ico"),
        webPreferences: { backgroundThrottling: false },
    });
    applicationWindow.setMenu(null);

    let canvasSizeInterval;
    const clearCanvasSizeInterval = () => {
        if (canvasSizeInterval) {
            clearInterval(canvasSizeInterval);
            canvasSizeInterval = undefined;
        }
    };

    const lockWindowToCanvas = async () => {
        if (applicationWindow.isDestroyed()) {
            return;
        }
        const size = await applicationWindow.webContents.executeJavaScript(`(() => {
            const canvas = document.getElementById('broadcast-canvas');
            if (!canvas) {
                return null;
            }
            const rect = canvas.getBoundingClientRect();
            return {
                width: Math.round(rect.width),
                height: Math.round(rect.height),
            };
        })();`);

        if (size?.width && size?.height) {
            if (broadcastRect.width !== size.width || broadcastRect.height !== size.height) {
                console.log(
                    `Window size did not match canvas old: ${broadcastRect.width}x${broadcastRect.height} new: ${size.width}x${size.height}. Resizing.`,
                );
                applicationWindow.setContentSize(size.width, size.height, false);
                applicationWindow.setResizable(false);
                broadcastRect = { ...size };
            }
        }
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
                applicationWindow.setSize(initialWindowWidthPx, initialWindowHeightPx, false);
            }
        } catch {
            // Ignore malformed URLs while navigating.
        }
    };

    applicationWindow.loadURL(url);

    applicationWindow.webContents.on("did-finish-load", () => {
        handleNavigation(applicationWindow.webContents.getURL());
    });

    applicationWindow.webContents.on("did-navigate", (_event, navigationUrl) => handleNavigation(navigationUrl));
    applicationWindow.webContents.on("did-navigate-in-page", (_event, navigationUrl) =>
        handleNavigation(navigationUrl),
    );
    applicationWindow.on("closed", clearCanvasSizeInterval);
}

app.whenReady().then(createWindow);
