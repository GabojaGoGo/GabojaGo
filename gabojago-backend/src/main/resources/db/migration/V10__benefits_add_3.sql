-- V6: 혜택 3건 추가
-- 근로자 휴가지원사업 / 문화누리카드 / 여행가는 봄(코레일)

-- ─── 1. 근로자 휴가지원사업 ────────────────────────────────
-- 중소기업·소상공인 근로자: 본인 20만 + 기업 10만 + 정부 10만 = 40만 여행 포인트
-- 선착순 15만명 (2026.1.30 오픈, 조기마감 가능성 높음)
INSERT INTO benefits (
    id, title, subtitle, description,
    category, benefit_type, status_label, status_type,
    gradient_start, gradient_end, icon_name, apply_url,
    sort_order, is_active, end_at,
    detail_json
) VALUES (
    'worker_vacation',
    '근로자 휴가지원사업',
    '총 40만원 여행 포인트 · 중소기업·소상공인 근로자 대상',
    '한국관광공사 주관. 근로자 20만원 적립 시 기업(10만) + 정부(10만)가 더해져 총 40만원 포인트 지급. 휴가샵 온라인몰에서 숙박·교통·테마파크·렌터카 등 국내여행 상품 구매 가능.',
    '근로자지원', 'pre', '12/31 사용기한', 'deadline',
    '#1565C0', '#42A5F5', 'business_center',
    'https://vacation.visitkorea.or.kr',
    4, true, '2026-12-31 23:59:59',
    JSON_OBJECT(
        'highlights', JSON_ARRAY(
            JSON_OBJECT(
                'icon','savings',
                'title','총 40만원 여행 포인트',
                'desc','근로자 20만 + 기업 10만 + 정부 10만원. 누적 참여 5년차 이상 중기업은 기업 15만·정부 5만으로 조정. 총액은 동일.',
                'actionLabel', NULL
            ),
            JSON_OBJECT(
                'icon','business_center',
                'title','중소기업·소상공인·비영리단체 근로자',
                'desc','중견기업·전문직(의사·변호사·건축사 등)·임원(소상공인 제외)은 참여 불가. 6세 이상 소속 근로자 신청 가능.',
                'actionLabel', NULL
            ),
            JSON_OBJECT(
                'icon','store',
                'title','휴가샵(vacation.benepia.co.kr)에서 사용',
                'desc','약 27만 개 상품: 전국 숙소·렌터카·국내선 항공·테마파크·워터파크·국내 패키지여행·캠핑용품. 현금 환불 불가.',
                'actionLabel', NULL
            ),
            JSON_OBJECT(
                'icon','event',
                'title','2026년 12월 31일까지 사용 필수',
                'desc','미사용 정부 지원금(10만)은 국고 환수. 근로자·기업 분담금은 환불 신청 가능. 기한 연장 없음.',
                'actionLabel', NULL
            )
        ),
        'steps', JSON_ARRAY(
            JSON_OBJECT(
                'step','01',
                'title','기업 담당자 온라인 신청',
                'desc','vacation.visitkorea.or.kr → 기업 단위 신청. 중소기업확인서·법인등기부등본(3개월 이내)·사업자등록증 등 서류 제출.',
                'icon','assignment'
            ),
            JSON_OBJECT(
                'step','02',
                'title','근로자 분담금 납부 및 포인트 지급',
                'desc','기업 승인 후 근로자 1인당 20만원 납부. 기업·정부 분담금 합산 → 총 40만원 포인트 자동 지급.',
                'icon','savings'
            ),
            JSON_OBJECT(
                'step','03',
                'title','휴가샵 가입 및 상품 구매',
                'desc','vacation.benepia.co.kr 또는 휴가샵 앱 가입 후 포인트 등록. 숙박·교통·체험 상품 자유롭게 구매.',
                'icon','store'
            ),
            JSON_OBJECT(
                'step','04',
                'title','2026년 12월 31일 이전 사용 완료',
                'desc','기한 내 미사용 포인트 소멸. 정부 지원분은 국고 환수. 문의: 1670-1330.',
                'icon','event_available'
            )
        )
    )
);

