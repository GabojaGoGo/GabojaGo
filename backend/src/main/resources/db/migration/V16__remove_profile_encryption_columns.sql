ALTER TABLE user_profiles
    CHANGE COLUMN nickname_enc nickname VARCHAR(100) NULL,
    CHANGE COLUMN email_enc email VARCHAR(255) NULL,
    DROP COLUMN email_hmac;

ALTER TABLE auth_login_requests
    CHANGE COLUMN code_verifier_enc code_verifier TEXT NOT NULL;
