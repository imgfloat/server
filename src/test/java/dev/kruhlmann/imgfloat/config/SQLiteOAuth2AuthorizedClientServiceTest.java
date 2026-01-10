package dev.kruhlmann.imgfloat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

class SQLiteOAuth2AuthorizedClientServiceTest {

    @Test
    void saveAuthorizedClientEncryptsTokenValues() throws Exception {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        ClientRegistrationRepository repository = mock(ClientRegistrationRepository.class);
        OAuthTokenCipher cipher = new OAuthTokenCipher(keyFrom("primary-key"), List.of(keyFrom("primary-key")));
        SQLiteOAuth2AuthorizedClientService service =
            new SQLiteOAuth2AuthorizedClientService(jdbcOperations, repository, cipher);
        Map<Integer, String> stringValues = new HashMap<>();
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        doAnswer(invocation -> {
            stringValues.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(preparedStatement).setString(anyInt(), anyString());
        doAnswer(invocation -> {
            PreparedStatementSetter setter = invocation.getArgument(1);
            setter.setValues(preparedStatement);
            return 1;
        }).when(jdbcOperations).update(anyString(), any(PreparedStatementSetter.class));

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access-token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Set.of("scope:one")
        );
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken("refresh-token", Instant.now());
        OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(
            clientRegistration(),
            "principal",
            accessToken,
            refreshToken
        );
        Authentication principal = mock(Authentication.class);
        when(principal.getName()).thenReturn("principal");

        service.saveAuthorizedClient(authorizedClient, principal);

        assertThat(stringValues.get(3)).startsWith("v1:").isNotEqualTo("access-token");
        assertThat(stringValues.get(7)).startsWith("v1:").isNotEqualTo("refresh-token");
    }

    @Test
    void loadAuthorizedClientDecryptsStoredTokens() throws Exception {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        ClientRegistrationRepository repository = mock(ClientRegistrationRepository.class);
        OAuthTokenCipher cipher = new OAuthTokenCipher(keyFrom("primary-key"), List.of(keyFrom("primary-key")));
        SQLiteOAuth2AuthorizedClientService service =
            new SQLiteOAuth2AuthorizedClientService(jdbcOperations, repository, cipher);
        ClientRegistration registration = clientRegistration();
        when(repository.findByRegistrationId("twitch")).thenReturn(registration);

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("client_registration_id")).thenReturn("twitch");
        when(resultSet.getString("principal_name")).thenReturn("principal");
        when(resultSet.getString("access_token_value")).thenReturn(cipher.encrypt("access-token"));
        when(resultSet.getObject("access_token_issued_at")).thenReturn(Instant.now().toEpochMilli());
        when(resultSet.getObject("access_token_expires_at")).thenReturn(Instant.now().plusSeconds(3600).toEpochMilli());
        when(resultSet.getString("access_token_scopes")).thenReturn("scope:one");
        when(resultSet.getObject("refresh_token_value")).thenReturn(cipher.encrypt("refresh-token"));
        when(resultSet.getObject("refresh_token_issued_at")).thenReturn(Instant.now().toEpochMilli());

        doAnswer(invocation -> {
            PreparedStatementSetter setter = invocation.getArgument(1);
            setter.setValues(mock(PreparedStatement.class));
            ResultSetExtractor<?> extractor = invocation.getArgument(2);
            return extractor.extractData(resultSet);
        }).when(jdbcOperations).query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class));

        OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("twitch", "principal");

        assertThat(loaded).isNotNull();
        assertThat(loaded.getAccessToken().getTokenValue()).isEqualTo("access-token");
        assertThat(loaded.getRefreshToken()).isNotNull();
        assertThat(loaded.getRefreshToken().getTokenValue()).isEqualTo("refresh-token");
    }

    @Test
    void loadAuthorizedClientPassesThroughPlaintextTokens() throws Exception {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        ClientRegistrationRepository repository = mock(ClientRegistrationRepository.class);
        OAuthTokenCipher cipher = new OAuthTokenCipher(keyFrom("primary-key"), List.of(keyFrom("primary-key")));
        SQLiteOAuth2AuthorizedClientService service =
            new SQLiteOAuth2AuthorizedClientService(jdbcOperations, repository, cipher);
        ClientRegistration registration = clientRegistration();
        when(repository.findByRegistrationId("twitch")).thenReturn(registration);

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("client_registration_id")).thenReturn("twitch");
        when(resultSet.getString("principal_name")).thenReturn("principal");
        when(resultSet.getString("access_token_value")).thenReturn("access-token");
        when(resultSet.getObject("access_token_issued_at")).thenReturn(Instant.now().toEpochMilli());
        when(resultSet.getObject("access_token_expires_at")).thenReturn(Instant.now().plusSeconds(3600).toEpochMilli());
        when(resultSet.getString("access_token_scopes")).thenReturn("scope:one");
        when(resultSet.getObject("refresh_token_value")).thenReturn("refresh-token");
        when(resultSet.getObject("refresh_token_issued_at")).thenReturn(Instant.now().toEpochMilli());

        doAnswer(invocation -> {
            PreparedStatementSetter setter = invocation.getArgument(1);
            setter.setValues(mock(PreparedStatement.class));
            ResultSetExtractor<?> extractor = invocation.getArgument(2);
            return extractor.extractData(resultSet);
        }).when(jdbcOperations).query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class));

        OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("twitch", "principal");

        assertThat(loaded).isNotNull();
        assertThat(loaded.getAccessToken().getTokenValue()).isEqualTo("access-token");
        assertThat(loaded.getRefreshToken()).isNotNull();
        assertThat(loaded.getRefreshToken().getTokenValue()).isEqualTo("refresh-token");
    }

    @Test
    void loadAuthorizedClientClearsTokensOnDecryptionFailure() throws Exception {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        ClientRegistrationRepository repository = mock(ClientRegistrationRepository.class);
        OAuthTokenCipher cipher = new OAuthTokenCipher(keyFrom("primary-key"), List.of(keyFrom("primary-key")));
        OAuthTokenCipher otherCipher = new OAuthTokenCipher(keyFrom("other-key"), List.of(keyFrom("other-key")));
        SQLiteOAuth2AuthorizedClientService service =
            new SQLiteOAuth2AuthorizedClientService(jdbcOperations, repository, cipher);
        ClientRegistration registration = clientRegistration();
        when(repository.findByRegistrationId("twitch")).thenReturn(registration);

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("client_registration_id")).thenReturn("twitch");
        when(resultSet.getString("principal_name")).thenReturn("principal");
        when(resultSet.getString("access_token_value")).thenReturn(otherCipher.encrypt("access-token"));
        when(resultSet.getObject("access_token_issued_at")).thenReturn(Instant.now().toEpochMilli());
        when(resultSet.getObject("access_token_expires_at")).thenReturn(Instant.now().plusSeconds(3600).toEpochMilli());
        when(resultSet.getString("access_token_scopes")).thenReturn("scope:one");
        when(resultSet.getObject("refresh_token_value")).thenReturn(otherCipher.encrypt("refresh-token"));
        when(resultSet.getObject("refresh_token_issued_at")).thenReturn(Instant.now().toEpochMilli());

        doAnswer(invocation -> {
            PreparedStatementSetter setter = invocation.getArgument(1);
            setter.setValues(mock(PreparedStatement.class));
            ResultSetExtractor<?> extractor = invocation.getArgument(2);
            return extractor.extractData(resultSet);
        }).when(jdbcOperations).query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class));

        OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("twitch", "principal");

        assertThat(loaded).isNull();
        verify(jdbcOperations).update(startsWith("DELETE FROM oauth2_authorized_client"), any(PreparedStatementSetter.class));
    }

    private ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId("twitch")
            .clientId("client-id")
            .clientSecret("client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationUri("https://id.twitch.tv/oauth2/authorize")
            .tokenUri("https://id.twitch.tv/oauth2/token")
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("scope:one")
            .build();
    }

    private SecretKey keyFrom(String seed) {
        byte[] bytes = Base64.getDecoder().decode(
            Base64.getEncoder().encodeToString(seed.getBytes(StandardCharsets.UTF_8))
        );
        byte[] keyBytes = new byte[32];
        System.arraycopy(bytes, 0, keyBytes, 0, Math.min(bytes.length, keyBytes.length));
        return new SecretKeySpec(keyBytes, "AES");
    }
}
