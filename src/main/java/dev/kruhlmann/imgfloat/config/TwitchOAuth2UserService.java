package dev.kruhlmann.imgfloat.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestTemplate;

/**
 * Adds the Twitch required "Client-ID" header to user info requests while preserving the default
 * Spring behavior for mapping the response into an {@link OAuth2User}.
 */
class TwitchOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final Function<OAuth2UserRequest, RestTemplate> restTemplateFactory;

    TwitchOAuth2UserService() {
        this(TwitchOAuth2UserService::createRestTemplate);
    }

    TwitchOAuth2UserService(Function<OAuth2UserRequest, RestTemplate> restTemplateFactory) {
        this.restTemplateFactory = restTemplateFactory;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        delegate.setRestOperations(restTemplateFactory.apply(userRequest));
        OAuth2User delegateUser = delegate.loadUser(twitchUserRequest(userRequest));
        Map<String, Object> twitchUser = unwrapUserAttributes(delegateUser.getAttributes());
        return new DefaultOAuth2User(delegateUser.getAuthorities(), twitchUser, "login");
    }

    private OAuth2UserRequest twitchUserRequest(OAuth2UserRequest userRequest) {
        return new OAuth2UserRequest(
                twitchUserRegistration(userRequest),
                userRequest.getAccessToken(),
                userRequest.getAdditionalParameters());
    }

    private ClientRegistration twitchUserRegistration(OAuth2UserRequest userRequest) {
        ClientRegistration registration = userRequest.getClientRegistration();
        return ClientRegistration.withClientRegistration(registration)
                // The Twitch response nests user details under a "data" array, so accept that
                // shape for the initial parsing step.
                .userNameAttributeName("data")
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapUserAttributes(Map<String, Object> responseAttributes) {
        Object data = responseAttributes.get("data");
        if (!(data instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> first)) {
            throw invalidUserInfo("Missing Twitch user array in user info response");
        }

        Map<String, Object> twitchUser = new HashMap<>();
        first.forEach((key, value) -> twitchUser.put(String.valueOf(key), value));

        Object login = twitchUser.get("login");
        if (!(login instanceof String loginValue) || loginValue.isBlank()) {
            throw invalidUserInfo("Missing Twitch login in user info response");
        }

        return twitchUser;
    }

    private OAuth2AuthenticationException invalidUserInfo(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info_response", message, null));
    }

    static RestTemplate createRestTemplate(OAuth2UserRequest userRequest) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(createRequestFactory(restTemplate.getRequestFactory()));
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(restTemplate.getInterceptors());
        interceptors.add((request, body, execution) -> {
            request.getHeaders().add("Client-ID", userRequest.getClientRegistration().getClientId());
            return execution.execute(request, body);
        });
        restTemplate.setInterceptors(interceptors);
        restTemplate.setErrorHandler(new TwitchOAuth2ErrorResponseErrorHandler());
        return restTemplate;
    }

    private static ClientHttpRequestFactory createRequestFactory(ClientHttpRequestFactory existing) {
        if (existing instanceof SimpleClientHttpRequestFactory simple) {
            simple.setConnectTimeout(30_000);
            simple.setReadTimeout(30_000);
            return simple;
        }
        return existing;
    }
}
