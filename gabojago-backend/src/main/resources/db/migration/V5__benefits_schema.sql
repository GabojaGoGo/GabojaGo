-- V5: 혜택 콘텐츠 테이블 (앱 재배포 없이 DB만 수정해서 혜택 추가/수정 가능)
CREATE TABLE benefits (
    id             VARCHAR(50)  NOT NULL,
    title          VARCHAR(100) NOT NULL,
    subtitle       VARCHAR(200),
    description    TEXT,
    category       VARCHAR(30)  COMMENT '정부지원|숙박할인|상품권',
    benefit_type   VARCHAR(20)  COMMENT 'pre(예약전)|local(현지소비)',
    status_label   VARCHAR(50)  COMMENT '화면에 표시될 상태 텍스트',
    status_type    VARCHAR(20)  COMMENT 'deadline|upcoming|ongoing|monthly',
    gradient_start VARCHAR(10)  COMMENT '#RRGGBB',
    gradient_end   VARCHAR(10),
    icon_name      VARCHAR(50)  COMMENT 'Material Icon 이름',
    apply_url      VARCHAR(300) COMMENT '신청 외부 URL',
    sort_order     INT          NOT NULL DEFAULT 0,
    is_active      BOOLEAN      NOT NULL DEFAULT true,
    start_at       DATETIME(3),
    end_at         DATETIME(3),
    detail_json    JSON         COMMENT 'highlights/regions/steps 구조화 데이터',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_benefits_active_sort (is_active, sort_order)
);

-- ─── 초기 데이터 ────────────────────────────────────────────

-- 1. 지역사랑 휴가지원 (인구감소지역 50% 환급, 2026.4~6월)
INSERT INTO benefits (
    id, title, subtitle, description,
    category, benefit_type, status_label, status_type,
    gradient_start, gradient_end, icon_name, apply_url,
    sort_order, is_active, end_at,
    detail_json
) VALUES (
    'vacation_support',
    '지역사랑 휴가지원',
    '여행비 50% 환급 · 인구감소지역 16곳 · 최대 10만원',
    '문화체육관광부·한국관광공사 주관. 인구감소지역 16곳 방문 시 숙박·식사·관광 비용의 50%를 모바일 지역사랑상품권으로 환급. 2인 이상 동반 시 최대 20만원.',
    '정부지원', 'local', '6/30 마감', 'deadline',
    '#5C4AE3', '#7B6CF0', 'location_on',
    'https://korean.visitkorea.or.kr',
    1, true, '2026-06-30 23:59:59',
    JSON_OBJECT(
        'highlights', JSON_ARRAY(
            JSON_OBJECT('icon','savings','title','최대 10만원 환급','desc','여행경비의 50%를 모바일 지역사랑상품권으로 환급. 2인 이상 동반 시 최대 20만원.','actionLabel',NULL),
            JSON_OBJECT('icon','place','title','인구감소지역 16곳','desc','강원·충북·전라·경남 16개 인구감소지역 방문 시 적용됩니다.','actionLabel',NULL),
            JSON_OBJECT('icon','event_available','title','2026년 4~6월 신청','desc','4월 오픈 즉시 신청 권장. 예산(65억원) 소진 시 예고 없이 마감됩니다.','actionLabel',NULL),
            JSON_OBJECT('icon','assignment_turned_in','title','여행 전 사전 신청 필수','desc','지자체 승인 없이 여행 시 환급 불가. 반드시 승인 확인 후 여행하세요.','actionLabel',NULL)
        ),
        'regions', JSON_ARRAY(
            JSON_OBJECT('name','강원 평창','category','자연/스키','budgetRate',0.72,'maxAmount',100000),
            JSON_OBJECT('name','강원 영월','category','자연/래프팅','budgetRate',0.65,'maxAmount',100000),
            JSON_OBJECT('name','강원 횡성','category','한우/자연','budgetRate',0.80,'maxAmount',100000),
            JSON_OBJECT('name','충북 제천','category','청풍호/약초','budgetRate',0.58,'maxAmount',100000),
            JSON_OBJECT('name','전북 고창','category','청보리/체험','budgetRate',0.71,'maxAmount',100000),
            JSON_OBJECT('name','전남 영광','category','굴비/불갑사','budgetRate',0.83,'maxAmount',100000),
            JSON_OBJECT('name','전남 영암','category','왕인박사/농촌','budgetRate',0.67,'maxAmount',100000),
            JSON_OBJECT('name','전남 강진','category','청자/다산','budgetRate',0.75,'maxAmount',100000),
            JSON_OBJECT('name','전남 해남','category','땅끝마을/공룡','budgetRate',0.62,'maxAmount',100000),
            JSON_OBJECT('name','전남 고흥','category','우주센터/해양','budgetRate',0.78,'maxAmount',100000),
            JSON_OBJECT('name','전남 완도','category','청산도/해조류','budgetRate',0.70,'maxAmount',100000),
            JSON_OBJECT('name','경남 합천','category','해인사/드라마','budgetRate',0.55,'maxAmount',100000),
            JSON_OBJECT('name','경남 하동','category','녹차/섬진강','budgetRate',0.68,'maxAmount',100000),
            JSON_OBJECT('name','경남 밀양','category','밀양아리랑/얼음골','budgetRate',0.74,'maxAmount',100000),
            JSON_OBJECT('name','경남 남해','category','독일마을/보리암','budgetRate',0.60,'maxAmount',100000),
            JSON_OBJECT('name','경남 거창','category','수승대/가조온천','budgetRate',0.82,'maxAmount',100000)
        ),
        'steps', JSON_ARRAY(
            JSON_OBJECT('step','01','title','사전 신청','desc','여행 전 대한민국 구석구석 또는 해당 지자체 홈페이지에서 여행계획 신청 후 승인 대기.','icon','assignment'),
            JSON_OBJECT('step','02','title','여행 실행','desc','승인 완료 후 해당 인구감소지역 방문. 숙박·식사·관광 비용 결제.','icon','flight_takeoff'),
            JSON_OBJECT('step','03','title','영수증 제출','desc','여행 후 앱/홈페이지에서 지출 증빙서류 업로드 (영업일 이내 제출).','icon','receipt_long'),
            JSON_OBJECT('step','04','title','지역사랑상품권 환급','desc','심사 완료 후 모바일 지역사랑상품권으로 환급 (영업일 7일 이내).','icon','savings')
        )
    )
);

