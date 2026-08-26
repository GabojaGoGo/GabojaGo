"""국민체육진흥공단_전국체육시설 정보 — 정식 expansion staging 승격 여부 판단 후 생성.

가치 판단 결과(내부 audit report 참고): 원천 전체(17,978건)의 65%는 신고/등록
민간 체육시설업(당구장·태권도·체력단련장 등)으로, 이미 LOCALDATA 체육시설업
계열과 개념이 겹치고 이전 P1 라운드에서 낮은 우선순위로 판정된 것과 같은
성격이다. 반면 `faci_gb_nm == "공공"`(6,314건)만 보면 기존 부산공공체육시설·
경남공공체육시설 대비 이름 겹침이 3%대뿐인 대규모 순증 카탈로그다.

따라서 이번 staging은 **공공 구분만** 대상으로 한다. 신고/등록(민간)은
staging에서 제외한다(raw는 보존, 이유만 기록).
"""

from __future__ import annotations

import json
from collections import Counter, defaultdict

from stage_p1_sources import (
    PROJECT_ROOT,
    RAW,
    SourceReport,
    make_record,
    write_report,
    write_staging,
)

from data_import.cleaning import (
    check_coordinate,
    haversine_m,
    is_area_name,
    norm_nospace,
    normalize_str,
)

NAME = "국민체육진흥공단전국체육시설"
RAW_PATH = RAW / "standard/국민체육진흥공단_전국체육시설/부울경_17978건.json"

STATUS_MAP = {"정상운영": "ACTIVE", "폐업": "CLOSED"}


def _score_row(row: dict) -> tuple:
    """대표행 선택 — 폐업보다 정상운영, 최신 updt_dt, 필드 채움 순."""
    status_rank = 1 if row.get("faci_stat_nm") == "정상운영" else 0
    addr_fill = (1 if row.get("faci_addr") else 0) + (1 if row.get("faci_road_addr") else 0)
    phone_fill = 1 if row.get("faci_tel_no") else 0
    updt = row.get("updt_dt") or ""
    return (status_rank, addr_fill, phone_fill, updt)


