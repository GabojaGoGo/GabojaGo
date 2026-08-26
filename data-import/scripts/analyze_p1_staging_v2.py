from __future__ import annotations

import csv
import json
import re
from collections import Counter, defaultdict
from datetime import UTC, date, datetime
from pathlib import Path

from data_import.cleaning import (
    STATUS_MAP,
    epsg5174_to_wgs84,
    haversine_m,
    lot_addr_key,
    norm_nospace,
    normalize_str,
    to_float,
)

PROJECT_ROOT = Path(__file__).resolve().parent.parent
RAW = PROJECT_ROOT / "data/raw"
REPORTS = PROJECT_ROOT / "data/reports/cleaning"


def norm(v: str | None) -> str:
    """이 스크립트의 기존 관례(None 대신 빈 문자열 반환)를 유지하는 래퍼."""
    return normalize_str(v) or ""

REGIONS_TOURLODGE = {
    "부산광역시": "data/raw/standard/숙박업/문화_관광숙박업_부산광역시.csv",
    "울산광역시": "data/raw/standard/숙박업/문화_관광숙박업_울산광역시.csv",
    "경상남도": "data/raw/standard/숙박업/문화_관광숙박업_경상남도.csv",
}

LOCALDATA_REGIONS = {
    "일반음식점": {
        "부산광역시": "data/raw/standard/일반음식점/식품_일반음식점_부산광역시.csv",
        "울산광역시": "data/raw/standard/일반음식점/식품_일반음식점_울산광역시.csv",
        "경상남도": "data/raw/standard/일반음식점/식품_일반음식점_경상남도.csv",
    },
    "휴게음식점": {
        "부산광역시": "data/raw/standard/휴게음식점/식품_휴게음식점_부산광역시.csv",
        "울산광역시": "data/raw/standard/휴게음식점/식품_휴게음식점_울산광역시.csv",
        "경상남도": "data/raw/standard/휴게음식점/식품_휴게음식점_경상남도.csv",
    },
    "제과점": {
        "부산광역시": "data/raw/standard/제과점/식품_제과점영업_부산광역시.csv",
        "울산광역시": "data/raw/standard/제과점/식품_제과점영업_울산광역시.csv",
        "경상남도": "data/raw/standard/제과점/식품_제과점영업_경상남도.csv",
    },
    "숙박업": {
        "부산광역시": "data/raw/standard/숙박업/문화_숙박업_부산광역시.csv",
        "울산광역시": "data/raw/standard/숙박업/문화_숙박업_울산광역시.csv",
        "경상남도": "data/raw/standard/숙박업/문화_숙박업_경상남도.csv",
    },
    "관광숙박업": REGIONS_TOURLODGE,
}

STORE_INFO_REGIONS = {
    "부산": "data/raw/standard/상가정보/소상공인시장진흥공단_상가(상권)정보_부산_202606.csv",
    "울산": "data/raw/standard/상가정보/소상공인시장진흥공단_상가(상권)정보_울산_202606.csv",
    "경남": "data/raw/standard/상가정보/소상공인시장진흥공단_상가(상권)정보_경남_202606.csv",
}

def load_csv(path: str, encoding: str) -> list[dict]:
    with (PROJECT_ROOT / path).open(encoding=encoding, errors="replace", newline="") as f:
        return list(csv.DictReader(f))


def write_json(name: str, data) -> Path:
    REPORTS.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(UTC).strftime("%Y%m%d-%H%M%SZ")
    path = REPORTS / f"{name}-{timestamp}.json"
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


# ---------------------------------------------------------------------------
# 1~2. 관광숙박업 duplicate group 구조 분석 + 대표행 규칙 검증
# ---------------------------------------------------------------------------

