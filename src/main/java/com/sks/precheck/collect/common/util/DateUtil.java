package com.sks.precheck.collect.common.util;

import com.sks.precheck.collect.common.constants.CollectConstants;
import com.sks.precheck.collect.common.exception.CollectException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 수집 서버에서 사용하는 날짜/시간 파싱 및 포맷 변환 유틸리티.
 */
public final class DateUtil {

    private static final DateTimeFormatter LOG_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern(CollectConstants.LOG_TIMESTAMP_FORMAT);

    private static final DateTimeFormatter COLLECT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern(CollectConstants.COLLECT_DATE_FORMAT);

    private DateUtil() {
    }

    /**
     * 정규화 로그의 timestamp 문자열을 LocalDateTime으로 파싱한다.
     *
     * @param timestampText 로그 timestamp 문자열 (yyyy/MM/dd HH:mm:ss.SSS)
     * @return 파싱된 LocalDateTime
     * @throws CollectException timestamp 형식이 올바르지 않은 경우
     */
    public static LocalDateTime parseLogTimestamp(String timestampText) {
        try {
            return LocalDateTime.parse(timestampText, LOG_TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CollectException("로그 timestamp 파싱 실패: " + timestampText, e);
        }
    }

    /**
     * 수집 실행 날짜를 yyyyMMdd 문자열로 포맷한다.
     *
     * @param date LocalDate
     * @return yyyyMMdd 형식 문자열
     */
    public static String formatCollectDate(LocalDate date) {
        return date.format(COLLECT_DATE_FORMATTER);
    }

    /**
     * 오늘 날짜를 yyyyMMdd 문자열로 반환한다.
     *
     * @return yyyyMMdd 형식 문자열
     */
    public static String todayCollectDate() {
        return formatCollectDate(LocalDate.now());
    }

    /**
     * 수집 대상 파일 경로 안의 날짜 자리표시자({@value CollectConstants#FILE_PATH_DATE_PLACEHOLDER})를
     * 수집 날짜(yyyyMMdd)로 치환한다.
     *
     * 예) "/logs/test.yyyymmdd" + "20260612" → "/logs/test.20260612"
     * 자리표시자가 없으면 원본 경로를 그대로 반환한다.
     *
     * @param filePathTemplate 스케줄에 정의된 파일 경로 (자리표시자 포함 가능)
     * @param collectDate 수집 날짜 (yyyyMMdd)
     * @return 날짜가 치환된 실제 파일 경로
     */
    public static String resolveFilePath(String filePathTemplate, String collectDate) {
        return filePathTemplate.replace(CollectConstants.FILE_PATH_DATE_PLACEHOLDER, collectDate);
    }
}
