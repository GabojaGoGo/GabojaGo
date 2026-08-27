"""P1 확장 원천 1차 staging — 대표 6개 원천으로 base와 동일한 품질 기준을 검증한다.

base 8개 원천(stage_p1_sources.py)과 필드 스키마·정규화 규칙을 최대한 재사용하되,
원천에 없는 필드는 억지로 채우지 않는다. raw는 절대 수정하지 않는다.

이번 패스 대상(구조가 서로 다른 6개를 우선 선택):
- 국가유산청 지정 국가유산 공간정보 (SHP, 폴리곤, EPSG:5179, zip)
- 부산광역시 공공체육시설 현황 (SHP, 포인트, WGS84, zip)
- 전국관광안내소표준데이터 (JSON, WGS84)
- 전국박물관미술관정보표준데이터 (JSON, WGS84)
- 전국농어촌체험휴양마을표준데이터 (JSON, WGS84)
- 해양수산부 마리나 정보 (CSV, WKT MULTIPOINT, EPSG:5179)
"""

from __future__ import annotations

import csv
import io
import json
import re
import zipfile
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass, field
from datetime import UTC, datetime
from pathlib import Path

import openpyxl
import shapefile
from pyproj import Transformer
from shapely.geometry import MultiPolygon, Point, Polygon

from data_import.cleaning import (
    check_coordinate,
    norm_nospace,
    normalize_str,
    to_float,
)

PROJECT_ROOT = Path(__file__).resolve().parent.parent
RAW = PROJECT_ROOT / "data/raw"
STAGING = PROJECT_ROOT / "data/staging"
REPORTS = PROJECT_ROOT / "data/reports/cleaning"

EPSG5179_TO_WGS84 = Transformer.from_crs("EPSG:5179", "EPSG:4326", always_xy=True)

REGION_PREFIXES = ("부산", "울산", "경남", "경상남도")
REGION_NAMES_CTPRVN = {"부산광역시", "울산광역시", "경상남도"}


def epsg5179_to_wgs84(x: float | None, y: float | None) -> tuple[float | None, float | None]:
    if x is None or y is None or x == 0 or y == 0:
        return None, None
    lon, lat = EPSG5179_TO_WGS84.transform(x, y)
    return lat, lon


def _ring_signed_area(ring: list[tuple[float, float]]) -> float:
    area = 0.0
    n = len(ring)
    for i in range(n - 1):
        x0, y0 = ring[i]
        x1, y1 = ring[i + 1]
        area += x0 * y1 - x1 * y0
    return area / 2.0


def _shape_rings(shp_i) -> list[list[tuple[float, float]]]:
    parts = list(shp_i.parts) + [len(shp_i.points)]
    return [shp_i.points[parts[i] : parts[i + 1]] for i in range(len(parts) - 1)]


def build_heritage_geometry(shp_i) -> tuple[Polygon | MultiPolygon | None, int]:
    """SHP 레코드(멀티파트 가능)를 올바른 shapely geometry로 조립한다.

    2차 정제에서 발견: 1,353건 중 256건이 멀티파트(디스조인트 폴리곤 또는
    구멍 있는 폴리곤)였는데, 기존 방식은 모든 점을 하나로 이어붙여 shoelace
    중심을 계산해 실제 폴리곤 외부로 대표점이 떨어지는 사례가 356건 있었다.
    ESRI shapefile 규약(외곽선=시계방향, 구멍=반시계방향)에 따라 링을
    외곽/구멍으로 분리하고, 구멍은 자신을 포함하는 외곽 폴리곤에 배정해
    올바른 Polygon/MultiPolygon을 만든다.
    """
    rings = _shape_rings(shp_i)
    if not rings:
        return None, 0

    exteriors = [r for r in rings if _ring_signed_area(r) < 0]
    holes = [r for r in rings if _ring_signed_area(r) >= 0]
    if not exteriors:
        exteriors = rings  # 방향 규칙이 깨진 경우의 안전한 폴백 — 전부 외곽으로 취급
        holes = []

    shells = [Polygon(e) for e in exteriors]
    hole_bins: list[list] = [[] for _ in shells]
    unassigned_holes = 0
    for h in holes:
        pt = Point(h[0])
        for idx, shell in enumerate(shells):
            if shell.contains(pt) or shell.intersects(pt):
                hole_bins[idx].append(h)
                break
        else:
            unassigned_holes += 1

    polys = []
    for idx, e in enumerate(exteriors):
        try:
            p = Polygon(e, hole_bins[idx])
            if not p.is_valid:
                p = p.buffer(0)
            if not p.is_empty:
                polys.append(p)
        except Exception:  # noqa: BLE001, S112 - 깨진 개별 ring은 건너뛰고 report에서 실패를 센다.
            continue

    if not polys:
        return None, unassigned_holes
    geom = MultiPolygon(polys) if len(polys) > 1 else polys[0]
    if not geom.is_valid:
        geom = geom.buffer(0)
    return geom, unassigned_holes


def polygon_representative_point(shp_i) -> tuple[tuple[float, float] | None, dict]:
    """대표점 = shapely representative_point()(항상 폴리곤 내부를 보장).

    기존 shoelace 중심(centroid) 방식은 오목한/멀티파트 폴리곤에서 폴리곤
    외부로 떨어질 수 있음이 실측으로 확인돼(356/1,353, 26.3%) 이 방식으로
    교체한다. 진단 정보(멀티파트 여부, 미배정 구멍 수)도 함께 반환한다.
    """
    geom, unassigned_holes = build_heritage_geometry(shp_i)
    diag = {
        "is_multipart": len(_shape_rings(shp_i)) > 1,
        "unassigned_holes": unassigned_holes,
        "geometry_build_failed": geom is None or geom.is_empty,
    }
    if geom is None or geom.is_empty:
        return None, diag
    rp = geom.representative_point()
    return (rp.x, rp.y), diag


@dataclass
class SourceReport:
    source: str
    raw_input_count: int = 0
    included_in_staging: int = 0
    non_place_excluded: int = 0
    unknown: int = 0
    coord_valid: int = 0
    coord_missing: int = 0
    coord_zero: int = 0
    coord_out_of_region: int = 0
    address_lot_filled: int = 0
    address_road_filled: int = 0
    internal_duplicate_groups: int = 0
    internal_duplicate_records: int = 0
    id_collision_groups: int = 0
    id_collision_records: int = 0
    id_field_used: str | None = None
    id_uniqueness_verified: bool | None = None
    multipart_geometry_count: int = 0
    unassigned_hole_count: int = 0
    geometry_build_failed_count: int = 0
    source_specific_exceptions: list[str] = field(default_factory=list)
    extra: dict = field(default_factory=dict)
    notes: list[str] = field(default_factory=list)

    def reconcile_ok(self) -> bool:
        return self.raw_input_count == self.included_in_staging + self.non_place_excluded


def make_record(
    *,
    source: str,
    source_record_id: str | None,
    normalized_status: str,
    normalized_name: str | None,
    normalized_address_lot: str | None,
    normalized_address_road: str | None,
    latitude: float | None,
    longitude: float | None,
    coordinate_status: str,
    exclusion_reason: str | None,
    duplicate_group_id: str | None = None,
    duplicate_count: int = 1,
    manual_review_required: bool = False,
    source_identifier_collision_id: str | None = None,
    source_identifier_collision_count: int = 1,
    provenance: dict,
) -> dict:
    return {
        "source": source,
        "source_record_id": source_record_id,
        "original_status": None,
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
        "source_identifier_collision_id": source_identifier_collision_id,
        "source_identifier_collision_count": source_identifier_collision_count,
        "status_enrichment_candidate": None,
        "status_link_confidence": None,
        "status_conflict_flag": None,
        "linked_source": None,
        "dedupe_status": None,
        "provenance": provenance,
    }