def analyze_tourlodge_duplicates():
    groups = defaultdict(list)
    cross_region_ids = defaultdict(set)
    for region, path in REGIONS_TOURLODGE.items():
        rows = load_csv(path, "cp949")
        for row in rows:
            mgmt_id = norm(row.get("관리번호"))
            if not mgmt_id:
                continue
            row["_region"] = region
            # 관리번호는 지역 파일 간에도 공유되는 사례가 있어(관리번호 자체의
            # 신뢰성 문제), 그룹 키는 반드시 (지역, 관리번호)로 지역 내부로 한정한다.
            groups[(region, mgmt_id)].append(row)
            cross_region_ids[mgmt_id].add(region)

    dup_groups = {k: v for k, v in groups.items() if len(v) > 1}
    cross_region_collisions = {k: sorted(v) for k, v in cross_region_ids.items() if len(v) > 1}

    classification_counts = Counter()
    classification_records = Counter()
    group_details = []
    representative_rule_agreement = Counter()
    representative_rule_details = []

    for (region_key, mgmt_id), rows in dup_groups.items():
        names = {norm(r.get("사업장명")) for r in rows}
        lots = {lot_addr_key(r.get("지번주소")) for r in rows}
        coords = []
        for r in rows:
            x, y = to_float(r.get("좌표정보(X)")), to_float(r.get("좌표정보(Y)"))
            lat, lon = epsg5174_to_wgs84(x, y)
            coords.append((lat, lon))
        valid_coords = [c for c in coords if c[0] is not None]
        max_dist = 0
        if len(valid_coords) > 1:
            for i in range(len(valid_coords)):
                for j in range(i + 1, len(valid_coords)):
                    d = haversine_m(*valid_coords[i], *valid_coords[j])
                    if d is not None:
                        max_dist = max(max_dist, d)

        statuses = [norm(r.get("상세영업상태명")) for r in rows]
        auth_dates = sorted({norm(r.get("인허가일자")) for r in rows if norm(r.get("인허가일자"))})

        same_name = len(names) == 1
        same_addr = len(lots) == 1
        close_coords = max_dist < 100  # 100m 이내면 사실상 동일 부지로 본다

        if same_name and (same_addr or close_coords):
            if len(set(statuses)) > 1 or len(auth_dates) > 1:
                classification = "A"  # 동일 시설의 상태 이력(이름/주소 동일, 상태·인허가일 변화)
            else:
                classification = "B"  # 동일 시설의 갱신/재등록(이름/주소 동일, 상태도 동일하나 레코드가 나뉨)
        elif not same_name and not same_addr and max_dist > 500:
            classification = "C"  # 실제 서로 다른 시설(이름·주소·좌표 모두 다름)
        else:
            classification = "D"  # 판정 불가(부분 일치, 좌표 결측 등)

        classification_counts[classification] += 1
        classification_records[classification] += len(rows)

        group_details.append(
            {
                "mgmt_id": mgmt_id,
                "region": rows[0]["_region"],
                "record_count": len(rows),
                "names": sorted(names),
                "lot_addr_keys": sorted(lots),
                "max_coord_distance_m": round(max_dist, 1) if valid_coords else None,
                "statuses": statuses,
                "auth_dates": auth_dates,
                "classification": classification,
            }
        )

        # 대표행 규칙 검증은 A/B 유형에서만
        if classification in ("A", "B"):
            def parse_date(s):
                s = norm(s)
                return s if re.match(r"^\d{8}$", s) else ""

            by_latest_auth = max(rows, key=lambda r: parse_date(r.get("인허가일자")) or "0")
            active_rows = [r for r in rows if norm(r.get("상세영업상태명")) == "영업중"]
            by_active = active_rows[0] if len(active_rows) == 1 else None
            by_fill = max(
                rows,
                key=lambda r: sum(
                    1
                    for f in ("지번주소", "도로명주소", "전화번호", "객실수")
                    if norm(r.get(f))
                ),
            )

            candidates = {
                "latest_auth_date": id(by_latest_auth),
                "active_status": id(by_active) if by_active else None,
                "best_field_fill": id(by_fill),
            }
            distinct_picks = {v for v in candidates.values() if v is not None}
            agree = len(distinct_picks) <= 1
            representative_rule_agreement["일치" if agree else "불일치"] += 1
            representative_rule_details.append(
                {
                    "mgmt_id": mgmt_id,
                    "classification": classification,
                    "record_count": len(rows),
                    "rules_agree": agree,
                    "latest_auth_date_name": norm(by_latest_auth.get("사업장명")),
                    "active_status_name": norm(by_active.get("사업장명")) if by_active else None,
                    "best_field_fill_name": norm(by_fill.get("사업장명")),
                }
            )

    result = {
        "purpose": "관광숙박업 duplicate group 전수 구조 분석(지역 내부로 한정) + 대표행 규칙 검증",
        "data_quality_finding_cross_region_id_collision": {
            "description": (
                "관리번호가 서로 다른 지역(시도) 파일 간에도 중복 등장하는 사례를 발견했다. "
                "같은 관리번호가 부산·울산·경남 파일에 동시에 나타나면 서로 다른 시설일 수밖에 "
                "없으므로(지역 파일이 시도 단위로 분리 제공됨), 이 필드는 지역을 넘어선 전역 "
                "고유 ID로 신뢰할 수 없다. 그룹 분석은 이 문제를 피하기 위해 (지역, 관리번호) "
                "조합으로 범위를 한정했다."
            ),
            "cross_region_colliding_id_count": len(cross_region_collisions),
            "sample": dict(list(cross_region_collisions.items())[:10]),
        },
        "total_groups": len(dup_groups),
        "total_records_in_groups": sum(len(v) for v in dup_groups.values()),
        "classification_group_counts": dict(classification_counts),
        "classification_record_counts": dict(classification_records),
        "classification_legend": {
            "A": "동일 시설의 상태 이력(이름·주소/좌표 동일, 상태 또는 인허가일자가 다른 레코드가 누적)",
            "B": "동일 시설의 갱신/재등록(이름·주소/좌표 동일, 상태·인허가일도 동일하나 레코드가 나뉨)",
            "C": "실제 서로 다른 시설(이름·주소 모두 다르고 좌표도 500m 이상 떨어짐)",
            "D": "판정 불가(부분 일치, 좌표 결측 등으로 자동 분류 불가)",
        },
        "representative_rule_verification": {
            "target_groups": "A/B 유형만",
            "agreement_counts": dict(representative_rule_agreement),
            "conclusion": None,  # 아래에서 채움
            "sample_details": representative_rule_details[:20],
        },
        "group_details": group_details,
    }

    total_ab = sum(representative_rule_agreement.values())
    agree_rate = (
        representative_rule_agreement["일치"] / total_ab if total_ab else 0
    )
    if total_ab == 0:
        conclusion = "A/B 유형 그룹이 없어 검증 불가"
    elif agree_rate >= 0.95:
        conclusion = "자동 대표행 선정 가능"
    elif agree_rate >= 0.7:
        conclusion = "조건부 가능(불일치 그룹은 수동 검토)"
    else:
        conclusion = "수동 검토 필요"
    result["representative_rule_verification"]["agreement_rate"] = round(agree_rate, 3)
    result["representative_rule_verification"]["conclusion"] = conclusion

    write_json("tourlodge-duplicate-group-analysis", result)
    print(
        f"관광숙박업 dup groups={len(dup_groups)} records={sum(len(v) for v in dup_groups.values())} "
        f"classification={dict(classification_counts)} rule_conclusion={conclusion}"
    )
    return result


