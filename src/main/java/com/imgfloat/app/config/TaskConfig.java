package com.imgfloat.app.config;

import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

@Configuration
public class TaskConfig {

    @Bean(name = "assetTaskExecutor")
    public TaskExecutor assetTaskExecutor(TaskExecutorBuilder builder) {
        return builder
                .threadNamePrefix("asset-optimizer-")
                .build();
    }
}
