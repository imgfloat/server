package com.imgfloat.app.model;

public class ImageEvent {
    public enum Type {
        CREATED,
        UPDATED,
        VISIBILITY,
        DELETED
    }

    private Type type;
    private String channel;
    private ImageLayer payload;
    private String imageId;

    public static ImageEvent created(String channel, ImageLayer layer) {
        ImageEvent event = new ImageEvent();
        event.type = Type.CREATED;
        event.channel = channel;
        event.payload = layer;
        event.imageId = layer.getId();
        return event;
    }

    public static ImageEvent updated(String channel, ImageLayer layer) {
        ImageEvent event = new ImageEvent();
        event.type = Type.UPDATED;
        event.channel = channel;
        event.payload = layer;
        event.imageId = layer.getId();
        return event;
    }

    public static ImageEvent visibility(String channel, ImageLayer layer) {
        ImageEvent event = new ImageEvent();
        event.type = Type.VISIBILITY;
        event.channel = channel;
        event.payload = layer;
        event.imageId = layer.getId();
        return event;
    }

    public static ImageEvent deleted(String channel, String imageId) {
        ImageEvent event = new ImageEvent();
        event.type = Type.DELETED;
        event.channel = channel;
        event.imageId = imageId;
        return event;
    }

    public Type getType() {
        return type;
    }

    public String getChannel() {
        return channel;
    }

    public ImageLayer getPayload() {
        return payload;
    }

    public String getImageId() {
        return imageId;
    }
}
