"""P1 원천 내부 중복 정리 — cross-source entity-resolution에서 드러난 확정 중복만
staging에서 실제로 제거한다. raw는 수정하지 않는다.

대상: 비짓부산패스, 전국농어촌체험휴양마을표준데이터, 전국박물관미술관정보표준데이터,
부산공공체육시설(확정 2건만).

공통 규칙(§6):
- raw 문자열 완전일치만 쓰지 않는다 — normalized name + (lot 없으면 road) + 좌표를 함께 본다
- 같은 좌표만으로 병합하지 않는다(이름도 완전일치해야 함)
- 시설유형/category가 다르면 복합시설 가능성으로 보고 병합하지 않는다
- source ID만으로 병합하지 않는다
- 확정 동일 장소(A)만 자동 dedupe. B/C/D는 플래그만 하고 병합하지 않는다
"""

from __future__ import annotations

import json
from collections import Counter, defaultdict
from datetime import UTC, datetime
from pathlib import Path

from data_import.cleaning import haversine_m, is_area_name, norm_nospace

PROJECT_ROOT = Path(__file__).resolve().parent.parent
STAGING = PROJECT_ROOT / "data/staging"
REPORTS = PROJECT_ROOT / "data/reports/cleaning"

# 실측으로 확인된 예외 — 근접 좌표·이름 일치이지만 실제로는 하나의 정형 주소가
# 아니라 여러 하위 지점을 나열한 집계형 설명이라 자동 병합에서 제외한다.
# (예: "해운대"의 한 레코드 주소가 "해운대해수욕장...청사포...미포..."를 한 번에 나열함)
MANUAL_EXCLUDE_NAMES: dict[str, set[str]] = {
    "비짓부산패스": {"해운대", "장안사"},
}


def phone_of(row: dict) -> str | None:
    prov = row.get("provenance") or {}
    for key in ("전화번호", "cntct_tel", "GUIDANCE_PHONE_NUMBER"):
        if prov.get(key):
            return prov[key]
    return None


def score_row(row: dict) -> tuple:
    lot = row.get("normalized_address_lot")
    road = row.get("normalized_address_road")
    phone = phone_of(row)
    addr_fill = (1 if lot else 0) + (1 if road else 0)
    has_phone = 1 if phone else 0
    # 문체부/정부 대표번호("044-...")보다 기관 직통번호가 더 구체적인 신호다
    phone_specific = 1 if phone and not str(phone).startswith("044") else 0
    addr_len = len((lot or "") + (road or ""))
    return (addr_fill, has_phone, phone_specific, addr_len)


def build_name_groups(rows: list[dict]) -> dict[str, list[tuple[int, dict]]]:
    groups: dict[str, list[tuple[int, dict]]] = defaultdict(list)
    for i, r in enumerate(rows):
        k = norm_nospace(r.get("normalized_name"))
        if k:
            groups[k].append((i, r))
    return {k: v for k, v in groups.items() if len(v) > 1}


def classify_group(source: str, name_key: str, members: list[tuple[int, dict]]) -> tuple[str, str]:
    """반환: (classification A/B/C/D, reason)."""
    rows = [r for _, r in members]
    name = rows[0]["normalized_name"]

    if is_area_name(name):
        return "D", "면적형 장소 이름 — 이번 패스에서 자동 dedupe 대상 제외(P2 대기)"

    if name in MANUAL_EXCLUDE_NAMES.get(source, set()):
        return "D", "표본 검토 결과 집계형/모호 주소로 확인돼 수동 제외"

    categories = {(r.get("provenance") or {}).get("시설유형") or (r.get("provenance") or {}).get("FCLTY_TYPE")
                  for r in rows}
    categories.discard(None)
    if len(categories) > 1:
        return "B", f"시설유형이 서로 다름({sorted(categories)}) — 복합시설의 다른 하위시설 가능성"

    coords = [(r["latitude"], r["longitude"]) for r in rows if r.get("latitude") is not None]
    if len(coords) < len(rows):
        # 좌표가 없는 쪽이 있음 — 주소 문자열이 완전히 같을 때만 A로 인정
        addrs = {(r.get("normalized_address_lot"), r.get("normalized_address_road")) for r in rows}
        if len(addrs) == 1:
            return "A", "좌표 일부 결측이나 주소 문자열이 완전히 동일"
        return "D", "좌표 결측 + 주소도 불일치 — 판정에 필요한 정보 부족"

    max_dist = 0.0
    for i in range(len(coords)):
        for j in range(i + 1, len(coords)):
            d = haversine_m(*coords[i], *coords[j])
            if d is not None:
                max_dist = max(max_dist, d)

    if max_dist <= 50:
        return "A", f"이름 완전일치 + 좌표 {max_dist:.1f}m 이내(동일 시설 확정)"
    if max_dist <= 500:
        return "D", f"이름은 같으나 좌표가 {max_dist:.1f}m 떨어짐 — 분점/별도 시설 가능성, 수동 검토 필요"
    return "C", f"이름은 같으나 좌표가 {max_dist:.1f}m 이상 떨어짐 — 별도 장소(체인/분점)로 판단"


