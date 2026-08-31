from __future__ import annotations

import argparse
from pathlib import Path

from data_import.taxonomy_validation import (
    TaxonomyValidationError,
    load_samples,
    validate_samples,
    write_report,
    write_tour_api_candidates,
    write_tour_api_proposals,
)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="RESTAURANT·CAFE Canonical Taxonomy 표본을 오프라인 검증합니다."
    )
    commands = parser.add_subparsers(dest="command", required=True)

    extract = commands.add_parser("extract", help="수집된 TourAPI 음식점에서 검토용 100건 CSV를 만듭니다.")
    extract.add_argument("--input", type=Path, default=Path("data/raw/tourism/tour-api"))
    extract.add_argument("--output", type=Path, required=True)
    extract.add_argument("--sample-size", type=int, default=100)

    propose = commands.add_parser(
        "propose",
        help="TourAPI 원천 분류에서 안전한 범위의 1차 Canonical 제안을 만듭니다.",
    )
    propose.add_argument("--input", type=Path, default=Path("data/raw/tourism/tour-api"))
    propose.add_argument("--output", type=Path, required=True)
    propose.add_argument("--sample-size", type=int, default=440)

    validate = commands.add_parser("validate", help="검토 완료 CSV의 분류 규칙을 검사합니다.")
    validate.add_argument("--input", type=Path, required=True)
    validate.add_argument("--report", type=Path, required=True)
    validate.add_argument("--minimum-samples", type=int, default=100)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        if args.command == "extract":
            count = write_tour_api_candidates(args.input, args.output, sample_size=args.sample_size)
            print(f"검토용 표본 {count}건 생성: {args.output}")
            return 0

        if args.command == "propose":
            count = write_tour_api_proposals(args.input, args.output, sample_size=args.sample_size)
            print(f"1차 Canonical 제안 {count}건 생성: {args.output}")
            return 0

        summary = validate_samples(load_samples(args.input), minimum_samples=args.minimum_samples)
        write_report(args.report, summary)
        print(
            f"검증 완료: 표본 {summary.samples}건, 적합 {summary.valid}, "
            f"검토 필요 {summary.needs_review}, 오류 {summary.invalid}"
        )
        print(f"보고서: {args.report}")
        return 1 if summary.invalid else 0
    except (OSError, TaxonomyValidationError, ValueError, KeyError) as error:
        print(f"ERROR: {error}")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
