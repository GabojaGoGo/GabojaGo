"""Collect real Korean food-place candidates for manual taxonomy review."""

from __future__ import annotations

import json
import random
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from data_import.taxonomy_validation import REQUIRED_COLUMNS, write_samples

OVERPASS_ENDPOINTS = (
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass-api.de/api/interpreter",
)


@dataclass(frozen=True)
class CityBounds:
    key: str
    south: float
    west: float
    north: float
    east: float


KOREAN_CITY_BOUNDS = (
    CityBounds("seoul", 37.53, 126.93, 37.60, 127.08),
    CityBounds("busan", 35.12, 129.02, 35.22, 129.14),
    CityBounds("daegu", 35.82, 128.52, 35.90, 128.65),
    CityBounds("incheon", 37.43, 126.62, 37.52, 126.75),
    CityBounds("gwangju", 35.13, 126.82, 35.20, 126.95),
    CityBounds("daejeon", 36.31, 127.35, 36.38, 127.46),
    CityBounds("ulsan", 35.50, 129.25, 35.60, 129.36),
    CityBounds("sejong", 36.45, 127.25, 36.52, 127.36),
    CityBounds("jeju_city", 33.47, 126.48, 33.54, 126.58),
    CityBounds("gangneung", 37.73, 128.85, 37.79, 128.94),
)
OpenUrl = Callable[[Request, float], bytes]


class SampleCollectionError(RuntimeError):
    pass


def _default_open_url(request: Request, timeout: float) -> bytes:
    with urlopen(request, timeout=timeout) as response:
        return response.read()


def collect_osm_food_samples(
    output: Path,
    *,
    target_per_type: int = 50,
    seed: int = 20260814,
    timeout: float = 30.0,
    endpoints: tuple[str, ...] = OVERPASS_ENDPOINTS,
    open_url: OpenUrl = _default_open_url,
) -> int:
    """Write geographically distributed restaurant and cafe candidates.

    OSM tags discover candidates only; reviewers supply Canonical decisions and
    authoritative menu/detail evidence. The seed makes selection reproducible.
    """
    _validate_collection_arguments(target_per_type, timeout, endpoints, "target_per_type")
    if target_per_type < len(KOREAN_CITY_BOUNDS):
        raise ValueError("target_per_type must cover every configured city")
    pools = {"restaurant": [], "cafe": []}
    failures: list[str] = []
    for city in KOREAN_CITY_BOUNDS:
        try:
            for candidate in fetch_city_candidates(city, timeout, endpoints, open_url):
                pools[candidate["source_category"]].append(candidate)
        except SampleCollectionError as error:
            failures.append(f"{city.key}: {error}")

    selected = _select_balanced(
        _deduplicate_pools(pools), target_per_type, seed, ("restaurant", "cafe")
    )
    if len(selected) != target_per_type * 2:
        raise SampleCollectionError(
            f"could not collect {target_per_type} restaurants and cafes: "
            + ("; ".join(failures) or "insufficient named candidates")
        )
    write_samples(output, selected)
    return len(selected)


def collect_osm_cafe_samples(
    output: Path,
    *,
    target: int = 300,
    seed: int = 20260814,
    timeout: float = 30.0,
    endpoints: tuple[str, ...] = OVERPASS_ENDPOINTS,
    open_url: OpenUrl = _default_open_url,
) -> int:
    """Write geographically balanced cafe-only candidates for facet review."""
    _validate_collection_arguments(target, timeout, endpoints, "target")
    if target < len(KOREAN_CITY_BOUNDS):
        raise ValueError("target must cover every configured city")
    pool: list[dict[str, str]] = []
    failures: list[str] = []
    for city in KOREAN_CITY_BOUNDS:
        try:
            pool.extend(
                candidate
                for candidate in fetch_city_candidates(city, timeout, endpoints, open_url)
                if candidate["source_category"] == "cafe"
            )
        except SampleCollectionError as error:
            failures.append(f"{city.key}: {error}")

    selected = _select_balanced(_deduplicate_pools({"cafe": pool}), target, seed, ("cafe",))
    if len(selected) != target:
        raise SampleCollectionError(
            f"could not collect {target} cafes: " + ("; ".join(failures) or "insufficient named candidates")
        )
    write_samples(output, selected)
    return len(selected)


