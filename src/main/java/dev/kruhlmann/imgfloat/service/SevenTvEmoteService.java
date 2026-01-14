package dev.kruhlmann.imgfloat.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class SevenTvEmoteService {

    private static final Logger LOG = LoggerFactory.getLogger(SevenTvEmoteService.class);
    private static final String USERS_URL = "https://api.twitch.tv/helix/users";
    private static final String USER_EMOTE_URL = "https://7tv.io/v3/users/twitch/";

    private final RestTemplate restTemplate;
    private final TwitchAppAccessTokenService tokenService;
    private final Path cacheRoot;
    private final Map<String, CachedEmote> emoteCache = new ConcurrentHashMap<>();
    private final Map<String, List<CachedEmote>> channelEmoteCache = new ConcurrentHashMap<>();

    public SevenTvEmoteService(
        RestTemplateBuilder builder,
        TwitchAppAccessTokenService tokenService,
        @Value("${IMGFLOAT_7TV_EMOTE_CACHE_PATH:#{null}}") String cachePath
    ) {
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(20))
            .setReadTimeout(Duration.ofSeconds(20))
            .build();
        this.tokenService = tokenService;
        String root = cachePath != null
            ? cachePath
            : Paths.get(System.getProperty("java.io.tmpdir"), "imgfloat-7tv-emotes").toString();
        this.cacheRoot = Paths.get(root).normalize().toAbsolutePath();
        try {
            Files.createDirectories(this.cacheRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create 7TV emote cache directory", ex);
        }
    }

    public List<EmoteDescriptor> getChannelEmotes(String channelLogin) {
        if (channelLogin == null || channelLogin.isBlank()) {
            return List.of();
        }
        String normalized = channelLogin.toLowerCase(Locale.ROOT);
        List<CachedEmote> emotes = channelEmoteCache.computeIfAbsent(normalized, this::fetchChannelEmotes);
        return emotes.stream().map(CachedEmote::descriptor).toList();
    }

    public Optional<EmoteAsset> loadEmoteAsset(String emoteId) {
        if (emoteId == null || emoteId.isBlank()) {
            return Optional.empty();
        }
        CachedEmote cached = emoteCache.get(emoteId);
        if (cached == null) {
            cached = restoreFromDisk(emoteId).orElse(null);
        }
        if (cached == null) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(cached.path());
            return Optional.of(new EmoteAsset(bytes, cached.mediaType()));
        } catch (IOException ex) {
            LOG.warn("Unable to read cached 7TV emote {}", emoteId, ex);
            return Optional.empty();
        }
    }

    private List<CachedEmote> fetchChannelEmotes(String channelLogin) {
        String broadcasterId = fetchBroadcasterId(channelLogin).orElse(null);
        if (broadcasterId == null) {
            return List.of();
        }
        String url = USER_EMOTE_URL + broadcasterId;
        SevenTvUserResponse response = fetchEmotes(url).orElse(null);
        SevenTvEmoteSet set = response != null ? response.emoteSet() : null;
        if (set == null || set.emotes() == null || set.emotes().isEmpty()) {
            return List.of();
        }
        List<CachedEmote> cached = set
            .emotes()
            .stream()
            .map(this::cacheEmote)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
        LOG.info("Loaded {} 7TV emotes for {}", cached.size(), channelLogin);
        return cached;
    }

    private Optional<String> fetchBroadcasterId(String channelLogin) {
        Optional<String> token = tokenService.getAccessToken();
        Optional<String> clientId = tokenService.getClientId();
        if (token.isEmpty() || clientId.isEmpty()) {
            return Optional.empty();
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(USERS_URL).queryParam("login", channelLogin);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token.get());
        headers.add("Client-ID", clientId.get());
        try {
            ResponseEntity<TwitchUsersResponse> response = restTemplate.exchange(
                builder.build(true).toUri(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                TwitchUsersResponse.class
            );
            TwitchUsersResponse body = response.getBody();
            if (body == null || body.data() == null || body.data().isEmpty()) {
                return Optional.empty();
            }
            return body.data().stream().findFirst().map(TwitchUserData::id);
        } catch (RestClientException ex) {
            LOG.warn("Unable to fetch Twitch broadcaster id for {}", channelLogin, ex);
            return Optional.empty();
        }
    }

    private Optional<SevenTvUserResponse> fetchEmotes(String url) {
        try {
            ResponseEntity<SevenTvUserResponse> response = restTemplate.exchange(
                URI.create(url),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                SevenTvUserResponse.class
            );
            return Optional.ofNullable(response.getBody());
        } catch (RestClientException ex) {
            LOG.warn("Unable to fetch 7TV emotes from {}", url, ex);
            return Optional.empty();
        }
    }

    private Optional<CachedEmote> cacheEmote(SevenTvEmote emote) {
        if (emote == null || emote.id() == null || emote.id().isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
            emoteCache.computeIfAbsent(
                emote.id(),
                (id) -> {
                    String imageUrl = selectImageUrl(emote);
                    if (imageUrl == null) {
                        return null;
                    }
                    return downloadEmote(id, emote.name(), imageUrl);
                }
            )
        );
    }

    private CachedEmote downloadEmote(String id, String name, String imageUrl) {
        Path filePath = resolveEmotePath(id, imageUrl);
        MediaType mediaType = null;

        if (!Files.exists(filePath)) {
            try {
                ResponseEntity<byte[]> response = restTemplate.getForEntity(URI.create(imageUrl), byte[].class);
                byte[] bytes = response.getBody();
                if (bytes == null || bytes.length == 0) {
                    return null;
                }
                mediaType = response.getHeaders().getContentType();
                Files.write(filePath, bytes);
            } catch (IOException | RestClientException ex) {
                LOG.warn("Unable to download 7TV emote {}", id, ex);
                return null;
            }
        }

        if (mediaType == null) {
            mediaType = mediaTypeFromPath(filePath);
        }
        return new CachedEmote(id, name, filePath, mediaType != null ? mediaType.toString() : "image/png");
    }

    private Optional<CachedEmote> restoreFromDisk(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            List<Path> candidates;
            try (var stream = Files.list(cacheRoot)) {
                candidates = stream.filter((path) -> path.getFileName().toString().startsWith(id + ".")).toList();
            }
            if (candidates.isEmpty()) {
                return Optional.empty();
            }
            Path path = candidates.get(0);
            MediaType mediaType = mediaTypeFromPath(path);
            CachedEmote cached = new CachedEmote(id, id, path, mediaType != null ? mediaType.toString() : "image/png");
            emoteCache.put(id, cached);
            return Optional.of(cached);
        } catch (IOException ex) {
            LOG.warn("Unable to restore cached 7TV emote {}", id, ex);
            return Optional.empty();
        }
    }

    private Path resolveEmotePath(String id, String imageUrl) {
        String extension = extensionFromUrl(imageUrl).orElse("png");
        return cacheRoot.resolve(id + "." + extension);
    }

    private Optional<String> extensionFromUrl(String imageUrl) {
        if (imageUrl == null) {
            return Optional.empty();
        }
        try {
            String path = URI.create(imageUrl).getPath();
            int dot = path.lastIndexOf('.');
            if (dot == -1) {
                return Optional.empty();
            }
            return Optional.of(path.substring(dot + 1));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private MediaType mediaTypeFromPath(Path path) {
        if (path == null) {
            return null;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (name.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (name.endsWith(".avif")) {
            return MediaType.parseMediaType("image/avif");
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.IMAGE_PNG;
    }

    private String selectImageUrl(SevenTvEmote emote) {
        if (emote == null || emote.data() == null || emote.data().host() == null) {
            return null;
        }
        SevenTvHost host = emote.data().host();
        if (host.files() == null || host.files().isEmpty()) {
            return null;
        }
        SevenTvFile selected = selectBestFile(host.files());
        if (selected == null || selected.name() == null || selected.name().isBlank()) {
            return null;
        }
        String hostUrl = normalizeHostUrl(host.url());
        if (hostUrl == null) {
            return null;
        }
        return hostUrl + "/" + selected.name();
    }

    private SevenTvFile selectBestFile(List<SevenTvFile> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        for (String size : List.of("4x", "3x", "2x", "1x")) {
            Optional<SevenTvFile> match = files.stream().filter((file) -> size.equals(file.size())).findFirst();
            if (match.isPresent()) {
                return match.get();
            }
        }
        return files.get(0);
    }

    private String normalizeHostUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String normalized = url;
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
        } else if (!normalized.startsWith("http")) {
            normalized = "https://" + normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public record EmoteDescriptor(String id, String name, String url) {}

    public record EmoteAsset(byte[] bytes, String mediaType) {}

    private record CachedEmote(String id, String name, Path path, String mediaType) {
        EmoteDescriptor descriptor() {
            return new EmoteDescriptor(id, name, "/api/7tv/emotes/" + id);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SevenTvUserResponse(@JsonProperty("emote_set") SevenTvEmoteSet emoteSet) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SevenTvEmoteSet(List<SevenTvEmote> emotes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SevenTvEmote(String id, String name, SevenTvEmoteData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SevenTvEmoteData(SevenTvHost host) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SevenTvHost(String url, List<SevenTvFile> files) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SevenTvFile(String name, String format, String size) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TwitchUsersResponse(List<TwitchUserData> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TwitchUserData(String id) {}
}
