-- SpotCongestionLog entity → spot_congestion_logs 테이블
-- IF NOT EXISTS: 맥북 로컬 DB에는 이미 존재하므로 스킵, 아이맥 신규 DB에는 생성
CREATE TABLE IF NOT EXISTS spot_congestion_logs (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    spot_id                 BIGINT          NOT NULL,
    congestion              VARCHAR(255)    NOT NULL,
    congestion_source       VARCHAR(255)    NOT NULL,
    congestion_base_ymd     VARCHAR(255),
    created_at              DATETIME(6)     NOT NULL,
    PRIMARY KEY (id)
);
