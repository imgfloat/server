package dev.kruhlmann.imgfloat.util;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared utility for normalizing and validating the allowed-domain lists that
 * gate {@code fetch()} calls inside broadcast script workers.
 *
 * <p>Two modes are provided:
 * <ul>
 *   <li>{@link #normalize} – strict; throws {@link ResponseStatusException} (400) on bad input.
 *       Use this for user-submitted data (API requests).</li>
 *   <li>{@link #normalizeLenient} – lenient; silently skips invalid entries.
 *       Use this when reading seed/marketplace data from disk.</li>
 * </ul>
 */
public final class AllowedDomainNormalizer {

    private static final Logger LOG = LoggerFactory.getLogger(AllowedDomainNormalizer.class);
    private static final int MAX_DOMAINS = 32;
    private static final Pattern VALID_DOMAIN = Pattern.compile("^[a-z0-9.-]+(?::[0-9]{1,5})?$");

    private AllowedDomainNormalizer() {}

    /**
     * Strict normalization: invalid entries cause a {@link ResponseStatusException} (400).
     */
    public static List<String> normalize(List<String> requestedDomains) {
        if (requestedDomains == null || requestedDomains.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String raw : requestedDomains) {
            if (raw == null) {
                continue;
            }
            String candidate = raw.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            String normalized = parseAndNormalize(candidate);
            if (normalized == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Invalid allowed domain: " + candidate);
            }
            if (!VALID_DOMAIN.matcher(normalized).matches()) {
                throw new ResponseStatusException(BAD_REQUEST, "Invalid allowed domain: " + candidate);
            }
            if (result.contains(normalized)) {
                continue;
            }
            if (result.size() >= MAX_DOMAINS) {
                throw new ResponseStatusException(
                    BAD_REQUEST,
                    "A maximum of " + MAX_DOMAINS + " allowed domains are supported per script asset"
                );
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    /**
     * Lenient normalization: invalid entries are skipped with a warning.
     */
    public static List<String> normalizeLenient(List<String> requestedDomains) {
        if (requestedDomains == null || requestedDomains.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String raw : requestedDomains) {
            if (raw == null) {
                continue;
            }
            String candidate = raw.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            String normalized = parseAndNormalize(candidate);
            if (normalized == null || !VALID_DOMAIN.matcher(normalized).matches()) {
                LOG.warn("Skipping invalid allowed domain {}", candidate);
                continue;
            }
            if (result.contains(normalized)) {
                continue;
            }
            if (result.size() >= MAX_DOMAINS) {
                LOG.warn("Trimming allowed domains at limit of {}", MAX_DOMAINS);
                break;
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private static String parseAndNormalize(String candidate) {
        String withScheme = candidate.contains("://") ? candidate : "https://" + candidate;
        URI uri;
        try {
            uri = URI.create(withScheme);
        } catch (IllegalArgumentException ex) {
            LOG.warn("Unable to parse allowed domain {}", candidate, ex);
            return null;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return null;
        }
        String domain = host.toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port > 0) {
            domain = domain + ":" + port;
        }
        return domain;
    }
}
