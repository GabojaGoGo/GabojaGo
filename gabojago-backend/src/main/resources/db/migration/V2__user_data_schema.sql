-- =====================================================
-- V2: 사용자 개인 데이터 스키마
-- =====================================================

-- 1. user_preferences: 여행 취향 설정
CREATE TABLE user_preferences (
    user_id    CHAR(36)     NOT NULL,
    purposes   VARCHAR(256) NULL     COMMENT 'comma-separated keys (resort,food,...)',
    duration   VARCHAR(16)  NULL     COMMENT 'day|1n2d|2n3d|3nplus',
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_upref_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. user_footprints: 방문 족적
CREATE TABLE user_footprints (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     CHAR(36)     NOT NULL,
    spot_id     VARCHAR(64)  NULL     COMMENT '투어 API contentId',
    spot_name   VARCHAR(128) NOT NULL,
    region_name VARCHAR(64)  NOT NULL,
    visited_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    tags        VARCHAR(256) NULL     COMMENT 'comma-separated',
    PRIMARY KEY (id),
    KEY idx_uf_user (user_id),
    KEY idx_uf_visited (user_id, visited_at DESC),
    CONSTRAINT fk_uf_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. user_bucket_list: 버킷리스트
CREATE TABLE user_bucket_list (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      CHAR(36)     NOT NULL,
    title        VARCHAR(128) NOT NULL,
    area         VARCHAR(64)  NULL,
    note         VARCHAR(512) NULL,
    is_completed TINYINT(1)   NOT NULL DEFAULT 0,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ubl_user (user_id),
    CONSTRAINT fk_ubl_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. user_benefit_reports: 혜택 리포트 항목
CREATE TABLE user_benefit_reports (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       CHAR(36)     NOT NULL,
    benefit_type  VARCHAR(32)  NOT NULL COMMENT 'SUBSIDY|COUPON|CASHBACK|OTHER',
    benefit_label VARCHAR(128) NOT NULL,
    amount        INT          NOT NULL DEFAULT 0,
    applied_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ubr_user (user_id),
    KEY idx_ubr_applied (user_id, applied_at DESC),
    CONSTRAINT fk_ubr_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
