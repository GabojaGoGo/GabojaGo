package com.tripmate.backend.benefit.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * VacationSupportCrawler
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *
 * 대한민국 구석구석 "지역사랑 휴가지원" 페이지를 크롤링하는 컴포넌트
 *
 * 역할:
 * - visitkorea 홈페이지의 지역별 신청 상태를 자동으로 추출
 * - 지역명 → 상태("신청접수중" | "준비중" | "마감") 매핑
 *
 * 크롤링 대상:
 * https://korean.visitkorea.or.kr/dgtourcard/tour50.do
 *
 * 크롤링 데이터 흐름:
 * 1. HTML 다운로드
 * 2. href에 'func_go_detail'을 포함하는 모든 <a> 태그 찾기
 * 3. 링크 텍스트에서 지역명과 상태 파싱
 *    예: "밀양신청접수중" → 지역: "밀양", 상태: "신청접수중"
 * 4. 결과를 Map으로 반환
 *
 * 사용 흐름:
 * VacationSupportSyncScheduler.sync() → crawler.crawl() → 결과로 DB 업데이트
 */
@Component
@Slf4j
public class VacationSupportCrawler {

    /** 크롤링 대상 URL */
    private static final String URL =
            "https://korean.visitkorea.or.kr/dgtourcard/tour50.do";

    /** 인식 가능한 상태 목록 (우선순위 있음) */
    private static final List<String> STATUSES =
            List.of("신청접수중", "준비중", "마감");

    /** 연결 제한 시간 (15초 초과면 실패 처리) */
    private static final int TIMEOUT_MS = 15_000;

    /**
     * 지역사랑 휴가지원 페이지를 크롤링
     *
     * 처리 과정:
     * 1. Jsoup을 사용하여 visitkorea 페이지 HTML 다운로드
     *    - userAgent 설정: 브라우저처럼 요청 (웹 크롤링 차단 회피)
     *    - timeout 설정: 15초 이상 걸리면 실패
     *
     * 2. CSS 선택자로 링크 추출
     *    - "a[href*='func_go_detail']": href 속성에 'func_go_detail' 포함하는 모든 <a> 태그
     *
     * 3. 각 링크 텍스트에서 지역명과 상태 파싱
     *    - 예: 링크 텍스트 = "밀양신청접수중"
     *    - 상태가 STATUSES에 포함되면 후진으로 제거
     *    - 남은 부분이 지역명
     *    - 예: "밀양신청접수중" - "신청접수중" = "밀양"
     *
     * 4. 결과를 Map으로 반환
     *    - Key: 지역 단축명 (예: "밀양", "부산", "서울" 등)
     *    - Value: 상태 (예: "신청접수중", "마감")
     *
     * 예시 결과:
     * {
     *   "밀양": "신청접수중",
     *   "부산": "마감",
     *   "서울": "준비중",
     *   ...
     * }
     *
     * 에러 처리:
     * - 크롤링 실패 시: 로그만 남기고 빈 Map 반환
     * - VacationSupportSyncScheduler는 빈 결과를 감지하고 DB 업데이트 스킵
     * - 기존 DB 데이터 유지 (크롤링 실패해도 앱 기능 안 깨짐)
     *
     * @return 지역명 → 상태 매핑 맵
     *         (크롤링 실패 시 빈 Map)
     */
    public Map<String, String> crawl() {
        Map<String, String> result = new HashMap<>();
        try {
            // 1. visitkorea 페이지 HTML 다운로드
            Document doc = Jsoup.connect(URL)
                    // 브라우저처럼 보이기 위한 User-Agent 설정
                    // (서버가 봇 차단하면 다운로드 실패 방지)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/124.0 Safari/537.36")
                    .timeout(TIMEOUT_MS)  // 15초 초과면 Exception
                    .get();

            // 2. 링크 선택 및 파싱
            // href에 'func_go_detail'이 포함된 모든 <a> 태그
            Elements links = doc.select("a[href*='func_go_detail']");
            for (Element link : links) {
                // 링크 텍스트: "밀양신청접수중", "부산마감" 등
                String text = link.text().trim();

                // 3. 상태 감지 및 제거
                for (String status : STATUSES) {
                    if (text.endsWith(status)) {
                        // "밀양신청접수중" → text.substring(0, "밀양".length) → "밀양"
                        String region = text.substring(0, text.length() - status.length()).trim();
                        if (!region.isEmpty()) {
                            result.put(region, status);
                        }
                        // 한 링크에서 상태 하나만 매칭 (break)
                        break;
                    }
                }
            }
            log.info("[VacationSupportCrawler] 크롤링 완료: {}개 지역 파싱", result.size());

        } catch (Exception e) {
            // 네트워크 오류, 타임아웃, HTML 파싱 오류 등
            // 로그만 남기고 빈 Map 반환 → DB 업데이트 스킵
            log.warn("[VacationSupportCrawler] 크롤링 실패 — DB 기존 데이터 유지: {}", e.getMessage());
        }

        return result;
    }
}
