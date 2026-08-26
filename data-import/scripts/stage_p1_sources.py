from __future__ import annotations

import csv
import json
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass, field
from datetime import UTC, datetime
from pathlib import Path

from data_import.cleaning import (
    check_coordinate,
    epsg5174_to_wgs84,
    haversine_m,
    lot_addr_key,
    norm_nospace,
    normalize_status,
    normalize_str,
    to_float,
)

# ---------------------------------------------------------------------------
# 원천 식별자 사용 원칙 (3차 정제, 2026-08 확정)
#
# 원천 식별자는 원천별 고유성 검증 없이 단독 dedupe key로 사용하지 않는다.
# 모든 원천 ID가 신뢰 불가라는 뜻이 아니다 — 실제로 단독 사용이 불가한 것으로
# "검증된" 원천만 아래 표에 기록한다. 다른 원천 ID는 각 원천별 검증 결과를 따른다.
#
#   관광숙박업 관리번호: 지역 내부에서도 최대 7개 서로 다른 시설이 하나의
#     관리번호를 공유한다(2차 검증 189그룹 실측). 자릿수 패턴이 "등록연도별
#     배치 코드"에 가까워 시설 단위 ID가 아니다. => dedupe key로 사용 금지.
#     대신 source_identifier_collision_id/count로 "ID가 겹친다"는 사실만 기록한다.
#   주차장관리번호: 감사에서 서로 다른 두 시설(노동자종합복지회관/문수체육관)이
#     같은 관리번호를 공유하는 사례가 확인됐다. => dedupe key로 사용 금지.
#     주차장은 이름+지번주소 조합을 key로 쓰고, 관리번호는 같은 name+addr
#     그룹 안에서 "동일 시설의 갱신 차이"를 뒷받침하는 보조 신호로만 쓴다.
# ---------------------------------------------------------------------------
UNRELIABLE_STANDALONE_IDS = {
    "관광숙박업": "관리번호",
    "주차장": "주차장관리번호",
}

PROJECT_ROOT = Path(__file__).resolve().parent.parent
RAW = PROJECT_ROOT / "data/raw"
STAGING = PROJECT_ROOT / "data/staging"
REPORTS = PROJECT_ROOT / "data/reports/cleaning"


@dataclass
class SourceReport:
    source: str
    raw_input_count: int = 0
    active: int = 0
    closed: int = 0
    suspended: int = 0
    unknown: int = 0
    status_excluded: int = 0
    non_place_excluded: int = 0
    included_in_staging: int = 0
    coord_valid: int = 0
    coord_missing: int = 0
    coord_zero: int = 0
    coord_out_of_region: int = 0
    duplicate_flagged_groups: int = 0
    duplicate_flagged_records: int = 0
    source_identifier_collision_groups: int = 0
    source_identifier_collision_records: int = 0
    included_before_dedupe: int = 0
    deduped_records_removed: int = 0
    final_included_after_dedupe: int = 0
    status_enrichment_candidate_count: int = 0
    status_conflict_count: int = 0
    original_status_values: dict = field(default_factory=dict)
    notes: list[str] = field(default_factory=list)

    def reconcile_ok(self) -> bool:
        return (
            self.raw_input_count
            == self.included_in_staging
            + self.status_excluded
            + self.non_place_excluded
            + self.deduped_records_removed
        )

    def dedupe_reconcile_ok(self) -> bool:
        return (
            self.included_before_dedupe - self.deduped_records_removed
            == self.final_included_after_dedupe
        )