# ---------------------------------------------------------------------------
# 3~4. 상가정보 ↔ LOCALDATA 연결성 + 상태 충돌 확인
# ---------------------------------------------------------------------------

def build_localdata_index():
    """정규화 (이름-공백제거, 지번주소-절단) 키 → 매칭 후보 목록.

    상태 무관하게 전부 색인한다(폐업 레코드와의 충돌도 봐야 하므로).
    """
    by_name_lot: dict[tuple[str, str], list[dict]] = defaultdict(list)
    by_name_road: dict[tuple[str, str], list[dict]] = defaultdict(list)
    coord_index: list[tuple[float, float, dict]] = []

    addr_business_index: dict[str, list[dict]] = defaultdict(list)
    name_addr_index: dict[str, set[str]] = defaultdict(set)

    for source_name, regions in LOCALDATA_REGIONS.items():
        for region, path in regions.items():
            rows = load_csv(path, "cp949")
            for row in rows:
                name = norm(row.get("사업장명"))
                if not name:
                    continue
                status_raw = norm(row.get("상세영업상태명"))
                status = STATUS_MAP.get(status_raw, "UNKNOWN")
                lot = lot_addr_key(row.get("지번주소"))
                road = norm(row.get("도로명주소"))
                x, y = to_float(row.get("좌표정보(X)")), to_float(row.get("좌표정보(Y)"))
                lat, lon = epsg5174_to_wgs84(x, y)
                entry = {
                    "source": source_name,
                    "region": region,
                    "mgmt_id": norm(row.get("관리번호")),
                    "name": name,
                    "lot_addr": norm(row.get("지번주소")),
                    "road_addr": road,
                    "status": status,
                    "status_raw": status_raw,
                    "lat": lat,
                    "lon": lon,
                    "data_update_time": norm(row.get("데이터갱신시점")) or norm(row.get("최종수정시점")),
                    "auth_date": norm(row.get("인허가일자")),
                    "close_date": norm(row.get("폐업일자")),
                    "biz_type": norm(row.get("업태구분명")),
                    "phone": norm(row.get("전화번호")),
                }
                name_ns = norm_nospace(name)
                if lot:
                    by_name_lot[(name_ns, lot)].append(entry)
                    # 동일 주소에 존재하는 다른 사업자(재개업/멀티테넌트 판별용) —
                    # 이름 무관하게 그 지번주소에 걸린 LOCALDATA 레코드를 전부 모은다.
                    addr_business_index[lot].append(entry)
                if road:
                    by_name_road[(name_ns, road)].append(entry)
                if lat is not None:
                    coord_index.append((lat, lon, entry))
                # 동일 상호명이 서로 다른 주소에 걸쳐 등장하는지(체인 가능성) —
                # 지역 내 전체 LOCALDATA 기준으로 본다.
                if lot:
                    name_addr_index[name_ns].add(lot)

    return by_name_lot, by_name_road, coord_index, addr_business_index, name_addr_index


