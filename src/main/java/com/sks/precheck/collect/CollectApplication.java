package com.sks.precheck.collect;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PreCheck 로그 수집 서버 애플리케이션 진입점.
 */
@SpringBootApplication
@EnableScheduling
public class CollectApplication {

    private static final Logger log = LogManager.getLogger(CollectApplication.class);

    public static void main(String[] args) {
        log.info("PreCheck 로그 수집 서버 기동 시작");
        SpringApplication.run(CollectApplication.class, args);
    }
}