def make_record(
    *,
    source: str,
    source_record_id: str | None,
    original_status: str | None,
    normalized_status: str,
    normalized_name: str | None,
    normalized_address_lot: str | None,
    normalized_address_road: str | None,
    latitude: float | None,
    longitude: float | None,
    coordinate_status: str,
    exclusion_reason: str | None,
    duplicate_group_id: str | None,
    duplicate_count: int,
    manual_review_required: bool,
    provenance: dict,
    source_identifier_collision_id: str | None = None,
    source_identifier_collision_count: int = 1,
    status_enrichment_candidate: str | None = None,
    status_link_confidence: str | None = None,
    status_conflict_flag: bool | None = None,
    linked_source: str | None = None,
    dedupe_status: str | None = None,
) -> dict:
    return {
        "source": source,
        "source_record_id": source_record_id,
        "original_status": original_status,
        "normalized_status": normalized_status,
        "normalized_name": normalized_name,
        "normalized_address_lot": normalized_address_lot,
        "normalized_address_road": normalized_address_road,
        "latitude": latitude,
        "longitude": longitude,
        "coordinate_status": coordinate_status,
        "exclusion_reason": exclusion_reason,
        "duplicate_group_id": duplicate_group_id,
        "duplicate_count": duplicate_count,
        "manual_review_required": manual_review_required,
        # 원천 ID가 여러 서로 다른 대상 간에 겹치는 현상(관리번호 등) — 실제
        # 중복이 아니라 "ID 신뢰 불가"를 뜻한다. duplicate_group_id와 의미가
        # 다르므로 별도 필드로 분리한다.
        "source_identifier_collision_id": source_identifier_collision_id,
        "source_identifier_collision_count": source_identifier_collision_count,
        # 다른 원천에서 관찰된 상태 사실(제안값) — normalized_status를
        # 덮어쓰지 않고 참고용으로만 보존한다.
        "status_enrichment_candidate": status_enrichment_candidate,
        "status_link_confidence": status_link_confidence,
        "status_conflict_flag": status_conflict_flag,
        "linked_source": linked_source,
        # "REPRESENTATIVE" | "DEDUPED_DUPLICATE" | None(dedupe 대상 아님)
        "dedupe_status": dedupe_status,
        "provenance": provenance,
    }


def write_report(name: str, report: SourceReport) -> Path:
    REPORTS.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(UTC).strftime("%Y%m%d-%H%M%SZ")
    path = REPORTS / f"staging-{name}-{timestamp}.json"
    data = asdict(report)
    data["reconcile_ok"] = report.reconcile_ok()
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def write_staging(name: str, region: str, records: list[dict]) -> Path:
    out_dir = STAGING / name
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / f"{region}.json"
    path.write_text(json.dumps(records, ensure_ascii=False, indent=1), encoding="utf-8")
    return path


# ---------------------------------------------------------------------------
# LOCALDATA 인허가류 (일반음식점/휴게음식점/제과점/숙박업/관광숙박업)
# ---------------------------------------------------------------------------

LOCALDATA_SOURCES = {
    "일반음식점": {
        "regions": {
            "부산광역시": "data/raw/standard/일반음식점/식품_일반음식점_부산광역시.csv",
            "울산광역시": "data/raw/standard/일반음식점/식품_일반음식점_울산광역시.csv",
            "경상남도": "data/raw/standard/일반음식점/식품_일반음식점_경상남도.csv",
        },
    },
    "휴게음식점": {
        "regions": {
            "부산광역시": "data/raw/standard/휴게음식점/식품_휴게음식점_부산광역시.csv",
            "울산광역시": "data/raw/standard/휴게음식점/식품_휴게음식점_울산광역시.csv",
            "경상남도": "data/raw/standard/휴게음식점/식품_휴게음식점_경상남도.csv",
        },
    },
    "제과점": {
        "regions": {
            "부산광역시": "data/raw/standard/제과점/식품_제과점영업_부산광역시.csv",
            "울산광역시": "data/raw/standard/제과점/식품_제과점영업_울산광역시.csv",
            "경상남도": "data/raw/standard/제과점/식품_제과점영업_경상남도.csv",
        },
    },
    "숙박업": {
        "regions": {
            "부산광역시": "data/raw/standard/숙박업/문화_숙박업_부산광역시.csv",
            "울산광역시": "data/raw/standard/숙박업/문화_숙박업_울산광역시.csv",
            "경상남도": "data/raw/standard/숙박업/문화_숙박업_경상남도.csv",
        },
    },
    "관광숙박업": {
        "regions": {
            "부산광역시": "data/raw/standard/숙박업/문화_관광숙박업_부산광역시.csv",
            "울산광역시": "data/raw/standard/숙박업/문화_관광숙박업_울산광역시.csv",
            "경상남도": "data/raw/standard/숙박업/문화_관광숙박업_경상남도.csv",
        },
    },
}


