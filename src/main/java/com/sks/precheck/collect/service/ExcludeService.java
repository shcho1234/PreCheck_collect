package com.sks.precheck.collect.service;

import com.sks.precheck.collect.common.constants.CollectConstants;
import com.sks.precheck.collect.common.util.DateUtil;
import com.sks.precheck.collect.domain.CollectExclude;
import com.sks.precheck.collect.mapper.CollectExcludeMapper;
import java.time.LocalDateTime;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

/**
 * 영구 제외 대상(TB_COLLECT_EXCLUDE) 관리 서비스.
 *
 * 역할:
 *   수집 실행 전에 해당 서버·파일이 영구 제외 대상인지 확인하고,
 *   파일 크기 초과 등 조건이 충족되면 해당 서버·파일을 영구 제외로 등록한다.
 *
 * 영구 제외 개념:
 *   한 번 제외로 등록된 파일(RESTORE_YN='N')은 관리자가 수동으로 복원하기 전까지
 *   수집 스케줄이 실행되더라도 자동으로 건너뛴다.
 *   이는 크기가 너무 커서 수집 자체가 불가능한 파일이 매 스케줄마다 실패를 반복하는
 *   상황을 방지하기 위한 안전장치이다.
 */
@Service
public class ExcludeService {

    private static final Logger log = LogManager.getLogger(ExcludeService.class);

    private final CollectExcludeMapper collectExcludeMapper;

    public ExcludeService(CollectExcludeMapper collectExcludeMapper) {
        this.collectExcludeMapper = collectExcludeMapper;
    }

    /**
     * 해당 서버·파일이 영구 제외 대상인지 확인한다.
     *
     * TB_COLLECT_EXCLUDE에서 serverId와 sourceFilePath가 일치하고
     * RESTORE_YN='N'인 레코드가 1건 이상 존재하면 제외 대상으로 판단한다.
     * RESTORE_YN='Y'(복원 완료)인 경우는 카운트에 포함되지 않으므로 다시 수집 대상이 된다.
     *
     * @param serverId       서버 구분 코드
     * @param sourceFilePath 수집 대상 원격 파일 경로
     * @return 제외 대상이면 true, 수집 가능하면 false
     */
    public boolean isExcluded(String serverId, String sourceFilePath) {
        int count = collectExcludeMapper.countActiveExclude(serverId, sourceFilePath);
        return count > 0;
    }

    /**
     * 해당 서버·파일을 영구 제외 대상으로 TB_COLLECT_EXCLUDE에 등록한다.
     *
     * 호출 시점:
     *   - 배치 수집 파일이 초기 수집 크기 한도(INIT_COLLECT_SIZE_LIMIT_BYTES)를 초과한 경우
     *   - 주기 수집의 증분량이 증분 크기 한도(PART_COLLECT_SIZE_LIMIT_BYTES)를 초과한 경우
     *
     * 등록 후에는 RESTORE_YN='N'으로 설정되어 이후 isExcluded() 호출에서 true를 반환한다.
     * PK(collectExcludeId)는 호출자가 SEQUENCE로 채번하여 전달한다.
     *
     * @param collectExcludeId   TB_COLLECT_EXCLUDE PK
     * @param serverId           서버 구분 코드
     * @param serverIp           서버 IP 주소
     * @param sourceFilePath     수집 대상 원격 파일 경로
     * @param excludeReason      제외 사유 코드 (INIT_SIZE / PART_SIZE)
     * @param excludeFileSizeBytes 제외 당시 파일 크기(bytes) — 이력 추적용
     * @param excludeDetail      제외 상세 사유 (사람이 읽을 수 있는 설명)
     */
    public void registerExclude(
            Long collectExcludeId,
            String serverId,
            String serverIp,
            String sourceFilePath,
            String excludeReason,
            long excludeFileSizeBytes,
            String excludeDetail
    ) {
        // 제외 도메인 객체를 구성한다.
        // excludeDate는 수집일(yyyyMMdd) 기준이며, createdAt/updatedAt은 현재 시각이다.
        CollectExclude exclude = new CollectExclude();
        exclude.setCollectExcludeId(collectExcludeId);
        exclude.setServerId(serverId);
        exclude.setServerIp(serverIp);
        exclude.setSourceFilePath(sourceFilePath);
        exclude.setExcludeReason(excludeReason);
        exclude.setExcludeFileSize(excludeFileSizeBytes);
        exclude.setExcludeDetail(excludeDetail);
        // 복원 전까지 수집을 차단하는 플래그. 관리자가 'Y'로 변경하면 다시 수집 가능해진다.
        exclude.setRestoreYn(CollectConstants.YN_NO);
        exclude.setExcludeDate(DateUtil.todayCollectDate());
        exclude.setCreatedAt(LocalDateTime.now());
        exclude.setUpdatedAt(LocalDateTime.now());

        collectExcludeMapper.insert(exclude);

        log.warn("영구 제외 등록 - 서버: {}, 파일: {}, 사유: {}, 크기: {}bytes",
                serverId, sourceFilePath, excludeReason, excludeFileSizeBytes);
    }
}
