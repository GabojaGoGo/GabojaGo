from __future__ import annotations

import argparse
import json
import os
from dataclasses import asdict
from datetime import datetime, timezone
from pathlib import Path

from data_import.tour_api.collector import (
    CONTENT_TYPE_DIRECTORIES,
    TourApiCollector,
    TourApiError,
)

DEFAULT_OUTPUT = Path("data/raw/tourism/tour-api")
DEFAULT_REPORTS = Path("data/reports")


def load_environment() -> None:
    """Load .env when python-dotenv is installed, with a stdlib fallback.

    The fallback intentionally supports only the simple KEY=VALUE form used by
    this repository's local import configuration.
    """
    try:
        from dotenv import load_dotenv

        load_dotenv()
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


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="TourAPI 전체 유형을 JSON 파일로 수집합니다.",
    )
    parser.add_argument(
        "--content-type",
        action="append",
        choices=CONTENT_TYPE_DIRECTORIES,
        help="특정 유형만 수집합니다. 생략하면 전체 유형을 수집합니다.",
    )
    parser.add_argument(
        "--classification-code",
        action="append",
        help="TourAPI lclsSystm3 코드. 반복 지정 가능하며 음식점(39) 수집에만 사용합니다.",
    )
    parser.add_argument("--max-pages", type=int)
    parser.add_argument("--num-of-rows", type=int, default=100)
    parser.add_argument("--request-interval", type=float, default=0.2)
    parser.add_argument("--no-resume", action="store_true")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--reports", type=Path, default=DEFAULT_REPORTS)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    load_environment()
    service_key = os.getenv("TOUR_API_SERVICE_KEY", "").strip()
    if not service_key:
        print("ERROR: TOUR_API_SERVICE_KEY가 .env에 설정되지 않았습니다.")
        return 2
    if args.max_pages is not None and args.max_pages < 1:
        print("ERROR: --max-pages는 1 이상이어야 합니다.")
        return 2
    if not 1 <= args.num_of_rows <= 100:
        print("ERROR: --num-of-rows는 1부터 100까지 사용할 수 있습니다.")
        return 2
    if args.classification_code and args.content_type != ["39"]:
        print("ERROR: --classification-code는 --content-type 39와 함께 사용해야 합니다.")
        return 2

    collector = TourApiCollector(
        service_key=service_key,
        output_root=args.output,
        num_of_rows=args.num_of_rows,
        request_interval=args.request_interval,
    )
    content_types = args.content_type or list(CONTENT_TYPE_DIRECTORIES)

    try:
        for content_type in content_types:
            directory_name = CONTENT_TYPE_DIRECTORIES[content_type]
            classification_codes = args.classification_code or [None]
            for classification_code in classification_codes:
                label = f" / {classification_code}" if classification_code else ""
                print(f"\n[{content_type}] {directory_name}{label} 수집")
                summary = collector.collect(
                    content_type,
                    classification_code=classification_code,
                    max_pages=args.max_pages,
                    resume=not args.no_resume,
                    on_progress=lambda page, total, count, reused: print(
                        f"  {page}/{total} {'재사용' if reused else '다운로드'} {count}건"
                    ),
                )
                report_path = write_report(args.reports, content_type, summary)
                print(
                    f"완료: 전체 {summary.total_count}건, "
                    f"다운로드 {summary.fetched_pages}페이지, "
                    f"재사용 {summary.reused_pages}페이지"
                )
                print(f"보고서: {report_path}")
    except (TourApiError, OSError) as error:
        print(f"ERROR: {error}")
        return 1

    return 0


def write_report(reports: Path, content_type: str, summary) -> Path:
    reports.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%SZ")
    classification_suffix = f"-{summary.classification_code}" if summary.classification_code else ""
    report_path = reports / f"tour-api-{content_type}{classification_suffix}-{timestamp}.json"
    report = asdict(summary)
    report["output_dir"] = str(summary.output_dir)
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return report_path


if __name__ == "__main__":
    raise SystemExit(main())
