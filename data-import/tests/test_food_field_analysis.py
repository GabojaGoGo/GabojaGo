import json
from pathlib import Path

from data_import.tour_api.food_field_analysis import analyze_detail_directory


def response(item: dict[str, object] | None) -> bytes:
    items = {"item": [item]} if item is not None else ""
    return json.dumps(
        {
            "response": {
                "header": {"resultCode": "0000", "resultMsg": "OK"},
                "body": {"items": items, "totalCount": 1 if item is not None else 0},
            }
        }
    ).encode()


def test_analyze_food_fields_separates_missing_blank_and_source_empty(tmp_path: Path) -> None:
    (tmp_path / "1.json").write_bytes(
        response(
            {
                "parkingfood": " 가능 ",
                "opentimefood": "월요일 09:00<br>18:00",
                "restdatefood": "",
            }
        )
    )
    (tmp_path / "2.json").write_bytes(response({"parkingfood": "가능", "restdatefood": "매주 일요일"}))
    (tmp_path / "3.json").write_bytes(response(None))
    (tmp_path / "4.json").write_text("not json", encoding="utf-8")

    report = analyze_detail_directory(tmp_path)

    assert report["detail_responses"] == {"answered": 2, "source_empty": 1, "parse_failed": 1}
    parking = report["fields"]["parkingfood"]
    assert parking["top_values"] == [{"value": "가능", "count": 2}]
    assert parking["nonblank"] == 2
    assert report["fields"]["opentimefood"]["missing_key"] == 1
    assert report["fields"]["restdatefood"]["blank"] == 1
    assert report["fields"]["opentimefood"]["format_signals"] == {
        "contains_html": 1,
        "contains_time": 1,
        "contains_weekday": 1,
        "contains_korean_unit": 0,
    }
