package com.nara.nara_be.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BidFavoriteSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS progist.bid_favorites (
                    favorite_seq    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '즐겨찾기 일련번호',
                    user_seq        BIGINT        NOT NULL COMMENT '사용자 일련번호',
                    bid_no          VARCHAR(50)   NOT NULL COMMENT '입찰공고번호',
                    bid_ord         VARCHAR(10)   NOT NULL COMMENT '입찰공고차수',
                    announce_date   DATE          NOT NULL COMMENT '공고일자',
                    bid_name        VARCHAR(500)  NOT NULL COMMENT '공고명',
                    industry        VARCHAR(200)           COMMENT '업종',
                    contract_method VARCHAR(100)           COMMENT '계약방법',
                    opening_date    VARCHAR(50)            COMMENT '개찰일시',
                    estimated_price BIGINT                 COMMENT '추정가격',
                    agency          VARCHAR(200)           COMMENT '공고기관',
                    detail_url      VARCHAR(500)           COMMENT '상세 URL',
                    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
                    PRIMARY KEY (favorite_seq),
                    UNIQUE KEY uk_bid_favorites_user_bid (user_seq, bid_no, bid_ord, announce_date),
                    KEY idx_bid_favorites_user (user_seq)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='공고 즐겨찾기'
                """);
    }
}
