"""P1 cross-source entity link 후보 생성 (3차) — 검증된 Rule A/B만 실제 staging에 적용해
재현 가능한 link 후보를 만들고 1:1/1:N/N:1/N:N 카디널리티를 감사한다.

실제 Place 레코드는 병합하지 않는다. canonical_place_id도 만들지 않는다 — 이 스크립트의
출력은 "이 두 staging 레코드가 같은 장소일 가능성이 매우 높다"는 후보 목록일 뿐이다.

적용 규칙(2차에서 표본 검토 false positive 0건으로 확인된 것만):
  Rule A: 이름 완전일치 + 좌표 ≤30m
  Rule B: 이름 완전일치 + PRECISE 주소 완전일치(COARSE 제외)
Rule C는 데이터 부족으로 미검증이라 이번 적용에서 제외한다.
AREA_PLACE_REVIEW_REQUIRED(면적형 장소 이름)는 자동 연결 대상에서 제외한다.
"""

from __future__ import annotations

import json
from collections import Counter, defaultdict
from datetime import UTC, datetime
from pathlib import Path

import analyze_cross_source_entity_resolution as e

from data_import.cleaning import haversine_m

REPORTS = e.REPORTS  # data/reports/entity-resolution


def rule_ab_match(a: dict, b: dict) -> tuple[str, float | None, list[str]] | None:
    """Rule A/B만 판정한다(MEDIUM/LOW/Rule C 없음). None이면 매칭 아님."""
    name_exact = a["name_ns"] == b["name_ns"] and a["name_ns"] != ""
    if not name_exact:
        return None

    dist = None
    if a["lat"] is not None and b["lat"] is not None:
        dist = haversine_m(a["lat"], a["lon"], b["lat"], b["lon"])

    if dist is not None and dist <= 30:
        return "A", dist, ["이름완전일치", f"좌표거리={round(dist)}m"]

    any_addr_exact = e.addr_lot_match(a, b) or e.addr_road_match(a, b) or e.addr_any_overlap(a, b)
    precise_addr_exact = any_addr_exact and a["addr_precision"] == "PRECISE" and b["addr_precision"] == "PRECISE"
    if precise_addr_exact:
        signals = ["이름완전일치", "PRECISE주소완전일치"]
        if dist is not None:
            signals.append(f"좌표거리={round(dist)}m")
        return "B", dist, signals

    return None


def generate_pair_links(pair_key: str, left: list[dict], right: list[dict],
                         left_label: str, right_label: str) -> tuple[list[dict], int]:
    right_idx = e.build_blocking_index(right)
    links = []
    area_excluded_count = 0

    for a in left:
        candidates = []
        if a["lat"] is not None:
            for cell in e.candidate_cells(a["lat"], a["lon"]):
                candidates.extend(right_idx.get(cell, []))
        candidates.extend(right_idx.get(("NOCOORD", "NOCOORD"), []))
        if a["lat"] is None:
            for cell, recs in right_idx.items():
                if cell != ("NOCOORD", "NOCOORD"):
                    candidates.extend(recs)

        area_left = e.is_area_name(a["name"])
        seen = set()
        for b in candidates:
            bid = id(b)
            if bid in seen:
                continue
            seen.add(bid)

            m = rule_ab_match(a, b)
            if m is None:
                continue
            rule, dist, signals = m
            area = area_left or e.is_area_name(b["name"])
            if area:
                # 면적형 장소는 자동 연결 대상에서 제외 — link를 만들지 않는다
                area_excluded_count += 1
                continue

            links.append(
                {
                    "pair": pair_key,
                    "left_source": left_label,
                    "left_ref": a["raw_ref"]["ref"],
                    "left_name": a["name"],
                    "left_category": a["category"],
                    "right_source": right_label,
                    "right_ref": b["raw_ref"]["ref"],
                    "right_name": b["name"],
                    "right_category": b["category"],
                    "match_rule": rule,
                    "match_confidence": "HIGH",
                    "name_signal": "이름완전일치",
                    "address_signal": {
                        "left_precision": a["addr_precision"],
                        "right_precision": b["addr_precision"],
                        "matched": "PRECISE" if rule == "B" else None,
                    },
                    "coordinate_distance_m": round(dist, 1) if dist is not None else None,
                    "area_place_excluded": False,
                    "conflict": None,  # annotate_conflicts()가 채운다
                    "conflict_root_cause_hint": None,
                    "signals": signals,
                }
            )

    return links, area_excluded_count


# ---------------------------------------------------------------------------
# 카디널리티 + cluster 감사
# ---------------------------------------------------------------------------


