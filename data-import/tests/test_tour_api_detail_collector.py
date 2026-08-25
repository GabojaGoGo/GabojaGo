import json
from pathlib import Path

import pytest

from data_import.tour_api.collector import TourApiError
from data_import.tour_api.detail_collector import (
    PlaceRef,
    TourApiDetailCollector,
    load_place_refs,
    parse_detail_item,
    stratified_sample,
)


def detail_response(**fields: object) -> bytes:
    item = {"contentid": "1", "contenttypeid": "12", **fields}
    return json.dumps(
        {
            "response": {
                "header": {"resultCode": "0000", "resultMsg": "OK"},
                "body": {"items": {"item": [item]}},
            }
        }
    ).encode()


def list_page(*items: dict[str, object]) -> bytes:
    return json.dumps(
        {
            "response": {
                "header": {"resultCode": "0000", "resultMsg": "OK"},
                "body": {
                    "numOfRows": 100,
                    "pageNo": 1,
                    "totalCount": len(items),
                    "items": {"item": list(items)},
                },
            }
        }
    ).encode()


def ref(content_id: str, sigungu: str = "110", category: str = "AC") -> PlaceRef:
    return PlaceRef(
        content_id=content_id,
        content_type_id="12",
        title=f"place-{content_id}",
        region_code="26",
        sigungu_code=sigungu,
        category_code=category,
    )


def test_parse_detail_item() -> None:
    item = parse_detail_item(detail_response(usetime="09:00~18:00"))

    assert item["usetime"] == "09:00~18:00"


def test_parse_detail_rejects_api_error() -> None:
    raw = json.dumps(
        {"response": {"header": {"resultCode": "22", "resultMsg": "LIMITED"}, "body": {}}}
    ).encode()

    with pytest.raises(TourApiError, match="22 LIMITED"):
        parse_detail_item(raw)


def test_load_place_refs_keeps_only_target_regions(tmp_path: Path) -> None:
    source_dir = tmp_path / "12_관광지"
    source_dir.mkdir()
    (source_dir / "page-00001.json").write_bytes(
        list_page(
            {"contentid": "1", "title": "부산", "lDongRegnCd": "26", "lclsSystm1": "AC"},
            {"contentid": "2", "title": "서울", "lDongRegnCd": "11", "lclsSystm1": "AC"},
            {"contentid": "3", "title": "경남", "lDongRegnCd": "48", "lclsSystm1": "AC"},
        )
    )

    refs = load_place_refs(tmp_path, "12")

    assert [item.content_id for item in refs] == ["1", "3"]


def test_load_place_refs_drops_duplicate_content_ids(tmp_path: Path) -> None:
    source_dir = tmp_path / "12_관광지"
    source_dir.mkdir()
    item = {"contentid": "1", "title": "부산", "lDongRegnCd": "26", "lclsSystm1": "AC"}
    (source_dir / "page-00001.json").write_bytes(list_page(item))
    (source_dir / "page-00002.json").write_bytes(list_page(item))

    assert len(load_place_refs(tmp_path, "12")) == 1


def test_stratified_sample_is_deterministic() -> None:
    refs = [ref(str(index), category=f"AC{index % 3}") for index in range(30)]

    first = stratified_sample(refs, 9, seed=1)
    second = stratified_sample(refs, 9, seed=1)

    assert [item.content_id for item in first] == [item.content_id for item in second]
    assert len(first) == 9


def test_stratified_sample_covers_every_stratum() -> None:
    refs = [ref(str(index), category=f"AC{index % 5}") for index in range(50)]

    sample = stratified_sample(refs, 10, seed=1)

    assert len({item.category_code for item in sample}) == 5


def test_stratum_ignores_sigungu() -> None:
    same = [ref("1", sigungu="110"), ref("2", sigungu="140")]

    assert same[0].stratum == same[1].stratum


def test_stratified_sample_allocates_in_proportion() -> None:
    big = [ref(f"b{index}", category="AC") for index in range(90)]
    small = [ref(f"s{index}", category="VE") for index in range(10)]

    sample = stratified_sample(big + small, 20, seed=1)
    picked = [item.category_code for item in sample]

    # 큰 층이 작은 층보다 확실히 많이 뽑혀야 한다. 층마다 1건씩 주는 방식이면 깨진다.
    assert picked.count("AC") > picked.count("VE") * 3


def test_stratified_sample_returns_all_when_size_exceeds_population() -> None:
    refs = [ref("1"), ref("2")]

    assert len(stratified_sample(refs, 10)) == 2


