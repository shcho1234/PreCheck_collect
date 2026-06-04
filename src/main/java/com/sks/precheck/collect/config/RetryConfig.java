package com.sks.precheck.collect.config;

import com.sks.precheck.collect.common.constants.CollectConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * 수집 서버 재시도 설정을 제공한다.
 *
 * 실패 시 10초 간격으로 최대 3회 재시도한다(최초 시도 제외).
 */
@Configuration
@EnableRetry
public class RetryConfig {

    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(CollectConstants.RETRY_DELAY_MILLISECONDS);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(CollectConstants.MAX_RETRY_COUNT + 1);
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }
}

