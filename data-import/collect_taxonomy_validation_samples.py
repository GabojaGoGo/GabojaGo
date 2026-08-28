from __future__ import annotations

import argparse
from pathlib import Path

from data_import.taxonomy_sample_collector import (
    SampleCollectionError,
    collect_osm_cafe_samples,
    collect_osm_food_samples,
)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="실제 음식점·카페 검토 표본을 수집합니다.")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--per-type", type=int, default=50)
    parser.add_argument("--cafe-count", type=int, help="카페만 수집할 표본 수")
    parser.add_argument("--seed", type=int, default=20260814)
    parser.add_argument("--timeout", type=float, default=30.0)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        if args.cafe_count is not None:
            count = collect_osm_cafe_samples(
                args.output, target=args.cafe_count, seed=args.seed, timeout=args.timeout
            )
        else:
            count = collect_osm_food_samples(
                args.output, target_per_type=args.per_type, seed=args.seed, timeout=args.timeout
            )
    except (OSError, SampleCollectionError, ValueError) as error:
        print(f"ERROR: {error}")
        return 1
    print(f"검토용 표본 {count}건 생성: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
