package dev.kruhlmann.imgfloat.model;

/**
 * Represents a partial update for an {@link Asset}. Only the fields that changed
 * for a given operation are populated to reduce payload sizes sent over WebSocket.
 */
public record AssetPatch(
        String id,
        Double x,
        Double y,
        Double width,
        Double height,
        Double rotation,
        Double speed,
        Boolean muted,
        Integer zIndex,
        Boolean hidden,
        Boolean audioLoop,
        Integer audioDelayMillis,
        Double audioSpeed,
        Double audioPitch,
        Double audioVolume
) {
    public static TransformSnapshot capture(Asset asset) {
        return new TransformSnapshot(
                asset.getX(),
                asset.getY(),
                asset.getWidth(),
                asset.getHeight(),
                asset.getRotation(),
                asset.getSpeed(),
                asset.isMuted(),
                asset.getZIndex(),
                asset.isAudioLoop(),
                asset.getAudioDelayMillis(),
                asset.getAudioSpeed(),
                asset.getAudioPitch(),
                asset.getAudioVolume()
        );
    }

    /**
     * Produces a minimal patch from a transform operation. Only fields that changed and were part of
     * the incoming request are populated to keep WebSocket payloads small.
     */
    public static AssetPatch fromTransform(TransformSnapshot before, Asset asset, TransformRequest request) {
        return new AssetPatch(
                asset.getId(),
                changed(before.x(), asset.getX()),
                changed(before.y(), asset.getY()),
                changed(before.width(), asset.getWidth()),
                changed(before.height(), asset.getHeight()),
                changed(before.rotation(), asset.getRotation()),
                request.getSpeed() != null ? changed(before.speed(), asset.getSpeed()) : null,
                request.getMuted() != null ? changed(before.muted(), asset.isMuted()) : null,
                request.getZIndex() != null ? changed(before.zIndex(), asset.getZIndex()) : null,
                null,
                request.getAudioLoop() != null ? changed(before.audioLoop(), asset.isAudioLoop()) : null,
                request.getAudioDelayMillis() != null ? changed(before.audioDelayMillis(), asset.getAudioDelayMillis()) : null,
                request.getAudioSpeed() != null ? changed(before.audioSpeed(), asset.getAudioSpeed()) : null,
                request.getAudioPitch() != null ? changed(before.audioPitch(), asset.getAudioPitch()) : null,
                request.getAudioVolume() != null ? changed(before.audioVolume(), asset.getAudioVolume()) : null
        );
    }

    public static AssetPatch fromVisibility(Asset asset) {
        return new AssetPatch(
                asset.getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                asset.isHidden(),
                null,
                null,
                null,
                null,
                null
        );
    }

    private static Double changed(double before, double after) {
        return Double.compare(before, after) == 0 ? null : after;
    }

    private static Integer changed(int before, int after) {
        return before == after ? null : after;
    }

    private static Boolean changed(boolean before, boolean after) {
        return before == after ? null : after;
    }

    public record TransformSnapshot(
            double x,
            double y,
            double width,
            double height,
            double rotation,
            double speed,
            boolean muted,
            int zIndex,
            boolean audioLoop,
            int audioDelayMillis,
            double audioSpeed,
            double audioPitch,
            double audioVolume
    ) { }
}
