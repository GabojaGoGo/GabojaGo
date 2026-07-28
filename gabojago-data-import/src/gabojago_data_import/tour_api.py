from __future__ import annotations

from decimal import Decimal, InvalidOperation
from urllib.parse import unquote

import requests

from .config import Settings
from .mapping import resolve_place_type
from .models import Area, PlaceRecord, Sigungu


CONTENT_TYPE_IDS = ("12", "14", "28", "32", "38", "39")


class TourApiClient:
    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.tour_api_base_url
        self._service_key = unquote(settings.tour_api_service_key)
        self._session = requests.Session()

    def list_areas(self) -> list[Area]:
        return [Area(code=item["code"], name=item["name"]) for item in self._items("areaCode1")]

    def list_sigungu(self, area_code: str) -> list[Sigungu]:
        return [
            Sigungu(area_code=area_code, code=item["code"], name=item["name"])
            for item in self._items("areaCode1", areaCode=area_code)
        ]

    def iter_places(
        self, area_code: str, sigungu_code: str | None, content_type_id: str, max_pages: int | None
    ):
        page_no = 1
        while max_pages is None or page_no <= max_pages:
            payload = self._request(
                "areaBasedList2",
                areaCode=area_code,
                sigunguCode=sigungu_code,
                contentTypeId=content_type_id,
                pageNo=page_no,
                numOfRows=100,
            )
            response = payload.get("response", {})
            body = response.get("body", {})
            items = body.get("items", {}).get("item", []) or []
            if isinstance(items, dict):
                items = [items]
            for item in items:
                record = self._to_place(item, content_type_id, area_code, sigungu_code)
                if record is not None:
                    yield record
            total_count = int(body.get("totalCount", 0) or 0)
            if not items or page_no * 100 >= total_count:
                return
            page_no += 1

    def _items(self, endpoint: str, **params: str) -> list[dict]:
        payload = self._request(endpoint, numOfRows=100, pageNo=1, **params)
        items = payload.get("response", {}).get("body", {}).get("items", {}).get("item", []) or []
        return [items] if isinstance(items, dict) else items

    def _request(self, endpoint: str, **params: object) -> dict:
        request_params = {
            "serviceKey": self._service_key,
            "MobileOS": "ETC",
            "MobileApp": "GabojaGo",
            "_type": "json",
            **{key: value for key, value in params.items() if value is not None},
        }
        response = self._session.get(f"{self._base_url}/{endpoint}", params=request_params, timeout=30)
        response.raise_for_status()
        payload = response.json()
        header = payload.get("response", {}).get("header", {})
        if header.get("resultCode") not in (None, "0000"):
            raise RuntimeError(f"TourAPI {endpoint} failed: {header}")
        return payload

    def _to_place(
        self, item: dict, content_type_id: str, area_code: str, sigungu_code: str | None
    ) -> PlaceRecord | None:
        source_place_id = text(item, "contentid")
        name = text(item, "title")
        category_code = text(item, "cat3")
        place_type = resolve_place_type(content_type_id, category_code)
        if not source_place_id or not name or not place_type:
            return None
        return PlaceRecord(
            source_place_id=source_place_id,
            name=name,
            area_code=text(item, "areacode") or area_code,
            sigungu_code=text(item, "sigungucode") or sigungu_code,
            place_type=place_type,
            category_code=category_code,
            address=join_address(text(item, "addr1"), text(item, "addr2")),
            latitude=decimal_or_none(text(item, "mapy")),
            longitude=decimal_or_none(text(item, "mapx")),
            phone=text(item, "tel"),
            image_url=text(item, "firstimage"),
            thumbnail_url=text(item, "firstimage2"),
        )


def text(item: dict, key: str) -> str | None:
    value = str(item.get(key, "")).strip()
    return value or None


def join_address(first: str | None, second: str | None) -> str | None:
    return " ".join(value for value in (first, second) if value) or None


def decimal_or_none(value: str | None) -> Decimal | None:
    if value is None:
        return None
    try:
        return Decimal(value).quantize(Decimal("0.0000001"))
    except InvalidOperation:
        return None