def analyze_storeinfo_linkage():
    """HIGH(이름+지번주소 완전일치)와 MEDIUM(이름+도로명주소)을 명확히 분리해서 센다.

    3차 정제에서 발견된 6,341(2차 리포트) vs 6,265(staging) 불일치의 원인은
    이 함수가 HIGH+MEDIUM CLOSED를 합쳐서 status_conflict_sample_count에
    넣었기 때문이었다(실제 staging에는 HIGH만 반영됨). 이번 패스부터는
    HIGH만을 "공식 conflict 건수"로 쓰고, MEDIUM은 별도 필드로 분리해
    stage_p1_sources.py(HIGH만 처리)와 항상 같은 수를 내도록 한다.
    """
    by_name_lot, by_name_road, _coord_index, addr_business_index, name_addr_index = (
        build_localdata_index()
    )

    high_confidence = 0
    medium_confidence = 0
    unmatched = 0
    high_status_dist = Counter()
    medium_status_dist = Counter()
    high_conflict_samples = []
    match_examples = {"high": [], "medium": []}
    medium_records = []  # 4번(중신뢰 검증)에서 재사용

    total = 0
    for region, path in STORE_INFO_REGIONS.items():
        rows = load_csv(path, "utf-8")
        for row in rows:
            total += 1
            name = norm(row.get("상호명"))
            name_ns = norm_nospace(name)
            lot = lot_addr_key(row.get("지번주소"))
            road = norm(row.get("도로명주소"))
            lat = to_float(row.get("위도"))
            lon = to_float(row.get("경도"))

            candidates = by_name_lot.get((name_ns, lot), [])
            tier = None
            if candidates:
                tier = "high"
            else:
                candidates = by_name_road.get((name_ns, road), [])
                if candidates:
                    tier = "medium"

            if tier is None:
                unmatched += 1
                continue

            match = candidates[0]

            if tier == "high":
                high_confidence += 1
                high_status_dist[match["status"]] += 1
                if match["status"] == "CLOSED" and len(high_conflict_samples) < 30:
                    high_conflict_samples.append(
                        {
                            "store_info_name": name,
                            "store_info_region": region,
                            "store_info_lot_addr": row.get("지번주소"),
                            "localdata_source": match["source"],
                            "localdata_status_raw": match["status_raw"],
                            "localdata_data_update_time": match["data_update_time"],
                        }
                    )
            else:
                medium_confidence += 1
                medium_status_dist[match["status"]] += 1
                medium_records.append(
                    {
                        "store_info_name": name,
                        "store_info_region": region,
                        "store_info_lot": row.get("지번주소"),
                        "store_info_road": road,
                        "store_info_lat": lat,
                        "store_info_lon": lon,
                        "store_info_category": norm(row.get("상권업종대분류명")),
                        "match": match,
                    }
                )

            if len(match_examples[tier]) < 5:
                match_examples[tier].append(
                    {"store_info_name": name, "matched_localdata_name": match["name"],
                     "localdata_status_raw": match["status_raw"], "source": match["source"]}
                )

    high_closed = high_status_dist.get("CLOSED", 0)
    result = {
        "purpose": "상가정보(387,313건) ↔ LOCALDATA 5종 연결성 측정 + 상태 보강 후보 산출(실제 덮어쓰지 않음)",
        "store_info_total": total,
        "high_confidence_matches": high_confidence,
        "medium_confidence_matches": medium_confidence,
        "unmatched": unmatched,
        "match_rate_pct": round((high_confidence + medium_confidence) / total * 100, 2),
        "high_confidence_rate_pct": round(high_confidence / total * 100, 2),
        "high_status_distribution": dict(high_status_dist),
        "medium_status_distribution": dict(medium_status_dist),
        "status_conflict_note": (
            "HIGH 매칭 중 LOCALDATA 상태가 CLOSED인 것은 상가정보(존재 자체가 '살아있다'는 "
            "암묵적 신호)와 충돌 가능성이 있는 사례다. LOCALDATA 폐업 = 현재도 폐업 이라고 "
            "단정하지 않는다 — 상가정보에 개업일 필드가 없어 재개업 여부를 이 데이터만으로는 "
            "구분할 수 없다. MEDIUM 상태분포는 별도(4번 검증용)이며 이 conflict 수치에 섞지 않는다."
        ),
        "status_conflict_sample_count": high_closed,
        "conflict_samples": high_conflict_samples,
        "match_examples": match_examples,
        "enrichment_candidate_note": (
            "status_enrichment_candidate는 제안값일 뿐이며 이번 패스에서 실제 "
            "normalized_status를 덮어쓰지 않았다. HIGH만 staging에 반영한다(MEDIUM 제외)."
        ),
    }

    write_json("storeinfo-localdata-linkage", result)
    print(
        f"상가정보 total={total} high={high_confidence} medium={medium_confidence} "
        f"unmatched={unmatched} match_rate={result['match_rate_pct']}% "
        f"high_closed_conflict={high_closed}"
    )
    return result, addr_business_index, name_addr_index, medium_records


# ---------------------------------------------------------------------------
# 2~3. CLOSED conflict 성격 분석 + 시간 관계 측정
# ---------------------------------------------------------------------------

STORE_INFO_SNAPSHOT_DATE = date(2026, 6, 30)  # 상가정보 파일명(202606) 기준
CATEGORY_COMPAT = {
    "일반음식점": {"음식"},
    "휴게음식점": {"음식"},
    "제과점": {"음식", "소매"},
    "숙박업": {"숙박"},
    "관광숙박업": {"숙박"},
}


def parse_iso_date(s: str | None):
    s = norm(s)
    if not s:
        return None
    try:
        return date.fromisoformat(s[:10])
    except ValueError:
        return None


