ALTER TABLE auth_login_requests
    MODIFY COLUMN id VARCHAR(36) NOT NULL,
    MODIFY COLUMN pending_user_id VARCHAR(36) NULL;
