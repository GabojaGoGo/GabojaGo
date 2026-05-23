-- Hibernate 6 물리 네이밍: camelCase → snake_case 자동 변환
-- AreaDailyTotal entity → area_daily_totals 테이블
CREATE TABLE area_daily_totals (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    base_ymd        VARCHAR(8)      NOT NULL,
    area_code       VARCHAR(20)     NOT NULL,
    area_name       VARCHAR(255),
    daywk_div_cd    INT,
    total_tou_num   DOUBLE          NOT NULL,
    fetched_at      DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_area_total_base_code UNIQUE (base_ymd, area_code),
    INDEX idx_area_total_base_ymd (base_ymd)
);

-- SigunguDailyTotal entity → sigungu_daily_totals 테이블
CREATE TABLE sigungu_daily_totals (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    base_ymd        VARCHAR(8)      NOT NULL,
    signgu_code     VARCHAR(20)     NOT NULL,
    signgu_name     VARCHAR(255),
    daywk_div_cd    INT,
    total_tou_num   DOUBLE          NOT NULL,
    fetched_at      DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sigungu_total_base_code UNIQUE (base_ymd, signgu_code),
    INDEX idx_sigungu_total_base_ymd (base_ymd)
);

-- CongestionApiBatch entity → congestion_api_batches 테이블
CREATE TABLE congestion_api_batches (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    scope           VARCHAR(16)     NOT NULL,
    request_ymd     VARCHAR(8)      NOT NULL,
    endpoint        VARCHAR(128)    NOT NULL,
    result_code     VARCHAR(8),
    result_msg      VARCHAR(128),
    total_count     INT             NOT NULL,
    fetched_at      DATETIME(6)     NOT NULL,
    raw_json        LONGTEXT,
    PRIMARY KEY (id),
    INDEX idx_congestion_batch_scope_ymd (scope, request_ymd),
    INDEX idx_congestion_batch_fetched_at (fetched_at)
);

-- CongestionApiItem entity → congestion_api_items 테이블
CREATE TABLE congestion_api_items (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    batch_id        BIGINT          NOT NULL,
    code            VARCHAR(16)     NOT NULL,
    name            VARCHAR(128),
    daywk_div_cd    INT,
    daywk_div_nm    VARCHAR(32),
    tou_div_cd      VARCHAR(8),
    tou_div_nm      VARCHAR(64),
    tou_num         DOUBLE,
    base_ymd        VARCHAR(8),
    PRIMARY KEY (id),
    CONSTRAINT fk_congestion_item_batch FOREIGN KEY (batch_id) REFERENCES congestion_api_batches (id),
    INDEX idx_congestion_item_code_base_ymd (code, base_ymd),
    INDEX idx_congestion_item_daywk (daywk_div_cd)
);