def process_localdata(name: str, regions: dict[str, str]) -> None:
    report = SourceReport(source=name)
    status_values: Counter[str] = Counter()

    for region, rel_path in regions.items():
        path = PROJECT_ROOT / rel_path
        with path.open(encoding="cp949", errors="replace", newline="") as f:
            reader = csv.DictReader(f)
            rows = list(reader)

        # 원천 내부 중복 후보 (관리번호 기준) — 자동 병합하지 않고 플래그만 만든다
        id_counter: Counter[str] = Counter(
            (row.get("관리번호") or "").strip() for row in rows
        )

        records: list[dict] = []
        for row in rows:
            report.raw_input_count += 1
            mgmt_id = normalize_str(row.get("관리번호"))
            original_status = row.get("상세영업상태명") or row.get("영업상태명")
            normalized_status, original_status_clean = normalize_status(original_status)
            status_values[original_status_clean or "(빈값)"] += 1

            if normalized_status == "ACTIVE":
                report.active += 1
            elif normalized_status == "CLOSED":
                report.closed += 1
            elif normalized_status == "SUSPENDED":
                report.suspended += 1
            else:
                report.unknown += 1

            exclusion_reason = None
            if normalized_status in ("CLOSED", "SUSPENDED"):
                exclusion_reason = f"STATUS_{normalized_status}"
                report.status_excluded += 1
            else:
                report.included_in_staging += 1

            x = to_float(row.get("좌표정보(X)"))
            y = to_float(row.get("좌표정보(Y)"))
            lat, lon = epsg5174_to_wgs84(x, y)
            coord_status = check_coordinate(lat, lon)
            if coord_status == "VALID":
                report.coord_valid += 1
            elif coord_status == "MISSING":
                report.coord_missing += 1
            elif coord_status == "ZERO":
                report.coord_zero += 1
            elif coord_status == "OUT_OF_REGION":
                report.coord_out_of_region += 1

            dup_count = id_counter.get(mgmt_id or "", 0)
            duplicate_group_id = None
            manual_review = False
            collision_id = None
            collision_count = 1

            if name in UNRELIABLE_STANDALONE_IDS:
                # 2차 검증 결과 관광숙박업 관리번호는 시설 단위 ID가 아니라
                # 서로 다른 시설 간에도 공유되는 값임이 확인됐다. duplicate_group_id로
                # 표현하면 "실제 중복"처럼 읽히므로, ID가 겹친다는 사실만
                # source_identifier_collision_*로 분리해 남긴다. 병합/대표행
                # 선택은 하지 않는다(모든 레코드를 개별 시설로 유지).
                if mgmt_id and dup_count > 1:
                    collision_id = f"{name}:{region}:{mgmt_id}"
                    collision_count = dup_count
            elif mgmt_id and dup_count > 1:
                duplicate_group_id = f"{name}:{region}:{mgmt_id}"
                manual_review = True

            records.append(
                make_record(
                    source=name,
                    source_record_id=mgmt_id,
                    original_status=original_status_clean,
                    normalized_status=normalized_status,
                    normalized_name=normalize_str(row.get("사업장명")),
                    normalized_address_lot=normalize_str(row.get("지번주소")),
                    normalized_address_road=normalize_str(row.get("도로명주소")),
                    latitude=lat,
                    longitude=lon,
                    coordinate_status=coord_status,
                    exclusion_reason=exclusion_reason,
                    duplicate_group_id=duplicate_group_id,
                    duplicate_count=dup_count,
                    manual_review_required=manual_review,
                    source_identifier_collision_id=collision_id,
                    source_identifier_collision_count=collision_count,
                    provenance={
                        "raw_file": rel_path,
                        "region": region,
                        "coord_x_epsg5174": x,
                        "coord_y_epsg5174": y,
                        "phone": normalize_str(row.get("전화번호")),
                    },
                )
            )

        dup_groups = {r["duplicate_group_id"] for r in records if r["duplicate_group_id"]}
        report.duplicate_flagged_groups += len(dup_groups)
        report.duplicate_flagged_records += sum(1 for r in records if r["duplicate_group_id"])
        collision_groups = {r["source_identifier_collision_id"] for r in records if r["source_identifier_collision_id"]}
        report.source_identifier_collision_groups += len(collision_groups)
        report.source_identifier_collision_records += sum(
            1 for r in records if r["source_identifier_collision_id"]
        )

        write_staging(name, region, records)

    report.original_status_values = dict(status_values.most_common())
    report.included_before_dedupe = report.included_in_staging
    report.final_included_after_dedupe = report.included_in_staging - report.deduped_records_removed
    if name in UNRELIABLE_STANDALONE_IDS:
        report.notes.append(
            f"{UNRELIABLE_STANDALONE_IDS[name]}는 시설 단위 고유 ID로 신뢰하지 않는다(2차 검증 확인). "
            "ID 겹침은 source_identifier_collision_*로만 표시했고, 병합/대표행 선택을 하지 않았다 — "
            f"{report.source_identifier_collision_records}건이 {report.source_identifier_collision_groups}개 "
            "그룹에서 ID가 겹치지만 전부 개별 시설로 staging에 유지된다."
        )
    write_report(name, report)
    print(f"{name}: raw={report.raw_input_count} included={report.included_in_staging} "
          f"closed_excl={report.status_excluded} reconcile={report.reconcile_ok()} "
          f"id_collision_groups={report.source_identifier_collision_groups}")


