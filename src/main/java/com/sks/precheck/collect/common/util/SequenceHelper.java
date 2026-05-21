package com.sks.precheck.collect.common.util;

import com.sks.precheck.collect.common.exception.CollectException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * DB SEQUENCE NEXTVAL 조회 공통 헬퍼.
 *
 * PostgreSQL은 nextval('시퀀스명'), Altibase는 시퀀스명.NEXTVAL FROM DUAL 문법을 사용하므로
 * DB 제품명에 따라 SQL을 분기한다.
 */
@Component
public class SequenceHelper {

    private final DataSource dataSource;

    public SequenceHelper(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * DB SEQUENCE의 다음 값(NEXTVAL)을 조회하여 PK로 사용할 값을 반환한다.
     *
     * @param sequenceName 시퀀스 이름 (예: SEQ_COLLECT_LOG)
     * @return NEXTVAL
     * @throws CollectException SEQUENCE 조회 실패 시
     */
    public Long nextval(String sequenceName) {
        try (Connection connection = dataSource.getConnection()) {
            String dbProductName = connection.getMetaData().getDatabaseProductName();
            String sql = buildNextvalSql(dbProductName, sequenceName);

            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new CollectException("SEQUENCE nextval 조회 결과가 없다: " + sequenceName);
                }
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new CollectException("SEQUENCE nextval 조회 실패: " + sequenceName, e);
        }
    }

    // dbProductName에 "postgres"가 포함되면 PostgreSQL 문법, 그 외에는 Altibase 문법을 사용한다.
    private String buildNextvalSql(String dbProductName, String sequenceName) {
        if (dbProductName != null && dbProductName.toLowerCase().contains("postgres")) {
            return "select nextval('" + sequenceName + "')";
        }
        return "SELECT " + sequenceName + ".NEXTVAL FROM DUAL";
    }
}
