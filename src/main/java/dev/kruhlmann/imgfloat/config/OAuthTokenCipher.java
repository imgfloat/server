package dev.kruhlmann.imgfloat.config;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

public class OAuthTokenCipher {

    private static final Logger LOG = LoggerFactory.getLogger(OAuthTokenCipher.class);
    private static final String KEY_ENV = "IMGFLOAT_TOKEN_ENCRYPTION_KEY";
    private static final String PREVIOUS_KEYS_ENV = "IMGFLOAT_TOKEN_ENCRYPTION_PREVIOUS_KEYS";
    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey encryptionKey;
    private final List<SecretKey> decryptionKeys;

    public OAuthTokenCipher(SecretKey encryptionKey, List<SecretKey> decryptionKeys) {
        this.encryptionKey = encryptionKey;
        this.decryptionKeys = List.copyOf(decryptionKeys);
    }

    public static OAuthTokenCipher fromEnvironment() {
        String base64Key = System.getenv(KEY_ENV);
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(KEY_ENV + " is required to encrypt OAuth tokens");
        }
        SecretKey primaryKey = decodeKey(base64Key, KEY_ENV);
        List<SecretKey> keys = new ArrayList<>();
        keys.add(primaryKey);

        String previousKeys = System.getenv(PREVIOUS_KEYS_ENV);
        if (previousKeys != null && !previousKeys.isBlank()) {
            for (String value : previousKeys.split(",")) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    keys.add(decodeKey(trimmed, PREVIOUS_KEYS_ENV));
                }
            }
        }

        return new OAuthTokenCipher(primaryKey, keys);
    }

    public static OAuthTokenCipher fromEnvironment(Environment environment) {
        String base64Key = environment.getProperty(KEY_ENV);
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(KEY_ENV + " is required to encrypt OAuth tokens");
        }
        SecretKey primaryKey = decodeKey(base64Key, KEY_ENV);
        List<SecretKey> keys = new ArrayList<>();
        keys.add(primaryKey);

        String previousKeys = environment.getProperty(PREVIOUS_KEYS_ENV);
        if (previousKeys != null && !previousKeys.isBlank()) {
            for (String value : previousKeys.split(",")) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    keys.add(decodeKey(trimmed, PREVIOUS_KEYS_ENV));
                }
            }
        }

        return new OAuthTokenCipher(primaryKey, keys);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] iv = new byte[IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt OAuth token", ex);
        }
    }

    public String decrypt(String value) {
        if (value == null) {
            return null;
        }
        if (!value.startsWith(PREFIX)) {
            return value;
        }
        byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
        if (payload.length <= IV_LENGTH_BYTES) {
            throw new IllegalStateException("Invalid encrypted OAuth token payload");
        }
        byte[] iv = new byte[IV_LENGTH_BYTES];
        byte[] ciphertext = new byte[payload.length - IV_LENGTH_BYTES];
        System.arraycopy(payload, 0, iv, 0, IV_LENGTH_BYTES);
        System.arraycopy(payload, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);
        for (SecretKey key : decryptionKeys) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
                return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            } catch (GeneralSecurityException ex) {
                LOG.debug("Failed to decrypt OAuth token with a configured key", ex);
            }
        }
        throw new IllegalStateException("Unable to decrypt OAuth token with configured keys");
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private static SecretKey decodeKey(String base64Key, String source) {
        byte[] decoded = decodeBase64(base64Key, source);
        if (decoded.length != 32) {
            throw new IllegalArgumentException(
                source + " must be a base64-encoded 256-bit (32 byte) key"
            );
        }
        return new SecretKeySpec(decoded, "AES");
    }

    private static byte[] decodeBase64(String base64Key, String source) {
        try {
            return Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException ex) {
            try {
                return Base64.getUrlDecoder().decode(base64Key);
            } catch (IllegalArgumentException urlEx) {
                ex.addSuppressed(urlEx);
                throw new IllegalArgumentException(
                    source + " must be a base64-encoded 256-bit (32 byte) key",
                    ex
                );
            }
        }
    }
}
