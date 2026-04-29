package dev.kruhlmann.imgfloat.model.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlaylistEvent {

    public enum Type {
        PLAYLIST_CREATED,
        PLAYLIST_UPDATED,
        PLAYLIST_DELETED,
        PLAYLIST_SELECTED,
        PLAYLIST_PLAY,
        PLAYLIST_PAUSE,
        PLAYLIST_NEXT,
        PLAYLIST_PREV,
        PLAYLIST_ENDED,
    }

    private Type type;
    private String channel;
    private String playlistId;
    private String trackId;
    private PlaylistView payload;

    private PlaylistEvent() {}

    public static PlaylistEvent created(String channel, PlaylistView view) {
        PlaylistEvent e = new PlaylistEvent();
        e.type = Type.PLAYLIST_CREATED;
        e.channel = channel;
        e.playlistId = view.id();
        e.payload = view;
        return e;
    }

    public static PlaylistEvent updated(String channel, PlaylistView view) {
        PlaylistEvent e = new PlaylistEvent();
        e.type = Type.PLAYLIST_UPDATED;
        e.channel = channel;
        e.playlistId = view.id();
        e.payload = view;
        return e;
    }

    public static PlaylistEvent deleted(String channel, String playlistId) {
        PlaylistEvent e = new PlaylistEvent();
        e.type = Type.PLAYLIST_DELETED;
        e.channel = channel;
        e.playlistId = playlistId;
        return e;
    }

    public static PlaylistEvent selected(String channel, PlaylistView view) {
        PlaylistEvent e = new PlaylistEvent();
        e.type = Type.PLAYLIST_SELECTED;
        e.channel = channel;
        e.playlistId = view != null ? view.id() : null;
        e.payload = view;
        return e;
    }

    public static PlaylistEvent play(String channel, String playlistId, String trackId) {
        PlaylistEvent e = new PlaylistEvent();
        e.type = Type.PLAYLIST_PLAY;
        e.channel = channel;
        e.playlistId = playlistId;
        e.trackId = trackId;
        return e;
    }

    public static PlaylistEvent pause(String channel, String playlistId) {
        PlaylistEvent e = new PlaylistEvent();
        e.type = Type.PLAYLIST_PAUSE;
        e.channel = channel;
        e.playlistId = playlistId;
        return e;
    }

    public static PlaylistEvent next(String channel, String playlistId, String trackId) {
        PlaylistEvent e = new PlaylistEvent();
        e.type = Type.PLAYLIST_NEXT;
        e.channel = channel;
        e.playlistId = playlistId;
        e.trackId = trackId;
        return e;
    }

    public static PlaylistEvent prev(String channel, String playlistId, String trackId) {
        PlaylistEvent e = new PlaylistEvent();
        e.type = Type.PLAYLIST_PREV;
        e.channel = channel;
        e.playlistId = playlistId;
        e.trackId = trackId;
        return e;
    }

    public static PlaylistEvent ended(String channel, String playlistId) {
        PlaylistEvent e = new PlaylistEvent();
        e.type = Type.PLAYLIST_ENDED;
        e.channel = channel;
        e.playlistId = playlistId;
        return e;
    }

    public Type getType() { return type; }
    public String getChannel() { return channel; }
    public String getPlaylistId() { return playlistId; }
    public String getTrackId() { return trackId; }
    public PlaylistView getPayload() { return payload; }
}
