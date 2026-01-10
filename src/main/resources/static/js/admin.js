import { createAdminConsole } from "./admin/console.js";
import { createCustomAssetModal } from "./customAssets.js";

let adminConsole;
const customAssetModal = createCustomAssetModal({
    broadcaster,
    adminChannels: ADMIN_CHANNELS,
    showToast: globalThis.showToast,
    onAssetSaved: (asset) => adminConsole?.handleCustomAssetSaved(asset),
});

adminConsole = createAdminConsole({
    broadcaster,
    username,
    settings: SETTINGS,
    uploadLimitBytes: UPLOAD_LIMIT_BYTES,
    showToast: globalThis.showToast,
    customAssetModal,
});

adminConsole.start();
