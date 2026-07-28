from decimal import Decimal

import pytest

from data_import.tour_api.importer import (
    RegionTarget,
    SubtypeMapping,
    TourApiImportError,
    load_subtype_mappings,
    resolve_region,
    to_place_record,
)


def item(**overrides):
    source = {
        "contentid": "100",
        "contenttypeid": "39",
        "title": "테스트 식당",
        "addr1": "부산광역시 해운대구 우동",
        "addr2": "",
        "mapx": "129.12345678",
        "mapy": "35.12345678",
        "cat3": "",
        "tel": "",
        "firstimage": "",
        "firstimage2": "",
    }
    source.update(overrides)
    return source


def test_resolve_region_uses_most_specific_address_prefix() -> None:
    item_data = item(addr1="경상남도 창원시 성산구", lDongRegnCd="48")
    area_by_code = {"48": RegionTarget(1, "gyeongnam", "경상남도")}
    targets = [
        RegionTarget(2, "gyeongnam.changwon", "경상남도 창원시"),
        RegionTarget(1, "gyeongnam", "경상남도"),
    ]

    matched = resolve_region(item_data, area_by_code, targets)

    assert matched is not None
    assert matched.region_key == "gyeongnam.changwon"


def test_resolve_region_uses_legal_code_when_address_is_missing() -> None:
    seoul = RegionTarget(1, "seoul", "서울특별시")

    matched = resolve_region(
        item(addr1="", lDongRegnCd="11"),
        {"11": seoul},
        [seoul],
    )

    assert matched == seoul


def test_to_place_record_maps_cafe_and_coordinates() -> None:
    region = RegionTarget(1, "busan", "부산광역시")

    mapping = SubtypeMapping(
        "39",
        "FD050100",
        "CAFE",
        "GENERAL_CAFE",
        "일반 카페",
        "CONFIRMED",
    )

    record = to_place_record(item(lclsSystm3="FD050100"), region, [mapping])

    assert record.place_type == "CAFE"
    assert record.latitude == Decimal("35.1234568")
    assert record.longitude == Decimal("129.1234568")


def test_load_subtype_mappings(tmp_path) -> None:
    mapping_path = tmp_path / "mapping.csv"
    mapping_path.write_text(
        "source_content_type_id,source_lcls3_code,target_place_type,"
        "target_subtype_code,target_subtype_name,mapping_status\n"
        "12,NA010100,TOURIST_SPOT,MOUNTAIN,산·오름,CONFIRMED\n"
        "12,NA010100,TOURIST_SPOT,PHOTO_SPOT,사진 명소,NEEDS_REVIEW\n",
        encoding="utf-8",
    )

    mappings = load_subtype_mappings(mapping_path)

    assert [mapping.target_subtype_code for mapping in mappings[("12", "NA010100")]] == [
        "MOUNTAIN",
        "PHOTO_SPOT",
    ]
    assert mappings[("12", "NA010100")][0].place_category_status == "INCLUDED"
    assert mappings[("12", "NA010100")][1].place_category_status == "NEED_REVIEW"


def test_load_subtype_mappings_rejects_duplicate_target(tmp_path) -> None:
    mapping_path = tmp_path / "mapping.csv"
    mapping_path.write_text(
        "source_content_type_id,source_lcls3_code,target_place_type,"
        "target_subtype_code,target_subtype_name,mapping_status\n"
        "12,NA010100,TOURIST_SPOT,MOUNTAIN,산·오름,CONFIRMED\n"
        "12,NA010100,TOURIST_SPOT,MOUNTAIN,산·오름,CONFIRMED\n",
        encoding="utf-8",
    )

    with pytest.raises(TourApiImportError, match="Duplicate subtype target"):
        load_subtype_mappings(mapping_path)


def test_review_mapping_becomes_need_review() -> None:
    mapping = SubtypeMapping(
        "12",
        "VE010800",
        "TOURIST_SPOT",
        "OBSERVATORY",
        "전망대",
        "NEEDS_REVIEW",
    )

    assert mapping.place_category_status == "NEED_REVIEW"


def test_to_place_record_rejects_missing_coordinate() -> None:
    region = RegionTarget(1, "busan", "부산광역시")

    with pytest.raises(ValueError, match="missing mapx"):
        to_place_record(item(mapx=""), region)


def test_to_place_record_rejects_zero_coordinate() -> None:
    region = RegionTarget(1, "busan", "부산광역시")

    with pytest.raises(ValueError, match="must not be zero"):
        to_place_record(item(mapx="0", mapy="0"), region)


def test_to_place_record_limits_phone_to_database_column() -> None:
    region = RegionTarget(1, "busan", "부산광역시")

    record = to_place_record(item(tel="1" * 123), region)

    assert record.phone == "1" * 100
