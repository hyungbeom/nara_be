CREATE SCHEMA IF NOT EXISTS progist;

CREATE TABLE IF NOT EXISTS progist.users (
    user_seq    BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     VARCHAR(50)  NOT NULL,
    password    VARCHAR(255) NOT NULL,
    user_name   VARCHAR(100) NOT NULL,
    use_yn      CHAR(1)      NOT NULL DEFAULT 'Y',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_seq),
    UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS progist.sample (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    PRIMARY KEY (id)
);
