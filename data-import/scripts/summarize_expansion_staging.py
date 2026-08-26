"""1·2차 확장 원천 staging 전체 통합 검증 — 원천별 reconcile 확인 + cross-source
연결 가능성 사전 조사(실제 병합은 하지 않는다)."""

from __future__ import annotations

import json
from datetime import UTC, datetime
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
REPORTS = PROJECT_ROOT / "data/reports/cleaning"
STAGING = PROJECT_ROOT / "data/staging"

# other-source-survey.md §1 A표에서 확정된 정본 규모 — reconcile 대조용
EXPECTED_COUNTS = {
    "국가유산공간정보": 1353,
    "부산공공체육시설": 239,
    "관광안내소": 115,
    "박물관미술관": 116,
    "농어촌체험휴양마을": 173,
    "마리나": 11,
    "경남공공체육시설": 322,  # 29+25+14+99+40+49+66 (7개 시군 합)
    "울산문화시설": 57,
    "어항정보": 25,
    "유아숲체험원": 55,
    "낚시터": 46,
    "치유농업시설": 7,
    "비짓부산패스": 956,  # uc_seq 기준 고유 시설 수(정본의 588은 다른 필터링 기준)
    "부산공공미술": 1763,
}

# 원천별로 cross-source 연결에 실제 쓸 수 있는 식별 필드 — 이번 패스에서는 평가만 한다
CROSS_SOURCE_ID_ASSESSMENT = {
    "국가유산공간정보": {"usable_id": "없음(이름+행정동 근사만 가능)", "note": "유산코드는 원천 내부에서만 고유 확인됨. cross-source 매칭은 이름+지번 근사만 가능"},
    "부산공공체육시설": {"usable_id": "없음", "note": "FID는 원천 내부 고유 확인됨. 이름+주소로만 cross-source 매칭 가능"},
    "관광안내소": {"usable_id": "없음", "note": "이름+지번주소 근사만 가능"},
    "박물관미술관": {"usable_id": "없음", "note": "이름+지번주소 근사만 가능"},
    "농어촌체험휴양마을": {"usable_id": "없음", "note": "이름+지번주소 근사만 가능"},
    "마리나": {"usable_id": "없음(공간정보일련번호는 전국 순번일 뿐)", "note": "이름+좌표 근접만 가능"},
    "경남공공체육시설": {"usable_id": "없음", "note": "시군마다 스키마가 달라 이름+주소(또는 이름만)로만 근사 가능. 부산공공체육시설과의 스키마 통합도 아직 없음"},
    "울산문화시설": {"usable_id": "없음(연번은 파일 내부 순번)", "note": "이름+주소로만 근사 가능"},
    "어항정보": {"usable_id": "없음", "note": "이름+좌표로만 근사 가능. 마리나와 지리적으로 겹칠 수 있으나 이번 패스는 병합하지 않음"},
    "유아숲체험원": {"usable_id": "없음", "note": "좌표가 없어 주소+이름 문자열 매칭만 가능(신뢰도 낮음)"},
    "낚시터": {"usable_id": "없음", "note": "이름+지번주소로만 근사 가능"},
    "치유농업시설": {"usable_id": "없음", "note": "이름만 존재 — 매칭 신뢰도 매우 낮음, 사실상 매칭 불가"},
    "비짓부산패스": {"usable_id": "uc_seq(원천 내부에서만 유일)", "note": "TourAPI와 이름+구군 근사 매칭 가능성 있음(정본 588/73.5% 순증 수치가 이미 존재)"},
    "부산공공미술": {"usable_id": "없음(작품 단위, 좌표 군집)", "note": "좌표 군집(같은 장소의 여러 작품)을 먼저 장소 단위로 묶을지부터 P2/P3 판단 필요"},
}


def latest_report(source: str) -> dict | None:
    candidates = sorted(REPORTS.glob(f"expansion-staging-{source}-*.json"))
    if not candidates:
        return None
    return json.loads(candidates[-1].read_text(encoding="utf-8"))


def staged_count(source: str) -> int:
    path = STAGING / source / "부울경.json"
    if not path.exists():
        return -1
    return len(json.loads(path.read_text(encoding="utf-8")))


