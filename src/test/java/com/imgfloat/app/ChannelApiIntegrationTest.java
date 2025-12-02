package com.imgfloat.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imgfloat.app.model.AssetRequest;
import com.imgfloat.app.model.VisibilityRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.twitch.client-id=test-client-id",
        "spring.security.oauth2.client.registration.twitch.client-secret=test-client-secret"
})
@AutoConfigureMockMvc
class ChannelApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void broadcasterManagesAdminsAndAssets() throws Exception {
        String broadcaster = "caster";
        mockMvc.perform(post("/api/channels/{broadcaster}/admins", broadcaster)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"helper\"}")
                        .with(oauth2Login().attributes(attrs -> attrs.put("preferred_username", broadcaster))))
                .andExpect(status().isOk());

        AssetRequest request = new AssetRequest();
        request.setUrl("https://example.com/image.png");
        request.setWidth(300);
        request.setHeight(200);

        String body = objectMapper.writeValueAsString(request);
        String assetId = objectMapper.readTree(mockMvc.perform(post("/api/channels/{broadcaster}/assets", broadcaster)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(oauth2Login().attributes(attrs -> attrs.put("preferred_username", broadcaster))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/channels/{broadcaster}/assets", broadcaster)
                        .with(oauth2Login().attributes(attrs -> attrs.put("preferred_username", broadcaster))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        VisibilityRequest visibilityRequest = new VisibilityRequest();
        visibilityRequest.setHidden(false);
        mockMvc.perform(put("/api/channels/{broadcaster}/assets/{id}/visibility", broadcaster, assetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(visibilityRequest))
                        .with(oauth2Login().attributes(attrs -> attrs.put("preferred_username", broadcaster))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(false));

        mockMvc.perform(get("/api/channels/{broadcaster}/assets/visible", broadcaster)
                        .with(oauth2Login().attributes(attrs -> attrs.put("preferred_username", broadcaster))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(delete("/api/channels/{broadcaster}/assets/{id}", broadcaster, assetId)
                        .with(oauth2Login().attributes(attrs -> attrs.put("preferred_username", broadcaster))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAdminChangesFromNonBroadcaster() throws Exception {
        mockMvc.perform(post("/api/channels/{broadcaster}/admins", "caster")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"helper\"}")
                        .with(oauth2Login().attributes(attrs -> attrs.put("preferred_username", "intruder"))))
                .andExpect(status().isForbidden());
    }
}
