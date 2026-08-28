from __future__ import annotations

import argparse
from pathlib import Path

from data_import.poi_validation import (
    PoiValidationError,
    load_samples,
    validate_samples,
    write_report,
    write_tour_api_poi_candidates,
)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="POINT_OF_INTEREST 표본을 수집·검증합니다.")
    commands = parser.add_subparsers(dest="command", required=True)
    extract = commands.add_parser(
        "extract",
        help="수집된 TourAPI 12·14 원본에서 층화 POI 표본 CSV를 만듭니다.",
    )
    extract.add_argument("--input", type=Path, default=Path("data/raw/tourism/tour-api"))
    extract.add_argument("--mapping", type=Path, default=Path("mappings/tour_api_subtypes.csv"))
    extract.add_argument("--output", type=Path, required=True)
    extract.add_argument("--per-kind", type=int, default=10)
    extract.add_argument("--seed", type=int, default=20260824)
    validate = commands.add_parser("validate", help="검토 완료 POI CSV를 검사합니다.")
    validate.add_argument("--input", type=Path, required=True)
    validate.add_argument("--report", type=Path, required=True)
    validate.add_argument("--minimum-samples", type=int, default=100)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        if args.command == "extract":
            count = write_tour_api_poi_candidates(
                args.input,
                args.mapping,
                args.output,
                per_kind=args.per_kind,
                seed=args.seed,
            )
            print(f"POI 검토 표본 {count}건 생성: {args.output}")
            return 0
        summary = validate_samples(load_samples(args.input), minimum_samples=args.minimum_samples)
        write_report(args.report, summary)
        print(
            f"검증 완료: 표본 {summary.samples}건, 적합 {summary.valid}, "
            f"검토 필요 {summary.needs_review}, 오류 {summary.invalid}"
        )
        print(f"보고서: {args.report}")
        return 1 if any(issue.severity == "ERROR" for issue in summary.issues) else 0
    except (OSError, PoiValidationError, ValueError, KeyError) as error:
        print(f"ERROR: {error}")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