def write_report(name: str, report: SourceReport) -> Path:
    REPORTS.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(UTC).strftime("%Y%m%d-%H%M%SZ")
    path = REPORTS / f"expansion-staging-{name}-{timestamp}.json"
    data = asdict(report)
    data["reconcile_ok"] = report.reconcile_ok()
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def write_staging(name: str, records: list[dict]) -> Path:
    out_dir = STAGING / name
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / "부울경.json"
    path.write_text(json.dumps(records, ensure_ascii=False, indent=1), encoding="utf-8")
    return path


def _addr_fill_stats(report: SourceReport, lot: str | None, road: str | None) -> None:
    if lot:
        report.address_lot_filled += 1
    if road:
        report.address_road_filled += 1


def _coord_stats(report: SourceReport, coord_status: str) -> None:
    if coord_status == "VALID":
        report.coord_valid += 1
    elif coord_status == "MISSING":
        report.coord_missing += 1
    elif coord_status == "ZERO":
        report.coord_zero += 1
    elif coord_status == "OUT_OF_REGION":
        report.coord_out_of_region += 1


def _flag_internal_duplicates(records: list[dict], key_fn) -> tuple[int, int]:
    """이름+주소 등 key로 내부 중복 후보만 플래그(자동 병합 없음)."""
    counter: Counter = Counter()
    keys = []
    for r in records:
        k = key_fn(r)
        keys.append(k)
        if k is not None:
            counter[k] += 1
    groups = 0
    flagged = 0
    seen_groups = set()
    for r, k in zip(records, keys):
        if k is not None and counter[k] > 1:
            r["duplicate_group_id"] = f"{r['source']}:name_addr:{k}"
            r["duplicate_count"] = counter[k]
            r["manual_review_required"] = True
            flagged += 1
            if k not in seen_groups:
                seen_groups.add(k)
                groups += 1
    return groups, flagged


# ---------------------------------------------------------------------------
# 1. 국가유산청 지정 국가유산 공간정보
# ---------------------------------------------------------------------------

HERITAGE_PLACE_LAYERS = {"국가지정유산", "시도지정유산", "국가등록문화유산", "시도등록문화유산"}
HERITAGE_NONPLACE_LAYERS = {"국가지정유산보호구역", "시도지정유산보호구역"}


def process_heritage() -> None:
    name = "국가유산공간정보"
    report = SourceReport(source=name)
    report.id_field_used = "유산코드"
    zip_path = RAW / "standard/국가유산공간정보/지정유산_부울경.zip"

    zf = zipfile.ZipFile(zip_path)
    bases = sorted({n.rsplit(".", 1)[0] for n in zf.namelist()})

    id_counter: Counter[str] = Counter()
    all_rows = []  # (layer_label, fields_dict, is_place)

    for base in bases:
        try:
            label = base.encode("cp437").decode("cp949")
        except UnicodeError:
            label = base
        shp = io.BytesIO(zf.read(base + ".shp"))
        dbf = io.BytesIO(zf.read(base + ".dbf"))
        shx = io.BytesIO(zf.read(base + ".shx"))
        r = shapefile.Reader(shp=shp, dbf=dbf, shx=shx, encoding="cp949")
        fields = [f[0] for f in r.fields[1:]]
        is_place = label in HERITAGE_PLACE_LAYERS

        for i in range(len(r)):
            row = dict(zip(fields, r.record(i)))
            shp_i = r.shape(i)
            row["_layer"] = label
            row["_rep_point"], row["_geom_diag"] = (
                polygon_representative_point(shp_i) if hasattr(shp_i, "points") else (None, {})
            )
            all_rows.append((label, row, is_place))
            if is_place:
                heritage_id = normalize_str(row.get("유산코드"))
                if heritage_id:
                    id_counter[heritage_id] += 1

    records = []
    for label, row, is_place in all_rows:
        report.raw_input_count += 1
        heritage_id = normalize_str(row.get("유산코드"))

        if not is_place:
            report.non_place_excluded += 1
            continue

        report.included_in_staging += 1
        report.unknown += 1

        rep_point = row.get("_rep_point")
        geom_diag = row.get("_geom_diag") or {}
        lat, lon = (None, None)
        if rep_point:
            lat, lon = epsg5179_to_wgs84(rep_point[0], rep_point[1])
        coord_status = check_coordinate(lat, lon)
        _coord_stats(report, coord_status)
        if geom_diag.get("is_multipart"):
            report.multipart_geometry_count += 1
        if geom_diag.get("unassigned_holes"):
            report.unassigned_hole_count += geom_diag["unassigned_holes"]
        if geom_diag.get("geometry_build_failed"):
            report.geometry_build_failed_count += 1

        collision_id = None
        collision_count = 1
        dup_count = id_counter.get(heritage_id or "", 0)
        if heritage_id and dup_count > 1:
            collision_id = f"{name}:{heritage_id}"
            collision_count = dup_count
            report.id_collision_records += 1

        records.append(
            make_record(
                source=name,
                source_record_id=heritage_id,
                normalized_status="UNKNOWN",
                normalized_name=normalize_str(row.get("국가유산명")),
                normalized_address_lot=None,
                normalized_address_road=None,
                latitude=lat,
                longitude=lon,
                coordinate_status=coord_status,
                exclusion_reason=None,
                source_identifier_collision_id=collision_id,
                source_identifier_collision_count=collision_count,
                provenance={
                    "raw_file": "data/raw/standard/국가유산공간정보/지정유산_부울경.zip",
                    "layer": label,
                    "종목명": normalize_str(row.get("종목명")),
                    "시도명": normalize_str(row.get("시도명")),
                    "시군구명": normalize_str(row.get("시군구명")),
                    "면적": row.get("면적"),
                    "geometry_type": "POLYGON_REPRESENTATIVE_POINT",
                    "is_multipart_geometry": geom_diag.get("is_multipart", False),
                    "source_crs": "EPSG:5179",
                },
            )
        )

    report.id_collision_groups = sum(1 for _, c in id_counter.items() if c > 1)
    report.id_uniqueness_verified = True
    if report.id_collision_groups:
        report.notes.append(
            f"유산코드가 {report.id_collision_groups}개 그룹에서 중복 등장한다(주로 같은 유산의 "
            "지정유산 레코드와 보호구역 레코드가 유산코드를 공유하는 경우) — dedupe key로 단독 "
            "사용하지 않고 source_identifier_collision_*로만 기록했다."
        )
    report.source_specific_exceptions.append(
        "폴리곤 형상(EPSG:5179)이라 대표점을 계산해 좌표로 썼다 — base 원천의 '원본 좌표 그대로 "
        "보존' 원칙과 달리 이 원천은 애초에 점 좌표가 없다. shoelace 중심(면적 가중 중심)은 "
        f"멀티파트/오목한 폴리곤에서 폴리곤 밖으로 떨어지는 사례가 356/1,353건(26.3%) 확인돼 "
        f"shapely representative_point()로 교체했다(항상 폴리곤 내부를 보장). "
        f"멀티파트 지오메트리 {report.multipart_geometry_count}건, 구멍 링을 외곽에 배정하지 "
        f"못한 사례 {report.unassigned_hole_count}건(면적을 소폭 과대추정하지만 대표점 유효성에는 "
        "영향 없음)."
    )
    report.source_specific_exceptions.append(
        "'보호구역' 레이어(국가지정유산보호구역/시도지정유산보호구역, 총 485건)는 지정유산 "
        "자체가 아니라 그 주변 보호/완충 구역 폴리곤이므로 NON_PLACE_RECORD로 staging에서 "
        "제외했다(raw는 보존)."
    )
    report.notes.append("이 원천에는 영업상태 개념이 없어 전량 UNKNOWN이다(base 5종 LOCALDATA와 다른 성격)")

    write_staging(name, records)
    write_report(name, report)
    print(
        f"{name}: raw={report.raw_input_count} included={report.included_in_staging} "
        f"non_place_excl={report.non_place_excluded} id_collision_groups={report.id_collision_groups} "
        f"reconcile={report.reconcile_ok()}"
    )