# ---------------------------------------------------------------------------
# 상가정보 ↔ LOCALDATA 고신뢰 연결(정규화 이름+지번주소 완전일치) — 상태 보강 후보용
# ---------------------------------------------------------------------------

def build_localdata_linkage_index() -> dict[tuple[str, str], dict]:
    """(정규화 이름-무공백, 지번주소 절단키) -> 매칭 후보 1건.

    2차 검증에서 확정된 고신뢰 기준(이름+지번주소 완전일치)만 사용한다.
    중신뢰(이름+도로명주소)는 이번 패스에서 상태 보강 대상이 아니므로 색인하지 않는다.
    """
    index: dict[tuple[str, str], dict] = {}
    for source_name, cfg in LOCALDATA_SOURCES.items():
        for region, rel_path in cfg["regions"].items():
            path = PROJECT_ROOT / rel_path
            with path.open(encoding="cp949", errors="replace", newline="") as f:
                reader = csv.DictReader(f)
                for row in reader:
                    raw_name = row.get("사업장명")
                    lot = lot_addr_key(row.get("지번주소"))
                    if not raw_name or not lot:
                        continue
                    key = (norm_nospace(raw_name), lot)
                    if key in index:
                        continue  # 첫 매칭만 사용(상세 값 산출용이 아니라 상태 관찰용)
                    status_raw = row.get("상세영업상태명") or row.get("영업상태명")
                    normalized_status, status_clean = normalize_status(status_raw)
                    index[key] = {
                        "source": source_name,
                        "region": region,
                        "status_enrichment_candidate": normalized_status,
                        "status_raw": status_clean,
                    }
    return index


