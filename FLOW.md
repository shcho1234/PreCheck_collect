# PreCheck 수집 서버 소스 코드 읽기 가이드

---

## 패키지 구조

```
com.sks.precheck.collect
│
├── CollectApplication.java          ← 진입점
│
├── common/
│   ├── constants/
│   │   └── CollectConstants.java    ← 숫자/문자열 상수 모음 (파일크기 한도, 재시도 횟수 등)
│   ├── exception/
│   │   └── CollectException.java    ← 수집 서버 전용 RuntimeException
│   └── util/
│       ├── DateUtil.java            ← 날짜 포맷/파싱 유틸 (timestamp 파싱, yyyyMMdd 변환)
│       └── SequenceHelper.java      ← DB SEQUENCE NEXTVAL 조회 (PostgreSQL/Altibase 분기)
│
├── config/
│   ├── DataSourceConfig.java        ← 프로파일별 DataSource 설정 (test=PostgreSQL, prod=Altibase)
│   └── RetryConfig.java             ← @EnableRetry 활성화
│
├── domain/
│   ├── CollectLog.java              ← TB_COLLECT_LOG 매핑 DTO
│   ├── CollectHistory.java          ← TB_COLLECT_HISTORY 매핑 DTO
│   └── CollectExclude.java          ← TB_COLLECT_EXCLUDE 매핑 DTO
│
├── mapper/
│   ├── CollectLogMapper.java        ← TB_COLLECT_LOG 접근 (insert, findForAnalyze)
│   ├── CollectHistoryMapper.java    ← TB_COLLECT_HISTORY 접근 (insert, update, findLastLineNumber)
│   └── CollectExcludeMapper.java    ← TB_COLLECT_EXCLUDE 접근 (insert, countActiveExclude)
│
├── parser/
│   ├── CollectScheduleParser.java   ← .conf 파일 → CollectScheduleVo 목록 변환
│   └── LogNormalizeParser.java      ← 로그 한 줄 → CollectLog 변환 (@@@...@@@ 파싱)
│
├── scheduler/
│   └── CollectScheduler.java        ← 1초마다 실행, 조건 판단 후 CollectService 호출
│
├── service/
│   ├── CollectService.java          ← 수집 진입점, 이력 선등록 후 RetryService에 위임
│   ├── CollectRetryService.java     ← 실제 수집 처리 + @Retryable 재시도 로직
│   ├── SftpService.java             ← SSH 접속, 파일 크기 조회, 라인 단위 읽기
│   └── ExcludeService.java          ← 영구 제외 대상 등록/조회
│
└── vo/
    └── CollectScheduleVo.java       ← .conf 파일 한 줄을 담는 VO (서버ID, IP, 파일경로, 스케줄표현식)

resources/
├── application.yml                  ← 공통 설정 (기본 프로파일: local)
├── application-local.yml            ← 로컬 개발용 (PostgreSQL localhost)
├── application-test.yml             ← 테스트 서버용 (PostgreSQL)
├── application-prod.yml             ← 운영 서버용 (Altibase)
└── mapper/
    ├── CollectLogMapper.xml
    ├── CollectHistoryMapper.xml
    └── CollectExcludeMapper.xml
```

---

## 클래스 간 호출 관계

```
CollectScheduler
    │  생성자에서 직접 new
    ├──► CollectScheduleParser          (Spring Bean 아님, 상태 없음)
    │
    │  Spring Bean 주입
    └──► CollectService
              │
              ├──► SequenceHelper       (SEQ_COLLECT_HISTORY NEXTVAL)
              ├──► CollectHistoryMapper (이력 INSERT)
              │
              └──► CollectRetryService  ← @Retryable AOP 프록시 경유 호출 (★중요)
                        │
                        ├──► SequenceHelper       (SEQ_COLLECT_LOG, SEQ_COLLECT_EXCLUDE NEXTVAL)
                        ├──► CollectLogMapper      (로그 INSERT)
                        ├──► CollectHistoryMapper  (이력 UPDATE)
                        ├──► ExcludeService        (제외 등록/조회)
                        │         └──► CollectExcludeMapper
                        ├──► SftpService            (파일 크기 조회, 라인 읽기)
                        │
                        └──► LogNormalizeParser    (생성자에서 직접 new, 상태 없음)
```

> **CollectService → CollectRetryService 분리 이유**
> Spring의 `@Retryable`은 AOP 프록시를 통해서만 동작한다.
> 같은 클래스 내부에서 `this.메서드()` 로 호출하면 프록시를 우회해 재시도가 무력화된다.
> 재시도가 필요한 로직만 별도 Bean(`CollectRetryService`)으로 분리하고,
> `CollectService`에서 Spring이 주입한 프록시 Bean을 통해 호출해야 `@Retryable`이 동작한다.

