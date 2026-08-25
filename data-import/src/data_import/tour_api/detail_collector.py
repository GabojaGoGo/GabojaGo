from __future__ import annotations

import json
import random
import time
from collections import defaultdict
from collections.abc import Callable, Iterable, Sequence
from dataclasses import dataclass, field
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen

from data_import.tour_api.collector import CONTENT_TYPE_DIRECTORIES, TourApiError

DETAIL_INTRO_URL = "https://apis.data.go.kr/B551011/KorService2/detailIntro2"

# 법정동 시도 코드. 부울경만 수집 대상이다.
REGION_CODES = {"26": "부산", "31": "울산", "48": "경남"}

# 채움률 집계에서 제외한다. 요청을 되비추는 값이라 원천이 준 사실이 아니다.
ECHO_FIELDS = frozenset({"contentid", "contenttypeid"})


@dataclass(frozen=True)
class PlaceRef:
    """상세 조회 대상 한 건. 목록 조회 응답에서 뽑는다."""

    content_id: str
    content_type_id: str
    title: str
    region_code: str
    sigungu_code: str
    category_code: str

    @property
    def stratum(self) -> tuple[str, str]:
        """표본 층. 시군구는 넣지 않는다.

        지역·시군구·분류로 잡으면 관광지 1,920건이 168개 층으로 갈라져, 표본 200건에서
        143개 층이 1건씩을 가져간다. 모집단 77건인 층과 1건인 층이 같은 무게가 되어
        전체 채움률 추정이 작은 층 쪽으로 치우친다. 지역·분류로 잡으면 12개 층이라
        비례 배분이 실제로 동작한다. 시군구 값은 필요할 때 쓰도록 보존만 한다.
        """
        return (self.region_code, self.category_code)


@dataclass(frozen=True)
class DetailCollectionSummary:
    content_type_id: str
    population: int
    sampled: int
    fetched: int
    reused: int
    empty: int
    failed: int
    strata: int
    output_dir: Path
    field_fill_counts: dict[str, int] = field(default_factory=dict)
    failures: list[str] = field(default_factory=list)

    def fill_rates(self) -> dict[str, float]:
        """필드별 채움률. 분모는 상세가 실제로 존재한 건수다.

        원천에 상세가 등록되지 않은 건(`empty`)은 분모에서 뺀다. 넣으면 "필드가
        비어 있다"와 "장소 자체에 상세가 없다"가 섞인다. `empty`는 `fetched`나
        `reused`에도 함께 세므로 여기서 한 번 빼야 중복이 사라진다.
        """
        answered = self.fetched + self.reused - self.empty
        if answered <= 0:
            return {}
        return {
            name: count / answered * 100
            for name, count in sorted(
                self.field_fill_counts.items(), key=lambda item: -item[1]
            )
        }


OpenUrl = Callable[[Request, float], bytes]


def _default_open_url(request: Request, timeout: float) -> bytes:
    with urlopen(request, timeout=timeout) as response:
        return response.read()


def load_place_refs(
    raw_root: Path,
    content_type_id: str,
    *,
    region_codes: Iterable[str] = REGION_CODES,
) -> list[PlaceRef]:
    """이미 수집한 목록 조회 원본에서 상세 조회 대상을 뽑는다. API를 호출하지 않는다."""
    directory_name = CONTENT_TYPE_DIRECTORIES.get(
        content_type_id, f"content-type-{content_type_id}"
    )
    source_dir = raw_root / directory_name
    if not source_dir.is_dir():
        raise TourApiError(f"목록 조회 원본이 없습니다: {source_dir}")

    wanted = set(region_codes)
    refs: list[PlaceRef] = []
    seen: set[str] = set()

    for page_path in sorted(source_dir.glob("page-*.json")):
        body = json.loads(page_path.read_text(encoding="utf-8"))["response"]["body"]
        items = body.get("items") or {}
        item_data = items.get("item") if isinstance(items, dict) else None
        if not isinstance(item_data, list):
            continue

        for item in item_data:
            region_code = str(item.get("lDongRegnCd", "")).zfill(2)
            content_id = str(item.get("contentid", ""))
            if region_code not in wanted or not content_id or content_id in seen:
                continue
            seen.add(content_id)
            refs.append(
                PlaceRef(
                    content_id=content_id,
                    content_type_id=str(item.get("contenttypeid", content_type_id)),
                    title=str(item.get("title", "")),
                    region_code=region_code,
                    sigungu_code=str(item.get("lDongSignguCd", "")),
                    category_code=str(item.get("lclsSystm1", "")),
                )
            )

    return refs


