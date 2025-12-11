package dev.kruhlmann.imgfloat.config;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.util.StreamUtils;

/**
 * Twitch occasionally returns error payloads without an {@code error} code field. The default
 * {@link OAuth2ErrorHttpMessageConverter} refuses to deserialize such payloads and throws an
 * {@link HttpMessageNotReadableException}. That propagates up as a 500 before we can surface a
 * meaningful login failure to the user. This handler falls back to a safe, synthetic
 * {@link OAuth2Error} so the login flow can fail gracefully.
 */
class TwitchOAuth2ErrorResponseErrorHandler extends OAuth2ErrorResponseErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TwitchOAuth2ErrorResponseErrorHandler.class);

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        byte[] bodyBytes = StreamUtils.copyToByteArray(response.getBody());
        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        if (body.isBlank()) {
            LOG.warn("Failed to parse Twitch OAuth error response (status: {}, headers: {}): <empty body>",
                    response.getStatusCode(),
                    response.getHeaders());
            throw asAuthorizationException(body, null);
        }

        try {
            super.handleError(new CachedBodyClientHttpResponse(response, bodyBytes));
        } catch (HttpMessageNotReadableException | IllegalArgumentException ex) {
            LOG.warn("Failed to parse Twitch OAuth error response (status: {}, headers: {}): {}",
                    response.getStatusCode(),
                    response.getHeaders(),
                    body,
                    ex);
            throw asAuthorizationException(body, ex);
        }
    }

    private OAuth2AuthorizationException asAuthorizationException(String body, Exception ex) {
        String description = "Failed to parse Twitch OAuth error response" + (body.isBlank() ? "." : ": " + body);
        OAuth2Error oauth2Error = new OAuth2Error("invalid_token_response", description, null);
        return new OAuth2AuthorizationException(oauth2Error, ex);
    }

    private static final class CachedBodyClientHttpResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final byte[] body;

        private CachedBodyClientHttpResponse(ClientHttpResponse delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override
        public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public int getRawStatusCode() throws IOException {
            return delegate.getRawStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public java.io.InputStream getBody() throws IOException {
            return new ByteArrayInputStream(body);
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }
}
