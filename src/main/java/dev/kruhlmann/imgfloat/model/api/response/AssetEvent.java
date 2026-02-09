package dev.kruhlmann.imgfloat.model.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssetEvent {

    public enum Type {
        CREATED,
        UPDATED,
        VISIBILITY,
        PLAY,
        PREVIEW,
        DELETED,
    }

    private Type type;
    private String channel;
    private AssetView payload;
    private String assetId;
    private Boolean play;
    private AssetPatch patch;

    public static AssetEvent created(String channel, AssetView asset) {
        AssetEvent event = new AssetEvent();
        event.type = Type.CREATED;
        event.channel = channel;
        event.payload = asset;
        event.assetId = asset.id();
        return event;
    }

    public static AssetEvent updated(String channel, AssetPatch patch) {
        AssetEvent event = new AssetEvent();
        event.type = Type.UPDATED;
        event.channel = channel;
        event.assetId = patch.id();
        event.patch = patch;
        return event;
    }

    public static AssetEvent updated(String channel, AssetView asset) {
        AssetEvent event = new AssetEvent();
        event.type = Type.UPDATED;
        event.channel = channel;
        event.payload = asset;
        event.assetId = asset.id();
        return event;
    }

    public static AssetEvent play(String channel, AssetView asset, boolean play) {
        AssetEvent event = new AssetEvent();
        event.type = Type.PLAY;
        event.channel = channel;
        event.payload = asset;
        event.assetId = asset.id();
        event.play = play;
        return event;
    }

    public static AssetEvent visibility(String channel, AssetPatch patch, AssetView asset) {
        AssetEvent event = new AssetEvent();
        event.type = Type.VISIBILITY;
        event.channel = channel;
        event.patch = patch;
        event.assetId = patch.id();
        event.payload = asset;
        return event;
    }

    public static AssetEvent preview(String channel, String assetId, AssetPatch patch) {
        AssetEvent event = new AssetEvent();
        event.type = Type.PREVIEW;
        event.channel = channel;
        event.patch = patch;
        event.assetId = assetId;
        return event;
    }

    public static AssetEvent deleted(String channel, String assetId) {
        AssetEvent event = new AssetEvent();
        event.type = Type.DELETED;
        event.channel = channel;
        event.assetId = assetId;
        return event;
    }

    public Type getType() {
        return type;
    }

    public String getChannel() {
        return channel;
    }

    public AssetView getPayload() {
        return payload;
    }

    public String getAssetId() {
        return assetId;
    }

    public Boolean getPlay() {
        return play;
    }

    public AssetPatch getPatch() {
        return patch;
    }
}
