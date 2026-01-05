package dev.kruhlmann.imgfloat.util;

import java.util.regex.Pattern;

public final class LogSanitizer {

    private static final Pattern NEWLINE_CHARACTERS = Pattern.compile("[\\r\\n]+");

    private LogSanitizer() {}

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return NEWLINE_CHARACTERS.matcher(value).replaceAll("_");
    }
}