def stratified_sample(
    refs: Sequence[PlaceRef], sample_size: int, *, seed: int = 20260820
) -> list[PlaceRef]:
    """`PlaceRef.stratum`을 층으로 잡아 비례 배분한다.

    모든 층에 최소 1건을 먼저 배정한 뒤 남은 몫을 층 크기에 비례해 나눈다. 층이
    표본 크기보다 많으면 큰 층부터 채운다. 같은 seed면 같은 표본이 나온다.
    """
    if sample_size <= 0:
        raise ValueError("sample_size는 1 이상이어야 합니다")
    if sample_size >= len(refs):
        return list(refs)

    groups: dict[tuple[str, str], list[PlaceRef]] = defaultdict(list)
    for ref in refs:
        groups[ref.stratum].append(ref)

    # 큰 층부터 배정해, 층이 표본보다 많을 때 작은 층이 통째로 빠지게 한다.
    ordered = sorted(groups.items(), key=lambda item: (-len(item[1]), item[0]))
    quotas = {key: 0 for key, _ in ordered}

    remaining = sample_size
    for key, members in ordered:
        if remaining == 0:
            break
        quotas[key] = 1
        remaining -= 1

    # 남은 몫은 층의 잔여 용량에 비례해 나눈다. 반올림으로 몫이 새지 않도록
    # 다 채울 때까지 반복하며, 한 바퀴에 최소 1건은 배정해 반드시 끝나게 한다.
    while remaining > 0:
        open_keys = [key for key, quota in quotas.items() if quota < len(groups[key])]
        if not open_keys:
            break
        capacity = {key: len(groups[key]) - quotas[key] for key in open_keys}
        total_capacity = sum(capacity.values())
        for key in sorted(open_keys, key=lambda k: (-capacity[k], k)):
            if remaining == 0:
                break
            share = max(1, round(remaining * capacity[key] / total_capacity))
            extra = min(share, capacity[key], remaining)
            quotas[key] += extra
            remaining -= extra

    rng = random.Random(seed)
    sampled: list[PlaceRef] = []
    for key, _ in ordered:
        quota = quotas[key]
        if quota:
            sampled.extend(rng.sample(groups[key], quota))

    sampled.sort(key=lambda ref: ref.content_id)
    return sampled