# ---------------------------------------------------------------------------
# 2. 부산광역시 공공체육시설 현황(SHP, 포인트)
# ---------------------------------------------------------------------------

def process_busan_sports_shp() -> None:
    name = "부산공공체육시설"
    report = SourceReport(source=name)
    report.id_field_used = "FID"
    zip_path = RAW / "busan_portal/공공체육시설SHP/부산광역시_공공체육시설_현황_SHP.zip"

    zf = zipfile.ZipFile(zip_path)
    shp = io.BytesIO(zf.read("TL_PSF_STUS.shp"))
    dbf = io.BytesIO(zf.read("TL_PSF_STUS.dbf"))
    shx = io.BytesIO(zf.read("TL_PSF_STUS.shx"))
    r = shapefile.Reader(shp=shp, dbf=dbf, shx=shx, encoding="utf-8")
    fields = [f[0] for f in r.fields[1:]]

    id_counter: Counter[str] = Counter()
    rows = [dict(zip(fields, r.record(i))) for i in range(len(r))]
    for row in rows:
        fid = normalize_str(row.get("FID"))
        if fid:
            id_counter[fid] += 1

    records = []
    for row in rows:
        report.raw_input_count += 1
        report.included_in_staging += 1
        report.unknown += 1

        lat = to_float(str(row.get("LAT"))) if row.get("LAT") is not None else None
        lon = to_float(str(row.get("LNG"))) if row.get("LNG") is not None else None
        coord_status = check_coordinate(lat, lon)
        _coord_stats(report, coord_status)

        lot = normalize_str(row.get("ADDRESS_JI"))
        road = normalize_str(row.get("ADDRESS"))
        _addr_fill_stats(report, lot, road)

        fid = normalize_str(row.get("FID"))
        collision_id = None
        collision_count = 1
        dup_count = id_counter.get(fid or "", 0)
        if fid and dup_count > 1:
            collision_id = f"{name}:{fid}"
            collision_count = dup_count
            report.id_collision_records += 1

        records.append(
            make_record(
                source=name,
                source_record_id=fid,
                normalized_status="UNKNOWN",
                normalized_name=normalize_str(row.get("NAME")),
                normalized_address_lot=lot,
                normalized_address_road=road,
                latitude=lat,
                longitude=lon,
                coordinate_status=coord_status,
                exclusion_reason=None,
                source_identifier_collision_id=collision_id,
                source_identifier_collision_count=collision_count,
                provenance={
                    "raw_file": "data/raw/busan_portal/공공체육시설SHP/부산광역시_공공체육시설_현황_SHP.zip",
                    "시설유형": normalize_str(row.get("TYPE_OF_FC")),
                    "소유기관": normalize_str(row.get("OWN_ORG")),
                    "관리기관": normalize_str(row.get("MNG_ORG")),
                    "구군": normalize_str(row.get("GUGUN")),
                    "자료기준일": normalize_str(row.get("DCBYMD")),
                    "source_crs": "WGS84(원본 LAT/LNG 그대로)",
                },
            )
        )

    report.id_collision_groups = sum(1 for _, c in id_counter.items() if c > 1)
    report.id_uniqueness_verified = True
    dup_groups, dup_records = _flag_internal_duplicates(
        records, lambda rec: (norm_nospace(rec["normalized_name"]), rec["normalized_address_lot"])
        if rec["normalized_name"] and rec["normalized_address_lot"] else None
    )
    report.internal_duplicate_groups = dup_groups
    report.internal_duplicate_records = dup_records
    report.notes.append("부산광역시 자체 SHP로, 부산만 다루고 울산/경남은 포함하지 않는다(원천 자체가 부산 한정)")

    write_staging(name, records)
    write_report(name, report)
    print(
        f"{name}: raw={report.raw_input_count} included={report.included_in_staging} "
        f"dup_groups={dup_groups} coord_valid={report.coord_valid} reconcile={report.reconcile_ok()}"
    )


# ---------------------------------------------------------------------------
# 3~5. 표준데이터 JSON 계열(관광안내소/박물관미술관/농어촌체험휴양마을)
# ---------------------------------------------------------------------------

def _in_buul_by_ctprvn(row: dict) -> bool:
    return row.get("CTPRVN_NM") in REGION_NAMES_CTPRVN


def _in_buul_by_addr(row: dict) -> bool:
    addr = row.get("RDNMADR") or row.get("LNMADR") or ""
    return addr.startswith(REGION_PREFIXES)


def process_standard_json(
    *,
    name: str,
    raw_file: str,
    name_field: str,
    region_filter,
    extra_provenance_fields: dict[str, str],
    source_specific_note: str | None = None,
) -> None:
    report = SourceReport(source=name)
    report.id_field_used = None  # 이 계열 원천에는 시설 단위 고유 ID 필드가 없다
    path = RAW / raw_file
    all_rows = json.loads(path.read_text(encoding="utf-8"))
    buul = [r for r in all_rows if region_filter(r)]

    records = []
    for row in buul:
        report.raw_input_count += 1
        report.included_in_staging += 1
        report.unknown += 1

        lat = to_float(row.get("LATITUDE"))
        lon = to_float(row.get("LONGITUDE"))
        coord_status = check_coordinate(lat, lon)
        _coord_stats(report, coord_status)

        lot = normalize_str(row.get("LNMADR"))
        road = normalize_str(row.get("RDNMADR"))
        _addr_fill_stats(report, lot, road)

        provenance = {
            "raw_file": raw_file,
            "source_crs": "WGS84(원본 LATITUDE/LONGITUDE 그대로)",
            "REFERENCE_DATE": normalize_str(row.get("REFERENCE_DATE")),
        }
        for label, field_name in extra_provenance_fields.items():
            provenance[label] = normalize_str(row.get(field_name))

        records.append(
            make_record(
                source=name,
                source_record_id=None,  # 원천에 시설 단위 ID 필드가 없다(검증 결과, 아래 notes 참조)
                normalized_status="UNKNOWN",
                normalized_name=normalize_str(row.get(name_field)),
                normalized_address_lot=lot,
                normalized_address_road=road,
                latitude=lat,
                longitude=lon,
                coordinate_status=coord_status,
                exclusion_reason=None,
                provenance=provenance,
            )
        )

    report.id_uniqueness_verified = False
    report.notes.append(
        "이 원천(표준데이터 JSON)에는 시설 단위 고유 ID 필드가 없다(INSTT_CODE는 운영/제공기관 "
        "코드이지 시설 ID가 아니다 — 여러 시설이 같은 기관코드를 공유할 수 있어 dedupe key로 "
        "쓰지 않았다). source_record_id는 null로 두고, 내부 중복은 이름+지번주소로만 플래그했다."
    )
    if source_specific_note:
        report.source_specific_exceptions.append(source_specific_note)

    dup_groups, dup_records = _flag_internal_duplicates(
        records, lambda rec: (norm_nospace(rec["normalized_name"]), rec["normalized_address_lot"])
        if rec["normalized_name"] and rec["normalized_address_lot"] else None
    )
    report.internal_duplicate_groups = dup_groups
    report.internal_duplicate_records = dup_records

    write_staging(name, records)
    write_report(name, report)
    print(
        f"{name}: raw={report.raw_input_count} included={report.included_in_staging} "
        f"dup_groups={dup_groups} coord_valid={report.coord_valid} reconcile={report.reconcile_ok()}"
    )


