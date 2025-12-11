package dev.kruhlmann.imgfloat.config;

import java.util.Arrays;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

final class OAuth2RestTemplateFactory {

    private OAuth2RestTemplateFactory() {
    }

    static RestTemplate create() {
        RestTemplate restTemplate = new RestTemplate(Arrays.asList(
                new FormHttpMessageConverter(),
                new OAuth2AccessTokenResponseHttpMessageConverter()
        ));
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();
        if (requestFactory instanceof SimpleClientHttpRequestFactory simple) {
            simple.setConnectTimeout(30_000);
            simple.setReadTimeout(30_000);
        }
        return restTemplate;
    }
}
