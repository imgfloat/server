package dev.kruhlmann.imgfloat.config;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

public class SQLiteOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {
    private static final String TABLE_NAME = "oauth2_authorized_client";

    private final JdbcOperations jdbcOperations;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final RowMapper<OAuth2AuthorizedClient> rowMapper;

    public SQLiteOAuth2AuthorizedClientService(JdbcOperations jdbcOperations,
                                               ClientRegistrationRepository clientRegistrationRepository) {
        this.jdbcOperations = jdbcOperations;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.rowMapper = (rs, rowNum) -> {
            String registrationId = rs.getString("client_registration_id");
            String principalName = rs.getString("principal_name");
            ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(registrationId);
            if (registration == null) {
                return null;
            }

            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    rs.getString("access_token_value"),
                    toInstant(rs.getObject("access_token_issued_at")),
                    toInstant(rs.getObject("access_token_expires_at")),
                    scopesFrom(rs.getString("access_token_scopes"))
            );

            Object refreshValue = rs.getObject("refresh_token_value");
            OAuth2RefreshToken refreshToken = refreshValue == null
                    ? null
                    : new OAuth2RefreshToken(
                        refreshValue.toString(),
                        toInstant(rs.getObject("refresh_token_issued_at"))
                    );

            return new OAuth2AuthorizedClient(registration, principalName, accessToken, refreshToken);
        };
    }

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId, String principalName) {
        return jdbcOperations.query(
                "SELECT client_registration_id, principal_name, access_token_value, access_token_issued_at, access_token_expires_at, access_token_scopes, refresh_token_value, refresh_token_issued_at " +
                        "FROM " + TABLE_NAME + " WHERE client_registration_id = ? AND principal_name = ?",
                ps -> {
                    ps.setString(1, clientRegistrationId);
                    ps.setString(2, principalName);
                },
                rs -> rs.next() ? (T) rowMapper.mapRow(rs, 0) : null
        );
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        try {
            int updated = jdbcOperations.update("""
                    INSERT INTO oauth2_authorized_client (
                        client_registration_id, principal_name,
                        access_token_value, access_token_issued_at, access_token_expires_at, access_token_scopes,
                        refresh_token_value, refresh_token_issued_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(client_registration_id, principal_name) DO UPDATE SET
                        access_token_value=excluded.access_token_value,
                        access_token_issued_at=excluded.access_token_issued_at,
                        access_token_expires_at=excluded.access_token_expires_at,
                        access_token_scopes=excluded.access_token_scopes,
                        refresh_token_value=excluded.refresh_token_value,
                        refresh_token_issued_at=excluded.refresh_token_issued_at
                    """,
                    preparedStatement -> {
                        preparedStatement.setString(1, authorizedClient.getClientRegistration().getRegistrationId());
                        preparedStatement.setString(2, principal.getName());
                        setToken(preparedStatement, 3, authorizedClient.getAccessToken());
                        preparedStatement.setObject(5, toEpochMillis(authorizedClient.getAccessToken().getExpiresAt()), java.sql.Types.BIGINT);
                        preparedStatement.setString(6, scopesToString(authorizedClient.getAccessToken().getScopes()));
                        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();
                        if (refreshToken != null) {
                            preparedStatement.setString(7, refreshToken.getTokenValue());
                            preparedStatement.setObject(8, toEpochMillis(refreshToken.getIssuedAt()), java.sql.Types.BIGINT);
                        } else {
                            preparedStatement.setNull(7, java.sql.Types.VARCHAR);
                            preparedStatement.setNull(8, java.sql.Types.BIGINT);
                        }
                    });
        } catch (DataAccessException ex) {
            throw ex;
        }
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
        jdbcOperations.update("DELETE FROM " + TABLE_NAME + " WHERE client_registration_id = ? AND principal_name = ?",
                preparedStatement -> {
                    preparedStatement.setString(1, clientRegistrationId);
                    preparedStatement.setString(2, principalName);
                });
    }

    private void setToken(java.sql.PreparedStatement ps, int startIndex, OAuth2AccessToken token) throws java.sql.SQLException {
        ps.setString(startIndex, token.getTokenValue());
        ps.setObject(startIndex + 1, toEpochMillis(token.getIssuedAt()), java.sql.Types.BIGINT);
    }

    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof Number num) {
            return Instant.ofEpochMilli(num.longValue());
        }
        String text = value.toString();
        try {
            long millis = Long.parseLong(text);
            return Instant.ofEpochMilli(millis);
        } catch (NumberFormatException ex) {
            return Timestamp.valueOf(text).toInstant();
        }
    }

    private Long toEpochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private Set<String> scopesFrom(String scopeString) {
        if (scopeString == null || scopeString.isBlank()) {
            return Set.of();
        }
        return Stream.of(scopeString.split(" "))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private String scopesToString(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return null;
        }
        return scopes.stream().sorted().collect(Collectors.joining(" "));
    }
}