def process_tourist_info_center() -> None:
    process_standard_json(
        name="관광안내소",
        raw_file="standard/관광안내소/전국관광안내소표준데이터.json",
        name_field="TRSMIC_NM",
        region_filter=_in_buul_by_ctprvn,
        extra_provenance_fields={
            "운영기관": "OPER_INSTITUTION_NM",
            "전화번호": "GUIDANCE_PHONE_NUMBER",
            "위치설명": "TRSMIC_LC",
        },
    )


def process_museum() -> None:
    process_standard_json(
        name="박물관미술관",
        raw_file="standard/박물관미술관/전국박물관미술관정보표준데이터.json",
        name_field="FCLTY_NM",
        region_filter=_in_buul_by_addr,  # CTPRVN_NM 필드 자체가 없다
        extra_provenance_fields={
            "시설유형": "FCLTY_TYPE",
            "전화번호": "PHONE_NUMBER",
            "성인요금": "ADULT_CHRGE",
        },
        source_specific_note=(
            "이 원천에는 CTPRVN_NM(시도명) 필드가 없어 다른 두 표준데이터 원천과 달리 "
            "도로명/지번주소 접두어로 부울경 여부를 판정했다(115/116/173건 규모가 기존 "
            "other-source-survey.md 실측치와 일치함을 확인)."
        ),
    )


def process_rural_village() -> None:
    process_standard_json(
        name="농어촌체험휴양마을",
        raw_file="standard/농어촌체험휴양마을/전국농어촌체험휴양마을표준데이터.json",
        name_field="EXPRN_VILAGE_NM",
        region_filter=_in_buul_by_ctprvn,
        extra_provenance_fields={
            "운영기관": "INSTITUTION_NM",
            "전화번호": "PHONE_NUMBER",
            "보유시설": "HOLD_FCLTY",
        },
        source_specific_note="LNMADR(지번주소)가 상당수 비어 있다 — 억지로 채우지 않고 null로 둔다.",
    )


# ---------------------------------------------------------------------------
# 6. 해양수산부 마리나 정보(CSV, WKT MULTIPOINT, EPSG:5179)
# ---------------------------------------------------------------------------

WKT_POINT_RE = re.compile(r"MULTIPOINT\s*\(\(\s*([\d.]+)\s+([\d.]+)\s*\)\)")


def parse_wkt_multipoint(value: str | None) -> tuple[float | None, float | None]:
    if not value:
        return None, None
    m = WKT_POINT_RE.search(value)
    if not m:
        return None, None
    return float(m.group(1)), float(m.group(2))


def process_marina() -> None:
    name = "마리나"
    report = SourceReport(source=name)
    report.id_field_used = "공간정보일련번호"
    path = RAW / "standard/마리나/해양수산부_마리나_정보_20250124.csv"

    with path.open(encoding="cp949", errors="replace", newline="") as f:
        reader = csv.DictReader(f)
        rows = list(reader)

    buul = [r for r in rows if (r.get("행정구역명") or "").startswith(REGION_PREFIXES)]

    id_counter: Counter[str] = Counter(normalize_str(r.get("공간정보일련번호")) for r in buul)

    records = []
    for row in buul:
        report.raw_input_count += 1
        report.included_in_staging += 1
        report.unknown += 1

        x, y = parse_wkt_multipoint(row.get("공간정보"))
        lat, lon = epsg5179_to_wgs84(x, y)
        coord_status = check_coordinate(lat, lon)
        _coord_stats(report, coord_status)

        addr = normalize_str(row.get("전체주소"))
        if addr:
            report.address_lot_filled += 1  # 이 원천은 지번/도로명 구분이 없는 단일 "전체주소"뿐

        rid = normalize_str(row.get("공간정보일련번호"))
        collision_id = None
        collision_count = 1
        dup_count = id_counter.get(rid or "", 0)
        if rid and dup_count > 1:
            collision_id = f"{name}:{rid}"
            collision_count = dup_count
            report.id_collision_records += 1

        records.append(
            make_record(
                source=name,
                source_record_id=rid,
                normalized_status="UNKNOWN",
                normalized_name=normalize_str(row.get("마리나항만명")),
                normalized_address_lot=addr,
                normalized_address_road=None,
                latitude=lat,
                longitude=lon,
                coordinate_status=coord_status,
                exclusion_reason=None,
                source_identifier_collision_id=collision_id,
                source_identifier_collision_count=collision_count,
                provenance={
                    "raw_file": "data/raw/standard/마리나/해양수산부_마리나_정보_20250124.csv",
                    "행정구역명": normalize_str(row.get("행정구역명")),
                    "마리나항만종류명": normalize_str(row.get("마리나항만종류명")),
                    "source_crs": "EPSG:5179",
                },
            )
        )

    report.id_collision_groups = sum(1 for _, c in id_counter.items() if c > 1)
    report.id_uniqueness_verified = True
    report.source_specific_exceptions.append(
        "지번/도로명주소 구분이 없는 단일 '전체주소' 필드뿐이라 normalized_address_lot에만 "
        "채우고 normalized_address_road는 null로 둔다(base의 두 주소체계 구조를 강제하지 않음)."
    )
    report.notes.append("공간정보일련번호는 전국 데이터의 순번으로, 부울경만 잘라내도 값 자체는 전국 기준 순번이다")

    write_staging(name, records)
    write_report(name, report)
    print(
        f"{name}: raw={report.raw_input_count} included={report.included_in_staging} "
        f"coord_valid={report.coord_valid} reconcile={report.reconcile_ok()}"
    )


# ---------------------------------------------------------------------------
# 2차. 경남 시군 공공체육시설(A유형 7개 시군) — 시군마다 스키마가 다르다
# ---------------------------------------------------------------------------

