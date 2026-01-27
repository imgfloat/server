package dev.kruhlmann.imgfloat.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.MultiValueMap;

class TwitchAuthorizationCodeGrantRequestEntityConverterTest {

    @Test
    void addsClientIdAndSecretToTokenRequestBody() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("twitch")
            .clientId("twitch-id")
            .clientSecret("twitch-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://example.com/redirect")
            .scope("user:read:email")
            .authorizationUri("https://id.twitch.tv/oauth2/authorize")
            .tokenUri("https://id.twitch.tv/oauth2/token")
            .userInfoUri("https://api.twitch.tv/helix/users")
            .userNameAttributeName("preferred_username")
            .build();

        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri(registration.getProviderDetails().getAuthorizationUri())
            .clientId(registration.getClientId())
            .redirectUri(registration.getRedirectUri())
            .state("state")
            .build();

        OAuth2AuthorizationResponse authorizationResponse = OAuth2AuthorizationResponse.success("code")
            .redirectUri(registration.getRedirectUri())
            .state("state")
            .build();

        MultiValueMap<String, String> body = getStringStringMultiValueMap(authorizationRequest, authorizationResponse, registration);

        assertThat(body.getFirst(OAuth2ParameterNames.CLIENT_ID)).isEqualTo("twitch-id");
        assertThat(body.getFirst(OAuth2ParameterNames.CLIENT_SECRET)).isEqualTo("twitch-secret");
    }

    private static MultiValueMap<String, String> getStringStringMultiValueMap(OAuth2AuthorizationRequest authorizationRequest, OAuth2AuthorizationResponse authorizationResponse, ClientRegistration registration) {
        OAuth2AuthorizationExchange exchange = new OAuth2AuthorizationExchange(
                authorizationRequest,
                authorizationResponse
        );
        OAuth2AuthorizationCodeGrantRequest grantRequest = new OAuth2AuthorizationCodeGrantRequest(
                registration,
            exchange
        );

        var converter = new TwitchAuthorizationCodeGrantRequestEntityConverter();
        RequestEntity<?> requestEntity = converter.convert(grantRequest);

        return (MultiValueMap<String, String>) requestEntity.getBody();
    }
}