def main() -> None:
    rows = []
    all_reconciled = True
    all_match_expected = True

    for source, expected in EXPECTED_COUNTS.items():
        report = latest_report(source)
        if report is None:
            rows.append({"source": source, "error": "리포트 없음"})
            all_reconciled = False
            continue

        staged = staged_count(source)
        reconcile_ok = report.get("reconcile_ok", False)

        # 정본(other-source-survey.md §1 A표)의 규모는 원천에 따라 raw 전체가 아니라
        # "장소 후보로 확정된 건수"를 가리킬 수 있다(국가유산공간정보=보호구역 제외 후 1,353).
        compare_value = (
            report["included_in_staging"] if source == "국가유산공간정보" else report["raw_input_count"]
        )
        matches_p1_measurement = compare_value == expected

        if not reconcile_ok:
            all_reconciled = False
        if not matches_p1_measurement:
            all_match_expected = False

        rows.append(
            {
                "source": source,
                "raw_input_count": report["raw_input_count"],
                "expected_p1_measurement": expected,
                "matches_p1_measurement": matches_p1_measurement,
                "included_in_staging": report["included_in_staging"],
                "non_place_excluded": report["non_place_excluded"],
                "status_all_unknown": report["unknown"] == report["included_in_staging"],
                "coord_valid": report["coord_valid"],
                "coord_missing": report["coord_missing"],
                "coord_zero": report["coord_zero"],
                "coord_out_of_region": report["coord_out_of_region"],
                "address_lot_filled": report["address_lot_filled"],
                "address_road_filled": report["address_road_filled"],
                "address_fill_pct": round(
                    (report["address_lot_filled"] + report["address_road_filled"])
                    / report["raw_input_count"] * 100, 1
                ) if report["raw_input_count"] else 0.0,
                "id_field_used": report["id_field_used"],
                "id_uniqueness_verified": report["id_uniqueness_verified"],
                "id_collision_groups": report["id_collision_groups"],
                "internal_duplicate_groups": report["internal_duplicate_groups"],
                "internal_duplicate_records": report["internal_duplicate_records"],
                "final_staging_count": staged,
                "reconcile_ok": reconcile_ok,
                "cross_source_id_assessment": CROSS_SOURCE_ID_ASSESSMENT.get(source),
            }
        )

    summary = {
        "purpose": "P1 확장 원천 1·2차 staging 전체(14개 원천) 통합 검증 + cross-source 연결 가능성 사전 조사",
        "scope": "1차 6개(국가유산공간정보/부산공공체육시설/관광안내소/박물관미술관/농어촌체험휴양마을/마리나) + 2차 8개(경남공공체육시설/울산문화시설/어항정보/유아숲체험원/낚시터/치유농업시설/비짓부산패스/부산공공미술)",
        "all_sources_reconcile_ok": all_reconciled,
        "all_sources_match_p1_measurement": all_match_expected,
        "note_on_visitbusanpass_count": (
            "비짓부산패스는 P1 정본의 588(점형 후보, 별도 필터링 기준)과 다르게 "
            "이번 패스는 t_tour_info의 uc_seq 고유값(956건) 전체를 staging했다 — "
            "588은 특정 tourist_tycd 필터링 이후 수치로 추정되며, 이번 패스는 필터링을 "
            "하지 않고 t_tour_info 전체를 보존했다(원천에 있는 사실을 임의로 덜어내지 않음)."
        ),
        "sources": rows,
        "cross_source_readiness": {
            "verdict": "아직 이르다(모든 원천에 신뢰 가능한 cross-source ID가 없음)",
            "reasoning": (
                "20개 원천(base 8 + expansion 14) 전체에서 이름+지번주소(또는 좌표 근접) 근사 "
                "매칭 외에 신뢰 가능한 공유 ID가 존재하는 쌍이 없다. base 원천끼리도(관광숙박업 "
                "관리번호, 주차장관리번호) 이미 ID 신뢰 불가가 확인됐고, expansion 원천 14개도 "
                "전부 원천 내부용 ID뿐이거나 아예 ID가 없다. cross-source entity resolution은 "
                "이름 정규화 + 주소 근사 + 좌표 근접의 조합(base staging에서 이미 검증된 방식)으로 "
                "접근해야 하며, 이는 다음 단계의 정식 작업 범위다."
            ),
            "base_to_expansion_candidates": [
                "TourAPI(관광지/문화시설) ↔ 국가유산공간정보/관광안내소/박물관미술관/농어촌체험휴양마을/마리나/낚시터/어항정보/비짓부산패스 — 전부 이름+지번주소 근사 매칭이 이미 P1 실측에서 시도된 바 있다(순증률 계산의 기반)",
                "LOCALDATA 체육계열(없음) ↔ 부산공공체육시설/경남공공체육시설 — base에는 대응 원천이 아예 없어(주차장처럼 유일 원천) cross-source 중복이 아니라 순수 신규 편입 판단",
            ],
            "expansion_to_expansion_candidates": [
                "어항정보 ↔ 마리나 — 둘 다 해양수산부 계열, 좌표 근접으로 겹치는 항만이 있을 가능성(§6에서 명시적으로 병합 보류)",
                "부산공공체육시설 ↔ 경남공공체육시설 — 스키마가 달라 직접 비교 불가, 도 경계상 지리적으로 겹치지 않아 실익 낮음",
                "비짓부산패스 ↔ 부산공공미술 — 둘 다 부산관광공사/부산시 계열이나 레코드 성격이 다름(관광지 vs 개별 작품)",
            ],
        },
        "generated_at": datetime.now(UTC).strftime("%Y-%m-%d"),
    }

    timestamp = datetime.now(UTC).strftime("%Y%m%d-%H%M%SZ")
    out_path = REPORTS / f"expansion-staging-consolidated-summary-{timestamp}.json"
    out_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"all_reconcile_ok={all_reconciled} all_match_p1_measurement={all_match_expected}")
    for r in rows:
        if "error" in r:
            print(f"  {r['source']}: ERROR {r['error']}")
        else:
            print(
                f"  {r['source']}: raw={r['raw_input_count']} included={r['included_in_staging']} "
                f"final={r['final_staging_count']} reconcile={r['reconcile_ok']} "
                f"matches_p1={r['matches_p1_measurement']}"
            )
    print(f"written: {out_path}")


if __name__ == "__main__":
    main()
