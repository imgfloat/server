package dev.kruhlmann.imgfloat.model.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.kruhlmann.imgfloat.model.api.request.TransformRequest;
import dev.kruhlmann.imgfloat.model.db.imgfloat.AudioAsset;
import dev.kruhlmann.imgfloat.model.db.imgfloat.VisualAsset;

/**
 * Represents a partial update for an {@link dev.kruhlmann.imgfloat.model.db.imgfloat.Asset}. Only the fields that changed
 * for a given operation are populated to reduce payload sizes sent over WebSocket.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssetPatch(
    String id,
    Double x,
    Double y,
    Double width,
    Double height,
    Double rotation,
    Double speed,
    Boolean muted,
    Integer order,
    Boolean hidden,
    Boolean audioLoop,
    Integer audioDelayMillis,
    Double audioSpeed,
    Double audioPitch,
    Double audioVolume
) {
    /**
     * Produces a minimal patch from a visual transform operation.
     */
    public static AssetPatch fromVisualTransform(VisualSnapshot before, VisualAsset asset, TransformRequest request) {
        return new AssetPatch(
            asset.getId(),
            request.getX() != null ? changed(before.x(), asset.getX()) : null,
            request.getY() != null ? changed(before.y(), asset.getY()) : null,
            request.getWidth() != null ? changed(before.width(), asset.getWidth()) : null,
            request.getHeight() != null ? changed(before.height(), asset.getHeight()) : null,
            request.getRotation() != null ? changed(before.rotation(), asset.getRotation()) : null,
            request.getSpeed() != null ? changed(before.speed(), asset.getSpeed()) : null,
            request.getMuted() != null ? changed(before.muted(), asset.isMuted()) : null,
            request.getOrder() != null ? changed(before.order(), request.getOrder()) : null,
            null,
            null,
            null,
            null,
            null,
            request.getAudioVolume() != null ? changed(before.audioVolume(), asset.getAudioVolume()) : null
        );
    }

    /**
     * Produces a minimal patch from an audio update operation.
     */
    public static AssetPatch fromAudioTransform(AudioSnapshot before, AudioAsset asset, TransformRequest request) {
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
            null,
            request.getAudioLoop() != null ? changed(before.audioLoop(), asset.isAudioLoop()) : null,
            request.getAudioDelayMillis() != null
                ? changed(before.audioDelayMillis(), asset.getAudioDelayMillis())
                : null,
            request.getAudioSpeed() != null ? changed(before.audioSpeed(), asset.getAudioSpeed()) : null,
            request.getAudioPitch() != null ? changed(before.audioPitch(), asset.getAudioPitch()) : null,
            request.getAudioVolume() != null ? changed(before.audioVolume(), asset.getAudioVolume()) : null
        );
    }

    public static AssetPatch fromVisibility(String assetId, boolean hidden) {
        return new AssetPatch(
            assetId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            hidden,
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

    public record VisualSnapshot(
        double x,
        double y,
        double width,
        double height,
        double rotation,
        double speed,
        boolean muted,
        int order,
        double audioVolume
    ) {}

    public record AudioSnapshot(
        boolean audioLoop,
        int audioDelayMillis,
        double audioSpeed,
        double audioPitch,
        double audioVolume
    ) {}
}
