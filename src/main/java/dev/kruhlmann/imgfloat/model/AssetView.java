package dev.kruhlmann.imgfloat.model;

import java.time.Instant;
import java.util.List;

public record AssetView(
    String id,
    String broadcaster,
    String name,
    String url,
    String previewUrl,
    double x,
    double y,
    double width,
    double height,
    double rotation,
    Double speed,
    Boolean muted,
    String mediaType,
    String originalMediaType,
    AssetType assetType,
    List<ScriptAssetAttachmentView> scriptAttachments,
    Integer zIndex,
    Boolean audioLoop,
    Integer audioDelayMillis,
    Double audioSpeed,
    Double audioPitch,
    Double audioVolume,
    boolean hidden,
    boolean hasPreview,
    Instant createdAt,
    Instant updatedAt
) {
    public static AssetView fromVisual(String broadcaster, Asset asset, VisualAsset visual) {
        boolean hasPreview = visual.getPreview() != null && !visual.getPreview().isBlank();
        return new AssetView(
            asset.getId(),
            asset.getBroadcaster(),
            visual.getName(),
            "/api/channels/" + broadcaster + "/assets/" + asset.getId() + "/content",
            hasPreview ? "/api/channels/" + broadcaster + "/assets/" + asset.getId() + "/preview" : null,
            visual.getX(),
            visual.getY(),
            visual.getWidth(),
            visual.getHeight(),
            visual.getRotation(),
            visual.getSpeed(),
            visual.isMuted(),
            visual.getMediaType(),
            visual.getOriginalMediaType(),
            asset.getAssetType(),
            null,
            visual.getZIndex(),
            null,
            null,
            null,
            null,
            visual.getAudioVolume(),
            visual.isHidden(),
            hasPreview,
            asset.getCreatedAt(),
            asset.getUpdatedAt()
        );
    }

    public static AssetView fromAudio(String broadcaster, Asset asset, AudioAsset audio) {
        return new AssetView(
            asset.getId(),
            asset.getBroadcaster(),
            audio.getName(),
            "/api/channels/" + broadcaster + "/assets/" + asset.getId() + "/content",
            null,
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            audio.getMediaType(),
            audio.getOriginalMediaType(),
            asset.getAssetType(),
            null,
            null,
            audio.isAudioLoop(),
            audio.getAudioDelayMillis(),
            audio.getAudioSpeed(),
            audio.getAudioPitch(),
            audio.getAudioVolume(),
            audio.isHidden(),
            false,
            asset.getCreatedAt(),
            asset.getUpdatedAt()
        );
    }

    public static AssetView fromScript(String broadcaster, Asset asset, ScriptAsset script) {
        return new AssetView(
            asset.getId(),
            asset.getBroadcaster(),
            script.getName(),
            "/api/channels/" + broadcaster + "/assets/" + asset.getId() + "/content",
            null,
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            script.getMediaType(),
            script.getOriginalMediaType(),
            asset.getAssetType(),
            script.getAttachments(),
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            false,
            asset.getCreatedAt(),
            asset.getUpdatedAt()
        );
    }
}
