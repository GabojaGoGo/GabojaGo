"""P1 cross-source entity resolution 검증 — 1차(5쌍) + 2차(8쌍 vs TourAPI) 통합.

목적: 22개 staging 원천을 바로 병합하는 게 아니라, 서로 다른 원천의 동일 장소를
안전하게 연결할 수 있는 기준(threshold)을 실측으로 검증한다. 실제 병합/canonical
ID 생성은 하지 않는다 — 결과는 report로만 남긴다.

1차 결론(재검증하지 않고 전제로 삼는다):
- 점형 장소: 이름 완전일치 + 좌표 30m 이내는 HIGH 후보로 안전
- 이름+주소 완전일치도 강한 신호이나, COARSE(번지/건물번호 없는) 주소는 하향 필요
- 산/공원/해변 등 면적형 장소는 점형 거리 threshold를 그대로 적용하면 안 됨
- 이름 단독 매칭은 일반명사 시설명(예: "인공암벽장")에서 위험 — 좌표 블로킹으로 방지
- 실제 canonical 병합은 아직 하지 않음

이번 패스 추가:
- 주소를 PRECISE/COARSE/MISSING으로 분류(`data_import.cleaning.classify_address_precision`)
- HIGH 조건을 A(이름+좌표30m)/B(이름+PRECISE주소)/C(PRECISE주소+전화번호) 규칙별로 분리 집계
- 면적형 장소 이름은 점형 통계에서 분리(AREA_PLACE_REVIEW_REQUIRED)
- 8개 신규 쌍(vs TourAPI) 추가
- pair별 예외 태그(COMMON_HIGH_OK/PAIR_SPECIFIC_RESTRICTION/MEDIUM_ONLY/LINKING_NOT_USEFUL/P2_REVIEW_REQUIRED)
"""

from __future__ import annotations

import json
from collections import Counter, defaultdict
from datetime import UTC, datetime
from pathlib import Path

from data_import.cleaning import (
    classify_address_precision,
    haversine_m,
    is_area_name,
    norm_nospace,
    normalize_str,
)

PROJECT_ROOT = Path(__file__).resolve().parent.parent
STAGING = PROJECT_ROOT / "data/staging"
RAW = PROJECT_ROOT / "data/raw"
REPORTS = PROJECT_ROOT / "data/reports/entity-resolution"

# 면적형 장소 판정은 data_import.cleaning.is_area_name(공용)이 소유한다.


# ---------------------------------------------------------------------------
# 공통 레코드 shape
# ---------------------------------------------------------------------------


def _rec(name, lot, road, lat, lon, phone, category, raw_ref):
    return {
        "name": name,
        "name_ns": norm_nospace(name) if name else "",
        "lot": lot,
        "road": road,
        "addr_precision": classify_address_precision(lot, road),
        "lat": lat,
        "lon": lon,
        "phone": normalize_str(phone) if phone else None,
        "category": category,
        "raw_ref": raw_ref,
    }


def load_staging(source: str) -> list[dict]:
    path = STAGING / source / "부울경.json"
    return json.loads(path.read_text(encoding="utf-8"))


def _staging_ref(source: str, source_record_id, index: int) -> str:
    """재현 가능한 staging-local reference. 원천에 안정적인 ID가 있으면 그것을,
    없으면 staging JSON 배열 내 순번을 쓴다(같은 staging 파일을 재생성하지 않는
    한 순서는 고정) — 임의의 canonical ID를 새로 만들지 않는다."""
    if source_record_id:
        return f"{source}#id:{source_record_id}"
    return f"{source}#idx:{index}"


def adapt_staging_generic(source: str, phone_provenance_key: str | None = None,
                            category_provenance_key: str | None = None) -> list[dict]:
    rows = load_staging(source)
    out = []
    for i, r in enumerate(rows):
        prov = r.get("provenance") or {}
        phone = prov.get(phone_provenance_key) if phone_provenance_key else None
        category = prov.get(category_provenance_key) if category_provenance_key else None
        rid = r.get("source_record_id")
        out.append(
            _rec(
                r.get("normalized_name"), r.get("normalized_address_lot"),
                r.get("normalized_address_road"), r.get("latitude"), r.get("longitude"),
                phone, category,
                {"source": source, "id": rid, "ref": _staging_ref(source, rid, i)},
            )
        )
    return out


def adapt_tourapi(type_names: list[str]) -> list[dict]:
    out = []
    for type_name in type_names:
        path = STAGING / "TourAPI목록" / f"{type_name}.json"
        if not path.exists():
            continue
        rows = json.loads(path.read_text(encoding="utf-8"))
        source = f"TourAPI:{type_name}"
        for i, r in enumerate(rows):
            rid = r.get("source_record_id")
            out.append(
                _rec(
                    r.get("normalized_name"), None, r.get("normalized_address_road"),
                    r.get("latitude"), r.get("longitude"), None,
                    r.get("provenance", {}).get("content_type_name"),
                    {"source": source, "id": rid, "ref": _staging_ref(source, rid, i)},
                )
            )
    return out


