package dev.kruhlmann.imgfloat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kruhlmann.imgfloat.model.api.response.ActivePlaylistState;
import dev.kruhlmann.imgfloat.model.api.response.PlaylistEvent;
import dev.kruhlmann.imgfloat.model.api.response.PlaylistView;
import dev.kruhlmann.imgfloat.model.db.imgfloat.AudioAsset;
import dev.kruhlmann.imgfloat.model.db.imgfloat.Channel;
import dev.kruhlmann.imgfloat.model.db.imgfloat.Playlist;
import dev.kruhlmann.imgfloat.model.db.imgfloat.PlaylistTrack;
import dev.kruhlmann.imgfloat.repository.AudioAssetRepository;
import dev.kruhlmann.imgfloat.repository.ChannelRepository;
import dev.kruhlmann.imgfloat.repository.PlaylistRepository;
import dev.kruhlmann.imgfloat.repository.PlaylistTrackRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

class PlaylistServiceTest {

    private PlaylistRepository playlistRepository;
    private PlaylistTrackRepository playlistTrackRepository;
    private AudioAssetRepository audioAssetRepository;
    private ChannelRepository channelRepository;
    private SimpMessagingTemplate messagingTemplate;
    private PlaylistService service;

    // In-memory stores so we can inspect saved state
    private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Playlist> playlists = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<PlaylistTrack>> tracksByPlaylist = new ConcurrentHashMap<>();

    // ── Fixture helpers ───────────────────────────────────────────────────

    private Channel channel(String broadcaster) {
        return channels.computeIfAbsent(broadcaster.toLowerCase(), Channel::new);
    }

    private Playlist playlist(String id, String broadcaster, String name) {
        Playlist p = new Playlist(broadcaster.toLowerCase(), name);
        setField(p, "id", id);
        playlists.put(id, p);
        tracksByPlaylist.putIfAbsent(id, new ArrayList<>());
        return p;
    }

    private PlaylistTrack track(String id, String playlistId, String audioAssetId, int order) {
        PlaylistTrack t = new PlaylistTrack(playlistId, audioAssetId, order);
        setField(t, "id", id);
        tracksByPlaylist.computeIfAbsent(playlistId, k -> new ArrayList<>()).add(t);
        return t;
    }

    private AudioAsset audioAsset(String id, String name) {
        AudioAsset a = mock(AudioAsset.class);
        when(a.getId()).thenReturn(id);
        when(a.getName()).thenReturn(name);
        when(audioAssetRepository.findById(id)).thenReturn(Optional.of(a));
        return a;
    }