-- 2. 2026 봄 숙박세일페스타 (4/8~4/30, 비수도권 2~7만원 정액 할인)
INSERT INTO benefits (
    id, title, subtitle, description,
    category, benefit_type, status_label, status_type,
    gradient_start, gradient_end, icon_name, apply_url,
    sort_order, is_active, start_at, end_at,
    detail_json
) VALUES (
    'sale_festa',
    '2026 봄 숙박세일페스타',
    '비수도권 숙박 최대 7만원 할인 · 4/8~4/30',
    '한국관광공사·문화체육관광부 주관. 비수도권 전국 숙박시설 정액 할인 쿠폰 제공. 야놀자·여기어때·인터파크 등 17개 OTA에서 매일 오전 10시 선착순 발급.',
    '숙박할인', 'pre', '4/8~4/30', 'deadline',
    '#E65100', '#FF8F00', 'hotel',
    'https://ktostay.visitkorea.or.kr',
    2, true, '2026-04-08 00:00:00', '2026-04-30 23:59:59',
    JSON_OBJECT(
        'highlights', JSON_ARRAY(
            JSON_OBJECT('icon','hotel','title','비수도권 전국 숙박시설 대상','desc','서울·경기·인천·세종 제외 전국 호텔·콘도·펜션·모텔·농촌민박 모두 포함.','actionLabel',NULL),
            JSON_OBJECT('icon','confirmation_number','title','매일 오전 10시 선착순 발급','desc','야놀자·여기어때·인터파크·G마켓 등 17개 OTA에서 1인 1매 선착순. 유효기간 21시간.','actionLabel',NULL),
            JSON_OBJECT('icon','percent','title','정액 할인 최대 7만원','desc','1박 7만원 미만 → 2만원 할인 / 1박 7만원 이상 → 3만원 할인\n연박 14만원 미만 → 5만원 / 연박 14만원 이상 → 7만원 할인','actionLabel',NULL),
            JSON_OBJECT('icon','notifications_active','title','오픈 알림 설정 권장','desc','인기 숙소는 오픈 직후 마감. 참여 OTA 앱 알림을 미리 설정해두세요.','actionLabel','알림 설정')
        )
    )
);

-- 3. 온누리상품권 (전통시장·지역상점 7% 할인 구매, 상시)
INSERT INTO benefits (
    id, title, subtitle, description,
    category, benefit_type, status_label, status_type,
    gradient_start, gradient_end, icon_name, apply_url,
    sort_order, is_active,
    detail_json
) VALUES (
    'gift_voucher',
    '온누리상품권',
    '전통시장·지역상점 7% 할인 구매 · 상시',
    '소상공인시장진흥공단 발행. 전통시장 및 지역 소상공인 가맹점에서 액면가 대비 7% 할인된 가격으로 구매해 사용. 명절 기간(설·추석)에는 최대 10% 할인.',
    '상품권', 'local', '상시 이용 가능', 'ongoing',
    '#283593', '#3949AB', 'savings',
    'https://onnurigift.or.kr',
    3, true,
    JSON_OBJECT(
        'highlights', JSON_ARRAY(
            JSON_OBJECT('icon','store','title','전통시장·지역 소상공인 전용','desc','대형마트·프랜차이즈 사용 불가. 온누리상품권 가맹 전통시장 및 지역 소상공인 매장에서만 사용 가능.','actionLabel',NULL),
            JSON_OBJECT('icon','percent','title','7% 할인 구매','desc','액면가 대비 7% 할인된 가격으로 구매. 명절(설·추석)에는 최대 10% 할인. 잔액 이월 가능.','actionLabel',NULL),
            JSON_OBJECT('icon','qr_code','title','QR·모바일 상품권 지원','desc','온누리페이 앱에서 QR코드로 즉시 구매·결제. 실물 상품권도 전통시장에서 구매 가능.','actionLabel',NULL),
            JSON_OBJECT('icon','map','title','가맹점 지도 연동','desc','여행지 근처 온누리상품권 가맹점을 지도에서 바로 확인하세요.','actionLabel','가맹점 찾기')
        )
    )
);
