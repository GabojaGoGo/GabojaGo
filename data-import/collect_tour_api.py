from __future__ import annotations

import argparse
import json
import os
from dataclasses import asdict
from datetime import UTC, datetime
from pathlib import Path

from dotenv import load_dotenv

from data_import.tour_api.collector import (
    CONTENT_TYPE_DIRECTORIES,
    TourApiCollector,
    TourApiError,
)

DEFAULT_OUTPUT = Path("data/raw/tourism/tour-api")
DEFAULT_REPORTS = Path("data/reports")


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
    parser.add_argument("--max-pages", type=int)
    parser.add_argument("--num-of-rows", type=int, default=100)
    parser.add_argument("--request-interval", type=float, default=0.2)
    parser.add_argument("--no-resume", action="store_true")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--reports", type=Path, default=DEFAULT_REPORTS)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    load_dotenv()
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
            print(f"\n[{content_type}] {directory_name} 수집")
            summary = collector.collect(
                content_type,
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
    timestamp = datetime.now(UTC).strftime("%Y%m%d-%H%M%SZ")
    report_path = reports / f"tour-api-{content_type}-{timestamp}.json"
    report = asdict(summary)
    report["output_dir"] = str(summary.output_dir)
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return report_path


if __name__ == "__main__":
    raise SystemExit(main())