    /** Reflective field setter for entity IDs that have no public setter. */
    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> c = clazz;
        while (c != null) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            c = c.getSuperclass();
        }
        throw new NoSuchFieldException(name + " in " + clazz);
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    @BeforeEach
    void setup() {
        playlistRepository     = mock(PlaylistRepository.class);
        playlistTrackRepository = mock(PlaylistTrackRepository.class);
        audioAssetRepository   = mock(AudioAssetRepository.class);
        channelRepository      = mock(ChannelRepository.class);
        messagingTemplate      = mock(SimpMessagingTemplate.class);

        // channelRepository stubs
        when(channelRepository.findById(anyString())).thenAnswer(inv ->
            Optional.ofNullable(channels.get(inv.getArgument(0))));
        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> {
            Channel ch = inv.getArgument(0);
            channels.put(ch.getBroadcaster(), ch);
            return ch;
        });

        // playlistRepository stubs
        when(playlistRepository.findByIdAndBroadcaster(anyString(), anyString())).thenAnswer(inv ->
            Optional.ofNullable(playlists.get(inv.getArgument(0)))
                .filter(p -> p.getBroadcaster().equals(inv.getArgument(1))));
        when(playlistRepository.findAllByBroadcasterOrderByCreatedAtAsc(anyString())).thenAnswer(inv ->
            playlists.values().stream()
                .filter(p -> p.getBroadcaster().equals(inv.getArgument(0)))
                .toList());
        when(playlistRepository.save(any(Playlist.class))).thenAnswer(inv -> inv.getArgument(0));

        // playlistTrackRepository stubs
        when(playlistTrackRepository.findAllByPlaylistIdOrderByTrackOrderAsc(anyString())).thenAnswer(inv ->
            List.copyOf(tracksByPlaylist.getOrDefault(inv.getArgument(0), List.of())));
        when(playlistTrackRepository.countByPlaylistId(anyString())).thenAnswer(inv ->
            tracksByPlaylist.getOrDefault(inv.getArgument(0), List.of()).size());
        when(playlistTrackRepository.save(any(PlaylistTrack.class))).thenAnswer(inv -> {
            PlaylistTrack t = inv.getArgument(0);
            tracksByPlaylist.computeIfAbsent(t.getPlaylistId(), k -> new ArrayList<>()).add(t);
            return t;
        });
        when(playlistTrackRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playlistTrackRepository.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return tracksByPlaylist.values().stream().flatMap(List::stream)
                .filter(t -> id.equals(t.getId())).findFirst();
        });

        // audioAssetRepository default (overridden per test)
        when(audioAssetRepository.findAllById(any())).thenReturn(List.of());

        service = new PlaylistService(
            playlistRepository, playlistTrackRepository,
            audioAssetRepository, channelRepository, messagingTemplate);
    }

    // ── createPlaylist ────────────────────────────────────────────────────

    @Test
    void createPlaylistReturnsViewWithName() {
        channel("caster");
        PlaylistView view = service.createPlaylist("caster", "My Mix");
        assertThat(view.name()).isEqualTo("My Mix");
        assertThat(view.tracks()).isEmpty();
    }

    @Test
    void createPlaylistPublishesCreatedEvent() {
        channel("caster");
        service.createPlaylist("caster", "Beats");
        ArgumentCaptor<PlaylistEvent> cap = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(messagingTemplate).convertAndSend(anyString(), cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(PlaylistEvent.Type.PLAYLIST_CREATED);
    }

    @Test
    void createPlaylistTrimsName() {
        channel("caster");
        PlaylistView view = service.createPlaylist("caster", "  Trimmed  ");
        assertThat(view.name()).isEqualTo("Trimmed");
    }

    // ── renamePlaylist ────────────────────────────────────────────────────

    @Test
    void renamePlaylistUpdatesNameAndPublishesUpdatedEvent() {
        channel("caster");
        Playlist p = playlist("p1", "caster", "Old");
        PlaylistView view = service.renamePlaylist("caster", "p1", "New");
        assertThat(view.name()).isEqualTo("New");
        ArgumentCaptor<PlaylistEvent> cap = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(messagingTemplate).convertAndSend(anyString(), cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(PlaylistEvent.Type.PLAYLIST_UPDATED);
    }

    @Test
    void renamePlaylistThrowsWhenNotFound() {
        channel("caster");
        assertThatThrownBy(() -> service.renamePlaylist("caster", "missing", "X"))
            .isInstanceOf(ResponseStatusException.class);
    }

    // ── deletePlaylist ────────────────────────────────────────────────────

    @Test
    void deletePlaylistPublishesDeletedEvent() {
        Channel ch = channel("caster");
        Playlist p = playlist("p1", "caster", "Mix");
        service.deletePlaylist("caster", "p1");
        ArgumentCaptor<PlaylistEvent> cap = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(messagingTemplate).convertAndSend(anyString(), cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(PlaylistEvent.Type.PLAYLIST_DELETED);
    }

    @Test
    void deletePlaylistClearsActivePLaylistAndPlaybackStateWhenActive() {
        Channel ch = channel("caster");
        Playlist p = playlist("p1", "caster", "Mix");
        ch.setActivePlaylistId("p1");
        ch.setPlaylistCurrentTrackId("t1");
        ch.setPlaylistIsPlaying(true);
        ch.setPlaylistIsPaused(false);
        ch.setPlaylistTrackPosition(42.0);

        service.deletePlaylist("caster", "p1");

        Channel saved = channels.get("caster");
        assertThat(saved.getActivePlaylistId()).isNull();
        assertThat(saved.getPlaylistCurrentTrackId()).isNull();
        assertThat(saved.isPlaylistIsPlaying()).isFalse();
        assertThat(saved.isPlaylistIsPaused()).isFalse();
        assertThat(saved.getPlaylistTrackPosition()).isEqualTo(0.0);
    }

    @Test
    void deletePlaylistDoesNotClearPlaybackStateWhenNotActive() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Active");
        playlist("p2", "caster", "Other");
        ch.setActivePlaylistId("p1");
        ch.setPlaylistCurrentTrackId("t1");
        ch.setPlaylistIsPlaying(true);
        ch.setPlaylistTrackPosition(30.0);

        service.deletePlaylist("caster", "p2");

        Channel saved = channels.get("caster");
        assertThat(saved.getActivePlaylistId()).isEqualTo("p1");
        assertThat(saved.isPlaylistIsPlaying()).isTrue();
        assertThat(saved.getPlaylistTrackPosition()).isEqualTo(30.0);
    }

    // ── addTrack / removeTrack ────────────────────────────────────────────

    @Test
    void addTrackAppendsAndPublishesUpdatedEvent() {
        channel("caster");
        playlist("p1", "caster", "Mix");
        audioAsset("a1", "Song");

        AudioAsset a1 = mock(AudioAsset.class);
        when(a1.getId()).thenReturn("a1");
        when(a1.getName()).thenReturn("Song");
        when(audioAssetRepository.findAllById(List.of("a1"))).thenReturn(List.of(a1));

        PlaylistView view = service.addTrack("caster", "p1", "a1");
        assertThat(view.tracks()).hasSize(1);
        assertThat(view.tracks().get(0).audioAssetId()).isEqualTo("a1");

        ArgumentCaptor<PlaylistEvent> cap = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(messagingTemplate).convertAndSend(anyString(), cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(PlaylistEvent.Type.PLAYLIST_UPDATED);
    }

    @Test
    void addTrackThrowsWhenAudioAssetNotFound() {
        channel("caster");
        playlist("p1", "caster", "Mix");
        when(audioAssetRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addTrack("caster", "p1", "missing"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void removeTrackClearsPlaybackStateWhenCurrentTrack() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Mix");
        track("t1", "p1", "a1", 0);
        ch.setActivePlaylistId("p1");
        ch.setPlaylistCurrentTrackId("t1");
        ch.setPlaylistIsPlaying(true);
        ch.setPlaylistTrackPosition(15.0);

        service.removeTrack("caster", "p1", "t1");

        Channel saved = channels.get("caster");
        assertThat(saved.getPlaylistCurrentTrackId()).isNull();
        assertThat(saved.isPlaylistIsPlaying()).isFalse();
        assertThat(saved.getPlaylistTrackPosition()).isEqualTo(0.0);
    }

    @Test
    void removeTrackDoesNotClearPlaybackStateWhenDifferentTrack() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Mix");
        track("t1", "p1", "a1", 0);
        track("t2", "p1", "a2", 1);
        ch.setPlaylistCurrentTrackId("t1");
        ch.setPlaylistIsPlaying(true);
        ch.setPlaylistTrackPosition(5.0);

        service.removeTrack("caster", "p1", "t2");

        Channel saved = channels.get("caster");
        assertThat(saved.isPlaylistIsPlaying()).isTrue();
        assertThat(saved.getPlaylistCurrentTrackId()).isEqualTo("t1");
    }

    // ── selectPlaylist ────────────────────────────────────────────────────

    @Test
    void selectPlaylistSetsActiveAndPublishesSelectedEvent() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Mix");

        service.selectPlaylist("caster", "p1");

        assertThat(channels.get("caster").getActivePlaylistId()).isEqualTo("p1");
        ArgumentCaptor<PlaylistEvent> cap = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(messagingTemplate).convertAndSend(anyString(), cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(PlaylistEvent.Type.PLAYLIST_SELECTED);
    }

    @Test
    void selectPlaylistNullDeselectsAndClearsPlaybackState() {
        Channel ch = channel("caster");
        ch.setActivePlaylistId("p1");
        ch.setPlaylistCurrentTrackId("t1");
        ch.setPlaylistIsPlaying(true);
        ch.setPlaylistTrackPosition(10.0);

        service.selectPlaylist("caster", null);

        Channel saved = channels.get("caster");
        assertThat(saved.getActivePlaylistId()).isNull();
        assertThat(saved.isPlaylistIsPlaying()).isFalse();
        assertThat(saved.getPlaylistCurrentTrackId()).isNull();
    }

    @Test
    void selectPlaylistClearsPlaybackStateWhenSwitchingPlaylists() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Old");
        playlist("p2", "caster", "New");
        ch.setActivePlaylistId("p1");
        ch.setPlaylistCurrentTrackId("t1");
        ch.setPlaylistIsPlaying(true);

        service.selectPlaylist("caster", "p2");

        Channel saved = channels.get("caster");
        assertThat(saved.getActivePlaylistId()).isEqualTo("p2");
        assertThat(saved.isPlaylistIsPlaying()).isFalse();
        assertThat(saved.getPlaylistCurrentTrackId()).isNull();
    }

    // ── getActivePlaylistState ────────────────────────────────────────────

    @Test
    void getActivePlaylistStateReturnsEmptyWhenNoActive() {
        channel("caster");
        assertThat(service.getActivePlaylistState("caster")).isEmpty();
    }

    @Test
    void getActivePlaylistStateReturnsStateWithPlaybackFields() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Mix");
        ch.setActivePlaylistId("p1");
        ch.setPlaylistCurrentTrackId("t1");
        ch.setPlaylistIsPlaying(true);
        ch.setPlaylistIsPaused(false);
        ch.setPlaylistTrackPosition(37.5);

        ActivePlaylistState state = service.getActivePlaylistState("caster").orElseThrow();
        assertThat(state.id()).isEqualTo("p1");
        assertThat(state.currentTrackId()).isEqualTo("t1");
        assertThat(state.isPlaying()).isTrue();
        assertThat(state.isPaused()).isFalse();
        assertThat(state.trackPosition()).isEqualTo(37.5);
    }

    @Test
    void getActivePlaylistStateReflectsPausedState() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Mix");
        ch.setActivePlaylistId("p1");
        ch.setPlaylistIsPlaying(true);
        ch.setPlaylistIsPaused(true);

        ActivePlaylistState state = service.getActivePlaylistState("caster").orElseThrow();
        assertThat(state.isPlaying()).isTrue();
        assertThat(state.isPaused()).isTrue();
    }

    // ── commandPlay ───────────────────────────────────────────────────────

    @Test
    void commandPlayPersistsTrackAndPublishesPlayEvent() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Mix");
        track("t1", "p1", "a1", 0);

        service.commandPlay("caster", "p1", "t1");

        Channel saved = channels.get("caster");
        assertThat(saved.getPlaylistCurrentTrackId()).isEqualTo("t1");
        assertThat(saved.isPlaylistIsPlaying()).isTrue();
        assertThat(saved.isPlaylistIsPaused()).isFalse();
        assertThat(saved.getPlaylistTrackPosition()).isEqualTo(0.0);

        ArgumentCaptor<PlaylistEvent> cap = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(messagingTemplate).convertAndSend(anyString(), cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(PlaylistEvent.Type.PLAYLIST_PLAY);
        assertThat(cap.getValue().getTrackId()).isEqualTo("t1");
    }

    @Test
    void commandPlayWithNullTrackIdResolvesToFirstTrack() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Mix");
        track("t1", "p1", "a1", 0);
        track("t2", "p1", "a2", 1);

        service.commandPlay("caster", "p1", null);

        assertThat(channels.get("caster").getPlaylistCurrentTrackId()).isEqualTo("t1");
        ArgumentCaptor<PlaylistEvent> cap = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(messagingTemplate).convertAndSend(anyString(), cap.capture());
        assertThat(cap.getValue().getTrackId()).isEqualTo("t1");
    }

    @Test
    void commandPlayResetsPositionToZero() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Mix");
        track("t1", "p1", "a1", 0);
        ch.setPlaylistTrackPosition(99.0);

        service.commandPlay("caster", "p1", "t1");

        assertThat(channels.get("caster").getPlaylistTrackPosition()).isEqualTo(0.0);
    }

    // ── commandPause ──────────────────────────────────────────────────────

    @Test
    void commandPauseSetsIsPausedAndKeepsPosition() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Mix");
        ch.setPlaylistCurrentTrackId("t1");
        ch.setPlaylistIsPlaying(true);
        ch.setPlaylistTrackPosition(20.0);

        service.commandPause("caster", "p1");

        Channel saved = channels.get("caster");
        assertThat(saved.isPlaylistIsPaused()).isTrue();
        assertThat(saved.isPlaylistIsPlaying()).isTrue();
        assertThat(saved.getPlaylistTrackPosition()).isEqualTo(20.0);

        ArgumentCaptor<PlaylistEvent> cap = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(messagingTemplate).convertAndSend(anyString(), cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(PlaylistEvent.Type.PLAYLIST_PAUSE);
    }

    // ── commandNext ───────────────────────────────────────────────────────

    @Test
    void commandNextAdvancesToNextTrack() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Mix");
        track("t1", "p1", "a1", 0);
        track("t2", "p1", "a2", 1);

        service.commandNext("caster", "p1", "t1");

        Channel saved = channels.get("caster");
        assertThat(saved.getPlaylistCurrentTrackId()).isEqualTo("t2");
        assertThat(saved.isPlaylistIsPlaying()).isTrue();
        assertThat(saved.isPlaylistIsPaused()).isFalse();
        assertThat(saved.getPlaylistTrackPosition()).isEqualTo(0.0);

        ArgumentCaptor<PlaylistEvent> cap = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(messagingTemplate).convertAndSend(anyString(), cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(PlaylistEvent.Type.PLAYLIST_NEXT);
        assertThat(cap.getValue().getTrackId()).isEqualTo("t2");
    }

    @Test
    void commandNextOnLastTrackEndsPlaylistAndClearsState() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Mix");
        track("t1", "p1", "a1", 0);

        service.commandNext("caster", "p1", "t1");

        Channel saved = channels.get("caster");
        assertThat(saved.isPlaylistIsPlaying()).isFalse();
        assertThat(saved.getPlaylistCurrentTrackId()).isNull();

        ArgumentCaptor<PlaylistEvent> cap = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(messagingTemplate).convertAndSend(anyString(), cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(PlaylistEvent.Type.PLAYLIST_ENDED);
    }

    // ── commandPrev ───────────────────────────────────────────────────────

    @Test
    void commandPrevGoesToPreviousTrack() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Mix");
        track("t1", "p1", "a1", 0);
        track("t2", "p1", "a2", 1);

        service.commandPrev("caster", "p1", "t2");

        Channel saved = channels.get("caster");
        assertThat(saved.getPlaylistCurrentTrackId()).isEqualTo("t1");
        assertThat(saved.isPlaylistIsPlaying()).isTrue();
        assertThat(saved.getPlaylistTrackPosition()).isEqualTo(0.0);

        ArgumentCaptor<PlaylistEvent> cap = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(messagingTemplate).convertAndSend(anyString(), cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(PlaylistEvent.Type.PLAYLIST_PREV);
        assertThat(cap.getValue().getTrackId()).isEqualTo("t1");
    }

    @Test
    void commandPrevOnFirstTrackSendsNullTrackIdToRestartCurrent() {
        Channel ch = channel("caster");
        playlist("p1", "caster", "Mix");
        track("t1", "p1", "a1", 0);

        service.commandPrev("caster", "p1", "t1");

        // DB is updated with the same track (restart)
        assertThat(channels.get("caster").getPlaylistCurrentTrackId()).isEqualTo("t1");
        // Event carries null trackId so renderer knows to restart
        ArgumentCaptor<PlaylistEvent> cap = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(messagingTemplate).convertAndSend(anyString(), cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(PlaylistEvent.Type.PLAYLIST_PREV);
        assertThat(cap.getValue().getTrackId()).isNull();
    }

    // ── reportPosition ────────────────────────────────────────────────────

    @Test
    void reportPositionUpdatesPositionWhenConditionsMatch() {
        Channel ch = channel("caster");
        ch.setActivePlaylistId("p1");
        ch.setPlaylistCurrentTrackId("t1");
        ch.setPlaylistIsPlaying(true);
        ch.setPlaylistIsPaused(false);

        service.reportPosition("caster", "p1", "t1", 55.3);

        assertThat(channels.get("caster").getPlaylistTrackPosition()).isEqualTo(55.3);
    }

    @Test
    void reportPositionIgnoredWhenNotPlaying() {
        Channel ch = channel("caster");
        ch.setActivePlaylistId("p1");
        ch.setPlaylistCurrentTrackId("t1");
        ch.setPlaylistIsPlaying(false);

        service.reportPosition("caster", "p1", "t1", 55.3);

        assertThat(channels.get("caster").getPlaylistTrackPosition()).isEqualTo(0.0);
    }

    @Test
    void reportPositionIgnoredWhenPaused() {
        Channel ch = channel("caster");
        ch.setActivePlaylistId("p1");
        ch.setPlaylistCurrentTrackId("t1");
        ch.setPlaylistIsPlaying(true);
        ch.setPlaylistIsPaused(true);

        service.reportPosition("caster", "p1", "t1", 55.3);

        assertThat(channels.get("caster").getPlaylistTrackPosition()).isEqualTo(0.0);
    }

    @Test
    void reportPositionIgnoredWhenWrongPlaylist() {
        Channel ch = channel("caster");
        ch.setActivePlaylistId("p1");
        ch.setPlaylistCurrentTrackId("t1");
        ch.setPlaylistIsPlaying(true);

        service.reportPosition("caster", "p2", "t1", 55.3);

        assertThat(channels.get("caster").getPlaylistTrackPosition()).isEqualTo(0.0);
    }

    @Test
    void reportPositionIgnoredWhenWrongTrack() {
        Channel ch = channel("caster");
        ch.setActivePlaylistId("p1");
        ch.setPlaylistCurrentTrackId("t1");
        ch.setPlaylistIsPlaying(true);

        service.reportPosition("caster", "p1", "t2", 55.3);

        assertThat(channels.get("caster").getPlaylistTrackPosition()).isEqualTo(0.0);
    }

    // ── normalization ─────────────────────────────────────────────────────

    @Test
    void broadcasterNameIsNormalizedToLowercase() {
        channel("caster");
        service.createPlaylist("CASTER", "Mix");
        verify(playlistRepository).save(any(Playlist.class));
    }
}