def process_store_info() -> None:
    name = "상가정보"
    report = SourceReport(source=name)
    regions = {
        "부산": "data/raw/standard/상가정보/소상공인시장진흥공단_상가(상권)정보_부산_202606.csv",
        "울산": "data/raw/standard/상가정보/소상공인시장진흥공단_상가(상권)정보_울산_202606.csv",
        "경남": "data/raw/standard/상가정보/소상공인시장진흥공단_상가(상권)정보_경남_202606.csv",
    }

    linkage_index = build_localdata_linkage_index()

    for region, rel_path in regions.items():
        path = PROJECT_ROOT / rel_path
        with path.open(encoding="utf-8", errors="replace", newline="") as f:
            reader = csv.DictReader(f)
            rows = list(reader)

        records = []
        for row in rows:
            report.raw_input_count += 1
            # 상가정보에는 영업상태 컬럼이 없다 — normalized_status는 이번 패스에서도
            # 계속 UNKNOWN으로 유지한다(고신뢰 매칭·ACTIVE 매칭이어도 덮어쓰지 않는다).
            normalized_status = "UNKNOWN"
            report.unknown += 1
            report.included_in_staging += 1

            lat = to_float(row.get("위도"))
            lon = to_float(row.get("경도"))
            coord_status = check_coordinate(lat, lon)
            if coord_status == "VALID":
                report.coord_valid += 1
            elif coord_status == "MISSING":
                report.coord_missing += 1
            elif coord_status == "ZERO":
                report.coord_zero += 1
            elif coord_status == "OUT_OF_REGION":
                report.coord_out_of_region += 1

            name_ns = norm_nospace(row.get("상호명"))
            lot_key = lot_addr_key(row.get("지번주소"))
            match = linkage_index.get((name_ns, lot_key)) if lot_key else None

            status_enrichment_candidate = None
            status_link_confidence = None
            status_conflict_flag = None
            linked_source = None
            if match:
                status_enrichment_candidate = match["status_enrichment_candidate"]
                status_link_confidence = "HIGH"
                linked_source = match["source"]
                status_conflict_flag = status_enrichment_candidate == "CLOSED"
                report.status_enrichment_candidate_count += 1
                if status_conflict_flag:
                    report.status_conflict_count += 1

            records.append(
                make_record(
                    source=name,
                    source_record_id=normalize_str(row.get("상가업소번호")),
                    original_status=None,
                    normalized_status=normalized_status,
                    normalized_name=normalize_str(row.get("상호명")),
                    normalized_address_lot=normalize_str(row.get("지번주소")),
                    normalized_address_road=normalize_str(row.get("도로명주소")),
                    latitude=lat,
                    longitude=lon,
                    coordinate_status=coord_status,
                    exclusion_reason=None,
                    duplicate_group_id=None,
                    duplicate_count=1,
                    manual_review_required=False,
                    status_enrichment_candidate=status_enrichment_candidate,
                    status_link_confidence=status_link_confidence,
                    status_conflict_flag=status_conflict_flag,
                    linked_source=linked_source,
                    provenance={"raw_file": rel_path, "region": region},
                )
            )

        write_staging(name, region, records)

    report.original_status_values = {"(컬럼 없음)": report.raw_input_count}
    report.included_before_dedupe = report.included_in_staging
    report.final_included_after_dedupe = report.included_in_staging
    report.notes.append(
        "상가정보 원천에는 영업상태 컬럼이 없어 normalized_status는 전량 UNKNOWN을 유지했다. "
        f"LOCALDATA 5종과 이름+지번주소 완전일치(고신뢰)로 연결된 {report.status_enrichment_candidate_count}건은 "
        "status_enrichment_candidate로 관찰 상태만 부기했고 normalized_status는 덮어쓰지 않았다. "
        f"그중 LOCALDATA가 CLOSED인 {report.status_conflict_count}건은 status_conflict_flag=true로 "
        "표시만 하고 서비스 제외/폐업 처리는 하지 않았다(개업일 정보 부재로 판단 불가)."
    )
    write_report(name, report)
    print(f"{name}: raw={report.raw_input_count} included={report.included_in_staging} "
          f"reconcile={report.reconcile_ok()} enrichment_candidates={report.status_enrichment_candidate_count} "
          f"conflicts={report.status_conflict_count}")


# ---------------------------------------------------------------------------
# TourAPI 목록
# ---------------------------------------------------------------------------

TOURAPI_TYPES = {
    "12": "관광지",
    "14": "문화시설",
    "15": "축제공연행사",
    "25": "여행코스",
    "28": "레포츠",
    "32": "숙박",
    "38": "쇼핑",
    "39": "음식점",
}


