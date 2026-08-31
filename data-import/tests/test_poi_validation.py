import csv
import json

import pytest

from data_import.poi_validation import (
    REQUIRED_COLUMNS,
    PoiValidationError,
    validate_samples,
    write_tour_api_poi_candidates,
)


def sample(**overrides):
    row = {
        "sample_id": "TOUR_API-12-1",
        "place_name": "테스트 박물관",
        "source_provider": "TOUR_API",
        "source_place_id": "1",
        "source_content_type_id": "12",
        "source_category": "VE070100",
        "primary_taxonomy": "POINT_OF_INTEREST",
        "poi_kind": "MUSEUM",
        "activity_offering_state": "UNKNOWN",
        "activity_offering_evidence": "",
        "event_state": "UNKNOWN",
        "event_evidence": "",
        "evidence": "공식 상세에서 박물관임을 확인",
        "review_status": "REVIEWED",
        "notes": "",
    }
    row.update(overrides)
    return row


def write_mapping(path):
    path.write_text(
        "source_content_type_id,source_lcls3_code,target_subtype_code,mapping_status\n"
        "12,HS010100,HISTORIC_SITE,CONFIRMED\n"
        "12,EX010100,EXPERIENCE_CENTER,CONFIRMED\n"
        "14,VE070100,MUSEUM,CONFIRMED\n",
        encoding="utf-8",
    )


def write_page(path, items):
    path.parent.mkdir(parents=True)
    path.write_text(
        json.dumps({"response": {"body": {"items": {"item": items}}}}, ensure_ascii=False),
        encoding="utf-8",
    )


def test_extracts_only_safe_poi_kinds_and_marks_presence_unknown(tmp_path) -> None:
    mapping = tmp_path / "mappings.csv"
    write_mapping(mapping)
    raw = tmp_path / "tour-api"
    write_page(
        raw / "12_관광지" / "page-00001.json",
        [
            {"contentid": "1", "title": "유적", "lclsSystm3": "HS010100"},
            {"contentid": "2", "title": "체험장", "lclsSystm3": "EX010100"},
        ],
    )
    write_page(
        raw / "14_문화시설" / "page-00001.json",
        [{"contentid": "3", "title": "박물관", "lclsSystm3": "VE070100"}],
    )
    output = tmp_path / "samples.csv"

    assert write_tour_api_poi_candidates(raw, mapping, output, per_kind=1) == 2
    rows = list(csv.DictReader(output.open(encoding="utf-8")))

    assert {row["poi_kind"] for row in rows} == {"HISTORIC_SITE", "MUSEUM"}
    assert {row["activity_offering_state"] for row in rows} == {"UNKNOWN"}
    assert {row["event_state"] for row in rows} == {"UNKNOWN"}


def test_extract_requires_raw_poi_records(tmp_path) -> None:
    mapping = tmp_path / "mappings.csv"
    write_mapping(mapping)

    with pytest.raises(PoiValidationError, match="no usable POI"):
        write_tour_api_poi_candidates(tmp_path / "tour-api", mapping, tmp_path / "samples.csv")


def test_confirmed_offering_or_event_requires_specific_evidence() -> None:
    summary = validate_samples(
        [sample(activity_offering_state="CONFIRMED_PRESENT")], minimum_samples=1
    )

    assert summary.invalid == 1
    assert any(issue.code == "MISSING_PRESENCE_EVIDENCE" for issue in summary.issues)


def test_rejects_poi_kind_when_review_moves_place_to_activity() -> None:
    summary = validate_samples([sample(primary_taxonomy="ACTIVITY")], minimum_samples=1)

    assert summary.invalid == 1
    assert any(issue.code == "POI_KIND_ON_NON_POI" for issue in summary.issues)


def test_reviewed_poi_sample_is_valid() -> None:
    summary = validate_samples([sample()], minimum_samples=1)

    assert summary.valid == 1
    assert summary.invalid == 0


def test_sample_contract_has_expected_columns() -> None:
    assert "poi_kind" in REQUIRED_COLUMNS
    assert "activity_offering_evidence" in REQUIRED_COLUMNS
