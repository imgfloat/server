package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.service.ChannelDirectoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
public class ChannelDirectoryApiController {

    private final ChannelDirectoryService channelDirectoryService;

    public ChannelDirectoryApiController(ChannelDirectoryService channelDirectoryService) {
        this.channelDirectoryService = channelDirectoryService;
    }

    @GetMapping
    public List<String> listChannels(@RequestParam(value = "q", required = false) String query) {
        return channelDirectoryService.searchBroadcasters(query);
    }
}
