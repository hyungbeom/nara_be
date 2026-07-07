-- progist 스키마 사용자 테이블
CREATE TABLE IF NOT EXISTS progist.users (
    user_seq    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '사용자 일련번호',
    user_id     VARCHAR(50)  NOT NULL COMMENT '로그인 아이디',
    password    VARCHAR(255) NOT NULL COMMENT '비밀번호 (BCrypt)',
    user_name   VARCHAR(100) NOT NULL COMMENT '사용자명',
    use_yn      CHAR(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (user_seq),
    UNIQUE KEY uk_users_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자';

CREATE TABLE IF NOT EXISTS progist.sample (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '샘플 ID',
    name        VARCHAR(100) NOT NULL COMMENT '이름',
    description VARCHAR(500)          COMMENT '설명',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='샘플';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='공고 즐겨찾기';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='즐겨찾기 첨부파일';