GYEONGNAM_SPORTS_DIR = "gyeongnam_portal/공공체육시설_시군샘플"
GYEONGNAM_COUNTY_CONFIGS = {
    "진주시": {
        "file": "진주시_공공체육시설_20260810.csv", "encoding": "utf-8",
        "name": "시설명", "road": "새주소", "lot": "지번주소",
        "category": "시설유형", "org": "소유기관",
    },
    "창녕군": {
        "file": "창녕군_체육시설정보_20260518_UTF8BOM.csv", "encoding": "utf-8-sig",
        "name": "명칭", "freeform_addr": "위치", "category": "종류",
    },
    "합천군": {
        "file": "합천군_공공체육시설현황및이용현황_20240516.csv", "encoding": "utf-8-sig",
        "name": "체육시설명", "road": "소재지도로명주소", "lot": "소재지지번주소",
        "category": "시설유형구분", "org": "관리기관명", "phone": "사용안내전화번호",
        "lat": "위도", "lon": "경도",
    },
    "창원시": {
        "file": "changwon_sports_원본CP949.csv", "encoding": "cp949",
        "name": "시설명", "freeform_addr": "위치", "category": "중분류",
    },
    "사천시": {
        "file": "sacheon_sports_원본CP949.csv", "encoding": "cp949",
        "name": "시설명", "freeform_addr": "소재지",
    },
    "밀양시": {
        "file": "miryang_sports_원본CP949.csv", "encoding": "cp949",
        "name": "시설명", "road": "소재지도로명주소", "lot": "소재지지번주소",
        "category": "시설유형", "org": "관리기관",
    },
    "함안군": {
        "file": "haman_sports_원본CP949.csv", "encoding": "cp949",
        "name": "체육시설명", "freeform_addr": "시설위치", "category": "업종",
    },
}


def process_gyeongnam_sports() -> None:
    """P1에서 A유형(공공시설 카탈로그)으로 확정된 7개 시군만 대상으로 한다
    (양산=구조변형 집계형, 거창=이용통계, 남해=민간인허가라벨, 함양/하동=GIS/UPIS
    수동접근필요, 골프장스키장=도 전체 혼재 — 전부 제외, other-source-survey.md §1 A표 `경남 시군별 공공체육시설 현황` 행 근거).
    시군마다 스키마가 달라 통합 schema를 억지로 만들지 않고, 공통 필드에 매핑
    가능한 사실만 매핑한다. 원본 필드는 provenance에 전부 보존한다."""
    name = "경남공공체육시설"
    report = SourceReport(source=name)
    report.id_field_used = None

    fill_summary = {}
    records = []

    for county, cfg in GYEONGNAM_COUNTY_CONFIGS.items():
        path = RAW / GYEONGNAM_SPORTS_DIR / cfg["file"]
        with path.open(encoding=cfg["encoding"], errors="replace", newline="") as f:
            rows = list(csv.DictReader(f))

        county_fill = {"raw": len(rows), "name": 0, "addr": 0, "coord": 0, "category": 0, "org": 0}
        county_records = []
        for row in rows:
            report.raw_input_count += 1
            report.included_in_staging += 1
            report.unknown += 1

            nm = normalize_str(row.get(cfg["name"])) if cfg.get("name") else None
            if nm:
                county_fill["name"] += 1

            lot = normalize_str(row.get(cfg["lot"])) if cfg.get("lot") else None
            road = normalize_str(row.get(cfg["road"])) if cfg.get("road") else None
            if not lot and not road and cfg.get("freeform_addr"):
                # 지번/도로명 구분이 없는 원천 — 유일한 위치 텍스트를 lot 자리에 그대로 보존
                lot = normalize_str(row.get(cfg["freeform_addr"]))
            if lot or road:
                county_fill["addr"] += 1
            _addr_fill_stats(report, lot, road)

            lat = to_float(row.get(cfg["lat"])) if cfg.get("lat") else None
            lon = to_float(row.get(cfg["lon"])) if cfg.get("lon") else None
            if lat is not None and lon is not None:
                county_fill["coord"] += 1
            coord_status = check_coordinate(lat, lon)
            _coord_stats(report, coord_status)

            category = normalize_str(row.get(cfg["category"])) if cfg.get("category") else None
            if category:
                county_fill["category"] += 1
            org = normalize_str(row.get(cfg["org"])) if cfg.get("org") else None
            if org:
                county_fill["org"] += 1
            phone = normalize_str(row.get(cfg["phone"])) if cfg.get("phone") else None

            rec = make_record(
                source=name,
                source_record_id=None,
                normalized_status="UNKNOWN",
                normalized_name=nm,
                normalized_address_lot=lot,
                normalized_address_road=road,
                latitude=lat,
                longitude=lon,
                coordinate_status=coord_status,
                exclusion_reason=None,
                provenance={
                    "raw_file": f"data/raw/{GYEONGNAM_SPORTS_DIR}/{cfg['file']}",
                    "시군": county,
                    "시설종류": category,
                    "관리기관": org,
                    "전화번호": phone,
                    "raw_row": {k: v for k, v in row.items()},
                },
            )
            records.append(rec)
            county_records.append(rec)

        for k in ("name", "addr", "coord", "category", "org"):
            county_fill[f"{k}_pct"] = (
                round(county_fill[k] / county_fill["raw"] * 100, 1) if county_fill["raw"] else 0.0
            )
        fill_summary[county] = county_fill

        dup_groups, dup_records = _flag_internal_duplicates(
            county_records,
            lambda rec: (norm_nospace(rec["normalized_name"]), rec["normalized_address_lot"])
            if rec["normalized_name"] and rec["normalized_address_lot"] else None,
        )
        report.internal_duplicate_groups += dup_groups
        report.internal_duplicate_records += dup_records

    report.id_uniqueness_verified = False
    report.extra["county_field_fill"] = fill_summary
    report.source_specific_exceptions.append(
        "P1에서 A유형으로 확정된 7개 시군(진주·창녕·합천·창원·사천·밀양·함안)만 대상으로 했다 — "
        "양산(집계형)/거창(이용통계)/남해(민간인허가라벨)/함양·하동(GIS·수동접근)/도 전체 "
        "골프장스키장(혼재)은 other-source-survey.md §1 A표 `경남 시군별 공공체육시설 현황` 행 근거에 따라 제외했다."
    )
    report.source_specific_exceptions.append(
        "시군마다 필드가 전혀 다르다 — 좌표는 합천군에만 있고(위도/경도), 지번/도로명 구분은 "
        "진주·합천·밀양에만 있다. 나머지 시군은 '위치'/'소재지'/'시설위치' 같은 단일 텍스트뿐이라 "
        "억지로 분리하지 않고 lot 자리에 그대로 담았다. 원본 행 전체를 provenance.raw_row에 "
        "보존해 추후 시군별 재처리가 가능하게 했다."
    )
    report.notes.append("이 원천에는 시설 단위 ID 필드가 없다(공통) — 내부 중복은 이름+주소로만 검사")

    write_staging(name, records)
    write_report(name, report)
    print(f"{name}: raw={report.raw_input_count} included={report.included_in_staging} reconcile={report.reconcile_ok()}")
    for county, fs in fill_summary.items():
        print(
            f"  {county}: raw={fs['raw']} name={fs['name_pct']}% addr={fs['addr_pct']}% "
            f"coord={fs['coord_pct']}% category={fs['category_pct']}% org={fs['org_pct']}%"
        )


# ---------------------------------------------------------------------------
# 2차. 울산광역시 문화시설 현황
# ---------------------------------------------------------------------------

