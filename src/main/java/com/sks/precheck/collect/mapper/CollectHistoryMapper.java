package com.sks.precheck.collect.mapper;

import com.sks.precheck.collect.domain.CollectHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * TB_COLLECT_HISTORY 테이블 접근 Mapper.
 */
@Mapper
public interface CollectHistoryMapper {

    /**
     * 수집 이력 1건을 INSERT 한다.
     *
     * SEQUENCE nextval은 서비스에서 미리 조회하여 파라미터로 전달한다.
     *
     * @param collectHistory 수집 이력 DTO
     * @return 처리 건수
     */
    int insert(CollectHistory collectHistory);

    /**
     * 수집 상태를 UPDATE 한다.
     *
     * 수집 성공/실패/제외 확정 시점에 상태, 라인번호, 재시도 횟수, 실패 사유 등을 갱신한다.
     *
     * @param collectHistory 수집 이력 DTO
     * @return 처리 건수
     */
    int updateCollectStatus(CollectHistory collectHistory);

    /**
     * 주기 수집에서 마지막 성공 수집의 라인번호를 조회한다.
     *
     * 결과가 없으면 null 이며, 서비스에서 0으로 처리한다(파일 처음부터 읽기).
     *
     * @param serverId 서버 구분
     * @param sourceFilePath 대상 파일 경로
     * @return 마지막 라인번호 (없으면 null)
     */
    Long findLastLineNumber(
            @Param("serverId") String serverId,
            @Param("sourceFilePath") String sourceFilePath
    );
}

