package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.service.TwitchEmoteService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/twitch/emotes")
public class TwitchEmoteController {

    private final TwitchEmoteService twitchEmoteService;

    public TwitchEmoteController(TwitchEmoteService twitchEmoteService) {
        this.twitchEmoteService = twitchEmoteService;
    }

    @GetMapping
    public EmoteCatalogResponse fetchEmoteCatalog(@RequestParam(value = "channel", required = false) String channel) {
        List<TwitchEmoteService.EmoteDescriptor> global = twitchEmoteService.getGlobalEmotes();
        List<TwitchEmoteService.EmoteDescriptor> channelEmotes = twitchEmoteService.getChannelEmotes(channel);
        return new EmoteCatalogResponse(global, channelEmotes);
    }

    @GetMapping("/{emoteId}")
    public ResponseEntity<byte[]> fetchEmoteAsset(@PathVariable("emoteId") String emoteId) {
        return twitchEmoteService
            .loadEmoteAsset(emoteId)
            .map((asset) -> ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.mediaType())).body(asset.bytes()))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record EmoteCatalogResponse(
        List<TwitchEmoteService.EmoteDescriptor> global,
        List<TwitchEmoteService.EmoteDescriptor> channel
    ) {}
}
