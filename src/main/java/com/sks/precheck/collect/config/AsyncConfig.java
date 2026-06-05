package com.sks.precheck.collect.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 수집 비동기 실행 설정.
 *
 * 스케줄러 스레드가 재시도 backoff sleep에 의해 블로킹되지 않도록
 * 각 수집 작업을 별도 스레드풀에서 실행한다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "collectExecutor")
    public Executor collectExecutor(
            @Value("${precheck.collect.async.core-pool-size:5}") int corePoolSize,
            @Value("${precheck.collect.async.max-pool-size:20}") int maxPoolSize,
            @Value("${precheck.collect.async.queue-capacity:100}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("collect-async-");
        executor.initialize();
        return executor;
    }
}