def process_tourapi() -> None:
    name = "TourAPI목록"
    report = SourceReport(source=name)

    for type_id, type_name in TOURAPI_TYPES.items():
        dir_path = RAW / "tourism/tour-api" / f"{type_id}_{type_name}"
        items = []
        for page_file in sorted(dir_path.glob("page-*.json")):
            data = json.loads(page_file.read_text(encoding="utf-8"))
            page_items = data.get("response", {}).get("body", {}).get("items", {}).get("item", [])
            if isinstance(page_items, dict):
                page_items = [page_items]
            items.extend(page_items)

        buul = [it for it in items if it.get("lDongRegnCd") in ("26", "31", "48")]

        records = []
        for it in buul:
            report.raw_input_count += 1
            # TourAPI 목록 응답에는 상태 필드가 없다 — 항상 UNKNOWN
            report.unknown += 1

            is_non_place = type_id == "25"
            exclusion_reason = None
            if is_non_place:
                exclusion_reason = "NON_PLACE_RECORD"
                report.non_place_excluded += 1
            else:
                report.included_in_staging += 1

            lat = to_float(it.get("mapy"))
            lon = to_float(it.get("mapx"))
            coord_status = check_coordinate(lat, lon)
            if coord_status == "VALID":
                report.coord_valid += 1
            elif coord_status == "MISSING":
                report.coord_missing += 1
            elif coord_status == "ZERO":
                report.coord_zero += 1
            elif coord_status == "OUT_OF_REGION":
                report.coord_out_of_region += 1

            records.append(
                make_record(
                    source=f"TourAPI:{type_name}",
                    source_record_id=str(it.get("contentid")),
                    original_status=None,
                    normalized_status="UNKNOWN",
                    normalized_name=normalize_str(it.get("title")),
                    normalized_address_lot=None,
                    normalized_address_road=normalize_str(
                        f"{it.get('addr1', '')} {it.get('addr2', '')}".strip()
                    )
                    or None,
                    latitude=lat,
                    longitude=lon,
                    coordinate_status=coord_status,
                    exclusion_reason=exclusion_reason,
                    duplicate_group_id=None,
                    duplicate_count=1,
                    manual_review_required=False,
                    provenance={
                        "content_type_id": type_id,
                        "content_type_name": type_name,
                        "raw_dir": str(dir_path.relative_to(PROJECT_ROOT)),
                    },
                )
            )

        write_staging(name, type_name, records)

    report.original_status_values = {"(컬럼 없음)": report.raw_input_count}
    report.included_before_dedupe = report.included_in_staging
    report.final_included_after_dedupe = report.included_in_staging
    report.notes.append(
        "TourAPI 목록 응답에는 상태 필드가 없어 전량 UNKNOWN으로 정규화했다. "
        "contentTypeId=25(여행코스)는 addr1 결측률 93%로 지점이 아니라 코스 단위라 "
        "NON_PLACE_RECORD로 staging에서 제외했다(raw는 보존)"
    )
    write_report(name, report)
    print(f"{name}: raw={report.raw_input_count} included={report.included_in_staging} "
          f"non_place_excl={report.non_place_excluded} reconcile={report.reconcile_ok()}")


# ---------------------------------------------------------------------------
# 주차장 표준데이터
# ---------------------------------------------------------------------------

PARKING_DETAIL_FIELDS = ["요금정보", "운영요일", "특기사항"]


def _parking_score(row: dict) -> tuple:
    lot = normalize_str(row.get("소재지지번주소"))
    road = normalize_str(row.get("소재지도로명주소"))
    phone = normalize_str(row.get("전화번호"))
    addr_fill = (1 if lot else 0) + (1 if road else 0)
    has_road = 1 if road else 0
    has_phone = 1 if phone else 0
    detail_fill = sum(1 for f in PARKING_DETAIL_FIELDS if normalize_str(row.get(f)))
    date_str = normalize_str(row.get("데이터기준일자")) or ""
    return (addr_fill, has_road, has_phone, detail_fill, date_str)


def select_parking_representative(rows: list[dict]) -> tuple[dict | None, bool]:
    """대표행 후보 선정: 주소 채움률 > 도로명주소 존재 > 전화번호 존재 >
    상세필드 채움률 > 기준일 최신성 순으로 비교한다. 단일 첫/마지막 행 선택
    금지, 사실값을 합친 synthetic record 생성 금지 — 우선순위 전부를
    비교했을 때 유일한 최고점 행이 있을 때만 대표행으로 선택한다."""
    scored = [(_parking_score(r), r) for r in rows]
    best_score = max(s for s, _ in scored)
    winners = [r for s, r in scored if s == best_score]
    if len(winners) == 1:
        return winners[0], False
    return None, True  # 동점 — 자동 선택 불가, 수동 검토


