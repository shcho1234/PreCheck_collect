package com.sks.precheck.collect.common.exception;

/**
 * 로그 수집 서버에서 발생하는 예외를 표현한다.
 *
 * SFTP 수집 실패, DB 저장 실패, 파싱 실패 등 수집 처리 과정에서 발생한 예외를 이 타입으로 통일한다.
 */
public class CollectException extends RuntimeException {

    public CollectException(String message) {
        super(message);
    }

    public CollectException(String message, Throwable cause) {
        super(message, cause);
    }
}
