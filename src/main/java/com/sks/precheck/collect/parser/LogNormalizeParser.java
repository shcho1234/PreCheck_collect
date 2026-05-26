package com.sks.precheck.collect.parser;

import com.sks.precheck.collect.common.constants.CollectConstants;
import com.sks.precheck.collect.common.util.DateUtil;
import com.sks.precheck.collect.domain.CollectLog;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 정규화 로그 형식(@@@...@@@)을 파싱하여 CollectLog 도메인 객체로 변환하는 파서.
 *
 * 정규화 로그 형식:
 *   @@@[yyyy/MM/dd HH:mm:ss.SSS][로그타입][LOG_ID]|로그내용|[$수치값$]@@@
 *
 *   예시 (텍스트형):  @@@[2024/01/15 09:30:00.123][TEXT][SYS_START]|시스템 시작 완료|@@@
 *   예시 (수치형):    @@@[2024/01/15 09:30:00.123][NUMERIC][CPU_USAGE]|CPU 사용률||$85.3$@@@
 *
 * 파싱 규칙:
 *   - 한 라인에 정규화 로그(@@@...@@@)가 1건만 존재해야 한다.
 *   - 시작(@@@)은 있으나 종료(@@@)가 없으면 해당 라인을 무시한다.
 *   - 포맷 불일치, 지원하지 않는 로그 타입, LOG_ID 형식 오류 등도 무시하고 null을 반환한다.
 *   - 무시된 라인은 WARN 로그로 기록하여 운영 중 원인 파악을 돕는다.
 *
 * 지원하는 로그 타입 (CollectConstants 참조):
 *   TEXT, INFO, DATE, NUMERIC, EXIST
 *   NUMERIC 타입은 $값$ 형식의 수치 토큰이 필수이다.
 */
public class LogNormalizeParser {

    private static final Logger log = LogManager.getLogger(LogNormalizeParser.class);

    /**
     * 정규화 로그 전체 패턴.
     *
     * 구성:
     *   ^@@@                           : 라인 시작 후 바로 @@@
     *   \[timestamp\]                  : yyyy/MM/dd HH:mm:ss.SSS 형식
     *   \[logType\]                    : ] 이외 문자로 구성된 로그 타입
     *   \[logId\]                      : ] 이외 문자로 구성된 LOG ID
     *   |logContent|                   : | 이외 문자로 구성된 로그 내용 (빈 문자열 허용)
     *   ($logValueToken$)?             : $로 감싼 수치 토큰 (선택, NUMERIC 타입에만 사용)
     *   @@@$                           : @@@로 끝
     */
    private static final Pattern NORMALIZED_LOG_PATTERN = Pattern.compile(
            "^@@@\\[(?<timestamp>\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\]" +
                    "\\[(?<logType>[^\\]]+)\\]" +
                    "\\[(?<logId>[^\\]]+)\\]" +
                    "\\|(?<logContent>[^|]*)\\|" +
                    "(?<logValueToken>\\$[^$]+\\$)?" +
                    "@@@$"
    );

