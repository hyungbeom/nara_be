-- progist 계정 (비밀번호: progist21c, BCrypt)
INSERT INTO progist.users (user_id, password, user_name, use_yn)
VALUES (
    'progist',
    '$2b$10$eTWHiSykdptxO16maeD1EuJH89TUWvgDjLBu6qnxUBtbQGzA9GvJq',
    'Progist',
    'Y'
)
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    user_name = VALUES(user_name),
    use_yn = VALUES(use_yn);
