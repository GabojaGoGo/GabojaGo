-- V8: 지역사랑 휴가지원 — 실제 데이터 반영
--   · 청년(19~34세) 70% 환급, 최대 14만원
--   · 가족(5인) 최대 50만원
--   · 지역별 실제 신청 상태/기간/지역화폐/문의처
--   · Q&A 섹션 추가

UPDATE benefits
SET
    subtitle     = '여행비 50% 환급 · 청년 70% · 가족 최대 50만원',
    description  = '문화체육관광부·한국관광공사 주관. 인구감소지역 16곳 방문 시 숙박·식사·체험 비용의 50%를 지역화폐로 환급합니다. 1인 최대 10만원, 2인 이상 최대 20만원. 청년(19~34세)은 70% 환급으로 최대 14만원. 가족 단위(5인)는 최대 50만원. 여행 출발 전 반드시 사전 신청 후 승인을 받아야 합니다.',
    detail_json  = JSON_OBJECT(
        'highlights', JSON_ARRAY(
            JSON_OBJECT('icon','savings','title','기본 50% 환급 — 최대 20만원',
                'desc','숙박·식사·체험 비용의 50%를 지역화폐로 환급. 1인 최대 10만원, 2인 이상 동반 시 최대 20만원.','actionLabel',NULL),
            JSON_OBJECT('icon','percent','title','청년 특별혜택 (19~34세) — 최대 14만원',
                'desc','청년(19~34세)은 환급률 20%p 추가 상향으로 1인당 70% 환급, 최대 14만원.','actionLabel',NULL),
            JSON_OBJECT('icon','savings','title','가족 단위 최대 50만원',
                'desc','가족 단위 신청 시 5인까지 지원. 최대 50만원 환급 가능.','actionLabel',NULL),
            JSON_OBJECT('icon','assignment_turned_in','title','여행 전 사전 신청 필수',
                'desc','여행 출발 1~5일 전(지역마다 상이) 신청 후 승인 확인 필수. 거주지 인접 지역은 신청 불가. 지정관광지 방문 인증사진도 제출해야 합니다.','actionLabel',NULL),
            JSON_OBJECT('icon','event_available','title','예산 소진 시 조기 마감',
                'desc','전체 예산 소진 시 신청기간 내라도 예고 없이 마감됩니다. 오픈 즉시 신청을 권장합니다.','actionLabel',NULL)
        ),
        'regions', JSON_ARRAY(
            JSON_OBJECT('name','경남 밀양','category','밀양아리랑/얼음골',
                'status','신청접수중','period','04.01~08.31','currency','제로페이',
                'contact','055-359-5785','maxAmount',100000),
            JSON_OBJECT('name','경남 합천','category','해인사/드라마세트장',
                'status','신청접수중','period','04.01~06.30','currency','제로페이',
                'contact','1660-3067','maxAmount',100000),
            JSON_OBJECT('name','전남 고흥','category','우주센터/해양',
                'status','신청접수중','period','04.01~08.31','currency','chak',
                'contact','061-830-5305','maxAmount',100000),
            JSON_OBJECT('name','강원 거창','category','수승대/가조온천',
                'status','준비중','period','04.13~08.30','currency','제로페이',
                'contact','1660-3042','maxAmount',100000),
            JSON_OBJECT('name','강원 영월','category','자연/래프팅',
                'status','준비중','period','04.10~08.31','currency','코나아이',
                'contact','1577-0545','maxAmount',100000),
            JSON_OBJECT('name','충북 제천','category','청풍호/약초',
                'status','준비중','period','04.07~08.31','currency','chak',
                'contact','홈페이지 확인','maxAmount',100000),
            JSON_OBJECT('name','전남 영광','category','굴비/불갑사',
                'status','준비중','period','04.10~08.31','currency','그리고/코나아이',
                'contact','061-350-6796','maxAmount',100000),
            JSON_OBJECT('name','전북 고창','category','청보리/체험',
                'status','준비중','period','04.18~08.31','currency','고향사랑페이',
                'contact','063-560-2946','maxAmount',100000),
            JSON_OBJECT('name','전남 해남','category','땅끝마을/공룡',
                'status','준비중','period','04.30~08.31','currency','chak',
                'contact','061-535-6290','maxAmount',100000),
            JSON_OBJECT('name','강원 평창','category','자연/스키',
                'status','준비중','period','5월~08.30 예정','currency','chak',
                'contact','033-333-5557','maxAmount',100000),
            JSON_OBJECT('name','전남 완도','category','청산도/해조류',
                'status','준비중','period','05.01~08.31','currency','chak',
                'contact','061-550-5413','maxAmount',100000),
            JSON_OBJECT('name','전남 강진','category','청자/다산',
                'status','준비중','period','06.01~08.31','currency','chak',
                'contact','061-433-3349','maxAmount',100000),
            JSON_OBJECT('name','강원 횡성','category','한우/자연',
                'status','준비중','period','일정 확인 필요','currency','제로페이',
                'contact','033-340-5975','maxAmount',100000),
            JSON_OBJECT('name','경남 하동','category','녹차/섬진강',
                'status','마감','period','-','currency','제로페이',
                'contact','070-5217-2408','maxAmount',100000),
            JSON_OBJECT('name','경남 남해','category','독일마을/보리암',
                'status','마감','period','-','currency','비플페이',
                'contact','055-860-8606','maxAmount',100000),
            JSON_OBJECT('name','전남 영암','category','왕인박사/농촌',
                'status','마감','period','-','currency','월출페이',
                'contact','061-470-2644','maxAmount',100000)
        ),
        'qna', JSON_ARRAY(
            JSON_OBJECT('q','거주지 인접 지역은 신청할 수 없나요?',
                'a','네. 주민등록 주소지 기준으로 인접한 지역(동일 시·군·구 또는 인접 시·군)은 신청이 제한됩니다. 지역마다 세부 기준이 다르니 각 지역 신청 페이지에서 확인하세요.'),
            JSON_OBJECT('q','영수증은 어떤 것이 인정되나요?',
                'a','지역화폐 결제 영수증이 우선 인정됩니다. 숙박의 경우 카드·현금 영수증도 가능합니다. 지정관광지 방문 인증사진(지역마다 1~2개소)도 반드시 제출해야 합니다.'),
            JSON_OBJECT('q','지역화폐는 어디에서 사용하나요?',
                'a','환급받은 지역화폐는 해당 지역 가맹 상점과 지역 쇼핑몰에서만 사용 가능합니다. 대형마트·프랜차이즈는 사용 불가한 경우가 많습니다.'),
            JSON_OBJECT('q','예산이 소진되면 어떻게 되나요?',
                'a','전체 예산(약 65억원) 소진 시 신청기간 내라도 예고 없이 마감됩니다. 오픈 즉시 신청하는 것을 강력히 권장합니다.'),
            JSON_OBJECT('q','환급은 얼마나 걸리나요?',
                'a','정산 신청 후 서류 심사 완료 시 영업일 기준 7일 이내 지역화폐로 환급됩니다.')
        ),
        'steps', JSON_ARRAY(
            JSON_OBJECT('step','01','title','사전 신청','desc','여행 출발 1~5일 전(지역마다 상이), 대한민국 구석구석 또는 각 지자체 홈페이지에서 여행계획·구비서류 제출 후 승인 대기.','icon','assignment'),
            JSON_OBJECT('step','02','title','여행 실행','desc','승인 완료 후 해당 인구감소지역 방문. 숙박·식사·체험 비용 결제. 지정관광지 방문 인증사진 촬영 필수.','icon','flight_takeoff'),
            JSON_OBJECT('step','03','title','영수증·사진 제출','desc','여행 후 정해진 기간 내 지출 증빙서류와 관광지 인증사진을 앱/홈페이지에 업로드.','icon','receipt_long'),
            JSON_OBJECT('step','04','title','지역화폐 환급','desc','심사 완료 후 영업일 7일 이내 지역화폐로 환급. 해당 지역 가맹점에서 사용 가능.','icon','savings')
        )
    )
WHERE id = 'vacation_support';
