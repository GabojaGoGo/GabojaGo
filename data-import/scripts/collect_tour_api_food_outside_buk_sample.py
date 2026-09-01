"""Collect a stratified outside-BU/UL/GN TourAPI restaurant detail sample."""

from __future__ import annotations

import argparse
import json
import os
from dataclasses import asdict
from datetime import datetime, timezone
from pathlib import Path

from data_import.tour_api.collector import CONTENT_TYPE_DIRECTORIES, TourApiError
from data_import.tour_api.detail_collector import (
    PlaceRef,
    TourApiDetailCollector,
    stratified_sample,
)

EXCLUDED_REGION_CODES = frozenset({"26", "31", "48"})
DEFAULT_LIST_ROOT = Path("data/raw/tourism/tour-api")
DEFAULT_OUTPUT = Path("data/raw/tourism/tour-api-detail-outside-buk/39_음식점")
DEFAULT_REPORTS = Path("data/reports")


def load_environment() -> None:
    """Load the local service key with the same stdlib fallback as the main collector."""
    try:
        from dotenv import load_dotenv

        load_dotenv(dotenv_path=Path(".env"))
        return
    except ModuleNotFoundError:
        pass

    env_path = Path(".env")
    if not env_path.is_file():
        return
    for line in env_path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        key = key.removeprefix("export ").strip()
        if key and key not in os.environ:
            os.environ[key] = value.strip().strip('"').strip("'")


def load_outside_buk_food_refs(raw_root: Path) -> list[PlaceRef]:
    """Load each restaurant once, excluding Busan, Ulsan, and Gyeongnam."""
    source_dir = raw_root / CONTENT_TYPE_DIRECTORIES["39"]
    if not source_dir.is_dir():
        raise TourApiError(f"목록 조회 원본이 없습니다: {source_dir}")

    refs: list[PlaceRef] = []
    seen: set[str] = set()
    for page_path in sorted(source_dir.glob("page-*.json")):
        body = json.loads(page_path.read_text(encoding="utf-8"))["response"]["body"]
        items = body.get("items") or {}
        item_data = items.get("item") if isinstance(items, dict) else None
        if not isinstance(item_data, list):
            continue
        for item in item_data:
            content_id = str(item.get("contentid", ""))
            region_code = str(item.get("lDongRegnCd", "")).zfill(2)
            if (
                not content_id
                or content_id in seen
                or region_code in EXCLUDED_REGION_CODES
            ):
                continue
            seen.add(content_id)
            refs.append(
                PlaceRef(
                    content_id=content_id,
                    content_type_id="39",
                    title="",
                    region_code=region_code,
                    sigungu_code=str(item.get("lDongSignguCd", "")),
                    category_code=str(item.get("lclsSystm1", "")),
                )
            )
    return refs


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="부울경 밖 TourAPI 음식점(39) detailIntro2 층화표본을 수집합니다."
    )
    parser.add_argument("--sample-size", type=int, default=500)
    parser.add_argument("--seed", type=int, default=20260901)
    parser.add_argument("--request-interval", type=float, default=0.2)
    parser.add_argument("--no-resume", action="store_true")
    parser.add_argument("--list-root", type=Path, default=DEFAULT_LIST_ROOT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--reports", type=Path, default=DEFAULT_REPORTS)
    parser.add_argument("--dry-run", action="store_true")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if args.sample_size < 1:
        print("ERROR: --sample-size는 1 이상이어야 합니다.")
        return 2

    try:
        refs = load_outside_buk_food_refs(args.list_root)
    except (TourApiError, OSError, json.JSONDecodeError, KeyError) as error:
        print(f"ERROR: {error}")
        return 1
    if not refs:
        print("ERROR: 부울경 밖 음식점(39) 목록 대상이 없습니다.")
        return 1

    sample = stratified_sample(refs, args.sample_size, seed=args.seed)
    print(
        f"부울경 밖 음식점 모집단 {len(refs):,}건 / "
        f"표본 {len(sample):,}건 / {len({ref.stratum for ref in sample})}개 층"
    )
    if args.dry_run:
        return 0

    load_environment()
    service_key = os.getenv("TOUR_API_SERVICE_KEY", "").strip()
    if not service_key:
        print("ERROR: TOUR_API_SERVICE_KEY가 .env에 설정되지 않았습니다.")
        return 2

    collector = TourApiDetailCollector(
        service_key=service_key,
        output_root=args.output.parent,
        request_interval=args.request_interval,
    )
    try:
        summary = collector.collect(
            sample,
            content_type_id="39",
            population=len(refs),
            resume=not args.no_resume,
            on_progress=lambda index, total, _ref, state: print(f"{index}/{total} {state}"),
        )
    except OSError as error:
        print(f"ERROR: {error}")
        return 1

    args.reports.mkdir(parents=True, exist_ok=True)
    report_path = args.reports / (
        f"tour-api-food-outside-buk-detail-sample-"
        f"{datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%SZ')}.json"
    )
    report = asdict(summary)
    report["output_dir"] = str(summary.output_dir)
    report.update(
        {
            "scope": "TourAPI restaurant(39), excluding Busan(26), Ulsan(31), Gyeongnam(48)",
            "seed": args.seed,
            "sample_size_requested": args.sample_size,
        }
    )
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
