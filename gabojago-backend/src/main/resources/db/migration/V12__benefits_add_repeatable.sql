-- is_repeatable 컬럼 추가 (기본값 false = 일회성)
ALTER TABLE benefits
    ADD COLUMN is_repeatable TINYINT(1) NOT NULL DEFAULT 0
        AFTER is_active;

-- 일회성 혜택 (연 1회 신청)
-- vacation_support, worker_vacation, culture_nuri → 기본값 0 유지

-- 반복 가능 혜택 (여러 번 이용 가능)
UPDATE benefits SET is_repeatable = 1
WHERE id IN ('sale_festa', 'gift_voucher', 'korail_spring');
