package dev.kruhlmann.imgfloat.service.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FfmpegService {

    private static final Logger logger = LoggerFactory.getLogger(FfmpegService.class);

    public Optional<VideoDimensions> extractVideoDimensions(byte[] bytes) {
        return Optional.ofNullable(withTempFile(bytes, ".bin", (input) -> {
            List<String> command = List.of(
                "ffprobe",
                "-v",
                "error",
                "-select_streams",
                "v:0",
                "-show_entries",
                "stream=width,height",
                "-of",
                "csv=p=0:s=x",
                input.toString()
            );
            ProcessResult result = run(command);
            if (result.exitCode() != 0) {
                logger.warn("ffprobe failed: {}", result.output());
                return null;
            }
            String output = result.output().trim();
            if (output.isBlank() || !output.contains("x")) {
                return null;
            }
            String[] parts = output.split("x", 2);
            try {
                int width = Integer.parseInt(parts[0].trim());
                int height = Integer.parseInt(parts[1].trim());
                return new VideoDimensions(width, height);
            } catch (NumberFormatException e) {
                logger.warn("Unable to parse ffprobe output: {}", output, e);
                return null;
            }
        }));
    }

    public Optional<byte[]> extractVideoPreview(byte[] bytes) {
        return Optional.ofNullable(withTempFile(bytes, ".bin", (input) -> {
            Path output = Files.createTempFile("imgfloat-preview", ".png");
            try {
                List<String> command = List.of(
                    "ffmpeg",
                    "-y",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-i",
                    input.toString(),
                    "-frames:v",
                    "1",
                    "-f",
                    "image2",
                    "-vcodec",
                    "png",
                    output.toString()
                );
                ProcessResult result = run(command);
                if (result.exitCode() != 0) {
                    logger.warn("ffmpeg preview failed: {}", result.output());
                    return null;
                }
                return Files.readAllBytes(output);
            } finally {
                Files.deleteIfExists(output);
            }
        }));
    }

    public Optional<byte[]> transcodeGifToMp4(byte[] bytes) {
        return Optional.ofNullable(withTempFile(bytes, ".gif", (input) -> {
            Path output = Files.createTempFile("imgfloat-transcode", ".mp4");
            try {
                List<String> command = List.of(
                    "ffmpeg",
                    "-y",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-i",
                    input.toString(),
                    "-movflags",
                    "+faststart",
                    "-pix_fmt",
                    "yuv420p",
                    "-vf",
                    "scale=trunc(iw/2)*2:trunc(ih/2)*2",
                    output.toString()
                );
                ProcessResult result = run(command);
                if (result.exitCode() != 0) {
                    logger.warn("ffmpeg transcode failed: {}", result.output());
                    return null;
                }
                return Files.readAllBytes(output);
            } finally {
                Files.deleteIfExists(output);
            }
        }));
    }

    private <T> T withTempFile(byte[] bytes, String suffix, TempFileHandler<T> handler) {
        Path input = null;
        try {
            input = Files.createTempFile("imgfloat-input", suffix);
            Files.write(input, bytes);
            return handler.handle(input);
        } catch (IOException e) {
            logger.warn("Unable to create temporary media file", e);
            return null;
        } finally {
            if (input != null) {
                try {
                    Files.deleteIfExists(input);
                } catch (IOException e) {
                    logger.warn("Unable to delete temporary file {}", input, e);
                }
            }
        }
    }

    private ProcessResult run(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (InputStream stream = process.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            stream.transferTo(baos);
            output = baos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            output = "";
        }
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exitCode = -1;
        }
        return new ProcessResult(exitCode, output);
    }

    private interface TempFileHandler<T> {
        T handle(Path input) throws IOException;
    }

    private record ProcessResult(int exitCode, String output) {}

    public record VideoDimensions(int width, int height) {}
}
