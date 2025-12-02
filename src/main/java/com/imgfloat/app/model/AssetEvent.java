package com.imgfloat.app.model;

public class AssetEvent {
    public enum Type {
        CREATED,
        UPDATED,
        VISIBILITY,
        DELETED
    }

    private Type type;
    private String channel;
    private Asset payload;
    private String assetId;

    public static AssetEvent created(String channel, Asset asset) {
        AssetEvent event = new AssetEvent();
        event.type = Type.CREATED;
        event.channel = channel;
        event.payload = asset;
        event.assetId = asset.getId();
        return event;
    }

    public static AssetEvent updated(String channel, Asset asset) {
        AssetEvent event = new AssetEvent();
        event.type = Type.UPDATED;
        event.channel = channel;
        event.payload = asset;
        event.assetId = asset.getId();
        return event;
    }

    public static AssetEvent visibility(String channel, Asset asset) {
        AssetEvent event = new AssetEvent();
        event.type = Type.VISIBILITY;
        event.channel = channel;
        event.payload = asset;
        event.assetId = asset.getId();
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

    public Asset getPayload() {
        return payload;
    }

    public String getAssetId() {
        return assetId;
    }
}
