package dev.kruhlmann.imgfloat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SystemEnvironmentValidator implements ApplicationRunner {

    @Value("${IMGFLOAT_UPLOAD_MAX_BYTES:#{null}}")
    private Long maxUploadBytes;

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = new ArrayList<>();

        if (maxUploadBytes == null)
            missing.add("IMGFLOAT_UPLOAD_MAX_BYTES");

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Missing required environment variables:\n - " +
                String.join("\n - ", missing)
            );
        }
    }
}
