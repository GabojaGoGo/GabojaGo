-- =====================================================
-- V1: 인증/사용자 기본 스키마
-- =====================================================

-- 1. users: 서비스 내부 사용자 기준축
CREATE TABLE users (
    id          CHAR(36)     NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'  COMMENT 'ACTIVE|LOCKED|DELETED|PENDING',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    last_login_at DATETIME(3)  NULL,
    deleted_at  DATETIME(3)  NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. social_accounts: 카카오 계정 연동 정보
CREATE TABLE social_accounts (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           CHAR(36)     NOT NULL,
    provider          VARCHAR(32)  NOT NULL  COMMENT 'KAKAO',
    provider_user_id  VARCHAR(128) NOT NULL,
    connected_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_login_at     DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_provider_user (provider, provider_user_id),
    CONSTRAINT fk_sa_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. user_profiles: 최소 프로필 (암호화 저장)
CREATE TABLE user_profiles (
    user_id           CHAR(36)     NOT NULL,
    nickname_enc      TEXT         NULL  COMMENT 'AES-256-GCM 암호화',
    email_enc         TEXT         NULL  COMMENT 'AES-256-GCM 암호화',
    email_hmac        VARCHAR(64)  NULL  COMMENT 'HMAC-SHA256 (중복 검사용)',
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_up_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. user_consents: 약관 동의 이력
CREATE TABLE user_consents (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          CHAR(36)     NOT NULL,
    terms_version    VARCHAR(16)  NOT NULL,
    privacy_version  VARCHAR(16)  NOT NULL,
    marketing_opt_in TINYINT(1)   NOT NULL DEFAULT 0,
    consented_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ip_hash          VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    KEY idx_uc_user (user_id),
    CONSTRAINT fk_uc_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. user_sessions: Refresh Token 세션 관리 (rotation + 재사용 탐지)
CREATE TABLE user_sessions (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                 CHAR(36)     NOT NULL,
    device_id               VARCHAR(128) NULL,
    refresh_token_hash      VARCHAR(64)  NOT NULL  COMMENT 'SHA-256',
    token_family_id         CHAR(36)     NOT NULL  COMMENT '재사용 탐지용 패밀리 ID',
    issued_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at              DATETIME(3)  NOT NULL,
    last_used_at            DATETIME(3)  NULL,
    revoked_at              DATETIME(3)  NULL,
    revoke_reason           VARCHAR(64)  NULL,
    ip_hash                 VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_rth (refresh_token_hash),
    KEY idx_us_user (user_id),
    KEY idx_us_family (token_family_id),
    CONSTRAINT fk_us_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. auth_login_requests: OAuth 로그인 시작~콜백 완료 임시 상태
CREATE TABLE auth_login_requests (
    id                    CHAR(36)     NOT NULL,
    state_hash            VARCHAR(64)  NOT NULL  COMMENT 'SHA-256(state)',
    code_verifier_enc     TEXT         NOT NULL  COMMENT 'PKCE code_verifier 암호화',
    nonce_hash            VARCHAR(64)  NULL,
    platform              VARCHAR(16)  NOT NULL DEFAULT 'ANDROID'  COMMENT 'ANDROID|IOS',
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at            DATETIME(3)  NOT NULL,
    used_at               DATETIME(3)  NULL,
    pending_access_token  TEXT         NULL  COMMENT '콜백 완료 후 교환 전 임시 저장',
    pending_refresh_token TEXT         NULL,
    pending_user_id       CHAR(36)     NULL,
    is_new_user           TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_state_hash (state_hash),
    KEY idx_alr_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
