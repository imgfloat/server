package dev.kruhlmann.imgfloat.model.db.imgfloat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "playlist_tracks")
public class PlaylistTrack {

    @Id
    private String id;

    @Column(name = "playlist_id", nullable = false)
    private String playlistId;

    @Column(name = "audio_asset_id", nullable = false)
    private String audioAssetId;

    @Column(name = "track_order", nullable = false)
    private int trackOrder;

    public PlaylistTrack() {}

    public PlaylistTrack(String playlistId, String audioAssetId, int trackOrder) {
        this.id = UUID.randomUUID().toString();
        this.playlistId = playlistId;
        this.audioAssetId = audioAssetId;
        this.trackOrder = trackOrder;
    }

    @PrePersist
    private void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public String getPlaylistId() { return playlistId; }
    public String getAudioAssetId() { return audioAssetId; }
    public int getTrackOrder() { return trackOrder; }
    public void setTrackOrder(int trackOrder) { this.trackOrder = trackOrder; }
}