class TourApiDetailCollector:
    """contentid별 detailIntro2 응답을 파일로 저장한다.

    한 건이 파일 하나다. 이미 있는 파일은 다시 받지 않으므로 중단 후 같은 명령을
    다시 실행하면 이어서 진행된다.
    """

    def __init__(
        self,
        service_key: str,
        output_root: Path,
        *,
        request_interval: float = 0.2,
        timeout: float = 30.0,
        max_retries: int = 3,
        open_url: OpenUrl = _default_open_url,
    ) -> None:
        if not service_key.strip():
            raise ValueError("TOUR_API_SERVICE_KEY is empty")

        self.service_key = service_key.strip()
        self.output_root = output_root
        self.request_interval = max(0.0, request_interval)
        self.timeout = timeout
        self.max_retries = max(1, max_retries)
        self.open_url = open_url

    def collect(
        self,
        refs: Sequence[PlaceRef],
        *,
        content_type_id: str,
        population: int,
        resume: bool = True,
        on_progress: Callable[[int, int, PlaceRef, str], None] | None = None,
    ) -> DetailCollectionSummary:
        directory_name = CONTENT_TYPE_DIRECTORIES.get(
            content_type_id, f"content-type-{content_type_id}"
        )
        output_dir = self.output_root / directory_name
        output_dir.mkdir(parents=True, exist_ok=True)

        fetched = reused = empty = failed = 0
        fill_counts: dict[str, int] = defaultdict(int)
        failures: list[str] = []

        for index, ref in enumerate(refs, start=1):
            path = output_dir / f"{ref.content_id}.json"
            if resume and path.exists():
                raw = path.read_bytes()
                reused += 1
                state = "재사용"
            else:
                try:
                    raw = self._fetch_detail(ref)
                except TourApiError as error:
                    failed += 1
                    failures.append(f"{ref.content_id} {ref.title}: {error}")
                    if on_progress is not None:
                        on_progress(index, len(refs), ref, "실패")
                    time.sleep(self.request_interval)
                    continue
                self._write_atomically(path, raw)
                fetched += 1
                state = "다운로드"

            names = _filled_field_names(raw)
            if names is None:
                empty += 1
                state = "원천없음"
            else:
                for name in names:
                    fill_counts[name] += 1

            if on_progress is not None:
                on_progress(index, len(refs), ref, state)
            if state == "다운로드":
                time.sleep(self.request_interval)

        return DetailCollectionSummary(
            content_type_id=content_type_id,
            population=population,
            sampled=len(refs),
            fetched=fetched,
            reused=reused,
            empty=empty,
            failed=failed,
            strata=len({ref.stratum for ref in refs}),
            output_dir=output_dir,
            field_fill_counts=dict(fill_counts),
            failures=failures,
        )

    def _fetch_detail(self, ref: PlaceRef) -> bytes:
        params = {
            "MobileOS": "ETC",
            "MobileApp": "GabojaGODataImport",
            "_type": "json",
            "contentId": ref.content_id,
            "contentTypeId": ref.content_type_id,
        }
        encoded_key = (
            self.service_key if "%" in self.service_key else quote(self.service_key, safe="")
        )
        url = f"{DETAIL_INTRO_URL}?serviceKey={encoded_key}&{urlencode(params)}"
        request = Request(url, headers={"Accept": "application/json"})

        last_error: Exception | None = None
        for attempt in range(1, self.max_retries + 1):
            try:
                raw = self.open_url(request, self.timeout)
                parse_detail_item(raw)  # 형식 검증. 빈 응답은 None이며 오류가 아니다.
                return raw
            except (HTTPError, URLError, TimeoutError, TourApiError, json.JSONDecodeError) as error:
                last_error = error
                if attempt < self.max_retries:
                    time.sleep(min(2 ** (attempt - 1), 4))

        raise TourApiError(f"상세 조회 실패 contentId={ref.content_id}") from last_error

    @staticmethod
    def _write_atomically(path: Path, raw: bytes) -> None:
        temporary_path = path.with_suffix(".json.tmp")
        temporary_path.write_bytes(raw)
        temporary_path.replace(path)


def parse_detail_item(raw: bytes) -> dict[str, object] | None:
    """상세 조회 응답에서 항목 하나를 꺼낸다.

    원천에 상세가 등록되지 않은 장소는 TourAPI가 `resultCode 0000`에 빈 `items`와
    `totalCount 0`을 준다. 이것은 수집 실패가 아니라 원천에 값이 없다는 사실이므로
    `None`을 돌려준다. 오류로 다루면 재시도로 쿼터를 낭비하고, 집계에서도
    `MISSING`과 `PARSE_FAILED`가 섞인다.

    응답 형식 자체가 어긋나거나 API가 오류 코드를 주면 TourApiError를 낸다.
    """
    root = json.loads(raw)
    response = root["response"]
    header = response["header"]
    result_code = str(header.get("resultCode", ""))
    if result_code != "0000":
        raise TourApiError(f"TourAPI error: {result_code} {header.get('resultMsg', '')}")

    body = response.get("body") or {}
    items = body.get("items")
    item_data = items.get("item") if isinstance(items, dict) else None

    if isinstance(item_data, dict):
        return item_data
    if isinstance(item_data, list) and item_data:
        return item_data[0]
    if int(body.get("totalCount", 0) or 0) == 0:
        return None
    raise TourApiError("totalCount는 0이 아닌데 상세 항목을 찾을 수 없습니다")


def _filled_field_names(raw: bytes) -> set[str] | None:
    """값이 들어 있는 필드 이름. 빈 문자열과 "0"은 비어 있는 것으로 본다.

    원천에 상세가 등록되지 않은 응답이면 `None`을 돌려준다. 빈 집합과 구분해야
    채움률 분모에서 뺄 수 있다.
    """
    try:
        item = parse_detail_item(raw)
    except (TourApiError, json.JSONDecodeError, KeyError):
        return set()
    if item is None:
        return None

    filled: set[str] = set()
    for name, value in item.items():
        if name in ECHO_FIELDS:
            continue
        text = str(value).strip()
        if text and text != "0":
            filled.add(name)
    return filled