---

## 코드 흐름 추적

### STEP 1 — 기동

```
CollectApplication.java
  @SpringBootApplication     → 패키지 하위 전체 Bean 스캔
  @EnableScheduling          → @Scheduled 어노테이션 활성화

  main() {
      SpringApplication.run(CollectApplication.class, args);
  }
```

기동 시 주요 자동 설정:
- `application-{profile}.yml` 의 `spring.datasource.*` → DataSource 생성
- `application.yml` 의 `mybatis.*` → MyBatis SqlSessionFactory 자동 구성
  - `map-underscore-to-camel-case: true` : `COLLECT_LOG_ID` → `collectLogId` 자동 매핑
  - `mapper-locations` : `resources/mapper/*.xml` 로드
- `@Mapper` 어노테이션이 붙은 인터페이스 자동 등록

---

### STEP 2 — 스케줄러 루프

```
CollectScheduler.java
  @Scheduled(fixedDelayString = "${precheck.collect.scheduler.fixed-delay-ms:1000}")
  run() {
      // SFTP 계정 미설정이면 건너뜀
      if (sftpUsername.isBlank() || sftpPassword.isBlank()) { return; }

      List<CollectScheduleVo> schedules = getSchedules();   // ← STEP 3
      for (CollectScheduleVo schedule : schedules) {
          if (shouldRun(schedule, now)) {                   // ← STEP 4
              collectService.collect(...);                  // ← STEP 5
          }
      }
  }
```

`fixedDelay` 방식: 이전 `run()` 실행이 완전히 끝난 뒤 1초 후에 다음 실행 시작.
→ `run()` 실행 중 다음 `run()`이 겹쳐 실행되지 않음.

---

### STEP 3 — 스케줄 파일 파싱

```
CollectScheduler.java
  getSchedules() {
      // 마지막 파싱 후 reloadIntervalMillis(기본 60초) 미경과 → 캐시 반환
      if (캐시 유효) return cachedSchedules;

      // 캐시 만료 → 파일 재파싱
      collectScheduleParser.parseScheduleFile(scheduleFilePath);
  }

CollectScheduleParser.java
  parseScheduleFile(filePath) {
      // 파일을 한 줄씩 읽어 parseLine() 호출
      // 결과: List<CollectScheduleVo>
  }

  parseLine(line, lineNumber) {
      // '#' 시작 또는 빈 줄 → null (무시)
      // extractBracketTokens() 로 [토큰] 4개 추출
      // 토큰이 4개가 아니거나 isValidScheduleExpression() 실패 → null (WARN 로그)
      // 성공 → CollectScheduleVo 반환
  }
```

**.conf 파일 한 줄 파싱 예:**
```
입력: [dlprem01][192.168.210.121][/tmp/check.out][주기|1-5|090001|10|100001]

CollectScheduleVo {
    serverId         = "dlprem01"
    serverIp         = "192.168.210.121"
    sourceFilePath   = "/tmp/check.out"
    scheduleExpression = "주기|1-5|090001|10|100001"
}
```

---

### STEP 4 — 실행 조건 판단

```
CollectScheduler.java
  shouldRun(schedule, now) {
      ScheduleRule rule = parseRule(schedule.getScheduleExpression());
      // scheduleExpression → type(배치/주기), daySpec, startTime, intervalMinutes, endTime

      if (!isTodayMatched(rule.daySpec, today)) return false;  // 요일 불일치

      if ("배치".equals(rule.type)) return shouldRunBatch(...);
      return shouldRunPeriodic(...);
  }
```

**배치 타입 판단 (`shouldRunBatch`):**
```
nowSeconds 가 [startSeconds, startSeconds + 2) 범위 안?  →  2초 실행 윈도우
AND 오늘 날짜로 아직 실행 안 함? (lastBatchRunDateByKey)
→ true 이면 실행, lastBatchRunDateByKey에 오늘 날짜 기록
```

**주기 타입 판단 (`shouldRunPeriodic`):**
```
startSeconds ≤ nowSeconds ≤ endSeconds?

offsetSeconds = nowSeconds - startSeconds
intervalSeconds = intervalMinutes * 60
runIndex = offsetSeconds / intervalSeconds   ← 몇 번째 주기인지
remainder = offsetSeconds % intervalSeconds  ← 이 주기 안에서 얼마나 지났는지

remainder < 2초(폴링 윈도우)?               ← 주기 시작점 근처인지
AND runIndex를 아직 실행 안 함? (lastPeriodicRunIndexByKey)
→ true 이면 실행, lastPeriodicRunIndexByKey에 runIndex 기록
```