def adapt_national_sports_facility() -> list[dict]:
    path = RAW / "standard/국민체육진흥공단_전국체육시설/부울경_17978건.json"
    rows = json.loads(path.read_text(encoding="utf-8"))
    source = "국민체육진흥공단_전국체육시설"
    out = []
    for i, r in enumerate(rows):
        rid = r.get("faci_cd")
        out.append(
            _rec(
                r.get("faci_nm"), r.get("faci_addr"), r.get("faci_road_addr"),
                float(r["faci_lat"]) if r.get("faci_lat") else None,
                float(r["faci_lot"]) if r.get("faci_lot") else None,
                r.get("faci_tel_no"), r.get("fcob_nm"),
                {"source": source, "id": rid, "status": r.get("faci_stat_nm"),
                 "ref": _staging_ref(source, rid, i)},
            )
        )
    return out


# ---------------------------------------------------------------------------
# 매칭 신호
# ---------------------------------------------------------------------------

DIST_BUCKETS = [10, 30, 50, 100, 300]


def dist_bucket(d: float | None) -> str:
    if d is None:
        return "좌표없음"
    for b in DIST_BUCKETS:
        if d <= b:
            return f"<={b}m"
    return ">300m"


def addr_lot_match(a: dict, b: dict) -> bool:
    return bool(a["lot"] and b["lot"] and a["lot"] == b["lot"])


def addr_road_match(a: dict, b: dict) -> bool:
    return bool(a["road"] and b["road"] and a["road"] == b["road"])


def addr_any_overlap(a: dict, b: dict) -> bool:
    """지번↔도로명처럼 서로 다른 주소체계를 가진 원천 간 대조용 — 한쪽이 다른 쪽과
    정확히 같은 문자열인지만 본다(공격적 정규화 없이 원문 그대로 비교)."""
    a_addrs = [x for x in (a["lot"], a["road"]) if x]
    b_addrs = [x for x in (b["lot"], b["road"]) if x]
    for x in a_addrs:
        for y in b_addrs:
            if x == y:
                return True
    return False


def name_similar(a_ns: str, b_ns: str) -> bool:
    """괄호/지점명 등으로 인한 부분일치만 본다 — 범용 fuzzy 엔진은 만들지 않는다."""
    if not a_ns or not b_ns or a_ns == b_ns:
        return False
    shorter, longer = (a_ns, b_ns) if len(a_ns) <= len(b_ns) else (b_ns, a_ns)
    if len(shorter) < 2:
        return False
    return shorter in longer


def classify(a: dict, b: dict, dist: float | None) -> tuple[str, list[str], str | None]:
    """반환: (tier, signals, high_rule) — high_rule은 A/B/C 중 HIGH를 발생시킨 규칙."""
    signals = []
    name_exact = a["name_ns"] == b["name_ns"] and a["name_ns"] != ""
    any_addr_exact = addr_lot_match(a, b) or addr_road_match(a, b) or addr_any_overlap(a, b)
    precise_addr_exact = any_addr_exact and a["addr_precision"] == "PRECISE" and b["addr_precision"] == "PRECISE"
    coarse_addr_exact = any_addr_exact and not precise_addr_exact
    phone_exact = bool(a["phone"] and b["phone"] and a["phone"] == b["phone"])
    close_30 = dist is not None and dist <= 30
    close_100 = dist is not None and dist <= 100
    name_sim = name_similar(a["name_ns"], b["name_ns"])

    if name_exact:
        signals.append("이름완전일치")
    if precise_addr_exact:
        signals.append("PRECISE주소완전일치")
    elif coarse_addr_exact:
        signals.append("COARSE주소완전일치")
    if phone_exact:
        signals.append("전화번호일치")
    if dist is not None:
        signals.append(f"좌표거리={round(dist)}m")
    if name_sim:
        signals.append("이름부분일치")

    # HIGH: A(이름+30m 이내) / B(이름+PRECISE주소) / C(PRECISE주소+전화번호)
    if name_exact and close_30:
        return "HIGH", signals, "A"
    if name_exact and precise_addr_exact:
        return "HIGH", signals, "B"
    if precise_addr_exact and phone_exact:
        return "HIGH", signals, "C"

    # MEDIUM: 이름+31~100m / 이름+COARSE주소 / 이름유사+강한좌표근접
    if name_exact and close_100:
        return "MEDIUM", signals, None
    if name_exact and coarse_addr_exact:
        return "MEDIUM", signals, None
    if name_sim and close_30:
        return "MEDIUM", signals, None

    # LOW: 이름만 / COARSE주소만 / 원거리+corroboration 없음
    if name_sim or coarse_addr_exact or close_100:
        return "LOW", signals, None

    return "UNMATCHED", signals, None