def analyze_closed_conflict_character(addr_business_index, name_addr_index):
    """HIGH-tier CLOSED conflict 전체(6,265건 규모)를 A~E로 분류하고,
    LOCALDATA 폐업일 ↔ 상가정보 기준일(2026-06) 간격 분포를 측정한다.
    normalized_status는 건드리지 않는다 — 분류 결과만 리포트에 남긴다.
    """
    by_name_lot, _by_name_road, _coord_index, _, _ = build_localdata_index()

    classification_counts = Counter()
    gap_bucket_counts = Counter()
    samples_by_class = defaultdict(list)
    total_checked = 0

    for path in STORE_INFO_REGIONS.values():
        rows = load_csv(path, "utf-8")
        for row in rows:
            name = norm(row.get("상호명"))
            name_ns = norm_nospace(name)
            lot = lot_addr_key(row.get("지번주소"))
            if not lot:
                continue
            candidates = by_name_lot.get((name_ns, lot), [])
            if not candidates:
                continue
            match = candidates[0]
            if match["status"] != "CLOSED":
                continue

            total_checked += 1

            # 시간 관계
            close_dt = parse_iso_date(match["close_date"])
            gap_days = (STORE_INFO_SNAPSHOT_DATE - close_dt).days if close_dt else None
            if gap_days is None:
                gap_bucket = "폐업일자 없음/파싱불가"
            elif gap_days < 0:
                gap_bucket = "폐업일이 상가정보 기준일보다 미래(데이터 이상)"
            elif gap_days <= 90:
                gap_bucket = "최근 폐업(90일 이내)"
            elif gap_days <= 365:
                gap_bucket = "91~365일 전 폐업"
            else:
                gap_bucket = "365일 초과(오래전 폐업)"
            gap_bucket_counts[gap_bucket] += 1

            # 성격 분류: B(재개업 신호) > C(체인 신호) > D(최근 폐업=기준시점차이) > A(오래된 단일 폐업) > E
            other_active_at_addr = [
                e for e in addr_business_index.get(lot, [])
                if norm_nospace(e["name"]) != name_ns and e["status"] == "ACTIVE"
            ]
            distinct_addrs_for_name = name_addr_index.get(name_ns, set())
            is_chain_like = len(distinct_addrs_for_name) >= 3

            if other_active_at_addr:
                classification = "B"  # 폐업 후 동일 위치 신규/재개업 가능성
            elif is_chain_like:
                classification = "C"  # 체인/동일상호로 인한 오연결 가능성
            elif gap_days is not None and gap_days <= 90:
                classification = "D"  # 원천 기준시점 차이 가능성(최근 폐업, 반영 지연)
            elif gap_days is not None and gap_days > 365:
                classification = "A"  # 실제 폐업 레코드일 가능성이 높음(오래전 폐업, 다른 신호 없음)
            else:
                classification = "E"  # 판정 불가

            classification_counts[classification] += 1
            if len(samples_by_class[classification]) < 8:
                samples_by_class[classification].append(
                    {
                        "store_info_name": name,
                        "store_info_lot_addr": row.get("지번주소"),
                        "localdata_source": match["source"],
                        "localdata_close_date": match["close_date"],
                        "localdata_auth_date": match["auth_date"],
                        "gap_days": gap_days,
                        "other_active_names_at_same_addr": [e["name"] for e in other_active_at_addr][:3],
                        "distinct_addr_count_for_name": len(distinct_addrs_for_name),
                    }
                )

    result = {
        "purpose": "HIGH-tier CLOSED conflict의 성격 분류(A~E) + 폐업일-상가정보 기준일 간격 분포",
        "total_closed_conflict_checked": total_checked,
        "classification_counts": dict(classification_counts),
        "classification_legend": {
            "A": "실제 동일 사업장의 폐업 레코드일 가능성이 높음(오래전 폐업, 재개업/체인 신호 없음)",
            "B": "폐업 후 동일 위치에 다른 상호의 ACTIVE 사업자 존재 — 재개업 가능성",
            "C": "동일 상호가 3개 이상 주소에 등장 — 체인이라 오연결됐을 가능성",
            "D": "폐업일이 상가정보 기준일(2026-06)로부터 90일 이내 — 원천 기준시점 차이일 가능성",
            "E": "위 신호가 뚜렷하지 않아 현재 데이터만으로 판정 불가",
        },
        "close_to_snapshot_gap_distribution": dict(gap_bucket_counts),
        "risk_summary": (
            f"오래전(365일 초과) 폐업했는데도 최신 상가정보에 동일 상호/주소가 남아있는 "
            f"A유형이 {classification_counts.get('A', 0)}건 — LOCALDATA CLOSED를 상가정보에 "
            "그대로 전파(자동 폐업 처리)하면 이 규모만큼은 비교적 안전하게 반영될 수 있지만, "
            f"B(재개업 의심 {classification_counts.get('B', 0)}건)와 "
            f"C(체인 오연결 의심 {classification_counts.get('C', 0)}건)는 그대로 전파할 경우 "
            "실제 영업 중인 다른 사업자를 폐업 처리하는 위험이 있다."
        ),
        "samples_by_classification": dict(samples_by_class),
    }

    write_json("storeinfo-closed-conflict-character", result)
    print(
        f"CLOSED conflict 성격분석 total={total_checked} "
        f"classification={dict(classification_counts)} gap={dict(gap_bucket_counts)}"
    )
    return result


# ---------------------------------------------------------------------------
# 4. MEDIUM(도로명주소 기반) 1,147건 검증
# ---------------------------------------------------------------------------

