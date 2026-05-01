/**
 * Now Playing — minimal playlist pill overlay.
 *
 * Displays a pill in the bottom-right corner of the broadcast canvas
 * showing the current track name when a playlist is active and playing.
 * Fades in when playback starts and fades out when paused or stopped.
 *
 * Context used: context.playlist, context.width, context.height
 */

exports.init = function (context, state) {
    state.opacity = 0;
    state.targetOpacity = 0;
    state.lastTrackName = null;
};

exports.tick = function (context, state) {
    const { ctx, width, height, deltaMs, playlist } = context;

    const isPlaying = playlist && playlist.active && !playlist.paused && playlist.trackName;
    state.targetOpacity = isPlaying ? 1 : 0;

    // Smooth fade
    const speed = (deltaMs / 1000) * 3; // ~333ms transition
    if (state.opacity < state.targetOpacity) {
        state.opacity = Math.min(state.targetOpacity, state.opacity + speed);
    } else if (state.opacity > state.targetOpacity) {
        state.opacity = Math.max(state.targetOpacity, state.opacity - speed);
    }

    if (state.opacity <= 0.01) {
        return;
    }

    const trackName = playlist?.trackName ?? "";

    ctx.save();
    ctx.globalAlpha = state.opacity;

    const fontSize = Math.round(height * 0.022);
    ctx.font = `600 ${fontSize}px system-ui, sans-serif`;

    const iconGlyph = "\u266A"; // ♪
    const text = `${iconGlyph}  ${trackName}`;
    const paddingH = fontSize * 0.9;
    const paddingV = fontSize * 0.55;
    const pillWidth = Math.min(ctx.measureText(text).width + paddingH * 2, width * 0.38);
    const pillHeight = fontSize + paddingV * 2;
    const margin = Math.round(height * 0.025);
    const x = width - pillWidth - margin;
    const y = height - pillHeight - margin;
    const radius = pillHeight / 2;

    // Pill background
    ctx.fillStyle = "rgba(0, 0, 0, 0.60)";
    ctx.beginPath();
    ctx.roundRect(x, y, pillWidth, pillHeight, radius);
    ctx.fill();

    // Clip text to pill
    ctx.beginPath();
    ctx.roundRect(x + paddingH, y, pillWidth - paddingH * 2, pillHeight, 0);
    ctx.clip();

    // Draw icon in accent colour
    ctx.fillStyle = "#a78bfa"; // purple accent
    ctx.fillText(iconGlyph, x + paddingH, y + paddingV + fontSize * 0.82);

    // Draw track name in white
    const iconWidth = ctx.measureText(iconGlyph + "  ").width;
    ctx.fillStyle = "#ffffff";
    ctx.fillText(trackName, x + paddingH + iconWidth, y + paddingV + fontSize * 0.82);

    ctx.restore();
};
