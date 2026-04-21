package dev.kruhlmann.imgfloat.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AllowedDomainNormalizerTest {

    // --- normalize (strict) ---

    @Test
    void returnsEmptyListWhenNullInput() {
        assertThat(AllowedDomainNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void returnsEmptyListWhenEmptyInput() {
        assertThat(AllowedDomainNormalizer.normalize(List.of())).isEmpty();
    }

    @Test
    void normalizesHostToLowercase() {
        assertThat(AllowedDomainNormalizer.normalize(List.of("EXAMPLE.COM")))
                .containsExactly("example.com");
    }

    @Test
    void preservesPort() {
        assertThat(AllowedDomainNormalizer.normalize(List.of("api.example.com:8080")))
                .containsExactly("api.example.com:8080");
    }

    @Test
    void stripsSchemeWhenProvided() {
        assertThat(AllowedDomainNormalizer.normalize(List.of("https://example.com")))
                .containsExactly("example.com");
    }

    @Test
    void deduplicatesEntries() {
        List<String> result = AllowedDomainNormalizer.normalize(List.of("example.com", "EXAMPLE.COM", "example.com"));
        assertThat(result).containsExactly("example.com");
    }

    @Test
    void throwsOn400WhenDomainInvalid() {
        assertThatThrownBy(() -> AllowedDomainNormalizer.normalize(List.of("not a domain!!!")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void throwsWhenExceedsMaxDomains() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 33; i++) {
            many.add("host" + i + ".example.com");
        }
        assertThatThrownBy(() -> AllowedDomainNormalizer.normalize(many))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("32");
    }

    @Test
    void skipsNullAndBlankEntries() {
        List<String> input = new ArrayList<>();
        input.add(null);
        input.add("  ");
        input.add("example.com");
        assertThat(AllowedDomainNormalizer.normalize(input)).containsExactly("example.com");
    }

    @Test
    void resultIsImmutable() {
        List<String> result = AllowedDomainNormalizer.normalize(List.of("example.com"));
        assertThatThrownBy(() -> result.add("other.com"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- normalizeLenient ---

    @Test
    void lenientSkipsInvalidDomainsWithoutThrowing() {
        List<String> result = AllowedDomainNormalizer.normalizeLenient(List.of("valid.com", "not a domain!!!"));
        assertThat(result).containsExactly("valid.com");
    }

    @Test
    void lenientStopsAtMaxDomains() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            many.add("host" + i + ".example.com");
        }
        List<String> result = AllowedDomainNormalizer.normalizeLenient(many);
        assertThat(result).hasSize(32);
    }

    @Test
    void lenientReturnsEmptyForNullInput() {
        assertThat(AllowedDomainNormalizer.normalizeLenient(null)).isEmpty();
    }
}