def analyze_medium_tier_validation(medium_records):
    """MEDIUM(이름+도로명주소 일치)에 좌표거리·업종호환성을 추가 검증해
    HIGH로 승격 가능한지, MEDIUM 유지가 맞는지, 오연결 위험이 있는지 분류한다.

    상가정보 원천에는 전화번호 컬럼이 없어 전화번호 일치 기준은 적용할 수
    없다 — 이 한계를 결과에 명시한다.
    """
    promote_high = []
    keep_medium = []
    likely_wrong = []

    for rec in medium_records:
        match = rec["match"]
        dist_m = None
        if rec["store_info_lat"] is not None and match["lat"] is not None:
            dist_m = haversine_m(rec["store_info_lat"], rec["store_info_lon"], match["lat"], match["lon"])

        compat_set = CATEGORY_COMPAT.get(match["source"], set())
        category_ok = rec["store_info_category"] in compat_set if compat_set else None

        entry = {
            "store_info_name": rec["store_info_name"],
            "store_info_road": rec["store_info_road"],
            "matched_name": match["name"],
            "matched_source": match["source"],
            "coord_distance_m": round(dist_m, 1) if dist_m is not None else None,
            "store_info_category": rec["store_info_category"],
            "category_compatible": category_ok,
        }

        if dist_m is not None and dist_m <= 50 and category_ok is True:
            promote_high.append(entry)
        elif (dist_m is not None and dist_m > 300) or category_ok is False:
            likely_wrong.append(entry)
        else:
            keep_medium.append(entry)

    result = {
        "purpose": "MEDIUM(이름+도로명주소) 1,147건에 좌표거리+업종호환성을 추가해 HIGH 승격 가능성을 검증",
        "limitation_note": (
            "상가정보 원천에 전화번호 컬럼이 없어 전화번호 일치 기준은 적용하지 못했다. "
            "좌표거리(<=50m)와 업종호환성(상가정보 상권업종대분류명 vs LOCALDATA 원천 유형) "
            "두 기준만으로 판단했다."
        ),
        "total_medium": len(medium_records),
        "promotable_to_high": len(promote_high),
        "keep_medium": len(keep_medium),
        "likely_wrong_link": len(likely_wrong),
        "promote_high_samples": promote_high[:15],
        "likely_wrong_samples": likely_wrong[:15],
        "decision": (
            f"{len(promote_high)}건은 좌표 50m 이내 + 업종 호환으로 HIGH 승격 후보로 볼 수 있으나, "
            "이번 패스에서는 실제 상태 보강(정 필드 반영)을 하지 않고 분류 결과만 남긴다. "
            f"나머지 {len(keep_medium) + len(likely_wrong)}건은 안전 기준 미달로 MEDIUM 유지."
        ),
    }

    write_json("storeinfo-medium-tier-validation", result)
    print(
        f"MEDIUM 검증 total={len(medium_records)} promotable={len(promote_high)} "
        f"keep_medium={len(keep_medium)} likely_wrong={len(likely_wrong)}"
    )
    return result


# ---------------------------------------------------------------------------
# 5. 주차장 duplicate 후보 검증(19그룹)
# ---------------------------------------------------------------------------

def analyze_parking_duplicates():
    path = "data/raw/standard/주차장/전국주차장정보표준데이터.csv"
    rows = load_csv(path, "cp949")
    buul = [
        r
        for r in rows
        if (r.get("소재지도로명주소") or r.get("소재지지번주소") or "").startswith(
            ("부산", "울산", "경남", "경상남도")
        )
    ]

    groups = defaultdict(list)
    for r in buul:
        name = norm(r.get("주차장명"))
        lot = norm(r.get("소재지지번주소"))
        if name and lot:
            groups[(name, lot)].append(r)

    dup_groups = {k: v for k, v in groups.items() if len(v) > 1}

    classification_counts = Counter()
    details = []
    for (name, lot), grp_rows in dup_groups.items():
        coords = []
        for r in grp_rows:
            lat = to_float(r.get("위도"))
            lon = to_float(r.get("경도"))
            if lat is not None:
                coords.append((lat, lon))
        max_dist = 0
        if len(coords) > 1:
            for i in range(len(coords)):
                for j in range(i + 1, len(coords)):
                    d = haversine_m(*coords[i], *coords[j])
                    if d is not None:
                        max_dist = max(max_dist, d)

        capacities = {norm(r.get("주차구획수")) for r in grp_rows}
        orgs = {norm(r.get("관리기관명")) for r in grp_rows}
        phones = {norm(r.get("전화번호")) for r in grp_rows}
        types = {norm(r.get("주차장유형")) for r in grp_rows}

        # 이름·주소가 이미 동일(그룹 키 자체가 이름+지번주소)하므로
        # 좌표 근접성과 부가 필드 일치 여부로 세부 성격을 가른다.
        if max_dist < 30 and len(capacities) == 1:
            classification = "A"  # 확정 동일 시설(좌표도 사실상 같은 지점, 구획수도 동일)
        elif max_dist < 30:
            classification = "B"  # 같은 부지의 서로 다른 시설(좌표는 같으나 구획수 등 상이)
        elif max_dist >= 30:
            classification = "C"  # 이름만 같은 별도 시설(같은 이름·주소 문자열이나 좌표가 떨어져 있음)
        else:
            classification = "D"

        classification_counts[classification] += 1
        details.append(
            {
                "name": name,
                "lot_addr": lot,
                "record_count": len(grp_rows),
                "max_coord_distance_m": round(max_dist, 1),
                "capacities": sorted(capacities),
                "org_names": sorted(orgs),
                "phones": sorted(phones),
                "parking_types": sorted(types),
                "classification": classification,
            }
        )

    result = {
        "purpose": "주차장 이름+지번주소 매칭 19그룹 전수 세부 검증(관리번호는 신뢰 불가로 제외)",
        "total_groups": len(dup_groups),
        "total_records": sum(len(v) for v in dup_groups.values()),
        "classification_counts": dict(classification_counts),
        "classification_legend": {
            "A": "확정 동일 시설(좌표 30m 이내 + 구획수 등 부가필드 동일)",
            "B": "같은 부지의 서로 다른 시설(좌표 30m 이내지만 구획수 등 상이 — 예: 지상/지하 별도 등록)",
            "C": "이름만 같은 별도 시설(이름·주소 문자열은 같으나 좌표가 30m 이상 떨어짐)",
            "D": "판정 불가",
        },
        "auto_dedupe_suggestion": (
            "A유형(좌표 30m 이내 + 구획수 동일)만 자동 dedupe 후보로 제안할 수 있다. "
            "B/C는 실제로 서로 다른 시설(별도 구획·건물)일 가능성이 있어 자동 병합하면 "
            "실제 주차 공간을 잃을 위험이 있다. 이번 패스에서는 제안만 하고 실제 병합하지 않았다."
        ),
        "group_details": details,
    }

    write_json("parking-duplicate-validation", result)
    print(
        f"주차장 dup groups={len(dup_groups)} records={sum(len(v) for v in dup_groups.values())} "
        f"classification={dict(classification_counts)}"
    )
    return result


