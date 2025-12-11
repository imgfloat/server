package com.imgfloat.app.service;

import com.imgfloat.app.model.Channel;
import com.imgfloat.app.repository.ChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.imgfloat.app.repository.AssetRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChannelDirectoryServiceIntegrationTest {

    @Autowired
    private ChannelRepository channelRepository;

    private ChannelDirectoryService channelDirectoryService;

    @BeforeEach
    void setUp() {
        channelDirectoryService = new ChannelDirectoryService(
                channelRepository,
                Mockito.mock(AssetRepository.class),
                Mockito.mock(SimpMessagingTemplate.class));
    }

    @Test
    void searchBroadcastersFindsPartialMatchesCaseInsensitive() {
        Channel alpha = new Channel("Alpha");
        Channel alphabeta = new Channel("AlphaBeta");
        Channel bravo = new Channel("Bravo");
        channelRepository.saveAll(List.of(alpha, alphabeta, bravo));

        List<String> results = channelDirectoryService.searchBroadcasters("lPh");

        assertThat(results).containsExactly("alpha", "alphabeta");
    }

    @Test
    void adminChannelsForFindsNormalizedAdminEntries() {
        Channel alpha = new Channel("Alpha");
        alpha.addAdmin("ModOne");
        Channel bravo = new Channel("Bravo");
        bravo.addAdmin("modone");
        Channel charlie = new Channel("Charlie");
        charlie.addAdmin("other");

        channelRepository.saveAll(List.of(alpha, bravo, charlie));

        List<String> adminChannels = channelDirectoryService.adminChannelsFor("MODONE").stream().sorted().toList();

        assertThat(adminChannels).containsExactly("alpha", "bravo");
    }
}