@pytest.mark.parametrize("size", [1, 12, 37, 148, 200, 499])
def test_stratified_sample_returns_exact_size(size: int) -> None:
    refs = [ref(str(index), category=f"AC{index % 12}") for index in range(500)]

    assert len(stratified_sample(refs, size, seed=7)) == size


def test_stratified_sample_never_exceeds_stratum_size() -> None:
    refs = [ref(f"b{index}", category="AC") for index in range(50)]
    refs += [ref("s1", category="VE")]

    sample = stratified_sample(refs, 51, seed=7)

    assert len(sample) == 51
    assert sum(1 for item in sample if item.category_code == "VE") == 1


def test_collect_reuses_existing_files(tmp_path: Path) -> None:
    calls: list[str] = []

    def open_url(request, timeout):
        calls.append(request.full_url)
        return detail_response(usetime="상시")

    collector = TourApiDetailCollector(
        service_key="key",
        output_root=tmp_path,
        request_interval=0.0,
        open_url=open_url,
    )
    refs = [ref("1")]

    first = collector.collect(refs, content_type_id="12", population=1)
    second = collector.collect(refs, content_type_id="12", population=1)

    assert first.fetched == 1
    assert second.fetched == 0
    assert second.reused == 1
    assert len(calls) == 1


def test_collect_counts_fill_rate_ignoring_empty_and_zero(tmp_path: Path) -> None:
    def open_url(request, timeout):
        return detail_response(usetime="상시", parking="", spendtime="0")

    collector = TourApiDetailCollector(
        service_key="key",
        output_root=tmp_path,
        request_interval=0.0,
        open_url=open_url,
    )

    summary = collector.collect([ref("1")], content_type_id="12", population=1)

    assert summary.fill_rates() == {"usetime": 100.0}


def empty_response() -> bytes:
    """원천에 상세가 없는 장소. resultCode는 0000이고 items가 비어 있다."""
    return json.dumps(
        {
            "response": {
                "header": {"resultCode": "0000", "resultMsg": "OK"},
                "body": {"items": "", "numOfRows": 0, "pageNo": 1, "totalCount": 0},
            }
        }
    ).encode()


def test_parse_detail_item_returns_none_for_empty_source() -> None:
    assert parse_detail_item(empty_response()) is None


def test_collect_counts_empty_apart_from_failure(tmp_path: Path) -> None:
    def open_url(request, timeout):
        return empty_response()

    collector = TourApiDetailCollector(
        service_key="key",
        output_root=tmp_path,
        request_interval=0.0,
        open_url=open_url,
    )

    summary = collector.collect([ref("1")], content_type_id="28", population=1)

    assert summary.empty == 1
    assert summary.failed == 0
    assert summary.failures == []


def test_empty_response_is_not_retried(tmp_path: Path) -> None:
    calls: list[str] = []

    def open_url(request, timeout):
        calls.append(request.full_url)
        return empty_response()

    collector = TourApiDetailCollector(
        service_key="key",
        output_root=tmp_path,
        request_interval=0.0,
        max_retries=3,
        open_url=open_url,
    )

    collector.collect([ref("1")], content_type_id="28", population=1)

    # 원천에 값이 없는 것은 오류가 아니므로 재시도하지 않는다. 재시도하면 쿼터를 버린다.
    assert len(calls) == 1


def test_fill_rate_denominator_excludes_empty(tmp_path: Path) -> None:
    def open_url(request, timeout):
        if "contentId=2" in request.full_url:
            return empty_response()
        return detail_response(usetime="상시")

    collector = TourApiDetailCollector(
        service_key="key",
        output_root=tmp_path,
        request_interval=0.0,
        open_url=open_url,
    )

    summary = collector.collect([ref("1"), ref("2")], content_type_id="28", population=2)

    # 상세가 존재한 1건 기준 100%다. 원천없음 1건을 분모에 넣으면 50%로 왜곡된다.
    assert summary.empty == 1
    assert summary.fill_rates() == {"usetime": 100.0}


def test_collect_records_failure_without_stopping(tmp_path: Path) -> None:
    def open_url(request, timeout):
        if "contentId=1" in request.full_url:
            raise TimeoutError
        return detail_response(usetime="상시")

    collector = TourApiDetailCollector(
        service_key="key",
        output_root=tmp_path,
        request_interval=0.0,
        max_retries=1,
        open_url=open_url,
    )

    summary = collector.collect([ref("1"), ref("2")], content_type_id="12", population=2)

    assert summary.failed == 1
    assert summary.fetched == 1
    assert len(summary.failures) == 1