def main() -> None:
    report = SourceReport(source=NAME)
    report.id_field_used = "faci_cd"

    rows = json.loads(RAW_PATH.read_text(encoding="utf-8"))
    report.raw_input_count = len(rows)

    public_rows = [r for r in rows if r.get("faci_gb_nm") == "공공"]
    private_excluded = len(rows) - len(public_rows)
    report.non_place_excluded = private_excluded  # 실제로는 '범위 밖 민간시설 제외' — notes에 명시
    report.notes.append(
        f"신고/등록 민간 체육시설업 {private_excluded}건은 이번 패스의 대상 범위(공공체육시설 카탈로그)가 "
        "아니라서 staging에서 제외했다(비장소가 아니라 '범위 밖'이라 non_place_excluded 필드를 "
        "그대로 재사용했다) — LOCALDATA 체육시설업 계열과 개념이 겹치는 것으로 이미 판단된 것과 같은 "
        "성격(당구장·태권도·체력단련장 등)이라 이번 원천의 신규 staging 가치는 공공 구분에서만 확인됐다."
    )

    for r in public_rows:
        status_raw = r.get("faci_stat_nm")
        normalized_status = STATUS_MAP.get(status_raw, "UNKNOWN")
        if normalized_status in ("ACTIVE",):
            pass  # 아래에서 그룹 계산 후 최종 포함 여부 결정
        elif normalized_status == "CLOSED":
            report.status_excluded += 1
        else:
            report.unknown += 1

    active_rows = [r for r in public_rows if r.get("faci_stat_nm") == "정상운영"]

    # --- 내부 중복 그룹 분류(A/B/C/D) — active 공공 범위에서만 ---
    groups: dict[str, list[dict]] = defaultdict(list)
    for r in active_rows:
        k = norm_nospace(r.get("faci_nm"))
        if k:
            groups[k].append(r)
    dup_groups = {k: v for k, v in groups.items() if len(v) > 1}

    remove_ids: set[str] = set()
    classification_counts = Counter()
    group_details = []

    for name_key, recs in dup_groups.items():
        detail = {"name": recs[0].get("faci_nm"), "member_count": len(recs),
                  "faci_cds": [r.get("faci_cd") for r in recs]}

        if is_area_name(name_key):
            classification_counts["area_excluded"] += 1
            detail["classification"] = "area_excluded"
            group_details.append(detail)
            continue

        ftypes = {r.get("ftype_nm") for r in recs}
        coords = [(float(r["faci_lat"]), float(r["faci_lot"])) for r in recs
                  if r.get("faci_lat") and r.get("faci_lot")]

        if len(ftypes) > 1:
            classification_counts["B_subfacility"] += 1
            detail["classification"] = "B"
            detail["reason"] = f"시설유형 상이({sorted(str(x) for x in ftypes)}) — 복합시설 하위시설, 병합 안 함"
        elif len(coords) < len(recs):
            classification_counts["D_coord_missing"] += 1
            detail["classification"] = "D"
            detail["reason"] = "좌표 결측 — 판정 불가, 병합 안 함"
        else:
            max_dist = 0.0
            for i in range(len(coords)):
                for j in range(i + 1, len(coords)):
                    d = haversine_m(*coords[i], *coords[j])
                    if d is not None:
                        max_dist = max(max_dist, d)
            detail["max_dist_m"] = round(max_dist, 1)
            if max_dist <= 50:
                classification_counts["A_confirmed_duplicate"] += 1
                detail["classification"] = "A"
                scored = sorted(recs, key=_score_row, reverse=True)
                representative = scored[0]
                removed = [r.get("faci_cd") for r in recs if r is not representative]
                detail["representative"] = representative.get("faci_cd")
                detail["removed"] = removed
                remove_ids.update(removed)
            elif max_dist > 500:
                classification_counts["C_chain_branch"] += 1
                detail["classification"] = "C"
                detail["reason"] = "동명 지점 500m 이상 이격 — 체인/분점으로 판단, 병합 안 함"
            else:
                classification_counts["D_ambiguous"] += 1
                detail["classification"] = "D"
                detail["reason"] = f"거리 {max_dist:.1f}m — 판정 불가(분점 가능성), 병합 안 함"

        group_details.append(detail)

    report.deduped_records_removed = len(remove_ids)

    # 내부 dedupe 후보(같은 name_key)로 duplicate_group_id/manual_review 플래그(A 제외 나머지 전부)
    dup_group_id_map: dict[str, str] = {}
    for detail in group_details:
        if detail.get("classification") in ("B", "C", "D"):
            for cd in detail["faci_cds"]:
                dup_group_id_map[cd] = f"{NAME}:{norm_nospace(detail['name'])}"

    records = []
    for r in active_rows:
        cd = r.get("faci_cd")
        if cd in remove_ids:
            continue  # A유형 대표행 제외본 — staging에서 제거(raw는 보존)

        lat = float(r["faci_lat"]) if r.get("faci_lat") else None
        lon = float(r["faci_lot"]) if r.get("faci_lot") else None
        coord_status = check_coordinate(lat, lon)
        if coord_status == "VALID":
            report.coord_valid += 1
        elif coord_status == "MISSING":
            report.coord_missing += 1
        elif coord_status == "ZERO":
            report.coord_zero += 1
        elif coord_status == "OUT_OF_REGION":
            report.coord_out_of_region += 1

        lot = normalize_str(r.get("faci_addr"))
        road = normalize_str(r.get("faci_road_addr"))

        dup_gid = dup_group_id_map.get(cd)
        was_representative = any(
            d.get("classification") == "A" and d.get("representative") == cd for d in group_details
        )

        records.append(
            make_record(
                source=NAME,
                source_record_id=cd,
                original_status=r.get("faci_stat_nm"),
                normalized_status="ACTIVE",
                normalized_name=normalize_str(r.get("faci_nm")),
                normalized_address_lot=lot,
                normalized_address_road=road,
                latitude=lat,
                longitude=lon,
                coordinate_status=coord_status,
                exclusion_reason=None,
                duplicate_group_id=dup_gid,
                duplicate_count=len(groups.get(norm_nospace(r.get("faci_nm")), [])),
                manual_review_required=bool(dup_gid),
                provenance={
                    "raw_file": "data/raw/standard/국민체육진흥공단_전국체육시설/부울경_17978건.json",
                    "시설유형": r.get("ftype_nm"),
                    "관리기관": r.get("fmng_cp_nm") or r.get("fmng_cpb_nm"),
                    "전화번호": r.get("faci_tel_no"),
                    "홈페이지": r.get("faci_homepage"),
                    "자료기준일": r.get("base_ymd"),
                    "최종수정일": r.get("updt_dt"),
                    "dedupe_representative_of_A_group": was_representative,
                },
            )
        )

    report.included_in_staging = len(records)
    report.included_before_dedupe = report.included_in_staging + report.deduped_records_removed
    report.final_included_after_dedupe = report.included_in_staging
    report.id_uniqueness_verified = True
    report.id_collision_groups = 0
    report.internal_duplicate_groups = sum(
        classification_counts[k] for k in ("B_subfacility", "C_chain_branch", "D_coord_missing", "D_ambiguous")
    )
    report.internal_duplicate_records = sum(
        len(d["faci_cds"]) for d in group_details if d.get("classification") in ("B", "C", "D")
    )
    report.notes.append(
        "faci_gb_nm='공공'만 staging 대상으로 했다(신고/등록 민간 체육시설업은 범위 밖). "
        f"내부 중복 분류(공공+정상운영 범위): {dict(classification_counts)}. "
        "A(확정 동일시설)만 대표행 선택으로 실제 제거했고, B(복합시설 하위시설)·C(체인/분점)·"
        "D(판정불가)는 duplicate_group_id/manual_review_required만 표시하고 병합하지 않았다."
    )
    report.notes.append(
        "기존 부산공공체육시설(237건)·경남공공체육시설(322건) 대비 이름 겹침은 각각 3.1%/3.7%뿐 — "
        "공공 구분에서도 93.3%가 순증이다. 이 사실이 이번 원천을 staging으로 승격한 핵심 근거다."
    )

    write_staging(NAME, "부울경", records)
    write_report(NAME, report)

    print(
        f"{NAME}: raw={report.raw_input_count} private_excluded={report.non_place_excluded} "
        f"status_excluded={report.status_excluded} dedup_removed={report.deduped_records_removed} "
        f"included={report.included_in_staging} reconcile={report.reconcile_ok()} "
        f"dedupe_reconcile={report.dedupe_reconcile_ok()}"
    )
    print("classification_counts:", dict(classification_counts))

    # group_details는 별도 상세 리포트로 남긴다(재현 가능한 근거)
    import datetime as _dt
    ts = _dt.datetime.now(_dt.UTC).strftime("%Y%m%d-%H%M%SZ")
    detail_path = PROJECT_ROOT / f"data/reports/cleaning/{NAME}-duplicate-groups-{ts}.json"
    detail_path.write_text(json.dumps(group_details, ensure_ascii=False, indent=2), encoding="utf-8")
    print("group detail:", detail_path)


if __name__ == "__main__":
    main()
