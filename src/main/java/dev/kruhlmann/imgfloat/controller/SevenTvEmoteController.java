package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.service.SevenTvEmoteService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/7tv/emotes")
public class SevenTvEmoteController {

    private final SevenTvEmoteService sevenTvEmoteService;

    public SevenTvEmoteController(SevenTvEmoteService sevenTvEmoteService) {
        this.sevenTvEmoteService = sevenTvEmoteService;
    }

    @GetMapping
    public EmoteCatalogResponse fetchEmoteCatalog(@RequestParam(value = "channel", required = false) String channel) {
        List<SevenTvEmoteService.EmoteDescriptor> channelEmotes = sevenTvEmoteService.getChannelEmotes(channel);
        return new EmoteCatalogResponse(channelEmotes);
    }

    @GetMapping("/{emoteId}")
    public ResponseEntity<byte[]> fetchEmoteAsset(@PathVariable("emoteId") String emoteId) {
        return sevenTvEmoteService
            .loadEmoteAsset(emoteId)
            .map((asset) -> ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.mediaType())).body(asset.bytes()))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record EmoteCatalogResponse(List<SevenTvEmoteService.EmoteDescriptor> channel) {}
}
