-- V7: 지역사랑 휴가지원 신청 URL 수정
UPDATE benefits
SET apply_url = 'https://korean.visitkorea.or.kr/dgtourcard/tour50.do'
WHERE id = 'vacation_support';
