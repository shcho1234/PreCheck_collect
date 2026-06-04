package com.sks.precheck.collect.common.constants;

/**
 * 로그 수집 서버 공통 상수.
 *
 * 파일 크기 제한, 재시도 정책, 날짜 포맷, 로그 타입/상태, Y/N 값, 제외 사유 등을 정의한다.
 */
public final class CollectConstants {

    public static final long INIT_COLLECT_SIZE_LIMIT_BYTES = 300L * 1024 * 1024;
    public static final long PART_COLLECT_SIZE_LIMIT_BYTES = 50L * 1024 * 1024;

    public static final int MAX_RETRY_COUNT = 3;
    public static final long RETRY_DELAY_MILLISECONDS = 10_000L;

    public static final String LOG_TIMESTAMP_FORMAT = "yyyy/MM/dd HH:mm:ss.SSS";
    public static final String COLLECT_DATE_FORMAT = "yyyyMMdd";

    public static final String LOG_TYPE_TEXT = "문구";
    public static final String LOG_TYPE_INFO = "정보";
    public static final String LOG_TYPE_DATE = "날짜";
    public static final String LOG_TYPE_NUMERIC = "수치";
    public static final String LOG_TYPE_EXIST = "존재";

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAIL = "FAIL";
    public static final String STATUS_SKIP = "SKIP";

    public static final String YN_YES = "Y";
    public static final String YN_NO = "N";

    public static final String EXCLUDE_REASON_INIT_SIZE = "INIT_SIZE";
    public static final String EXCLUDE_REASON_PART_SIZE = "PART_SIZE";

    private CollectConstants() {
    }
}

