package com.sks.precheck.collect.service;

import java.nio.charset.Charset;
import java.util.function.BiConsumer;

/**
 * 파일 접근 추상화 인터페이스.
 *
 * 기본 구현은 SftpService(SFTP 원격 파일), 로컬 테스트용으로 LocalFileService(로컬 파일 직접 읽기)가 있다.
 * precheck.collect.mode 프로퍼티로 구현체를 선택한다.
 *   - sftp (기본값): SftpService
 *   - local: LocalFileService
 */
public interface FileReadService {

    /**
     * 파일 크기(bytes)를 반환한다.
     */
    long getFileSizeBytes(String serverIp, int port, String username, String password, String filePath);

    /**
     * filePath를 startLineNumber 라인부터 끝까지 읽어 lineConsumer로 전달한다.
     */
    void readLines(
            String serverIp,
            int port,
            String username,
            String password,
            String filePath,
            long startLineNumber,
            Charset charset,
            BiConsumer<Long, String> lineConsumer
    );
}