def process_ulsan_culture() -> None:
    name = "울산문화시설"
    report = SourceReport(source=name)
    report.id_field_used = "연번"
    path = RAW / "ulsan_portal/문화시설현황/울산광역시_문화시설_현황_251111.csv"

    with path.open(encoding="utf-8", errors="replace", newline="") as f:
        rows = list(csv.DictReader(f))

    id_counter: Counter[str] = Counter(normalize_str(r.get("연번")) for r in rows)

    records = []
    for row in rows:
        report.raw_input_count += 1
        report.included_in_staging += 1
        report.unknown += 1

        lat = to_float(row.get("위도"))
        lon = to_float(row.get("경도"))
        coord_status = check_coordinate(lat, lon)
        _coord_stats(report, coord_status)

        addr = normalize_str(row.get("주소"))
        _addr_fill_stats(report, addr, None)

        rid = normalize_str(row.get("연번"))
        collision_id = None
        collision_count = 1
        dup_count = id_counter.get(rid or "", 0)
        if rid and dup_count > 1:
            collision_id = f"{name}:{rid}"
            collision_count = dup_count
            report.id_collision_records += 1

        records.append(
            make_record(
                source=name,
                source_record_id=rid,
                normalized_status="UNKNOWN",
                normalized_name=normalize_str(row.get("시설")),
                normalized_address_lot=addr,
                normalized_address_road=None,
                latitude=lat,
                longitude=lon,
                coordinate_status=coord_status,
                exclusion_reason=None,
                source_identifier_collision_id=collision_id,
                source_identifier_collision_count=collision_count,
                provenance={
                    "raw_file": "data/raw/ulsan_portal/문화시설현황/울산광역시_문화시설_현황_251111.csv",
                    "구분(원본 분류값 그대로)": normalize_str(row.get("구분")),
                    "전화번호": normalize_str(row.get("전화번호")),
                    "사이트주소": normalize_str(row.get("사이트주소")),
                    "좌석수_열람석수": normalize_str(row.get("공연장 좌석수 도서관 열람석수")),
                    "개관일": normalize_str(row.get("개관일")),
                    "source_crs": "WGS84(원본 위도/경도 그대로)",
                },
            )
        )

    report.id_collision_groups = sum(1 for _, c in id_counter.items() if c > 1)
    report.id_uniqueness_verified = True
    report.source_specific_exceptions.append(
        "'구분' 컬럼에 공연장/미술관/도서관/기타 성격이 섞여 있으나 이번 패스에서는 "
        "PlaceType/Subtype을 결정하지 않고 원본 분류값을 provenance에 그대로 보존했다."
    )
    dup_groups, dup_records = _flag_internal_duplicates(
        records, lambda rec: (norm_nospace(rec["normalized_name"]), rec["normalized_address_lot"])
        if rec["normalized_name"] and rec["normalized_address_lot"] else None
    )
    report.internal_duplicate_groups = dup_groups
    report.internal_duplicate_records = dup_records

    write_staging(name, records)
    write_report(name, report)
    print(f"{name}: raw={report.raw_input_count} included={report.included_in_staging} reconcile={report.reconcile_ok()}")


# ---------------------------------------------------------------------------
# 2차. 해양수산부 어항정보
# ---------------------------------------------------------------------------

def process_fishing_port() -> None:
    name = "어항정보"
    report = SourceReport(source=name)
    report.id_field_used = None
    path = RAW / "standard/어항정보/해양수산부_어항정보_20191231.csv"

    with path.open(encoding="cp949", errors="replace", newline="") as f:
        rows = list(csv.DictReader(f))
    buul = [r for r in rows if (r.get("어항주소") or "").startswith(REGION_PREFIXES)]

    records = []
    for row in buul:
        report.raw_input_count += 1
        report.included_in_staging += 1
        report.unknown += 1

        lat = to_float(row.get("위도"))
        lon = to_float(row.get("경도"))
        coord_status = check_coordinate(lat, lon)
        _coord_stats(report, coord_status)

        addr = normalize_str(row.get("어항주소"))
        _addr_fill_stats(report, addr, None)

        records.append(
            make_record(
                source=name,
                source_record_id=None,
                normalized_status="UNKNOWN",
                normalized_name=normalize_str(row.get("어항명")),
                normalized_address_lot=addr,
                normalized_address_road=None,
                latitude=lat,
                longitude=lon,
                coordinate_status=coord_status,
                exclusion_reason=None,
                provenance={
                    "raw_file": "data/raw/standard/어항정보/해양수산부_어항정보_20191231.csv",
                    "연도": normalize_str(row.get("연도")),
                    "인근어항명": normalize_str(row.get("인근어항명")),
                    "인근어항항종": normalize_str(row.get("인근어항항종")),
                    "어촌계명": normalize_str(row.get("어촌계명")),
                    "source_crs": "WGS84(원본 위도/경도 그대로)",
                },
            )
        )

    report.id_uniqueness_verified = False
    report.source_specific_exceptions.append(
        "시설 단위 ID 필드가 없다. 어항정보와 이미 staging한 마리나는 장소가 겹칠 가능성이 "
        "있으나 이번 패스에서 cross-source 병합을 하지 않는다(각 원천 내부 정제만 수행)."
    )
    dup_groups, dup_records = _flag_internal_duplicates(
        records, lambda rec: (norm_nospace(rec["normalized_name"]), rec["normalized_address_lot"])
        if rec["normalized_name"] and rec["normalized_address_lot"] else None
    )
    report.internal_duplicate_groups = dup_groups
    report.internal_duplicate_records = dup_records

    write_staging(name, records)
    write_report(name, report)
    print(f"{name}: raw={report.raw_input_count} included={report.included_in_staging} reconcile={report.reconcile_ok()}")


# ---------------------------------------------------------------------------
# 2차. 산림청 유아숲체험원 등록현황(좌표 없음 — 지오코딩하지 않는다)
# ---------------------------------------------------------------------------

def process_kids_forest() -> None:
    name = "유아숲체험원"
    report = SourceReport(source=name)
    report.id_field_used = None
    path = RAW / "standard/유아숲체험원/산림청_유아숲체험원_등록현황_241231.csv"

    with path.open(encoding="cp949", errors="replace", newline="") as f:
        rows = list(csv.DictReader(f))
    buul = [r for r in rows if (r.get("주소") or "").startswith(REGION_PREFIXES)]

    records = []
    for row in buul:
        report.raw_input_count += 1
        report.included_in_staging += 1
        report.unknown += 1

        addr = normalize_str(row.get("주소"))
        _addr_fill_stats(report, addr, None)
        coord_status = check_coordinate(None, None)  # 원천에 좌표 컬럼 자체가 없다
        _coord_stats(report, coord_status)

        records.append(
            make_record(
                source=name,
                source_record_id=None,
                normalized_status="UNKNOWN",
                normalized_name=normalize_str(row.get("시설명")),
                normalized_address_lot=addr,
                normalized_address_road=None,
                latitude=None,
                longitude=None,
                coordinate_status=coord_status,
                exclusion_reason=None,
                provenance={
                    "raw_file": "data/raw/standard/유아숲체험원/산림청_유아숲체험원_등록현황_241231.csv",
                    "운영기간": normalize_str(row.get("운영기간")),
                    "전화번호": normalize_str(row.get("전화번호")),
                    "참여방법": normalize_str(row.get("참여방법")),
                },
            )
        )

    report.id_uniqueness_verified = False
    report.source_specific_exceptions.append(
        "원천 자체에 좌표 컬럼이 없다(산지번 주소만 존재) — 외부 지오코딩을 하지 않고 "
        "MISSING으로 남겼다. 삭제하지 않고 시설명/주소/전화번호/참여방법은 전부 보존했다."
    )
    dup_groups, dup_records = _flag_internal_duplicates(
        records, lambda rec: (norm_nospace(rec["normalized_name"]), rec["normalized_address_lot"])
        if rec["normalized_name"] and rec["normalized_address_lot"] else None
    )
    report.internal_duplicate_groups = dup_groups
    report.internal_duplicate_records = dup_records

    write_staging(name, records)
    write_report(name, report)
    print(f"{name}: raw={report.raw_input_count} included={report.included_in_staging} reconcile={report.reconcile_ok()}")


