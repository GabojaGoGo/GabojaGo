from __future__ import annotations

import json
import math
import time
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen

TOUR_API_URL = "https://apis.data.go.kr/B551011/KorService2/areaBasedList2"

CONTENT_TYPE_DIRECTORIES = {
    "12": "12_관광지",
    "14": "14_문화시설",
    "15": "15_축제공연행사",
    "25": "25_여행코스",
    "28": "28_레포츠",
    "32": "32_숙박",
    "38": "38_쇼핑",
    "39": "39_음식점",
}


class TourApiError(RuntimeError):
    """Raised when TourAPI cannot return a valid page."""


@dataclass(frozen=True)
class TourApiPage:
    page_no: int
    num_of_rows: int
    total_count: int
    item_count: int

    @property
    def total_pages(self) -> int:
        return max(1, math.ceil(self.total_count / self.num_of_rows))


@dataclass(frozen=True)
class CollectionSummary:
    content_type_id: str
    classification_code: str | None
    total_count: int
    total_pages: int
    fetched_pages: int
    reused_pages: int
    output_dir: Path


OpenUrl = Callable[[Request, float], bytes]


def _default_open_url(request: Request, timeout: float) -> bytes:
    with urlopen(request, timeout=timeout) as response:
        return response.read()


class TourApiCollector:
    def __init__(
        self,
        service_key: str,
        output_root: Path,
        *,
        num_of_rows: int = 100,
        request_interval: float = 0.2,
        timeout: float = 30.0,
        max_retries: int = 3,
        open_url: OpenUrl = _default_open_url,
    ) -> None:
        if not service_key.strip():
            raise ValueError("TOUR_API_SERVICE_KEY is empty")
        if not 1 <= num_of_rows <= 100:
            raise ValueError("num_of_rows must be between 1 and 100")

        self.service_key = service_key.strip()
        self.output_root = output_root
        self.num_of_rows = num_of_rows
        self.request_interval = max(0.0, request_interval)
        self.timeout = timeout
        self.max_retries = max(1, max_retries)
        self.open_url = open_url

    def collect(
        self,
        content_type_id: str,
        *,
        classification_code: str | None = None,
        max_pages: int | None = None,
        resume: bool = True,
        on_progress: Callable[[int, int, int, bool], None] | None = None,
    ) -> CollectionSummary:
        directory_name = CONTENT_TYPE_DIRECTORIES.get(
            content_type_id, f"content-type-{content_type_id}"
        )
        output_dir = self.output_root / directory_name
        if classification_code is not None:
            _validate_classification_code(classification_code)
            output_dir = output_dir / classification_code
        output_dir.mkdir(parents=True, exist_ok=True)

        page_no = 1
        total_pages = 1
        total_count = 0
        fetched_pages = 0
        reused_pages = 0

        while page_no <= total_pages:
            if max_pages is not None and page_no > max_pages:
                break

            page_path = output_dir / f"page-{page_no:05d}.json"
            reused = resume and page_path.exists()
            if reused:
                raw = page_path.read_bytes()
                reused_pages += 1
            else:
                raw = self._fetch_page(content_type_id, page_no, classification_code)
                self._write_atomically(page_path, raw)
                fetched_pages += 1

            page = parse_tour_api_page(raw)
            if page.page_no != page_no:
                raise TourApiError(
                    f"requested page {page_no}, but TourAPI returned page {page.page_no}"
                )

            total_count = page.total_count
            total_pages = max(1, math.ceil(total_count / self.num_of_rows))
            if on_progress is not None:
                on_progress(page_no, total_pages, page.item_count, reused)

            page_no += 1
            if not reused and page_no <= total_pages:
                time.sleep(self.request_interval)

        return CollectionSummary(
            content_type_id=content_type_id,
            classification_code=classification_code,
            total_count=total_count,
            total_pages=total_pages,
            fetched_pages=fetched_pages,
            reused_pages=reused_pages,
            output_dir=output_dir,
        )

    def _fetch_page(
        self, content_type_id: str, page_no: int, classification_code: str | None
    ) -> bytes:
        params = {
            "numOfRows": self.num_of_rows,
            "pageNo": page_no,
            "MobileOS": "ETC",
            "MobileApp": "GabojaGODataImport",
            "_type": "json",
            "contentTypeId": content_type_id,
            "arrange": "C",
        }
        if classification_code is not None:
            params.update(
                {
                    "lclsSystm1": classification_code[:2],
                    "lclsSystm2": classification_code[:4],
                    "lclsSystm3": classification_code,
                }
            )
        encoded_key = (
            self.service_key if "%" in self.service_key else quote(self.service_key, safe="")
        )
        url = f"{TOUR_API_URL}?serviceKey={encoded_key}&{urlencode(params)}"
        request = Request(url, headers={"Accept": "application/json"})

        last_error: Exception | None = None
        for attempt in range(1, self.max_retries + 1):
            try:
                raw = self.open_url(request, self.timeout)
                parse_tour_api_page(raw)
                return raw
            except (HTTPError, URLError, TimeoutError, TourApiError, json.JSONDecodeError) as error:
                last_error = error
                if attempt < self.max_retries:
                    time.sleep(min(2 ** (attempt - 1), 4))

        raise TourApiError(
            f"failed to fetch contentTypeId={content_type_id}, page={page_no}"
        ) from last_error

    @staticmethod
    def _write_atomically(path: Path, raw: bytes) -> None:
        temporary_path = path.with_suffix(".json.tmp")
        temporary_path.write_bytes(raw)
        temporary_path.replace(path)


def _validate_classification_code(code: str) -> None:
    if len(code) != 8 or not code[:2].isalpha() or not code[2:].isdigit():
        raise ValueError(f"invalid lclsSystm3 code: {code}")


def parse_tour_api_page(raw: bytes) -> TourApiPage:
    try:
        root = json.loads(raw)
        response = root["response"]
        header = response["header"]
        result_code = str(header.get("resultCode", ""))
        result_message = str(header.get("resultMsg", ""))
        if result_code != "0000":
            raise TourApiError(f"TourAPI error: {result_code} {result_message}")

        body = response["body"]
        num_of_rows = int(body["numOfRows"])
        page_no = int(body["pageNo"])
        total_count = int(body["totalCount"])
        items = body.get("items") or {}
        item_data = items.get("item") if isinstance(items, dict) else None

        if isinstance(item_data, list):
            item_count = len(item_data)
        elif isinstance(item_data, dict):
            item_count = 1
        else:
            item_count = 0

        if num_of_rows <= 0 or page_no <= 0 or total_count < 0:
            raise TourApiError("TourAPI returned invalid pagination values")

        return TourApiPage(
            page_no=page_no,
            num_of_rows=num_of_rows,
            total_count=total_count,
            item_count=item_count,
        )
    except TourApiError:
        raise
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
        raise TourApiError("TourAPI returned an invalid JSON response") from error
