package dev.kruhlmann.imgfloat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class TwitchOAuth2ErrorResponseErrorHandlerTest {

    private final TwitchOAuth2ErrorResponseErrorHandler handler = new TwitchOAuth2ErrorResponseErrorHandler();

    @Test
    void fallsBackToSyntheticErrorWhenErrorBodyIsMissing() {
        MockClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.BAD_REQUEST);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        OAuth2AuthorizationException exception = assertThrows(OAuth2AuthorizationException.class, () ->
            handler.handleError(response)
        );

        assertThat(exception.getError().getErrorCode()).isEqualTo("invalid_token_response");
        assertThat(exception.getError().getDescription()).contains("Failed to parse Twitch OAuth error response");
    }

    @Test
    void successfulResponsesStillParseNormally() {
        RestTemplate restTemplate = OAuth2RestTemplateFactory.create();
        restTemplate.setErrorHandler(new TwitchOAuth2ErrorResponseErrorHandler());
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server
            .expect(requestTo("https://id.twitch.tv/oauth2/token"))
            .andRespond(
                withSuccess(
                    "{\"access_token\":\"abc\",\"token_type\":\"bearer\",\"expires_in\":3600,\"scope\":[]}",
                    MediaType.APPLICATION_JSON
                )
            );

        RequestEntity<Void> request = RequestEntity.post(URI.create("https://id.twitch.tv/oauth2/token")).build();
        ResponseEntity<OAuth2AccessTokenResponse> response = restTemplate.exchange(
            request,
            OAuth2AccessTokenResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken().getTokenValue()).isEqualTo("abc");

        server.verify();
    }
}
