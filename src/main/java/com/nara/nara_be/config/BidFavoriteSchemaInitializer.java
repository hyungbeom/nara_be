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
                    detail_content  LONGTEXT               COMMENT '공고 상세내용',
                    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
                    PRIMARY KEY (favorite_seq),
                    UNIQUE KEY uk_bid_favorites_user_bid (user_seq, bid_no, bid_ord, announce_date),
                    KEY idx_bid_favorites_user (user_seq)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='공고 즐겨찾기'
                """);

        jdbcTemplate.execute("""
                ALTER TABLE progist.bid_favorites
                ADD COLUMN IF NOT EXISTS detail_content LONGTEXT NULL COMMENT '공고 상세내용' AFTER detail_url
                """);

        jdbcTemplate.execute("""
                ALTER TABLE progist.bid_favorites
                ADD COLUMN IF NOT EXISTS contact_name VARCHAR(100) NULL COMMENT '담당자명' AFTER detail_content
                """);

        jdbcTemplate.execute("""
                ALTER TABLE progist.bid_favorites
                ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(50) NULL COMMENT '담당자 전화번호' AFTER contact_name
                """);

        jdbcTemplate.execute("""
                ALTER TABLE progist.bid_favorites
                ADD COLUMN IF NOT EXISTS contact_email VARCHAR(200) NULL COMMENT '담당자 이메일' AFTER contact_phone
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS progist.bid_favorite_attachments (
                    attachment_seq       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '첨부파일 일련번호',
                    favorite_seq         BIGINT        NOT NULL COMMENT '즐겨찾기 일련번호',
                    original_file_name   VARCHAR(500)  NOT NULL COMMENT '원본 파일명',
                    stored_file_name     VARCHAR(500)  NOT NULL COMMENT '저장 파일명',
                    original_url         VARCHAR(1000)          COMMENT '원본 URL',
                    content_type         VARCHAR(100)           COMMENT 'MIME 타입',
                    file_size            BIGINT                 COMMENT '파일 크기',
                    converted_from_hwpx  TINYINT(1)    NOT NULL DEFAULT 0 COMMENT 'HWPX PDF 변환 여부',
                    storage_path         VARCHAR(1000) NOT NULL COMMENT '저장 경로',
                    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
                    PRIMARY KEY (attachment_seq),
                    KEY idx_bid_favorite_attachments_favorite (favorite_seq)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='즐겨찾기 첨부파일'
                """);

        jdbcTemplate.execute("""
                ALTER TABLE progist.bid_favorite_attachments
                ADD COLUMN IF NOT EXISTS google_drive_file_id VARCHAR(100) NULL COMMENT '구글 드라이브 파일 ID' AFTER storage_path
                """);

        jdbcTemplate.execute("""
                ALTER TABLE progist.bid_favorites
                ADD COLUMN IF NOT EXISTS google_drive_folder_id VARCHAR(100) NULL COMMENT '구글 드라이브 폴더 ID' AFTER contact_email
                """);
    }
}
