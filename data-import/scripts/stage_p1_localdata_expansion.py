"""P1 §1 확장 후보 중 미staging LOCALDATA 업종 + 표준데이터 3종 staging.

대상(other-source-survey.md §1 A표 "확장 후보 자격 있음"/그에 준하는 것만, 정본 우선):
  단일 파일 LOCALDATA: 농어촌민박업·단란주점영업·유흥주점영업·외국인관광도시민박업·
    한옥체험업·전통사찰·박물관및미술관(인허가)·공연장·지방문화원·대규모점포
  다중 파일 병합 LOCALDATA: 야영장(일반+자동차), 테마파크(일반+종합+기타),
    체육시설업(8업종 — 체육도장업·체력단련장업·골프연습장업·수영장업·골프장·승마장업·
    종합체육시설업·등록체육시설업)
  표준데이터 JSON: 전국전통시장표준데이터·전국도시공원정보표준데이터·전국휴양림표준데이터

기존 staging 원천(관광안내소·박물관미술관 표준데이터·비짓부산패스 등)은 건드리지 않는다.
raw는 수정하지 않는다. STATUS_MAP 보완은 `data_import.cleaning`에서 이미 완료했고
base 5종 회귀 검증도 별도로 끝냈다 — 이 스크립트는 그 보완된 매핑을 그대로 쓴다.
"""

from __future__ import annotations

import csv
import json
from collections import Counter

from stage_p1_sources import PROJECT_ROOT, SourceReport, make_record, write_report, write_staging

from data_import.cleaning import (
    check_coordinate,
    epsg5174_to_wgs84,
    norm_nospace,
    normalize_status,
    normalize_str,
    to_float,
)

RAW = PROJECT_ROOT / "data/raw"
BASE_LOCALDATA_DIR = RAW / "standard/인허가_지역별"
REGIONS = ["부산광역시", "울산광역시", "경상남도"]

# ---------------------------------------------------------------------------
# 원천 식별자 원칙 재확인: 관리번호를 단독 dedupe key로 쓰지 않는다. 이 스크립트는
# base와 동일하게 (지역, 관리번호)로 내부 중복 "후보"만 플래그하고 자동 병합하지
# 않는다 — A(확정 동일시설) 여부는 별도 감사에서 판정한다.
# ---------------------------------------------------------------------------


def process_single_file_localdata(
    name: str,
    files: dict[str, str],
    *,
    exposure_caveat: str | None = None,
) -> SourceReport:
    """단일 파일짜리 LOCALDATA 업종 — base process_localdata와 동일한 규칙."""
    report = SourceReport(source=name)
    report.id_field_used = "관리번호"
    status_values: Counter[str] = Counter()

    all_records = []
    for region, rel_path in files.items():
        path = PROJECT_ROOT / rel_path
        if not path.exists():
            report.notes.append(f"{region} 파일 없음: {rel_path}")
            continue
        with path.open(encoding="cp949", errors="replace", newline="") as f:
            rows = list(csv.DictReader(f))

        id_counter: Counter[str] = Counter((row.get("관리번호") or "").strip() for row in rows)
        records = []
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
            if mgmt_id and dup_count > 1:
                duplicate_group_id = f"{name}:{region}:{mgmt_id}"
                manual_review = True

            prov = {
                "raw_file": rel_path,
                "region": region,
                "coord_x_epsg5174": x,
                "coord_y_epsg5174": y,
                "phone": normalize_str(row.get("전화번호")),
                "인허가일자": normalize_str(row.get("인허가일자")),
            }
            if exposure_caveat:
                prov["exposure_caveat"] = exposure_caveat

            rec = make_record(
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
                provenance=prov,
            )
            records.append(rec)
        all_records.extend(records)

    dup_groups = {r["duplicate_group_id"] for r in all_records if r["duplicate_group_id"]}
    report.duplicate_flagged_groups = len(dup_groups)
    report.duplicate_flagged_records = sum(1 for r in all_records if r["duplicate_group_id"])
    report.original_status_values = dict(status_values.most_common())
    report.included_before_dedupe = report.included_in_staging
    report.final_included_after_dedupe = report.included_in_staging

    write_staging(name, "부울경", all_records)
    write_report(name, report)
    print(
        f"{name}: raw={report.raw_input_count} included={report.included_in_staging} "
        f"closed_excl={report.status_excluded} dup_groups={report.duplicate_flagged_groups} "
        f"reconcile={report.reconcile_ok()}"
    )
    return report