    // LOG_ID는 영문(대소문자)·숫자·언더스코어·한글·공백을 허용하며 최대 30자이다.
    private static final Pattern LOG_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_\\p{IsHangul} ]{1,30}$");

    // $값$ 형식의 수치 토큰 패턴. 내부 값을 "value" 그룹으로 추출한다.
    private static final Pattern LOG_VALUE_PATTERN = Pattern.compile("^\\$(?<value>[^$]+)\\$$");

    /**
     * 로컬 파일을 처음부터 끝까지 읽으며 정규화 로그를 추출한다.
     *
     * 테스트·단독 실행용 메서드이다. 운영 수집은 SftpService를 통해 원격 파일을 읽는다.
     * 파일 전체를 읽어 파싱된 CollectLog 목록을 반환한다.
     *
     * @param filePath 로컬 로그 파일 경로
     * @return 파싱된 CollectLog 목록 (정규화 로그가 없으면 빈 리스트)
     * @throws IOException 파일 읽기 실패 시
     */
    public List<CollectLog> parseFile(Path filePath) throws IOException {
        List<CollectLog> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            long lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                CollectLog collectLog = parseNormalizedLogFromLine(line, lineNumber);
                if (collectLog != null) {
                    result.add(collectLog);
                }
            }
        }
        return result;
    }

    /**
     * 로그 한 라인에서 정규화 로그(@@@...@@@)를 찾아 CollectLog로 변환한다.
     *
     * 처리 순서:
     *   1. @@@의 시작 위치를 찾는다. 없으면 null 반환.
     *   2. 종료 @@@의 위치를 찾는다. 없으면 무시(null 반환).
     *   3. 한 라인에 @@@가 3개 이상이면(로그 2건 이상) 무시.
     *   4. 추출한 rawLog 문자열을 정규식으로 파싱하여 각 필드를 추출한다.
     *   5. 로그 타입이 지원 목록에 있는지 검증한다.
     *   6. LOG_ID 형식을 검증한다.
     *   7. timestamp를 LocalDateTime으로 변환한다.
     *   8. NUMERIC 타입이면 수치 토큰을 BigDecimal로 파싱한다.
     *   9. CollectLog를 생성하여 반환한다.
     *
     * @param line       파일의 한 라인 문자열
     * @param lineNumber 파일 내 라인번호 (1-based, 로그·경고 메시지에 사용)
     * @return 파싱 성공 시 CollectLog, 정규화 로그가 없거나 무시 대상이면 null
     */
    public CollectLog parseNormalizedLogFromLine(String line, long lineNumber) {

        // ── Step 1. 빈 라인 및 @@@가 없는 라인 조기 탈출 ──────────────────────
        if (line == null || line.isEmpty()) {
            return null;
        }
        int start = line.indexOf("@@@");
        if (start < 0) {
            return null;
        }

        // ── Step 2. 종료 @@@ 위치 확인 ───────────────────────────────────────
        // start + 3 이후에서 검색하여 시작 @@@와 종료 @@@를 구분한다.
        int end = line.indexOf("@@@", start + 3);
        if (end < 0) {
            log.warn("정규화 로그 종료(@@@) 누락으로 무시 - lineNumber: {}", lineNumber);
            return null;
        }

        // ── Step 3. 한 라인에 @@@가 3개 이상인 경우 무시 ────────────────────
        // 종료 @@@ 이후에 또 @@@가 있으면 로그가 2건 이상 섞인 비정상 라인이다.
        int afterEnd = end + 3;
        if (line.indexOf("@@@", afterEnd) >= 0) {
            log.warn("한 라인에 정규화 로그가 2건 이상으로 무시 - lineNumber: {}", lineNumber);
            return null;
        }

        // ── Step 4. rawLog 추출 및 정규식 파싱 ──────────────────────────────
        // @@@...@@@ 전체를 잘라내어 NORMALIZED_LOG_PATTERN에 매칭한다.
        String rawLog = line.substring(start, afterEnd);
        Matcher matcher = NORMALIZED_LOG_PATTERN.matcher(rawLog);
        if (!matcher.matches()) {
            log.warn("정규화 로그 포맷 불일치로 무시 - lineNumber: {}, rawLog: {}", lineNumber, rawLog);
            return null;
        }

        String timestampText = matcher.group("timestamp");
        String logType       = matcher.group("logType");
        String logId         = matcher.group("logId");
        String logContent    = matcher.group("logContent");
        String logValueToken = matcher.group("logValueToken"); // NUMERIC이 아니면 null

        // ── Step 5. 로그 타입 검증 ───────────────────────────────────────────
        // CollectConstants에 정의된 타입(TEXT, INFO, DATE, NUMERIC, EXIST) 외에는 무시한다.
        if (!isSupportedLogType(logType)) {
            log.warn("정규화 로그 타입 불일치로 무시 - lineNumber: {}, logType: {}", lineNumber, logType);
            return null;
        }

        // ── Step 6. LOG_ID 형식 검증 ─────────────────────────────────────────
        // 대문자·숫자·언더스코어, 최대 30자. 소문자나 특수문자가 포함되면 무시한다.
        if (!LOG_ID_PATTERN.matcher(logId).matches()) {
            log.warn("LOG_ID 형식 불일치로 무시 - lineNumber: {}, logId: {}", lineNumber, logId);
            return null;
        }

        // ── Step 7. timestamp 파싱 ───────────────────────────────────────────
        // DateUtil.parseLogTimestamp()가 파싱하며, 실패하면 RuntimeException을 던진다.
        LocalDateTime logTimestamp;
        try {
            logTimestamp = DateUtil.parseLogTimestamp(timestampText);
        } catch (RuntimeException e) {
            log.warn("timestamp 파싱 실패로 무시 - lineNumber: {}, timestamp: {}", lineNumber, timestampText);
            return null;
        }

        // ── Step 8. NUMERIC 타입 수치 토큰 파싱 ─────────────────────────────
        // NUMERIC 타입은 $값$ 형식의 수치 토큰이 필수이다.
        // 토큰이 없거나 숫자로 변환할 수 없으면 무시한다.
        BigDecimal logValue = null;
        if (CollectConstants.LOG_TYPE_NUMERIC.equals(logType)) {
            if (logValueToken == null || logValueToken.isEmpty()) {
                log.warn("수치형 로그 값 누락으로 무시 - lineNumber: {}, rawLog: {}", lineNumber, rawLog);
                return null;
            }
            logValue = parseLogValue(logValueToken, lineNumber);
            if (logValue == null) {
                return null;
            }
        }

        // ── Step 9. CollectLog 생성 ──────────────────────────────────────────
        // 파서가 알 수 있는 필드(로그 고유 정보)만 채운다.
        // 서버 ID, 파일 경로, 수집일시 등 수집 맥락 정보는 CollectRetryService에서 보완한다.
        CollectLog collectLog = new CollectLog();
        collectLog.setLogType(logType);
        collectLog.setLogId(logId);
        collectLog.setLogTimestamp(logTimestamp);
        collectLog.setLogContent(logContent);
        collectLog.setLogValue(logValue);        // NUMERIC이 아니면 null
        collectLog.setRawLog(rawLog);
        collectLog.setLineNumber(lineNumber);
        return collectLog;
    }

    /**
     * logType이 시스템에서 지원하는 타입인지 확인한다.
     *
     * 지원 타입: TEXT, INFO, DATE, NUMERIC, EXIST (CollectConstants 상수 참조)
     */
    private boolean isSupportedLogType(String logType) {
        return CollectConstants.LOG_TYPE_TEXT.equals(logType)
                || CollectConstants.LOG_TYPE_INFO.equals(logType)
                || CollectConstants.LOG_TYPE_DATE.equals(logType)
                || CollectConstants.LOG_TYPE_NUMERIC.equals(logType)
                || CollectConstants.LOG_TYPE_EXIST.equals(logType);
    }

    /**
     * $값$ 형식의 수치 토큰에서 숫자 문자열을 추출하여 BigDecimal로 변환한다.
     *
     * BigDecimal을 사용하는 이유:
     *   수집 대상 수치 로그는 소수점 자리수가 정해지지 않으므로
     *   정밀도 손실 없이 표현하기 위해 BigDecimal을 사용한다.
     *
     * @param logValueToken "$숫자$" 형식의 원본 토큰 문자열
     * @param lineNumber    경고 로그 출력용 라인번호
     * @return 파싱된 BigDecimal, 실패 시 null
     */
    private BigDecimal parseLogValue(String logValueToken, long lineNumber) {
        Matcher valueMatcher = LOG_VALUE_PATTERN.matcher(logValueToken);
        if (!valueMatcher.matches()) {
            log.warn("수치형 로그 값 포맷 불일치로 무시 - lineNumber: {}, valueToken: {}", lineNumber, logValueToken);
            return null;
        }

        String valueText = valueMatcher.group("value").trim();
        if (valueText.isEmpty()) {
            log.warn("수치형 로그 값 공백으로 무시 - lineNumber: {}", lineNumber);
            return null;
        }

        try {
            return new BigDecimal(valueText);
        } catch (NumberFormatException e) {
            log.warn("수치형 로그 값 파싱 실패로 무시 - lineNumber: {}, value: {}", lineNumber, valueText);
            return null;
        }
    }
}