-- ─── 2. 문화누리카드(통합문화이용권) ──────────────────────
-- 기초생활수급자·차상위계층 1인 연 15만원 (청소년·준고령기 +1만원)
-- 여행사·숙박·교통 포함 35,000개 가맹점 이용 가능
INSERT INTO benefits (
    id, title, subtitle, description,
    category, benefit_type, status_label, status_type,
    gradient_start, gradient_end, icon_name, apply_url,
    sort_order, is_active, end_at,
    detail_json
) VALUES (
    'culture_nuri',
    '문화누리카드',
    '연 최대 16만원 지원 · 기초수급·차상위 대상 · 여행사·숙박 사용 가능',
    '한국문화예술위원회 주관. 기초생활수급자·차상위계층(6세 이상) 대상 연 15만원 지원. 청소년(13~18세)·준고령기(60~64세)는 1만원 추가. 하나투어·야놀자·여기어때 등 여행 가맹점 포함 35,000개 사용처.',
    '문화복지', 'local', '~11/30 신청가능', 'ongoing',
    '#6A1B9A', '#9C27B0', 'credit_card',
    'https://www.mnuri.kr',
    5, true, '2026-12-31 23:59:59',
    JSON_OBJECT(
        'highlights', JSON_ARRAY(
            JSON_OBJECT(
                'icon','credit_card',
                'title','연 최대 16만원 지원',
                'desc','기초생활수급자·차상위계층 1인당 연 15만원. 청소년(13~18세)·준고령기(60~64세)는 1만원 추가 지원.',
                'actionLabel', NULL
            ),
            JSON_OBJECT(
                'icon','hotel',
                'title','여행사·숙박·교통 사용 가능',
                'desc','하나투어·모두투어·인터파크·NH여행 등 여행사, 야놀자·여기어때·아고다·익스피디아 숙박 플랫폼, 코레일(기차) 이용 가능.',
                'actionLabel', NULL
            ),
            JSON_OBJECT(
                'icon','qr_code',
                'title','NHpay 앱으로 온라인 결제',
                'desc','NHpay 앱 [더보기 > 결제 > 기프트카드]에서 카드 등록 후 QR·결제코드로 온라인 가맹점 결제. 교보문고·CGV 등 1,000개 이상 온라인 가맹점 사용.',
                'actionLabel', NULL
            ),
            JSON_OBJECT(
                'icon','event_available',
                'title','2026년 2/2~11/30 신청, 12/31 사용기한',
                'desc','미사용 지원금은 자동 국고반납. 사용기간 연장 없음. 2025년 3만원 이상 사용자는 별도 신청 없이 자동재충전.',
                'actionLabel', NULL
            )
        ),
        'steps', JSON_ARRAY(
            JSON_OBJECT(
                'step','01',
                'title','주민센터 방문 또는 온라인 신청',
                'desc','읍·면·동 주민센터 방문 신청, mnuri.kr 온라인 신청, 또는 문화누리카드 앱에서 신청. ☎ 1544-3412.',
                'icon','assignment'
            ),
            JSON_OBJECT(
                'step','02',
                'title','자격 확인 및 카드 수령',
                'desc','발급 자격 검증 약 7일 소요. 주민센터 방문 수령·등기우편·농협 영업점(약 2시간) 중 선택.',
                'icon','savings'
            ),
            JSON_OBJECT(
                'step','03',
                'title','NHpay 앱에 카드 등록',
                'desc','NHpay(구 올원페이) 앱 다운로드 → [더보기 > 결제 > 기프트카드] → 문화누리카드 등록. 온라인 가맹점 QR 결제 가능.',
                'icon','qr_code'
            ),
            JSON_OBJECT(
                'step','04',
                'title','여행·문화·체육 가맹점 사용',
                'desc','여행사·숙박·교통·공연·영화·도서·스포츠 35,000개 가맹점에서 사용. 오프라인은 실물카드, 온라인은 NHpay 앱 이용.',
                'icon','map'
            )
        )
    )
);

