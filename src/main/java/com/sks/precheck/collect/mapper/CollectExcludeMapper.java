package com.sks.precheck.collect.mapper;

import com.sks.precheck.collect.domain.CollectExclude;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * TB_COLLECT_EXCLUDE 테이블 접근 Mapper.
 */
@Mapper
public interface CollectExcludeMapper {

    /**
     * 영구 제외 대상을 INSERT 한다.
     *
     * SEQUENCE nextval은 서비스에서 미리 조회하여 파라미터로 전달한다.
     *
     * @param collectExclude 영구 제외 대상 DTO
     * @return 처리 건수
     */
    int insert(CollectExclude collectExclude);

    /**
     * 영구 제외 대상 여부를 확인한다.
     *
     * RESTORE_YN='N' 인 데이터가 존재하면 제외 대상으로 판단한다.
     *
     * @param serverId 서버 구분
     * @param sourceFilePath 대상 파일 경로
     * @return 제외 대상 건수 (0이면 제외 대상 아님)
     */
    int countActiveExclude( 
            @Param("serverId") String serverId,
            @Param("sourceFilePath") String sourceFilePath
    );
}

