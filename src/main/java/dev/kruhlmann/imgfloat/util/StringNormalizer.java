package dev.kruhlmann.imgfloat.util;

import java.util.Locale;

/**
 * Shared string normalization utilities. Centralises {@link Locale#ROOT} lowercase
 * conversions so that individual classes do not duplicate the same one-liner.
 */
public final class StringNormalizer {

    private StringNormalizer() {}

    /**
     * Returns {@code value.toLowerCase(Locale.ROOT)}, or {@code null} if {@code value} is {@code null}.
     */
    public static String toLowerCaseRoot(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