def build_clusters(links: list[dict]) -> dict:
    """union-find로 (source, ref) 노드들의 연결 클러스터를 만든다.
    canonical clustering 알고리즘이 아니라, 몇 개 원천이 한 클러스터에 몰리는지만 센다."""
    parent = {}

    def find(x):
        parent.setdefault(x, x)
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(x, y):
        rx, ry = find(x), find(y)
        if rx != ry:
            parent[rx] = ry

    for link in links:
        lnode = (link["left_source"], link["left_ref"])
        rnode = (link["right_source"], link["right_ref"])
        union(lnode, rnode)

    clusters = defaultdict(set)
    for node in parent:
        clusters[find(node)].add(node)

    size_dist = Counter()
    source_count_dist = Counter()
    three_plus_source_clusters = []
    conflict_clusters = []  # 클러스터 안에 같은 원천 노드가 2개 이상 = 잠재 충돌

    for nodes in clusters.values():
        size_dist[len(nodes)] += 1
        sources_in_cluster = Counter(n[0] for n in nodes)
        source_count_dist[len(sources_in_cluster)] += 1
        if len(sources_in_cluster) >= 3:
            three_plus_source_clusters.append(sorted(nodes))
        if any(c > 1 for c in sources_in_cluster.values()):
            conflict_clusters.append({"nodes": sorted(nodes), "source_counts": dict(sources_in_cluster)})

    return {
        "total_clusters": len(clusters),
        "cluster_size_distribution": dict(size_dist),
        "cluster_source_count_distribution": dict(source_count_dist),
        "three_plus_source_cluster_count": len(three_plus_source_clusters),
        "three_plus_source_cluster_samples": three_plus_source_clusters[:10],
        "conflict_cluster_count": len(conflict_clusters),
        "conflict_cluster_samples": conflict_clusters[:10],
    }


def annotate_conflicts(links: list[dict]) -> None:
    """각 link의 conflict/conflict_root_cause_hint를 실제 카디널리티 기준으로 채운다.

    중요: 카디널리티는 **같은 right_source(또는 같은 left_source) 내에서**만 센다.
    한 left 레코드가 서로 다른 right_source(예: 박물관미술관과 TourAPI 둘 다)에
    한 건씩 정상적으로 연결되는 것은 entity resolution의 정상 결과이지 충돌이
    아니다 — 다른 원천으로 계산하면 진짜 3-source 매칭까지 '1:N 충돌'로
    오분류한다(실제로 이번 패스에서 발견해 수정한 버그).
    진짜 충돌 신호는 "같은 right_source 안에서 서로 다른 레코드 2개 이상에
    연결됨"(그 원천 내부 중복 노출) 하나뿐이다.

    자동 판정은 힌트일 뿐이다 — 시설유형(category)이 갈리면 복합시설/하위시설
    가능성을, 카테고리가 같거나 없으면 원천 내부 중복 가능성을 힌트로만 남기고
    최종 판정(A/B/C/D)은 사람이 한다."""
    # (left_source, left_ref) -> {right_source: [links]}
    left_by_right_source = defaultdict(lambda: defaultdict(list))
    right_by_left_source = defaultdict(lambda: defaultdict(list))
    for link in links:
        lk = (link["left_source"], link["left_ref"])
        rk = (link["right_source"], link["right_ref"])
        left_by_right_source[lk][link["right_source"]].append(link)
        right_by_left_source[rk][link["left_source"]].append(link)

    for link in links:
        lk = (link["left_source"], link["left_ref"])
        rk = (link["right_source"], link["right_ref"])

        right_siblings_same_source = left_by_right_source[lk][link["right_source"]]
        distinct_rights_same_source = {s["right_ref"] for s in right_siblings_same_source}

        left_siblings_same_source = right_by_left_source[rk][link["left_source"]]
        distinct_lefts_same_source = {s["left_ref"] for s in left_siblings_same_source}

        is_1n = len(distinct_rights_same_source) > 1
        is_n1 = len(distinct_lefts_same_source) > 1

        if not is_1n and not is_n1:
            continue  # 1:1, conflict 없음(다른 원천에 추가로 연결된 것은 정상)

        if is_1n and is_n1:
            link["conflict"] = "N:N"
            siblings = right_siblings_same_source + left_siblings_same_source
        elif is_1n:
            link["conflict"] = "1:N"
            siblings = right_siblings_same_source
        else:
            link["conflict"] = "N:1"
            siblings = left_siblings_same_source

        sib_left_categories = {s["left_category"] for s in siblings if s["left_category"]}
        sib_right_categories = {s["right_category"] for s in siblings if s["right_category"]}
        if len(sib_left_categories) > 1 or len(sib_right_categories) > 1:
            link["conflict_root_cause_hint"] = "복합시설_하위시설_가능성(카테고리 상이)"
        else:
            link["conflict_root_cause_hint"] = "원천_내부_중복_가능성(수동확인 필요)"