# ---------------------------------------------------------------------------
# 5-보완. 대표행 자동선택 실패(동점) 7그룹 마무리 검토
# ---------------------------------------------------------------------------

PARKING_DETAIL_FIELDS = ["요금정보", "운영요일", "특기사항", "평일운영시작시각", "평일운영종료시각"]
PARKING_FULL_DIFF_FIELDS = [
    "소재지도로명주소",
    "전화번호",
    "관리기관명",
    "주차구획수",
    "장애인전용주차구역보유여부",
    "요금정보",
    "운영요일",
    "평일운영시작시각",
    "평일운영종료시각",
    "특기사항",
    "데이터기준일자",
]


def _parking_score(row: dict) -> tuple:
    lot = norm(row.get("소재지지번주소"))
    road = norm(row.get("소재지도로명주소"))
    phone = norm(row.get("전화번호"))
    addr_fill = (1 if lot else 0) + (1 if road else 0)
    has_road = 1 if road else 0
    has_phone = 1 if phone else 0
    detail_fill = sum(1 for f in PARKING_DETAIL_FIELDS if norm(row.get(f)))
    date_str = norm(row.get("데이터기준일자"))
    return (addr_fill, has_road, has_phone, detail_fill, date_str)


def analyze_parking_remaining_ties():
    """대표행 자동 선택 규칙(주소채움률>도로명주소>전화번호>상세필드채움률>기준일)이
    동점으로 끝난 7그룹만 전체 필드 diff로 재검토한다. 7개 그룹 때문에 범용
    dedupe 엔진을 만들지 않고, 명백한 구본/신본 관계 + 정보 손실 없음이 확인된
    경우에만 최신행을 대표행 "후보"로 제안한다(실제 staging 반영은 하지 않음)."""
    path = "data/raw/standard/주차장/전국주차장정보표준데이터.csv"
    rows = load_csv(path, "cp949")
    buul = [
        r for r in rows
        if (r.get("소재지도로명주소") or r.get("소재지지번주소") or "").startswith(
            ("부산", "울산", "경남", "경상남도")
        )
    ]
    groups = defaultdict(list)
    for r in buul:
        name = norm(r.get("주차장명"))
        lot = norm(r.get("소재지지번주소"))
        if name and lot:
            groups[(name, lot)].append(r)

    tie_groups = []
    for key, grp_rows in groups.items():
        if len(grp_rows) < 2:
            continue
        scored = [(_parking_score(r), r) for r in grp_rows]
        best = max(s for s, _ in scored)
        winners = [r for s, r in scored if s == best]
        if len(winners) > 1:
            tie_groups.append((key, grp_rows))

    results = []
    proposal_counts = Counter()
    for (name, lot), grp_rows in tie_groups:
        by_date = sorted(
            grp_rows, key=lambda r: norm(r.get("데이터기준일자")), reverse=True
        )
        latest, older = by_date[0], by_date[1]

        field_diff = {}
        for f in PARKING_FULL_DIFF_FIELDS:
            lv, ov = norm(latest.get(f)), norm(older.get(f))
            if lv != ov:
                field_diff[f] = {"latest": lv, "older": ov}

        info_loss_fields = [
            f for f in PARKING_FULL_DIFF_FIELDS
            if not norm(latest.get(f)) and norm(older.get(f))
        ]

        if norm(latest.get("데이터기준일자")) != norm(older.get("데이터기준일자")) and not info_loss_fields:
            decision = "A'(명백한 구본/신본, 정보 손실 없음) — 최신행을 대표행 후보로 제안"
            proposal_counts["대표행_후보_제안"] += 1
        elif info_loss_fields:
            decision = "정보 보완적(최신행이 일부 정보를 잃음) — 병합/제거하지 않고 유지"
            proposal_counts["유지_정보보완적"] += 1
        else:
            decision = "판정 불가 — 유지"
            proposal_counts["유지_판정불가"] += 1

        results.append(
            {
                "name": name,
                "lot_addr": lot,
                "latest_mgmt_id": norm(latest.get("주차장관리번호")),
                "older_mgmt_id": norm(older.get("주차장관리번호")),
                "latest_data_date": norm(latest.get("데이터기준일자")),
                "older_data_date": norm(older.get("데이터기준일자")),
                "field_diff": field_diff,
                "info_loss_fields_in_latest": info_loss_fields,
                "decision": decision,
            }
        )

    result = {
        "purpose": "대표행 자동선택 동점 7그룹 전체 필드 diff 재검토(범용 엔진 아님, 이 7건 전용)",
        "tie_group_count": len(tie_groups),
        "decision_counts": dict(proposal_counts),
        "groups": results,
    }
    write_json("parking-tie-group-final-review", result)
    print(f"주차장 동점 그룹 재검토 count={len(tie_groups)} decisions={dict(proposal_counts)}")
    return result


