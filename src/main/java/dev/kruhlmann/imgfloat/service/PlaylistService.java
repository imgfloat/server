package dev.kruhlmann.imgfloat.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import dev.kruhlmann.imgfloat.model.api.response.ActivePlaylistState;
import dev.kruhlmann.imgfloat.model.api.response.PlaylistEvent;
import dev.kruhlmann.imgfloat.model.api.response.PlaylistTrackView;
import dev.kruhlmann.imgfloat.model.api.response.PlaylistView;
import dev.kruhlmann.imgfloat.model.db.imgfloat.AudioAsset;
import dev.kruhlmann.imgfloat.model.db.imgfloat.Channel;
import dev.kruhlmann.imgfloat.model.db.imgfloat.Playlist;
import dev.kruhlmann.imgfloat.model.db.imgfloat.PlaylistTrack;
import dev.kruhlmann.imgfloat.repository.AudioAssetRepository;
import dev.kruhlmann.imgfloat.repository.ChannelRepository;
import dev.kruhlmann.imgfloat.repository.PlaylistRepository;
import dev.kruhlmann.imgfloat.repository.PlaylistTrackRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlaylistService {

    private static final Logger LOG = LoggerFactory.getLogger(PlaylistService.class);

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final AudioAssetRepository audioAssetRepository;
    private final ChannelRepository channelRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public PlaylistService(
        PlaylistRepository playlistRepository,
        PlaylistTrackRepository playlistTrackRepository,
        AudioAssetRepository audioAssetRepository,
        ChannelRepository channelRepository,
        SimpMessagingTemplate messagingTemplate
    ) {
        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
        this.audioAssetRepository = audioAssetRepository;
        this.channelRepository = channelRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PlaylistView> listPlaylists(String broadcaster) {
        List<Playlist> playlists = playlistRepository.findAllByBroadcasterOrderByCreatedAtAsc(normalize(broadcaster));
        return playlists.stream().map(p -> toView(p, loadTracks(p.getId()))).toList();
    }

    @Transactional
    public PlaylistView createPlaylist(String broadcaster, String name) {
        Playlist playlist = new Playlist(normalize(broadcaster), name.trim());
        playlistRepository.save(playlist);
        PlaylistView view = toView(playlist, List.of());
        publish(broadcaster, PlaylistEvent.created(normalize(broadcaster), view));
        return view;
    }

    @Transactional
    public PlaylistView renamePlaylist(String broadcaster, String playlistId, String name) {
        Playlist playlist = requirePlaylist(broadcaster, playlistId);
        playlist.setName(name.trim());
        playlistRepository.save(playlist);
        List<PlaylistTrackView> tracks = loadTracks(playlistId);
        PlaylistView view = toView(playlist, tracks);
        publish(broadcaster, PlaylistEvent.updated(normalize(broadcaster), view));
        return view;
    }

    @Transactional
    public void deletePlaylist(String broadcaster, String playlistId) {
        Playlist playlist = requirePlaylist(broadcaster, playlistId);
        channelRepository.findById(normalize(broadcaster)).ifPresent(channel -> {
            if (playlistId.equals(channel.getActivePlaylistId())) {
                channel.setActivePlaylistId(null);
            }
            // Clear playback state if this playlist was playing
            if (playlistId.equals(channel.getActivePlaylistId())
                    || (channel.getPlaylistCurrentTrackId() != null
                        && channel.isPlaylistIsPlaying())) {
                clearPlaybackState(channel);
            }
            channelRepository.save(channel);
        });
        playlistTrackRepository.deleteAllByPlaylistId(playlistId);
        playlistRepository.delete(playlist);
        publish(broadcaster, PlaylistEvent.deleted(normalize(broadcaster), playlistId));
    }

    // ── Tracks ────────────────────────────────────────────────────────────

    @Transactional
    public PlaylistView addTrack(String broadcaster, String playlistId, String audioAssetId) {
        requirePlaylist(broadcaster, playlistId);
        audioAssetRepository.findById(audioAssetId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Audio asset not found"));
        int nextOrder = playlistTrackRepository.countByPlaylistId(playlistId);
        PlaylistTrack track = new PlaylistTrack(playlistId, audioAssetId, nextOrder);
        playlistTrackRepository.save(track);
        return refreshAndPublish(broadcaster, playlistId);
    }

    @Transactional
    public PlaylistView removeTrack(String broadcaster, String playlistId, String trackId) {
        requirePlaylist(broadcaster, playlistId);
        PlaylistTrack track = playlistTrackRepository.findById(trackId)
            .filter(t -> t.getPlaylistId().equals(playlistId))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Track not found"));
        playlistTrackRepository.delete(track);
        // Re-number remaining tracks
        List<PlaylistTrack> remaining = playlistTrackRepository.findAllByPlaylistIdOrderByTrackOrderAsc(playlistId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setTrackOrder(i);
        }
        playlistTrackRepository.saveAll(remaining);
        // Clear playback state if the removed track was the current one
        channelRepository.findById(normalize(broadcaster)).ifPresent(channel -> {
            if (trackId.equals(channel.getPlaylistCurrentTrackId())) {
                clearPlaybackState(channel);
                channelRepository.save(channel);
            }
        });
        return refreshAndPublish(broadcaster, playlistId);
    }

    @Transactional
    public PlaylistView reorderTracks(String broadcaster, String playlistId, List<String> trackIds) {
        requirePlaylist(broadcaster, playlistId);
        List<PlaylistTrack> tracks = playlistTrackRepository.findAllByPlaylistIdOrderByTrackOrderAsc(playlistId);
        Map<String, PlaylistTrack> byId = tracks.stream().collect(Collectors.toMap(PlaylistTrack::getId, t -> t));
        if (trackIds.size() != tracks.size() || !byId.keySet().containsAll(trackIds)) {
            throw new ResponseStatusException(BAD_REQUEST, "trackIds must contain exactly the current track IDs");
        }
        for (int i = 0; i < trackIds.size(); i++) {
            byId.get(trackIds.get(i)).setTrackOrder(i);
        }
        playlistTrackRepository.saveAll(byId.values());
        return refreshAndPublish(broadcaster, playlistId);
    }

    // ── Active playlist ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<ActivePlaylistState> getActivePlaylistState(String broadcaster) {
        Channel channel = channelRepository.findById(normalize(broadcaster)).orElse(null);
        if (channel == null || channel.getActivePlaylistId() == null) {
            return Optional.empty();
        }
        return playlistRepository.findByIdAndBroadcaster(channel.getActivePlaylistId(), normalize(broadcaster))
            .map(p -> {
                List<PlaylistTrackView> tracks = loadTracks(p.getId());
                return new ActivePlaylistState(
                    p.getId(),
                    p.getName(),
                    tracks,
                    channel.getPlaylistCurrentTrackId(),
                    channel.isPlaylistIsPlaying(),
                    channel.isPlaylistIsPaused(),
                    channel.getPlaylistTrackPosition()
                );
            });
    }

    @Transactional
    public Optional<PlaylistView> selectPlaylist(String broadcaster, String playlistId) {
        Channel channel = channelRepository.findById(normalize(broadcaster))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Channel not found"));

        PlaylistView view = null;
        if (playlistId != null) {
            Playlist playlist = requirePlaylist(broadcaster, playlistId);
            view = toView(playlist, loadTracks(playlistId));
        }
        channel.setActivePlaylistId(playlistId);
        clearPlaybackState(channel);
        channelRepository.save(channel);
        publish(broadcaster, PlaylistEvent.selected(normalize(broadcaster), view));
        return Optional.ofNullable(view);
    }

    // ── Playback commands ─────────────────────────────────────────────────

    @Transactional
    public void commandPlay(String broadcaster, String playlistId, String trackId) {
        requirePlaylist(broadcaster, playlistId);
        // If no trackId specified, resolve the first track
        String resolvedTrackId = trackId;
        if (resolvedTrackId == null) {
            List<PlaylistTrack> tracks = playlistTrackRepository.findAllByPlaylistIdOrderByTrackOrderAsc(playlistId);
            if (!tracks.isEmpty()) resolvedTrackId = tracks.get(0).getId();
        }
        channelRepository.findById(normalize(broadcaster)).ifPresent(channel -> {
            channel.setPlaylistCurrentTrackId(resolvedTrackId);
            channel.setPlaylistIsPlaying(true);
            channel.setPlaylistIsPaused(false);
            channel.setPlaylistTrackPosition(0.0);
            channelRepository.save(channel);
        });
        publish(broadcaster, PlaylistEvent.play(normalize(broadcaster), playlistId, resolvedTrackId));
    }

    @Transactional
    public void commandPause(String broadcaster, String playlistId) {
        requirePlaylist(broadcaster, playlistId);
        channelRepository.findById(normalize(broadcaster)).ifPresent(channel -> {
            channel.setPlaylistIsPaused(true);
            channelRepository.save(channel);
        });
        publish(broadcaster, PlaylistEvent.pause(normalize(broadcaster), playlistId));
    }

    @Transactional
    public void commandNext(String broadcaster, String playlistId, String currentTrackId) {
        requirePlaylist(broadcaster, playlistId);
        List<PlaylistTrack> tracks = playlistTrackRepository.findAllByPlaylistIdOrderByTrackOrderAsc(playlistId);
        if (tracks.isEmpty()) return;
        String nextTrackId = null;
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).getId().equals(currentTrackId) && i + 1 < tracks.size()) {
                nextTrackId = tracks.get(i + 1).getId();
                break;
            }
        }
        if (nextTrackId != null) {
            final String resolvedNext = nextTrackId;
            channelRepository.findById(normalize(broadcaster)).ifPresent(channel -> {
                channel.setPlaylistCurrentTrackId(resolvedNext);
                channel.setPlaylistIsPlaying(true);
                channel.setPlaylistIsPaused(false);
                channel.setPlaylistTrackPosition(0.0);
                channelRepository.save(channel);
            });
            publish(broadcaster, PlaylistEvent.next(normalize(broadcaster), playlistId, nextTrackId));
        } else {
            channelRepository.findById(normalize(broadcaster)).ifPresent(channel -> {
                clearPlaybackState(channel);
                channelRepository.save(channel);
            });
            publish(broadcaster, PlaylistEvent.ended(normalize(broadcaster), playlistId));
        }
    }

    @Transactional
    public void commandPrev(String broadcaster, String playlistId, String currentTrackId) {
        requirePlaylist(broadcaster, playlistId);
        List<PlaylistTrack> tracks = playlistTrackRepository.findAllByPlaylistIdOrderByTrackOrderAsc(playlistId);
        if (tracks.isEmpty()) return;
        String prevTrackId = null;
        for (int i = tracks.size() - 1; i >= 0; i--) {
            if (tracks.get(i).getId().equals(currentTrackId) && i - 1 >= 0) {
                prevTrackId = tracks.get(i - 1).getId();
                break;
            }
        }
        // null means restart current — persist the same track, reset position
        final String resolvedTrackId = prevTrackId != null ? prevTrackId : currentTrackId;
        channelRepository.findById(normalize(broadcaster)).ifPresent(channel -> {
            channel.setPlaylistCurrentTrackId(resolvedTrackId);
            channel.setPlaylistIsPlaying(true);
            channel.setPlaylistIsPaused(false);
            channel.setPlaylistTrackPosition(0.0);
            channelRepository.save(channel);
        });
        publish(broadcaster, PlaylistEvent.prev(normalize(broadcaster), playlistId, prevTrackId));
    }

    @Transactional
    public void commandTrackEnded(String broadcaster, String playlistId, String finishedTrackId) {
        commandNext(broadcaster, playlistId, finishedTrackId);
    }

    // ── Position reporting ────────────────────────────────────────────────

    @Transactional
    public void reportPosition(String broadcaster, String playlistId, String trackId, double position) {
        channelRepository.findById(normalize(broadcaster)).ifPresent(channel -> {
            // Only persist if this is still the active playlist and we're playing
            if (playlistId.equals(channel.getActivePlaylistId())
                    && channel.isPlaylistIsPlaying()
                    && !channel.isPlaylistIsPaused()
                    && trackId.equals(channel.getPlaylistCurrentTrackId())) {
                channel.setPlaylistTrackPosition(position);
                channelRepository.save(channel);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void clearPlaybackState(Channel channel) {
        channel.setPlaylistCurrentTrackId(null);
        channel.setPlaylistIsPlaying(false);
        channel.setPlaylistIsPaused(false);
        channel.setPlaylistTrackPosition(0.0);
    }

    private Playlist requirePlaylist(String broadcaster, String playlistId) {
        return playlistRepository.findByIdAndBroadcaster(playlistId, normalize(broadcaster))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Playlist not found"));
    }

    private List<PlaylistTrackView> loadTracks(String playlistId) {
        List<PlaylistTrack> tracks = playlistTrackRepository.findAllByPlaylistIdOrderByTrackOrderAsc(playlistId);
        List<String> audioIds = tracks.stream().map(PlaylistTrack::getAudioAssetId).toList();
        Map<String, String> nameById = audioAssetRepository.findAllById(audioIds).stream()
            .collect(Collectors.toMap(AudioAsset::getId, AudioAsset::getName));
        return tracks.stream()
            .map(t -> new PlaylistTrackView(t.getId(), t.getAudioAssetId(),
                nameById.getOrDefault(t.getAudioAssetId(), t.getAudioAssetId()), t.getTrackOrder()))
            .toList();
    }

    private PlaylistView toView(Playlist playlist, List<PlaylistTrackView> tracks) {
        return new PlaylistView(playlist.getId(), playlist.getName(), tracks);
    }

    private PlaylistView refreshAndPublish(String broadcaster, String playlistId) {
        Playlist playlist = requirePlaylist(broadcaster, playlistId);
        List<PlaylistTrackView> tracks = loadTracks(playlistId);
        PlaylistView view = toView(playlist, tracks);
        publish(broadcaster, PlaylistEvent.updated(normalize(broadcaster), view));
        return view;
    }

    private void publish(String broadcaster, PlaylistEvent event) {
        messagingTemplate.convertAndSend("/topic/channel/" + normalize(broadcaster), event);
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