def write_json(name: str, data) -> Path:
    REPORTS.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(UTC).strftime("%Y%m%d-%H%M%SZ")
    path = REPORTS / f"{name}-{timestamp}.json"
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def main() -> None:
    pair_defs = []

    fishing_port = e.adapt_staging_generic("어항정보")
    marina = e.adapt_staging_generic("마리나")
    pair_defs.append(("fishing-port-vs-marina", "어항정보", "마리나", fishing_port, marina))

    ulsan_culture = e.adapt_staging_generic("울산문화시설", phone_provenance_key="전화번호",
                                             category_provenance_key="구분(원본 분류값 그대로)")
    museum = e.adapt_staging_generic("박물관미술관", phone_provenance_key="전화번호",
                                      category_provenance_key="시설유형")
    pair_defs.append(("ulsan-culture-vs-museum", "울산문화시설", "박물관미술관", ulsan_culture, museum))

    visitbusanpass = e.adapt_staging_generic("비짓부산패스", phone_provenance_key="cntct_tel",
                                              category_provenance_key="tourist_tycd")
    tourapi_vbp = e.adapt_tourapi(["관광지", "문화시설", "음식점"])
    pair_defs.append(("visitbusanpass-vs-tourapi", "비짓부산패스", "TourAPI(관광지+문화시설+음식점)",
                       visitbusanpass, tourapi_vbp))

    heritage = e.adapt_staging_generic("국가유산공간정보", category_provenance_key="종목명")
    tourapi_heritage = e.adapt_tourapi(["관광지", "문화시설"])
    pair_defs.append(("heritage-vs-tourapi", "국가유산공간정보", "TourAPI(관광지+문화시설)",
                       heritage, tourapi_heritage))

    busan_sports = e.adapt_staging_generic("부산공공체육시설", category_provenance_key="시설유형")
    national_sports_raw = e.adapt_national_sports_facility()
    pair_defs.append(("busan-sports-vs-national-sports-raw", "부산공공체육시설", "국민체육진흥공단_전국체육시설(raw)",
                       busan_sports, national_sports_raw))

    # 국민체육진흥공단이 이번에 정식 staging으로 승격됐다 — staging 기준으로도 비교한다
    national_sports_staged = e.adapt_staging_generic(
        "국민체육진흥공단전국체육시설", phone_provenance_key="전화번호", category_provenance_key="시설유형"
    )
    gyeongnam_sports = e.adapt_staging_generic("경남공공체육시설", category_provenance_key="시설종류")
    tourapi_sports = e.adapt_tourapi(["레포츠"])

    pair_defs.append(("busan-sports-vs-national-sports-staged", "부산공공체육시설", "국민체육진흥공단전국체육시설",
                       busan_sports, national_sports_staged))
    pair_defs.append(("gyeongnam-sports-vs-national-sports-staged", "경남공공체육시설", "국민체육진흥공단전국체육시설",
                       gyeongnam_sports, national_sports_staged))
    pair_defs.append(("national-sports-staged-vs-tourapi-reports", "국민체육진흥공단전국체육시설", "TourAPI(레포츠)",
                       national_sports_staged, tourapi_sports))

    tourapi_general = e.adapt_tourapi(["관광지", "문화시설", "레포츠"])
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
        left = e.adapt_staging_generic(source, phone_provenance_key=phone_key, category_provenance_key=cat_key)
        pair_defs.append((f"{source}-vs-tourapi", source, "TourAPI(관광지+문화시설+레포츠)", left, tourapi_general))

    # --- 이번 패스에서 신규 staging한 §1 확장 후보 중 기존/신규 모집단과
    # 겹칠 개연성이 있는 것만 선택적으로 추가한다(전수 조합 금지, Rule A/B 자체는 불변) ---
    tourapi_shopping = e.adapt_tourapi(["쇼핑"])
    jeontong_sijang = e.adapt_staging_generic("전통시장", phone_provenance_key="전화번호", category_provenance_key="시장유형")
    pair_defs.append(("jeontongsijang-vs-tourapi-shopping", "전통시장", "TourAPI(쇼핑)", jeontong_sijang, tourapi_shopping))

    localdata_museum = e.adapt_staging_generic("인허가박물관미술관", phone_provenance_key="phone")
    standard_museum = e.adapt_staging_generic("박물관미술관", phone_provenance_key="전화번호", category_provenance_key="시설유형")
    tourapi_culture = e.adapt_tourapi(["문화시설"])
    pair_defs.append(("localdata-museum-vs-standard-museum", "인허가박물관미술관", "박물관미술관", localdata_museum, standard_museum))
    pair_defs.append(("localdata-museum-vs-tourapi-culture", "인허가박물관미술관", "TourAPI(문화시설)", localdata_museum, tourapi_culture))

    gongyeonjang = e.adapt_staging_generic("공연장", phone_provenance_key="phone")
    pair_defs.append(("gongyeonjang-vs-tourapi-culture", "공연장", "TourAPI(문화시설)", gongyeonjang, tourapi_culture))

    cheyuk_siseol = e.adapt_staging_generic("체육시설업")
    pair_defs.append(("cheyuksiseol-vs-busan-sports", "체육시설업", "부산공공체육시설", cheyuk_siseol, busan_sports))
    pair_defs.append(("cheyuksiseol-vs-gyeongnam-sports", "체육시설업", "경남공공체육시설", cheyuk_siseol, gyeongnam_sports))
    pair_defs.append(("cheyuksiseol-vs-national-sports-staged", "체육시설업", "국민체육진흥공단전국체육시설", cheyuk_siseol, national_sports_staged))

    yayoungjang = e.adapt_staging_generic("야영장")
    tema_park = e.adapt_staging_generic("테마파크")
    pair_defs.append(("yayoungjang-vs-tourapi-reports", "야영장", "TourAPI(레포츠)", yayoungjang, tourapi_sports))
    tourapi_spot = e.adapt_tourapi(["관광지"])
    pair_defs.append(("temapark-vs-tourapi-spot", "테마파크", "TourAPI(관광지)", tema_park, tourapi_spot))

    all_links = []
    per_pair_summary = []
    pair_links_map = {}
    total_area_excluded = 0
    for key, left_label, right_label, left, right in pair_defs:
        links, area_excluded = generate_pair_links(key, left, right, left_label, right_label)
        pair_links_map[key] = links
        all_links.extend(links)
        total_area_excluded += area_excluded

    annotate_conflicts(all_links)  # 전체 링크 그래프 기준으로 한 번에 주석

    for key, left_label, right_label, left, right in pair_defs:
        links = pair_links_map[key]
        rule_counts = Counter(link["match_rule"] for link in links)
        conflict_counts = Counter(link["conflict"] or "NONE" for link in links)
        per_pair_summary.append({
            "pair": key, "link_count": len(links),
            "rule_counts": dict(rule_counts), "conflict_counts": dict(conflict_counts),
        })
        path = write_json(f"entity-links-{key}", links)
        print(f"{key}: links={len(links)} rules={dict(rule_counts)} conflicts={dict(conflict_counts)} -> {path.name}")

    # --- 카디널리티는 annotate_conflicts가 채운 conflict 필드에서 직접 집계한다
    # (같은 right_source/left_source 내부에서만 충돌로 본다 — 다른 원천에 정상적으로
    # 추가 연결된 것까지 충돌로 잘못 세는 문제를 여기서 고쳤다) ---
    def cardinality_from_links(link_list: list[dict]) -> dict:
        counts = Counter()
        for link in link_list:
            counts[link["conflict"] or "1:1"] += 1
        return dict(counts)

    global_cardinality = {"cardinality_counts": cardinality_from_links(all_links)}
    global_clusters = build_clusters(all_links)

    per_pair_cardinality = {}
    for key, *_ in pair_defs:
        pair_links = [link for link in all_links if link["pair"] == key]
        if pair_links:
            per_pair_cardinality[key] = cardinality_from_links(pair_links)

    summary = {
        "purpose": "P1 cross-source entity link 후보 생성(3차) — Rule A/B만 적용, "
                   "canonical 병합/ID 생성 없이 link 후보와 카디널리티만 감사",
        "rule_scope": "Rule A(이름+좌표30m) / Rule B(이름+PRECISE주소완전일치)만 적용. "
                      "Rule C(주소+전화) 제외, COARSE 주소는 Rule B에 쓰지 않음, "
                      "면적형 장소 이름은 자동 연결 대상에서 제외",
        "pairs": per_pair_summary,
        "total_links": len(all_links),
        "global_cardinality": global_cardinality,
        "per_pair_cardinality": per_pair_cardinality,
        "cluster_analysis": global_clusters,
        "area_place_excluded_from_links": total_area_excluded,
        "generated_at": datetime.now(UTC).strftime("%Y-%m-%d"),
    }
    write_json("entity-links-summary", summary)
    print(json.dumps({k: v for k, v in summary.items() if k != "pairs"}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