---

### STEP 5 — 수집 진입점

```
CollectService.java
  collect(schedule, port, username, password) {

      // 1. 수집 타입 파싱: scheduleExpression.split("|")[0] → "배치" or "주기"
      String scheduleType = parseScheduleType(schedule.getScheduleExpression());

      // 2. 수집 이력 PK 채번
      Long historyId = sequenceHelper.nextval("SEQ_COLLECT_HISTORY");

      // 3. 수집 이력 선등록 (수집 시작 마킹)
      //    STATUS = "FAIL", FAIL_REASON = "IN_PROGRESS"
      //    → 수집 도중 서버 다운 시에도 이력이 남아 추적 가능
      CollectHistory history = new CollectHistory();
      history.setCollectStatus(CollectConstants.STATUS_FAIL);
      history.setFailReason("IN_PROGRESS");
      collectHistoryMapper.insert(history);

      // 4. 실제 수집은 CollectRetryService에 위임
      //    ↓ 반드시 주입된 Bean(프록시)을 통해 호출해야 @Retryable 동작
      return collectRetryService.collectWithRetry(historyId, schedule, ...);
  }
```

---

### STEP 6 — 실제 수집 (@Retryable)

```
CollectRetryService.java

  @Retryable(
      retryFor = {CollectException.class},
      maxAttemptsExpression = "#{...CollectConstants.MAX_RETRY_COUNT + 1}",  // 4회
      backoff = @Backoff(delayExpression = "#{...RETRY_DELAY_MILLISECONDS}") // 10초
  )
  collectWithRetry(historyId, schedule, scheduleType, port, username, password) {

      // --- 재시도 횟수 갱신 ---
      int retryCount = RetrySynchronizationManager.getContext().getRetryCount();
      // → TB_COLLECT_HISTORY.RETRY_COUNT UPDATE

      // --- 영구 제외 확인 ---
      if (excludeService.isExcluded(serverId, sourceFilePath)) {
          // TB_COLLECT_HISTORY STATUS=SKIP, return 0
      }

      // --- 파일 크기 조회 ---
      long fileSizeBytes = sftpService.getFileSizeBytes(...);
      // 실패 시 CollectException → @Retryable 재시도 발동

      // --- [배치] 초기 크기 초과 ---
      if ("배치".equals(scheduleType) && fileSizeBytes >= 300MB) {
          excludeService.registerExclude(..., "INIT_SIZE", ...);
          // TB_COLLECT_HISTORY STATUS=SKIP, return 0
      }

      // --- [주기] 증분 시작점 계산 ---
      Long lastLineNumber = null;
      if ("주기".equals(scheduleType)) {
          lastLineNumber = collectHistoryMapper.findLastLineNumber(serverId, sourceFilePath);
          // SELECT LAST_LINE_NUMBER ... FETCH FIRST 1 ROWS ONLY
      }
      long startLineNumber = (lastLineNumber == null) ? 1 : lastLineNumber + 1;

      // --- SFTP 파일 읽기 + 파싱 ---
      List<CollectLog> parsedLogs = new ArrayList<>();
      LineReadState lineReadState = new LineReadState(startLineNumber - 1);

      sftpService.readLines(..., startLineNumber, (lineNumber, lineText) -> {
          lineReadState.lastReadLineNumber = lineNumber;
          lineReadState.totalReadBytes += lineText.getBytes(UTF_8).length;

          // [주기] 50MB 초과 시 파싱 중단 (읽기는 계속해 lastReadLineNumber 추적)
          if ("주기".equals(scheduleType) && lineReadState.totalReadBytes >= 50MB) {
              lineReadState.exceededPartSizeLimit = true;
              return;
          }

          CollectLog parsed = logNormalizeParser.parseNormalizedLogFromLine(lineText, lineNumber);
          if (parsed != null) parsedLogs.add(parsed);
      });

      // --- [주기] 증분 크기 초과 ---
      if ("주기".equals(scheduleType) && lineReadState.exceededPartSizeLimit) {
          excludeService.registerExclude(..., "PART_SIZE", ...);
          // TB_COLLECT_HISTORY STATUS=SKIP, return 0
      }

      // --- TB_COLLECT_LOG INSERT ---
      for (CollectLog logRow : parsedLogs) {
          logRow.setCollectLogId(sequenceHelper.nextval("SEQ_COLLECT_LOG"));
          logRow.setServerId(serverId);           // 파서가 모르는 정보를 여기서 보완
          logRow.setCollectDate(today);
          logRow.setScheduleType(scheduleType);
          collectLogMapper.insert(logRow);
      }

      // --- TB_COLLECT_HISTORY SUCCESS UPDATE ---
      // LAST_LINE_NUMBER = lineReadState.lastReadLineNumber  ← 다음 주기 수집 시작점
      collectHistoryMapper.updateCollectStatus(update);

      return parsedLogs.size();
  }

  @Recover  // 4회 모두 실패 시 호출
  recover(CollectException e, Long historyId, ...) {
      // TB_COLLECT_HISTORY STATUS=FAIL, FAIL_REASON=예외메시지
      // ERROR 로그 기록
      return 0;
  }
```