def process_merged_localdata(name: str, sub_sources: dict[str, dict[str, str]]) -> SourceReport:
    """여러 업종 파일을 하나의 Place staging 원천으로 합친다(예: 야영장=일반+자동차).
    sub_sources: {업종라벨: {region: rel_path}}"""
    report = SourceReport(source=name)
    report.id_field_used = "관리번호"
    status_values: Counter[str] = Counter()
    sub_counts: Counter[str] = Counter()

    all_records = []
    for sub_label, files in sub_sources.items():
        for region, rel_path in files.items():
            path = PROJECT_ROOT / rel_path
            if not path.exists():
                report.notes.append(f"{sub_label}/{region} 파일 없음: {rel_path}")
                continue
            with path.open(encoding="cp949", errors="replace", newline="") as f:
                rows = list(csv.DictReader(f))

            id_counter: Counter[str] = Counter((row.get("관리번호") or "").strip() for row in rows)
            for row in rows:
                report.raw_input_count += 1
                sub_counts[sub_label] += 1
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
                if mgmt_id and dup_count > 1:
                    duplicate_group_id = f"{name}:{sub_label}:{region}:{mgmt_id}"
                    manual_review = True

                rec = make_record(
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
                    provenance={
                        "raw_file": rel_path,
                        "region": region,
                        "업종": sub_label,
                        "coord_x_epsg5174": x,
                        "coord_y_epsg5174": y,
                        "phone": normalize_str(row.get("전화번호")),
                        "업태구분명": normalize_str(row.get("업태구분명")),
                        "문화체육업종명": normalize_str(row.get("문화체육업종명")),
                    },
                )
                all_records.append(rec)

    dup_groups = {r["duplicate_group_id"] for r in all_records if r["duplicate_group_id"]}
    report.duplicate_flagged_groups = len(dup_groups)
    report.duplicate_flagged_records = sum(1 for r in all_records if r["duplicate_group_id"])
    report.original_status_values = dict(status_values.most_common())
    report.included_before_dedupe = report.included_in_staging
    report.final_included_after_dedupe = report.included_in_staging
    report.notes.append(f"업종별 raw 건수: {dict(sub_counts)}")
    report.notes.append(
        "이 원천은 서로 다른 업종 파일을 하나의 staging 원천으로 합친 것이다 — 원본 업종 구분은 "
        "provenance.업종에 보존했고, PlaceType/Subtype 대응은 이번 패스에서 결정하지 않는다."
    )

    write_staging(name, "부울경", all_records)
    write_report(name, report)
    print(
        f"{name}: raw={report.raw_input_count} included={report.included_in_staging} "
        f"closed_excl={report.status_excluded} dup_groups={report.duplicate_flagged_groups} "
        f"reconcile={report.reconcile_ok()} sub={dict(sub_counts)}"
    )
    return report


def _region_files(filename: str) -> dict[str, str]:
    return {
        region: f"data/raw/standard/인허가_지역별/{region}/{filename}"
        for region in REGIONS
    }


# ---------------------------------------------------------------------------
# 표준데이터 JSON 3종 (전통시장/도시공원/휴양림) — 상태 컬럼 자체가 없어 항상 UNKNOWN
# ---------------------------------------------------------------------------


def process_standard_json_source(
    *, name: str, raw_file: str, name_field: str, lot_field: str | None, road_field: str,
    phone_field: str, extra: dict[str, str],
) -> SourceReport:
    report = SourceReport(source=name)
    report.id_field_used = None
    path = RAW / raw_file
    all_rows = json.loads(path.read_text(encoding="utf-8"))

    def in_buul(row: dict) -> bool:
        candidates = [row.get(road_field)]
        if lot_field:
            candidates.append(row.get(lot_field))
        return any((c or "").startswith(("부산", "울산", "경남", "경상남도")) for c in candidates)

    buul = [r for r in all_rows if in_buul(r)]

    id_counter: Counter[tuple] = Counter()
    keys = []
    for r in buul:
        k = (norm_nospace(r.get(name_field)), normalize_str(r.get(road_field)))
        keys.append(k)
        id_counter[k] += 1

    records = []
    for row, key in zip(buul, keys):
        report.raw_input_count += 1
        report.included_in_staging += 1
        report.unknown += 1

        lat = to_float(row.get("LATITUDE"))
        lon = to_float(row.get("LONGITUDE"))
        coord_status = check_coordinate(lat, lon)
        if coord_status == "VALID":
            report.coord_valid += 1
        elif coord_status == "MISSING":
            report.coord_missing += 1
        elif coord_status == "ZERO":
            report.coord_zero += 1
        elif coord_status == "OUT_OF_REGION":
            report.coord_out_of_region += 1

        lot = normalize_str(row.get(lot_field)) if lot_field else None
        road = normalize_str(row.get(road_field))

        dup_count = id_counter.get(key, 0)
        duplicate_group_id = None
        manual_review = False
        if key[0] and dup_count > 1:
            duplicate_group_id = f"{name}:{key[0]}:{key[1]}"
            manual_review = True

        provenance = {
            "raw_file": raw_file,
            "source_crs": "WGS84(원본 LATITUDE/LONGITUDE 그대로)",
            "REFERENCE_DATE": normalize_str(row.get("REFERENCE_DATE")),
        }
        for label, field_name in extra.items():
            provenance[label] = normalize_str(row.get(field_name))

        records.append(
            make_record(
                source=name,
                source_record_id=None,
                original_status=None,
                normalized_status="UNKNOWN",
                normalized_name=normalize_str(row.get(name_field)),
                normalized_address_lot=lot,
                normalized_address_road=road,
                latitude=lat,
                longitude=lon,
                coordinate_status=coord_status,
                exclusion_reason=None,
                duplicate_group_id=duplicate_group_id,
                duplicate_count=dup_count,
                manual_review_required=manual_review,
                provenance=provenance,
            )
        )

    report.id_uniqueness_verified = False
    report.included_before_dedupe = report.included_in_staging
    report.final_included_after_dedupe = report.included_in_staging
    dup_groups = {r["duplicate_group_id"] for r in records if r["duplicate_group_id"]}
    report.duplicate_flagged_groups = len(dup_groups)
    report.duplicate_flagged_records = sum(1 for r in records if r["duplicate_group_id"])
    report.notes.append("시설 단위 ID 필드가 없어 내부 중복은 이름+도로명주소로만 플래그했다")

    write_staging(name, "부울경", records)
    write_report(name, report)
    print(
        f"{name}: raw={report.raw_input_count} included={report.included_in_staging} "
        f"dup_groups={report.duplicate_flagged_groups} reconcile={report.reconcile_ok()}"
    )
    return report