# ---------------------------------------------------------------------------
# 2차. 전국낚시터정보표준데이터
# ---------------------------------------------------------------------------

def process_fishing_spot() -> None:
    name = "낚시터"
    report = SourceReport(source=name)
    report.id_field_used = None
    path = RAW / "standard/낚시터정보/전국낚시터정보표준데이터.json"
    all_rows = json.loads(path.read_text(encoding="utf-8"))
    buul = [r for r in all_rows if _in_buul_by_addr(r)]

    records = []
    for row in buul:
        report.raw_input_count += 1
        report.included_in_staging += 1
        report.unknown += 1

        lat = to_float(row.get("LATITUDE"))
        lon = to_float(row.get("LONGITUDE"))
        coord_status = check_coordinate(lat, lon)
        _coord_stats(report, coord_status)

        lot = normalize_str(row.get("LNMADR"))
        road = normalize_str(row.get("RDNMADR"))
        _addr_fill_stats(report, lot, road)

        records.append(
            make_record(
                source=name,
                source_record_id=None,
                normalized_status="UNKNOWN",
                normalized_name=normalize_str(row.get("FSHLC_NM")),
                normalized_address_lot=lot,
                normalized_address_road=road,
                latitude=lat,
                longitude=lon,
                coordinate_status=coord_status,
                exclusion_reason=None,
                provenance={
                    "raw_file": "data/raw/standard/낚시터정보/전국낚시터정보표준데이터.json",
                    "source_crs": "WGS84(원본 LATITUDE/LONGITUDE 그대로)",
                    "REFERENCE_DATE": normalize_str(row.get("REFERENCE_DATE")),
                    "낚시터유형": normalize_str(row.get("FSHLC_TYPE")),
                    "수면적유형": normalize_str(row.get("WTRC_FCLTY_TYPE")),
                    "전화번호": normalize_str(row.get("PHONE_NUMBER")),
                    "이용요금": normalize_str(row.get("USE_CHARGE")),
                },
            )
        )

    report.id_uniqueness_verified = False
    report.notes.append("시설 단위 ID 필드가 없어 내부 중복은 이름+지번주소로만 검사했다")
    dup_groups, dup_records = _flag_internal_duplicates(
        records, lambda rec: (norm_nospace(rec["normalized_name"]), rec["normalized_address_lot"])
        if rec["normalized_name"] and rec["normalized_address_lot"] else None
    )
    report.internal_duplicate_groups = dup_groups
    report.internal_duplicate_records = dup_records

    write_staging(name, records)
    write_report(name, report)
    print(f"{name}: raw={report.raw_input_count} included={report.included_in_staging} reconcile={report.reconcile_ok()}")


# ---------------------------------------------------------------------------
# 2차. 농촌진흥청 우수 치유농업시설 — 필드가 극도로 최소(sido/sigungu/name뿐)
# ---------------------------------------------------------------------------

def process_agrohealing() -> None:
    name = "치유농업시설"
    report = SourceReport(source=name)
    report.id_field_used = None
    path = RAW / "agrohealing_portal/우수치유농업시설_부울경.json"
    rows = json.loads(path.read_text(encoding="utf-8"))

    records = []
    for row in rows:
        report.raw_input_count += 1
        report.included_in_staging += 1
        report.unknown += 1

        addr = normalize_str(f"{row.get('sido', '')} {row.get('sigungu', '')}".strip()) or None
        _addr_fill_stats(report, addr, None)
        coord_status = check_coordinate(None, None)
        _coord_stats(report, coord_status)

        records.append(
            make_record(
                source=name,
                source_record_id=None,
                normalized_status="UNKNOWN",
                normalized_name=normalize_str(row.get("name")),
                normalized_address_lot=addr,
                normalized_address_road=None,
                latitude=None,
                longitude=None,
                coordinate_status=coord_status,
                exclusion_reason=None,
                provenance={"raw_file": "data/raw/agrohealing_portal/우수치유농업시설_부울경.json"},
            )
        )

    report.id_uniqueness_verified = False
    report.source_specific_exceptions.append(
        "원천에 실제로 존재하는 필드가 시도/시군구/명칭 3개뿐이다 — 좌표·상세주소·전화번호 "
        "등은 원천 자체에 없어 채우지 않았다(추론 금지 원칙). normalized_address_lot에는 "
        "'시도 시군구'까지만 들어간다."
    )
    dup_groups, dup_records = _flag_internal_duplicates(
        records, lambda rec: norm_nospace(rec["normalized_name"]) if rec["normalized_name"] else None
    )
    report.internal_duplicate_groups = dup_groups
    report.internal_duplicate_records = dup_records

    write_staging(name, records)
    write_report(name, report)
    print(f"{name}: raw={report.raw_input_count} included={report.included_in_staging} reconcile={report.reconcile_ok()}")


# ---------------------------------------------------------------------------
# 2차. 부산관광공사 비짓부산패스 — xlsx 6개 시트 중 t_tour_info만 Place, 나머지는 비장소
# ---------------------------------------------------------------------------

VISITBUSANPASS_KOREAN_LANG_CD = "CD0000001258"


def _xlsx_str(value) -> str | None:
    """openpyxl은 숫자처럼 보이는 셀을 int/float로 돌려준다 — normalize_str에
    넘기기 전에 문자열로 통일한다."""
    if value is None:
        return None
    return normalize_str(str(value))


