package dev.kruhlmann.imgfloat.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class OAuthTokenCipherTest {

    @Test
    void encryptDecryptRoundTrip() {
        OAuthTokenCipher cipher = new OAuthTokenCipher(keyFrom("primary-key"), List.of(keyFrom("primary-key")));

        String encrypted = cipher.encrypt("access-token");

        assertThat(encrypted).startsWith("v1:");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("access-token");
    }

    @Test
    void decryptPlaintextReturnsOriginalValue() {
        OAuthTokenCipher cipher = new OAuthTokenCipher(keyFrom("primary-key"), List.of(keyFrom("primary-key")));

        assertThat(cipher.decrypt("plain-token")).isEqualTo("plain-token");
    }

    @Test
    void decryptsTokensWithPreviousKey() {
        OAuthTokenCipher previousCipher = new OAuthTokenCipher(
            keyFrom("previous-key"),
            List.of(keyFrom("previous-key"))
        );
        String encrypted = previousCipher.encrypt("refresh-token");
        OAuthTokenCipher cipher = new OAuthTokenCipher(
            keyFrom("primary-key"),
            List.of(keyFrom("primary-key"), keyFrom("previous-key"))
        );

        assertThat(cipher.decrypt(encrypted)).isEqualTo("refresh-token");
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