# ---------------------------------------------------------------------------
# 6. UNKNOWN 상태 성격 분류(원천별) — 판단만, 결정 없음
# ---------------------------------------------------------------------------

def summarize_unknown_character(tourlodge_result, storeinfo_result):
    result = {
        "purpose": "UNKNOWN 상태의 원천별 성격 구분 — 서비스 노출 여부는 이번 패스에서 결정하지 않는다",
        "categories": {
            "상태_컬럼_자체_없음": {
                "sources": ["상가정보", "TourAPI 목록", "주차장 표준데이터"],
                "note": "원천 스키마에 영업상태 필드가 존재하지 않는다. 다른 원천과 연결하지 않는 한 영구히 UNKNOWN이다",
            },
            "다른_원천으로_상태_보강_가능": {
                "sources": ["상가정보"],
                "note": (
                    f"LOCALDATA 5종과 이름+주소 매칭 시 {storeinfo_result['match_rate_pct']}%가 "
                    f"연결되며, 그중 고신뢰(이름+지번주소 완전일치)는 "
                    f"{storeinfo_result['high_confidence_rate_pct']}%다. 다만 이번 패스에서는 "
                    "제안(status_enrichment_candidate)만 남기고 실제 보강은 하지 않았다"
                ),
            },
            "상태_확인_불가_원천_식별자_신뢰_문제": {
                "sources": ["주차장(관리번호 신뢰 불가)"],
                "note": "관리번호가 서로 다른 시설 간에도 공유돼, 상태와 무관하게 레코드 자체의 식별 신뢰도가 낮다",
            },
            "최신성_신뢰도_낮음": {
                "sources": ["상가정보(2026-06 스냅샷 고정, 개업일 필드 없음)"],
                "note": (
                    f"LOCALDATA CLOSED와 매칭되는 상가정보 레코드가 "
                    f"{storeinfo_result['status_conflict_sample_count']}건 있다 — 재개업/신규 "
                    "사업자 여부를 상가정보 단독으로는 구분할 수 없다"
                ),
            },
        },
    }
    write_json("unknown-status-character-summary", result)
    print("UNKNOWN 성격 요약 리포트 작성 완료")
    return result


if __name__ == "__main__":
    tourlodge_result = analyze_tourlodge_duplicates()
    storeinfo_result, addr_business_index, name_addr_index, medium_records = (
        analyze_storeinfo_linkage()
    )
    closed_conflict_result = analyze_closed_conflict_character(addr_business_index, name_addr_index)
    medium_result = analyze_medium_tier_validation(medium_records)
    parking_result = analyze_parking_duplicates()
    parking_tie_result = analyze_parking_remaining_ties()
    unknown_result = summarize_unknown_character(tourlodge_result, storeinfo_result)

    summary = {
        "purpose": "P1 staging 2차 정제 검증 통합 요약",
        "tourlodge_duplicate_analysis": {
            "total_groups": tourlodge_result["total_groups"],
            "classification_group_counts": tourlodge_result["classification_group_counts"],
            "cross_region_id_collision_count": tourlodge_result[
                "data_quality_finding_cross_region_id_collision"
            ]["cross_region_colliding_id_count"],
            "representative_rule_conclusion": tourlodge_result[
                "representative_rule_verification"
            ]["conclusion"],
        },
        "storeinfo_linkage": {
            "match_rate_pct": storeinfo_result["match_rate_pct"],
            "high_confidence_rate_pct": storeinfo_result["high_confidence_rate_pct"],
            "high_closed_conflict_count": storeinfo_result["status_conflict_sample_count"],
        },
        "closed_conflict_character": {
            "classification_counts": closed_conflict_result["classification_counts"],
            "gap_distribution": closed_conflict_result["close_to_snapshot_gap_distribution"],
        },
        "medium_tier_validation": {
            "total_medium": medium_result["total_medium"],
            "promotable_to_high": medium_result["promotable_to_high"],
            "keep_medium": medium_result["keep_medium"],
            "likely_wrong_link": medium_result["likely_wrong_link"],
        },
        "parking_duplicate_validation": {
            "total_groups": parking_result["total_groups"],
            "classification_counts": parking_result["classification_counts"],
        },
        "parking_tie_group_final_review": {
            "tie_group_count": parking_tie_result["tie_group_count"],
            "decision_counts": parking_tie_result["decision_counts"],
        },
        "generated_at": datetime.now(UTC).strftime("%Y-%m-%d"),
    }
    write_json("staging-v2-summary", summary)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