def process_visitbusanpass() -> None:
    name = "비짓부산패스"
    report = SourceReport(source=name)
    report.id_field_used = "uc_seq"
    path = RAW / "busan_portal/비짓부산패스/비짓부산패스_관광지_여행일정_데이터.xlsx"
    wb = openpyxl.load_workbook(path, data_only=True)

    def sheet_rows(sheet_name):
        ws = wb[sheet_name]
        header = [c.value for c in ws[1]]
        rows = []
        for r in ws.iter_rows(min_row=2, values_only=True):
            if all(v is None or v == "" for v in r):
                continue
            rows.append(dict(zip(header, r)))
        return rows

    # 비장소 시트: 상품/일정/URL메타데이터/빈 테이블 — Place staging에서 제외한다
    non_place_sheets = {
        "t_tour": "여행 상품(다일 코스 패키지) — 장소가 아니라 상품",
        "t_tour_schedule": "여행 일정의 시간대별 방문 스케줄 — t_tour_info의 장소를 재참조하는 일정 레코드",
        "t_tour_url": "외부 API 호출 설정(서비스키 등) 메타데이터 — 장소 데이터가 아님",
        "t_attractions": "빈 테이블(헤더만 존재, 실제 데이터 0건)",
        "t_interest_area": "빈 테이블(헤더만 존재, 실제 데이터 0건)",
    }
    for sheet_name in non_place_sheets:
        rows = sheet_rows(sheet_name)
        report.raw_input_count += len(rows)
        report.non_place_excluded += len(rows)

    # t_tour_info: 관광지 상세 콘텐츠 — uc_seq가 실제 시설 단위 ID(다국어로 반복됨)
    info_rows = sheet_rows("t_tour_info")
    by_uc_seq: dict = defaultdict(list)
    for row in info_rows:
        by_uc_seq[row.get("uc_seq")].append(row)

    # raw_input_count는 "staging 단위(시설)" 기준으로 센다 — 언어별 반복 행은
    # 중복 원천 row가 아니라 같은 시설의 번역본이라 reconcile 공식(raw=included+제외)의
    # "raw"에 포함시키지 않는다. 원본 행 수(언어 반복 포함)는 extra에 별도로 남긴다.
    report.raw_input_count += len(by_uc_seq)
    report.extra["t_tour_info_raw_rows_including_language_variants"] = len(info_rows)
    report.extra["t_tour_info_distinct_facilities"] = len(by_uc_seq)
    report.id_uniqueness_verified = True

    records = []
    for uc_seq, group_rows in by_uc_seq.items():
        report.included_in_staging += 1
        report.unknown += 1

        row = next((r for r in group_rows if r.get("language_tycd") == VISITBUSANPASS_KOREAN_LANG_CD), group_rows[0])

        lat = to_float(str(row.get("lat"))) if row.get("lat") is not None else None
        lon = to_float(str(row.get("lng"))) if row.get("lng") is not None else None
        coord_status = check_coordinate(lat, lon)
        _coord_stats(report, coord_status)

        addr = _xlsx_str(row.get("addr1"))
        _addr_fill_stats(report, addr, None)

        records.append(
            make_record(
                source=name,
                source_record_id=str(uc_seq) if uc_seq else None,
                normalized_status="UNKNOWN",
                normalized_name=_xlsx_str(row.get("place")) or _xlsx_str(row.get("main_place")),
                normalized_address_lot=addr,
                normalized_address_road=None,
                latitude=lat,
                longitude=lon,
                coordinate_status=coord_status,
                exclusion_reason=None,
                duplicate_count=len(group_rows),
                provenance={
                    "raw_file": "data/raw/busan_portal/비짓부산패스/비짓부산패스_관광지_여행일정_데이터.xlsx",
                    "sheet": "t_tour_info",
                    "gugun_nm": _xlsx_str(row.get("gugun_nm")),
                    "tourist_tycd": _xlsx_str(row.get("tourist_tycd")),
                    "cntct_tel": _xlsx_str(row.get("cntct_tel")),
                    "language_variants_available": len(group_rows),
                    "used_language": row.get("language_tycd"),
                },
            )
        )

    report.notes.append(
        f"t_tour_info는 uc_seq(시설)당 최대 5개 언어로 행이 반복된다(총 {len(info_rows)}행 → "
        f"고유 시설 {len(by_uc_seq)}건). 한국어(language_tycd={VISITBUSANPASS_KOREAN_LANG_CD}) 행을 "
        "우선 사용하고 없으면 그 시설의 아무 언어 행이나 사용했다(내용 손실 없음, 언어만 다름)."
    )
    report.source_specific_exceptions.append(
        f"t_tour(상품)/t_tour_schedule(일정)/t_tour_url(API메타)/t_attractions·t_interest_area(빈 "
        f"테이블) {report.non_place_excluded}행은 NON_PLACE_RECORD로 분류해 제외했다 — 이 중 "
        "t_tour_schedule에는 course_nm+좌표를 가진 개별 방문지 레코드가 있어 잠재적으로 "
        "장소 정보를 담고 있지만, t_tour_info와 겹칠 가능성이 있어(cross-reference 필요) 이번 "
        "패스에서는 t_tour_info만 Place staging 대상으로 확정했다."
    )
    report.source_specific_exceptions.append(
        "TourAPI와의 중복 여부는 이번 단계에서 다시 병합하지 않는다(1차 P1 측정치 588건/73.5% "
        "순증은 survey 정본에 이미 기록되어 있고, 이번 패스는 staging 스키마 검증이 목적)."
    )
    dup_groups, dup_records = _flag_internal_duplicates(
        records, lambda rec: (norm_nospace(rec["normalized_name"]), rec["normalized_address_lot"])
        if rec["normalized_name"] and rec["normalized_address_lot"] else None
    )
    report.internal_duplicate_groups = dup_groups
    report.internal_duplicate_records = dup_records

    write_staging(name, records)
    write_report(name, report)
    print(
        f"{name}: raw={report.raw_input_count} included={report.included_in_staging} "
        f"non_place_excl={report.non_place_excluded} reconcile={report.reconcile_ok()}"
    )


# ---------------------------------------------------------------------------
# 2차. 부산광역시 공공미술 현황 — §1에 남은 확장 후보 중 1건(정본 §5 마지막 행)
# ---------------------------------------------------------------------------

def process_busan_public_art() -> None:
    name = "부산공공미술"
    report = SourceReport(source=name)
    report.id_field_used = None
    path = RAW / "busan_portal/공공미술현황/부산공공미술현황_전량.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    items = data["response"]["body"]["items"]["item"]

    records = []
    for row in items:
        report.raw_input_count += 1
        report.included_in_staging += 1
        report.unknown += 1

        lat = to_float(row.get("lat"))
        lon = to_float(row.get("lon"))
        coord_status = check_coordinate(lat, lon)
        _coord_stats(report, coord_status)

        lot = normalize_str(row.get("lot_address"))
        road = normalize_str(row.get("road_address"))
        _addr_fill_stats(report, lot, road)

        records.append(
            make_record(
                source=name,
                source_record_id=None,
                normalized_status="UNKNOWN",
                normalized_name=normalize_str(row.get("work_name")),
                normalized_address_lot=lot,
                normalized_address_road=road,
                latitude=lat,
                longitude=lon,
                coordinate_status=coord_status,
                exclusion_reason=None,
                provenance={
                    "raw_file": "data/raw/busan_portal/공공미술현황/부산공공미술현황_전량.json",
                    "구군": normalize_str(row.get("gugun")),
                    "작품유형": normalize_str(row.get("work_type")),
                    "설치일": normalize_str(row.get("inst_date")),
                    "기준일": normalize_str(row.get("basic_date")),
                    "source_crs": "WGS84(원본 lat/lon 그대로)",
                },
            )
        )

    report.id_uniqueness_verified = False
    report.source_specific_exceptions.append(
        "작품(work) 단위 레코드다 — 시설 단위 ID가 없고, 여러 작품이 같은 장소(공원 등)에 "
        "군집해 정확히 같은 좌표를 공유하는 사례가 많다(예: 구덕문화공원 6점). 이 세션에서는 "
        "'작품 = Place'인지 '설치 장소 = Place'인지 P2 판단 대상이라 결정하지 않고, 원본 그대로 "
        "작품 단위로 staging했다 — 좌표 군집은 duplicate_group_id로 자연히 드러난다."
    )
    dup_groups, dup_records = _flag_internal_duplicates(
        records, lambda rec: (rec["latitude"], rec["longitude"])
        if rec["latitude"] is not None and rec["longitude"] is not None else None
    )
    report.internal_duplicate_groups = dup_groups
    report.internal_duplicate_records = dup_records

    write_staging(name, records)
    write_report(name, report)
    print(
        f"{name}: raw={report.raw_input_count} included={report.included_in_staging} "
        f"dup_groups={dup_groups}(좌표 군집) reconcile={report.reconcile_ok()}"
    )


def main() -> None:
    process_heritage()
    process_busan_sports_shp()
    process_tourist_info_center()
    process_museum()
    process_rural_village()
    process_marina()
    process_gyeongnam_sports()
    process_ulsan_culture()
    process_fishing_port()
    process_kids_forest()
    process_fishing_spot()
    process_agrohealing()
    process_visitbusanpass()
    process_busan_public_art()


if __name__ == "__main__":
    main()