-- ─── 3. 여행가는 봄 — 코레일 기차여행 혜택 ────────────────
-- 인구감소지역 42곳행 열차운임 100% 환급 (지정관광지 QR 인증 시)
-- 테마열차 5개 노선 50% 할인, 내일로 패스 2만원 추가 할인
-- 탑승기간: 2026.4.1~5.31
INSERT INTO benefits (
    id, title, subtitle, description,
    category, benefit_type, status_label, status_type,
    gradient_start, gradient_end, icon_name, apply_url,
    sort_order, is_active, start_at, end_at,
    detail_json
) VALUES (
    'korail_spring',
    '여행가는 봄 — 코레일',
    '인구감소지역 열차운임 100% 환급 · 테마열차 50% · 4/1~5/31',
    '문화체육관광부·한국철도공사 주관. 42개 인구감소지역행 자유여행상품 구매 후 지정 관광지 1곳 이상 방문 인증 시 열차운임 전액 환급쿠폰 지급. 테마열차 5개 노선 50% 할인, 내일로 패스 2만원 추가 할인.',
    '교통할인', 'pre', '~5/31 탑승', 'deadline',
    '#B71C1C', '#E53935', 'train',
    'https://www.korailtravel.com',
    2, true, '2026-04-01 00:00:00', '2026-05-31 23:59:59',
    JSON_OBJECT(
        'highlights', JSON_ARRAY(
            JSON_OBJECT(
                'icon','train',
                'title','인구감소지역 42곳 열차운임 100% 환급',
                'desc','강원·경상·충청·전라 42개 인구감소지역행 자유여행상품 구매 후, 지정 관광지 최소 1곳 방문 인증 시 열차운임 전액 쿠폰으로 환급. 쿠폰 유효기간 1년.',
                'actionLabel', NULL
            ),
            JSON_OBJECT(
                'icon','percent',
                'title','테마열차 5개 노선 50% 할인',
                'desc','서해금빛(용산~익산)·남도해양(서울~여수/부산~목포)·동해산타(강릉~분천)·백두대간협곡(영주~철암)·정선아리랑(청량리~민둥산) 열차 50% 할인.',
                'actionLabel', NULL
            ),
            JSON_OBJECT(
                'icon','confirmation_number',
                'title','내일로 패스 2만원 추가 할인',
                'desc','연속 7일권·선택 3일권 모두 기존 할인 대비 2만원 추가 할인. 하루 400매 한정 선착순 판매 (~5/30). KTX 포함 모든 열차 이용.',
                'actionLabel', NULL
            ),
            JSON_OBJECT(
                'icon','place',
                'title','42개 인구감소지역 전국 방방곡곡',
                'desc','강원 삼척·태백·영월·정선·횡성, 경상 문경·밀양·안동·영덕·의성 등, 충청 공주·보령·제천·서천 등, 전라 남원·고흥·강진·해남 등 42곳.',
                'actionLabel', NULL
            )
        ),
        'steps', JSON_ARRAY(
            JSON_OBJECT(
                'step','01',
                'title','코레일톡 앱 또는 korailtravel.com에서 예매',
                'desc','인구감소지역행 자유여행상품·테마열차·내일로 패스 중 원하는 상품 구매. 4/1~5/31 탑승. 내일로 패스는 3/25~5/30 판매.',
                'icon','assignment'
            ),
            JSON_OBJECT(
                'step','02',
                'title','인구감소지역 방문',
                'desc','42개 인구감소지역 중 원하는 곳으로 기차 여행. 지정 관광지 1곳 이상 방문.',
                'icon','flight_takeoff'
            ),
            JSON_OBJECT(
                'step','03',
                'title','코레일톡으로 QR 방문 인증',
                'desc','지정 관광지에서 코레일톡 앱으로 QR코드 스캔 또는 대한민국 구석구석 앱 디지털관광주민증으로 인증. 탑승 후 5일 이내 필수.',
                'icon','qr_code'
            ),
            JSON_OBJECT(
                'step','04',
                'title','열차운임 환급 쿠폰 자동 수령',
                'desc','인증 완료 후 자동 지급. 발행일로부터 1년 이내 KTX 이하 모든 열차 승차권 구매 시 사용 가능. 명절(설·추석) 대수송기간 제외.',
                'icon','savings'
            )
        )
    )
);

-- 숙박세일페스타 sort_order 업데이트 (코레일보다 긴급하게 조정)
UPDATE benefits SET sort_order = 1 WHERE id = 'sale_festa';
UPDATE benefits SET sort_order = 3 WHERE id = 'vacation_support';
