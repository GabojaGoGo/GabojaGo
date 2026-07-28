import json

import pytest

from data_import.tour_api.collector import (
    TourApiCollector,
    TourApiError,
    parse_tour_api_page,
)


def response(page_no: int = 1, total_count: int = 2, num_of_rows: int = 100) -> bytes:
    return json.dumps(
        {
            "response": {
                "header": {"resultCode": "0000", "resultMsg": "OK"},
                "body": {
                    "numOfRows": num_of_rows,
                    "pageNo": page_no,
                    "totalCount": total_count,
                    "items": {"item": [{"contentid": "1"}, {"contentid": "2"}]},
                },
            }
        }
    ).encode()


def test_parse_tour_api_page() -> None:
    page = parse_tour_api_page(response())

    assert page.page_no == 1
    assert page.total_count == 2
    assert page.total_pages == 1
    assert page.item_count == 2


def test_parse_rejects_api_error() -> None:
    raw = json.dumps(
        {
            "response": {
                "header": {"resultCode": "99", "resultMsg": "FAILED"},
                "body": {},
            }
        }
    ).encode()

    with pytest.raises(TourApiError, match="99 FAILED"):
        parse_tour_api_page(raw)


def test_collect_writes_page_and_reuses_it(tmp_path) -> None:
    calls = 0

    def open_url(_request, _timeout) -> bytes:
        nonlocal calls
        calls += 1
        return response()

    collector = TourApiCollector(
        service_key="test-key",
        output_root=tmp_path,
        request_interval=0,
        open_url=open_url,
    )

    first = collector.collect("12")
    second = collector.collect("12")

    assert calls == 1
    assert first.fetched_pages == 1
    assert second.reused_pages == 1
    assert (tmp_path / "12_관광지" / "page-00001.json").exists()


def test_collect_calculates_pages_from_requested_size(tmp_path) -> None:
    collector = TourApiCollector(
        service_key="test-key",
        output_root=tmp_path,
        num_of_rows=100,
        request_interval=0,
        open_url=lambda _request, _timeout: response(
            total_count=12_682,
            num_of_rows=82,
        ),
    )

    summary = collector.collect("12", max_pages=1)

    assert summary.total_pages == 127
