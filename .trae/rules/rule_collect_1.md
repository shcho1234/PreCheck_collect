# PreCheck 수집 서버 - AI 코드 생성 Rule
# 이 파일은 Trae가 수집 서버 코드를 생성·수정할 때 반드시 따르는 규칙이다.
# Context 파일(01~07번)에 명세 상세 내용이 있으니 모르는 내용은 Context를 참고한다.

---

## 1. 기술 스택 (변경 금지)

### 사용 필수
| 구분 | 기술 |
|---|---|
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 3.x |
| 빌드 | Gradle |
| DB 접근 | MyBatis |
| 스케줄 | Spring Scheduler (@Scheduled) |
| 재시도 | Spring Retry (@Retryable) |
| 로그 | Log4j2 |
| SFTP | SSHJ |
| 수치 비교 | BigDecimal |
| 테스트 | JUnit 5 |

### 사용 금지
- JPA / Hibernate / Spring Data JPA
- Quartz Scheduler
- Logback
- float / double (수치 계산 전용)

---

## 2. DB 타입 규칙 (Altibase + PostgreSQL 공통)

### Java ↔ DB 타입 매핑
| DB 타입 | Java 타입 | 비고 |
|---|---|---|
| NUMERIC(19,0) | Long | PK 등 정수 |
| NUMERIC(18,6) | BigDecimal | 수치형 로그값 |
| CHAR(1) | String | "Y" 또는 "N" 만 허용 |
| VARCHAR(n) | String | 최대 VARCHAR(4000) |
| TIMESTAMP | LocalDateTime | |

### 금지 타입
| 금지 | 대체 |
|---|---|
| BOOLEAN | CHAR(1) ('Y'/'N') |
| TEXT | VARCHAR(4000) 이하 |
| BIGINT | NUMERIC(19,0) |
| SERIAL / AUTO_INCREMENT | SEQUENCE 객체 |
| CLOB | VARCHAR(4000) 이하 |

### 기타 DB 규칙
- PK는 SEQUENCE 객체로 생성, MyBatis에서 nextval 호출 후 파라미터로 전달
- DB 레벨 FK 제약 생성 금지, 정합성은 애플리케이션에서 관리

---

## 3. 수집 서버 역할 경계 (혼용 금지)

### 수집 서버가 하는 일
- 레거시 서버에서 SFTP로 로그 파일 수집
- 정규화 로그(@@@...@@@) 파싱 후 TB_COLLECT_LOG 저장
- 수집 이력 TB_COLLECT_HISTORY 저장
- 영구 제외 대상 TB_COLLECT_EXCLUDE 관리

### 수집 서버가 절대 하면 안 되는 일
- 로그 분석 로직 포함 금지
- SMS / 통보 로직 포함 금지
- TB_ANALYZE_RESULT / TB_ANALYZE_HISTORY 접근 금지

---

## 4. 패키지 구조 (이 구조를 준용하되 필요하면 생성 가능)

```
com.sks.precheck.collect
├── CollectApplication.java
├── common/
│   ├── constants/CollectConstants.java
│   ├── exception/CollectException.java
│   └── util/DateUtil.java
├── config/
│   ├── DataSourceConfig.java
│   ├── MyBatisConfig.java
│   └── RetryConfig.java
├── domain/
│   ├── CollectLog.java
│   ├── CollectHistory.java
│   └── CollectExclude.java
├── mapper/
│   ├── CollectLogMapper.java
│   ├── CollectHistoryMapper.java
│   └── CollectExcludeMapper.java
├── parser/
│   ├── CollectScheduleParser.java
│   └── LogNormalizeParser.java
├── scheduler/
│   └── CollectScheduler.java
├── service/
│   ├── CollectService.java
│   ├── SftpService.java
│   └── ExcludeService.java
└── vo/
    └── CollectScheduleVo.java

resources/
├── application.yml
├── application-test.yml
├── application-prod.yml
└── mapper/
    ├── CollectLogMapper.xml
    ├── CollectHistoryMapper.xml
    └── CollectExcludeMapper.xml
```

---

## 5. 네이밍 규칙

### 클래스
| 종류 | 패턴 | 예시 |
|---|---|---|
| 서비스 | {기능}Service | CollectService, SftpService |
| 스케줄러 | {기능}Scheduler | CollectScheduler |
| 파서 | {대상}Parser | CollectScheduleParser, LogNormalizeParser |
| Mapper | {테이블}Mapper | CollectLogMapper |
| 도메인 DTO | 테이블명 축약 | CollectLog, CollectHistory, CollectExclude |
| VO | {기능}Vo | CollectScheduleVo |
| 예외 | {서버}Exception | CollectException |
| 상수 | {서버}Constants | CollectConstants |

### 메서드
| 동작 | 접두사 | 예시 |
|---|---|---|
| DB 단건 조회 | find | findLastLineNumber |
| DB 목록 조회 | findAll / findBy~ | findAllByDate |
| DB 저장 | insert | insertCollectLog |
| DB 수정 | update | updateCollectStatus |
| 파싱 | parse | parseScheduleFile |
| 검증 | is / has / validate | isExcluded, hasPolicy |
| 수집 실행 | collect | collectBatch, collectPeriodic |