def build_blocking_index(records: list[dict], cell_deg: float = 0.02):
    idx: dict[tuple, list[dict]] = defaultdict(list)
    for r in records:
        if r["lat"] is None or r["lon"] is None:
            idx[("NOCOORD", "NOCOORD")].append(r)
            continue
        key = (round(r["lat"] / cell_deg), round(r["lon"] / cell_deg))
        idx[key].append(r)
    return idx


def candidate_cells(lat: float, lon: float, cell_deg: float = 0.02):
    cy, cx = round(lat / cell_deg), round(lon / cell_deg)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            yield (cy + dy, cx + dx)


def match_pair(pair_name: str, left: list[dict], right: list[dict],
               left_label: str, right_label: str) -> dict:
    right_idx = build_blocking_index(right)
    dist_dist = Counter()
    dist_dist_name_exact = Counter()
    tier_counts = Counter()
    high_rule_counts = Counter()
    area_flagged = Counter()  # tier -> count, 면적형이라 통계에서 분리한 것
    matches_high = []
    matches_medium = []
    matches_low_sample = []
    area_samples = []

    for a in left:
        candidates = []
        if a["lat"] is not None:
            for cell in candidate_cells(a["lat"], a["lon"]):
                candidates.extend(right_idx.get(cell, []))
        candidates.extend(right_idx.get(("NOCOORD", "NOCOORD"), []))
        if a["lat"] is None:
            for cell, recs in right_idx.items():
                if cell != ("NOCOORD", "NOCOORD"):
                    candidates.extend(recs)

        best = None
        seen = set()
        for b in candidates:
            bid = id(b)
            if bid in seen:
                continue
            seen.add(bid)

            dist = None
            if a["lat"] is not None and b["lat"] is not None:
                dist = haversine_m(a["lat"], a["lon"], b["lat"], b["lon"])

            if a["name_ns"] == b["name_ns"] and a["name_ns"] != "" and dist is not None:
                dist_dist_name_exact[dist_bucket(dist)] += 1

            tier, signals, high_rule = classify(a, b, dist)
            if tier == "UNMATCHED":
                continue
            rank = {"HIGH": 0, "MEDIUM": 1, "LOW": 2}[tier]
            if best is None or rank < best[0] or (rank == best[0] and (dist or 1e9) < (best[1] or 1e9)):
                best = (rank, dist, b, tier, signals, high_rule)

        if best is None:
            tier_counts["UNMATCHED"] += 1
            continue

        _, dist, b, tier, signals, high_rule = best
        area = is_area_name(a["name"]) or is_area_name(b["name"])

        entry = {
            "left_name": a["name"], "left_lot": a["lot"], "left_road": a["road"],
            "left_addr_precision": a["addr_precision"],
            "right_name": b["name"], "right_lot": b["lot"], "right_road": b["road"],
            "right_addr_precision": b["addr_precision"],
            "distance_m": round(dist, 1) if dist is not None else None,
            "signals": signals, "high_rule": high_rule,
            "left_ref": a["raw_ref"], "right_ref": b["raw_ref"],
        }

        if area:
            area_flagged[tier] += 1
            if len(area_samples) < 15:
                area_samples.append({**entry, "flag": "AREA_PLACE_REVIEW_REQUIRED"})
            continue  # 점형 통계에서 제외

        tier_counts[tier] += 1
        dist_dist[dist_bucket(dist)] += 1
        if tier == "HIGH":
            high_rule_counts[high_rule] += 1

        if tier == "HIGH" and len(matches_high) < 30:
            matches_high.append(entry)
        elif tier == "MEDIUM" and len(matches_medium) < 20:
            matches_medium.append(entry)
        elif tier == "LOW" and len(matches_low_sample) < 15:
            matches_low_sample.append(entry)

    total_point = sum(tier_counts.values())
    return {
        "pair": pair_name,
        "left_source": left_label,
        "right_source": right_label,
        "left_count": len(left),
        "right_count": len(right),
        "point_type_matched_total": total_point,
        "tier_counts": dict(tier_counts),
        "high_rule_counts": dict(high_rule_counts),
        "area_place_flagged_counts": dict(area_flagged),
        "distance_distribution_of_best_match": dict(dist_dist),
        "distance_distribution_when_name_exact": dict(dist_dist_name_exact),
        "high_samples": matches_high,
        "medium_samples": matches_medium,
        "low_samples": matches_low_sample,
        "area_place_samples": area_samples,
    }


