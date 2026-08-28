import json

import pytest

from data_import.taxonomy_validation import (
    TaxonomyValidationError,
    validate_samples,
    write_tour_api_candidates,
    write_tour_api_proposals,
)


def sample(**overrides):
    row = {
        "sample_id": "TOUR_API-1",
        "place_name": "테스트 돈까스",
        "source_provider": "TOUR_API",
        "source_place_id": "1",
        "source_url": "",
        "source_category": "FD020200",
        "primary_taxonomy": "RESTAURANT",
        "cuisine": "JAPANESE",
        "cuisine_variant": "",
        "dining_format": "",
        "signature_menu": "DONKATSU|UDON",
        "primary_beverage": "",
        "offering": "",
        "cafe_service_style": "",
        "evidence": "TourAPI 일식 분류, 메뉴판에 돈까스·우동 표기",
        "review_status": "REVIEWED",
        "notes": "",
    }
    row.update(overrides)
    return row


def test_valid_restaurant_sample_fits_taxonomy() -> None:
    summary = validate_samples([sample()], minimum_samples=1)

    assert summary.valid == 1
    assert summary.invalid == 0


def test_rejects_legacy_mixed_subtype() -> None:
    summary = validate_samples([sample(signature_menu="SNACK_FAST_FOOD")], minimum_samples=1)

    assert summary.invalid == 1
    assert any(issue.code == "LEGACY_SUBTYPE" for issue in summary.issues)


def test_requires_cuisine_variant_to_belong_to_cuisine() -> None:
    summary = validate_samples(
        [sample(cuisine="CHINESE", cuisine_variant="KOREAN_CHINESE")], minimum_samples=1
    )

    assert summary.valid == 1

    invalid = validate_samples(
        [sample(cuisine="JAPANESE", cuisine_variant="KOREAN_CHINESE")], minimum_samples=1
    )
    assert invalid.invalid == 1
    assert any(issue.code == "UNKNOWN_FACET_VALUE" for issue in invalid.issues)


def test_accepts_asian_cuisine_variant() -> None:
    summary = validate_samples(
        [sample(cuisine="ASIAN", cuisine_variant="VIETNAMESE")], minimum_samples=1
    )

    assert summary.valid == 1


def test_cafe_accepts_offering_and_service_style() -> None:
    summary = validate_samples(
        [
            sample(
                primary_taxonomy="CAFE",
                cuisine="",
                cuisine_variant="",
                dining_format="",
                signature_menu="",
                primary_beverage="COFFEE",
                offering="COFFEE|BAKERY",
                cafe_service_style="ROASTERY|STAY_FOCUSED",
            )
        ],
        minimum_samples=1,
    )

    assert summary.valid == 1


def test_marks_sample_without_evidence_or_facet_for_review() -> None:
    summary = validate_samples(
        [sample(cuisine="", cuisine_variant="", dining_format="", signature_menu="", evidence="")], minimum_samples=1
    )

    assert summary.needs_review == 1
    assert summary.invalid == 0


def test_extracts_real_candidates_from_tour_api_raw_page(tmp_path) -> None:
    raw_dir = tmp_path / "tour-api" / "39_음식점"
    raw_dir.mkdir(parents=True)
    raw_dir.joinpath("page-00001.json").write_text(
        json.dumps(
            {
                "response": {
                    "body": {
                        "items": {
                            "item": [
                                {"contentid": "1", "title": "첫 번째 식당", "lclsSystm3": "FD010100"},
                                {"contentid": "2", "title": "두 번째 카페", "lclsSystm3": "FD050100"},
                            ]
                        }
                    }
                }
            }
        ),
        encoding="utf-8",
    )
    output = tmp_path / "samples.csv"

    assert write_tour_api_candidates(tmp_path / "tour-api", output, sample_size=2) == 2
    assert "TOUR_API-1" in output.read_text(encoding="utf-8")


def test_extracts_candidates_from_classification_subdirectory(tmp_path) -> None:
    raw_dir = tmp_path / "tour-api" / "39_음식점" / "FD010100"
    raw_dir.mkdir(parents=True)
    raw_dir.joinpath("page-00001.json").write_text(
        json.dumps(
            {"response": {"body": {"items": {"item": [
                {"contentid": "1", "title": "분류 식당", "lclsSystm3": "FD010100"}
            ]}}}},
        ),
        encoding="utf-8",
    )
    output = tmp_path / "samples.csv"

    assert write_tour_api_candidates(tmp_path / "tour-api", output, sample_size=1) == 1
    assert "분류 식당" in output.read_text(encoding="utf-8")


def test_extract_requires_enough_collected_records(tmp_path) -> None:
    with pytest.raises(TaxonomyValidationError, match="only 0"):
        write_tour_api_candidates(tmp_path, tmp_path / "samples.csv", sample_size=1)


def test_keeps_broad_foreign_restaurant_category_unclassified_for_review(tmp_path) -> None:
    raw_dir = tmp_path / "tour-api" / "39_음식점" / "FD020400"
    raw_dir.mkdir(parents=True)
    raw_dir.joinpath("page-00001.json").write_text(
        json.dumps(
            {"response": {"body": {"items": {"item": [
                {"contentid": "1", "title": "사이공본가", "lclsSystm3": "FD020400"}
            ]}}}},
        ),
        encoding="utf-8",
    )
    output = tmp_path / "proposals.csv"

    assert write_tour_api_proposals(tmp_path / "tour-api", output, sample_size=1) == 1
    row = next(__import__("csv").DictReader(output.open(encoding="utf-8")))
    assert row["cuisine"] == ""
    assert row["cuisine_variant"] == ""
    assert row["review_status"] == "NEEDS_MANUAL"


def test_rejects_unknown_review_status_and_counts_only_cafes() -> None:
    summary = validate_samples(
        [sample(primary_taxonomy="BAR", review_status="DONE")], minimum_samples=1
    )

    assert summary.cafe_samples == 0
    assert summary.invalid == 1
    assert any(issue.code == "INVALID_REVIEW_STATUS" for issue in summary.issues)


def test_rejects_non_positive_minimum_sample_size() -> None:
    with pytest.raises(ValueError, match="minimum_samples"):
        validate_samples([sample()], minimum_samples=0)
