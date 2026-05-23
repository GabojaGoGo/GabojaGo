-- V9: 지역사랑 휴가지원 — 지역별 오픈 일정 추가
UPDATE benefits
SET detail_json = JSON_SET(
    detail_json,
    '$.openSchedule', JSON_ARRAY(
        JSON_OBJECT('period','3월 4주',  'regions','밀양, 남해'),
        JSON_OBJECT('period','4월 1주',  'regions','합천, 하동, 고흥, 영암'),
        JSON_OBJECT('period','4월 2주',  'regions','영광, 제천, 영월'),
        JSON_OBJECT('period','4월 3주',  'regions','거창, 해남, 고창'),
        JSON_OBJECT('period','5월',      'regions','평창, 횡성, 완도'),
        JSON_OBJECT('period','6월',      'regions','강진')
    )
)
WHERE id = 'vacation_support';
