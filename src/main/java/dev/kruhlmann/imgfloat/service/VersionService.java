package dev.kruhlmann.imgfloat.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VersionService {

    private static final Logger LOG = LoggerFactory.getLogger(VersionService.class);
    private static final Pattern PACKAGE_VERSION_PATTERN = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");

    private final String serverVersion;
    private final String releaseVersion;

    public VersionService() throws IOException {
        this.serverVersion = resolveServerVersion();
        this.releaseVersion = normalizeReleaseVersion(serverVersion);
    }

    public String getVersion() {
        return serverVersion;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public String getReleaseTag() {
        if (releaseVersion == null || releaseVersion.isBlank()) {
            throw new IllegalStateException("Release version is not available");
        }
        String normalized = releaseVersion.startsWith("v") ? releaseVersion.substring(1) : releaseVersion;
        return "v" + normalized;
    }

    private String resolveServerVersion() {
        String pomVersion = getPomVersion();
        if (pomVersion != null && !pomVersion.isBlank()) {
            return pomVersion;
        }

        String pomXmlVersion = getPomXmlVersion();
        if (pomXmlVersion != null && !pomXmlVersion.isBlank()) {
            return pomXmlVersion;
        }

        throw new IllegalStateException("Release version is not available");
    }

    private String normalizeReleaseVersion(String baseVersion) throws IllegalStateException {
        String normalized = baseVersion.trim();
        normalized = normalized.replaceFirst("(?i)^v", "");
        normalized = normalized.replaceFirst("-SNAPSHOT$", "");
        if (normalized.isBlank()) {
            throw new IllegalStateException("Invalid version: " + baseVersion);
        }
        return normalized;
    }

    private String getPomVersion() {
        try (
            var inputStream = getClass().getResourceAsStream("/META-INF/maven/dev.kruhlmann/imgfloat/pom.properties")
        ) {
            if (inputStream == null) {
                return null;
            }
            var properties = new java.util.Properties();
            properties.load(inputStream);
            String pomVersion = properties.getProperty("version");
            if (pomVersion != null && !pomVersion.isBlank()) {
                return pomVersion.trim();
            }
        } catch (IOException e) {
            LOG.warn("Unable to read version from pom.properties", e);
        }
        return null;
    }

    private String getPomXmlVersion() {
        // Attempt to load pom.xml from the classpath (available when running from sources)
        InputStream classpathPom = getClass().getClassLoader().getResourceAsStream("pom.xml");
        if (classpathPom != null) {
            try {
                String version = extractVersionFromPom(classpathPom);
                if (version != null) {
                    return version;
                }
            } finally {
                try {
                    classpathPom.close();
                } catch (IOException e) {
                    LOG.warn("Unable to close pom.xml stream from classpath", e);
                }
            }
        }

        // Fallback to reading pom.xml from the filesystem root (common during development)
        Path pomPath = Paths.get("pom.xml");
        if (Files.exists(pomPath) && Files.isRegularFile(pomPath)) {
            try (InputStream filePom = Files.newInputStream(pomPath)) {
                return extractVersionFromPom(filePom);
            } catch (IOException e) {
                LOG.warn("Unable to read pom.xml from filesystem", e);
            }
        }

        return null;
    }

    private String extractVersionFromPom(InputStream pomInputStream) {
        if (pomInputStream == null) {
            return null;
        }
        try {
            var documentBuilderFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            var documentBuilder = documentBuilderFactory.newDocumentBuilder();
            var document = documentBuilder.parse(pomInputStream);
            var versionNodes = document.getElementsByTagName("version");
            if (versionNodes.getLength() > 0) {
                var version = versionNodes.item(0).getTextContent();
                if (version != null && !version.isBlank()) {
                    return version.trim();
                }
            }
        } catch (Exception e) {
            LOG.warn("Unable to parse version from pom.xml", e);
        }
        return null;
    }
}
