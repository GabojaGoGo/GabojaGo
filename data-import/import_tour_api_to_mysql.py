from __future__ import annotations

import argparse
import json
import os
from datetime import UTC, datetime
from pathlib import Path

from dotenv import load_dotenv

from data_import.tour_api.importer import (
    DatabaseConfig,
    TourApiImportError,
    connect_database,
    import_tour_api,
)

DEFAULT_INPUT = Path("data/raw/tourism/tour-api")
DEFAULT_MAPPING = Path("mappings/tour_api_subtypes.csv")
DEFAULT_REPORTS = Path("data/reports")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="수집된 TourAPI JSON을 검사하고 MySQL에 적재합니다.",
    )
    parser.add_argument(
        "--write",
        action="store_true",
        help="실제로 DB에 반영합니다. 생략하면 dry-run입니다.",
    )
    parser.add_argument("--limit", type=int)
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    parser.add_argument("--reports", type=Path, default=DEFAULT_REPORTS)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if args.limit is not None and args.limit < 1:
        print("ERROR: --limit은 1 이상이어야 합니다.")
        return 2

    load_dotenv()
    config = DatabaseConfig(
        host=os.getenv("IMPORT_DB_HOST", "127.0.0.1"),
        port=int(os.getenv("IMPORT_DB_PORT", "3306")),
        database=os.getenv("IMPORT_DB_NAME", "gabojago"),
        username=os.getenv("DB_USERNAME", "").strip(),
        password=os.getenv("DB_PASSWORD", ""),
    )
    if not config.username:
        print("ERROR: DB_USERNAME이 .env에 설정되지 않았습니다.")
        return 2

    connection = None
    try:
        connection = connect_database(config)
        summary = import_tour_api(
            connection,
            args.input,
            args.mapping,
            limit=args.limit,
            dry_run=not args.write,
        )
    except (TourApiImportError, OSError, ValueError) as error:
        print(f"ERROR: {error}")
        return 1
    finally:
        if connection is not None:
            connection.close()

    mode = "실제 저장" if args.write else "검사만 수행"
    report_path = write_report(args.reports, mode, summary)
    print(f"\n완료 ({mode})")
    print(f"  전체 확인: {summary.scanned}건")
    print(f"  적재 대상: {summary.eligible}건")
    print(f"  신규: {summary.created}건")
    print(f"  갱신: {summary.updated}건")
    print(f"  지역 확인 불가: {summary.unresolved_region}건")
    print(f"  미지원 유형: {summary.unsupported}건")
    print(f"  잘못된 데이터: {summary.invalid}건")
    print(f"  신규 광역지역: {summary.new_regions}건")
    print(f"  SUBTYPE 연결: {summary.subtype_links}건")
    print(f"  다중 SUBTYPE 장소: {summary.multi_subtype_places}건")
    print(f"  미매핑 SUBTYPE: {summary.unmapped_subtype}건")
    print(f"보고서: {report_path}")
    return 0


def write_report(reports: Path, mode: str, summary) -> Path:
    reports.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(UTC).strftime("%Y%m%d-%H%M%SZ")
    report_path = reports / f"tour-api-import-{timestamp}.json"
    report = summary.to_dict()
    report["mode"] = mode
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return report_path


if __name__ == "__main__":
    raise SystemExit(main())
