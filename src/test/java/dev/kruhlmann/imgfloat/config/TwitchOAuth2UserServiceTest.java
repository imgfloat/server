package dev.kruhlmann.imgfloat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class TwitchOAuth2UserServiceTest {

    @Test
    void unwrapsTwitchUserAndAddsClientIdHeaderToUserInfoRequest() {
        ClientRegistration registration = twitchRegistrationBuilder()
                .clientId("client-123")
                .clientSecret("secret")
                .build();

        OAuth2UserRequest userRequest = userRequest(registration);
        RestTemplate restTemplate = TwitchOAuth2UserService.createRestTemplate(userRequest);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        TwitchOAuth2UserService service = new TwitchOAuth2UserService(ignored -> restTemplate);

        server.expect(requestTo("https://api.twitch.tv/helix/users"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Client-ID", "client-123"))
                .andRespond(withSuccess(
                        "{\"data\":[{\"id\":\"42\",\"login\":\"demo\",\"display_name\":\"Demo\"}]}",
                        MediaType.APPLICATION_JSON));

        OAuth2User user = service.loadUser(userRequest);

        assertThat(user.getName()).isEqualTo("demo");
        assertThat(user.getAttributes())
                .containsEntry("id", "42")
                .containsEntry("display_name", "Demo");
        server.verify();
    }

    private OAuth2UserRequest userRequest(ClientRegistration registration) {
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Set.of("user:read:email"));
        return new OAuth2UserRequest(registration, accessToken);
    }

    private ClientRegistration.Builder twitchRegistrationBuilder() {
        return ClientRegistration.withRegistrationId("twitch")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .clientName("Twitch")
                .redirectUri("https://example.com/login/oauth2/code/twitch")
                .authorizationUri("https://id.twitch.tv/oauth2/authorize")
                .tokenUri("https://id.twitch.tv/oauth2/token")
                .userInfoUri("https://api.twitch.tv/helix/users")
                .userNameAttributeName("login");
    }
}
