import json

from data_import.taxonomy_sample_collector import (
    CityBounds,
    _deduplicate_pools,
    _to_candidates,
    collect_osm_cafe_samples,
    fetch_city_candidates,
)


def test_converts_named_osm_food_places_to_pending_review_rows() -> None:
    candidates = _to_candidates(
        "seoul",
        {"elements": [
            {"type": "node", "id": 1, "tags": {"amenity": "restaurant", "name": "식당 A"}},
            {"type": "way", "id": 2, "tags": {"amenity": "cafe", "name": "카페 B"}},
            {"type": "node", "id": 3, "tags": {"amenity": "restaurant"}},
        ]},
    )

    assert [candidate["source_category"] for candidate in candidates] == ["restaurant", "cafe"]
    assert candidates[0]["source_url"] == "https://www.openstreetmap.org/node/1"
    assert candidates[1]["review_status"] == "PENDING"
    assert candidates[0]["primary_taxonomy"] == ""


def test_uses_fallback_endpoint_after_network_failure() -> None:
    calls = []

    def open_url(request, timeout):
        calls.append(request.full_url)
        if len(calls) == 1:
            raise TimeoutError("first endpoint timeout")
        return json.dumps({"elements": [
            {"type": "node", "id": 1, "tags": {"amenity": "cafe", "name": "카페"}}
        ]}).encode()

    candidates = fetch_city_candidates(
        CityBounds("test", 1, 2, 3, 4),
        1.0,
        ("https://one.example", "https://two.example"),
        open_url,
    )

    assert calls == ["https://one.example", "https://two.example"]
    assert candidates[0]["place_name"] == "카페"


def test_collects_cafe_only_samples(tmp_path) -> None:
    request_count = 0

    def open_url(request, timeout):
        nonlocal request_count
        request_count += 1
        return json.dumps({"elements": [
            {"type": "node", "id": request_count, "tags": {"amenity": "cafe", "name": "카페"}},
            {"type": "node", "id": 1000 + request_count, "tags": {"amenity": "restaurant", "name": "식당"}},
        ]}).encode()

    output = tmp_path / "cafes.csv"
    count = collect_osm_cafe_samples(
        output,
        target=10,
        endpoints=("https://one.example",),
        open_url=open_url,
    )

    assert count == 10
    assert "restaurant" not in output.read_text(encoding="utf-8")


def test_deduplicates_osm_candidates_before_writing_review_csv() -> None:
    duplicate = _to_candidates(
        "seoul", {"elements": [{"type": "node", "id": 1, "tags": {"amenity": "cafe", "name": "카페"}}]}
    )[0]

    pools = _deduplicate_pools({"cafe": [duplicate, duplicate]})

    assert pools == {"cafe": [duplicate]}