def process_parking() -> None:
    name = "주차장"
    report = SourceReport(source=name)
    path = RAW / "standard/주차장/전국주차장정보표준데이터.csv"

    with path.open(encoding="cp949", errors="replace", newline="") as f:
        reader = csv.DictReader(f)
        rows = list(reader)

    buul = [
        row
        for row in rows
        if (row.get("소재지도로명주소") or row.get("소재지지번주소") or "").startswith(
            ("부산", "울산", "경남", "경상남도")
        )
    ]

    # 관리번호가 신뢰 가능한 고유 ID가 아님이 확인됐다(감사·2차 검증에서 서로 다른
    # 시설 간에도 관리번호가 공유되는 사례 발견) — 이름+지번주소 조합을 그룹 key로
    # 쓰고, 관리번호는 "같은 name+addr 그룹 안에서 동일 시설임을 뒷받침하는 보조
    # 신호"로만 쓴다(그룹을 짓는 key로는 쓰지 않는다).
    groups: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for row in buul:
        key = (
            normalize_str(row.get("주차장명")) or "",
            normalize_str(row.get("소재지지번주소")) or "",
        )
        if key != ("", ""):
            groups[key].append(row)

    # 각 그룹의 대표행/중복 판정 결과를 row id(파이썬 객체 id)로 미리 계산해둔다.
    row_dedupe_status: dict[int, str] = {}
    row_manual_review: dict[int, bool] = {}
    row_classification: dict[int, str] = {}
    row_group_key: dict[int, tuple[str, str]] = {}
    classification_counts: Counter[str] = Counter()

    for key, grp_rows in groups.items():
        if len(grp_rows) < 2:
            continue

        coords = []
        for r in grp_rows:
            lat, lon = to_float(r.get("위도")), to_float(r.get("경도"))
            if lat is not None and lon is not None:
                coords.append((lat, lon))
        max_dist = 0.0
        for i in range(len(coords)):
            for j in range(i + 1, len(coords)):
                d = haversine_m(*coords[i], *coords[j])
                if d is not None:
                    max_dist = max(max_dist, d)

        capacities = {normalize_str(r.get("주차구획수")) for r in grp_rows}
        mgmt_ids = {normalize_str(r.get("주차장관리번호")) for r in grp_rows}
        same_location = max_dist < 30
        same_capacity = len(capacities) == 1

        if not same_location:
            # 이름+지번주소가 같은데 좌표가 30m 이상 떨어짐 — 그룹 key 매칭 자체가
            # 의심스러운 경우. 자동 처리하지 않고 전부 수동 검토로 남긴다.
            classification = "D_PRIME"
        elif same_capacity:
            classification = "A"
        elif len(mgmt_ids) == 1:
            # 2차 검증에서 실측: 좌표 동일 + 관리번호 동일 + 구획수 등 부가필드만
            # 다름 = 같은 시설을 서로 다른 제공기관이 다른 시점에 갱신한 값이다.
            classification = "A_PRIME"
        else:
            # 좌표는 같으나 관리번호까지 다르면 이름+주소가 우연히 겹치는 별도
            # 시설일 가능성을 배제할 수 없다 — 자동 dedupe 대상에서 제외한다.
            classification = "B_PRIME"

        classification_counts[classification] += 1
        for r in grp_rows:
            row_classification[id(r)] = classification
            row_group_key[id(r)] = key

        if classification in ("A", "A_PRIME"):
            winner, tie = select_parking_representative(grp_rows)
            if tie:
                for r in grp_rows:
                    row_manual_review[id(r)] = True
            else:
                for r in grp_rows:
                    if r is winner:
                        row_dedupe_status[id(r)] = "REPRESENTATIVE"
                    else:
                        row_dedupe_status[id(r)] = "DEDUPED_DUPLICATE"
        else:
            for r in grp_rows:
                row_manual_review[id(r)] = True

    records = []
    for row in buul:
        report.raw_input_count += 1
        report.unknown += 1

        lat = to_float(row.get("위도"))
        lon = to_float(row.get("경도"))
        coord_status = check_coordinate(lat, lon)
        if coord_status == "VALID":
            report.coord_valid += 1
        elif coord_status == "MISSING":
            report.coord_missing += 1
        elif coord_status == "ZERO":
            report.coord_zero += 1
        elif coord_status == "OUT_OF_REGION":
            report.coord_out_of_region += 1

        name_norm = normalize_str(row.get("주차장명"))
        lot_addr = normalize_str(row.get("소재지지번주소"))
        key = (name_norm or "", lot_addr or "")
        grp_rows = groups.get(key, [])
        dup_count = len(grp_rows)

        duplicate_group_id = None
        manual_review = row_manual_review.get(id(row), False)
        dedupe_status = row_dedupe_status.get(id(row))
        classification = row_classification.get(id(row))
        if dup_count > 1:
            duplicate_group_id = f"parking:name_addr:{name_norm}:{lot_addr}"

        exclusion_reason = None
        if dedupe_status == "DEDUPED_DUPLICATE":
            exclusion_reason = "DEDUPE_REPRESENTATIVE_SELECTED"
            report.deduped_records_removed += 1
        else:
            report.included_in_staging += 1

        records.append(
            make_record(
                source=name,
                source_record_id=normalize_str(row.get("주차장관리번호")),
                original_status=None,
                normalized_status="UNKNOWN",
                normalized_name=name_norm,
                normalized_address_lot=lot_addr,
                normalized_address_road=normalize_str(row.get("소재지도로명주소")),
                latitude=lat,
                longitude=lon,
                coordinate_status=coord_status,
                exclusion_reason=exclusion_reason,
                duplicate_group_id=duplicate_group_id,
                duplicate_count=dup_count,
                manual_review_required=manual_review,
                dedupe_status=dedupe_status,
                provenance={
                    "raw_file": "data/raw/standard/주차장/전국주차장정보표준데이터.csv",
                    "mgmt_id_reliable": False,
                    "mgmt_id_note": "주차장관리번호는 감사에서 서로 다른 시설 간 중복 공유가 확인돼 단독 dedupe key로 신뢰하지 않는다",
                    "dedupe_classification": classification,
                },
            )
        )

    dup_groups = {r["duplicate_group_id"] for r in records if r["duplicate_group_id"]}
    report.duplicate_flagged_groups = len(dup_groups)
    report.duplicate_flagged_records = sum(1 for r in records if r["duplicate_group_id"])

    write_staging(name, "부울경", records)
    report.original_status_values = {"(컬럼 없음)": report.raw_input_count}
    report.included_before_dedupe = report.included_in_staging + report.deduped_records_removed
    report.final_included_after_dedupe = report.included_in_staging
    report.notes.append(
        "주차장관리번호는 단독 dedupe key로 쓰지 않는다. 이름+지번주소로 묶은 19그룹 중 "
        f"{classification_counts.get('A', 0)}그룹(A, 좌표·구획수 완전동일)과 "
        f"{classification_counts.get('A_PRIME', 0)}그룹(A', 좌표·관리번호 동일·부가필드만 다름 — 제공기관 갱신시점 차이)에서 "
        f"대표행 규칙(주소채움률>도로명주소>전화번호>상세필드채움률>기준일최신성)으로 "
        f"{report.deduped_records_removed}건을 자동 dedupe했다. "
        f"{classification_counts.get('B_PRIME', 0)}그룹(B', 좌표는 같으나 관리번호도 다름)과 "
        f"{classification_counts.get('D_PRIME', 0)}그룹(D', 판정불가)은 병합하지 않고 수동 검토로 남겼다."
    )
    write_report(name, report)
    print(
        f"{name}: raw={report.raw_input_count} included_before_dedupe={report.included_before_dedupe} "
        f"deduped_removed={report.deduped_records_removed} final_included={report.final_included_after_dedupe} "
        f"dedupe_reconcile={report.dedupe_reconcile_ok()} classification={dict(classification_counts)} "
        f"reconcile={report.reconcile_ok()}"
    )


def main() -> None:
    for name, cfg in LOCALDATA_SOURCES.items():
        process_localdata(name, cfg["regions"])
    process_store_info()
    process_tourapi()
    process_parking()


if __name__ == "__main__":
    main()
