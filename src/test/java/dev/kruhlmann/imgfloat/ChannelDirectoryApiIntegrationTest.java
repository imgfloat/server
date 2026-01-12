package dev.kruhlmann.imgfloat;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kruhlmann.imgfloat.model.Channel;
import dev.kruhlmann.imgfloat.repository.ChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
        "spring.security.oauth2.client.registration.twitch.client-id=test-client-id",
        "spring.security.oauth2.client.registration.twitch.client-secret=test-client-secret",
        "spring.datasource.url=jdbc:sqlite:target/test-${random.uuid}.db",
        "IMGFLOAT_TOKEN_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    }
)
@AutoConfigureMockMvc
class ChannelDirectoryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChannelRepository channelRepository;

    @BeforeEach
    void cleanChannels() {
        channelRepository.deleteAll();
    }

    @Test
    void searchesBroadcastersCaseInsensitiveAndSorted() throws Exception {
        channelRepository.save(new Channel("Beta"));
        channelRepository.save(new Channel("alpha"));
        channelRepository.save(new Channel("ALPINE"));

        mockMvc
            .perform(get("/api/channels").param("q", "Al"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0]").value("alpha"))
            .andExpect(jsonPath("$[1]").value("alpine"));
    }
}
