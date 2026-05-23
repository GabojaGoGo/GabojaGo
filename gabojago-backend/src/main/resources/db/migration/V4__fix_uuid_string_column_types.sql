ALTER TABLE social_accounts DROP FOREIGN KEY fk_sa_user;
ALTER TABLE user_profiles DROP FOREIGN KEY fk_up_user;
ALTER TABLE user_consents DROP FOREIGN KEY fk_uc_user;
ALTER TABLE user_sessions DROP FOREIGN KEY fk_us_user;
ALTER TABLE user_preferences DROP FOREIGN KEY fk_upref_user;
ALTER TABLE user_footprints DROP FOREIGN KEY fk_uf_user;
ALTER TABLE user_bucket_list DROP FOREIGN KEY fk_ubl_user;
ALTER TABLE user_benefit_reports DROP FOREIGN KEY fk_ubr_user;
ALTER TABLE users
    MODIFY COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE social_accounts
    MODIFY COLUMN user_id VARCHAR(36) NOT NULL;
ALTER TABLE user_profiles
    MODIFY COLUMN user_id VARCHAR(36) NOT NULL;
ALTER TABLE user_consents
    MODIFY COLUMN user_id VARCHAR(36) NOT NULL;
ALTER TABLE user_sessions
    MODIFY COLUMN user_id VARCHAR(36) NOT NULL,
    MODIFY COLUMN token_family_id VARCHAR(36) NOT NULL;
ALTER TABLE user_preferences
    MODIFY COLUMN user_id VARCHAR(36) NOT NULL;
ALTER TABLE user_footprints
    MODIFY COLUMN user_id VARCHAR(36) NOT NULL;
ALTER TABLE user_bucket_list
    MODIFY COLUMN user_id VARCHAR(36) NOT NULL;
ALTER TABLE user_benefit_reports
    MODIFY COLUMN user_id VARCHAR(36) NOT NULL;
ALTER TABLE social_accounts
    ADD CONSTRAINT fk_sa_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE user_profiles
    ADD CONSTRAINT fk_up_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE user_consents
    ADD CONSTRAINT fk_uc_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE user_sessions
    ADD CONSTRAINT fk_us_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE user_preferences
    ADD CONSTRAINT fk_upref_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE user_footprints
    ADD CONSTRAINT fk_uf_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE user_bucket_list
    ADD CONSTRAINT fk_ubl_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE user_benefit_reports
    ADD CONSTRAINT fk_ubr_user FOREIGN KEY (user_id) REFERENCES users (id);
