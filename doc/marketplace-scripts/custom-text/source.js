const TEXT_CONFIG = {
    text: "Hello from the marketplace!",
    fontFamily: "Atkinson Hyperlegible",
    fontSize: 64,
    fontStyle: "normal",
    position: {
        x: 80,
        y: 120,
    },
    color: "#00ff00",
    textAlign: "left",
    textBaseline: "top",
};

const FONT_ATTACHMENT_NAME = "AtkinsonHyperlegible-Regular.ttf";

function resolveFontAttachment(assets) {
    if (!Array.isArray(assets)) {
        return null;
    }
    const normalizedName = FONT_ATTACHMENT_NAME.toLowerCase();
    return assets.find((asset) => asset?.name?.toLowerCase() === normalizedName) || null;
}

async function loadFont(context, state) {
    if (state.fontLoading || state.fontReady) {
        return;
    }
    state.fontLoading = true;
    const attachment = resolveFontAttachment(context.assets);
    if (!attachment?.url) {
        state.fontReady = true;
        state.fontLoading = false;
        return;
    }
    if (typeof FontFace !== "function" || !self?.fonts) {
        state.fontReady = true;
        state.fontLoading = false;
        return;
    }
    try {
        const response = await fetch(attachment.url);
        if (!response.ok) {
            throw new Error("Unable to load font attachment");
        }
        const buffer = await response.arrayBuffer();
        const font = new FontFace(TEXT_CONFIG.fontFamily, buffer);
        await font.load();
        self.fonts.add(font);
    } catch (error) {
        state.fontError = error;
    } finally {
        state.fontReady = true;
        state.fontLoading = false;
    }
}

async function init(context, state) {
    await loadFont(context, state);
}

function tick(context, state) {
    const { ctx, width, height } = context;
    if (!ctx) {
        return;
    }
    if (!state?.fontReady) {
        loadFont(context, state);
        return;
    }
    ctx.clearRect(0, 0, width, height);
    ctx.save();
    ctx.fillStyle = TEXT_CONFIG.color;
    ctx.textAlign = TEXT_CONFIG.textAlign;
    ctx.textBaseline = TEXT_CONFIG.textBaseline;
    ctx.font = `${TEXT_CONFIG.fontStyle} ${TEXT_CONFIG.fontSize}px "${TEXT_CONFIG.fontFamily}", sans-serif`;
    ctx.fillText(TEXT_CONFIG.text, TEXT_CONFIG.position.x, TEXT_CONFIG.position.y);
    ctx.restore();
}
