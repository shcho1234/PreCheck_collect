package com.sks.precheck.collect.mapper;

import com.sks.precheck.collect.domain.CollectLog;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * TB_COLLECT_LOG 테이블 접근 Mapper.
 */
@Mapper
public interface CollectLogMapper {

    /**
     * 수집 로그 1건을 INSERT 한다.
     *
     * SEQUENCE nextval은 서비스에서 미리 조회하여 파라미터로 전달한다.
     *
     * @param collectLog 수집 로그 DTO
     * @return 처리 건수
     */
    int insert(CollectLog collectLog);

    /**
     * 분석 서버용 조회를 수행한다.
     *
     * COLLECT_DATE + SERVER_ID + LOG_TYPE 기준으로 조회하며,
     * LOG_ID는 선택 조건이다(null/빈 문자열이면 조건에서 제외).
     *
     * @param collectDate 수집일자(yyyyMMdd)
     * @param serverId 서버 구분
     * @param logType 로그 입력 타입(문구/정보/날짜/수치/존재/비교/시간)
     * @param logId 로그 식별 코드(선택)
     * @return 조회 결과 목록
     */
    List<CollectLog> findForAnalyze(
            @Param("collectDate") String collectDate,
            @Param("serverId") String serverId,
            @Param("logType") String logType,
            @Param("logId") String logId
    );
}
