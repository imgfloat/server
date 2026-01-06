const path = require("node:path");

const { app, BrowserWindow } = require("electron");
const { autoUpdater } = require("electron-updater");

const initialWindowWidthPx = 960;
const initialWindowHeightPx = 640;

let canvasSizeInterval;
function clearCanvasSizeInterval() {
    if (canvasSizeInterval) {
        clearInterval(canvasSizeInterval);
        canvasSizeInterval = undefined;
    }
}

async function autoResizeWindow(win, lastSize) {
    if (win.isDestroyed()) {
        return lastSize;
    }
    const newSize = await win.webContents.executeJavaScript(`(() => {
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

    if (!newSize?.width || !newSize?.height) {
        return lastSize;
    }
    if (lastSize.width === newSize.width && lastSize.height === newSize.height) {
        return lastSize;
    }
    console.info(
        `Window size did not match canvas old: ${lastSize.width}x${lastSize.height} new: ${newSize.width}x${newSize.height}. Resizing.`,
    );
    win.setContentSize(newSize.width, newSize.height, false);
    win.setResizable(false);
    return newSize;
}

function onPostNavigationLoad(win, url, broadcastRect) {
    url = url || win.webContents.getURL();
    let pathname;
    try {
        pathname = new URL(url).pathname;
    } catch (e) {
        console.error(`Failed to parse URL: ${url}`, e);
        return;
    }
    const isBroadcast = /\/view\/[^/]+\/broadcast\/?$/.test(pathname);

    console.info(`Navigation to ${url} detected. Is broadcast: ${isBroadcast}`);
    if (isBroadcast) {
        clearCanvasSizeInterval();
        console.info("Setting up auto-resize for broadcast window.");
        canvasSizeInterval = setInterval(() => {
            autoResizeWindow(win, broadcastRect).then((newSize) => {
                broadcastRect = newSize;
            });
        }, 750);
        autoResizeWindow(win, broadcastRect).then((newSize) => {
            broadcastRect = newSize;
        });
    } else {
        clearCanvasSizeInterval();
        win.setSize(initialWindowWidthPx, initialWindowHeightPx, false);
    }
}

function createWindow(version) {
    const win = new BrowserWindow({
        width: initialWindowWidthPx,
        height: initialWindowHeightPx,
        transparent: true,
        frame: true,
        backgroundColor: "#00000000",
        alwaysOnTop: false,
        icon: path.join(__dirname, "../resources/assets/icon/appicon.ico"),
        webPreferences: { backgroundThrottling: false },
    });
    win.setMenu(null);
    win.setTitle(`Imgfloat Client v${version}`);

    return win;
}

app.whenReady().then(() => {
    if (process.env.CI) {
        process.on("uncaughtException", (err) => {
            console.error("Uncaught exception:", err);
            app.exit(1);
        });
        setTimeout(() => app.quit(), 3000);
    }
    autoUpdater.checkForUpdatesAndNotify();

    let broadcastRect = { width: 0, height: 0 };
    const version = app.getVersion();
    const win = createWindow(version);
    win.loadURL(process.env["IMGFLOAT_CHANNELS_URL"] || "https://imgfloat.kruhlmann.dev/channels");
    win.webContents.on("did-finish-load", () => onPostNavigationLoad(win, undefined, broadcastRect));
    win.webContents.on("did-navigate", (_, url) => onPostNavigationLoad(win, url, broadcastRect));
    win.webContents.on("did-navigate-in-page", (_, url) => onPostNavigationLoad(win, url, broadcastRect));
    win.on("page-title-updated", (e) => e.preventDefault());
    win.on("closed", clearCanvasSizeInterval);
});