def dedupe_source(source: str) -> dict:
    path = STAGING / source / "부울경.json"
    rows = json.loads(path.read_text(encoding="utf-8"))
    before = len(rows)

    groups = build_name_groups(rows)
    classification_counts = Counter()
    group_details = []
    remove_indices: set[int] = set()

    for name_key, members in groups.items():
        cls, reason = classify_group(source, name_key, members)
        classification_counts[cls] += 1

        detail = {
            "name": members[0][1]["normalized_name"],
            "member_count": len(members),
            "classification": cls,
            "reason": reason,
            "members": [
                {
                    "index": idx,
                    "source_record_id": r.get("source_record_id"),
                    "lot": r.get("normalized_address_lot"),
                    "road": r.get("normalized_address_road"),
                    "lat": r.get("latitude"), "lon": r.get("longitude"),
                }
                for idx, r in members
            ],
        }

        if cls == "A":
            scored = sorted(members, key=lambda im: score_row(im[1]), reverse=True)
            representative_idx = scored[0][0]
            removed = [idx for idx, _ in members if idx != representative_idx]
            detail["representative_index"] = representative_idx
            detail["removed_indices"] = removed
            remove_indices.update(removed)

        group_details.append(detail)

    kept_rows = []
    removed_rows = []
    for i, r in enumerate(rows):
        if i in remove_indices:
            removed_rows.append({"index": i, "source_record_id": r.get("source_record_id"),
                                  "normalized_name": r.get("normalized_name")})
        else:
            kept_rows.append(r)

    path.write_text(json.dumps(kept_rows, ensure_ascii=False, indent=1), encoding="utf-8")

    result = {
        "source": source,
        "before": before,
        "duplicate_name_groups": len(groups),
        "classification_counts": dict(classification_counts),
        "removed_rows": len(removed_rows),
        "final_staging": len(kept_rows),
        "reconcile_ok": before == len(kept_rows) + len(removed_rows),
        "removed_row_details": removed_rows,
        "group_details": group_details,
    }
    return result


def dedupe_busan_sports_confirmed() -> dict:
    """2차 entity-resolution에서 이미 필드 단위로 확정된 2건만 처리한다
    (대저생태공원 파크골프장, 삼락생태공원게이트볼장 — SKEY 외 전 필드 동일).
    올림픽기념 국민생활관/명지레포츠센터는 복합시설 하위시설이라 손대지 않는다."""
    source = "부산공공체육시설"
    path = STAGING / source / "부울경.json"
    rows = json.loads(path.read_text(encoding="utf-8"))
    before = len(rows)

    # (정규화 이름, 지번주소) 완전일치 + 관리기관/시설유형까지 동일한 경우만
    # (실제로 이미 사전 감사에서 확정된 2 그룹) — 이름만으로 재추정하지 않고
    # 사전에 확정된 fid로 직접 지정한다.
    CONFIRMED_DUP_FIDS = {
        "대저생태공원 파크골프장": {
            "keep": "TL_PSF_STUS.fid-2e51d260_19639b5a7c2_6eb3",  # SKEY 223(더 작음)
            "remove": "TL_PSF_STUS.fid-2e51d260_19639b5a7c2_6eb4",  # SKEY 224
        },
        "삼락생태공원게이트볼장": {
            "keep": "TL_PSF_STUS.fid-2e51d260_19639b5a7c2_6ec2",  # SKEY 238
            "remove": "TL_PSF_STUS.fid-2e51d260_19639b5a7c2_6ec3",  # SKEY 239
        },
    }

    remove_ids = {v["remove"] for v in CONFIRMED_DUP_FIDS.values()}
    kept_rows = [r for r in rows if r.get("source_record_id") not in remove_ids]
    removed_rows = [
        {"source_record_id": r.get("source_record_id"), "normalized_name": r.get("normalized_name")}
        for r in rows if r.get("source_record_id") in remove_ids
    ]

    path.write_text(json.dumps(kept_rows, ensure_ascii=False, indent=1), encoding="utf-8")

    return {
        "source": source,
        "before": before,
        "confirmed_duplicate_groups": CONFIRMED_DUP_FIDS,
        "removed_rows": len(removed_rows),
        "final_staging": len(kept_rows),
        "reconcile_ok": before == len(kept_rows) + len(removed_rows),
        "removed_row_details": removed_rows,
        "not_merged_note": "올림픽기념 국민생활관(테니스장/생활체육관), 명지레포츠센터(생활체육관/수영장)는 "
                            "시설유형이 서로 달라 복합시설의 다른 하위시설로 판정 — 병합하지 않음",
    }


def write_report(name: str, data: dict) -> Path:
    REPORTS.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(UTC).strftime("%Y%m%d-%H%M%SZ")
    path = REPORTS / f"{name}-{timestamp}.json"
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def main() -> None:
    results = {}

    for source in ["비짓부산패스", "농어촌체험휴양마을", "박물관미술관"]:
        result = dedupe_source(source)
        results[source] = result
        write_report(f"dedupe-{source}", result)
        print(
            f"{source}: before={result['before']} dup_groups={result['duplicate_name_groups']} "
            f"class={result['classification_counts']} removed={result['removed_rows']} "
            f"final={result['final_staging']} reconcile={result['reconcile_ok']}"
        )

    busan = dedupe_busan_sports_confirmed()
    results["부산공공체육시설"] = busan
    write_report("dedupe-부산공공체육시설", busan)
    print(
        f"부산공공체육시설: before={busan['before']} removed={busan['removed_rows']} "
        f"final={busan['final_staging']} reconcile={busan['reconcile_ok']}"
    )

    summary = {
        "purpose": "P1 원천 내부 중복 정리 — cross-source entity-resolution에서 발견된 확정 중복(A)만 "
                   "실제 staging에서 제거. B/C/D는 플래그만 하고 병합하지 않음. raw는 수정하지 않음",
        "sources": {
            k: {kk: vv for kk, vv in v.items() if kk not in ("removed_row_details", "group_details")}
            for k, v in results.items()
        },
        "generated_at": datetime.now(UTC).strftime("%Y-%m-%d"),
    }
    write_report("dedupe-summary", summary)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
