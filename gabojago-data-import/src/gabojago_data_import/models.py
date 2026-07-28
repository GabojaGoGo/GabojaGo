from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal


@dataclass(frozen=True)
class Area:
    code: str
    name: str


@dataclass(frozen=True)
class Sigungu:
    area_code: str
    code: str
    name: str


@dataclass(frozen=True)
class PlaceRecord:
    source_place_id: str
    name: str
    area_code: str
    sigungu_code: str | None
    place_type: str
    category_code: str | None
    address: str | None
    latitude: Decimal | None
    longitude: Decimal | None
    phone: str | None
    image_url: str | None
    thumbnail_url: str | None


@dataclass(frozen=True)
class Subtype:
    code: str
    name: str
