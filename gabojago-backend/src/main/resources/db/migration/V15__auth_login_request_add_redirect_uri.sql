ALTER TABLE auth_login_requests
    ADD COLUMN redirect_uri VARCHAR(500) NULL AFTER platform;