def fetch_city_candidates(
    city: CityBounds, timeout: float, endpoints: tuple[str, ...], open_url: OpenUrl
) -> list[dict[str, str]]:
    """Fetch both food candidate types in one request so fallback is per city."""
    query = (
        "[out:json][timeout:25];"
        "("
        f"nwr[\"amenity\"=\"restaurant\"]({city.south},{city.west},{city.north},{city.east});"
        f"nwr[\"amenity\"=\"cafe\"]({city.south},{city.west},{city.north},{city.east});"
        ");"
        "out center 250;"
    )
    errors: list[str] = []
    for endpoint in endpoints:
        request = Request(
            endpoint,
            data=query.encode(),
            headers={"Content-Type": "application/x-www-form-urlencoded", "User-Agent": "GabojaGO-taxonomy-validator/0.1"},
            method="POST",
        )
        try:
            return _to_candidates(city.key, json.loads(open_url(request, timeout).decode()))
        except (HTTPError, URLError, OSError, TimeoutError, UnicodeDecodeError, json.JSONDecodeError) as error:
            errors.append(f"{endpoint}: {error}")
    raise SampleCollectionError("all endpoints failed: " + " | ".join(errors))


def _to_candidates(city_key: str, payload: dict) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for element in payload.get("elements", []):
        tags = element.get("tags") or {}
        amenity, name = tags.get("amenity"), str(tags.get("name", "")).strip()
        osm_type, osm_id = str(element.get("type", "")).strip(), str(element.get("id", "")).strip()
        if amenity not in {"restaurant", "cafe"} or not name or not osm_type or not osm_id:
            continue
        source_id = f"{osm_type}/{osm_id}"
        row = {column: "" for column in REQUIRED_COLUMNS}
        row.update({
            "sample_id": f"OSM-{osm_type.upper()}-{osm_id}",
            "place_name": name,
            "source_provider": "OPENSTREETMAP",
            "source_place_id": source_id,
            "source_url": f"https://www.openstreetmap.org/{source_id}",
            "source_category": amenity,
            "review_status": "PENDING",
            "notes": f"collection_city={city_key}; osm_cuisine={tags.get('cuisine', '')}",
        })
        rows.append(row)
    return rows


def _validate_collection_arguments(
    target: int, timeout: float, endpoints: tuple[str, ...], target_name: str
) -> None:
    if target < 1:
        raise ValueError(f"{target_name} must be at least 1")
    if timeout <= 0:
        raise ValueError("timeout must be greater than zero")
    if not endpoints:
        raise ValueError("at least one Overpass endpoint is required")


def _deduplicate_pools(pools: dict[str, list[dict[str, str]]]) -> dict[str, list[dict[str, str]]]:
    result: dict[str, list[dict[str, str]]] = {}
    for amenity, candidates in pools.items():
        seen_ids: set[str] = set()
        result[amenity] = []
        for candidate in candidates:
            source_id = candidate["source_place_id"]
            if source_id not in seen_ids:
                seen_ids.add(source_id)
                result[amenity].append(candidate)
    return result


def _select_balanced(
    pools: dict[str, list[dict[str, str]]],
    target: int,
    seed: int,
    amenities: tuple[str, ...],
) -> list[dict[str, str]]:
    rng, selected = random.Random(seed), []
    for amenity in amenities:
        by_city = {city.key: [] for city in KOREAN_CITY_BOUNDS}
        for candidate in pools[amenity]:
            city = candidate["notes"].split(";", 1)[0].removeprefix("collection_city=")
            by_city[city].append(candidate)
        for candidates in by_city.values():
            rng.shuffle(candidates)
        while len([row for row in selected if row["source_category"] == amenity]) < target:
            progressed = False
            for city in KOREAN_CITY_BOUNDS:
                if by_city[city.key] and len([row for row in selected if row["source_category"] == amenity]) < target:
                    selected.append(by_city[city.key].pop())
                    progressed = True
            if not progressed:
                break
    return selected