---

### STEP 7 — SSH/SFTP 접속

```
SftpService.java

  getFileSizeBytes(serverIp, port, username, password, remoteFilePath) {
      try (SSHClient client = createClient()) {     // PromiscuousVerifier (내부망 전용)
          connectAndAuth(client, ...);              // connect() → authPassword()
          try (SFTPClient sftp = client.newSFTPClient()) {
              return sftp.stat(remoteFilePath).getSize();
          }
      }
      // IOException → CollectException 변환 → @Retryable 재시도 대상
  }

  readLines(serverIp, port, username, password, remoteFilePath,
            startLineNumber, charset, lineConsumer) {
      try (SSHClient client = createClient()) {
          connectAndAuth(client, ...);
          try (SFTPClient sftp = client.newSFTPClient();
               RemoteFile remoteFile = sftp.open(remoteFilePath);
               BufferedReader reader = new BufferedReader(
                       new InputStreamReader(remoteFile.new RemoteFileInputStream(), charset))) {

              long currentLineNumber = 0;
              String line;
              while ((line = reader.readLine()) != null) {
                  currentLineNumber++;
                  if (currentLineNumber < startLineNumber) continue;  // 앞부분 skip
                  lineConsumer.accept(currentLineNumber, line);        // 콜백 호출
              }
          }
      }
  }
```

---

### STEP 8 — 정규화 로그 파싱

```
LogNormalizeParser.java

  parseNormalizedLogFromLine(line, lineNumber) {

      // "@@@" 없으면 즉시 null (일반 로그 라인)
      int start = line.indexOf("@@@");
      if (start < 0) return null;

      // 종료 "@@@" 찾기
      int end = line.indexOf("@@@", start + 3);
      if (end < 0) { WARN "종료 누락"; return null; }

      // 한 라인에 @@@가 3개 이상 (로그 2건 혼재)
      if (line.indexOf("@@@", end + 3) >= 0) { WARN "2건 이상"; return null; }

      // rawLog = "@@@[...][...][...]|...|...@@@"
      String rawLog = line.substring(start, end + 3);

      // 헤더(@@@[timestamp][type][logId])를 먼저 파싱하고,
      // 이후 |...| 구간(contentPart)과 뒤쪽(tailPart)에서 $...$ 토큰을 추출한다.

      // logType 검증: 문구/정보/날짜/수치/존재/비교/시간 중 하나여야 함
      // LOG_ID 검증: 대문자+숫자+언더스코어, 최대 30자  (패턴: ^[A-Z0-9_]{1,30}$)
      // timestamp 파싱: DateUtil.parseLogTimestamp() → LocalDateTime

      // 값 토큰 규칙(v1.1)
      //  - 수치: $...$ 토큰이 정확히 1개 (콜론(:) 없는 값만 허용)
      //  - 비교: $...$ 토큰이 정확히 2개 (콜론(:) 없는 값만 허용)
      //  - 시간: $...$ 토큰이 정확히 1개 (콜론(:) 포함, HH:mm → 분(minute) 값으로 저장)

      // CollectLog 반환 (serverId 등 수집 맥락 정보는 CollectRetryService에서 보완)
  }
```

**파싱 예시:**

```
입력 라인:
  2026/05/01 sis15007 @@@[2026/05/01 09:00:01.123][수치][DISK_HOME]|홈디스크|$80$@@@

rawLog 추출:
  @@@[2026/05/01 09:00:01.123][수치][DISK_HOME]|홈디스크|$80$@@@

추출 필드:
  timestamp   = "2026/05/01 09:00:01.123" → LocalDateTime
  logType     = "수치"
  logId       = "DISK_HOME"
  logContent  = "홈디스크"
  logValue    = BigDecimal("80")
  rawLog      = "@@@[2026/05/01 09:00:01.123][수치][DISK_HOME]|홈디스크|$80$@@@"
  lineNumber  = 42  (파일에서의 라인번호)
```