### 변수 / 상수
```java
// 변수: 의미와 단위가 명확하게
long fileSizeBytes  = remoteFile.getSize();   // 단위를 이름에 포함
int  lastLineNumber = history.getLastLineNumber();

// 상수: CollectConstants에 정의, 매직 넘버 사용 금지
public static final long INIT_COLLECT_SIZE_LIMIT_BYTES = 300L * 1024 * 1024; // 300MB
public static final long PART_COLLECT_SIZE_LIMIT_BYTES = 50L  * 1024 * 1024; // 50MB
public static final int  MAX_RETRY_COUNT                = 3;
public static final long RETRY_DELAY_MILLISECONDS       = 10_000L;            // 10초
public static final String COLLECT_DATE_FORMAT          = "yyyyMMdd";
public static final String LOG_TIMESTAMP_FORMAT         = "yyyy/MM/dd HH:mm:ss.SSS";
public static final String STATUS_SUCCESS = "SUCCESS";
public static final String STATUS_FAIL    = "FAIL";
public static final String STATUS_SKIP    = "SKIP";
public static final String YN_YES = "Y";
public static final String YN_NO  = "N";
public static final String EXCLUDE_REASON_INIT_SIZE = "INIT_SIZE";
public static final String EXCLUDE_REASON_PART_SIZE = "PART_SIZE";
```

---

## 6. 클래스 내부 선언 순서

```java
public class XxxService {

    // 1. 로거
    private static final Logger log = LogManager.getLogger(XxxService.class);

    // 2. 상수 (클래스 내부 전용 상수만, 공통은 CollectConstants)

    // 3. 의존성 주입 필드 (final)
    private final CollectLogMapper collectLogMapper;

    // 4. 생성자 (의존성 주입)
    public XxxService(CollectLogMapper collectLogMapper) {
        this.collectLogMapper = collectLogMapper;
    }

    // 5. public 메서드

    // 6. private 메서드 (내부 헬퍼)

}
```

---

## 7. 주석 규칙

### 언어
- **모든 주석은 한국어**로 작성
- 코드(클래스명·메서드명·변수명)는 영어
- SFTP, MyBatis, SEQUENCE 등 기술 용어는 영어 허용

### Javadoc 작성 대상 (보통 엄격도)
| 작성 필수 | 생략 가능 |
|---|---|
| 모든 public 클래스 | getter / setter |
| 비즈니스 로직 포함 public 메서드 | 기본 생성자 |
| 복잡한 private 메서드 (30줄↑ 또는 분기 3개↑) | 단순 위임 메서드 |
| 커스텀 예외 클래스 | 자명한 상수 |

### Javadoc 형식
```java
/**
 * [한 줄 요약 - 동사로 시작]
 *
 * [상세 설명 - 비즈니스 규칙, 주의사항이 있을 때만]
 *
 * @param 파라미터명 설명
 * @return 반환값 설명 (void면 생략)
 * @throws 예외클래스 발생 조건
 */
```

### 인라인 주석 - 달아야 하는 경우
```java
// ✅ 비즈니스 규칙 숫자
if (fileSizeBytes >= CollectConstants.INIT_COLLECT_SIZE_LIMIT_BYTES) { // 배치 300MB 초과 → 영구 제외
    ...
}

// ✅ 중복 수집 방지 핵심 로직
// 이전 수집의 마지막 라인번호 이후부터만 읽어 중복 수집을 방지한다
int startLine = lastLineNumber + 1;

// ✅ DB 쿼리 호출 목적
// SEQUENCE nextval은 서비스에서 미리 조회하여 파라미터로 전달한다
collectLogMapper.insert(collectLog);

// ❌ 자명한 코드에 주석 금지
int count = 0; // count를 0으로 초기화  ← 이런 주석은 작성하지 않는다
```

---

## 8. 예외 처리 & 로그 레벨

| 레벨 | 사용 기준 |
|---|---|
| ERROR | DB 오류, SFTP 실패, 재시도 3회 모두 실패 |
| WARN | 파일 크기 초과, 포맷 불일치, 제외 대상 처리 |
| INFO | 수집 시작·완료, 건수 등 주요 결과 |
| DEBUG | 라인 단위 파싱 등 상세 처리 (운영 OFF) |

```java
// ERROR: 시스템 이상
log.error("SFTP 연결 실패 - 서버: {}, IP: {}", serverId, serverIp, e);
throw new CollectException("SFTP 연결 실패: " + serverIp, e);

// WARN: 비즈니스 예외
log.warn("배치 파일 크기 초과, 영구 제외 처리 - 서버: {}, 파일: {}, 크기: {}bytes",
         serverId, filePath, fileSizeBytes);
```

---

## 9. MyBatis XML 주석 형식

```xml
<!--
    XxxMapper.xml
    TB_XXX 테이블 CRUD
    INSERT 주체: 로그 수집 서버 / SELECT 주체: ~
-->
<mapper namespace="com.sks.precheck.collect.mapper.XxxMapper">

    <!-- [목적 한 줄 설명] -->
    <!-- [주의사항이 있으면 추가] -->
    <insert id="insert" ...> ... </insert>

</mapper>
```

---

## 10. TODO 형식

```java
// TODO: [작업 내용] - [이유 또는 참고 파일]
// TODO: SEQUENCE nextval 조회 추가 - 04_collect_db.md 참고
// TODO: 파일 크기 검사 추가 - 배치 300MB / 주기 50MB 기준
```

---

## 11. AI 코드 생성 행동 규칙

- 생성 완료 후 아래 형식으로 반드시 출력한다
  ```
  ✅ 생성 완료
  - 파일: [파일명]
  - 역할: [한 줄 요약]
  - 다음 단계: [다음에 만들 파일 또는 확인 사항]
  ```
- 명세(Context 파일)와 충돌이 생기면 **코드 작성 전에 먼저 질문한다**
- 패키지 구조·클래스 분리 등 구조 변경이 필요하면 **먼저 설명하고 승인을 받는다**
- 테스트 코드는 요청 시에만 생성한다
- 모르는 내용은 추측하지 말고 Context 파일을 참고하거나 질문한다
