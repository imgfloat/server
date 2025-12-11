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
    public static AssetPatch fromTransform(Asset asset) {
        return new AssetPatch(
                asset.getId(),
                asset.getX(),
                asset.getY(),
                asset.getWidth(),
                asset.getHeight(),
                asset.getRotation(),
                asset.getSpeed(),
                asset.isMuted(),
                asset.getZIndex(),
                null,
                asset.isAudioLoop(),
                asset.getAudioDelayMillis(),
                asset.getAudioSpeed(),
                asset.getAudioPitch(),
                asset.getAudioVolume()
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
}
