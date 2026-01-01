package dev.kruhlmann.imgfloat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class VersionService {
    private static final Logger LOG = LoggerFactory.getLogger(VersionService.class);
    private final String version;
    private final String releaseVersion;

    public VersionService() {
        this.version = resolveVersion();
        this.releaseVersion = normalizeReleaseVersion(this.version);
    }

    public String getVersion() {
        return version;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    private String resolveVersion() {
        String pomVersion = getPomVersion();
        if (pomVersion != null && !pomVersion.isBlank()) {
            return pomVersion;
        }

        String pomXmlVersion = getPomXmlVersion();
        if (pomXmlVersion != null && !pomXmlVersion.isBlank()) {
            return pomXmlVersion;
        }

        return "unknown";
    }

    private String normalizeReleaseVersion(String baseVersion) {
        if (baseVersion == null || baseVersion.isBlank()) {
            return "latest";
        }

        String normalized = baseVersion.trim();
        normalized = normalized.replaceFirst("(?i)^v", "");
        normalized = normalized.replaceFirst("-SNAPSHOT$", "");
        if (normalized.isBlank()) {
            return "latest";
        }
        return normalized;
    }

    private String getPomVersion() {
        try (var inputStream = getClass().getResourceAsStream("/META-INF/maven/dev.kruhlmann/imgfloat/pom.properties")) {
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