```
입력 라인(수치, 중간 배치):
  2026/05/28 sis15007 @@@[2026/05/28 13:01:09.555][수치][PROC_AO_TR]|ao_tr_pro_nm PROCESS 갯수 $4$ 처리중|@@@

추출 필드:
  logType     = "수치"
  logContent  = "ao_tr_pro_nm PROCESS 갯수 처리중"
  logValue    = BigDecimal("4")
```

```
입력 라인(시간):
  2026/05/01 sis15007 @@@[2026/05/01 09:00:01.123][시간][DATE_BTIME]|처리시간 $07:35$|@@@

추출 필드:
  logType     = "시간"
  logContent  = "처리시간"
  logValue    = BigDecimal("455")  // 07:35 → 455분
```

---

### STEP 9 — DB SEQUENCE 채번

```
SequenceHelper.java

  nextval(sequenceName) {
      Connection connection = dataSource.getConnection();
      String dbProductName = connection.getMetaData().getDatabaseProductName();

      // DB 종류에 따라 SQL 분기
      if (dbProductName.contains("postgres"))
          sql = "select nextval('" + sequenceName + "')";      // PostgreSQL
      else
          sql = "SELECT " + sequenceName + ".NEXTVAL FROM DUAL"; // Altibase

      // PreparedStatement 실행 → Long 반환
  }
```

사용처:
- `CollectService` : `SEQ_COLLECT_HISTORY` (수집 이력 PK)
- `CollectRetryService` : `SEQ_COLLECT_LOG` (수집 로그 PK), `SEQ_COLLECT_EXCLUDE` (제외 PK)

---

## 데이터 흐름 한눈에 보기

```
.conf 파일
    │
    ▼  CollectScheduleParser.parseScheduleFile()
CollectScheduleVo (서버ID, IP, 파일경로, 스케줄표현식)
    │
    ▼  CollectScheduler.shouldRun()  →  조건 불충족이면 버림
    │
    ▼  CollectService.collect()
    │      TB_COLLECT_HISTORY INSERT  (STATUS=FAIL, FAIL_REASON=IN_PROGRESS)
    │
    ▼  CollectRetryService.collectWithRetry()   @Retryable
    │      SftpService.getFileSizeBytes()
    │      SftpService.readLines()
    │          │
    │          ▼  (각 라인마다 콜백)
    │      LogNormalizeParser.parseNormalizedLogFromLine()
    │          │
    │          ▼  @@@...@@@ 라인만
    │      CollectLog  (logType, logId, logTimestamp, logContent, logValue, rawLog, lineNumber)
    │          │
    │          ▼  수집 맥락 정보 보완 (serverId, serverIp, collectDate, scheduleType ...)
    │      TB_COLLECT_LOG INSERT
    │
    ▼  TB_COLLECT_HISTORY UPDATE  (STATUS=SUCCESS, LAST_LINE_NUMBER=마지막라인번호)
```

---

## 설정값 목록

| 설정 키 | 기본값 | 설명 |
|---------|--------|------|
| `precheck.collect.schedule-file-path` | `~/cfg/PreCheck_CollectLogs_Schedule.conf` | 스케줄 설정 파일 경로 |
| `precheck.sftp.port` | `22` | SSH 포트 |
| `precheck.sftp.username` | (없음) | SFTP 계정 — 미설정 시 수집 건너뜀 |
| `precheck.sftp.password` | (없음) | SFTP 비밀번호 — 미설정 시 수집 건너뜀 |
| `precheck.collect.scheduler.fixed-delay-ms` | `1000` | 스케줄러 실행 간격 (ms) |
| `precheck.collect.scheduler.reload-interval-ms` | `60000` | 스케줄 파일 캐시 유효 시간 (ms) |

| 상수 | 값 | 설명 |
|------|----|------|
| `INIT_COLLECT_SIZE_LIMIT_BYTES` | 300MB | 배치 수집 파일 크기 한도 |
| `PART_COLLECT_SIZE_LIMIT_BYTES` | 50MB  | 주기 증분 크기 한도 |
| `MAX_RETRY_COUNT` | 3 | 재시도 횟수 (최초 포함 총 4회) |
| `RETRY_DELAY_MILLISECONDS` | 10,000 (10초) | 재시도 간격 |
