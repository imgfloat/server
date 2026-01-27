package dev.kruhlmann.imgfloat.model.api.response;

import dev.kruhlmann.imgfloat.model.api.request.CanvasSettingsRequest;
public class CanvasEvent {

    public enum Type {
        CANVAS,
    }

    private Type type;
    private String channel;
    private CanvasSettingsRequest payload;

    public static CanvasEvent updated(String channel, CanvasSettingsRequest payload) {
        CanvasEvent event = new CanvasEvent();
        event.type = Type.CANVAS;
        event.channel = channel;
        event.payload = payload;
        return event;
    }

    public Type getType() {
        return type;
    }

    public String getChannel() {
        return channel;
    }

    public CanvasSettingsRequest getPayload() {
        return payload;
    }
}