def main() -> None:
    reports = {}

    single_targets = [
        ("농어촌민박업", "문화_농어촌민박업.csv", None),
        ("단란주점영업", "식품_단란주점영업.csv", None),
        (
            "유흥주점영업",
            "식품_유흥주점영업.csv",
            (
                "업태 44.9%가 룸살롱·카바레·고고클럽 — 서비스 노출 적합성은 P2/서비스 정책 판단 대상. "
                "이번 패스는 raw 사실 보존만 한다(노출 여부 결정 아님)"
            ),
        ),
        ("외국인관광도시민박업", "문화_외국인관광도시민박업.csv", None),
        ("한옥체험업", "문화_한옥체험업.csv", None),
        ("전통사찰", "문화_전통사찰.csv", None),
        ("인허가박물관미술관", "문화_박물관 및 미술관.csv", None),
        ("공연장", "문화_공연장.csv", None),
        ("지방문화원", "문화_지방문화원.csv", None),
        ("대규모점포", "생활_대규모점포.csv", None),
    ]
    for name, filename, caveat in single_targets:
        reports[name] = process_single_file_localdata(name, _region_files(filename), exposure_caveat=caveat)

    reports["야영장"] = process_merged_localdata("야영장", {
        "일반야영장업": _region_files("문화_일반야영장업.csv"),
        "자동차야영장업": _region_files("문화_자동차야영장업.csv"),
    })

    reports["테마파크"] = process_merged_localdata("테마파크", {
        "일반테마파크업": _region_files("문화_일반테마파크업.csv"),
        "종합테마파크업": _region_files("문화_종합테마파크업.csv"),
        "테마파크업(기타)": _region_files("문화_테마파크업(기타).csv"),
    })

    reports["체육시설업"] = process_merged_localdata("체육시설업", {
        "체육도장업": _region_files("생활_체육도장업.csv"),
        "체력단련장업": _region_files("생활_체력단련장업.csv"),
        "골프연습장업": _region_files("생활_골프연습장업.csv"),
        "수영장업": _region_files("생활_수영장업.csv"),
        "골프장": _region_files("생활_골프장.csv"),
        "승마장업": _region_files("생활_승마장업.csv"),
        "종합체육시설업": _region_files("생활_종합체육시설업.csv"),
        "등록체육시설업": _region_files("생활_등록체육시설업.csv"),
    })

    reports["전통시장"] = process_standard_json_source(
        name="전통시장", raw_file="standard/전통시장/전국전통시장표준데이터.json",
        name_field="MRKT_NM", lot_field="LNMADR", road_field="RDNMADR", phone_field="PHONE_NUMBER",
        extra={"시장유형": "MRKT_TYPE", "개설주기": "MRKT_ESTBL_CYCLE", "취급품목": "TRTMNT_PRDLST",
               "전화번호": "PHONE_NUMBER", "홈페이지": "HOMEPAGE_URL"},
    )
    reports["도시공원"] = process_standard_json_source(
        name="도시공원", raw_file="standard/도시공원/전국도시공원정보표준데이터.json",
        name_field="PARK_NM", lot_field="LNMADR", road_field="RDNMADR", phone_field="PHONE_NUMBER",
        extra={"공원구분": "PARK_SE", "면적": "PARK_AR", "전화번호": "PHONE_NUMBER",
               "지정고시일": "APPN_NTFC_DATE"},
    )
    reports["휴양림"] = process_standard_json_source(
        name="휴양림", raw_file="standard/휴양림/전국휴양림표준데이터.json",
        name_field="RCRFRST_NM", lot_field=None, road_field="RDNMADR", phone_field="TELEPHONE_NUMBER",
        extra={"휴양림구분": "RCRFRST_TYPE", "숙박가능여부": "STAYNG_POSBL_YN",
               "전화번호": "TELEPHONE_NUMBER", "홈페이지": "HOMEPAGE_URL", "수용인원": "ACEPTNC_CO"},
    )

    print()
    print("=== 요약 ===")
    for name, r in reports.items():
        print(f"{name}: raw={r.raw_input_count} included={r.included_in_staging} reconcile={r.reconcile_ok()}")


if __name__ == "__main__":
    main()
