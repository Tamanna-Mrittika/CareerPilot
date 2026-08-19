package com.careerpilot.resume.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * A bounded executor for resume parsing -- the async story without a message broker (this
 * project is REST-only by choice). Tika/PDFBox parsing is CPU-bound; Spring's default
 * async executor spawns one unbounded thread per task, which under any real load would
 * exhaust the container. Core/max pool size and a finite queue keep this service's
 * resource use predictable regardless of upload burst size.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "resumeProcessingExecutor")
    public Executor resumeProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("resume-proc-");
        // AbortPolicy over CallerRunsPolicy deliberately: CallerRunsPolicy would make an
        // overloaded queue fall back to running the parse on the calling (HTTP request)
        // thread, which defeats the entire point of the async 202 flow under exactly the
        // load conditions it exists to handle. Abort and let the caller see the rejection
        // (see ResumeUploadService) instead.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