def write_report(pair_key: str, result: dict) -> Path:
    REPORTS.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(UTC).strftime("%Y%m%d-%H%M%SZ")
    path = REPORTS / f"{pair_key}-{timestamp}.json"
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def main() -> None:
    pairs = []

    # --- 1차 5쌍(재검증 아님 — 새 규칙으로 재계산해 일관성 유지) ---
    fishing_port = adapt_staging_generic("어항정보")
    marina = adapt_staging_generic("마리나")
    pairs.append(("fishing-port-vs-marina", "어항정보", "마리나", fishing_port, marina))

    ulsan_culture = adapt_staging_generic("울산문화시설", phone_provenance_key="전화번호",
                                           category_provenance_key="구분(원본 분류값 그대로)")
    museum = adapt_staging_generic("박물관미술관", phone_provenance_key="전화번호",
                                    category_provenance_key="시설유형")
    pairs.append(("ulsan-culture-vs-museum", "울산문화시설", "박물관미술관", ulsan_culture, museum))

    visitbusanpass = adapt_staging_generic("비짓부산패스", phone_provenance_key="cntct_tel",
                                            category_provenance_key="tourist_tycd")
    tourapi_vbp = adapt_tourapi(["관광지", "문화시설", "음식점"])
    pairs.append(("visitbusanpass-vs-tourapi", "비짓부산패스", "TourAPI(관광지+문화시설+음식점)",
                  visitbusanpass, tourapi_vbp))

    heritage = adapt_staging_generic("국가유산공간정보", category_provenance_key="종목명")
    tourapi_heritage = adapt_tourapi(["관광지", "문화시설"])
    pairs.append(("heritage-vs-tourapi", "국가유산공간정보", "TourAPI(관광지+문화시설)",
                  heritage, tourapi_heritage))

    busan_sports = adapt_staging_generic("부산공공체육시설", category_provenance_key="시설유형")
    national_sports = adapt_national_sports_facility()
    pairs.append(("busan-sports-vs-national-sports", "부산공공체육시설", "국민체육진흥공단_전국체육시설",
                  busan_sports, national_sports))

    # --- 2차 8쌍(vs TourAPI 관광지+문화지설+레포츠) ---
    tourapi_general = adapt_tourapi(["관광지", "문화시설", "레포츠"])

    for source, phone_key, cat_key in [
        ("낚시터", "전화번호", "낚시터유형"),
        ("어항정보", None, None),
        ("관광안내소", "전화번호", None),
        ("농어촌체험휴양마을", "전화번호", None),
        ("유아숲체험원", "전화번호", None),
        ("마리나", None, "마리나항만종류명"),
        ("치유농업시설", None, None),
        ("울산문화시설", "전화번호", "구분(원본 분류값 그대로)"),
    ]:
        left = adapt_staging_generic(source, phone_provenance_key=phone_key, category_provenance_key=cat_key)
        key = f"{source}-vs-tourapi"
        pairs.append((key, source, "TourAPI(관광지+문화시설+레포츠)", left, tourapi_general))

    all_results = []
    for key, left_label, right_label, left, right in pairs:
        result = match_pair(key, left, right, left_label, right_label)
        path = write_report(key, result)
        summary_row = {k: v for k, v in result.items()
                        if k not in ("high_samples", "medium_samples", "low_samples", "area_place_samples")}
        summary_row["report_file"] = path.name
        all_results.append(summary_row)
        print(
            f"{key}: left={result['left_count']} right={result['right_count']} "
            f"point_matched={result['point_type_matched_total']} tiers={result['tier_counts']} "
            f"high_rules={result['high_rule_counts']} area_flagged={result['area_place_flagged_counts']}"
        )

    # --- HIGH precision 전체 집계(규칙별) ---
    high_rule_totals = Counter()
    for r in all_results:
        for rule, cnt in r["high_rule_counts"].items():
            high_rule_totals[rule] += cnt
    total_high = sum(high_rule_totals.values())

    summary = {
        "purpose": "cross-source entity resolution 검증 — 1차 5쌍 + 2차 8쌍(vs TourAPI), "
                   "주소 PRECISE/COARSE 분류 + HIGH 규칙별(A/B/C) precision 집계",
        "pairs": all_results,
        "high_rule_totals": dict(high_rule_totals),
        "total_high_candidates": total_high,
        "generated_at": datetime.now(UTC).strftime("%Y-%m-%d"),
    }
    write_report("consolidated-summary-v2", summary)
    print(json.dumps({k: v for k, v in summary.items() if k != "pairs"}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
