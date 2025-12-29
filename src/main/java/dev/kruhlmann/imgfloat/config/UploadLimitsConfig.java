package dev.kruhlmann.imgfloat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.unit.DataSize;

@Configuration
public class UploadLimitsConfig {

    private final Environment environment;
    private static final long DEFAULT_UPLOAD_BYTES = DataSize.ofMegabytes(50).toBytes();
    private static final long DEFAULT_REQUEST_BYTES = DataSize.ofMegabytes(100).toBytes();

    public UploadLimitsConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public long uploadLimitBytes() {
        String value = environment.getProperty("spring.servlet.multipart.max-file-size");
        if (isTestContext()) return DEFAULT_UPLOAD_BYTES;
        if (value == null || value.isBlank()) return DEFAULT_UPLOAD_BYTES;

        return DataSize.parse(value).toBytes();
    }

    @Bean
    public long uploadRequestLimitBytes() {
        String value = environment.getProperty("spring.servlet.multipart.max-request-size");
        if (isTestContext()) return DEFAULT_REQUEST_BYTES;
        if (value == null || value.isBlank()) return DEFAULT_REQUEST_BYTES;

        return DataSize.parse(value).toBytes();
    }

    private boolean isTestContext() {
        return Boolean.parseBoolean(environment.getProperty("org.springframework.boot.test.context.SpringBootTestContextBootstrapper"));
    }
}
