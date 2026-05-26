ALTER TABLE users
    ADD COLUMN nickname VARCHAR(100) NULL AFTER status,
    ADD COLUMN email VARCHAR(255) NULL AFTER nickname;

UPDATE users u
LEFT JOIN user_profiles up ON up.user_id = u.id
SET u.nickname = COALESCE(up.nickname, '여행자'),
    u.email = up.email;

ALTER TABLE users
    MODIFY COLUMN nickname VARCHAR(100) NOT NULL DEFAULT '여행자';

DROP TABLE user_profiles;
