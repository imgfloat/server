package dev.kruhlmann.imgfloat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import dev.kruhlmann.imgfloat.model.api.response.ScriptMarketplaceEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarketplaceScriptSeedLoaderTest {

    @TempDir
    Path tempDir;

    private void writeScript(Path dir, String name, String description, String source) throws IOException {
        Files.createDirectories(dir);
        String metaJson = description != null
                ? "{\"name\":\"" + name + "\",\"description\":\"" + description + "\"}"
                : "{\"name\":\"" + name + "\"}";
        Files.writeString(dir.resolve("metadata.json"), metaJson);
        Files.writeString(dir.resolve("source.js"), source);
    }

    @Test
    void returnsEmptyListWhenRootPathIsNonExistentDirectory() throws IOException {
        // Pass a path that does not exist and is not the fallback doc/marketplace-scripts
        Path nonExistent = tempDir.resolve("does-not-exist");
        MarketplaceScriptSeedLoader loader = new MarketplaceScriptSeedLoader(nonExistent.toString());
        assertThat(loader.listEntriesForQuery(null)).isEmpty();
    }

    @Test
    void loadsScriptFromDirectory() throws IOException {
        Path scriptDir = tempDir.resolve("my-script");
        writeScript(scriptDir, "My Script", "A test script", "console.log('hi');");

        MarketplaceScriptSeedLoader loader = new MarketplaceScriptSeedLoader(tempDir.toString());
        List<ScriptMarketplaceEntry> entries = loader.listEntriesForQuery(null);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).id()).isEqualTo("my-script");
        assertThat(entries.get(0).name()).isEqualTo("My Script");
        assertThat(entries.get(0).description()).isEqualTo("A test script");
    }

    @Test
    void skipsDirectoryWithMissingMetadata() throws IOException {
        Path scriptDir = tempDir.resolve("no-meta");
        Files.createDirectories(scriptDir);
        Files.writeString(scriptDir.resolve("source.js"), "/* source */");

        MarketplaceScriptSeedLoader loader = new MarketplaceScriptSeedLoader(tempDir.toString());
        assertThat(loader.listEntriesForQuery(null)).isEmpty();
    }

    @Test
    void skipsDirectoryWithMissingSourceJs() throws IOException {
        Path scriptDir = tempDir.resolve("no-source");
        Files.createDirectories(scriptDir);
        Files.writeString(scriptDir.resolve("metadata.json"), "{\"name\":\"Test\"}");

        MarketplaceScriptSeedLoader loader = new MarketplaceScriptSeedLoader(tempDir.toString());
        assertThat(loader.listEntriesForQuery(null)).isEmpty();
    }

    @Test
    void skipsDirectoryWithBlankName() throws IOException {
        Path scriptDir = tempDir.resolve("blank-name");
        Files.createDirectories(scriptDir);
        Files.writeString(scriptDir.resolve("metadata.json"), "{\"name\":\"  \"}");
        Files.writeString(scriptDir.resolve("source.js"), "/* source */");

        MarketplaceScriptSeedLoader loader = new MarketplaceScriptSeedLoader(tempDir.toString());
        assertThat(loader.listEntriesForQuery(null)).isEmpty();
    }

    @Test
    void filtersByQueryOnNameAndDescription() throws IOException {
        Path dir1 = tempDir.resolve("alpha-script");
        writeScript(dir1, "Alpha Script", "does alpha things", "/* */");
        Path dir2 = tempDir.resolve("beta-script");
        writeScript(dir2, "Beta Script", "does beta things", "/* */");

        MarketplaceScriptSeedLoader loader = new MarketplaceScriptSeedLoader(tempDir.toString());
        assertThat(loader.listEntriesForQuery("alpha")).hasSize(1);
        assertThat(loader.listEntriesForQuery("beta")).hasSize(1);
        assertThat(loader.listEntriesForQuery("script")).hasSize(2);
        assertThat(loader.listEntriesForQuery("zzznotfound")).isEmpty();
    }

    @Test
    void findByIdReturnsCorrectScript() throws IOException {
        Path scriptDir = tempDir.resolve("find-me");
        writeScript(scriptDir, "Find Me", null, "/* */");

        MarketplaceScriptSeedLoader loader = new MarketplaceScriptSeedLoader(tempDir.toString());
        Optional<MarketplaceScriptSeedLoader.SeedScript> found = loader.findById("find-me");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Find Me");
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() throws IOException {
        MarketplaceScriptSeedLoader loader = new MarketplaceScriptSeedLoader(tempDir.toString());
        assertThat(loader.findById("does-not-exist")).isEmpty();
    }

    @Test
    void loadsLogoWhenPresent() throws IOException {
        Path scriptDir = tempDir.resolve("with-logo");
        writeScript(scriptDir, "With Logo", null, "/* */");
        // write a minimal PNG (1x1 white pixel)
        byte[] minimalPng = new byte[]{
            (byte)0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, // PNG signature
            0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte)0x90, 0x77, 0x53,
            (byte)0xde, 0x00, 0x00, 0x00, 0x0c, 0x49, 0x44, 0x41, // IDAT chunk
            0x54, 0x08, (byte)0xd7, 0x63, (byte)0xf8, (byte)0xcf, (byte)0xc0, 0x00,
            0x00, 0x00, 0x02, 0x00, 0x01, (byte)0xe2, 0x21, (byte)0xbc,
            0x33, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, // IEND chunk
            0x44, (byte)0xae, 0x42, 0x60, (byte)0x82
        };
        Files.write(scriptDir.resolve("logo.png"), minimalPng);

        MarketplaceScriptSeedLoader loader = new MarketplaceScriptSeedLoader(tempDir.toString());
        Optional<MarketplaceScriptSeedLoader.SeedScript> found = loader.findById("with-logo");
        assertThat(found).isPresent();
        assertThat(found.get().loadLogo()).isPresent();
        assertThat(found.get().entry().logoUrl()).isNotNull();
    }

    @Test
    void normalizesAllowedDomains() throws IOException {
        Path scriptDir = tempDir.resolve("domains-script");
        Files.createDirectories(scriptDir);
        Files.writeString(scriptDir.resolve("metadata.json"),
                "{\"name\":\"Domains\",\"allowedDomains\":[\"EXAMPLE.COM\",\"api.foo.bar:8080\",\"duplicate.com\",\"duplicate.com\"]}");
        Files.writeString(scriptDir.resolve("source.js"), "/* */");

        MarketplaceScriptSeedLoader loader = new MarketplaceScriptSeedLoader(tempDir.toString());
        Optional<MarketplaceScriptSeedLoader.SeedScript> found = loader.findById("domains-script");
        assertThat(found).isPresent();
        List<String> domains = found.get().allowedDomains();
        assertThat(domains).contains("example.com", "api.foo.bar:8080");
        assertThat(domains).doesNotHaveDuplicates();
        assertThat(domains).hasSize(3); // EXAMPLE.COM + api.foo.bar:8080 + duplicate.com (deduped)
    }

    @Test
    void loadsSourceContent() throws IOException {
        Path scriptDir = tempDir.resolve("source-test");
        writeScript(scriptDir, "Source Test", null, "console.log('loaded');");

        MarketplaceScriptSeedLoader loader = new MarketplaceScriptSeedLoader(tempDir.toString());
        Optional<MarketplaceScriptSeedLoader.SeedScript> found = loader.findById("source-test");
        assertThat(found).isPresent();
        assertThat(found.get().loadSource()).isPresent();
        assertThat(new String(found.get().loadSource().get().bytes())).contains("loaded");
    }

    @Test
    void loadsAttachments() throws IOException {
        Path scriptDir = tempDir.resolve("with-attachments");
        writeScript(scriptDir, "With Attachments", null, "/* */");
        Path attachmentsDir = scriptDir.resolve("attachments");
        Files.createDirectories(attachmentsDir);
        Files.writeString(attachmentsDir.resolve("data.json"), "{\"key\":\"value\"}");

        MarketplaceScriptSeedLoader loader = new MarketplaceScriptSeedLoader(tempDir.toString());
        Optional<MarketplaceScriptSeedLoader.SeedScript> found = loader.findById("with-attachments");
        assertThat(found).isPresent();
        assertThat(found.get().attachments()).hasSize(1);
        assertThat(found.get().attachments().get(0).name()).isEqualTo("data.json");
    }
}
