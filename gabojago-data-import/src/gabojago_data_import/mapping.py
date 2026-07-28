from __future__ import annotations

import csv
from pathlib import Path

from .models import PlaceRecord, Subtype


CAFE_CATEGORY_CODES = {"A05020900"}
CONTENT_TYPE_TO_PLACE_TYPE = {
    "12": "TOURIST_SPOT",
    "14": "TOURIST_SPOT",
    "28": "ACTIVITY",
    "32": "ACCOMMODATION",
    "38": "SHOP",
}


def load_subtype_mappings(path: Path) -> dict[tuple[str, str], Subtype]:
    with path.open(newline="", encoding="utf-8") as handle:
        rows = csv.DictReader(handle)
        return {
            (row["place_type"], row["cat3"]): Subtype(row["code"], row["name"])
            for row in rows
        }


def resolve_place_type(content_type_id: str, category_code: str | None) -> str | None:
    if content_type_id == "39":
        return "CAFE" if category_code in CAFE_CATEGORY_CODES else "RESTAURANT"
    return CONTENT_TYPE_TO_PLACE_TYPE.get(content_type_id)


def resolve_subtype(
    record: PlaceRecord, mappings: dict[tuple[str, str], Subtype]
) -> Subtype | None:
    if not record.category_code:
        return None
    return mappings.get(
        (record.place_type, record.category_code),
        Subtype(code=f"TOUR_API_{record.category_code}", name=record.category_code),
    )
