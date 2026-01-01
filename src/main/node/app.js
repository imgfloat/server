const { app, BrowserWindow } = require('electron');

function createWindow() {
    const url = process.env.ELECTRON_START_URL || "https://imgfloat.kruhlmann.dev/channels";
    const width = Number.parseInt(process.env.ELECTRON_WINDOW_WIDTH, 10) || 1920;
    const height = Number.parseInt(process.env.ELECTRON_WINDOW_HEIGHT, 10) || 1080;

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

    win.loadURL(url);

    win.webContents.on('did-finish-load', () => {
        win.webContents.insertCSS(`
            html, body {
                -webkit-app-region: drag;
                user-select: none;
            }

            a, button, input, select, textarea, option, [role="button"], [role="textbox"] {
                -webkit-app-region: no-drag;
                user-select: auto;
            }
        `);

        win.webContents.executeJavaScript(`(() => {
            const overlayId = 'imgfloat-drag-overlay';
            if (!document.getElementById(overlayId)) {
                const overlay = document.createElement('div');
                overlay.id = overlayId;
                Object.assign(overlay.style, {
                    position: 'fixed',
                    inset: '0',
                    background: 'rgba(0, 0, 0, 0)',
                    transition: 'background 120ms ease',
                    pointerEvents: 'none',
                    zIndex: '2147483647',
                    webkitAppRegion: 'drag'
                });
                document.documentElement.appendChild(overlay);
            }

            const overlay = document.getElementById(overlayId);
            let timeout;

            window.__imgfloatShowDragOverlay = () => {
                if (!overlay) {
                    return;
                }

                overlay.style.background = 'rgba(0, 0, 0, 0.35)';
                clearTimeout(timeout);
                timeout = setTimeout(() => {
                    overlay.style.background = 'rgba(0, 0, 0, 0)';
                }, 150);
            };
        })();`);
    });

    win.on('move', () => {
        win.webContents.executeJavaScript('window.__imgfloatShowDragOverlay && window.__imgfloatShowDragOverlay();');
    });
}

app.whenReady().then(() => {
    createWindow();
});
