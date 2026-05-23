-- V6: 봄 숙박세일페스타 — OTA → 소비자 친화 표현, 알림 설명 업데이트

UPDATE benefits
SET
    description = '한국관광공사·문화체육관광부 주관. 비수도권 전국 숙박시설 정액 할인 쿠폰 제공. 야놀자·여기어때·인터파크 등 17개 숙박 예약 앱에서 매일 오전 10시 선착순 발급.',
    detail_json  = JSON_SET(
        detail_json,
        '$.highlights[1].desc',
        '야놀자·여기어때·인터파크·G마켓 등 17개 숙박 예약 앱에서 1인 1매 선착순 발급. 유효기간 21시간.',
        '$.highlights[3].desc',
        '발급 10분 전, 5분 전, 발급 시각 이렇게 3번 알림이 발송됩니다. 아래 [알림 설정] 버튼을 눌러 미리 등록해두세요.'
    )
WHERE id = 'sale_festa';
